package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mode (extra "mode"):
 *  packages = paket & harga koin | ledger = riwayat transaksi koin
 *  wallet   = wallet coinseller (seller: saldo sendiri, admin: isi wallet) | send = coinseller kirim koin
 *  sales    = laporan penjualan coinseller
 */
class CoinAdminActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var mode = "ledger"
    private var myRole = Role.USER
    private var myRoles: Set<String> = emptySet()
    private var myRoles: Set<String> = emptySet()
    private var myUid = ""
    private var ready = false
    private var walletInfo = ""

    private lateinit var actionBar: LinearLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var etQuery: EditText
    private lateinit var tvInfo: TextView
    private lateinit var list: LinearLayout

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun fmt(n: Long): String = String.format(Locale("id", "ID"), "%,d", n)
    private fun timeStr(ms: Long): String = SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(ms))
    private fun num(doc: DocumentSnapshot, f: String): Long = (doc.get(f) as? Number)?.toLong() ?: 0L

    private fun isCoinAdmin() = Role.canAccessAny(myRoles, "coin")
    private fun isSeller() = Role.COINSELLER in myRoles

    private fun setInfo(s: String) {
        tvInfo.text = if (walletInfo.isEmpty()) s else "$walletInfo\n$s"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "ledger"
        buildUI(intent.getStringExtra("title") ?: "Koin")

        myUid = auth.currentUser?.uid ?: run { finish(); return }
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { doc ->
                myRoles = Role.rolesOf(doc)
                myRole = Role.top(myRoles)
                if (!allowed()) {
                    toast("Akses ditolak 🚫"); finish()
                } else {
                    ready = true
                    start()
                }
            }
            .addOnFailureListener { toast("Gagal cek akses"); finish() }
    }

    private fun allowed(): Boolean = when (mode) {
        "packages" -> isCoinAdmin() || isSeller() || Role.canAccessAny(myRoles, "transaction")
        "ledger" -> isCoinAdmin() || Role.canAccessAny(myRoles, "transaction")
        "wallet" -> isCoinAdmin() || isSeller()
        "send" -> isSeller()
        "sales" -> isCoinAdmin() || isSeller() || Role.canAccessAny(myRoles, "transaction")
        else -> false
    }

    private fun start() {
        when (mode) {
            "packages" -> {
                if (isCoinAdmin()) addAction("➕ Paket baru") { packageDialog(null) }
                loadPackages()
            }
            "ledger" -> loadLedger()
            "sales" -> loadSales()
            "wallet" -> if (isSeller()) loadMyWallet() else {
                searchBar.visibility = View.VISIBLE
                setInfo("Cari coinseller (nickname / UserID / UID), tap untuk isi wallet")
            }
            "send" -> {
                searchBar.visibility = View.VISIBLE
                refreshSellerBalance()
                setInfo("Cari customer (nickname / UserID / UID), tap untuk kirim koin")
            }
        }
    }

    // ---------------- UI ----------------
    private fun buildUI(title: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#080818"))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(Color.parseColor("#0D0D3D"))
        header.setPadding(d(16), d(14), d(16), d(14))
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
        actionBar.setPadding(d(12), d(8), d(12), 0)
        actionBar.visibility = View.GONE
        root.addView(actionBar)

        searchBar = LinearLayout(this)
        searchBar.orientation = LinearLayout.HORIZONTAL
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.setPadding(d(12), d(8), d(12), d(4))
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
        tvInfo.setPadding(d(16), d(8), d(16), d(8))
        root.addView(tvInfo)

        val sv = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(d(12), 0, d(12), d(80))
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun addAction(text: String, onClick: () -> Unit) {
        actionBar.visibility = View.VISIBLE
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        actionBar.addView(b)
    }

    private fun addRow(title: String, sub: String, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(d(14), d(12), d(14), d(12))
        val gd = GradientDrawable()
        gd.setColor(Color.parseColor("#12122A"))
        gd.cornerRadius = d(12).toFloat()
        gd.setStroke(d(1), Color.parseColor("#2E2E50"))
        row.background = gd

        val t1 = TextView(this)
        t1.text = title
        t1.textSize = 14f
        t1.setTextColor(Color.parseColor("#F1F5F9"))
        val t2 = TextView(this)
        t2.text = sub
        t2.textSize = 12f
        t2.setTextColor(Color.parseColor("#8892AA"))
        row.addView(t1)
        row.addView(t2)
        if (onClick != null) row.setOnClickListener { onClick() }

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = d(8)
        list.addView(row, lp)
    }

    private fun amountDialog(title: String, max: Long, onOk: (Long) -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.hint = "Jumlah koin (maks ${fmt(max)})"
        val pad = d(20)
        et.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("Lanjut") { _, _ ->
                val v = et.text.toString().trim().toLongOrNull()
                if (v == null || v <= 0L || v > max) toast("Masukkan angka 1 – ${fmt(max)}") else onOk(v)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------------- PAKET KOIN ----------------
    private fun loadPackages() {
        db.collection("coin_packages").get()
            .addOnSuccessListener { snap ->
                list.removeAllViews()
                val docs = snap.documents.sortedBy { num(it, "coins") }
                setInfo(if (docs.isEmpty()) "Belum ada paket" else "${docs.size} paket" + if (isCoinAdmin()) " — tap untuk ubah" else "")
                for (doc in docs) {
                    val off = if (doc.getBoolean("active") == false) "  • nonaktif" else ""
                    addRow("🪙 ${fmt(num(doc, "coins"))} koin", "Rp ${fmt(num(doc, "price"))}$off") {
                        if (isCoinAdmin()) packageMenu(doc)
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun packageMenu(doc: DocumentSnapshot) {
        val active = doc.getBoolean("active") != false
        val labels = arrayOf("✏️ Ubah", if (active) "⏸️ Nonaktifkan" else "▶️ Aktifkan", "🗑️ Hapus")
        AlertDialog.Builder(this)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> packageDialog(doc)
                    1 -> doc.reference.update("active", !active)
                        .addOnSuccessListener { loadPackages() }
                        .addOnFailureListener { toast("Gagal: ${it.message}") }
                    else -> AlertDialog.Builder(this)
                        .setMessage("Hapus paket ini?")
                        .setPositiveButton("Hapus") { _, _ ->
                            doc.reference.delete()
                                .addOnSuccessListener { loadPackages() }
                                .addOnFailureListener { toast("Gagal: ${it.message}") }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            .show()
    }

    private fun packageDialog(doc: DocumentSnapshot?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(d(20), d(12), d(20), d(4))
        val etCoins = EditText(this)
        etCoins.inputType = InputType.TYPE_CLASS_NUMBER
        etCoins.hint = "Jumlah koin"
        val etPrice = EditText(this)
        etPrice.inputType = InputType.TYPE_CLASS_NUMBER
        etPrice.hint = "Harga (Rp)"
        if (doc != null) {
            etCoins.setText(num(doc, "coins").toString())
            etPrice.setText(num(doc, "price").toString())
        }
        box.addView(etCoins)
        box.addView(etPrice)

        AlertDialog.Builder(this)
            .setTitle(if (doc == null) "Paket baru" else "Ubah paket")
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                val c = etCoins.text.toString().trim().toLongOrNull()
                val p = etPrice.text.toString().trim().toLongOrNull()
                if (c == null || p == null || c <= 0L || p < 0L) {
                    toast("Isi angka yang valid")
                } else if (doc == null) {
                    db.collection("coin_packages").add(
                        mapOf<String, Any>(
                            "coins" to c, "price" to p, "active" to true,
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                        .addOnSuccessListener { loadPackages() }
                        .addOnFailureListener { toast("Gagal: ${it.message}") }
                } else {
                    doc.reference.update(mapOf<String, Any>("coins" to c, "price" to p))
                        .addOnSuccessListener { loadPackages() }
                        .addOnFailureListener { toast("Gagal: ${it.message}") }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------------- LEDGER / PENJUALAN ----------------
    private fun typeLabel(t: String) = when (t) {
        "admin_adjust" -> "⚙️ Penyesuaian admin"
        "topup_approved" -> "💳 Top up disetujui"
        "seller_send" -> "📤 Penjualan coinseller"
        "seller_stock" -> "💼 Isi wallet coinseller"
        else -> "🪙 $t"
    }

    private fun renderLedger(docs: List<DocumentSnapshot>, emptyMsg: String) {
        list.removeAllViews()
        if (docs.isEmpty()) { setInfo(emptyMsg); return }
        for (doc in docs) {
            val delta = num(doc, "delta")
            val sign = if (delta >= 0) "+" else ""
            val who = doc.getString("name").orEmpty().ifEmpty { doc.getString("uid").orEmpty().take(6) }
            val bal = if (doc.get("balanceAfter") != null) "  • saldo ${fmt(num(doc, "balanceAfter"))}" else ""
            addRow(
                "${typeLabel(doc.getString("type").orEmpty())}  $sign${fmt(delta)}",
                "$who • ${timeStr(num(doc, "at"))}$bal"
            )
        }
    }

    private fun loadLedger() {
        db.collection("coin_ledger").orderBy("at", Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener { snap ->
                setInfo("50 transaksi terakhir")
                renderLedger(snap.documents, "Belum ada transaksi")
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadSales() {
        val q: Query = if (isSeller()) {
            db.collection("coin_ledger").whereEqualTo("byUid", myUid)
        } else {
            db.collection("coin_ledger").whereEqualTo("type", "seller_send")
        }
        q.limit(200).get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents
                    .filter { it.getString("type") == "seller_send" }
                    .sortedByDescending { num(it, "at") }
                val total = docs.sumOf { num(it, "delta") }
                setInfo("Terjual ${fmt(total)} koin • ${docs.size} transaksi")
                renderLedger(docs, "Belum ada penjualan")
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------------- WALLET COINSELLER ----------------
    private fun refreshSellerBalance() {
        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            walletInfo = "💼 Wallet kamu: ${fmt(num(me, "sellerWallet"))} koin"
            setInfo(if (mode == "send") "Cari customer, tap untuk kirim koin" else "")
        }
    }

    private fun loadMyWallet() {
        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            walletInfo = "💼 Saldo wallet: ${fmt(num(me, "sellerWallet"))} koin"
            db.collection("coin_ledger").whereEqualTo("uid", myUid).limit(100).get()
                .addOnSuccessListener { snap ->
                    val docs = snap.documents
                        .filter { it.getString("type") == "seller_stock" }
                        .sortedByDescending { num(it, "at") }
                    setInfo("Riwayat isi wallet")
                    renderLedger(docs, "Belum ada isi wallet dari admin")
                }
                .addOnFailureListener { toast("Gagal: ${it.message}") }
        }.addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------------- CARI USER (send / wallet-admin) ----------------
    private fun doSearch() {
        if (!ready) { toast("Tunggu, mengecek akses…"); return }
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
                renderUsers(seen.values.toList())
            }
            .addOnFailureListener { toast("Gagal cari: ${it.message}") }
    }

    private fun renderUsers(docs: List<DocumentSnapshot>) {
        list.removeAllViews()
        val shown = docs.filter { u ->
            if (mode == "wallet") Role.COINSELLER in Role.rolesOf(u) else u.id != myUid
        }
        if (shown.isEmpty()) {
            setInfo(if (mode == "wallet") "Coinseller tidak ditemukan 😕" else "User tidak ditemukan 😕")
            return
        }
        setInfo("Ditemukan ${shown.size} user")
        for (u in shown) {
            val nick = u.getString("nickname").orEmpty().ifEmpty { "Pengguna HAYU" }
            val extra = if (mode == "wallet") "  💼 ${fmt(num(u, "sellerWallet"))}" else ""
            addRow("$nick • ID ${num(u, "userId")}", "🪙 ${fmt(num(u, "coins"))}$extra") {
                if (mode == "send") {
                    amountDialog("Kirim koin ke $nick", 1_000_000L) { sendCoins(u.id, nick, it) }
                } else {
                    amountDialog("Isi wallet $nick", 10_000_000L) { stockCoins(u.id, nick, it) }
                }
            }
        }
    }

    // Coinseller -> customer (wallet berkurang, koin customer bertambah, tercatat di ledger)
    private fun sendCoins(targetUid: String, name: String, amount: Long) {
        if (targetUid == myUid) { toast("Tidak bisa kirim ke diri sendiri"); return }
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(targetUid)
        val ledgerRef = db.collection("coin_ledger").document()

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val target = tx.get(targetRef)
            if (Role.COINSELLER !in Role.rolesOf(actor)) {
                throw IllegalStateException("Hanya coinseller yang boleh mengirim koin")
            }
            if (!target.exists()) throw IllegalStateException("User tidak ditemukan")
            val wallet = num(actor, "sellerWallet")
            if (wallet < amount) throw IllegalStateException("Wallet tidak cukup (sisa $wallet)")
            val newCoins = num(target, "coins") + amount
            tx.update(actorRef, "sellerWallet", wallet - amount)
            tx.update(targetRef, "coins", newCoins)
            tx.set(
                ledgerRef, mapOf<String, Any>(
                    "type" to "seller_send", "uid" to targetUid, "name" to name,
                    "delta" to amount, "balanceAfter" to newCoins,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener {
                toast("✅ ${fmt(amount)} koin terkirim ke $name")
                refreshSellerBalance()
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // Admin isi wallet coinseller
    private fun stockCoins(targetUid: String, name: String, amount: Long) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(targetUid)
        val ledgerRef = db.collection("coin_ledger").document()

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val target = tx.get(targetRef)
            if (!Role.canAccessAny(Role.rolesOf(actor), "coin")) {
                throw IllegalStateException("Kamu tidak boleh mengisi wallet")
            }
            if (Role.COINSELLER !in Role.rolesOf(target)) {
                throw IllegalStateException("Target bukan coinseller")
            }
            val newWallet = num(target, "sellerWallet") + amount
            tx.update(targetRef, "sellerWallet", newWallet)
            tx.set(
                ledgerRef, mapOf<String, Any>(
                    "type" to "seller_stock", "uid" to targetUid, "name" to name,
                    "delta" to amount, "balanceAfter" to newWallet,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener {
                toast("✅ Wallet $name +${fmt(amount)}")
                doSearch()
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
