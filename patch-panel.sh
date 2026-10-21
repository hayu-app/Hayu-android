#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch 2: fitur panel yang BENAR-BENAR jalan
# (Cari User, Ban/Unban, Verifikasi, Saldo Koin) + blokir akun saat login
#   cd ~/Hayu-android && bash patch-panel.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
grep -q "Kelola Role" "$D/PanelDetailActivity.kt" || { echo "❌ Jalankan patch-role.sh dulu"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python

echo "▶ 1/4 UserAdminActivity.kt"
cat > "$D/UserAdminActivity.kt" <<'EOF'
package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction

/**
 * Panel user serbaguna. Mode (extra "mode"):
 *  search = cari & lihat detail, ban = ban/unban, verify = verifikasi akun, coin = tambah/kurangi koin
 */
class UserAdminActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var mode = "search"
    private var myRole = Role.USER
    private var ready = false
    private var lastQuery = ""

    private lateinit var etQuery: EditText
    private lateinit var tvInfo: TextView
    private lateinit var list: LinearLayout

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "search"
        buildUI(intent.getStringExtra("title") ?: "Kelola User")

        val uid = auth.currentUser?.uid ?: run { finish(); return }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                myRole = Role.normalize(doc.getString("role"))
                if (!allowed()) {
                    toast("Akses ditolak 🚫"); finish()
                } else {
                    ready = true
                    tvInfo.text = "Login sebagai ${Role.label(myRole)}"
                }
            }
            .addOnFailureListener { toast("Gagal cek akses"); finish() }
    }

    private fun allowed(): Boolean = when (mode) {
        "ban" -> Role.canAccess(myRole, "ban") || Role.canAccess(myRole, "user")
        "verify" -> Role.canAccess(myRole, "verify") || Role.canAccess(myRole, "user")
        "coin" -> Role.canAccess(myRole, "coin")
        else -> Role.canAccess(myRole, "dashboard")
    }

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

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(d(12), d(12), d(12), d(4))
        etQuery = EditText(this)
        etQuery.hint = "Nickname / UserID / UID"
        etQuery.setTextColor(Color.WHITE)
        etQuery.setHintTextColor(Color.parseColor("#8892AA"))
        etQuery.setSingleLine(true)
        bar.addView(etQuery, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val btn = Button(this)
        btn.text = "Cari"
        btn.setOnClickListener { doSearch() }
        bar.addView(btn)
        root.addView(bar)

        tvInfo = TextView(this)
        tvInfo.setTextColor(Color.parseColor("#C4CAFF"))
        tvInfo.textSize = 12f
        tvInfo.setPadding(d(16), d(4), d(16), d(8))
        root.addView(tvInfo)

        val sv = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(d(12), 0, d(12), d(80))
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun doSearch() {
        if (!ready) { toast("Tunggu, mengecek akses…"); return }
        val q = etQuery.text.toString().trim()
        if (q.isEmpty()) { toast("Ketik nickname, UserID, atau UID dulu"); return }
        lastQuery = q
        tvInfo.text = "Mencari…"

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
                render(seen.values.toList())
            }
            .addOnFailureListener { e -> tvInfo.text = ""; toast("Gagal cari: ${e.message}") }
    }

    private fun num(doc: DocumentSnapshot, f: String): Long = (doc.get(f) as? Number)?.toLong() ?: 0L

    private fun isBanned(doc: DocumentSnapshot): Boolean {
        val until = num(doc, "bannedUntil")
        return doc.getBoolean("banned") == true && (until == 0L || until > System.currentTimeMillis())
    }

    private fun render(docs: List<DocumentSnapshot>) {
        list.removeAllViews()
        if (docs.isEmpty()) { tvInfo.text = "Tidak ditemukan 😕"; return }
        tvInfo.text = "Ditemukan ${docs.size} user — tap untuk aksi"
        for (doc in docs) {
            val nick = doc.getString("nickname").orEmpty().ifEmpty { "Pengguna HAYU" }
            val role = Role.normalize(doc.getString("role"))
            val flags = buildString {
                append("🪙 ${num(doc, "coins")}")
                if (isBanned(doc)) append("  🚫 DIBLOKIR")
                if (doc.getBoolean("verified") == true) append("  ✅ Terverifikasi")
            }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(d(14), d(12), d(14), d(12))
            val gd = GradientDrawable()
            gd.setColor(Color.parseColor("#12122A"))
            gd.cornerRadius = d(12).toFloat()
            gd.setStroke(d(1), Color.parseColor("#2E2E50"))
            row.background = gd

            val t1 = TextView(this)
            t1.text = "$nick • ID ${num(doc, "userId")}  ${Role.badgeShort(role) ?: ""}"
            t1.textSize = 14f
            t1.setTextColor(Color.parseColor("#F1F5F9"))
            val t2 = TextView(this)
            t2.text = flags
            t2.textSize = 12f
            t2.setTextColor(Color.parseColor("#8892AA"))
            row.addView(t1)
            row.addView(t2)
            row.setOnClickListener { onRowClick(doc) }

            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = d(8)
            list.addView(row, lp)
        }
    }

    private fun onRowClick(doc: DocumentSnapshot) {
        when (mode) {
            "ban" -> banDialog(doc)
            "verify" -> verifyDialog(doc)
            "coin" -> coinDialog(doc)
            else -> infoDialog(doc)
        }
    }

    private fun infoDialog(doc: DocumentSnapshot) {
        val msg = "UID: ${doc.id}\n" +
            "UserID: ${num(doc, "userId")}\n" +
            "Email: ${doc.getString("email").orEmpty()}\n" +
            "Role: ${Role.label(doc.getString("role"))}\n" +
            "Level: ${num(doc, "level")}\n" +
            "Koin: ${num(doc, "coins")}\n" +
            "Diblokir: ${if (isBanned(doc)) "Ya" else "Tidak"}\n" +
            "Terverifikasi: ${if (doc.getBoolean("verified") == true) "Ya" else "Tidak"}"
        AlertDialog.Builder(this)
            .setTitle(doc.getString("nickname").orEmpty().ifEmpty { "User" })
            .setMessage(msg)
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ---------- BAN ----------
    private fun banDialog(doc: DocumentSnapshot) {
        if (doc.id == auth.currentUser?.uid) { toast("Tidak bisa memblokir diri sendiri"); return }
        if (Role.rankOf(myRole) <= Role.rankOf(doc.getString("role"))) {
            toast("Pangkat target setara/lebih tinggi dari kamu"); return
        }
        val labels = arrayOf("🚫 Ban permanen", "⏳ Ban 1 hari", "⏳ Ban 7 hari", "⏳ Ban 30 hari", "✅ Unban")
        AlertDialog.Builder(this)
            .setTitle(doc.getString("nickname").orEmpty().ifEmpty { "User" })
            .setItems(labels) { _, which ->
                val now = System.currentTimeMillis()
                val day = 86400000L
                val data: Map<String, Any> = when (which) {
                    0 -> mapOf("banned" to true, "bannedUntil" to 0L)
                    1 -> mapOf("banned" to true, "bannedUntil" to now + day)
                    2 -> mapOf("banned" to true, "bannedUntil" to now + 7 * day)
                    3 -> mapOf("banned" to true, "bannedUntil" to now + 30 * day)
                    else -> mapOf("banned" to false, "bannedUntil" to 0L)
                }
                safeUpdate(doc.id, data, if (which == 4) "unban" else "ban", true)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- VERIFIKASI ----------
    private fun verifyDialog(doc: DocumentSnapshot) {
        val now = doc.getBoolean("verified") == true
        AlertDialog.Builder(this)
            .setTitle(doc.getString("nickname").orEmpty().ifEmpty { "User" })
            .setMessage(if (now) "Akun ini sudah terverifikasi." else "Akun ini belum terverifikasi.")
            .setPositiveButton(if (now) "Cabut verifikasi" else "Verifikasi") { _, _ ->
                safeUpdate(doc.id, mapOf<String, Any>("verified" to !now), if (now) "unverify" else "verify", false)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- KOIN ----------
    private fun coinDialog(doc: DocumentSnapshot) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        et.hint = "contoh 100 atau -50"
        val pad = d(20)
        et.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this)
            .setTitle("Koin ${doc.getString("nickname").orEmpty()} (saldo ${num(doc, "coins")})")
            .setView(et)
            .setPositiveButton("Terapkan") { _, _ ->
                val delta = et.text.toString().trim().toLongOrNull()
                if (delta == null || delta == 0L || Math.abs(delta) > 10_000_000L) {
                    toast("Masukkan angka valid (maks 10.000.000)")
                } else {
                    changeCoins(doc.id, delta)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun changeCoins(targetUid: String, delta: Long) {
        val actorUid = auth.currentUser?.uid ?: return
        val actorRef = db.collection("users").document(actorUid)
        val targetRef = db.collection("users").document(targetUid)
        val logRef = db.collection("admin_logs").document()

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actorRole = Role.normalize(tx.get(actorRef).getString("role"))
            val target = tx.get(targetRef)
            if (!Role.canAccess(actorRole, "coin")) throw IllegalStateException("Kamu tidak boleh mengubah koin")
            val old = (target.get("coins") as? Number)?.toLong() ?: 0L
            val newVal = old + delta
            if (newVal < 0L) throw IllegalStateException("Saldo tidak boleh minus")
            tx.update(targetRef, "coins", newVal)
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "coin",
                    "targetUid" to targetUid,
                    "delta" to delta,
                    "oldValue" to old,
                    "newValue" to newVal,
                    "byUid" to actorUid,
                    "byRole" to actorRole,
                    "at" to Timestamp.now()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ Koin diperbarui"); refresh() }
            .addOnFailureListener { e -> toast("Gagal: ${e.message}") }
    }

    // Update field + audit log, divalidasi ulang di server
    private fun safeUpdate(targetUid: String, data: Map<String, Any>, action: String, checkRank: Boolean) {
        val actorUid = auth.currentUser?.uid ?: return
        val actorRef = db.collection("users").document(actorUid)
        val targetRef = db.collection("users").document(targetUid)
        val logRef = db.collection("admin_logs").document()

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actorRole = Role.normalize(tx.get(actorRef).getString("role"))
            val target = tx.get(targetRef)
            if (checkRank && Role.rankOf(actorRole) <= Role.rankOf(target.getString("role"))) {
                throw IllegalStateException("Pangkat target setara/lebih tinggi dari kamu")
            }
            tx.update(targetRef, data)
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to action,
                    "targetUid" to targetUid,
                    "byUid" to actorUid,
                    "byRole" to actorRole,
                    "at" to Timestamp.now()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ Berhasil"); refresh() }
            .addOnFailureListener { e -> toast("Gagal: ${e.message}") }
    }

    private fun refresh() {
        if (lastQuery.isNotEmpty()) {
            etQuery.setText(lastQuery)
            doSearch()
        }
    }
}
EOF

echo "▶ 2/4 Patch Manifest, PanelDetail, MainActivity"
D="$D" python3 - <<'PY'
import os
D = os.environ["D"]
M = "app/src/main/AndroidManifest.xml"

# Manifest
s = open(M, encoding="utf-8").read()
if "UserAdminActivity" not in s:
    old = '<activity\n            android:name=".RoleManagerActivity"'
    assert old in s, "Manifest: RoleManagerActivity tidak ketemu"
    new = ('<activity\n            android:name=".UserAdminActivity"\n'
           '            android:exported="false"\n'
           '            android:screenOrientation="portrait" />\n\n'
           '        <activity\n            android:name=".RoleManagerActivity"')
    open(M, "w", encoding="utf-8").write(s.replace(old, new, 1))
print("  ok manifest")

# PanelDetailActivity
p = os.path.join(D, "PanelDetailActivity.kt")
s = open(p, encoding="utf-8").read()
if "adminModes" not in s:
    a = 'val roleItem = "🎭 Kelola Role"\n'
    assert a in s, "PanelDetail: roleItem tidak ketemu"
    s = s.replace(a, a + (
        '        val adminModes = mapOf(\n'
        '            "Cari User by UID" to "search", "Cari UID Customer" to "search", "Status Akun" to "search",\n'
        '            "Ban & Suspension" to "ban", "Temporary Ban" to "ban", "Warning & Ban" to "ban",\n'
        '            "Verifikasi Akun" to "verify", "Saldo Koin User" to "coin"\n'
        '        )\n'), 1)
    b = '                if (item == roleItem) {\n'
    assert b in s, "PanelDetail: click roleItem tidak ketemu"
    s = s.replace(b, '                val adminMode = adminModes[item.substringAfter(\' \')]\n' + b, 1)
    c = '                } else {\n                    Toast.makeText(this, "$item — segera tersedia"'
    assert c in s, "PanelDetail: toast tidak ketemu"
    s = s.replace(c, (
        '                } else if (adminMode != null) {\n'
        '                    val i = Intent(this, UserAdminActivity::class.java)\n'
        '                    i.putExtra("mode", adminMode)\n'
        '                    i.putExtra("title", item)\n'
        '                    startActivity(i)\n'
        '                } else {\n'
        '                    Toast.makeText(this, "$item — segera tersedia"'), 1)
    open(p, "w", encoding="utf-8").write(s)
print("  ok PanelDetailActivity")

# MainActivity: blokir akun yang di-ban
p = os.path.join(D, "MainActivity.kt")
s = open(p, encoding="utf-8").read()
if "bannedUntil" not in s:
    a = '        bottomNav = findViewById(R.id.bottom_nav)\n'
    assert a in s, "MainActivity: bottomNav tidak ketemu"
    s = s.replace(a, a + (
        '\n        auth.currentUser?.uid?.let { myUid ->\n'
        '            db.collection("users").document(myUid).get().addOnSuccessListener { d ->\n'
        '                val until = d.getLong("bannedUntil") ?: 0L\n'
        '                if (d.getBoolean("banned") == true && (until == 0L || until > System.currentTimeMillis())) {\n'
        '                    auth.signOut()\n'
        '                    android.widget.Toast.makeText(this, "Akun kamu diblokir 🚫", android.widget.Toast.LENGTH_LONG).show()\n'
        '                    startActivity(Intent(this, LoginActivity::class.java))\n'
        '                    finishAffinity()\n'
        '                }\n'
        '            }\n'
        '        }\n'), 1)
    open(p, "w", encoding="utf-8").write(s)
print("  ok MainActivity")
PY

echo "▶ 3/4 Cek kurung"
for f in UserAdminActivity.kt PanelDetailActivity.kt MainActivity.kt; do
  o=$(tr -cd '{' < "$D/$f" | wc -c); c=$(tr -cd '}' < "$D/$f" | wc -c)
  [ "$o" = "$c" ] || { echo "❌ kurung tidak seimbang di $f"; exit 1; }
done
echo "  ✔"

echo "▶ 4/4 Git"
if [ -n "$NO_GIT" ]; then echo "  (NO_GIT)"; exit 0; fi
git add -A
git commit -m "feat(panel): cari user, ban/unban, verifikasi, saldo koin + blokir akun saat login" || true
git push origin HEAD
echo "✅ Selesai"
