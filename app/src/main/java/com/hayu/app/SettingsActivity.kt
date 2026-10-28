package com.hayu.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Pengaturan: akun (email), ID pengguna, kebijakan privasi, bahasa, dan Keluar. */
class SettingsActivity : AppCompatActivity() {

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var list: LinearLayout
    private lateinit var idValue: TextView

    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = auth.currentUser
        if (user == null) {
            finish()
            return
        }
        buildUi(user.email ?: "-")
        loadUserId(user.uid)
    }

    private fun buildUi(email: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#080818"))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(Color.parseColor("#0D0D3D"))
        header.setPadding(d(16), d(14), d(16), d(14))
        val back = TextView(this)
        back.text = "‹"
        back.textSize = 28f
        back.setTextColor(Color.WHITE)
        back.setOnClickListener { finish() }
        header.addView(back)
        header.addView(
            Ui.text(this, "  Pengaturan", 18f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        root.addView(header)

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(d(16), d(8), d(16), d(40))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        row("Akun", email) { copy("Email", email) }
        idValue = row("ID pengguna", "…") { copy("ID pengguna", idValue.text.toString()) }
        row("Kebijakan privasi", "") { toast("Kebijakan privasi segera hadir") }
        row("Bahasa", "Indonesia") { toast("Bahasa lain segera hadir") }

        val logout = Ui.text(this, "Keluar", 15f, Color.parseColor("#EF4444"), true)
        logout.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#12122A"))
        bg.cornerRadius = d(24).toFloat()
        bg.setStroke(d(1), Color.parseColor("#EF4444"))
        logout.background = bg
        logout.setOnClickListener { confirmLogout() }
        list.addView(
            logout,
            LinearLayout.LayoutParams(MATCH, d(50)).apply { topMargin = d(28) }
        )

        setContentView(root)
    }

    /** Satu baris pengaturan: label kiri, nilai kanan. Mengembalikan TextView nilainya. */
    private fun row(label: String, value: String, onClick: () -> Unit): TextView {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.setPadding(d(16), 0, d(16), 0)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#12122A"))
        bg.cornerRadius = d(12).toFloat()
        bg.setStroke(d(1), Color.parseColor("#2E2E50"))
        h.background = bg

        h.addView(Ui.text(this, label, 15f, Ui.WHITE_SOFT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = Ui.text(this, value, 13f, Ui.GRAY)
        v.maxLines = 1
        v.ellipsize = TextUtils.TruncateAt.END
        v.maxWidth = d(210)
        h.addView(v)
        h.addView(Ui.text(this, "  ›", 18f, Ui.GRAY))
        h.setOnClickListener { onClick() }

        list.addView(h, LinearLayout.LayoutParams(MATCH, d(56)).apply { topMargin = d(10) })
        return v
    }

    private fun loadUserId(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val id = doc.getLong("userId") ?: 0L
                idValue.text = doc.getString("customId")?.takeIf { it.isNotEmpty() } ?: if (id > 0L) id.toString() else "-"
            }
            .addOnFailureListener { idValue.text = "-" }
    }

    private fun copy(label: String, text: String) {
        if (text.isBlank() || text == "-" || text == "…") return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("$label disalin")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Keluar dari akun?")
            .setMessage("Kamu perlu login lagi untuk memakai HAYU.")
            .setPositiveButton("Keluar") { _, _ ->
                auth.signOut()
                toast("Berhasil logout 👋")
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
