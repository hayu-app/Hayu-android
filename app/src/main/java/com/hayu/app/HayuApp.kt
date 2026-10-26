package com.hayu.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class HayuApp : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        // Unduh aset VIP/SVIP di latar belakang; aset APK tetap menjadi cadangan.
        HayuAssetManager.ensureDownloaded(this)

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Cleanup room lama saat app dibuka
        cleanupRooms()
    }

    // Tutup room yang tidak aktif > 5 menit
    private fun cleanupRooms() {
        val db = FirebaseFirestore.getInstance()
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

        db.collection("rooms")
            .whereEqualTo("active", true)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val lastActive = doc.getLong("lastActiveAt") ?: 0L
                    val createdAt = doc.getLong("createdAt") ?: 0L

                    // Kalau belum ada aktivitas sama sekali, pakai createdAt
                    val effectiveTime = if (lastActive > 0) lastActive else createdAt

                    if (effectiveTime > 0 && effectiveTime < fiveMinutesAgo) {
                        doc.reference.update("active", false)
                    }
                }
            }
    }
}
