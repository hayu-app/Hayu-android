package com.hayu.app

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.adapter.DMMessageAdapter
import com.hayu.app.model.DMMessage
import coil.load

class ChatActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var peerUid: String = ""
    private var peerName: String = "Pengguna HAYU"
    private var peerPhoto: String = ""
    private var chatId: String = ""
    private lateinit var adapter: DMMessageAdapter
    private lateinit var rvMessages: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_dm)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        peerUid = intent.getStringExtra("peerUid") ?: run {
            Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val myUid = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        // Buat chatId yang konsisten (urutkan UID)
        chatId = if (myUid < peerUid) "${myUid}_${peerUid}" else "${peerUid}_${myUid}"

        val btnBack = findViewById<TextView>(R.id.btn_back_chat)
        val imgPeer = findViewById<ImageView>(R.id.img_chat_peer)
        val tvPeerName = findViewById<TextView>(R.id.tv_peer_name)
        val tvPeerStatus = findViewById<TextView>(R.id.tv_peer_status)
        rvMessages = findViewById(R.id.rv_dm_messages)
        val etMessage = findViewById<EditText>(R.id.et_dm_message)
        val btnSend = findViewById<TextView>(R.id.btn_dm_send)

        btnBack.setOnClickListener { finish() }

        adapter = DMMessageAdapter(myUid)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        rvMessages.layoutManager = lm
        rvMessages.adapter = adapter

        // Load data peer
        db.collection("users").document(peerUid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    peerName = doc.getString("nickname") ?: "Pengguna HAYU"
                    peerPhoto = doc.getString("photoUrl") ?: ""
                    val online = doc.getBoolean("online") ?: false

                    tvPeerName.text = peerName
                    tvPeerStatus.text = if (online) "🟢 Online" else "Offline"

                    if (peerPhoto.isNotEmpty()) {
                        imgPeer.load(peerPhoto) { crossfade(true) }
                    }

                    // Pastikan chat document ada
                    ensureChatExists(myUid)
                }
            }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            sendMessage(myUid, text, etMessage)
        }

        listenMessages()
    }

    // Buat document chat kalau belum ada
    private fun ensureChatExists(myUid: String) {
        val chatRef = db.collection("dm_chats").document(chatId)
        chatRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                // Ambil nama saya
                db.collection("users").document(myUid).get()
                    .addOnSuccessListener { myDoc ->
                        val myName = myDoc.getString("nickname") ?: "Pengguna HAYU"
                        val myPhoto = myDoc.getString("photoUrl") ?: ""

                        val chatData = hashMapOf(
                            "user1Uid" to myUid,
                            "user2Uid" to peerUid,
                            "peerName_$myUid" to peerName,
                            "peerPhoto_$myUid" to peerPhoto,
                            "peerName_$peerUid" to myName,
                            "peerPhoto_$peerUid" to myPhoto,
                            "lastMessage" to "",
                            "lastTimestamp" to System.currentTimeMillis(),
                            "lastSenderUid" to ""
                        )
                        chatRef.set(chatData)
                    }
            }
        }
    }

    private fun listenMessages() {
        db.collection("dm_chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .limitToLast(100)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { DMMessage.fromDoc(doc.id, it) }
                }
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            }
    }

    private fun sendMessage(myUid: String, text: String, etMessage: EditText) {
        etMessage.setText("")

        val msgData = hashMapOf(
            "senderUid" to myUid,
            "text" to text,
            "timestamp" to System.currentTimeMillis(),
            "seen" to false
        )

        val chatRef = db.collection("dm_chats").document(chatId)

        // 1. Tambah pesan ke subcollection
        chatRef.collection("messages").add(msgData)

        // 2. Update metadata chat (untuk daftar percakapan)
        chatRef.update(
            mapOf(
                "lastMessage" to text,
                "lastTimestamp" to System.currentTimeMillis(),
                "lastSenderUid" to myUid
            )
        )

        // 3. Kirim notifikasi ke peer (kalau belum ada chat aktif)
        sendMessageNotification(myUid, text)
    }

    private fun sendMessageNotification(myUid: String, text: String) {
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { myDoc ->
                val myName = myDoc.getString("nickname") ?: "Pengguna HAYU"
                val notifData = hashMapOf(
                    "userId" to peerUid,
                    "type" to "message",
                    "fromUid" to myUid,
                    "fromName" to myName,
                    "message" to text.take(50),
                    "read" to false,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("notifications").add(notifData)
            }
    }
}
