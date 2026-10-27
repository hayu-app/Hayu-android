package com.hayu.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service supaya suara room tetap jalan saat aplikasi di background / room di-minimize.
 * Mesin suara (Agora) tetap dipegang RoomActivity; service ini hanya menjaga proses tetap hidup
 * dan menampilkan notifikasi "kamu masih di room".
 */
class VoiceService : Service() {

    companion object {
        private const val CHANNEL_ID = "hayu_voice"
        private const val NOTIF_ID = 4101

        fun start(ctx: Context, roomId: String, roomName: String, withMic: Boolean) {
            val i = Intent(ctx, VoiceService::class.java)
                .putExtra("roomId", roomId)
                .putExtra("roomName", roomName)
                .putExtra("withMic", withMic)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roomId = intent?.getStringExtra("roomId") ?: ""
        val roomName = intent?.getStringExtra("roomName") ?: "Room"
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val withMic = intent?.getBooleanExtra("withMic", false) == true && micGranted

        createChannel()

        val open = Intent(this, RoomActivity::class.java)
            .putExtra("roomId", roomId)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("HAYU \u2022 $roomName")
            .setContentText(
                if (withMic) "Mic aktif \u2014 ketuk untuk kembali ke room"
                else "Kamu masih di room \u2014 ketuk untuk kembali"
            )
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= 30) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (withMic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIF_ID, notif, type)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Suara room", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
