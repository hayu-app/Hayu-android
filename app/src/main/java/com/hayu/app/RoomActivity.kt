// file: app/src/main/java/com/hayu/app/RoomActivity.kt
// runtime: Kotlin, Firebase Firestore realtime, Android API 21+
// replace seluruh file ini

package com.hayu.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.hayu.app.adapter.RoomMessageAdapter
import com.hayu.app.model.RoomMessage
import com.hayu.app.util.SessionManager
import com.opensource.svgaplayer.SVGAImageView

// struktur slot mic di Firestore:
// rooms/{roomId}/micSlots/{slotIndex}
//   uid: String       — uid user yang on mic, "" kalau kosong
//   nickname: String  — nama tampil
//   isMuted: Boolean  — muted atau tidak
//   photoUrl: String  — foto profil (tampil di kursi)
//   frameUrl: String  — bingkai SVGA yang dipakai (tampil melingkari foto)

data class MicSlot(
    val uid: String = "",
    val nickname: String = "",
    val isMuted: Boolean = false,
    val photoUrl: String = "",
    val frameUrl: String = ""
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
    private val micNameViews = arrayOfNulls<TextView>(5)          // angka -> nickname saat duduk
    private val micAvatarViews = arrayOfNulls<ShapeableImageView>(5)
    private val micFrameViews = arrayOfNulls<SVGAImageView>(5)
    private val micMuteBadges = arrayOfNulls<TextView>(5)
    private val micPhotoShown = Array(5) { "\u0000" }             // penanda: belum pernah dimuat
    private val micFrameShown = Array(5) { "\u0000" }
    // Chat dimulai kosong saat masuk room (pesan lama tidak ditampilkan)
    private val joinedAt = System.currentTimeMillis() - 1500L
    private var micSlotListener: ListenerRegistration? = null
    private var redPacketManager: RedPacketManager? = null

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
        buildMicSeatOverlays()

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
                        announceCarEntrance(userDoc)
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

        redPacketManager = RedPacketManager(this, roomId!!)
        redPacketManager?.start()
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
        // Ambil foto & bingkai terbaru supaya tampil di kursi untuk semua orang di room
        db.collection("users").document(uid).get()
            .addOnSuccessListener { u ->
                writeMicSlot(
                    slotIndex, uid,
                    u.getString("nickname") ?: myNickname,
                    u.getString("photoUrl") ?: "",
                    u.getString("equippedFrameUrl") ?: ""
                )
            }
            .addOnFailureListener { writeMicSlot(slotIndex, uid, myNickname, "", "") }
    }

    private fun writeMicSlot(slotIndex: Int, uid: String, nick: String, photo: String, frame: String) {
        val slotData = hashMapOf<String, Any>(
            "uid"      to uid,
            "nickname" to nick,
            "isMuted"  to false,
            "photoUrl" to photo,
            "frameUrl" to frame
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
            .set(emptySlotData())
            .addOnSuccessListener {
                Toast.makeText(this, "⬇️ Kamu turun dari mic", Toast.LENGTH_SHORT).show()
                sendSystemMessage("⬇️ $myNickname turun dari slot mic ${slotIndex + 1}")
            }
    }

    private fun kickFromMicSlot(slotIndex: Int, targetNickname: String) {
        db.collection("rooms").document(roomId!!)
            .collection("micSlots").document(slotIndex.toString())
            .set(emptySlotData())
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
                        isMuted  = doc.getBoolean("isMuted") ?: false,
                        photoUrl = doc.getString("photoUrl") ?: "",
                        frameUrl = doc.getString("frameUrl") ?: ""
                    )
                    updateMicSlotView(i)
                }
            }
    }

    private fun emptySlotData() = hashMapOf<String, Any>(
        "uid" to "", "nickname" to "", "isMuted" to false, "photoUrl" to "", "frameUrl" to ""
    )

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    /**
     * Bungkus tiap kursi dengan FrameLayout supaya bisa ditumpuk:
     * lingkaran kursi (lama) -> foto profil -> bingkai SVGA -> lencana mute.
     * Ukuran kursi tidak berubah (60dp); bingkai boleh sedikit keluar dari kotak.
     */
    private fun buildMicSeatOverlays() {
        for (i in 0 until MIC_SLOT_COUNT) {
            val seat = micSlotViews[i] ?: continue
            val column = seat.parent as? LinearLayout ?: continue
            val idx = column.indexOfChild(seat)
            micNameViews[i] = column.getChildAt(idx + 1) as? TextView
            micNameViews[i]?.apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dpi(64)
            }

            column.removeView(seat)
            val wrap = FrameLayout(this)
            wrap.clipChildren = false
            wrap.addView(seat, FrameLayout.LayoutParams(dpi(60), dpi(60), Gravity.CENTER))

            val avatar = ShapeableImageView(this)
            avatar.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(RelativeCornerSize(0.5f)).build()
            avatar.scaleType = ImageView.ScaleType.CENTER_CROP
            avatar.isClickable = false
            avatar.isFocusable = false
            avatar.visibility = android.view.View.GONE
            wrap.addView(avatar, FrameLayout.LayoutParams(dpi(54), dpi(54), Gravity.CENTER))

            val frame = SVGAImageView(this)
            frame.scaleType = ImageView.ScaleType.FIT_CENTER
            frame.isClickable = false
            frame.isFocusable = false
            frame.visibility = android.view.View.GONE
            wrap.addView(frame, FrameLayout.LayoutParams(dpi(78), dpi(78), Gravity.CENTER))

            val mute = TextView(this)
            mute.text = "🔇"
            mute.textSize = 11f
            mute.visibility = android.view.View.GONE
            wrap.addView(mute, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ))

            column.addView(wrap, idx, LinearLayout.LayoutParams(dpi(60), dpi(60)))
            micAvatarViews[i] = avatar
            micFrameViews[i] = frame
            micMuteBadges[i] = mute

            // supaya bingkai yang lebih besar dari kursi tidak terpotong
            var p: ViewParent? = wrap
            while (p is ViewGroup) {
                p.clipChildren = false
                if (p.id == android.R.id.content) break
                p = p.parent
            }
        }
    }

    private fun updateMicSlotView(i: Int) {
        val view = micSlotViews[i] ?: return
        val slot = micSlots[i]
        val label = view.getChildAt(0) as? TextView ?: return
        val name = micNameViews[i]
        val avatar = micAvatarViews[i]
        val frame = micFrameViews[i]
        val mute = micMuteBadges[i]

        if (slot.uid.isEmpty()) {
            // kursi kosong: ikon mic + angka kursi
            label.visibility = android.view.View.VISIBLE
            label.text = "🎤"
            label.textSize = 24f
            view.alpha = 0.6f
            view.setBackgroundResource(R.drawable.bg_seat_empty)
            avatar?.visibility = android.view.View.GONE
            micPhotoShown[i] = "\u0000"
            if (micFrameShown[i] != "") {
                frame?.stopAnimation()
                frame?.setImageDrawable(null)
                frame?.visibility = android.view.View.GONE
                micFrameShown[i] = ""
            }
            mute?.visibility = android.view.View.GONE
            name?.text = (i + 1).toString()
            return
        }

        // kursi terisi: foto + bingkai, dan angka berubah jadi nickname
        view.alpha = 1f
        view.setBackgroundResource(if (slot.isMuted) R.drawable.bg_seat_muted else R.drawable.bg_seat_active)
        label.visibility = android.view.View.GONE
        name?.text = slot.nickname.ifBlank { "…" }
        mute?.visibility = if (slot.isMuted) android.view.View.VISIBLE else android.view.View.GONE

        if (avatar != null) {
            avatar.visibility = android.view.View.VISIBLE
            if (micPhotoShown[i] != slot.photoUrl) {
                micPhotoShown[i] = slot.photoUrl
                if (slot.photoUrl.isNotEmpty()) {
                    avatar.load(slot.photoUrl) { crossfade(true) }
                } else {
                    avatar.setImageResource(R.drawable.ic_profile)
                }
            }
        }
        if (frame != null && micFrameShown[i] != slot.frameUrl) {
            micFrameShown[i] = slot.frameUrl
            if (slot.frameUrl.isEmpty()) {
                frame.stopAnimation()
                frame.setImageDrawable(null)
                frame.visibility = android.view.View.GONE
            } else {
                frame.visibility = android.view.View.VISIBLE
                SvgaPlayer.play(frame, slot.frameUrl, 0)
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
        view.findViewById<LinearLayout>(R.id.menu_red_packet).setOnClickListener {
            sheet.dismiss()
            RedPacketSheet.show(this, roomId!!)
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
        GiftSheet.show(this, myNickname, list) { text, emoji, svgaUrl ->
            sendGiftMessage(text, svgaUrl)
            if (emoji.isNotBlank()) playGiftAnimation(emoji)
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

    private fun sendGiftMessage(text: String, svgaUrl: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val msg = hashMapOf<String, Any>(
            "senderUid"  to uid,
            "senderName" to "🎁 GIFT",
            "text"       to text,
            "timestamp"  to System.currentTimeMillis()
        )
        if (!svgaUrl.isNullOrEmpty()) msg["svgaUrl"] = svgaUrl
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
        micFrameViews.forEach { it?.stopAnimation() }
        redPacketManager?.stop()
        leaveMicIfOn()
        updateLastActive()
    }

    private fun updateLastActive() {
        roomId?.let { id ->
            db.collection("rooms").document(id)
                .update("lastActiveAt", System.currentTimeMillis())
        }
    }

    private var messagesLoaded = false

    /** Putar animasi SVGA (gift / mobil masuk) dari pesan baru milik orang lain. */
    private fun playIncomingSvga(snapshot: QuerySnapshot) {
        val firstLoad = !messagesLoaded
        messagesLoaded = true
        if (firstLoad) return
        val me = auth.currentUser?.uid
        val now = System.currentTimeMillis()
        for (change in snapshot.documentChanges) {
            if (change.type != DocumentChange.Type.ADDED) continue
            val doc = change.document
            val url = doc.getString("svgaUrl") ?: continue
            if (doc.getString("senderUid") == me) continue
            if (now - (doc.getLong("timestamp") ?: 0L) > 15_000L) continue
            SvgaOverlay.show(this, url)
        }
    }

    /** Kalau punya mobil terpasang: putar animasinya & umumkan ke semua orang di room. */
    private fun announceCarEntrance(userDoc: DocumentSnapshot) {
        val url = userDoc.getString("equippedCarUrl") ?: ""
        if (url.isEmpty()) return
        val uid = auth.currentUser?.uid ?: return
        val carName = userDoc.getString("equippedCarName") ?: "kendaraan"
        SvgaOverlay.show(this, url)
        val msg = hashMapOf<String, Any>(
            "senderUid"  to uid,
            "senderName" to "🚗 MASUK",
            "text"       to "$myNickname masuk dengan $carName",
            "timestamp"  to System.currentTimeMillis(),
            "svgaUrl"    to url
        )
        roomId?.let { id ->
            db.collection("rooms").document(id).collection("messages").add(msg)
        }
    }

    private fun listenMessages() {
        roomId?.let { id ->
            db.collection("rooms").document(id)
                .collection("messages")
                .whereGreaterThanOrEqualTo("timestamp", joinedAt)
                .orderBy("timestamp")
                .limitToLast(100)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener
                    playIncomingSvga(snapshot)
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
