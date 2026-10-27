package com.hayu.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class HayuApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)

        FirebaseApp.initializeApp(this)

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Alamat hosting aset VIP/SVIP (bisa diganti lewat Firestore app_config/assets.baseUrl)
        VipAssets.refreshConfig()

        // Cleanup room lama saat app dibuka
        cleanupRooms()
    }

    // Batasi cache gambar supaya penyimpanan app tidak membengkak (bawaan Coil bisa sampai 250MB)
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()

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
