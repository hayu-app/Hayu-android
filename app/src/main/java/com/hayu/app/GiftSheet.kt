package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import kotlin.random.Random

object GiftSheet {

    /**
     * kind: "normal" | "lucky" (cashback sampai x500) | "cp" (tambah poin keharmonisan CP/BFF)
     * bond (khusus cp): "cp" | "bff" | "both"
     */
    private class Gift(
        val emoji: String,
        val name: String,
        val price: Int,
        val svgaUrl: String? = null,
        val kind: String = "normal",
        val bond: String = "",
        val iconUrl: String = ""
    ) {
        /** Poin keharmonisan yang didapat per 1 hadiah CP/BFF. */
        val points: Int get() = (price / 10).coerceAtLeast(1)
    }

    private val baseGifts = listOf(
        Gift("🌹", "Mawar", 10),
        Gift("☕", "Kopi", 20),
        Gift("🍫", "Cokelat", 30),
        Gift("💖", "Hati", 50),
        Gift("🎂", "Kue", 100),
        Gift("🚀", "Roket", 200),
        Gift("👑", "Mahkota", 500),
        Gift("💎", "Berlian", 1000)
    )

    private val luckyGifts = listOf(
        Gift("🍀", "Golden Luck", 200, kind = "lucky"),
        Gift("👍", "Like", 200, kind = "lucky"),
        Gift("🎟️", "Lottery", 500, kind = "lucky"),
        Gift("💰", "Shagun Coins", 500, kind = "lucky"),
        Gift("🧧", "Pocket", 1000, kind = "lucky"),
        Gift("💵", "Money Gang", 1000, kind = "lucky"),
        Gift("🪙", "Flashing Coin", 2000, kind = "lucky"),
        Gift("👑", "Crown", 3000, kind = "lucky")
    )

    private val cpGifts = listOf(
        Gift("🍔", "Good Friends", 300, kind = "cp", bond = "bff"),
        Gift("🌹", "Golden Rose", 600, kind = "cp", bond = "cp"),
        Gift("💍", "Gold Ring", 800, kind = "cp", bond = "cp"),
        Gift("🫖", "Golden Jug", 600, kind = "cp", bond = "both"),
        Gift("🧸", "Teddy Love", 1000, kind = "cp", bond = "both"),
        Gift("🎈", "Hot Air Balloon", 2000, kind = "cp", bond = "cp"),
        Gift("💋", "Love Kiss", 3000, kind = "cp", bond = "cp"),
        Gift("🚢", "Night Voyage", 5000, kind = "cp", bond = "both")
    )

    /**
     * Peluang cashback Lucky Gift: pasangan (pengali, bobot). Pengali dikali harga 1 hadiah.
     * Bawaan: kembali rata-rata sekitar x0.70 dari harga, maksimal x500.
     * Bisa diganti tanpa update app lewat Firestore app_config/lucky_gift -> "table": [{x: 5, w: 250}, ...]
     */
    object LuckyOdds {
        const val MAX_X = 500
        val DEFAULT = listOf(
            0 to 7324, 1 to 1800, 2 to 500, 5 to 250, 10 to 100, 50 to 20, 100 to 5, 500 to 1
        )
        var table: List<Pair<Int, Int>> = DEFAULT

        fun load(db: FirebaseFirestore, done: () -> Unit) {
            db.collection("app_config").document("lucky_gift").get()
                .addOnSuccessListener { doc ->
                    val parsed = (doc.get("table") as? List<*>)?.mapNotNull { row ->
                        val m = row as? Map<*, *> ?: return@mapNotNull null
                        val x = (m["x"] as? Number)?.toInt() ?: return@mapNotNull null
                        val w = (m["w"] as? Number)?.toInt() ?: return@mapNotNull null
                        if (x < 0 || w <= 0) null else Pair(minOf(x, MAX_X), w)
                    }
                    table = if (parsed.isNullOrEmpty()) DEFAULT else parsed
                    done()
                }
                .addOnFailureListener { table = DEFAULT; done() }
        }

