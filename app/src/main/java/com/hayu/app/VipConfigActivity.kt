// file: app/src/main/java/com/hayu/app/VipConfigActivity.kt
// Panel Developer: ubah harga VIP (7 level) dan threshold poin SVIP (7 level)
// Data disimpan di Firestore: app_config/vip_config
//   vip_prices: [100, 300, 600, 1000, 1500, 2200, 3000]
//   svip_points: [1000, 3000, 6000, 10000, 15000, 25000, 40000]
// VipActivity & SvipActivity membaca dari sini kalau ada, fallback ke companion object.

package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class VipConfigActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val configRef get() = db.collection("app_config").document("vip_config")

    private val vipFields = Array(7) { EditText(this) }
    private val svipFields = Array(7) { EditText(this) }

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
        val tvTitle = TextView(this).apply {
            text = "  ⭐ Konfigurasi VIP & SVIP"
            textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val sv = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(80))
        }

        // ── Harga VIP ─────────────────────────────────────────────────────────
        container.addView(sectionHeader("💎 Harga VIP per Level (koin)"))
        container.addView(hint("Berapa koin yang harus dibayar user untuk naik ke level VIP tsb."))
        for (i in 0..6) {
            container.addView(fieldRow("VIP ${i + 1}", vipFields[i]))
        }

        // ── Threshold SVIP ────────────────────────────────────────────────────
        container.addView(sectionHeader("👑 Poin Top-Up untuk SVIP (poin)"))
        container.addView(hint("Berapa total poin top-up yang dibutuhkan untuk mencapai level SVIP tsb."))
        for (i in 0..6) {
            container.addView(fieldRow("SVIP ${i + 1}", svipFields[i]))
        }

        // Tombol Simpan
        val saveBtn = TextView(this).apply {
            text = "💾 Simpan Konfigurasi"
            textSize = 16f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6C63FF")); cornerRadius = dp(12).toFloat()
            }
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { saveConfig() }
        }
        val saveLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        saveLp.topMargin = dp(24)
        container.addView(saveBtn, saveLp)

        sv.addView(container)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        loadConfig()
    }

    private fun loadConfig() {
        configRef.get().addOnSuccessListener { doc ->
            // Baca dari Firestore, fallback ke default VipActivity
            @Suppress("UNCHECKED_CAST")
            val prices = (doc.get("vip_prices") as? List<Long>)?.map { it.toInt() }
                ?: VipActivity.VIP_PRICES.toList()
            @Suppress("UNCHECKED_CAST")
            val points = (doc.get("svip_points") as? List<Long>)?.map { it.toInt() }
                ?: VipActivity.SVIP_POINTS.toList()

            for (i in 0..6) {
                vipFields[i].setText(prices.getOrNull(i)?.toString() ?: "")
                svipFields[i].setText(points.getOrNull(i)?.toString() ?: "")
            }
        }.addOnFailureListener {
            // Isi default kalau gagal load
            for (i in 0..6) {
                vipFields[i].setText(VipActivity.VIP_PRICES[i].toString())
                svipFields[i].setText(VipActivity.SVIP_POINTS[i].toString())
            }
            toast("Gagal load config, menampilkan default")
        }
    }

    private fun saveConfig() {
        val vipPrices = mutableListOf<Long>()
        val svipPoints = mutableListOf<Long>()

        for (i in 0..6) {
            val v = vipFields[i].text.toString().trim().toLongOrNull()
            val s = svipFields[i].text.toString().trim().toLongOrNull()
            if (v == null || v <= 0) { toast("Harga VIP ${i + 1} tidak valid"); return }
            if (s == null || s <= 0) { toast("Poin SVIP ${i + 1} tidak valid"); return }
            if (i > 0 && v <= vipPrices[i - 1]) { toast("Harga VIP ${i + 1} harus lebih besar dari VIP $i"); return }
            if (i > 0 && s <= svipPoints[i - 1]) { toast("Poin SVIP ${i + 1} harus lebih besar dari SVIP $i"); return }
            vipPrices.add(v)
            svipPoints.add(s)
        }

        configRef.set(mapOf(
            "vip_prices"  to vipPrices,
            "svip_points" to svipPoints,
            "updatedAt"   to System.currentTimeMillis()
        )).addOnSuccessListener {
            toast("✅ Konfigurasi VIP & SVIP tersimpan!")
        }.addOnFailureListener {
            toast("❌ Gagal simpan: ${it.message}")
        }
    }

    // ── Helper UI ─────────────────────────────────────────────────────────────

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title; textSize = 15f; setTextColor(Color.parseColor("#A78BFA"))
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Color.parseColor("#888AAA"))
        setPadding(0, 0, 0, dp(8))
    }

    private fun fieldRow(label: String, field: EditText): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        val tv = TextView(this).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE)
        }
        row.addView(tv, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))

        field.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 14f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12122A"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#2E2E50"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
