package com.hayu.app

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object GiftSheet {

    private class Gift(val emoji: String, val name: String, val price: Int)

    private val gifts = listOf(
        Gift("🌹", "Mawar", 10),
        Gift("☕", "Kopi", 20),
        Gift("🍫", "Cokelat", 30),
        Gift("💖", "Hati", 50),
        Gift("🎂", "Kue", 100),
        Gift("🚀", "Roket", 200),
        Gift("👑", "Mahkota", 500),
        Gift("💎", "Berlian", 1000)
    )

    // recipients: pasangan (uid, nama tampil). Boleh berisi uid sendiri.
    // onSent(teks pesan, emoji hadiah)
    fun show(
        activity: AppCompatActivity,
        myNickname: String,
        recipients: List<Pair<String, String>>,
        onSent: (String, String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (recipients.isEmpty()) return

        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            val coins = (me.getLong("coins") ?: 0L).toInt()
            val dialog = BottomSheetDialog(activity)
            val d = { v: Int -> Ui.dp(activity, v) }

            val root = LinearLayout(activity)
            root.orientation = LinearLayout.VERTICAL
            root.setBackgroundResource(R.drawable.bg_bottom_sheet)
            root.setPadding(d(16), d(16), d(16), d(16))

            root.addView(Ui.text(activity, "🎁 Kirim Hadiah", 18f, Ui.WHITE_SOFT, true))
            root.addView(Ui.text(activity, "Kirim ke:", 12f, Ui.GRAY))

            // ---- penerima ----
            var selectedUid = (recipients.firstOrNull { it.first != myUid } ?: recipients[0]).first
            val chips = ArrayList<Pair<TextView, String>>()
            val refreshChips = {
                for ((tv, uid) in chips) {
                    tv.setBackgroundResource(
                        if (uid == selectedUid) R.drawable.bg_button_primary else R.drawable.bg_quick_item
                    )
                }
            }
            val chipRow = LinearLayout(activity)
            chipRow.orientation = LinearLayout.HORIZONTAL
            for ((uid, name) in recipients) {
                val label = if (uid == myUid) "👤 Saya" else name.take(16)
                val tv = Ui.text(activity, label, 12f, Color.WHITE, true)
                tv.setPadding(d(12), d(6), d(12), d(6))
                tv.setOnClickListener {
                    selectedUid = uid
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
            hsLp.bottomMargin = d(12)
            root.addView(hs, hsLp)

            // ---- grid hadiah (4 kolom) ----
            var selectedGift = gifts[0]
            val tiles = ArrayList<Pair<LinearLayout, Gift>>()
            val refreshTiles = {
                for ((t, g) in tiles) {
                    t.setBackgroundResource(
                        if (g === selectedGift) R.drawable.bg_gift_tile_selected else R.drawable.bg_gift_tile
                    )
                }
            }
            var i = 0
            while (i < gifts.size) {
                val line = LinearLayout(activity)
                line.orientation = LinearLayout.HORIZONTAL
                for (j in i until i + 4) {
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(d(3), d(3), d(3), d(3))
                    if (j >= gifts.size) {
                        line.addView(TextView(activity), lp)
                        continue
                    }
                    val g = gifts[j]
                    val tile = LinearLayout(activity)
                    tile.orientation = LinearLayout.VERTICAL
                    tile.gravity = Gravity.CENTER_HORIZONTAL
                    tile.setPadding(d(4), d(10), d(4), d(10))
                    tile.addView(Ui.text(activity, g.emoji, 30f))
                    tile.addView(Ui.text(activity, g.name, 11f, Ui.WHITE_SOFT, true))
                    tile.addView(Ui.text(activity, "🪙 ${g.price}", 10f, Ui.GOLD))
                    tile.setOnClickListener {
                        selectedGift = g
                        refreshTiles()
                    }
                    line.addView(tile, lp)
                    tiles.add(Pair(tile, g))
                }
                root.addView(line)
                i += 4
            }
            refreshTiles()

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
            val qtyBtn = Ui.button(activity, "x1") {}
            qtyBtn.setBackgroundResource(R.drawable.bg_quick_item)
            qtyBtn.setOnClickListener {
                qty = when (qty) {
                    1 -> 5
                    5 -> 10
                    else -> 1
                }
                qtyBtn.text = "x$qty"
            }
            val qtyLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            qtyLp.rightMargin = d(8)
            bar.addView(qtyBtn, qtyLp)

            val sendBtn = Ui.button(activity, "Kirim") {}
            sendBtn.setOnClickListener {
                val g = selectedGift
                val total = g.price * qty
                if (coins < total) {
                    Toast.makeText(activity, "Koin tidak cukup", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val to = recipients.first { it.first == selectedUid }
                sendBtn.isEnabled = false
                send(activity, db, myUid, myNickname, to, g, qty,
                    onDone = { text ->
                        dialog.dismiss()
                        onSent(text, g.emoji)
                    },
                    onFail = { sendBtn.isEnabled = true }
                )
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

    private fun send(
        activity: AppCompatActivity,
        db: FirebaseFirestore,
        myUid: String,
        myName: String,
        to: Pair<String, String>,
        g: Gift,
        qty: Int,
        onDone: (String) -> Unit,
        onFail: () -> Unit
    ) {
        val meRef = db.collection("users").document(myUid)
        val toRef = db.collection("users").document(to.first)
        val isSelf = to.first == myUid
        val total = g.price * qty

        db.runTransaction { tx ->
            val me = tx.get(meRef)
            val coins = me.getLong("coins") ?: 0L
            if (coins < total) throw IllegalStateException("Koin tidak cukup")
            val exp = (me.getLong("exp") ?: 0L) + total / 10
            val share = (total / 2).toLong()
            val level = Ui.levelForExp(exp.toInt()).toLong()
            val sent = (me.getLong("giftsSent") ?: 0L) + qty

            if (isSelf) {
                // kirim ke diri sendiri: satu dokumen saja
                tx.update(
                    meRef, mapOf(
                        "coins" to (coins - total + share),
                        "totalCoinsEarned" to ((me.getLong("totalCoinsEarned") ?: 0L) + share),
                        "exp" to exp,
                        "level" to level,
                        "giftsSent" to sent,
                        "giftsReceived" to ((me.getLong("giftsReceived") ?: 0L) + qty)
                    )
                )
            } else {
                val rc = tx.get(toRef)
                tx.update(
                    meRef, mapOf(
                        "coins" to (coins - total),
                        "exp" to exp,
                        "level" to level,
                        "giftsSent" to sent
                    )
                )
                tx.update(
                    toRef, mapOf(
                        "coins" to ((rc.getLong("coins") ?: 0L) + share),
                        "totalCoinsEarned" to ((rc.getLong("totalCoinsEarned") ?: 0L) + share),
                        "giftsReceived" to ((rc.getLong("giftsReceived") ?: 0L) + qty)
                    )
                )
            }
            null
        }.addOnSuccessListener {
            val text = if (isSelf) {
                "$myName mengirim ${g.emoji} ${g.name} x$qty untuk dirinya sendiri"
            } else {
                "$myName mengirim ${g.emoji} ${g.name} x$qty ke ${to.second}"
            }
            onDone(text)
        }.addOnFailureListener {
            Toast.makeText(activity, "Gagal: ${it.message}", Toast.LENGTH_LONG).show()
            onFail()
        }
    }
}
