package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

/**
 * Panel developer: atur peluang cashback Lucky Gift.
 * Tiap baris = pengali (x) dan bobot (w). Peluang = bobot / total bobot.
 * Disimpan di Firestore app_config/lucky_gift -> "table". Pengali maksimal x500.
 */
class LuckyConfigActivity : AppCompatActivity() {

    private class Row(val view: LinearLayout, val etX: EditText, val etW: EditText, val tvP: TextView)

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val rows = ArrayList<Row>()
    private lateinit var rowsBox: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var btnSave: TextView

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = Role.top(Role.rolesOf(doc))
                if (!Role.canAccess(role, "all")) {
                    toast("Akses ditolak")
                    finish()
                } else {
                    buildUI()
                    GiftSheet.LuckyOdds.load(db) {
                        if (!isFinishing && !isDestroyed) fillRows(GiftSheet.LuckyOdds.table)
                    }
                }
            }
            .addOnFailureListener { finish() }
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#080818"))

        val header = TextView(this)
        header.text = "‹  Lucky Gift • Cashback"
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
        info.text = "Tiap hadiah Lucky Gift diundi satu pengali cashback (x harga hadiah). " +
            "Peluang = bobot ÷ total bobot. Pengali 0 = tidak dapat cashback. Maksimal x${GiftSheet.LuckyOdds.MAX_X}."
        info.textSize = 12f
        info.setTextColor(Color.parseColor("#8892AA"))
        info.setPadding(0, 0, 0, d(10))
        box.addView(info)

        tvSummary = TextView(this)
        tvSummary.textSize = 13f
        tvSummary.setTextColor(Color.parseColor("#7CF29A"))
        tvSummary.setTypeface(tvSummary.typeface, Typeface.BOLD)
        tvSummary.setPadding(0, 0, 0, d(10))
        box.addView(tvSummary)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(colLabel("Pengali (x)"), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(colLabel("Bobot"), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(colLabel("Peluang"), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this), LinearLayout.LayoutParams(d(44), -2))
        box.addView(head)

        rowsBox = LinearLayout(this)
        rowsBox.orientation = LinearLayout.VERTICAL
        box.addView(rowsBox)

        box.addView(button("+ Tambah baris", "#2A2A45") { addRow(1, 1); updateSummary() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })

        btnSave = button("Simpan", "#7C3AED") { save() }
        box.addView(btnSave, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        box.addView(button("Kembalikan ke bawaan", "#1C1C35") {
            fillRows(GiftSheet.LuckyOdds.DEFAULT)
            toast("Nilai bawaan dimuat. Tekan Simpan untuk menerapkan.")
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        sv.addView(box)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun colLabel(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 11f
        v.setTextColor(Color.parseColor("#8892AA"))
        return v
    }

    private fun button(label: String, color: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, d(13), 0, d(13))
        t.background = GradientDrawable().apply {
            setColor(Color.parseColor(color)); cornerRadius = d(12).toFloat()
        }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun field(value: Int): EditText {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setTextColor(Color.WHITE)
        et.setText(value.toString())
        et.setPadding(d(10), d(8), d(10), d(8))
        et.background = GradientDrawable().apply {
            setColor(Color.parseColor("#12122A")); cornerRadius = d(10).toFloat()
            setStroke(d(1), Color.parseColor("#2E2E50"))
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateSummary() }
        })
        return et
    }

    private fun addRow(x: Int, w: Int) {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL

        val etX = field(x)
        val etW = field(w)
        val tvP = TextView(this)
        tvP.textSize = 13f
        tvP.setTextColor(Color.parseColor("#C4CAFF"))
        tvP.setPadding(d(8), 0, 0, 0)

        val del = TextView(this)
        del.text = "✕"
        del.textSize = 16f
        del.gravity = Gravity.CENTER
        del.setTextColor(Color.parseColor("#FF6B6B"))

        line.addView(etX, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = d(6) })
        line.addView(etW, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = d(6) })
        line.addView(tvP, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(del, LinearLayout.LayoutParams(d(44), d(40)))

        val row = Row(line, etX, etW, tvP)
        del.setOnClickListener {
            rowsBox.removeView(line)
            rows.remove(row)
            updateSummary()
        }
        rows.add(row)
        rowsBox.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })
    }

    private fun fillRows(table: List<Pair<Int, Int>>) {
        rowsBox.removeAllViews()
        rows.clear()
        table.sortedBy { it.first }.forEach { addRow(it.first, it.second) }
        updateSummary()
    }

    /** Baca isian; null kalau ada yang tidak valid (isi null berarti baris dilewati saat hitung ringkasan). */
    private fun read(r: Row): Pair<Int, Int>? {
        val x = r.etX.text.toString().trim().toIntOrNull() ?: return null
        val w = r.etW.text.toString().trim().toIntOrNull() ?: return null
        if (x < 0 || x > GiftSheet.LuckyOdds.MAX_X || w <= 0) return null
        return Pair(x, w)
    }

    private fun updateSummary() {
        if (!::tvSummary.isInitialized) return
        val valid = rows.mapNotNull { read(it) }
        val total = valid.sumOf { it.second }.toDouble()
        if (total <= 0.0) {
            tvSummary.text = "Belum ada baris yang valid"
            rows.forEach { it.tvP.text = "-" }
            return
        }
        val avg = valid.sumOf { it.first.toDouble() * it.second } / total
        tvSummary.text = String.format(Locale.US, "Rata-rata cashback: x%.2f dari harga hadiah", avg)
        tvSummary.setTextColor(Color.parseColor(if (avg > 1.0) "#FF6B6B" else "#7CF29A"))
        for (r in rows) {
            val v = read(r)
            r.tvP.text = if (v == null) "⚠" else String.format(Locale.US, "%.3f%%", v.second * 100.0 / total)
        }
    }

    private fun save() {
        val parsed = rows.map { read(it) }
        if (parsed.isEmpty() || parsed.any { it == null }) {
            toast("Periksa isian: pengali 0–${GiftSheet.LuckyOdds.MAX_X}, bobot minimal 1")
            return
        }
        val table = parsed.map { it!! }
        val total = table.sumOf { it.second }.toDouble()
        val avg = table.sumOf { it.first.toDouble() * it.second } / total

        val doSave = {
            btnSave.isEnabled = false
            btnSave.alpha = 0.5f
            val data = mapOf(
                "table" to table.map { mapOf("x" to it.first, "w" to it.second) },
                "updatedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("app_config").document("lucky_gift").set(data)
                .addOnSuccessListener {
                    btnSave.isEnabled = true; btnSave.alpha = 1f
                    Toast.makeText(this, "Tersimpan ✓", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    btnSave.isEnabled = true; btnSave.alpha = 1f
                    toast("Gagal simpan: ${e.localizedMessage}")
                }
        }

        if (avg > 1.0) {
            AlertDialog.Builder(this)
                .setTitle("Cashback melebihi harga")
                .setMessage(
                    String.format(
                        Locale.US,
                        "Rata-rata cashback x%.2f (di atas x1). Koin di aplikasi akan terus bertambah dari Lucky Gift. Tetap simpan?",
                        avg
                    )
                )
                .setPositiveButton("Simpan") { _, _ -> doSave() }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            doSave()
        }
    }
}