        fun roll(): Int {
            val total = table.sumOf { it.second }
            var r = Random.nextInt(total)
            for ((x, w) in table) {
                if (r < w) return x
                r -= w
            }
            return 0
        }

        fun describe(): String {
            val total = table.sumOf { it.second }.toDouble()
            val sb = StringBuilder()
            for ((x, w) in table.sortedBy { it.first }) {
                sb.append(String.format(Locale.US, "x%d  →  %.2f%%\n", x, w * 100.0 / total))
            }
            val avg = table.sumOf { it.first.toDouble() * it.second } / total
            sb.append(String.format(Locale.US, "\nRata-rata cashback: x%.2f dari harga hadiah", avg))
            return sb.toString()
        }
    }

    // baseGifts + gift SVGA unggahan Developer (diisi ulang tiap sheet dibuka)
    private var gifts: List<Gift> = baseGifts

    // recipients: pasangan (uid, nama tampil). Boleh berisi uid sendiri.
    // onSent(teks pesan, emoji hadiah, url SVGA atau null)
    fun show(
        activity: AppCompatActivity,
        myNickname: String,
        recipients: List<Pair<String, String>>,
        roomId: String? = null,
        onSent: (String, String, String?) -> Unit
    ) {
        SvgaCatalog.load(SvgaCatalog.GIFT) { assets ->
            gifts = baseGifts + assets.map {
                Gift(it.emoji.ifBlank { "🎁" }, it.name, it.price, it.url, iconUrl = it.iconUrl)
            }
            LuckyOdds.load(FirebaseFirestore.getInstance()) {
                showInternal(activity, myNickname, recipients, roomId, onSent)
            }
        }
    }

