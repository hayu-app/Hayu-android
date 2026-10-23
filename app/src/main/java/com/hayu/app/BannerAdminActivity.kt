package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.util.ImageUtil
import com.hayu.app.util.ImgbbUploader

/**
 * Panel developer: tambah / ubah / hapus / urutkan banner beranda.
 * Disimpan di Firestore app_config/home_banners -> "items".
 */
class BannerAdminActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val banners = ArrayList<HomeBannerAdapter.Banner>()
    private lateinit var listBox: LinearLayout

    private var pickedUri: Uri? = null
    private var pickLabel: TextView? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickLabel?.text = "✅ Gambar baru dipilih"
        }
    }

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
        header.text = "‹  Banner Beranda"
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
        info.text = "Banner geser di atas beranda. Bisa gambar (disarankan lebar sekitar 2:1) atau teks dengan warna. Kalau daftar kosong, beranda memakai banner bawaan."
        info.textSize = 12f
        info.setTextColor(Color.parseColor("#8892AA"))
        info.setPadding(0, 0, 0, d(12))
        box.addView(info)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        box.addView(listBox)

        box.addView(bigButton("+ Tambah Banner", "#7C3AED") { showEditor(null) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
        box.addView(bigButton("Kembalikan ke bawaan", "#2A2A45") { resetDefault() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        sv.addView(box)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun bigButton(label: String, color: String, onClick: () -> Unit): TextView {
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

    private fun smallButton(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 12f
        t.setTextColor(Color.parseColor("#C4CAFF"))
        t.setPadding(d(12), d(8), d(12), d(8))
        t.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1C1C35")); cornerRadius = d(8).toFloat()
        }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun load() {
        db.collection("app_config").document("home_banners").get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                banners.clear()
                HomeBannerAdapter.fromConfig(doc.get("items"))?.let { banners.addAll(it) }
                render()
            }
            .addOnFailureListener { toast("Gagal memuat: ${it.localizedMessage}"); render() }
    }

    private fun render() {
        listBox.removeAllViews()
        if (banners.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Belum ada banner kustom (memakai banner bawaan)"
            empty.setTextColor(Color.parseColor("#8892AA"))
            empty.textSize = 13f
            listBox.addView(empty)
            return
        }
        banners.forEachIndexed { i, b ->
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(d(14), d(12), d(14), d(12))
            card.background = GradientDrawable().apply {
                setColor(Color.parseColor("#12122A")); cornerRadius = d(12).toFloat()
                setStroke(d(1), Color.parseColor("#2E2E50"))
            }

            val t1 = TextView(this)
            t1.text = "${i + 1}.  " + b.title.ifBlank { "(tanpa judul)" }
            t1.textSize = 14f
            t1.setTextColor(Color.WHITE)
            t1.setTypeface(t1.typeface, Typeface.BOLD)
            card.addView(t1)

            val t2 = TextView(this)
            t2.text = (if (b.imageUrl.isNotBlank()) "🖼 Gambar" else "🎨 Warna") +
                (if (b.subtitle.isNotBlank()) "  •  ${b.subtitle}" else "")
            t2.textSize = 12f
            t2.setTextColor(Color.parseColor("#8892AA"))
            card.addView(t2)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, d(8), 0, 0)
            if (i > 0) row.addView(smallButton("↑") { move(i, -1) })
            if (i < banners.size - 1) row.addView(smallButton("↓") { move(i, 1) },
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = d(6) })
            row.addView(smallButton("Edit") { showEditor(i) },
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = d(6) })
            row.addView(smallButton("Hapus") { confirmDelete(i) },
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = d(6) })
            card.addView(row)

            listBox.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(8) })
        }
    }

    private fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in banners.indices) return
        val tmp = banners[i]; banners[i] = banners[j]; banners[j] = tmp
        save("Urutan disimpan")
    }

    private fun confirmDelete(i: Int) {
        AlertDialog.Builder(this)
            .setTitle("Hapus banner")
            .setMessage("Hapus banner ini dari beranda?")
            .setPositiveButton("Hapus") { _, _ -> banners.removeAt(i); save("Banner dihapus") }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun resetDefault() {
        AlertDialog.Builder(this)
            .setTitle("Kembalikan ke bawaan")
            .setMessage("Semua banner kustom dihapus dan beranda memakai banner bawaan.")
            .setPositiveButton("Ya") { _, _ -> banners.clear(); save("Dikembalikan ke bawaan") }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun save(okMsg: String) {
        render()
        val data = mapOf(
            "items" to banners.map { HomeBannerAdapter.toMap(it) },
            "updatedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("app_config").document("home_banners").set(data)
            .addOnSuccessListener { Toast.makeText(this, okMsg + " ✓", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { toast("Gagal simpan: ${it.localizedMessage}"); load() }
    }

    private fun showEditor(index: Int?) {
        val old = index?.let { banners[it] }
        pickedUri = null
        var curUrl = old?.imageUrl ?: ""

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(d(20), d(12), d(20), 0)

        val etTitle = EditText(this)
        etTitle.hint = "Judul (opsional kalau pakai gambar)"
        etTitle.setText(old?.title ?: "")
        form.addView(etTitle)

        val etSub = EditText(this)
        etSub.hint = "Subjudul (opsional)"
        etSub.setText(old?.subtitle ?: "")
        form.addView(etSub)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            HomeBannerAdapter.PRESETS.map { "Warna: ${it.first}" }
        )
        val presetIdx = HomeBannerAdapter.PRESETS.indexOfFirst { it.second == old?.startColor }
        spinner.setSelection(if (presetIdx >= 0) presetIdx else 0)
        form.addView(spinner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        val tvPick = TextView(this)
        tvPick.text = if (curUrl.isNotBlank()) "🖼 Ganti gambar (sudah ada gambar)" else "🖼 Pilih gambar"
        tvPick.textSize = 14f
        tvPick.setPadding(0, d(14), 0, d(6))
        tvPick.setOnClickListener { pickImage.launch("image/*") }
        pickLabel = tvPick
        form.addView(tvPick)

        if (curUrl.isNotBlank()) {
            val tvClear = TextView(this)
            tvClear.text = "✕ Hapus gambar"
            tvClear.textSize = 13f
            tvClear.setPadding(0, d(6), 0, d(6))
            tvClear.setOnClickListener {
                curUrl = ""
                pickedUri = null
                tvPick.text = "🖼 Pilih gambar"
                tvClear.text = "Gambar akan dihapus saat disimpan"
            }
            form.addView(tvClear)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (index == null) "Tambah banner" else "Edit banner")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnDismissListener { pickLabel = null }
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val preset = HomeBannerAdapter.PRESETS[spinner.selectedItemPosition]
            val title = etTitle.text.toString().trim()
            val sub = etSub.text.toString().trim()
            val uri = pickedUri

            if (title.isEmpty() && curUrl.isEmpty() && uri == null) {
                toast("Isi judul atau pilih gambar")
                return@setOnClickListener
            }

            fun finish(url: String) {
                val nb = HomeBannerAdapter.Banner(title, sub, preset.second, preset.third, url)
                if (index == null) banners.add(nb) else banners[index] = nb
                dialog.dismiss()
                save("Banner disimpan")
            }

            if (uri != null) {
                val f = ImageUtil.uriToFile(this, uri)
                if (f == null) { toast("Gagal membaca gambar"); return@setOnClickListener }
                val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                btn.isEnabled = false
                btn.text = "Upload..."
                ImgbbUploader.uploadImage(
                    imageFile = f,
                    onSuccess = { url -> runOnUiThread { finish(url) } },
                    onError = { err ->
                        runOnUiThread {
                            btn.isEnabled = true
                            btn.text = "Simpan"
                            toast("Gagal upload: $err")
                        }
                    }
                )
            } else {
                finish(curUrl)
            }
        }
    }
}
