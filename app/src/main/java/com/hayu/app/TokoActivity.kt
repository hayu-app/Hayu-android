package com.hayu.app

import com.google.firebase.firestore.DocumentSnapshot

class TokoActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Toko"

    private class Item(val id: String, val emoji: String, val name: String, val price: Int) {
        val title: String get() = "$emoji $name"
    }

    private val items = listOf(
        Item("t_pemula", "🌱", "Pemula", 20),
        Item("t_bintang", "⭐", "Bintang", 50),
        Item("t_api", "🔥", "Api", 120),
        Item("t_raja", "👑", "Raja", 200),
        Item("t_berlian", "💎", "Berlian", 500)
    )

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val coins = (user.getLong("coins") ?: 0L).toInt()
        val owned = (user.get("ownedItems") as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
        val equipped = user.getString("equippedTitle") ?: ""

        container.addView(Ui.text(this, "Gelar tampil di depan nickname-mu", 12f, Ui.GRAY))

        if (equipped.isNotBlank()) {
            val off = Ui.button(this, "Lepas") { equip("") }
            container.addView(Ui.row(this, "🏷️", "Dipakai: $equipped", "Ketuk Lepas untuk menghapus", off))
        }

        for (item in items) {
            val btn = when {
                equipped == item.title -> Ui.button(this, "Dipakai", false) {}
                item.id in owned -> Ui.button(this, "Pakai") { equip(item.title) }
                else -> Ui.button(this, "🪙 ${item.price}", coins >= item.price) { buy(item) }
            }
            val desc = if (item.id in owned) "Sudah dimiliki" else "Gelar • ${item.price} koin"
            container.addView(Ui.row(this, item.emoji, item.name, desc, btn))
        }
    }

    private fun equip(title: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("equippedTitle", title)
            .addOnSuccessListener { reload() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun buy(item: Item) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val owned = (snap.get("ownedItems") as? List<*>)?.map { o -> o.toString() } ?: emptyList()
            val coins = snap.getLong("coins") ?: 0L
            if (item.id in owned) throw IllegalStateException("Sudah dimiliki")
            if (coins < item.price) throw IllegalStateException("Koin tidak cukup")
            tx.update(
                ref, mapOf(
                    "coins" to (coins - item.price),
                    "ownedItems" to (owned + item.id)
                )
            )
            null
        }.addOnSuccessListener {
            toast("Berhasil membeli ${item.name} 🎉")
            reload()
        }.addOnFailureListener {
            toast("Gagal: ${it.message}")
        }
    }
}
