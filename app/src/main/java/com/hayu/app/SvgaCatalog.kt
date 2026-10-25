package com.hayu.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Katalog aset SVGA yang diunggah Developer (Firestore: koleksi svgaAssets). */
object SvgaCatalog {
    const val COLLECTION = "svgaAssets"

    const val GIFT = "gift"
    const val FRAME = "frame"
    const val CAR = "car"
    const val OTHER = "other"

    /** Tambah jenis baru cukup di sini. */
    val types = listOf(
        GIFT to "🎁 Gift",
        FRAME to "🖼️ Frame",
        CAR to "🚗 Car",
        OTHER to "✨ Lainnya"
    )

    fun typeLabel(type: String): String =
        types.firstOrNull { it.first == type }?.second ?: type

    class Asset(
        val id: String,
        val type: String,
        val name: String,
        val price: Int,
        val days: Int,          // masa berlaku (hari); 0 = permanen
        val url: String,
        val emoji: String,
        val storagePath: String,
        val createdAt: Long,
        val iconUrl: String = ""   // gambar ikon (tampil di tombol Gift/Toko menggantikan emoji)
    )

    /** type = null → semua jenis. Gagal memuat → daftar kosong. */
    fun load(type: String?, onDone: (List<Asset>) -> Unit) {
        val col = FirebaseFirestore.getInstance().collection(COLLECTION)
        val query: Query = if (type == null) col else col.whereEqualTo("type", type)
        query.get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { d ->
                    val url = d.getString("url") ?: return@mapNotNull null
                    Asset(
                        id = d.id,
                        type = d.getString("type") ?: OTHER,
                        name = d.getString("name") ?: "Tanpa nama",
                        price = (d.getLong("price") ?: 0L).toInt(),
                        days = (d.getLong("days") ?: 0L).toInt(),
                        url = url,
                        emoji = d.getString("emoji") ?: "",
                        storagePath = d.getString("storagePath") ?: "",
                        createdAt = d.getLong("createdAt") ?: 0L,
                        iconUrl = d.getString("iconUrl") ?: ""
                    )
                }.sortedByDescending { it.createdAt }
                onDone(list)
            }
            .addOnFailureListener { onDone(emptyList()) }
    }
}
