package com.hayu.app

import com.google.firebase.firestore.DocumentSnapshot

class VipActivity : SimplePageActivity() {

    override val pageTitle: String get() = "VIP & SVIP"

    companion object {
        val VIP_PRICES = intArrayOf(100, 300, 600, 1000, 1500, 2200, 3000)
        val SVIP_POINTS = intArrayOf(1000, 3000, 6000, 10000, 15000, 25000, 40000)

        fun svipLevel(points: Long): Int {
            var lv = 0
            for (i in SVIP_POINTS.indices) if (points >= SVIP_POINTS[i]) lv = i + 1
            return lv
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

        container.addView(Ui.text(this, "\n👑 SVIP — naik level lewat top up di Dompet", 12f, Ui.GRAY))

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
}
