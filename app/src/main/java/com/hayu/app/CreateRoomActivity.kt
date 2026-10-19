package com.hayu.app

import com.hayu.app.util.ImgbbUploader
import com.hayu.app.util.ImageUtil
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.ImageView
import android.view.ViewGroup
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.util.SessionManager

class CreateRoomActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var session: SessionManager
    private var selectedPrivacy: String = "publik"
    private var selectedMic: Int = 5
    private var selectedImageUri: Uri? = null
    private var coverUrl: String = ""

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            coverUrl = ""
            val box = findViewById<LinearLayout>(R.id.btn_change_cover)
            box.removeAllViews()
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageURI(uri)
            box.addView(iv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_room)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        session = SessionManager(this)

        val btnClose = findViewById<TextView>(R.id.btn_close)
        val btnChangeCover = findViewById<LinearLayout>(R.id.btn_change_cover)
        val etRoomName = findViewById<EditText>(R.id.et_room_name)
        val btnPrivacy = findViewById<LinearLayout>(R.id.btn_privacy)
        val tvPrivacy = findViewById<TextView>(R.id.tv_privacy)
        val etRoomTopic = findViewById<EditText>(R.id.et_room_topic)
        val btnMic5 = findViewById<FrameLayout>(R.id.btn_mic_5)
        val btnMic10 = findViewById<FrameLayout>(R.id.btn_mic_10)
        val btnMic15 = findViewById<FrameLayout>(R.id.btn_mic_15)
        val btnCreate = findViewById<TextView>(R.id.btn_create_room)

        btnClose.setOnClickListener { finish() }

        btnChangeCover.setOnClickListener { pickImage.launch("image/*") }

        btnPrivacy.setOnClickListener {
            val options = arrayOf("🌍 Publik", "🔒 Privat")
            AlertDialog.Builder(this)
                .setTitle("Pilih Privasi Room")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        selectedPrivacy = "publik"
                        tvPrivacy.text = "Publik"
                    } else {
                        selectedPrivacy = "privat"
                        tvPrivacy.text = "Privat"
                    }
                }
                .show()
        }

        val updateMicUI = { selected: Int ->
            selectedMic = selected
            val all = listOf(btnMic5 to 5, btnMic10 to 10, btnMic15 to 15)
            all.forEach { (btn, value) ->
                if (value == selected) {
                    btn.setBackgroundResource(R.drawable.bg_button_primary)
                } else {
                    btn.setBackgroundResource(R.drawable.bg_quick_item)
                }
            }
        }

        btnMic5.setOnClickListener { updateMicUI(5) }
        btnMic10.setOnClickListener { updateMicUI(10) }
        btnMic15.setOnClickListener { updateMicUI(15) }

        btnCreate.setOnClickListener {
            val roomName = etRoomName.text.toString().trim()
            val roomTopic = etRoomTopic.text.toString().trim()

            if (roomName.isEmpty()) {
                Toast.makeText(this, "Nama room wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Sesi habis, login ulang", Toast.LENGTH_SHORT).show()
                finish()
                return@setOnClickListener
            }

            val coverUri = selectedImageUri
            if (coverUri != null && coverUrl.isEmpty()) {
                val f = ImageUtil.uriToFile(this, coverUri)
                if (f == null) {
                    Toast.makeText(this, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                btnCreate.isEnabled = false
                btnCreate.text = "Upload cover..."
                ImgbbUploader.uploadImage(
                    imageFile = f,
                    onSuccess = { url ->
                        runOnUiThread {
                            coverUrl = url
                            btnCreate.isEnabled = true
                            btnCreate.performClick()
                        }
                    },
                    onError = { err ->
                        runOnUiThread {
                            btnCreate.isEnabled = true
                            btnCreate.text = "🎉  Buat Room Sekarang"
                            Toast.makeText(this, "Gagal upload cover: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                return@setOnClickListener
            }

            btnCreate.isEnabled = false
            btnCreate.text = "Membuat room..."

            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { doc ->
                    val ownerName = doc.getString("nickname") ?: "Pengguna HAYU"
                    saveRoom(roomName, roomTopic, ownerName, currentUser.uid, btnCreate)
                }
                .addOnFailureListener {
                    saveRoom(roomName, roomTopic, "Pengguna HAYU", currentUser.uid, btnCreate)
                }
        }
    }

    private fun saveRoom(
        name: String,
        topic: String,
        ownerName: String,
        ownerUid: String,
        btnCreate: TextView
    ) {
        val roomData = hashMapOf(
            "name" to name,
            "topic" to topic,
            "maxMic" to selectedMic,
            "currentUsers" to 1,
            "ownerUid" to ownerUid,
            "ownerName" to ownerName,
            "privacy" to selectedPrivacy,
            "coverColor" to "purple",
            "coverUrl" to coverUrl,
            "active" to true,
            "lastActiveAt" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("rooms").add(roomData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Room berhasil dibuat! 🎉", Toast.LENGTH_SHORT).show()

                // Simpan ke session (untuk floating icon)
                session.setActiveRoomId(documentReference.id)
                session.setActiveRoomName(name)

                val intent = Intent(this, RoomActivity::class.java)
                intent.putExtra("roomId", documentReference.id)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                btnCreate.isEnabled = true
                btnCreate.text = "🎉  Buat Room Sekarang"
                Toast.makeText(this, "Gagal membuat room: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
