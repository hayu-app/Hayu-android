#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch: RED PACKET di room
#  Menu room -> 🧧 Red Packet -> pilih Ruangan / Super, koin, jumlah, waktu tunggu -> Kirim.
#  Penerima di room melihat tombol 🧧 melayang, ketuk untuk ambil (bagian acak).
#  Sisa yang tidak terambil (10 menit) dikembalikan ke pengirim.
#   cd ~/Hayu-android && bash /sdcard/Download/patch-redpacket.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
[ -f "$D/RoomActivity.kt" ] || { echo "❌ RoomActivity.kt tidak ditemukan"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python

echo "▶ 1/3 RedPacket.kt"
cat > "$D/RedPacket.kt" <<'KOTLIN_EOF'
package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

// Struktur Firestore:
// rooms/{roomId}/redPackets/{packetId}
//   senderUid, senderName, type ("room" / "super")
//   totalCoins, count, remainingCoins, remainingCount
//   createdAt, openAt (ms; openAt > createdAt = ada hitung mundur)
//   claims: [ { uid, name, amount } ]
//   refunded: Boolean (sisa koin sudah dikembalikan ke pengirim)

object RedPacketConfig {
    // Red Packet yang tidak habis diambil dalam waktu ini dianggap kedaluwarsa
    const val EXPIRE_MS = 10 * 60 * 1000L

    class Tier(
        val type: String,
        val title: String,
        val coins: List<Long>,
        val counts: List<Int>,
        val delaysSec: List<Int>   // 0 = ambil sekarang
    )

    // Ubah angka di sini kalau mau mengatur nominal / jumlah / waktu tunggu
    val tiers = listOf(
        Tier(
            "room", "Red Packet Ruangan",
            listOf(20_000L, 40_000L, 80_000L, 100_000L, 200_000L),
            listOf(3, 6, 10, 20, 30),
            listOf(0, 300)
        ),
        Tier(
            "super", "Red Packet Super",
            listOf(300_000L, 500_000L, 600_000L, 1_000_000L, 3_000_000L),
            listOf(30, 50, 70, 100, 150),
            listOf(0, 60)
        )
    )

    fun fmt(n: Long): String = when {
        n >= 1_000_000L && n % 1_000_000L == 0L -> "${n / 1_000_000L}M"
        n >= 1_000L && n % 1_000L == 0L -> "${n / 1_000L}K"
        else -> n.toString()
    }
}

data class RedPacket(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val type: String = "room",
    val totalCoins: Long = 0L,
    val count: Int = 0,
    val remainingCoins: Long = 0L,
    val remainingCount: Int = 0,
    val createdAt: Long = 0L,
    val openAt: Long = 0L,
    val refunded: Boolean = false,
    val claims: List<Map<String, Any?>> = emptyList()
) {
    fun claimOf(uid: String): Long? =
        claims.firstOrNull { it["uid"] == uid }?.get("amount").let { (it as? Number)?.toLong() }

    fun hasClaimed(uid: String): Boolean = claims.any { it["uid"] == uid }

    fun isExpired(now: Long): Boolean = now > createdAt + RedPacketConfig.EXPIRE_MS

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromDoc(id: String, m: Map<String, Any?>): RedPacket = RedPacket(
            id = id,
            senderUid = m["senderUid"] as? String ?: "",
            senderName = m["senderName"] as? String ?: "",
            type = m["type"] as? String ?: "room",
            totalCoins = (m["totalCoins"] as? Number)?.toLong() ?: 0L,
            count = (m["count"] as? Number)?.toInt() ?: 0,
            remainingCoins = (m["remainingCoins"] as? Number)?.toLong() ?: 0L,
            remainingCount = (m["remainingCount"] as? Number)?.toInt() ?: 0,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
            openAt = (m["openAt"] as? Number)?.toLong() ?: 0L,
            refunded = m["refunded"] as? Boolean ?: false,
            claims = (m["claims"] as? List<Map<String, Any?>>) ?: emptyList()
        )
    }
}

// ────────────────────────────────────────────
// SHEET KIRIM RED PACKET
// ────────────────────────────────────────────
object RedPacketSheet {

