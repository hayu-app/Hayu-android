package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Panel developer: atur jumlah koin check-in harian hari 1..7. Disimpan di app_config/daily_checkin. */
class CheckinConfigActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val fields = ArrayList<EditText>()
    private lateinit var btnSave: TextView

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = Role.top(Role.rolesOf(doc))
                if (!Role.canAccess(role, "all")) {
                    Toast.makeText(this, "Akses ditolak", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    buildUI()
                    load()
                }
            }
            .addOnFailureListener { finish() }
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#080818"))

        val header = TextView(this)
        header.text = "‹  Check-in Harian"
        header.textSize = 18f
        header.setTextColor(Color.WHITE)
        header.setTypeface(header.typeface, Typeface.BOLD)
        header.setBackgroundColor(Color.parseColor("#0D0D3D"))
        header.setPadding(d(16), d(14), d(16), d(14))
        header.setOnClickListener { finish() }
        root.addView(header)

        val sv = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(d(16), d(16), d(16), d(80))

        val info = TextView(this)
        info.text = "Jumlah koin yang didapat user pada tiap hari check-in. Setelah hari ke-7 siklus mulai lagi dari hari 1."
        info.textSize = 12f
        info.setTextColor(Color.parseColor("#8892AA"))
        info.setPadding(0, 0, 0, d(12))
        box.addView(info)

        for (day in 1..7) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, d(4), 0, d(4))

            val label = TextView(this)
            label.text = "Hari $day"
            label.textSize = 14f
            label.setTextColor(Color.WHITE)
            row.addView(label, LinearLayout.LayoutParams(d(80), -2))

            val et = EditText(this)
            et.inputType = InputType.TYPE_CLASS_NUMBER
            et.setTextColor(Color.WHITE)
            et.setText(DailyCoinActivity.DEFAULT_REWARDS[day - 1].toString())
            et.background = GradientDrawable().apply {
                setColor(Color.parseColor("#12122A"))
                cornerRadius = d(10).toFloat()
                setStroke(d(1), Color.parseColor("#2E2E50"))
            }
            et.setPadding(d(12), d(10), d(12), d(10))
            fields.add(et)
            row.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(row)
        }

        btnSave = TextView(this)
        btnSave.text = "Simpan"
        btnSave.textSize = 15f
        btnSave.gravity = Gravity.CENTER
        btnSave.setTextColor(Color.WHITE)
        btnSave.setTypeface(btnSave.typeface, Typeface.BOLD)
        btnSave.setPadding(0, d(14), 0, d(14))
        btnSave.background = GradientDrawable().apply {
            setColor(Color.parseColor("#7C3AED")); cornerRadius = d(12).toFloat()
        }
        btnSave.setOnClickListener { save() }
        box.addView(btnSave, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(16) })

        val btnReset = TextView(this)
        btnReset.text = "Kembalikan ke default"
        btnReset.textSize = 13f
        btnReset.gravity = Gravity.CENTER
        btnReset.setTextColor(Color.parseColor("#A78BFA"))
        btnReset.setPadding(0, d(14), 0, d(14))
        btnReset.setOnClickListener {
            DailyCoinActivity.DEFAULT_REWARDS.forEachIndexed { i, v -> fields[i].setText(v.toString()) }
        }
        box.addView(btnReset)

        sv.addView(box)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun load() {
        db.collection("app_config").document("daily_checkin").get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val r = DailyCoinActivity.parseRewards(doc.get("rewards"))
                r.forEachIndexed { i, v -> fields[i].setText(v.toString()) }
            }
    }

    private fun save() {
        val values = fields.map { it.text.toString().trim().toLongOrNull() }
        if (values.any { it == null || it < 0L }) {
            Toast.makeText(this, "Isi semua hari dengan angka 0 atau lebih", Toast.LENGTH_SHORT).show()
            return
        }
        btnSave.isEnabled = false
        btnSave.alpha = 0.5f
        val data = mapOf(
            "rewards" to values.map { it!! },
            "updatedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("app_config").document("daily_checkin").set(data)
            .addOnSuccessListener {
                btnSave.isEnabled = true; btnSave.alpha = 1f
                Toast.makeText(this, "Tersimpan ✓", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true; btnSave.alpha = 1f
                Toast.makeText(this, "Gagal simpan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
}
