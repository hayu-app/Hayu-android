package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Basis layar daftar: header, tombol aksi, kolom cari user, info, dan list baris. */
abstract class ListPageActivity : AppCompatActivity() {

    protected val db by lazy { FirebaseFirestore.getInstance() }
    protected val auth by lazy { FirebaseAuth.getInstance() }
    protected var myUid = ""
    protected var myRole = Role.USER
    protected var myDoc: DocumentSnapshot? = null
    protected var mode = ""

    private lateinit var actionBar: LinearLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var etQuery: EditText
    private lateinit var tvInfo: TextView
    private lateinit var list: LinearLayout

    protected abstract fun allowed(): Boolean
    protected abstract fun onReady()
    protected open fun onUserResults(docs: List<DocumentSnapshot>) {}

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    protected fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    protected fun fmt(n: Long): String = String.format(Locale("id", "ID"), "%,d", n)
    protected fun timeStr(ms: Long): String =
        SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(ms))
    protected fun num(doc: DocumentSnapshot, f: String): Long = (doc.get(f) as? Number)?.toLong() ?: 0L
    protected fun nick(doc: DocumentSnapshot): String =
        doc.getString("nickname").orEmpty().ifEmpty { "Pengguna HAYU" }
    protected fun medal(i: Int): String = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: ""
        buildUI(intent.getStringExtra("title") ?: "")

        myUid = auth.currentUser?.uid ?: run { finish(); return }
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { doc ->
                myDoc = doc
                myRole = Role.normalize(doc.getString("role"))
                if (!allowed()) {
                    toast("Akses ditolak 🚫"); finish()
                } else {
                    onReady()
                }
            }
            .addOnFailureListener { toast("Gagal cek akses"); finish() }
    }

    private fun buildUI(title: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#080818"))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(Color.parseColor("#0D0D3D"))
        header.setPadding(dp(16), dp(14), dp(16), dp(14))
        val back = TextView(this)
        back.text = "‹  $title"
        back.textSize = 18f
        back.typeface = Typeface.DEFAULT_BOLD
        back.setTextColor(Color.WHITE)
        back.setOnClickListener { finish() }
        header.addView(back)
        root.addView(header)

        actionBar = LinearLayout(this)
        actionBar.orientation = LinearLayout.HORIZONTAL
        actionBar.setPadding(dp(12), dp(8), dp(12), 0)
        actionBar.visibility = View.GONE
        root.addView(actionBar)

        searchBar = LinearLayout(this)
        searchBar.orientation = LinearLayout.HORIZONTAL
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.setPadding(dp(12), dp(8), dp(12), dp(4))
        searchBar.visibility = View.GONE
        etQuery = EditText(this)
        etQuery.hint = "Nickname / UserID / UID"
        etQuery.setTextColor(Color.WHITE)
        etQuery.setHintTextColor(Color.parseColor("#8892AA"))
        etQuery.setSingleLine(true)
        searchBar.addView(etQuery, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val btn = Button(this)
        btn.text = "Cari"
        btn.setOnClickListener { doSearch() }
        searchBar.addView(btn)
        root.addView(searchBar)

        tvInfo = TextView(this)
        tvInfo.setTextColor(Color.parseColor("#C4CAFF"))
        tvInfo.textSize = 12f
        tvInfo.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.addView(tvInfo)

        val sv = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(12), 0, dp(12), dp(80))
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    protected fun setInfo(s: String) { tvInfo.text = s }
    protected fun clearList() { list.removeAllViews() }
    protected fun showSearch() { searchBar.visibility = View.VISIBLE }
    protected fun clearActions() { actionBar.removeAllViews(); actionBar.visibility = View.GONE }

    protected fun addAction(text: String, onClick: () -> Unit) {
        actionBar.visibility = View.VISIBLE
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        actionBar.addView(b)
    }

    protected fun addRow(title: String, sub: String, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dp(14), dp(12), dp(14), dp(12))
        val gd = GradientDrawable()
        gd.setColor(Color.parseColor("#12122A"))
        gd.cornerRadius = dp(12).toFloat()
        gd.setStroke(dp(1), Color.parseColor("#2E2E50"))
        row.background = gd

        val t1 = TextView(this)
        t1.text = title
        t1.textSize = 14f
        t1.setTextColor(Color.parseColor("#F1F5F9"))
        row.addView(t1)
        if (sub.isNotEmpty()) {
            val t2 = TextView(this)
            t2.text = sub
            t2.textSize = 12f
            t2.setTextColor(Color.parseColor("#8892AA"))
            row.addView(t2)
        }
        if (onClick != null) row.setOnClickListener { onClick() }

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        list.addView(row, lp)
    }

    protected fun confirm(title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ya") { _, _ -> onYes() }
            .setNegativeButton("Batal", null)
            .show()
    }

    protected fun textDialog(title: String, hint: String, initial: String = "", onOk: (String) -> Unit) {
        val et = EditText(this)
        et.hint = hint
        et.setText(initial)
        et.minLines = 1
        et.maxLines = 6
        val pad = dp(20)
        et.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isEmpty()) toast("Tidak boleh kosong") else onOk(t)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Cari user: UserID (angka), UID, atau nickname (prefix, tidak sensitif huruf besar/kecil)
    protected fun doSearch() {
        val q = etQuery.text.toString().trim()
        if (q.isEmpty()) { toast("Ketik nickname, UserID, atau UID dulu"); return }
        setInfo("Mencari…")

        val users = db.collection("users")
        val tasks = mutableListOf<Task<QuerySnapshot>>()
        q.toLongOrNull()?.let { tasks.add(users.whereEqualTo("userId", it).limit(10).get()) }
        if (q.length >= 20 && !q.contains(' ')) {
            tasks.add(users.whereEqualTo(FieldPath.documentId(), q).get())
        }
        for (v in linkedSetOf(q, q.lowercase(), q.replaceFirstChar { it.uppercase() })) {
            tasks.add(
                users.whereGreaterThanOrEqualTo("nickname", v)
                    .whereLessThanOrEqualTo("nickname", v + "\uf8ff")
                    .limit(30).get()
            )
        }

        Tasks.whenAllSuccess<QuerySnapshot>(tasks)
            .addOnSuccessListener { snaps ->
                val seen = linkedMapOf<String, DocumentSnapshot>()
                snaps.forEach { s -> s.documents.forEach { seen.putIfAbsent(it.id, it) } }
                onUserResults(seen.values.toList())
            }
            .addOnFailureListener { toast("Gagal cari: ${it.message}") }
    }
}