    fun show(activity: AppCompatActivity, roomId: String) {
        val db = FirebaseFirestore.getInstance()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            val coins = me.getLong("coins") ?: 0L
            val dialog = BottomSheetDialog(activity)
            val d = { v: Int -> Ui.dp(activity, v) }

            val root = LinearLayout(activity)
            root.orientation = LinearLayout.VERTICAL
            root.setBackgroundResource(R.drawable.bg_bottom_sheet)
            root.setPadding(d(16), d(16), d(16), d(24))

            val tabRow = LinearLayout(activity)
            tabRow.orientation = LinearLayout.HORIZONTAL
            val body = LinearLayout(activity)
            body.orientation = LinearLayout.VERTICAL
            root.addView(tabRow)
            root.addView(body)

            var tierIdx = 0
            var coinIdx = 0
            var countIdx = 0
            var delayIdx = 0
            var sending = false
            var render: () -> Unit = {}

            render = {
                val tier = RedPacketConfig.tiers[tierIdx]

                tabRow.removeAllViews()
                RedPacketConfig.tiers.forEachIndexed { i, t ->
                    val tv = Ui.text(activity, t.title, 14f, if (i == tierIdx) Ui.GOLD else Ui.GRAY, true)
                    tv.gravity = Gravity.CENTER
                    tv.setPadding(0, d(8), 0, d(12))
                    tv.setOnClickListener {
                        tierIdx = i; coinIdx = 0; countIdx = 0; delayIdx = 0
                        render()
                    }
                    tabRow.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }

                body.removeAllViews()

                body.addView(Ui.text(activity, "Koin", 13f, Ui.GRAY, true))
                body.addView(chipRow(activity, tier.coins.map { RedPacketConfig.fmt(it) }, coinIdx) {
                    coinIdx = it; render()
                })

                val lbl2 = Ui.text(activity, "Jumlah", 13f, Ui.GRAY, true)
                lbl2.setPadding(0, d(14), 0, 0)
                body.addView(lbl2)
                body.addView(chipRow(activity, tier.counts.map { it.toString() }, countIdx) {
                    countIdx = it; render()
                })

                val lbl3 = Ui.text(activity, "Waktu penerimaan", 13f, Ui.GRAY, true)
                lbl3.setPadding(0, d(14), 0, 0)
                body.addView(lbl3)
                body.addView(chipRow(
                    activity,
                    tier.delaysSec.map { if (it == 0) "Ambil sekarang" else "$it dtk" },
                    delayIdx
                ) { delayIdx = it; render() })

                val total = tier.coins[coinIdx]
                val count = tier.counts[countIdx]
                val avg = total / count
                val info = Ui.text(activity, "≈ 🪙$avg per bagian (acak)", 11f, Ui.GRAY)
                info.setPadding(0, d(10), 0, 0)
                body.addView(info)

                val bottom = LinearLayout(activity)
                bottom.orientation = LinearLayout.HORIZONTAL
                bottom.gravity = Gravity.CENTER_VERTICAL
                bottom.setPadding(0, d(16), 0, 0)
                bottom.addView(
                    Ui.text(activity, "🪙 $coins", 16f, Ui.GOLD, true),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )

                val btn = Ui.text(activity, "Kirim 🧧  🪙${RedPacketConfig.fmt(total)}", 14f, Color.WHITE, true)
                btn.gravity = Gravity.CENTER
                btn.setBackgroundResource(R.drawable.bg_button_primary)
                btn.setPadding(d(24), d(12), d(24), d(12))
                btn.setOnClickListener {
                    if (sending) return@setOnClickListener
                    if (coins < total) {
                        Toast.makeText(activity, "Koin tidak cukup", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    sending = true
                    btn.alpha = 0.5f
                    send(activity, roomId, tier.type, total, count, tier.delaysSec[delayIdx],
                        onDone = { dialog.dismiss() },
                        onFail = { sending = false; btn.alpha = 1f })
                }
                bottom.addView(btn)
                body.addView(bottom)
            }
            render()

            dialog.setContentView(root)
            dialog.show()
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun chipRow(
        activity: AppCompatActivity,
        labels: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, Ui.dp(activity, 6), 0, 0)
        labels.forEachIndexed { i, label ->
            val tv = Ui.text(activity, label, 13f, Color.WHITE, true)
            tv.gravity = Gravity.CENTER
            tv.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 10), Ui.dp(activity, 4), Ui.dp(activity, 10))
            tv.setBackgroundResource(if (i == selected) R.drawable.bg_button_primary else R.drawable.bg_quick_item)
            tv.setOnClickListener { onPick(i) }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(Ui.dp(activity, 3), 0, Ui.dp(activity, 3), 0)
            row.addView(tv, lp)
        }
        return row
    }

