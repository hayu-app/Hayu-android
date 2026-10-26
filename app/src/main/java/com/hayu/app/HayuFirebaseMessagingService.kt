// file: app/src/main/java/com/hayu/app/HayuFirebaseMessagingService.kt
// Terima FCM push notification: foreground (tampilkan notif manual) &
// background (sistem tampilkan otomatis). Simpan token FCM ke Firestore user.

package com.hayu.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HayuFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "hayu_notif"
        const val CHANNEL_NAME = "Notifikasi Hayu"

        /** Buat notification channel (panggil dari Application / MainActivity). */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi dari admin dan sistem Hayu"
                    enableLights(true)
                    enableVibration(true)
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(ch)
            }
        }

        /** Simpan / perbarui token FCM milik user yang sedang login. */
        fun saveToken(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token)
        }
    }

    // ── Token berubah (install ulang / reset) ────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveToken(token)
    }

    // ── Pesan masuk saat app di foreground ───────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Hayu"
        val body  = message.notification?.body
            ?: message.data["body"]
            ?: return   // tidak ada isi → skip

        showNotification(title, body)
    }

    // ── Tampilkan notifikasi ──────────────────────────────────────────────────

    private fun showNotification(title: String, body: String) {
        createChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
