// file: app/src/main/java/com/hayu/app/RoomActivity.kt
// runtime: Kotlin, Firebase Firestore realtime, Android API 21+
// replace seluruh file ini

package com.hayu.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hayu.app.adapter.RoomMessageAdapter
import com.hayu.app.model.RoomMessage
import com.hayu.app.util.SessionManager

// struktur slot mic di Firestore:
// rooms/{roomId}/micSlots/{slotIndex}
//   uid: String       — uid user yang on mic, "" kalau kosong
//   nickname: String  — nama tampil
//   isMuted: Boolean  — muted atau tidak

data class MicSlot(
    val uid: String = "",
    val nickname: String = "",
    val isMuted: Boolean = false
)

class RoomActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var session: SessionManager

    private var roomId: String? = null
    private var roomName: String = "Room"
    private var roomOwnerUid: String = ""
    private var roomOwnerName: String = "Owner"
    private var myNickname: String = "Pengguna HAYU"

    private lateinit var adapter: RoomMessageAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText

    // 5 slot mic — index 0–4 = slot 1–5
    private val micSlots = Array(5) { MicSlot() }
    private val micSlotViews = arrayOfNulls<LinearLayout>(5)
    private var micSlotListener: ListenerRegistration? = null

    private val MIC_SLOT_COUNT = 5
    private val REQ_RECORD_AUDIO = 1001

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

        val btnBack      = findViewById<TextView>(R.id.btn_back)
        val tvRoomName   = findViewById<TextView>(R.id.tv_room_name)
        val tvRoomOwner  = findViewById<TextView>(R.id.tv_room_owner)
        val tvRoomUsers  = findViewById<TextView>(R.id.tv_room_users)
        rvMessages       = findViewById(R.id.rv_messages)
        etMessage        = findViewById(R.id.et_message)
        val btnSend      = findViewById<TextView>(R.id.btn_send)
        val btnVolume    = findViewById<TextView>(R.id.btn_volume)
        val btnNotif     = findViewById<TextView>(R.id.btn_notif)
        val btnMenu      = findViewById<TextView>(R.id.btn_menu)
        val btnGift      = findViewById<TextView>(R.id.btn_gift)
        val btnGame      = findViewById<TextView>(R.id.btn_game)

        // bind mic slot views
        val slotIds = listOf(
            R.id.mic_slot_1, R.id.mic_slot_2, R.id.mic_slot_3,
            R.id.mic_slot_4, R.id.mic_slot_5
        )
        slotIds.forEachIndexed { i, id ->
            micSlotViews[i] = findViewById(id)
        }

        btnBack.setOnClickListener { showLeaveDialog() }

        adapter = RoomMessageAdapter()
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        rvMessages.layoutManager = lm
        rvMessages.adapter = adapter

        // load room info
        db.collection("rooms").document(roomId!!).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { finish(); return@addOnSuccessListener }
                roomName     = doc.getString("name") ?: "Room"
                roomOwnerUid = doc.getString("ownerUid") ?: ""
                val ownerName    = doc.getString("ownerName") ?: "Pengguna HAYU"
                roomOwnerName = ownerName
                val currentUsers = (doc.getLong("currentUsers") ?: 1L).toInt()

                tvRoomName.text  = roomName
                tvRoomOwner.text = "Owner: $ownerName"
                tvRoomUsers.text = "👤 $currentUsers"

                session.setActiveRoomId(roomId!!)
                session.setActiveRoomName(roomName)
                updateLastActive()

                // load nickname lalu setup mic
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { userDoc ->
                        myNickname = userDoc.getString("nickname") ?: "Pengguna HAYU"
                        setupMicSlots()
                        listenMicSlots()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat room", Toast.LENGTH_SHORT).show()
                finish()
            }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            sendMessage(text)
        }

        btnVolume.setOnClickListener {
            toggleMuteMySlot()
        }

        btnNotif.setOnClickListener {
            Toast.makeText(this, "Notifikasi segera hadir! 🔔", Toast.LENGTH_SHORT).show()
        }

        btnMenu.setOnClickListener { showRoomMenu() }

        btnGift.setOnClickListener { openGiftSheet() }
        btnGame.setOnClickListener { GamePanelSheet.show(this) }

        listenMessages()
    }

    // ────────────────────────────────────────────
    // MIC SLOT LOGIC
    // ────────────────────────────────────────────

    private fun setupMicSlots() {
        val uid = auth.currentUser?.uid ?: return
        micSlotViews.forEachIndexed { i, view ->
            view?.setOnClickListener {
                val slot = micSlots[i]
                when {
                    // slot kosong — request naik
                    slot.uid.isEmpty() -> requestMic(i)

                    // slot Sam sendiri — mute toggle atau turun
                    slot.uid == uid -> showMySlotOptions(i)

                    // slot user lain — owner bisa kick
                    uid == roomOwnerUid -> showOwnerKickDialog(i, slot)

                    // tap slot orang lain, bukan owner — tampilkan nama
                    else -> Toast.makeText(
                        this, "🎙️ ${slot.nickname} sedang on mic", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun requestMic(slotIndex: Int) {
        val uid = auth.currentUser?.uid ?: return

        // cek dulu apa Sam udah on mic di slot lain
        val alreadyOnMic = micSlots.any { it.uid == uid }
        if (alreadyOnMic) {
            Toast.makeText(this, "Kamu sudah on mic di slot lain!", Toast.LENGTH_SHORT).show()
            return
        }

        // cek permission mikrofon
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO
            )
            // simpan intent slot untuk dicoba lagi setelah permission granted
            pendingMicSlot = slotIndex
            return
        }

        claimMicSlot(slotIndex, uid)
    }

    private var pendingMicSlot: Int = -1

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (pendingMicSlot >= 0) {
                claimMicSlot(pendingMicSlot, auth.currentUser?.uid ?: return)
                pendingMicSlot = -1
            }
        } else {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk on mic", Toast.LENGTH_SHORT).show()
        }
    }

    private fun claimMicSlot(slotIndex: Int, uid: String) {
        val slotData = hashMapOf(
            "uid"      to uid,
            "nickname" to myNickname,
            "isMuted"  to false
        )
        db.collection("rooms").document(roomId!!)
            .collection("micSlots").document(slotIndex.toString())
            .set(slotData)
            .addOnSuccessListener {
                Toast.makeText(this, "🎙️ Kamu on mic di slot ${slotIndex + 1}!", Toast.LENGTH_SHORT).show()
                // broadcast ke chat
                sendSystemMessage("🎙️ $myNickname naik ke slot mic ${slotIndex + 1}")
            }
    }

    private fun showMySlotOptions(slotIndex: Int) {
        val isMuted = micSlots[slotIndex].isMuted
        val options = arrayOf(
            if (isMuted) "🔊 Unmute" else "🔇 Mute",
            "⬇️ Turun dari Mic"
        )
        AlertDialog.Builder(this)
            .setTitle("Slot Mic ${slotIndex + 1} — Kamu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleMuteSlot(slotIndex, !isMuted)
                    1 -> leaveMicSlot(slotIndex)
                }
            }
            .show()
    }

    private fun showOwnerKickDialog(slotIndex: Int, slot: MicSlot) {
        AlertDialog.Builder(this)
            .setTitle("Kick dari Mic?")
            .setMessage("Keluarkan ${slot.nickname} dari slot mic ${slotIndex + 1}?")
            .setPositiveButton("Kick") { _, _ ->
                kickFromMicSlot(slotIndex, slot.nickname)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleMuteMySlot() {
        val uid = auth.currentUser?.uid ?: return
        val mySlotIndex = micSlots.indexOfFirst { it.uid == uid }
        if (mySlotIndex < 0) {
            Toast.makeText(this, "Kamu belum on mic", Toast.LENGTH_SHORT).show()
            return
        }
        toggleMuteSlot(mySlotIndex, !micSlots[mySlotIndex].isMuted)
    }

    private fun toggleMuteSlot(slotIndex: Int, mute: Boolean) {
        db.collection("rooms").document(roomId!!)
            .collection("micSlots").document(slotIndex.toString())
            .update("isMuted", mute)
            .addOnSuccessListener {
                val msg = if (mute) "🔇 Kamu mute" else "🔊 Kamu unmute"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }

    private fun leaveMicSlot(slotIndex: Int) {
        db.collection("rooms").document(roomId!!)
            .collection("micSlots").document(slotIndex.toString())
            .set(hashMapOf("uid" to "", "nickname" to "", "isMuted" to false))
            .addOnSuccessListener {
                Toast.makeText(this, "⬇️ Kamu turun dari mic", Toast.LENGTH_SHORT).show()
                sendSystemMessage("⬇️ $myNickname turun dari slot mic ${slotIndex + 1}")
            }
    }

    private fun kickFromMicSlot(slotIndex: Int, targetNickname: String) {
        db.collection("rooms").document(roomId!!)
            .collection("micSlots").document(slotIndex.toString())
            .set(hashMapOf("uid" to "", "nickname" to "", "isMuted" to false))
            .addOnSuccessListener {
                Toast.makeText(this, "👢 $targetNickname di-kick dari mic", Toast.LENGTH_SHORT).show()
                sendSystemMessage("👢 $targetNickname di-kick dari slot mic ${slotIndex + 1} oleh owner")
            }
    }

    // listen perubahan mic slot realtime
    private fun listenMicSlots() {
        micSlotListener = db.collection("rooms").document(roomId!!)
            .collection("micSlots")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val i = doc.id.toIntOrNull() ?: continue
                    if (i < 0 || i >= MIC_SLOT_COUNT) continue
                    micSlots[i] = MicSlot(
                        uid      = doc.getString("uid") ?: "",
                        nickname = doc.getString("nickname") ?: "",
                        isMuted  = doc.getBoolean("isMuted") ?: false
                    )
                    updateMicSlotView(i)
                }
            }
    }

    private fun updateMicSlotView(i: Int) {
        val view = micSlotViews[i] ?: return
        val slot = micSlots[i]
        val label = view.getChildAt(0) as? TextView ?: return

        when {
            slot.uid.isEmpty() -> {
                label.text = "🎤"
                label.textSize = 24f
                view.alpha = 0.6f
                view.setBackgroundResource(R.drawable.bg_seat_empty)
            }
            slot.isMuted -> {
                label.text = "🔇\n${slot.nickname.take(6)}"
                label.textSize = 11f
                view.alpha = 1f
                view.setBackgroundResource(R.drawable.bg_seat_muted)
            }
            else -> {
                label.text = "🎙️\n${slot.nickname.take(6)}"
                label.textSize = 11f
                view.alpha = 1f
                view.setBackgroundResource(R.drawable.bg_seat_active)
            }
        }
    }

    // ────────────────────────────────────────────
    // SYSTEM MESSAGE
    // ────────────────────────────────────────────

    private fun sendSystemMessage(text: String) {
        val msg = hashMapOf(
            "senderUid"  to "SYSTEM",
            "senderName" to "🔔 Sistem",
            "text"       to text,
            "timestamp"  to System.currentTimeMillis()
        )
        roomId?.let { id ->
            db.collection("rooms").document(id)
                .collection("messages").add(msg)
        }
    }

    // ────────────────────────────────────────────
    // SISANYA SAMA — chat, menu, dice, dll
    // ────────────────────────────────────────────

    private fun showRoomMenu() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.sheet_room_menu, null)
        sheet.setContentView(view)

        view.findViewById<LinearLayout>(R.id.menu_dice).setOnClickListener {
            sheet.dismiss(); rollDice()
        }
        view.findViewById<LinearLayout>(R.id.menu_vote).setOnClickListener {
            sheet.dismiss()
            Toast.makeText(this, "Fitur Vote segera hadir! 🗳️", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<LinearLayout>(R.id.menu_undian).setOnClickListener {
            sheet.dismiss()
            Toast.makeText(this, "Fitur Undian segera hadir! ❓", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<LinearLayout>(R.id.menu_clear_chat).setOnClickListener {
            sheet.dismiss(); clearChat()
        }
        view.findViewById<LinearLayout>(R.id.menu_settings).setOnClickListener {
            sheet.dismiss()
            Toast.makeText(this, "Pengaturan room segera hadir! ⚙️", Toast.LENGTH_SHORT).show()
        }
        sheet.show()
    }

    private fun openGiftSheet() {
        val me = auth.currentUser?.uid ?: return
        val list = ArrayList<Pair<String, String>>()
        if (roomOwnerUid.isNotEmpty() && roomOwnerUid != me) {
            list.add(Pair(roomOwnerUid, "$roomOwnerName 👑"))
        }
        for (s in micSlots) {
            if (s.uid.isNotEmpty() && s.uid != me && list.none { it.first == s.uid }) {
                list.add(Pair(s.uid, s.nickname))
            }
        }
        list.add(Pair(me, "Saya"))
        GiftSheet.show(this, myNickname, list) { text, emoji ->
            sendGiftMessage(text)
            playGiftAnimation(emoji)
        }
    }

    private fun playGiftAnimation(emoji: String) {
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val tv = TextView(this)
        tv.text = emoji
        tv.textSize = 72f
        tv.alpha = 0f
        tv.scaleX = 0.3f
        tv.scaleY = 0.3f
        val lp = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = android.view.Gravity.CENTER
        root.addView(tv, lp)
        tv.animate().alpha(1f).scaleX(1.4f).scaleY(1.4f).setDuration(450).withEndAction {
            tv.animate().alpha(0f).translationY(-200f).setDuration(600).withEndAction {
                root.removeView(tv)
            }.start()
        }.start()
    }

    private fun sendGiftMessage(text: String) {
        val uid = auth.currentUser?.uid ?: return
        val msg = hashMapOf(
            "senderUid"  to uid,
            "senderName" to "🎁 GIFT",
            "text"       to text,
            "timestamp"  to System.currentTimeMillis()
        )
        roomId?.let { id ->
            db.collection("rooms").document(id).collection("messages").add(msg)
        }
    }

    private fun clearChat() {
        AlertDialog.Builder(this)
            .setTitle("Bersihkan Chat?")
            .setMessage("Semua pesan di room ini akan dihapus. Lanjutkan?")
            .setPositiveButton("Ya, Hapus") { _, _ ->
                roomId?.let { id ->
                    db.collection("rooms").document(id).collection("messages")
                        .get()
                        .addOnSuccessListener { snap ->
                            snap.documents.forEach { it.reference.delete() }
                            Toast.makeText(this, "Chat dibersihkan! 🧹", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun rollDice() {
        val roll     = (1..6).random()
        val diceIcon = listOf("⚀","⚁","⚂","⚃","⚄","⚅")[roll - 1]
        val uid      = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val name = userDoc.getString("nickname") ?: "Pengguna HAYU"
                val msg  = hashMapOf(
                    "senderUid"  to uid,
                    "senderName" to "🎲 DADU",
                    "text"       to "$name melempar dadu: $diceIcon ($roll)",
                    "timestamp"  to System.currentTimeMillis()
                )
                roomId?.let { id ->
                    db.collection("rooms").document(id).collection("messages").add(msg)
                }
            }
    }

    private fun showLeaveDialog() {
        val options = arrayOf("🔽  Minimize", "🚪  Keluar Room")
        AlertDialog.Builder(this)
            .setTitle("Keluar dari Room?")
            .setItems(options) { _, which ->
                if (which == 0) {
                    Toast.makeText(this, "Room di-minimize.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    leaveMicIfOn()
                    session.clearActiveRoom()
                    Toast.makeText(this, "Kamu keluar dari room", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    private fun leaveMicIfOn() {
        val uid = auth.currentUser?.uid ?: return
        val mySlotIndex = micSlots.indexOfFirst { it.uid == uid }
        if (mySlotIndex >= 0) leaveMicSlot(mySlotIndex)
    }

    override fun onDestroy() {
        super.onDestroy()
        micSlotListener?.remove()
        leaveMicIfOn()
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
                    if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.size - 1)
                }
        }
    }

    private fun sendMessage(text: String) {
        val uid = auth.currentUser?.uid ?: return
        etMessage.setText("")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val name = userDoc.getString("nickname") ?: "Pengguna HAYU"
                val msg  = hashMapOf(
                    "senderUid"  to uid,
                    "senderName" to name,
                    "text"       to text,
                    "timestamp"  to System.currentTimeMillis()
                )
                roomId?.let { id ->
                    db.collection("rooms").document(id)
                        .collection("messages").add(msg)
                        .addOnSuccessListener { updateLastActive() }
                }
            }
    }
}
