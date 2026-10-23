package com.hayu.app.util

import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.Role

/**
 * Cache info pengirim chat (foto + role) supaya tiap pesan tidak membaca Firestore berulang.
 * Satu pembacaan per user, disegarkan lagi kalau sudah lebih dari 5 menit.
 */
object UserInfoCache {

    data class Info(
        val photoUrl: String,
        val roles: Set<String>,
        val fetchedAt: Long
    )

    private const val TTL_MS = 5 * 60 * 1000L

    private val cache = HashMap<String, Info>()
    private val waiting = HashMap<String, MutableList<() -> Unit>>()

    fun peek(uid: String): Info? = cache[uid]

    private fun isFresh(i: Info?) = i != null && System.currentTimeMillis() - i.fetchedAt < TTL_MS

    /** Pastikan info uid ada & segar. onReady dipanggil di main thread setelah data baru masuk. */
    fun request(uid: String, onReady: () -> Unit) {
        if (uid.isEmpty() || isFresh(cache[uid])) return
        val list = waiting[uid]
        if (list != null) {
            list.add(onReady)
            return
        }
        waiting[uid] = mutableListOf(onReady)
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                cache[uid] = Info(
                    photoUrl = doc.getString("photoUrl") ?: "",
                    roles = Role.rolesOf(doc),
                    fetchedAt = System.currentTimeMillis()
                )
                waiting.remove(uid)?.forEach { it() }
            }
            .addOnFailureListener {
                // Simpan kosong sementara agar tidak mencoba terus-menerus
                cache[uid] = Info("", emptySet(), System.currentTimeMillis())
                waiting.remove(uid)?.forEach { it() }
            }
    }
}
