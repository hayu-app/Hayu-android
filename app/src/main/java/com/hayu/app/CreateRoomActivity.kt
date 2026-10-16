package com.hayu.app

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

class CreateRoomActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedPrivacy: String = "publik"
    private var selectedMic: Int = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_room)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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

        btnChangeCover.setOnClickListener {
            Toast.makeText(this, "Upload cover segera hadir! 📷", Toast.LENGTH_SHORT).show()
        }

        // Dropdown privasi
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

        // Pilih slot mic
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

        // Tombol buat room
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
            "active" to true,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("rooms").add(roomData)
            .addOnSuccessListener {
                Toast.makeText(this, "Room berhasil dibuat! 🎉", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnCreate.isEnabled = true
                btnCreate.text = "🎉  Buat Room Sekarang"
                Toast.makeText(this, "Gagal membuat room: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
