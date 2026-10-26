// file: app/src/main/java/com/hayu/app/NotifAdminActivity.kt
// Panel Developer: kirim push notification ke semua user atau user tertentu.
// Strategi:
//   - Simpan pesan ke Firestore: notifications/{docId}
//     { title, body, targetUid (kosong = broadcast), sentAt, sentBy }
//   - Cloud Function (atau trigger) yang kamu buat nanti bisa baca koleksi ini
//     dan kirim via FCM Admin SDK.
//   - Untuk "kirim langsung" dari app (tanpa server), kita kirim ke token FCM
//     user tertentu via Firestore relay: device user target yang online akan
//     pick-up dari listener di HayuApp.
//
// CATATAN: Kirim notif ke SEMUA user butuh server/Cloud Function karena
// Firebase tidak izinkan client kirim ke topic "all" langsung.
// File ini menyimpan antrian notifikasi ke Firestore — Cloud Function kamu
// yang eksekusi pengiriman FCM.

package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NotifAdminActivity : AppCompatActivity() {

    private val db   by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var etTitle    : EditText
    private lateinit var etBody     : EditText
    private lateinit var etTargetUid: EditText
    private lateinit var tvHistory  : LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#080818"))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D3D"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val btnBack = TextView(this).apply {
            text = "‹"; textSize = 28f; setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }
        header.addView(btnBack)
        header.addView(TextView(this).apply {
            text = "  🔔 Kirim Notifikasi"
            textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val sv = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(80))
        }

        // ── Form ──────────────────────────────────────────────────────────────
        container.addView(label("📌 Judul Notifikasi"))
        etTitle = inputField("Contoh: Promo Spesial!")
        container.addView(etTitle)

        container.addView(label("📝 Isi Pesan"))
        etBody = inputField("Contoh: Dapatkan bonus koin hari ini!", multiline = true)
        container.addView(etBody)

        container.addView(label("🎯 Target UID (kosongkan = semua user)"))
        etTargetUid = inputField("UID user tertentu, atau kosong untuk broadcast")
        container.addView(etTargetUid)

        // Info broadcast
        container.addView(TextView(this).apply {
            text = "ℹ️  Broadcast ke semua user membutuhkan Cloud Function di server. " +
                    "Notifikasi akan disimpan ke Firestore dan dikirim oleh server secara otomatis."
            textSize = 11f; setTextColor(Color.parseColor("#888AAA"))
            setPadding(0, dp(4), 0, dp(16))
        })

        // Tombol Kirim
        val btnSend = TextView(this).apply {
            text = "🚀 Kirim Notifikasi"
            textSize = 16f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6C63FF")); cornerRadius = dp(12).toFloat()
            }
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { confirmAndSend() }
        }
        container.addView(btnSend, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        // ── Riwayat ───────────────────────────────────────────────────────────
        container.addView(TextView(this).apply {
            text = "📋 Riwayat Notifikasi Terakhir"
            textSize = 14f; setTextColor(Color.parseColor("#A78BFA"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })

        tvHistory = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(tvHistory)

        sv.addView(container)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        loadHistory()
    }

    private fun confirmAndSend() {
        val title  = etTitle.text.toString().trim()
        val body   = etBody.text.toString().trim()
        val target = etTargetUid.text.toString().trim()

        if (title.isEmpty()) { toast("Judul tidak boleh kosong"); return }
        if (body.isEmpty())  { toast("Isi pesan tidak boleh kosong"); return }

        val targetLabel = if (target.isEmpty()) "SEMUA USER" else "UID: $target"
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Kirim")
            .setMessage("Kirim notifikasi ke $targetLabel?\n\nJudul: $title\n\n$body")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Kirim") { _, _ -> sendNotif(title, body, target) }
            .show()
    }

    private fun sendNotif(title: String, body: String, targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val data = hashMapOf<String, Any>(
            "title"     to title,
            "body"      to body,
            "targetUid" to targetUid,   // kosong = broadcast semua
            "sentAt"    to System.currentTimeMillis(),
            "sentBy"    to myUid,
            "status"    to "pending"    // Cloud Function update jadi "sent" setelah kirim
        )

        db.collection("notifications").add(data)
            .addOnSuccessListener {
                toast("✅ Notifikasi diantrekan!")
                etTitle.text.clear()
                etBody.text.clear()
                etTargetUid.text.clear()
                loadHistory()
            }
            .addOnFailureListener {
                toast("❌ Gagal: ${it.message}")
            }
    }

    private fun loadHistory() {
        db.collection("notifications")
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { snap ->
                tvHistory.removeAllViews()
                if (snap.isEmpty) {
                    tvHistory.addView(TextView(this).apply {
                        text = "Belum ada notifikasi yang dikirim."
                        textSize = 13f; setTextColor(Color.parseColor("#666880"))
                    })
                    return@addOnSuccessListener
                }
                for (doc in snap.documents) {
                    val t      = doc.getString("title") ?: "-"
                    val b      = doc.getString("body") ?: "-"
                    val target = doc.getString("targetUid") ?: ""
                    val status = doc.getString("status") ?: "pending"
                    val ms     = doc.getLong("sentAt") ?: 0L
                    val time   = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(ms))
                    val targetLabel = if (target.isEmpty()) "Semua user" else "UID: $target"

                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#12122A"))
                            cornerRadius = dp(10).toFloat()
                            setStroke(dp(1), Color.parseColor("#2E2E50"))
                        }
                    }
                    card.addView(TextView(this).apply {
                        text = "[$time]  $targetLabel  •  $status"
                        textSize = 11f; setTextColor(Color.parseColor("#888AAA"))
                    })
                    card.addView(TextView(this).apply {
                        text = t; textSize = 13f; setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(0, dp(4), 0, 0)
                    })
                    card.addView(TextView(this).apply {
                        text = b; textSize = 12f; setTextColor(Color.parseColor("#C4CAFF"))
                    })

                    val cardLp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                    tvHistory.addView(card, cardLp)
                }
            }
    }

    // ── Helper UI ─────────────────────────────────────────────────────────────

    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.WHITE)
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun inputField(hint: String, multiline: Boolean = false) = EditText(this).apply {
        this.hint = hint
        this.setHintTextColor(Color.parseColor("#555570"))
        textSize = 14f; setTextColor(Color.WHITE)
        if (multiline) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; maxLines = 6
        } else {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#12122A"))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#2E2E50"))
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams = lp
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
