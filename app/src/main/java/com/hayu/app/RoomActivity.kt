package com.hayu.app

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class RoomActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var roomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        db = FirebaseFirestore.getInstance()
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
        val btnSend = findViewById<TextView>(R.id.btn_send)

        btnBack.setOnClickListener { finish() }

        // Load data room
        db.collection("rooms").document(roomId!!).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Room tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val name = doc.getString("name") ?: "Room Tanpa Nama"
                val ownerName = doc.getString("ownerName") ?: "Pengguna HAYU"
                val currentUsers = (doc.getLong("currentUsers") ?: 1L).toInt()

                tvRoomName.text = name
                tvRoomOwner.text = "Owner: $ownerName"
                tvRoomUsers.text = "👤 $currentUsers"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat room", Toast.LENGTH_SHORT).show()
                finish()
            }

        // Slot mic — placeholder
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

        // Tombol kirim — placeholder
        btnSend.setOnClickListener {
            Toast.makeText(this, "Chat room segera hadir! 💬", Toast.LENGTH_SHORT).show()
        }
    }
}
