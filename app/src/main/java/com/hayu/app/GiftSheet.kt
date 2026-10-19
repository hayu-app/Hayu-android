package com.hayu.app

import android.graphics.Color
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object GiftSheet {

    private class Gift(val emoji: String, val name: String, val price: Int)

    private val gifts = listOf(
        Gift("🌹", "Mawar", 10),
        Gift("☕", "Kopi", 20),
        Gift("💖", "Hati", 50),
        Gift("🚀", "Roket", 200),
        Gift("👑", "Mahkota", 500)
    )

    // recipients: pasangan (uid, nama tampil)
    fun show(
        activity: AppCompatActivity,
        myNickname: String,
        recipients: List<Pair<String, String>>,
        onSent: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (recipients.isEmpty()) {
            Toast.makeText(
                activity, "Belum ada penerima. Hadiah dikirim ke owner atau yang sedang on mic.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            val coins = (me.getLong("coins") ?: 0L).toInt()
            val dialog = BottomSheetDialog(activity)

            val root = LinearLayout(activity)
            root.orientation = LinearLayout.VERTICAL
            root.setBackgroundResource(R.drawable.bg_bottom_sheet)
            root.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 20))

            root.addView(Ui.text(activity, "🎁 Kirim Hadiah", 18f, Ui.WHITE_SOFT, true))
            val coinTv = Ui.text(activity, "Koinmu: 🪙 $coins", 12f, Ui.GOLD, true)
            root.addView(coinTv)
            root.addView(Ui.text(activity, "Kirim ke:", 12f, Ui.GRAY))

            var selected = recipients[0].first
            val chips = ArrayList<Pair<TextView, String>>()
            val refresh = {
                for ((tv, uid) in chips) {
                    tv.setBackgroundResource(
                        if (uid == selected) R.drawable.bg_button_primary else R.drawable.bg_quick_item
                    )
                }
            }

            val chipRow = LinearLayout(activity)
            chipRow.orientation = LinearLayout.HORIZONTAL
            for ((uid, name) in recipients) {
                val tv = Ui.text(activity, name.take(16), 12f, Color.WHITE, true)
                tv.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 6), Ui.dp(activity, 12), Ui.dp(activity, 6))
                tv.setOnClickListener {
                    selected = uid
                    refresh()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = Ui.dp(activity, 8)
                chipRow.addView(tv, lp)
                chips.add(Pair(tv, uid))
            }
            refresh()
            val hs = HorizontalScrollView(activity)
            hs.addView(chipRow)
            val hsLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            hsLp.topMargin = Ui.dp(activity, 6)
            hsLp.bottomMargin = Ui.dp(activity, 10)
            root.addView(hs, hsLp)

            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            for (g in gifts) {
                val btn = Ui.button(activity, "🪙 ${g.price}", coins >= g.price) {
                    val to = recipients.first { it.first == selected }
                    send(activity, db, myUid, myNickname, to, g) { text ->
                        dialog.dismiss()
                        onSent(text)
                    }
                }
                list.addView(Ui.row(activity, g.emoji, g.name, "Penerima dapat ${g.price / 2} koin", btn))
            }
            val sv = ScrollView(activity)
            sv.addView(list)
            root.addView(sv)

            dialog.setContentView(root)
            dialog.show()
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
        onDone: (String) -> Unit
    ) {
        val meRef = db.collection("users").document(myUid)
        val toRef = db.collection("users").document(to.first)
        db.runTransaction { tx ->
            val me = tx.get(meRef)
            val rc = tx.get(toRef)
            val coins = me.getLong("coins") ?: 0L
            if (coins < g.price) throw IllegalStateException("Koin tidak cukup")
            val exp = (me.getLong("exp") ?: 0L) + g.price / 10
            tx.update(
                meRef, mapOf(
                    "coins" to (coins - g.price),
                    "exp" to exp,
                    "level" to Ui.levelForExp(exp.toInt()).toLong(),
                    "giftsSent" to ((me.getLong("giftsSent") ?: 0L) + 1)
                )
            )
            val share = (g.price / 2).toLong()
            tx.update(
                toRef, mapOf(
                    "coins" to ((rc.getLong("coins") ?: 0L) + share),
                    "totalCoinsEarned" to ((rc.getLong("totalCoinsEarned") ?: 0L) + share),
                    "giftsReceived" to ((rc.getLong("giftsReceived") ?: 0L) + 1)
                )
            )
            null
        }.addOnSuccessListener {
            Toast.makeText(activity, "Hadiah terkirim ${g.emoji}", Toast.LENGTH_SHORT).show()
            onDone("$myName mengirim ${g.emoji} ${g.name} ke ${to.second}")
        }.addOnFailureListener {
            Toast.makeText(activity, "Gagal: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
