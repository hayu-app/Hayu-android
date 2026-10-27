package com.hayu.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pencatat crash sementara untuk mencari penyebab force close.
 * Saat app crash, errornya disimpan ke file. Begitu app dibuka lagi, muncul dialog berisi teks error
 * yang bisa disalin (tombol "Salin") lalu ditempel ke chat.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private var shown = false

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val head = "Waktu: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
                    "Thread: ${thread.name}\n" +
                    "HP: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\n" +
                    "ABI: ${Build.SUPPORTED_ABIS.joinToString()}\n\n"
                File(app.filesDir, FILE).writeText(head + sw.toString().take(7000))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                showIfAny(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun showIfAny(activity: Activity) {
        if (shown || activity.isFinishing) return
        try {
            val f = File(activity.filesDir, FILE)
            if (!f.exists()) return
            val text = f.readText()
            shown = true

            val pad = (16 * activity.resources.displayMetrics.density).toInt()
            val tv = TextView(activity)
            tv.text = text
            tv.textSize = 10f
            tv.setTextColor(Color.BLACK)
            tv.setTextIsSelectable(true)
            tv.setPadding(pad, pad, pad, pad)
            val sv = ScrollView(activity)
            sv.addView(tv)

            AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("⚠️ Aplikasi sempat crash")
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Salin") { _, _ ->
                    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                    Toast.makeText(activity, "Disalin. Tempel ke chat ya", Toast.LENGTH_LONG).show()
                    f.delete()
                }
                .setNegativeButton("Tutup") { _, _ -> f.delete() }
                .show()
        } catch (_: Throwable) {
        }
    }
}
