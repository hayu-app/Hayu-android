package com.hayu.app

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.adapter.RoomMessageAdapter
import com.hayu.app.model.RoomMessage
import com.hayu.app.util.SessionManager

class RoomActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var session: SessionManager
    private var roomId: String? = null
    private var roomName: String = "Room"
    private lateinit var adapter: RoomMessageAdapter
    private lateinit var rvMessages: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        session = SessionManager(this)
        roomId = intent.getStringExtra("roomId")

        if (roomId.isNullOrEmpty()) {
            Toast.makeText(this, "Room tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnBack = findViewById<TextView>(R.id.btn_back)
        val tvRoomName = findViewById<TextView>(R.id.tv_room_name)
        val tvRoomOwner = findViewById<TextView>(R.id.tv_room_owner)
        val tvRoomUsers = findViewById<TextView>(R.id.tv_room_users)
        val btnDice = findViewById<TextView>(R.id.btn_dice)
        rvMessages = findViewById(R.id.rv_messages)
        val etMessage = findViewById<EditText>(R.id.et_message)
        val btnSend = findViewById<TextView>(R.id.btn_send)

        btnBack.setOnClickListener { showLeaveDialog() }

        adapter = RoomMessageAdapter()
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        rvMessages.layoutManager = lm
        rvMessages.adapter = adapter

        db.collection("rooms").document(roomId!!).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Room tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                roomName = doc.getString("name") ?: "Room"
                val ownerName = doc.getString("ownerName") ?: "Pengguna HAYU"
                val currentUsers = (doc.getLong("currentUsers") ?: 1L).toInt()

                tvRoomName.text = roomName
                tvRoomOwner.text = "Owner: $ownerName"
                tvRoomUsers.text = "👤 $currentUsers"

                session.setActiveRoomId(roomId!!)
                session.setActiveRoomName(roomName)

                updateLastActive()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat room", Toast.LENGTH_SHORT).show()
                finish()
            }

        // Slot mic
        val micSlots = listOf(
            R.id.mic_slot_1,
            R.id.mic_slot_2,
            R.id.mic_slot_3,
            R.id.mic_slot_4,
            R.id.mic_slot_5
        )
        micSlots.forEachIndexed { index, id ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, "Slot mic ${index + 1} — fitur voice segera hadir! 🎙️", Toast.LENGTH_SHORT).show()
            }
        }

        // Dadu
        btnDice.setOnClickListener { rollDice() }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            sendMessage(text, etMessage)
        }

        listenMessages()
    }

    private fun showLeaveDialog() {
        val options = arrayOf("🔽  Minimize", "🚪  Keluar Room")
        AlertDialog.Builder(this)
            .setTitle("Keluar dari Room?")
            .setItems(options) { _, which ->
                if (which == 0) {
                    Toast.makeText(this, "Room di-minimize. Tap ikon 🎙️ untuk balik!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    session.clearActiveRoom()
                    Toast.makeText(this, "Kamu keluar dari room", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    private fun rollDice() {
        val roll = (1..6).random()
        val diceIcon = when (roll) {
            1 -> "⚀"
            2 -> "⚁"
            3 -> "⚂"
            4 -> "⚃"
            5 -> "⚄"
            else -> "⚅"
        }

        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val name = userDoc.getString("nickname") ?: "Pengguna HAYU"
                val msg = hashMapOf(
                    "senderUid" to uid,
                    "senderName" to "🎲 DADU",
                    "text" to "$name melempar dadu: $diceIcon ($roll)",
                    "timestamp" to System.currentTimeMillis()
                )
                roomId?.let { id ->
                    db.collection("rooms").document(id)
                        .collection("messages")
                        .add(msg)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateLastActive()
    }

    private fun updateLastActive() {
        roomId?.let { id ->
            db.collection("rooms").document(id)
                .update("lastActiveAt", System.currentTimeMillis())
        }
    }

    private fun listenMessages() {
        roomId?.let { id ->
            db.collection("rooms").document(id)
                .collection("messages")
                .orderBy("timestamp")
                .limitToLast(100)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener
                    val messages = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { RoomMessage.fromDoc(doc.id, it) }
                    }
                    adapter.submitList(messages)
                    if (messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }

    private fun sendMessage(text: String, etMessage: EditText) {
        val uid = auth.currentUser?.uid ?: return
        etMessage.setText("")

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val name = userDoc.getString("nickname") ?: "Pengguna HAYU"
                val msg = hashMapOf(
                    "senderUid" to uid,
                    "senderName" to name,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
                roomId?.let { id ->
                    db.collection("rooms").document(id)
                        .collection("messages")
                        .add(msg)
                        .addOnSuccessListener {
                            updateLastActive()
                        }
                }
            }
    }
}