    private fun send(
        activity: AppCompatActivity,
        roomId: String,
        type: String,
        total: Long,
        count: Int,
        delaySec: Int,
        onDone: () -> Unit,
        onFail: () -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { onFail(); return }
        val meRef = db.collection("users").document(uid)
        val roomRef = db.collection("rooms").document(roomId)
        val pRef = roomRef.collection("redPackets").document()
        val now = System.currentTimeMillis()
        var myName = "Pengguna HAYU"

        db.runTransaction { tx ->
            val me = tx.get(meRef)
            val coins = me.getLong("coins") ?: 0L
            if (coins < total) throw IllegalStateException("Koin tidak cukup")
            myName = me.getString("nickname") ?: "Pengguna HAYU"

            tx.update(meRef, mapOf("coins" to (coins - total)))
            tx.set(
                pRef, hashMapOf<String, Any>(
                    "senderUid" to uid,
                    "senderName" to myName,
                    "type" to type,
                    "totalCoins" to total,
                    "count" to count,
                    "remainingCoins" to total,
                    "remainingCount" to count,
                    "createdAt" to now,
                    "openAt" to (now + delaySec * 1000L),
                    "refunded" to false,
                    "claims" to emptyList<Map<String, Any>>()
                )
            )
            null
        }.addOnSuccessListener {
            val extra = if (delaySec > 0) ", bisa diambil dalam $delaySec dtk" else ""
            val label = if (type == "super") "Red Packet Super" else "Red Packet"
            roomRef.collection("messages").add(
                hashMapOf(
                    "senderUid" to uid,
                    "senderName" to "🧧 RED PACKET",
                    "text" to "$myName mengirim $label 🪙${RedPacketConfig.fmt(total)} ($count bagian$extra). Ketuk 🧧 untuk ambil!",
                    "timestamp" to System.currentTimeMillis()
                )
            )
            onDone()
        }.addOnFailureListener {
            Toast.makeText(activity, "Gagal: ${it.message}", Toast.LENGTH_LONG).show()
            onFail()
        }
    }
}

