package com.hayu.app

import android.view.ViewGroup
import android.widget.LinearLayout
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

        renderSvgaShop(user, owned)
    }

    // Bingkai avatar & kendaraan masuk room (SVGA unggahan Developer)
    private fun renderSvgaShop(user: DocumentSnapshot, owned: Set<String>) {
        val coins = (user.getLong("coins") ?: 0L).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        container.addView(box)

        val sections = listOf(
            Triple(SvgaCatalog.FRAME, "🖼️ Bingkai Avatar", "equippedFrame"),
            Triple(SvgaCatalog.CAR, "🚗 Kendaraan Masuk Room", "equippedCar")
        )
        SvgaCatalog.load(null) { all ->
            for ((type, title, field) in sections) {
                val list = all.filter { it.type == type }
                if (list.isEmpty()) continue
                val equippedUrl = user.getString("${field}Url") ?: ""
                val head = Ui.text(this, title, 15f, Ui.WHITE_SOFT, true)
                head.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6))
                box.addView(head)

                for (a in list) {
                    val have = a.price == 0 || "svga_${a.id}" in owned
                    val main = when {
                        equippedUrl == a.url -> Ui.button(this, "Lepas") { equipSvga(field, "", "") }
                        have -> Ui.button(this, "Pakai") { equipSvga(field, a.url, a.name) }
                        else -> Ui.button(this, "🪙 ${a.price}", coins >= a.price) { buySvga(a) }
                    }
                    val preview = Ui.button(this, "▶") { SvgaOverlay.show(this, a.url) }
                    val right = LinearLayout(this)
                    right.orientation = LinearLayout.HORIZONTAL
                    right.addView(preview)
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginStart = Ui.dp(this, 6)
                    right.addView(main, lp)

                    val desc = when {
                        equippedUrl == a.url -> "Sedang dipakai"
                        have -> "Sudah dimiliki"
                        else -> "${a.price} koin"
                    }
                    val icon = a.emoji.ifBlank { if (type == SvgaCatalog.FRAME) "🖼️" else "🚗" }
                    box.addView(Ui.row(this, icon, a.name, desc, right))
                }
            }
        }
    }

    private fun equipSvga(field: String, url: String, name: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update(mapOf("${field}Url" to url, "${field}Name" to name))
            .addOnSuccessListener { reload() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun buySvga(a: SvgaCatalog.Asset) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
        val itemId = "svga_${a.id}"
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val owned = (snap.get("ownedItems") as? List<*>)?.map { o -> o.toString() } ?: emptyList()
            val coins = snap.getLong("coins") ?: 0L
            if (itemId in owned) throw IllegalStateException("Sudah dimiliki")
            if (coins < a.price) throw IllegalStateException("Koin tidak cukup")
            tx.update(
                ref, mapOf(
                    "coins" to (coins - a.price),
                    "ownedItems" to (owned + itemId)
                )
            )
            null
        }.addOnSuccessListener {
            toast("Berhasil membeli ${a.name} 🎉")
            reload()
        }.addOnFailureListener {
            toast("Gagal: ${it.message}")
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
