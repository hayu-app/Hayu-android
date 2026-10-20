package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class VipActivity : SimplePageActivity() {

    override val pageTitle: String get() = "VIP & SVIP"

    companion object {
        val VIP_PRICES = intArrayOf(100, 300, 600, 1000, 1500, 2200, 3000)
        val SVIP_POINTS = intArrayOf(1000, 3000, 6000, 10000, 15000, 25000, 40000)
        val TOPUP_OPTIONS = intArrayOf(1000, 5000, 10000, 25000)

        fun svipLevel(points: Long): Int {
            var lv = 0
            for (i in SVIP_POINTS.indices) if (points >= SVIP_POINTS[i]) lv = i + 1
            return lv
        }

        fun creditTopUp(db: FirebaseFirestore, uid: String, amount: Int, onDone: (Boolean, String?) -> Unit) {
            val ref = db.collection("users").document(uid)
            db.runTransaction { tx ->
                val s = tx.get(ref)
                val oldPts = s.getLong("svipPoints") ?: 0L
                val newPts = oldPts + amount
                val oldLv = svipLevel(oldPts)
                val newLv = svipLevel(newPts)
                val upd = hashMapOf<String, Any>(
                    "coins" to ((s.getLong("coins") ?: 0L) + amount),
                    "svipPoints" to newPts,
                    "svipLevel" to newLv.toLong()
                )
                if (newLv > oldLv) upd["equippedTitle"] = "👑 SVIP$newLv"
                tx.update(ref, upd)
                null
            }.addOnSuccessListener { onDone(true, null) }
                .addOnFailureListener { onDone(false, it.message) }
        }
    }

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val coins = (user.getLong("coins") ?: 0L).toInt()
        val vipLv = (user.getLong("vipLevel") ?: 0L).toInt()
        val pts = user.getLong("svipPoints") ?: 0L
        val svLv = svipLevel(pts)

        container.addView(Ui.text(this, "Status kamu: VIP $vipLv  •  SVIP $svLv", 13f, Ui.GOLD, true))

        container.addView(Ui.text(this, "\n⭐ VIP — beli dengan koin (berurutan)", 12f, Ui.GRAY))
        for (i in 1..7) {
            val price = VIP_PRICES[i - 1]
            val btn = when {
                i <= vipLv -> Ui.button(this, "Dimiliki ✅", false) {}
                i == vipLv + 1 -> Ui.button(this, "🪙 $price", coins >= price) { buyVip(i) }
                else -> Ui.button(this, "🔒", false) {}
            }
            container.addView(Ui.row(this, "⭐", "VIP $i", "Harga $price koin", btn))
        }

        container.addView(Ui.text(this, "\n👑 SVIP — naik level lewat top up", 12f, Ui.GRAY))

        val card = Ui.card(this)
        card.addView(Ui.text(this, "Poin top up: $pts", 14f, Ui.WHITE_SOFT, true))
        val next = if (svLv < 7) SVIP_POINTS[svLv] else 0
        card.addView(
            Ui.text(
                this,
                if (svLv < 7) "${next - pts} poin lagi menuju SVIP ${svLv + 1}  (1 koin top up = 1 poin)"
                else "Level SVIP maksimal 🎉",
                11f, Ui.GRAY
            )
        )
        container.addView(card)

        container.addView(Ui.row(this, "💳", "Top Up Koin", "Top up untuk naik level SVIP", Ui.button(this, "Top Up") { chooseTopUp() }))

        for (i in 1..7) {
            val tag = Ui.text(this, if (svLv >= i) "✅" else "🔒", 14f)
            container.addView(Ui.row(this, "👑", "SVIP $i", "Butuh ${SVIP_POINTS[i - 1]} poin top up", tag))
        }
    }

    private fun buyVip(level: Int) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
        val price = VIP_PRICES[level - 1]
        db.runTransaction { tx ->
            val s = tx.get(ref)
            val coins = s.getLong("coins") ?: 0L
            val cur = (s.getLong("vipLevel") ?: 0L).toInt()
            if (level != cur + 1) throw IllegalStateException("Beli VIP ${cur + 1} dulu")
            if (coins < price) throw IllegalStateException("Koin tidak cukup")
            val upd = hashMapOf<String, Any>(
                "coins" to (coins - price),
                "vipLevel" to level.toLong()
            )
            val title = s.getString("equippedTitle") ?: ""
            if (title.isBlank() || title.startsWith("⭐ VIP")) upd["equippedTitle"] = "⭐ VIP$level"
            tx.update(ref, upd)
            null
        }.addOnSuccessListener {
            toast("Berhasil! VIP $level aktif 🎉")
            reload()
        }.addOnFailureListener {
            toast("Gagal: ${it.message}")
        }
    }

    private fun chooseTopUp() {
        val labels = TOPUP_OPTIONS.map { "🪙 $it koin  (+$it poin SVIP)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih nominal top up")
            .setItems(labels) { _, which -> requestTopUp(TOPUP_OPTIONS[which]) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun requestTopUp(amount: Int) {
        val uid = auth.currentUser?.uid ?: return
        val req = hashMapOf(
            "uid" to uid,
            "amount" to amount,
            "status" to "pending",
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("topupRequests").add(req)
            .addOnSuccessListener { toast("Permintaan top up $amount koin dikirim. Menunggu konfirmasi admin ⏳") }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
