#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch 4: BATCH CS / AGENCY / HOST / BD
#  BD     : daftarkan agency, verifikasi agency, dashboard, performa, laporan, ranking
#  Agency : rekrut host, verifikasi host, absensi, performa, manajemen member
#  Host   : dashboard, absen harian, ranking, level, tugas
#  CS     : support ticket (user kirim, CS balas), komplain per kategori, histori, laporan, pemulihan akun
#   cd ~/Hayu-android && bash patch-org.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
grep -q "coinModes" "$D/PanelDetailActivity.kt" || { echo "❌ Jalankan patch-role.sh, patch-panel.sh, patch-koin.sh dulu"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python

echo "▶ 1/6 ListPageActivity.kt (basis layar)"
cat > "$D/ListPageActivity.kt" <<'EOF'
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
EOF

echo "▶ 2/6 OrgActivity.kt (BD / Agency / Host)"
cat > "$D/OrgActivity.kt" <<'EOF'
package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jalur rekrutmen: BD -> Agency -> Host.
 * Mode: register_agency, verify_agency, agencies_{dash|perf|report|rank},
 *       recruit_host, verify_host, hosts_{dash|perf|manage}, attendance_agency,
 *       host_home, ranking_host
 */
class OrgActivity : ListPageActivity() {

    private val orgAdminRoles = setOf(Role.DEVELOPER, Role.SUPERADMIN, Role.ADMIN)
    private val ymd = SimpleDateFormat("yyyyMMdd", Locale.US)

    private fun isBd() = myRole == Role.BD
    private fun isAgency() = myRole == Role.AGENCY
    private fun isOrgAdmin() = myRole in orgAdminRoles

    override fun allowed(): Boolean = when {
        mode == "register_agency" || mode == "verify_agency" || mode.startsWith("agencies_") -> isBd() || isOrgAdmin()
        mode == "recruit_host" || mode == "attendance_agency" || mode.startsWith("hosts_") -> isAgency()
        mode == "verify_host" -> isAgency() || isOrgAdmin()
        mode == "host_home" -> myRole == Role.HOST
        mode == "ranking_host" -> isAgency() || isBd() || isOrgAdmin() || myRole == Role.HOST
        else -> false
    }

    override fun onReady() {
        when {
            mode == "register_agency" -> { showSearch(); setInfo("Cari user biasa untuk dijadikan agency") }
            mode == "recruit_host" -> { showSearch(); setInfo("Cari user biasa untuk direkrut jadi host") }
            mode == "verify_agency" -> loadPendingAgencies()
            mode.startsWith("agencies_") -> loadAgencies(mode.substringAfter("_"))
            mode == "verify_host" -> loadPendingHosts()
            mode.startsWith("hosts_") -> loadHosts(mode.substringAfter("_"))
            mode == "attendance_agency" -> loadAttendance()
            mode == "host_home" -> loadHostHome()
            mode == "ranking_host" -> loadRanking()
        }
    }

    // ---------- hasil cari user ----------
    override fun onUserResults(docs: List<DocumentSnapshot>) {
        clearList()
        val shown = docs.filter { it.id != myUid && Role.normalize(it.getString("role")) == Role.USER }
        if (shown.isEmpty()) {
            setInfo("Tidak ada user biasa yang cocok (hanya user tanpa role yang bisa dipilih)")
            return
        }
        setInfo("Ditemukan ${shown.size} user — tap untuk pilih")
        for (u in shown) {
            addRow("${nick(u)} • ID ${num(u, "userId")}", "🪙 ${fmt(num(u, "coins"))}") {
                if (mode == "register_agency") {
                    textDialog("Nama agency", "Nama agency", nick(u)) { name -> registerAgency(u, name) }
                } else {
                    confirm("Rekrut ${nick(u)}", "Jadikan host di agency kamu?") { recruitHost(u) }
                }
            }
        }
    }

    // ---------- BD: daftarkan agency ----------
    private fun registerAgency(target: DocumentSnapshot, name: String) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(target.id)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            val ar = Role.normalize(actor.getString("role"))
            if (ar != Role.BD && ar !in orgAdminRoles) throw IllegalStateException("Tidak punya akses")
            if (Role.normalize(t.getString("role")) != Role.USER) throw IllegalStateException("User ini sudah punya role")
            tx.update(
                targetRef, mapOf<String, Any>(
                    "role" to Role.AGENCY, "agencyStatus" to "pending", "agencyName" to name,
                    "bdUid" to myUid, "recruitedAt" to System.currentTimeMillis()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "register_agency", "targetUid" to target.id,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ ${nick(target)} didaftarkan sebagai agency (menunggu verifikasi)"); clearList(); doSearch() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- BD/Admin: verifikasi agency ----------
    private fun loadPendingAgencies() {
        db.collection("users").whereEqualTo("agencyStatus", "pending").limit(100).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                    .filter { isOrgAdmin() || it.getString("bdUid") == myUid }
                    .sortedBy { num(it, "recruitedAt") }
                setInfo(if (docs.isEmpty()) "Tidak ada agency yang menunggu 🎉" else "${docs.size} agency menunggu verifikasi")
                for (a in docs) {
                    addRow("🏢 ${a.getString("agencyName").orEmpty().ifEmpty { nick(a) }}", "Pemilik: ${nick(a)} • ${timeStr(num(a, "recruitedAt"))}") {
                        AlertDialog.Builder(this)
                            .setTitle(nick(a))
                            .setItems(arrayOf("✅ Verifikasi", "❌ Tolak")) { _, which -> decideAgency(a.id, which == 0) }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun decideAgency(agencyUid: String, approve: Boolean) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(agencyUid)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            val ar = Role.normalize(actor.getString("role"))
            val ownBd = ar == Role.BD && t.getString("bdUid") == myUid
            if (!ownBd && ar !in orgAdminRoles) throw IllegalStateException("Bukan agency di bawah kamu")
            if (t.getString("agencyStatus") != "pending") throw IllegalStateException("Sudah diproses")
            if (approve) {
                tx.update(targetRef, mapOf<String, Any>("agencyStatus" to "verified", "verifiedAt" to System.currentTimeMillis()))
            } else {
                tx.update(targetRef, mapOf<String, Any>("role" to Role.USER, "agencyStatus" to "rejected"))
            }
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to (if (approve) "verify_agency" else "reject_agency"),
                    "targetUid" to agencyUid, "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast(if (approve) "✅ Agency diverifikasi" else "Agency ditolak"); loadPendingAgencies() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- BD: dashboard / performa / laporan / ranking agency ----------
    private fun loadAgencies(variant: String) {
        db.collection("users").whereEqualTo("role", Role.AGENCY).limit(200).get()
            .addOnSuccessListener { aSnap ->
                db.collection("users").whereEqualTo("role", Role.HOST).limit(1000).get()
                    .addOnSuccessListener { hSnap ->
                        val verified = hSnap.documents.filter { it.getString("hostStatus") == "verified" }
                            .groupingBy { it.getString("agencyUid").orEmpty() }.eachCount()
                        val pending = hSnap.documents.filter { it.getString("hostStatus") == "pending" }
                            .groupingBy { it.getString("agencyUid").orEmpty() }.eachCount()

                        val mine = aSnap.documents.filter { isOrgAdmin() || it.getString("bdUid") == myUid }
                        val agencies = if (variant == "report") {
                            mine.sortedByDescending { num(it, "recruitedAt") }
                        } else {
                            mine.sortedByDescending { verified[it.id] ?: 0 }
                        }

                        val totalHosts = agencies.sumOf { (verified[it.id] ?: 0).toLong() }
                        val okAgency = agencies.count { it.getString("agencyStatus") == "verified" }
                        setInfo(
                            "Agency: ${agencies.size} (terverifikasi $okAgency) • Host aktif: $totalHosts"
                        )
                        clearList()
                        if (agencies.isEmpty()) setInfo("Belum ada agency")
                        for ((i, a) in agencies.withIndex()) {
                            val name = a.getString("agencyName").orEmpty().ifEmpty { nick(a) }
                            val prefix = if (variant == "rank") "${medal(i)} " else ""
                            val status = when (a.getString("agencyStatus")) {
                                "verified" -> "✅ Terverifikasi"
                                "pending" -> "⏳ Menunggu"
                                else -> "—"
                            }
                            val extra = if (variant == "report") " • direkrut ${timeStr(num(a, "recruitedAt"))}" else ""
                            addRow(
                                "$prefix🏢 $name",
                                "👥 ${verified[a.id] ?: 0} host (+${pending[a.id] ?: 0} menunggu) • $status$extra"
                            )
                        }
                    }
                    .addOnFailureListener { toast("Gagal: ${it.message}") }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency: rekrut host ----------
    private fun recruitHost(target: DocumentSnapshot) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(target.id)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            if (Role.normalize(actor.getString("role")) != Role.AGENCY) throw IllegalStateException("Hanya agency yang boleh merekrut")
            if (actor.getString("agencyStatus") != "verified") throw IllegalStateException("Agency kamu belum diverifikasi")
            if (Role.normalize(t.getString("role")) != Role.USER) throw IllegalStateException("User ini sudah punya role")
            tx.update(
                targetRef, mapOf<String, Any>(
                    "role" to Role.HOST, "hostStatus" to "pending", "agencyUid" to myUid,
                    "agencyName" to (actor.getString("agencyName") ?: ""),
                    "recruitedAt" to System.currentTimeMillis()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "recruit_host", "targetUid" to target.id,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ ${nick(target)} direkrut (menunggu verifikasi)"); clearList(); doSearch() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency/Admin: verifikasi host ----------
    private fun loadPendingHosts() {
        db.collection("users").whereEqualTo("hostStatus", "pending").limit(100).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                    .filter { isOrgAdmin() || it.getString("agencyUid") == myUid }
                    .sortedBy { num(it, "recruitedAt") }
                setInfo(if (docs.isEmpty()) "Tidak ada host yang menunggu 🎉" else "${docs.size} host menunggu verifikasi")
                for (h in docs) {
                    addRow("🎙️ ${nick(h)} • ID ${num(h, "userId")}", "Direkrut ${timeStr(num(h, "recruitedAt"))}") {
                        AlertDialog.Builder(this)
                            .setTitle(nick(h))
                            .setItems(arrayOf("✅ Verifikasi", "❌ Tolak")) { _, which -> decideHost(h.id, which == 0) }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun decideHost(hostUid: String, approve: Boolean) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(hostUid)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            val ar = Role.normalize(actor.getString("role"))
            val own = ar == Role.AGENCY && t.getString("agencyUid") == myUid
            if (!own && ar !in orgAdminRoles) throw IllegalStateException("Bukan host di bawah kamu")
            if (t.getString("hostStatus") != "pending") throw IllegalStateException("Sudah diproses")
            if (approve) {
                tx.update(targetRef, mapOf<String, Any>("hostStatus" to "verified", "verifiedAt" to System.currentTimeMillis()))
            } else {
                tx.update(
                    targetRef, mapOf<String, Any>(
                        "role" to Role.USER, "hostStatus" to "rejected", "agencyUid" to FieldValue.delete()
                    )
                )
            }
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to (if (approve) "verify_host" else "reject_host"),
                    "targetUid" to hostUid, "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast(if (approve) "✅ Host diverifikasi" else "Host ditolak"); loadPendingHosts() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency: dashboard / performa / manajemen member ----------
    private fun myHosts(onDone: (List<DocumentSnapshot>) -> Unit) {
        db.collection("users").whereEqualTo("agencyUid", myUid).limit(200).get()
            .addOnSuccessListener { snap ->
                onDone(snap.documents.filter { Role.normalize(it.getString("role")) == Role.HOST })
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun lv(doc: DocumentSnapshot): Long = maxOf(1L, num(doc, "level"))

    private fun loadHosts(variant: String) {
        myHosts { hosts ->
            clearList()
            val sorted = if (variant == "perf") {
                hosts.sortedWith(compareByDescending<DocumentSnapshot> { lv(it) }.thenByDescending { num(it, "coins") })
            } else {
                hosts.sortedBy { nick(it).lowercase() }
            }
            val ok = hosts.count { it.getString("hostStatus") == "verified" }
            val wait = hosts.count { it.getString("hostStatus") == "pending" }
            val hint = if (variant == "manage") " — tap untuk keluarkan" else ""
            setInfo("Host: ${hosts.size} (aktif $ok, menunggu $wait)$hint")
            for ((i, h) in sorted.withIndex()) {
                val status = if (h.getString("hostStatus") == "verified") "✅ Aktif" else "⏳ Menunggu"
                val prefix = if (variant == "perf") "${medal(i)} " else ""
                addRow("$prefix🎙️ ${nick(h)} • Lv ${lv(h)}", "$status • 🪙 ${fmt(num(h, "coins"))}") {
                    if (variant == "manage") {
                        confirm("Keluarkan ${nick(h)}?", "Host akan kembali menjadi user biasa.") { removeHost(h.id) }
                    }
                }
            }
        }
    }

    private fun removeHost(hostUid: String) {
        val targetRef = db.collection("users").document(hostUid)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val t = tx.get(targetRef)
            if (t.getString("agencyUid") != myUid || Role.normalize(t.getString("role")) != Role.HOST) {
                throw IllegalStateException("Bukan host di bawah kamu")
            }
            tx.update(
                targetRef, mapOf<String, Any>(
                    "role" to Role.USER, "hostStatus" to FieldValue.delete(), "agencyUid" to FieldValue.delete()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "remove_host", "targetUid" to hostUid,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("Host dikeluarkan"); loadHosts("manage") }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency: absensi host ----------
    private fun loadAttendance() {
        val today = ymd.format(Date())
        val month = today.substring(0, 6)
        myHosts { hosts ->
            db.collection("host_attendance").whereEqualTo("agencyUid", myUid).limit(1000).get()
                .addOnSuccessListener { snap ->
                    clearList()
                    val byUid = snap.documents.groupBy { it.getString("uid").orEmpty() }
                    val active = hosts.filter { it.getString("hostStatus") == "verified" }
                    val present = active.count { h -> byUid[h.id].orEmpty().any { it.getString("date") == today } }
                    setInfo("Absensi hari ini: $present / ${active.size} host hadir")
                    for (h in active) {
                        val rec = byUid[h.id].orEmpty()
                        val hadir = rec.any { it.getString("date") == today }
                        val bulan = rec.count { it.getString("date").orEmpty().startsWith(month) }
                        addRow("🎙️ ${nick(h)}", "Hari ini: ${if (hadir) "✅ hadir" else "❌ belum"} • Bulan ini: $bulan hari")
                    }
                }
                .addOnFailureListener { toast("Gagal: ${it.message}") }
        }
    }

    // ---------- Host: dashboard + absen ----------
    private fun loadHostHome() {
        clearActions()
        val me = myDoc ?: return
        val today = ymd.format(Date())
        val month = today.substring(0, 6)
        val verified = me.getString("hostStatus") == "verified"

        db.collection("host_attendance").whereEqualTo("uid", myUid).limit(100).get()
            .addOnSuccessListener { snap ->
                clearList()
                val dates = snap.documents.mapNotNull { it.getString("date") }
                val hadir = dates.contains(today)
                val bulan = dates.count { it.startsWith(month) }

                setInfo("🎙️ ${nick(me)} • Lv ${lv(me)} • 🪙 ${fmt(num(me, "coins"))}")
                addRow(
                    "Status host",
                    if (verified) "✅ Aktif" else "⏳ Menunggu verifikasi agency"
                )
                addRow("Agency", me.getString("agencyName").orEmpty().ifEmpty { "—" })
                addRow("Absensi", "Hari ini: ${if (hadir) "✅ sudah absen" else "❌ belum"} • Bulan ini: $bulan hari")
                if (verified && !hadir) addAction("📅 Absen hari ini") { absen(today) }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun absen(today: String) {
        val data = mapOf<String, Any>(
            "uid" to myUid,
            "agencyUid" to (myDoc?.getString("agencyUid") ?: ""),
            "date" to today,
            "at" to System.currentTimeMillis()
        )
        db.collection("host_attendance").document("${myUid}_$today").set(data)
            .addOnSuccessListener { toast("✅ Absen tercatat"); loadHostHome() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Ranking host ----------
    private fun loadRanking() {
        db.collection("users").whereEqualTo("role", Role.HOST).limit(200).get()
            .addOnSuccessListener { snap ->
                clearList()
                val top = snap.documents
                    .filter { it.getString("hostStatus") != "pending" }
                    .sortedWith(compareByDescending<DocumentSnapshot> { lv(it) }.thenByDescending { num(it, "coins") })
                    .take(50)
                setInfo(if (top.isEmpty()) "Belum ada host" else "Top ${top.size} host berdasarkan level")
                for ((i, h) in top.withIndex()) {
                    addRow("${medal(i)} ${nick(h)}", "Lv ${lv(h)} • ${h.getString("agencyName").orEmpty()}")
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
EOF

echo "▶ 3/6 SupportActivity.kt + TicketAdminActivity.kt (CS)"
cat > "$D/SupportActivity.kt" <<'EOF'
package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot

object TicketCat {
    val all = listOf("complaint", "topup", "transaksi", "refund", "other")
    fun label(c: String?): String = when (c) {
        "complaint" -> "📩 Komplain"
        "topup" -> "🪙 Top-up"
        "transaksi" -> "💳 Transaksi"
        "refund" -> "↩️ Refund"
        else -> "❓ Lainnya"
    }
}

/** Sisi USER: kirim tiket bantuan & lihat balasan CS. */
class SupportActivity : ListPageActivity() {

    override fun allowed(): Boolean = true

    override fun onReady() {
        addAction("➕ Tiket baru") { pickCategory() }
        loadMine()
    }

    private fun pickCategory() {
        val labels = TicketCat.all.map { TicketCat.label(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Kategori masalah")
            .setItems(labels) { _, which ->
                val cat = TicketCat.all[which]
                textDialog("Jelaskan masalahmu", "Tulis detail (maks 1000 karakter)") { msg ->
                    createTicket(cat, msg.take(1000))
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createTicket(cat: String, msg: String) {
        val data = mapOf<String, Any>(
            "uid" to myUid,
            "nickname" to (myDoc?.getString("nickname") ?: ""),
            "category" to cat,
            "message" to msg,
            "status" to "open",
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("tickets").add(data)
            .addOnSuccessListener { toast("✅ Tiket terkirim, tunggu balasan CS"); loadMine() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadMine() {
        db.collection("tickets").whereEqualTo("uid", myUid).limit(50).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents.sortedByDescending { num(it, "createdAt") }
                setInfo(if (docs.isEmpty()) "Belum ada tiket. Tap ➕ untuk membuat." else "${docs.size} tiket")
                for (t in docs) {
                    val done = t.getString("status") == "closed"
                    addRow(
                        "${TicketCat.label(t.getString("category"))} • ${if (done) "✅ Dibalas" else "⏳ Menunggu"}",
                        t.getString("message").orEmpty().take(70)
                    ) { showTicket(t) }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun showTicket(t: DocumentSnapshot) {
        val reply = t.getString("reply").orEmpty()
        val msg = "Pesan:\n${t.getString("message").orEmpty()}\n\n" +
            if (reply.isEmpty()) "Belum ada balasan dari CS." else "Balasan CS:\n$reply"
        AlertDialog.Builder(this)
            .setTitle(TicketCat.label(t.getString("category")))
            .setMessage(msg)
            .setPositiveButton("Tutup", null)
            .show()
    }
}
EOF

cat > "$D/TicketAdminActivity.kt" <<'EOF'
package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Transaction

/** Sisi CS. Mode: tickets (extra "cat" untuk filter kategori), history, stats. */
class TicketAdminActivity : ListPageActivity() {

    private var cat = ""

    override fun allowed(): Boolean = Role.canAccess(myRole, "support") || Role.canAccess(myRole, "cs")

    override fun onReady() {
        cat = intent.getStringExtra("cat") ?: ""
        when (mode) {
            "tickets" -> loadOpen()
            "history" -> loadHistory()
            "stats" -> loadStats()
        }
    }

    private fun loadOpen() {
        db.collection("tickets").whereEqualTo("status", "open").limit(200).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                    .filter { cat.isEmpty() || it.getString("category") == cat }
                    .sortedBy { num(it, "createdAt") }
                setInfo(if (docs.isEmpty()) "Tidak ada tiket menunggu 🎉" else "${docs.size} tiket menunggu (terlama di atas)")
                for (t in docs) {
                    addRow(
                        "${TicketCat.label(t.getString("category"))} • ${t.getString("nickname").orEmpty()}",
                        "${timeStr(num(t, "createdAt"))} • ${t.getString("message").orEmpty().take(60)}"
                    ) { ticketDialog(t) }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun ticketDialog(t: DocumentSnapshot) {
        AlertDialog.Builder(this)
            .setTitle("${TicketCat.label(t.getString("category"))} • ${t.getString("nickname").orEmpty()}")
            .setMessage(t.getString("message").orEmpty())
            .setPositiveButton("Balas & tutup") { _, _ ->
                textDialog("Balasan untuk user", "Tulis balasan") { reply -> replyTicket(t.id, reply.take(1000)) }
            }
            .setNegativeButton("Kembali", null)
            .show()
    }

    private fun replyTicket(ticketId: String, reply: String) {
        val ref = db.collection("tickets").document(ticketId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val t = tx.get(ref)
            if (t.getString("status") != "open") throw IllegalStateException("Sudah ditangani CS lain")
            tx.update(
                ref, mapOf<String, Any>(
                    "status" to "closed", "reply" to reply,
                    "repliedBy" to myUid, "repliedAt" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ Balasan terkirim"); loadOpen() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadHistory() {
        db.collection("tickets").whereEqualTo("status", "closed").limit(300).get()
            .addOnSuccessListener { snap ->
                clearList()
                val onlyMine = myRole == Role.CS
                val docs = snap.documents
                    .filter { !onlyMine || it.getString("repliedBy") == myUid }
                    .sortedByDescending { num(it, "repliedAt") }
                    .take(50)
                setInfo(if (docs.isEmpty()) "Belum ada riwayat balasan" else "${docs.size} balasan terakhir")
                for (t in docs) {
                    addRow(
                        "${TicketCat.label(t.getString("category"))} • ${t.getString("nickname").orEmpty()}",
                        "${timeStr(num(t, "repliedAt"))} • ${t.getString("reply").orEmpty().take(60)}"
                    ) {
                        AlertDialog.Builder(this)
                            .setMessage("Pesan:\n${t.getString("message").orEmpty()}\n\nBalasan:\n${t.getString("reply").orEmpty()}")
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadStats() {
        db.collection("tickets").limit(500).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                val open = docs.count { it.getString("status") == "open" }
                val closed = docs.size - open
                val mine = docs.count { it.getString("repliedBy") == myUid }
                setInfo("Total ${docs.size} tiket • menunggu $open • selesai $closed")
                addRow("🎧 Dibalas olehmu", "$mine tiket")
                for (c in TicketCat.all) {
                    val n = docs.count { it.getString("category") == c }
                    val o = docs.count { it.getString("category") == c && it.getString("status") == "open" }
                    addRow(TicketCat.label(c), "$n tiket • $o menunggu")
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
EOF

echo "▶ 4/6 Patch Manifest, Role, PanelDetail, ProfileFragment, UserAdmin"
D="$D" python3 - <<'PY'
import os, re
D = os.environ["D"]
M = "app/src/main/AndroidManifest.xml"
def rd(p): return open(p, encoding="utf-8").read()
def wr(p, s): open(p, "w", encoding="utf-8").write(s)

# --- Manifest ---
s = rd(M)
if "OrgActivity" not in s:
    old = '<activity\n            android:name=".CoinAdminActivity"'
    assert old in s, "Manifest: CoinAdminActivity tidak ketemu"
    blocks = "".join(
        '<activity\n            android:name=".%s"\n            android:exported="false"\n            android:screenOrientation="portrait" />\n\n        ' % n
        for n in ("OrgActivity", "SupportActivity", "TicketAdminActivity")
    )
    wr(M, s.replace(old, blocks + old, 1))
print("  ok manifest")

# --- Role.kt: panel milik sendiri + BD hanya boleh urus jalurnya ---
p = os.path.join(D, "Role.kt"); s = rd(p)
for role, key in (("BD", "bd"), ("AGENCY", "agency"), ("HOST", "host")):
    pat = re.compile(r'(\b%s\s+to listOf\("dashboard",)(?!"%s")' % (role, key))
    s, c = pat.subn(r'\1"%s",' % key, s, count=1)
    print("  role", role, "ok" if c else "sudah/skip")
old = ('    fun canManageTarget(actorRole: String?, targetRole: String?): Boolean =\n'
       '        canManageRoles(actorRole) && rankOf(actorRole) > rankOf(targetRole)\n')
new = ('    fun canManageTarget(actorRole: String?, targetRole: String?): Boolean {\n'
       '        val actor = normalize(actorRole)\n'
       '        val target = normalize(targetRole)\n'
       '        if (!canManageRoles(actor) || rankOf(actor) <= rankOf(target)) return false\n'
       '        // BD hanya boleh mengurus jalur rekrutmennya (user / agency / host)\n'
       '        if (actor == BD && target !in listOf(USER, AGENCY, HOST)) return false\n'
       '        return true\n'
       '    }\n')
if old in s:
    s = s.replace(old, new, 1)
elif "BD hanya boleh mengurus" not in s:
    raise SystemExit("Role.kt: canManageTarget tidak ketemu")
wr(p, s)
print("  ok Role.kt")

# --- PanelDetailActivity ---
p = os.path.join(D, "PanelDetailActivity.kt"); s = rd(p)
if "orgModes" not in s:
    a = '"Saldo Koin User" to "coin"\n        )\n'
    if a in s:
        s = s.replace(a, '"Saldo Koin User" to "coin", "Pemulihan Akun" to "recover"\n        )\n', 1)
    assert '"Pemulihan Akun" to "recover"' in s, "PanelDetail: adminModes tidak ketemu"

    b = '            "Laporan Penjualan" to "sales", "Top-up & Pembelian" to "topup"\n        )\n'
    assert b in s, "PanelDetail: coinModes tidak ketemu"
    s = s.replace(b, b + (
        '        val orgModes = mapOf(\n'
        '            "Dashboard BD" to "agencies_dash", "Daftarkan Agency" to "register_agency",\n'
        '            "Verifikasi Agency" to "verify_agency", "Performa Agency & Host" to "agencies_perf",\n'
        '            "Laporan Rekrutmen" to "agencies_report", "Ranking Agency" to "agencies_rank",\n'
        '            "Dashboard Agency" to "hosts_dash", "Rekrut Host" to "recruit_host",\n'
        '            "Verifikasi Host" to "verify_host", "Absensi Host" to "attendance_agency",\n'
        '            "Performa Host" to "hosts_perf", "Manajemen Member" to "hosts_manage",\n'
        '            "Dashboard Host" to "host_home", "Absensi & Jam Aktif" to "host_home",\n'
        '            "Ranking Host" to "ranking_host"\n'
        '        )\n'
        '        val csModes = mapOf(\n'
        '            "Dashboard CS" to "stats", "Laporan Support" to "stats",\n'
        '            "Support Ticket" to "tickets|", "Komplain User" to "tickets|complaint",\n'
        '            "Komplain Top-up" to "tickets|topup", "Komplain Transaksi" to "tickets|transaksi",\n'
        '            "Request Refund" to "tickets|refund", "Histori Response" to "history"\n'
        '        )\n'), 1)

    c = "                val coinMode = coinModes[item.substringAfter(' ')]\n"
    assert c in s, "PanelDetail: coinMode tidak ketemu"
    s = s.replace(c, c + (
        "                val orgMode = orgModes[item.substringAfter(' ')]\n"
        "                val csMode = csModes[item.substringAfter(' ')]\n"
        "                val plainName = item.substringAfter(' ')\n"), 1)

    d = '                } else if (coinMode == "topup") {\n'
    assert d in s, "PanelDetail: cabang topup tidak ketemu"
    s = s.replace(d, (
        '                } else if (orgMode != null) {\n'
        '                    val oi = Intent(this, OrgActivity::class.java)\n'
        '                    oi.putExtra("mode", orgMode)\n'
        '                    oi.putExtra("title", item)\n'
        '                    startActivity(oi)\n'
        '                } else if (csMode != null) {\n'
        '                    val parts = csMode.split("|")\n'
        '                    val ti = Intent(this, TicketAdminActivity::class.java)\n'
        '                    ti.putExtra("mode", parts[0])\n'
        '                    ti.putExtra("cat", parts.getOrElse(1) { "" })\n'
        '                    ti.putExtra("title", item)\n'
        '                    startActivity(ti)\n'
        '                } else if (plainName == "Level & EXP Host") {\n'
        '                    startActivity(Intent(this, LevelActivity::class.java))\n'
        '                } else if (plainName == "Tugas Harian") {\n'
        '                    startActivity(Intent(this, TugasActivity::class.java))\n'
        + d), 1)
    wr(p, s)
print("  ok PanelDetailActivity")

# --- ProfileFragment: menu Bantuan -> SupportActivity ---
p = os.path.join(D, "ProfileFragment.kt"); s = rd(p)
old = 'view.findViewById<View>(R.id.menu_help).setOnClickListener { notif("Bantuan segera hadir! ❓") }'
if old in s:
    s = s.replace(old, 'view.findViewById<View>(R.id.menu_help).setOnClickListener { startActivity(Intent(requireContext(), SupportActivity::class.java)) }', 1)
    wr(p, s)
elif "SupportActivity" not in s:
    raise SystemExit("ProfileFragment: menu_help tidak ketemu")
print("  ok ProfileFragment")

# --- UserAdminActivity: mode recover (reset password) ---
p = os.path.join(D, "UserAdminActivity.kt"); s = rd(p)
if "recoverDialog" not in s:
    a = '        "coin" -> Role.canAccess(myRole, "coin")\n        else -> Role.canAccess(myRole, "dashboard")\n'
    assert a in s, "UserAdmin: allowed() tidak ketemu"
    s = s.replace(a, '        "coin" -> Role.canAccess(myRole, "coin")\n        "recover" -> Role.canAccess(myRole, "recovery")\n        else -> Role.canAccess(myRole, "dashboard")\n', 1)
    b = '            "coin" -> coinDialog(doc)\n'
    assert b in s, "UserAdmin: onRowClick tidak ketemu"
    s = s.replace(b, b + '            "recover" -> recoverDialog(doc)\n', 1)
    c = '    // ---------- KOIN ----------\n'
    assert c in s, "UserAdmin: penanda KOIN tidak ketemu"
    fn = ('    // ---------- PEMULIHAN AKUN ----------\n'
          '    private fun recoverDialog(doc: DocumentSnapshot) {\n'
          '        val email = doc.getString("email").orEmpty()\n'
          '        if (email.isEmpty()) { toast("Email user tidak tersimpan"); return }\n'
          '        AlertDialog.Builder(this)\n'
          '            .setTitle("Pemulihan akun")\n'
          '            .setMessage("Kirim email reset password ke $email?")\n'
          '            .setPositiveButton("Kirim") { _, _ ->\n'
          '                auth.sendPasswordResetEmail(email)\n'
          '                    .addOnSuccessListener {\n'
          '                        db.collection("admin_logs").add(\n'
          '                            mapOf<String, Any>(\n'
          '                                "action" to "recover", "targetUid" to doc.id,\n'
          '                                "byUid" to (auth.currentUser?.uid ?: ""), "at" to Timestamp.now()\n'
          '                            )\n'
          '                        )\n'
          '                        toast("✅ Email reset password terkirim")\n'
          '                    }\n'
          '                    .addOnFailureListener { toast("Gagal: ${it.message}") }\n'
          '            }\n'
          '            .setNegativeButton("Batal", null)\n'
          '            .show()\n'
          '    }\n\n')
    s = s.replace(c, fn + c, 1)
    wr(p, s)
print("  ok UserAdminActivity")
PY

echo "▶ 5/6 Cek kurung"
for f in ListPageActivity.kt OrgActivity.kt SupportActivity.kt TicketAdminActivity.kt PanelDetailActivity.kt UserAdminActivity.kt Role.kt ProfileFragment.kt; do
  o=$(tr -cd '{' < "$D/$f" | wc -c); c=$(tr -cd '}' < "$D/$f" | wc -c)
  [ "$o" = "$c" ] || { echo "❌ kurung tidak seimbang di $f ($o vs $c)"; exit 1; }
done
echo "  ✔"

echo "▶ 6/6 Git"
if [ -n "$NO_GIT" ]; then echo "  (NO_GIT)"; exit 0; fi
git add -A
git commit -m "feat(org): BD/Agency/Host rekrutmen+absensi+ranking, CS tiket support & pemulihan akun" || true
git push origin HEAD
echo "✅ Selesai"