// ────────────────────────────────────────────
// MANAJER: tombol 🧧 melayang di room + klaim
// ────────────────────────────────────────────
class RedPacketManager(
    private val activity: AppCompatActivity,
    private val roomId: String
) {
    private val db = FirebaseFirestore.getInstance()
    private val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val roomRef = db.collection("rooms").document(roomId)
    private val handler = Handler(Looper.getMainLooper())

    private var reg: ListenerRegistration? = null
    private var badge: View? = null
    private var badgeIcon: TextView? = null
    private var badgeCount: TextView? = null
    private var packets: List<RedPacket> = emptyList()
    private val refunding = HashSet<String>()
    private val claimedIds = HashSet<String>()   // sudah kuambil (langsung disembunyikan)

    private var dialog: BottomSheetDialog? = null
    private var detailRefresh: (() -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            refreshBadge()
            detailRefresh?.invoke()
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (myUid.isEmpty()) return
        addBadge()
        reg = roomRef.collection("redPackets")
            .whereGreaterThan("createdAt", System.currentTimeMillis() - 24L * 60 * 60 * 1000)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                packets = snap.documents.mapNotNull { doc ->
                    doc.data?.let { RedPacket.fromDoc(doc.id, it) }
                }
                refundExpired()
                refreshBadge()
                detailRefresh?.invoke()
            }
        handler.post(ticker)
    }

    fun stop() {
        reg?.remove()
        reg = null
        handler.removeCallbacks(ticker)
        detailRefresh = null
        dialog?.dismiss()
        dialog = null
        badge?.let { (it.parent as? ViewGroup)?.removeView(it) }
        badge = null
        badgeIcon = null
        badgeCount = null
    }

    // ---------- tombol melayang ----------

    private fun addBadge() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val size = Ui.dp(activity, 58)

        val box = FrameLayout(activity)
        box.elevation = Ui.dp(activity, 8).toFloat()
        box.visibility = View.GONE
        box.setOnClickListener { onBadgeClick() }

        val icon = TextView(activity)
        icon.gravity = Gravity.CENTER
        icon.textSize = 11f
        icon.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#EF4444"))
        bg.setStroke(Ui.dp(activity, 2), Color.parseColor("#FBBF24"))
        icon.background = bg
        box.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))

        // angka kecil di kanan atas: jumlah Red Packet yang belum kamu ambil
        val cnt = TextView(activity)
        cnt.gravity = Gravity.CENTER
        cnt.textSize = 11f
        cnt.setTextColor(Color.parseColor("#EF4444"))
        cnt.setTypeface(null, Typeface.BOLD)
        cnt.setPadding(Ui.dp(activity, 5), 0, Ui.dp(activity, 5), 0)
        cnt.minWidth = Ui.dp(activity, 20)
        cnt.minHeight = Ui.dp(activity, 20)
        val cbg = GradientDrawable()
        cbg.shape = GradientDrawable.RECTANGLE
        cbg.cornerRadius = Ui.dp(activity, 20).toFloat()
        cbg.setColor(Color.WHITE)
        cbg.setStroke(Ui.dp(activity, 1), Color.parseColor("#FBBF24"))
        cnt.background = cbg
        cnt.visibility = View.GONE
        box.addView(
            cnt,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        lp.rightMargin = Ui.dp(activity, 8)
        root.addView(box, lp)
        badge = box
        badgeIcon = icon
        badgeCount = cnt
    }

    private fun visiblePackets(now: Long): List<RedPacket> =
        packets.filter { !it.isExpired(now) && it.remainingCount > 0 }

    private fun isDone(p: RedPacket): Boolean = p.hasClaimed(myUid) || claimedIds.contains(p.id)

    private fun refreshBadge() {
        val box = badge ?: return
        val now = System.currentTimeMillis()
        val available = visiblePackets(now).filter { !isDone(it) }
        if (available.isEmpty()) {
            box.visibility = View.GONE
            return
        }
        val first = available.first()
        val wait = first.openAt - now
        badgeIcon?.text = "🧧\n" + if (wait > 0) "${(wait + 999) / 1000}s" else "Ambil"

        // angka kecil di kanan atas ikon jika Red Packet lebih dari 1
        val n = available.size
        badgeCount?.let { c ->
            if (n >= 2) {
                c.text = if (n > 99) "99+" else n.toString()
                c.visibility = View.VISIBLE
            } else {
                c.visibility = View.GONE
            }
        }
        box.visibility = View.VISIBLE
    }

    private fun onBadgeClick() {
        val target = visiblePackets(System.currentTimeMillis()).firstOrNull { !isDone(it) } ?: return
        openDetail(target.id)
    }

    // ---------- detail + klaim ----------

    private fun openDetail(packetId: String) {
        dialog?.dismiss()
        val sheet = BottomSheetDialog(activity)
        val d = { v: Int -> Ui.dp(activity, v) }

        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_bottom_sheet)
        root.setPadding(d(16), d(16), d(16), d(24))

        val tvTitle = Ui.text(activity, "🧧 Red Packet", 18f, Ui.WHITE_SOFT, true)
        val tvStatus = Ui.text(activity, "", 15f, Ui.GOLD, true)
        tvStatus.setPadding(0, d(12), 0, d(8))
        val btn = Ui.text(activity, "Ambil 🧧", 15f, Color.WHITE, true)
        btn.gravity = Gravity.CENTER
        btn.setBackgroundResource(R.drawable.bg_button_primary)
        btn.setPadding(0, d(12), 0, d(12))
        val tvList = Ui.text(activity, "Sudah mengambil", 12f, Ui.GRAY, true)
        tvList.setPadding(0, d(14), 0, d(4))
        val claimsBox = LinearLayout(activity)
        claimsBox.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(activity)
        scroll.addView(claimsBox)

        root.addView(tvTitle)
        root.addView(tvStatus)
        root.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(tvList)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(200)))

        var claiming = false

        val render = {
            val p = packets.firstOrNull { it.id == packetId }
            if (p != null) {
                val now = System.currentTimeMillis()
                val mine = p.claimOf(myUid)
                val wait = p.openAt - now
                tvTitle.text = "🧧 Red Packet dari ${p.senderName}"

                val canClaim = mine == null && p.remainingCount > 0 && !p.isExpired(now) && wait <= 0
                tvStatus.text = when {
                    mine != null -> "Kamu dapat 🪙$mine"
                    p.remainingCount <= 0 -> "Red Packet sudah habis"
                    p.isExpired(now) -> "Red Packet sudah kedaluwarsa"
                    wait > 0 -> "Bisa diambil dalam ${(wait + 999) / 1000} detik"
                    else -> "Ayo ambil sekarang!"
                }
                btn.alpha = if (canClaim && !claiming) 1f else 0.4f

                claimsBox.removeAllViews()
                p.claims.forEach { c ->
                    val row = LinearLayout(activity)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, d(4), 0, d(4))
                    row.addView(
                        Ui.text(activity, (c["name"] as? String) ?: "-", 13f, Ui.WHITE_SOFT),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    row.addView(Ui.text(activity, "🪙${(c["amount"] as? Number)?.toLong() ?: 0L}", 13f, Ui.GOLD, true))
                    claimsBox.addView(row)
                }
            }
        }

        btn.setOnClickListener {
            val p = packets.firstOrNull { it.id == packetId } ?: return@setOnClickListener
            val now = System.currentTimeMillis()
            if (claiming || p.hasClaimed(myUid) || p.remainingCount <= 0 || p.isExpired(now) || now < p.openAt) {
                return@setOnClickListener
            }
            claiming = true
            claim(packetId) { amount, err ->
                claiming = false
                if (amount != null) {
                    claimedIds.add(packetId)
                    Toast.makeText(activity, "🧧 Kamu dapat 🪙$amount!", Toast.LENGTH_LONG).show()
                    refreshBadge()
                    sheet.dismiss()
                } else {
                    Toast.makeText(activity, err ?: "Gagal mengambil", Toast.LENGTH_SHORT).show()
                }
                render()
            }
            render()
        }

        sheet.setOnDismissListener {
            detailRefresh = null
            if (dialog === sheet) dialog = null
        }
        detailRefresh = { render() }
        dialog = sheet
        render()
        sheet.setContentView(root)
        sheet.show()
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun claim(packetId: String, onResult: (Long?, String?) -> Unit) {
        val meRef = db.collection("users").document(myUid)
        val pRef = roomRef.collection("redPackets").document(packetId)

        db.runTransaction { tx ->
            val ps = tx.get(pRef)
            val me = tx.get(meRef)
            if (!ps.exists()) throw IllegalStateException("Red Packet tidak ditemukan")
            val p = RedPacket.fromDoc(ps.id, ps.data ?: emptyMap())
            val now = System.currentTimeMillis()

            if (p.hasClaimed(myUid)) throw IllegalStateException("Kamu sudah mengambil Red Packet ini")
            if (p.remainingCount <= 0) throw IllegalStateException("Red Packet sudah habis")
            if (p.isExpired(now)) throw IllegalStateException("Red Packet sudah kedaluwarsa")
            if (now < p.openAt) throw IllegalStateException("Belum waktunya diambil")

            // bagi acak: bagian terakhir mengambil semua sisa; lainnya acak
            // antara 1 dan 2x rata-rata, sambil menyisakan minimal 1 koin per bagian lain
            val amount: Long = if (p.remainingCount == 1) {
                p.remainingCoins
            } else {
                val maxAmt = minOf(
                    p.remainingCoins - (p.remainingCount - 1),
                    p.remainingCoins * 2 / p.remainingCount
                )
                if (maxAmt <= 1L) 1L else (1L..maxAmt).random()
            }

            val name = me.getString("nickname") ?: "Pengguna HAYU"
            val coins = me.getLong("coins") ?: 0L
            val earned = me.getLong("totalCoinsEarned") ?: 0L
            val newClaims = p.claims + mapOf<String, Any>("uid" to myUid, "name" to name, "amount" to amount)

            tx.update(
                pRef, mapOf(
                    "remainingCoins" to (p.remainingCoins - amount),
                    "remainingCount" to (p.remainingCount - 1),
                    "claims" to newClaims
                )
            )
            tx.update(
                meRef, mapOf(
                    "coins" to (coins + amount),
                    "totalCoinsEarned" to (earned + amount)
                )
            )
            amount
        }.addOnSuccessListener { amount ->
            onResult(amount, null)
        }.addOnFailureListener { e ->
            onResult(null, e.message)
        }
    }

    // Sisa koin Red Packet kedaluwarsa dikembalikan ke pengirim
    // (dijalankan saat pengirim berada di room dalam 24 jam setelah dikirim)
    private fun refundExpired() {
        val now = System.currentTimeMillis()
        for (p in packets) {
            if (p.senderUid != myUid) continue
            if (!p.isExpired(now) || p.refunded || p.remainingCoins <= 0L) continue
            if (!refunding.add(p.id)) continue

            val meRef = db.collection("users").document(myUid)
            val pRef = roomRef.collection("redPackets").document(p.id)
            db.runTransaction { tx ->
                val ps = tx.get(pRef)
                val me = tx.get(meRef)
                val cur = RedPacket.fromDoc(ps.id, ps.data ?: emptyMap())
                if (cur.refunded || cur.remainingCoins <= 0L) return@runTransaction 0L
                val back = cur.remainingCoins
                tx.update(pRef, mapOf("refunded" to true, "remainingCoins" to 0L))
                tx.update(meRef, mapOf("coins" to ((me.getLong("coins") ?: 0L) + back)))
                back
            }.addOnSuccessListener { back ->
                if (back > 0L) {
                    Toast.makeText(activity, "🧧 Sisa Red Packet 🪙$back dikembalikan", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                refunding.remove(p.id)
            }
        }
    }
}
KOTLIN_EOF

echo "▶ 2/3 menu room (sheet_room_menu.xml)"
python3 - <<'PY'
import sys
p = "app/src/main/res/layout/sheet_room_menu.xml"
s = open(p, encoding="utf-8").read()
if "menu_red_packet" in s:
    print("   (sudah ada, dilewati)")
else:
    old = '''        <View
            android:layout_width="0dp"
            android:layout_height="88dp"
            android:layout_weight="1"
            android:layout_marginStart="6dp" />'''
    new = '''        <LinearLayout
            android:id="@+id/menu_red_packet"
            android:layout_width="0dp"
            android:layout_height="88dp"
            android:layout_weight="1"
            android:orientation="vertical"
            android:gravity="center"
            android:background="@drawable/bg_quick_item"
            android:layout_marginStart="6dp"
            android:clickable="true"
            android:focusable="true">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="🧧"
                android:textSize="26sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Red Packet"
                android:textColor="@color/hayu_text"
                android:textSize="11sp"
                android:layout_marginTop="4dp"
                android:gravity="center" />
        </LinearLayout>'''
    if s.count(old) != 1:
        sys.exit("❌ sheet_room_menu.xml: placeholder kosong tidak ditemukan (sudah berubah?)")
    open(p, "w", encoding="utf-8").write(s.replace(old, new))
    print("   OK")
PY

echo "▶ 3/3 RoomActivity.kt"
python3 - <<'PY'
import sys
p = "app/src/main/java/com/hayu/app/RoomActivity.kt"
s = open(p, encoding="utf-8").read()
if "RedPacketManager" in s:
    print("   (sudah ada, dilewati)")
    sys.exit(0)

def rep(s, old, new):
    if s.count(old) != 1:
        sys.exit("❌ RoomActivity.kt: bagian ini tidak ditemukan / ganda:\n" + old)
    return s.replace(old, new)

s = rep(s,
    "    private var micSlotListener: ListenerRegistration? = null\n",
    "    private var micSlotListener: ListenerRegistration? = null\n"
    "    private var redPacketManager: RedPacketManager? = null\n")

s = rep(s,
    "        btnGame.setOnClickListener { GamePanelSheet.show(this) }\n\n        listenMessages()\n",
    "        btnGame.setOnClickListener { GamePanelSheet.show(this) }\n\n        listenMessages()\n\n"
    "        redPacketManager = RedPacketManager(this, roomId!!)\n"
    "        redPacketManager?.start()\n")

s = rep(s,
    "        micSlotListener?.remove()\n",
    "        micSlotListener?.remove()\n        redPacketManager?.stop()\n")

s = rep(s,
    "        sheet.show()\n    }\n\n    private fun openGiftSheet()",
    "        view.findViewById<LinearLayout>(R.id.menu_red_packet).setOnClickListener {\n"
    "            sheet.dismiss()\n"
    "            RedPacketSheet.show(this, roomId!!)\n"
    "        }\n"
    "        sheet.show()\n    }\n\n    private fun openGiftSheet()")

open(p, "w", encoding="utf-8").write(s)
print("   OK")
PY

echo ""
echo "✅ Selesai. Lanjut:"
echo "   git add -A && git commit -m 'Fitur Red Packet' && git pull --rebase && git push"