    private fun showInternal(
        activity: AppCompatActivity,
        myNickname: String,
        recipients: List<Pair<String, String>>,
        roomId: String?,
        onSent: (String, String, String?) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (recipients.isEmpty()) return

        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            val coins = me.getLong("coins") ?: 0L
            val dialog = BottomSheetDialog(activity)
            val d = { v: Int -> Ui.dp(activity, v) }

            val root = LinearLayout(activity)
            root.orientation = LinearLayout.VERTICAL
            root.setBackgroundResource(R.drawable.bg_bottom_sheet)
            root.setPadding(d(16), d(16), d(16), d(16))

            root.addView(Ui.text(activity, "🎁 Kirim Hadiah", 18f, Ui.WHITE_SOFT, true))
            root.addView(Ui.text(activity, "Kirim ke:", 12f, Ui.GRAY))

            // ---- penerima (multi-select) ----
            // selectedUids: set uid yang dipilih. Default: semua kecuali diri sendiri.
            val onMicUids = recipients.filter { it.first != myUid }.map { it.first }.toSet()
            val selectedUids = onMicUids.toMutableSet()
            if (selectedUids.isEmpty()) selectedUids.add(myUid) // fallback kalau sendiri

            val chips = ArrayList<Pair<TextView, String>>()

            val refreshChips = {
                for ((tv, uid) in chips) {
                    tv.setBackgroundResource(
                        if (uid in selectedUids) R.drawable.bg_button_primary else R.drawable.bg_quick_item
                    )
                    tv.alpha = if (uid in selectedUids) 1f else 0.5f
                }
            }

            val chipRow = LinearLayout(activity)
            chipRow.orientation = LinearLayout.HORIZONTAL

            // Tombol "Semua Mic" di posisi pertama
            val allMicBtn = Ui.text(activity, "👥 Semua Mic", 12f, Color.WHITE, true)
            allMicBtn.setPadding(d(12), d(6), d(12), d(6))
            allMicBtn.setBackgroundResource(R.drawable.bg_button_primary)
            allMicBtn.setOnClickListener {
                selectedUids.clear()
                selectedUids.addAll(onMicUids)
                if (selectedUids.isEmpty()) selectedUids.add(myUid)
                refreshChips()
            }
            val allLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            allLp.rightMargin = d(8)
            chipRow.addView(allMicBtn, allLp)

            for ((uid, name) in recipients) {
                val label = if (uid == myUid) "👤 Saya" else name.take(16)
                val tv = Ui.text(activity, label, 12f, Color.WHITE, true)
                tv.setPadding(d(12), d(6), d(12), d(6))
                tv.setOnClickListener {
                    if (uid in selectedUids) {
                        if (selectedUids.size > 1) selectedUids.remove(uid)
                        // minimal 1 penerima tetap dipilih
                    } else {
                        selectedUids.add(uid)
                    }
                    refreshChips()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = d(8)
                chipRow.addView(tv, lp)
                chips.add(Pair(tv, uid))
            }
            refreshChips()
            val hs = HorizontalScrollView(activity)
            hs.addView(chipRow)
            val hsLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            hsLp.topMargin = d(6)
            hsLp.bottomMargin = d(8)
            root.addView(hs, hsLp)

            // ---- tab kategori ----
            var tab = "normal"
            var selectedGift: Gift = gifts[0]
            val tiles = ArrayList<Pair<LinearLayout, Gift>>()
            val tabViews = ArrayList<Pair<TextView, String>>()
            val tabRow = LinearLayout(activity)
            tabRow.orientation = LinearLayout.HORIZONTAL

            val info = Ui.text(activity, "", 11f, Color.parseColor("#FFB4C8"))
            info.setPadding(d(4), d(8), d(4), d(4))
            info.visibility = android.view.View.GONE

            val gridBox = LinearLayout(activity)
            gridBox.orientation = LinearLayout.VERTICAL

            fun refreshTiles() {
                for ((t, g) in tiles) {
                    t.setBackgroundResource(
                        if (g === selectedGift) R.drawable.bg_gift_tile_selected else R.drawable.bg_gift_tile
                    )
                }
            }

            fun renderGrid() {
                for ((tv, key) in tabViews) {
                    val on = key == tab
                    tv.setTextColor(if (on) Color.WHITE else Ui.GRAY)
                    tv.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
                    tv.textSize = if (on) 16f else 14f
                }
                when (tab) {
                    "lucky" -> {
                        info.visibility = android.view.View.VISIBLE
                        info.text = "🍀 Cashback hingga x${LuckyOdds.MAX_X} dari harga hadiah • ketuk untuk lihat peluang ›"
                        info.setOnClickListener {
                            AlertDialog.Builder(activity)
                                .setTitle("Peluang Cashback")
                                .setMessage(LuckyOdds.describe())
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                    "cp" -> {
                        info.visibility = android.view.View.VISIBLE
                        info.text = "💕 Jika berstatus CP/BFF dengan penerima, hadiah ini menambah Poin Keharmonisan"
                        info.setOnClickListener(null)
                        info.isClickable = false
                    }
                    else -> {
                        info.visibility = android.view.View.GONE
                        info.setOnClickListener(null)
                        info.isClickable = false
                    }
                }

                val list = when (tab) {
                    "lucky" -> luckyGifts
                    "cp" -> cpGifts
                    else -> gifts
                }
                if (selectedGift !in list) selectedGift = list[0]

                gridBox.removeAllViews()
                tiles.clear()
                var i = 0
                while (i < list.size) {
                    val line = LinearLayout(activity)
                    line.orientation = LinearLayout.HORIZONTAL
                    for (j in i until i + 4) {
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        lp.setMargins(d(3), d(3), d(3), d(3))
                        if (j >= list.size) {
                            line.addView(TextView(activity), lp)
                            continue
                        }
                        val g = list[j]
                        val tile = LinearLayout(activity)
                        tile.orientation = LinearLayout.VERTICAL
                        tile.gravity = Gravity.CENTER_HORIZONTAL
                        tile.setPadding(d(4), d(10), d(4), d(10))
                        tile.addView(
                            if (g.iconUrl.isBlank()) Ui.text(activity, g.emoji, 30f)
                            else Ui.iconView(activity, g.emoji, g.iconUrl, 40, 30f)
                        )
                        tile.addView(Ui.text(activity, g.name, 11f, Ui.WHITE_SOFT, true))
                        tile.addView(Ui.text(activity, "🪙 ${g.price}", 10f, Ui.GOLD))
                        when (g.kind) {
                            "lucky" -> tile.addView(Ui.text(activity, "🍀 s/d x${LuckyOdds.MAX_X}", 9f, Color.parseColor("#7CF29A")))
                            "cp" -> tile.addView(Ui.text(activity, "💗 +${g.points}", 9f, Color.parseColor("#FF8FB1")))
                        }
                        tile.setOnClickListener {
                            selectedGift = g
                            refreshTiles()
                        }
                        line.addView(tile, lp)
                        tiles.add(Pair(tile, g))
                    }
                    gridBox.addView(line)
                    i += 4
                }
                refreshTiles()
            }

            for ((key, label) in listOf("normal" to "Hadiah", "lucky" to "Lucky Gift", "cp" to "CP/BFF")) {
                val tv = Ui.text(activity, label, 14f, Ui.GRAY, true)
                tv.setPadding(d(4), d(6), d(4), d(6))
                tv.setOnClickListener {
                    tab = key
                    renderGrid()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = d(20)
                tabRow.addView(tv, lp)
                tabViews.add(Pair(tv, key))
            }
            root.addView(tabRow)
            root.addView(info)

            val sv = ScrollView(activity)
            sv.addView(gridBox)
            root.addView(
                sv,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(300)).apply { topMargin = d(6) }
            )
            renderGrid()

            // ---- bar bawah: saldo, jumlah, kirim ----
            var qty = 1
            val bar = LinearLayout(activity)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.gravity = Gravity.CENTER_VERTICAL
            val barLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            barLp.topMargin = d(14)

            bar.addView(
                Ui.text(activity, "🪙 $coins", 16f, Ui.GOLD, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            val qtyBtn = Ui.button(activity, "⌄ 1") {}
            qtyBtn.setBackgroundResource(R.drawable.bg_quick_item)
            qtyBtn.setOnClickListener { anchor ->
                val options = listOf(7777, 777, 555, 77, 17, 7, 1)
                val itemH = d(46)
                val popW = d(140)
                val box = LinearLayout(activity)
                box.orientation = LinearLayout.VERTICAL
                val popup = android.widget.PopupWindow(box, popW, itemH * options.size)
                popup.isOutsideTouchable = true
                popup.isFocusable = true
                popup.setBackgroundDrawable(
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor("#F2000000"))
                        cornerRadius = d(10).toFloat()
                        setStroke(d(1), Color.parseColor("#33FFFFFF"))
                    }
                )
                options.forEachIndexed { idx, n ->
                    val row = TextView(activity)
                    row.text = n.toString()
                    row.textSize = if (n == qty) 22f else 20f
                    row.setTypeface(null, Typeface.BOLD)
                    row.setTextColor(if (n == qty) Color.parseColor("#FF8A65") else Color.WHITE)
                    row.gravity = Gravity.CENTER
                    row.setOnClickListener {
                        qty = n
                        qtyBtn.text = "⌄ $qty"
                        popup.dismiss()
                    }
                    box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemH))
                    if (idx < options.size - 1) {
                        val line = android.view.View(activity)
                        line.setBackgroundColor(Color.parseColor("#22FFFFFF"))
                        box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
                    }
                }
                qtyBtn.text = "⌃ $qty"
                popup.setOnDismissListener { qtyBtn.text = "⌄ $qty" }
                popup.showAsDropDown(anchor, 0, -(itemH * options.size + anchor.height))
            }
            val qtyLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            qtyLp.rightMargin = d(8)
            bar.addView(qtyBtn, qtyLp)

            val sendBtn = Ui.button(activity, "Kirim") {}
            sendBtn.setOnClickListener {
                val g = selectedGift
                val targets = recipients.filter { it.first in selectedUids }
                val total = g.price.toLong() * qty * targets.size
                if (coins < total) {
                    Toast.makeText(activity, "Koin tidak cukup (butuh $total 🪙)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sendBtn.isEnabled = false
                var doneCount = 0
                var failCount = 0
                val texts = ArrayList<String>()
                val totalTargets = targets.size

                fun checkAllDone() {
                    if (doneCount + failCount < totalTargets) return
                    dialog.dismiss()
                    val combined = texts.joinToString(" | ")
                    onSent(combined, if (g.svgaUrl != null) "" else g.emoji, g.svgaUrl)
                }

                for (to in targets) {
                    send(activity, db, myUid, myNickname, to, g, qty, roomId,
                        onDone = { text ->
                            texts.add(text)
                            doneCount++
                            checkAllDone()
                        },
                        onFail = {
                            failCount++
                            if (failCount == totalTargets) sendBtn.isEnabled = true
                            checkAllDone()
                        }
                    )
                }
            }
            bar.addView(sendBtn)
            root.addView(bar, barLp)

            dialog.setContentView(root)
            dialog.show()
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }.addOnFailureListener {
            Toast.makeText(activity, "Gagal memuat koin", Toast.LENGTH_SHORT).show()
        }
    }

    /** Cari ikatan CP/BFF aktif antara pengirim & penerima yang cocok dengan hadiah. Null kalau tidak ada. */
    private fun findBond(
        db: FirebaseFirestore, myUid: String, toUid: String, g: Gift, cb: (String?) -> Unit
    ) {
        db.collection("bonds").whereArrayContains("uids", myUid).get()
            .addOnSuccessListener { snap ->
                val match: DocumentSnapshot? = snap.documents
                    .filter { b ->
                        b.getString("status") == "active" &&
                            (b.get("uids") as? List<*>)?.contains(toUid) == true &&
                            (g.bond == "both" || b.getString("type") == g.bond)
                    }
                    .sortedBy { if (it.getString("type") == "cp") 0 else 1 }
                    .firstOrNull()
                cb(match?.id)
            }
            .addOnFailureListener { cb(null) }
    }

    private fun send(
        activity: AppCompatActivity,
        db: FirebaseFirestore,
        myUid: String,
        myName: String,
        to: Pair<String, String>,
        g: Gift,
        qty: Int,
        roomId: String?,
        onDone: (String) -> Unit,
        onFail: () -> Unit
    ) {
        val isSelf = to.first == myUid
        if (g.kind == "cp" && !isSelf) {
            findBond(db, myUid, to.first, g) { bondId ->
                doSend(activity, db, myUid, myName, to, g, qty, roomId, bondId, onDone, onFail)
            }
        } else {
            doSend(activity, db, myUid, myName, to, g, qty, roomId, null, onDone, onFail)
        }
    }

    private fun doSend(
        activity: AppCompatActivity,
        db: FirebaseFirestore,
        myUid: String,
        myName: String,
        to: Pair<String, String>,
        g: Gift,
        qty: Int,
        roomId: String?,
        bondId: String?,
        onDone: (String) -> Unit,
        onFail: () -> Unit
    ) {
        val meRef = db.collection("users").document(myUid)
        val toRef = db.collection("users").document(to.first)
        val bondRef = bondId?.let { db.collection("bonds").document(it) }
        val pointsRef = roomId?.let {
            db.collection("rooms").document(it).collection("giftPoints").document(myUid)
        }
        val isSelf = to.first == myUid
        val total = g.price.toLong() * qty
        val pts = g.points.toLong() * qty

        // Cashback diundi SEKALI di luar transaksi (transaksi bisa diulang), tiap hadiah diundi sendiri.
        val rolls = if (g.kind == "lucky") List(qty) { LuckyOdds.roll() } else emptyList()
        val cashback = rolls.sumOf { it.toLong() * g.price }
        val bestX = rolls.maxOrNull() ?: 0

        db.runTransaction { tx ->
            // semua baca dulu
            val me = tx.get(meRef)
            val rc = if (isSelf) null else tx.get(toRef)
            val bond = bondRef?.let { tx.get(it) }

            val coins = me.getLong("coins") ?: 0L
            if (coins < total) throw IllegalStateException("Koin tidak cukup")
            val exp = (me.getLong("exp") ?: 0L) + total / 10
            val share = total / 2
            val level = Ui.levelForExp(exp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toLong()
            val sent = (me.getLong("giftsSent") ?: 0L) + qty

            val myUpdate = HashMap<String, Any>()
            if (isSelf) {
                // kirim ke diri sendiri: satu dokumen saja
                myUpdate["coins"] = coins - total + share + cashback
                myUpdate["totalCoinsEarned"] = (me.getLong("totalCoinsEarned") ?: 0L) + share
                myUpdate["giftsReceived"] = (me.getLong("giftsReceived") ?: 0L) + qty
            } else {
                myUpdate["coins"] = coins - total + cashback
            }
            myUpdate["exp"] = exp
            myUpdate["level"] = level
            myUpdate["giftsSent"] = sent
            if (cashback > 0L) myUpdate["luckyCashbackTotal"] = FieldValue.increment(cashback)
            tx.update(meRef, myUpdate)

            if (rc != null) {
                tx.update(
                    toRef, mapOf(
                        "coins" to ((rc.getLong("coins") ?: 0L) + share),
                        "totalCoinsEarned" to ((rc.getLong("totalCoinsEarned") ?: 0L) + share),
                        "giftsReceived" to ((rc.getLong("giftsReceived") ?: 0L) + qty)
                    )
                )
            }

            // poin gift di room = total koin yang dikirim
            if (pointsRef != null) {
                tx.set(
                    pointsRef,
                    mapOf(
                        "uid" to myUid,
                        "nickname" to myName,
                        "points" to FieldValue.increment(total),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
            }

            // poin keharmonisan CP/BFF
            var applied = false
            if (bondRef != null && bond != null && bond.getString("status") == "active") {
                tx.update(bondRef, "points", FieldValue.increment(pts))
                applied = true
            }
            applied
        }.addOnSuccessListener { applied ->
            val svga = g.svgaUrl
            if (svga != null) SvgaOverlay.show(activity, svga)
            else GiftAnimation.show(activity, g.emoji, g.name)

            val sb = StringBuilder()
            if (isSelf) {
                sb.append("$myName mengirim ${g.emoji} ${g.name} x$qty untuk dirinya sendiri")
            } else {
                sb.append("$myName mengirim ${g.emoji} ${g.name} x$qty ke ${to.second}")
            }

            if (g.kind == "lucky") {
                if (cashback > 0L) {
                    sb.append(" 🍀 cashback x$bestX (+$cashback koin)")
                    Toast.makeText(activity, "🍀 Cashback x$bestX! +$cashback koin", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "Belum beruntung, coba lagi 🍀", Toast.LENGTH_SHORT).show()
                }
            }
            if (g.kind == "cp") {
                if (applied == true) {
                    sb.append(" 💗 +$pts poin keharmonisan")
                    Toast.makeText(activity, "💗 +$pts Poin Keharmonisan", Toast.LENGTH_SHORT).show()
                } else if (!isSelf) {
                    Toast.makeText(
                        activity,
                        "Belum berstatus CP/BFF yang cocok dengan ${to.second}, poin tidak bertambah",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            onDone(sb.toString())
        }.addOnFailureListener {
            Toast.makeText(activity, "Gagal: ${it.message}", Toast.LENGTH_LONG).show()
            onFail()
        }
    }
}
