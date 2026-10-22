package com.hayu.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import java.io.File
import java.io.FileInputStream

/** Khusus Developer: upload file .svga (frame, car, gift, dll), pratinjau, hapus. */
class SvgaManagerActivity : AppCompatActivity() {

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedType = SvgaCatalog.GIFT
    private var pickedFile: File? = null
    private var uploading = false

    private lateinit var etName: EditText
    private lateinit var etPrice: EditText
    private lateinit var etEmoji: EditText
    private lateinit var tvFile: TextView
    private lateinit var preview: SVGAImageView
    private lateinit var progress: ProgressBar
    private lateinit var btnUpload: TextView
    private lateinit var listBox: LinearLayout
    private val typeChips = ArrayList<Pair<TextView, String>>()

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = auth.currentUser?.uid ?: run { finish(); return }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (Role.DEVELOPER !in Role.rolesOf(doc)) {
                    toast("Hanya untuk Developer")
                    finish()
                    return@addOnSuccessListener
                }
                buildUi()
                loadList()
            }
            .addOnFailureListener { finish() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun d(v: Int) = Ui.dp(this, v)

    private fun field(hintText: String, inputKind: Int): EditText {
        val et = EditText(this)
        et.hint = hintText
        et.inputType = inputKind
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Color.parseColor("#6B7280"))
        et.textSize = 14f
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#12122A"))
        bg.cornerRadius = d(10).toFloat()
        bg.setStroke(d(1), Color.parseColor("#2E2E50"))
        et.background = bg
        et.setPadding(d(12), d(10), d(12), d(10))
        return et
    }

    private fun buildUi() {
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
            Ui.text(this, "  SVGA Manager", 18f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        root.addView(header)

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(d(14), d(14), d(14), d(60))
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ---------- form upload ----------
        val form = Ui.card(this)
        form.addView(Ui.text(this, "⬆️ Upload SVGA baru", 16f, Ui.WHITE_SOFT, true))

        form.addView(Ui.text(this, "Jenis", 12f, Ui.GRAY).also { it.setPadding(0, d(10), 0, d(4)) })
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        for ((type, label) in SvgaCatalog.types) {
            val chip = Ui.text(this, label, 12f, Color.WHITE, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(d(6), d(8), d(6), d(8))
            chip.setOnClickListener {
                selectedType = type
                refreshChips()
            }
            typeChips.add(chip to type)
            chipRow.addView(
                chip,
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(d(2), 0, d(2), 0) }
            )
        }
        form.addView(chipRow)
        refreshChips()

        etName = field("Nama (mis. Mobil Sport, Bingkai Emas)", InputType.TYPE_CLASS_TEXT)
        etPrice = field("Harga koin (0 = gratis)", InputType.TYPE_CLASS_NUMBER)
        etEmoji = field("Emoji ikon (opsional)", InputType.TYPE_CLASS_TEXT)
        val fieldLp = { LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(8) } }
        form.addView(etName, fieldLp())
        form.addView(etPrice, fieldLp())
        form.addView(etEmoji, fieldLp())

        form.addView(
            Ui.button(this, "📂 Pilih file .svga") { picker.launch(arrayOf("*/*")) },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(12) }
        )
        tvFile = Ui.text(this, "Belum ada file", 12f, Ui.GRAY)
        tvFile.setPadding(0, d(6), 0, d(6))
        form.addView(tvFile)

        preview = SVGAImageView(this)
        val box = GradientDrawable()
        box.setColor(Color.parseColor("#12122A"))
        box.cornerRadius = d(12).toFloat()
        preview.background = box
        form.addView(preview, LinearLayout.LayoutParams(MATCH, d(190)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.visibility = android.view.View.GONE
        form.addView(progress, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(10) })

        btnUpload = Ui.button(this, "⬆️ Upload") { startUpload() }
        form.addView(btnUpload, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(10) })
        col.addView(form)

        // ---------- daftar aset ----------
        col.addView(Ui.text(this, "Aset terunggah", 16f, Ui.WHITE_SOFT, true).also {
            it.setPadding(0, d(8), 0, d(8))
        })
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        col.addView(listBox)

        setContentView(root)
    }

    private fun refreshChips() {
        for ((chip, type) in typeChips) {
            chip.setBackgroundResource(
                if (type == selectedType) R.drawable.bg_button_primary else R.drawable.bg_quick_item
            )
        }
    }

    // ---------- pilih & validasi file ----------

    private fun onPicked(uri: Uri) {
        var name = "file.svga"
        var size = -1L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (n >= 0) name = c.getString(n) ?: name
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
        if (!name.lowercase().endsWith(".svga")) {
            toast("File harus berekstensi .svga")
            return
        }
        val target = File(cacheDir, "pending_upload.svga")
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Tidak bisa membaca file")
            input.use { src -> target.outputStream().use { out -> src.copyTo(out) } }
        } catch (e: Exception) {
            toast("Gagal membaca file: ${e.message}")
            return
        }
        if (target.length() == 0L) {
            toast("File kosong")
            return
        }

        pickedFile = null
        tvFile.text = "⏳ Memeriksa $name ..."
        if (etName.text.isBlank()) etName.setText(name.substring(0, name.length - 5))

        // Parse lokal dulu: file rusak / bukan SVGA ditolak sebelum diunggah.
        SVGAParser(this).decodeFromInputStream(
            FileInputStream(target),
            "pending_${System.currentTimeMillis()}",
            object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    pickedFile = target
                    tvFile.text = "✅ $name • ${target.length() / 1024} KB"
                    preview.setVideoItem(videoItem)
                    preview.loops = 0
                    preview.startAnimation()
                }

                override fun onError() {
                    pickedFile = null
                    tvFile.text = "❌ $name bukan file SVGA yang valid"
                }
            },
            true
        )
    }

    // ---------- upload ----------

    private fun setUploading(on: Boolean) {
        uploading = on
        progress.progress = 0
        progress.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
        btnUpload.alpha = if (on) 0.45f else 1f
        btnUpload.text = if (on) "Mengunggah..." else "⬆️ Upload"
    }

    private fun failMessage(prefix: String, e: Exception): String {
        val m = e.message ?: "tidak diketahui"
        val hint = if (m.contains("permission", true) || m.contains("not authorized", true)) {
            "\nCek Firestore Rules untuk koleksi svgaAssets & svgaChunks."
        } else ""
        return "$prefix: $m$hint"
    }

    private fun startUpload() {
        if (uploading) return
        val file = pickedFile ?: run {
            toast("Pilih file SVGA yang valid dulu")
            return
        }
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            toast("Isi nama aset")
            return
        }
        val price = etPrice.text.toString().trim().toIntOrNull() ?: 0
        val emoji = etEmoji.text.toString().trim()
        val uid = auth.currentUser?.uid ?: return
        val type = selectedType

        setUploading(true)
        SvgaStore.upload(
            file,
            onProgress = { progress.progress = it },
            onDone = { assetId, chunks, err ->
                if (assetId == null) {
                    toast(failMessage("Upload gagal", err ?: IllegalStateException("tidak diketahui")))
                    setUploading(false)
                } else {
                    val data = mapOf(
                        "type" to type,
                        "name" to name,
                        "price" to price.toLong(),
                        "emoji" to emoji,
                        "url" to (SvgaStore.PREFIX + assetId),
                        "storagePath" to "",
                        "chunks" to chunks.toLong(),
                        "size" to file.length(),
                        "createdBy" to uid,
                        "createdAt" to System.currentTimeMillis()
                    )
                    db.collection(SvgaCatalog.COLLECTION).document(assetId).set(data)
                        .addOnSuccessListener {
                            toast("Berhasil diunggah 🎉")
                            resetForm()
                            setUploading(false)
                            loadList()
                        }
                        .addOnFailureListener { e ->
                            SvgaStore.deleteChunks(assetId) {}
                            toast(failMessage("Gagal menyimpan data", e))
                            setUploading(false)
                        }
                }
            }
        )
    }

    private fun resetForm() {
        etName.setText("")
        etPrice.setText("")
        etEmoji.setText("")
        pickedFile = null
        tvFile.text = "Belum ada file"
        preview.stopAnimation()
        preview.setImageDrawable(null)
    }

    // ---------- daftar ----------

    private fun loadList() {
        SvgaCatalog.load(null) { all ->
            listBox.removeAllViews()
            if (all.isEmpty()) {
                listBox.addView(Ui.text(this, "Belum ada aset.", 12f, Ui.GRAY))
                return@load
            }
            for (a in all) {
                val play = Ui.button(this, "▶") { SvgaOverlay.show(this, a.url) }
                val del = Ui.button(this, "🗑") { confirmDelete(a) }
                val right = LinearLayout(this)
                right.orientation = LinearLayout.HORIZONTAL
                right.addView(play)
                right.addView(del, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = d(6) })
                val priceText = if (a.price > 0) "🪙 ${a.price}" else "Gratis"
                val desc = SvgaCatalog.typeLabel(a.type) + " • " + priceText
                listBox.addView(Ui.row(this, a.emoji.ifBlank { "🎞️" }, a.name, desc, right))
            }
        }
    }

    private fun confirmDelete(a: SvgaCatalog.Asset) {
        AlertDialog.Builder(this)
            .setTitle("Hapus aset?")
            .setMessage("${a.name} akan dihapus untuk semua pengguna.")
            .setPositiveButton("Hapus") { _, _ ->
                if (a.storagePath.isNotEmpty()) {
                    FirebaseStorage.getInstance().reference.child(a.storagePath).delete()
                }
                File(cacheDir, "svga_${a.id}.svga").delete()
                SvgaStore.deleteChunks(a.id) {
                    db.collection(SvgaCatalog.COLLECTION).document(a.id).delete()
                        .addOnSuccessListener {
                            toast("Dihapus")
                            loadList()
                        }
                        .addOnFailureListener { e -> toast(failMessage("Gagal menghapus", e)) }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
