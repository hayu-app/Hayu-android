#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch 5: MULTI-ROLE + PANEL DINAMIS DI PROFIL
#  - 1 player bisa punya banyak role (field `roles` array; `role` = role tertinggi, kompatibel dgn kode lama)
#  - Profil: daftar "Panel <Role>" muncul otomatis, tepat di atas tombol Keluar
#  - Panel yang bukan haknya TIDAK dirender sama sekali (mis. Panel Developer)
#  - Kelola Role: centang banyak role sekaligus
#   cd ~/Hayu-android && bash patch-multirole.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
[ -f "$D/OrgActivity.kt" ] || { echo "❌ Jalankan patch-org.sh dulu"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python

echo "▶ 1/4 File baru: RolePanels.kt + RoleManagerActivity.kt (multi-role)"
cat > "$D/RolePanels.kt" <<'EOF'
package com.hayu.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Daftar panel dinamis di Profil: satu baris per role yang BENAR-BENAR dimiliki user. */
object RolePanels {

    private fun emoji(role: String) = when (role) {
        Role.DEVELOPER -> "👑"
        Role.SUPERADMIN -> "🛡️"
        Role.ADMIN -> "⭐"
        Role.BD -> "🏡"
        Role.AGENCY -> "🏢"
        Role.HOST -> "🎙️"
        Role.COINSELLER -> "🪙"
        Role.FINANCE -> "💰"
        Role.MODERATOR -> "🔍"
        Role.CS -> "🎧"
        else -> "🔐"
    }

    private fun title(role: String) = when (role) {
        Role.DEVELOPER -> "Developer"
        Role.SUPERADMIN -> "Superadmin"
        Role.ADMIN -> "Admin"
        Role.BD -> "BD"
        Role.AGENCY -> "Agency"
        Role.HOST -> "Host"
        Role.COINSELLER -> "Coinseller"
        Role.FINANCE -> "Finance"
        Role.MODERATOR -> "Moderator"
        Role.CS -> "CS"
        else -> role
    }

    fun build(ctx: Context, container: LinearLayout, roles: Set<String>) {
        container.removeAllViews()
        val owned = roles.filter { it != Role.USER }.sortedByDescending { Role.rankOf(it) }
        if (owned.isEmpty()) {
            container.visibility = View.GONE   // user biasa: tidak ada panel sama sekali
            return
        }
        container.visibility = View.VISIBLE
        val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).toInt() }

        val gd = GradientDrawable()
        gd.setColor(Color.parseColor("#12122A"))
        gd.cornerRadius = dp(14).toFloat()
        gd.setStroke(dp(1), Color.parseColor("#2E2E50"))
        container.background = gd

        val head = TextView(ctx)
        head.text = "AKSES PANEL"
        head.textSize = 11f
        head.setTextColor(Color.parseColor("#8892AA"))
        head.setPadding(dp(16), dp(12), dp(16), dp(4))
        container.addView(head)

        for ((i, role) in owned.withIndex()) {
            if (i > 0) {
                val line = View(ctx)
                line.setBackgroundColor(Color.parseColor("#1E1E40"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                lp.marginStart = dp(48)
                container.addView(line, lp)
            }
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), 0, dp(16), 0)

            val ic = TextView(ctx)
            ic.text = emoji(role)
            ic.textSize = 20f
            row.addView(ic)

            val tv = TextView(ctx)
            tv.text = "Panel ${title(role)}"
            tv.textSize = 14f
            tv.setTextColor(Color.parseColor("#F1F5F9"))
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.marginStart = dp(12)
            row.addView(tv, tvLp)

            val arrow = TextView(ctx)
            arrow.text = "›"
            arrow.textSize = 18f
            arrow.setTextColor(Color.parseColor("#8892AA"))
            row.addView(arrow)

            row.setOnClickListener { open(ctx, role, owned.toSet()) }
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        container.addView(View(ctx), LinearLayout.LayoutParams(1, dp(6)))
    }

    fun open(ctx: Context, role: String, owned: Set<String>) {
        if (role !in owned) return   // pengaman: hanya role yang dimiliki
        when (role) {
            Role.DEVELOPER, Role.SUPERADMIN, Role.ADMIN ->
                ctx.startActivity(Intent(ctx, DevPanelActivity::class.java).putExtra("role", role))
            else ->
                ctx.startActivity(
                    Intent(ctx, PanelDetailActivity::class.java)
                        .putExtra("panel_key", role)
                        .putExtra("panel_label", "Panel ${title(role)}")
                        .putExtra("my_role", role)
                )
        }
    }
}
EOF

cat > "$D/RoleManagerActivity.kt" <<'EOF'
package com.hayu.app

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import com.hayu.app.adapter.UserAdapter
import com.hayu.app.model.User

/** Kelola Role: 1 user boleh punya BANYAK role. Centang role yang ingin diberikan. */
class RoleManagerActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var btnBackRole: ImageButton
    private lateinit var etSearchUser: EditText
    private lateinit var btnSearch: ImageButton
    private lateinit var tvResultInfo: TextView
    private lateinit var progressBarRole: ProgressBar
    private lateinit var rvRoleUser: RecyclerView
    private lateinit var tvEmptyState: TextView

    private lateinit var userAdapter: UserAdapter
    private var currentRoles: Set<String> = emptySet()
    private var accessOk = false
    private var lastQuery = ""
    private val rolesByUid = mutableMapOf<String, Set<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_manager)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        btnBackRole = findViewById(R.id.btnBackRole)
        etSearchUser = findViewById(R.id.etSearchUser)
        btnSearch = findViewById(R.id.btnSearch)
        tvResultInfo = findViewById(R.id.tvResultInfo)
        progressBarRole = findViewById(R.id.progressBarRole)
        rvRoleUser = findViewById(R.id.rvRoleUser)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        userAdapter = UserAdapter { user -> onUserClicked(user) }
        rvRoleUser.layoutManager = LinearLayoutManager(this)
        rvRoleUser.adapter = userAdapter

        btnBackRole.setOnClickListener { finish() }
        btnSearch.setOnClickListener { doSearch() }
        etSearchUser.setOnEditorActionListener { _, _, _ -> doSearch(); true }

        checkAccess()
    }

    private fun checkAccess() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Belum login", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                currentRoles = Role.rolesOf(doc)
                if (!Role.canManageRolesAny(currentRoles)) {
                    Toast.makeText(this, "Akses ditolak 🚫", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    accessOk = true
                    tvResultInfo.text = "Login sebagai " +
                        currentRoles.sortedByDescending { Role.rankOf(it) }.joinToString(", ") { Role.label(it) }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal cek akses", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun doSearch() {
        if (!accessOk) {
            Toast.makeText(this, "Tunggu, mengecek akses…", Toast.LENGTH_SHORT).show()
            return
        }
        val query = etSearchUser.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Ketik nama, UserID, atau UID dulu", Toast.LENGTH_SHORT).show()
            return
        }
        lastQuery = query
        showLoading(true)
        tvResultInfo.text = ""

        val users = db.collection("users")
        val tasks = mutableListOf<Task<QuerySnapshot>>()
        query.toLongOrNull()?.let { tasks.add(users.whereEqualTo("userId", it).limit(10).get()) }
        if (query.length >= 20 && !query.contains(' ')) {
            tasks.add(users.whereEqualTo(FieldPath.documentId(), query).get())
        }
        for (v in linkedSetOf(query, query.lowercase(), query.replaceFirstChar { it.uppercase() })) {
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
                rolesByUid.clear()
                seen.values.forEach { rolesByUid[it.id] = Role.rolesOf(it) }
                val result = seen.values.map { doc -> User.fromDoc(doc.id, doc.data ?: emptyMap()) }
                showLoading(false)
                showResult(query, result)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Gagal cari: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showResult(query: String, users: List<User>) {
        if (users.isEmpty()) {
            rvRoleUser.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.text = "User \"$query\" tidak ditemukan 😕"
            tvResultInfo.text = ""
        } else {
            rvRoleUser.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
            tvResultInfo.text = "Ditemukan ${users.size} user"
            userAdapter.submitList(users)
        }
    }

    private fun onUserClicked(user: User) {
        if (user.uid == auth.currentUser?.uid) {
            Toast.makeText(this, "Tidak bisa mengubah role sendiri", Toast.LENGTH_SHORT).show()
            return
        }
        val targetRoles = rolesByUid[user.uid] ?: Role.rolesFrom(user.role, null)
        if (!Role.canManageTargetAny(currentRoles, targetRoles)) {
            Toast.makeText(this, "Role user ini setara/lebih tinggi dari kamu", Toast.LENGTH_LONG).show()
            return
        }
        showRolePicker(user, targetRoles)
    }

    // Centang banyak role sekaligus
    private fun showRolePicker(targetUser: User, targetRoles: Set<String>) {
        val options = Role.assignableByAny(currentRoles)
        if (options.isEmpty()) {
            Toast.makeText(this, "Tidak ada role yang bisa diberikan", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = options.map { Role.label(it) }.toTypedArray()
        val checked = BooleanArray(options.size) { options[it] in targetRoles }
        val before = options.filter { it in targetRoles }.toSet()

        AlertDialog.Builder(this)
            .setTitle("Role ${targetUser.nickname.ifEmpty { "user" }}")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Simpan") { _, _ ->
                val chosen = options.filterIndexed { i, _ -> checked[i] }
                if (chosen.toSet() == before) {
                    Toast.makeText(this, "Tidak ada perubahan", Toast.LENGTH_SHORT).show()
                } else {
                    updateUserRoles(targetUser, chosen)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Validasi ulang di server (transaction) + audit log
    private fun updateUserRoles(targetUser: User, chosen: List<String>) {
        val actorUid = auth.currentUser?.uid ?: return
        showLoading(true)

        val actorRef = db.collection("users").document(actorUid)
        val targetRef = db.collection("users").document(targetUser.uid)
        val logRef = db.collection("role_logs").document()

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actorRoles = Role.rolesOf(tx.get(actorRef))
            val oldRoles = Role.rolesOf(tx.get(targetRef))
            val allowed = Role.assignableByAny(actorRoles)

            if (!chosen.all { it in allowed }) throw IllegalStateException("Kamu tidak boleh memberi role itu")
            if (!Role.canManageTargetAny(actorRoles, oldRoles)) {
                throw IllegalStateException("Role target setara/lebih tinggi dari kamu")
            }

            val newRoles = (oldRoles - allowed.toSet()) + chosen
            tx.update(
                targetRef, mapOf<String, Any>(
                    "roles" to newRoles.toList(),
                    "role" to Role.top(newRoles),
                    "roleUpdatedBy" to actorUid,
                    "roleUpdatedAt" to Timestamp.now()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "targetUid" to targetUser.uid,
                    "targetName" to targetUser.nickname,
                    "oldRoles" to oldRoles.toList(),
                    "newRoles" to newRoles.toList(),
                    "byUid" to actorUid,
                    "byRole" to Role.top(actorRoles),
                    "at" to Timestamp.now()
                )
            )
            null
        })
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "✅ Role ${targetUser.nickname} diperbarui", Toast.LENGTH_LONG).show()
                if (lastQuery.isNotEmpty()) doSearch()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Gagal update: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showLoading(show: Boolean) {
        progressBarRole.visibility = if (show) View.VISIBLE else View.GONE
        btnSearch.isEnabled = !show
    }
}
EOF

echo "▶ 2/4 Patch semua file lama (atomik: gagal = tidak ada yang berubah)"
D="$D" python3 - <<'PY'
import os, re
D = os.environ["D"]
LAY = "app/src/main/res/layout/fragment_profile.xml"
files = {}

def P(n): return os.path.join(D, n)
def get(path):
    if path not in files:
        files[path] = open(path, encoding="utf-8").read()
    return files[path]

def rep(path, old, new, all_=False, optional=False):
    s = get(path)
    if old in s:
        files[path] = s.replace(old, new) if all_ else s.replace(old, new, 1)
    elif new in s:
        pass
    elif optional:
        print("  (lewati) %s: %s" % (os.path.basename(path), old[:50].replace("\n", " ")))
    else:
        raise SystemExit("❌ GAGAL: %s -> tidak ketemu: %s" % (path, old[:70]))

# ---------- Role.kt ----------
rk = P("Role.kt")
s = get(rk)
if "fun rolesOf" not in s:
    s = s.replace("package com.hayu.app\n", "package com.hayu.app\n\nimport com.google.firebase.firestore.DocumentSnapshot\n", 1)
    idx = s.rstrip().rfind("}")
    extra = '''
    // ================= MULTI-ROLE =================
    /** Gabungan field `roles` (array) + `role` (string lama). "user" = tidak punya role. */
    fun rolesFrom(primary: Any?, list: Any?): Set<String> {
        val out = linkedSetOf<String>()
        (list as? List<*>)?.forEach { r ->
            val n = normalize(r?.toString())
            if (n != USER && access.containsKey(n)) out.add(n)
        }
        val p = normalize(primary?.toString())
        if (p != USER && access.containsKey(p)) out.add(p)
        return out
    }

    fun rolesOf(doc: DocumentSnapshot?): Set<String> =
        if (doc == null) emptySet() else rolesFrom(doc.get("role"), doc.get("roles"))

    /** Role dengan pangkat tertinggi (disimpan juga di field `role` untuk kode lama). */
    fun top(roles: Collection<String>): String = roles.maxByOrNull { rankOf(it) } ?: USER

    fun canAccessAny(roles: Collection<String>, panel: String): Boolean = roles.any { canAccess(it, panel) }

    fun assignableByAny(roles: Collection<String>): List<String> =
        roles.flatMap { assignableBy(it) }.distinct()

    fun canManageRolesAny(roles: Collection<String>): Boolean = roles.any { canManageRoles(it) }

    /** Semua role target harus bisa diurus oleh salah satu role actor. */
    fun canManageTargetAny(actorRoles: Collection<String>, targetRoles: Collection<String>): Boolean {
        if (!canManageRolesAny(actorRoles)) return false
        val t = if (targetRoles.isEmpty()) listOf(USER) else targetRoles.toList()
        return t.all { tr -> actorRoles.any { canManageTarget(it, tr) } }
    }

    /** Badge semua role yang dimiliki, null jika user biasa. */
    fun badgeAll(roles: Collection<String>): String? =
        roles.sortedByDescending { rankOf(it) }.mapNotNull { badge(it) }.joinToString("  ").ifEmpty { null }
'''
    s = s[:idx] + extra + "}\n"
    files[rk] = s

# ---------- ListPageActivity ----------
lp = P("ListPageActivity.kt")
rep(lp, "    protected var myRole = Role.USER\n",
        "    protected var myRole = Role.USER\n    protected var myRoles: Set<String> = emptySet()\n")
rep(lp, '                myRole = Role.normalize(doc.getString("role"))\n',
        '                myRoles = Role.rolesOf(doc)\n                myRole = Role.top(myRoles)\n')
rep(lp, "    // Cari user: UserID (angka)",
'''    /** User yang memegang role tertentu (field `roles` array ATAU `role` lama), digabung tanpa duplikat. */
    protected fun usersByRole(role: String, limit: Long): Task<List<DocumentSnapshot>> {
        val users = db.collection("users")
        val tasks = listOf(
            users.whereArrayContains("roles", role).limit(limit).get(),
            users.whereEqualTo("role", role).limit(limit).get()
        )
        return Tasks.whenAllSuccess<QuerySnapshot>(tasks).continueWith { t ->
            val seen = linkedMapOf<String, DocumentSnapshot>()
            t.result.forEach { s -> s.documents.forEach { seen.putIfAbsent(it.id, it) } }
            seen.values.toList()
        }
    }

    // Cari user: UserID (angka)''')

# ---------- OrgActivity ----------
o = P("OrgActivity.kt")
rep(o, "private fun isBd() = myRole == Role.BD", "private fun isBd() = Role.BD in myRoles")
rep(o, "private fun isAgency() = myRole == Role.AGENCY", "private fun isAgency() = Role.AGENCY in myRoles")
rep(o, "private fun isOrgAdmin() = myRole in orgAdminRoles", "private fun isOrgAdmin() = myRoles.any { it in orgAdminRoles }")
rep(o, 'mode == "host_home" -> myRole == Role.HOST', 'mode == "host_home" -> Role.HOST in myRoles')
rep(o, "|| myRole == Role.HOST", "|| Role.HOST in myRoles")
rep(o, 'val shown = docs.filter { it.id != myUid && Role.normalize(it.getString("role")) == Role.USER }',
       'val wanted = if (mode == "register_agency") Role.AGENCY else Role.HOST\n        val shown = docs.filter { it.id != myUid && wanted !in Role.rolesOf(it) }')
rep(o, 'setInfo("Tidak ada user biasa yang cocok (hanya user tanpa role yang bisa dipilih)")',
       'setInfo("Tidak ada user yang cocok (atau sudah memegang role ini)")')
rep(o, 'val ar = Role.normalize(actor.getString("role"))', 'val ar = Role.rolesOf(actor)', all_=True)
rep(o, "if (ar != Role.BD && ar !in orgAdminRoles)", "if (Role.BD !in ar && ar.none { it in orgAdminRoles })")
rep(o, 'val ownBd = ar == Role.BD && t.getString("bdUid") == myUid', 'val ownBd = Role.BD in ar && t.getString("bdUid") == myUid')
rep(o, "if (!ownBd && ar !in orgAdminRoles)", "if (!ownBd && ar.none { it in orgAdminRoles })")
rep(o, 'val own = ar == Role.AGENCY && t.getString("agencyUid") == myUid', 'val own = Role.AGENCY in ar && t.getString("agencyUid") == myUid')
rep(o, "if (!own && ar !in orgAdminRoles)", "if (!own && ar.none { it in orgAdminRoles })")
# registerAgency (kemunculan pertama) lalu recruitHost (kedua)
rep(o, 'if (Role.normalize(t.getString("role")) != Role.USER) throw IllegalStateException("User ini sudah punya role")',
       'if (Role.AGENCY in Role.rolesOf(t)) throw IllegalStateException("User ini sudah menjadi agency")\n            val newRoles = Role.rolesOf(t) + Role.AGENCY')
rep(o, 'if (Role.normalize(t.getString("role")) != Role.USER) throw IllegalStateException("User ini sudah punya role")',
       'if (Role.HOST in Role.rolesOf(t)) throw IllegalStateException("User ini sudah menjadi host")\n            val newRoles = Role.rolesOf(t) + Role.HOST')
rep(o, '"role" to Role.AGENCY, "agencyStatus" to "pending"',
       '"role" to Role.top(newRoles), "roles" to newRoles.toList(), "agencyStatus" to "pending"')
rep(o, '"role" to Role.HOST, "hostStatus" to "pending"',
       '"role" to Role.top(newRoles), "roles" to newRoles.toList(), "hostStatus" to "pending"')
rep(o, 'if (Role.normalize(actor.getString("role")) != Role.AGENCY) throw', 'if (Role.AGENCY !in Role.rolesOf(actor)) throw')
rep(o, '"role" to Role.USER, "agencyStatus" to "rejected"',
       '"role" to Role.top(Role.rolesOf(t) - Role.AGENCY), "roles" to (Role.rolesOf(t) - Role.AGENCY).toList(), "agencyStatus" to "rejected"')
rep(o, '"role" to Role.USER, "hostStatus" to "rejected", "agencyUid" to FieldValue.delete()',
       '"role" to Role.top(Role.rolesOf(t) - Role.HOST), "roles" to (Role.rolesOf(t) - Role.HOST).toList(), "hostStatus" to "rejected", "agencyUid" to FieldValue.delete()')
rep(o, '"role" to Role.USER, "hostStatus" to FieldValue.delete(), "agencyUid" to FieldValue.delete()',
       '"role" to Role.top(Role.rolesOf(t) - Role.HOST), "roles" to (Role.rolesOf(t) - Role.HOST).toList(), "hostStatus" to FieldValue.delete(), "agencyUid" to FieldValue.delete()')
rep(o, 'snap.documents.filter { Role.normalize(it.getString("role")) == Role.HOST }', 'snap.documents.filter { Role.HOST in Role.rolesOf(it) }')
rep(o, 'Role.normalize(t.getString("role")) != Role.HOST) {', 'Role.HOST !in Role.rolesOf(t)) {')
rep(o, 'db.collection("users").whereEqualTo("role", Role.AGENCY).limit(200).get()', 'usersByRole(Role.AGENCY, 200)')
rep(o, 'db.collection("users").whereEqualTo("role", Role.HOST).limit(1000).get()', 'usersByRole(Role.HOST, 1000)')
rep(o, 'db.collection("users").whereEqualTo("role", Role.HOST).limit(200).get()', 'usersByRole(Role.HOST, 200)')
rep(o, "hSnap.documents", "hSnap", all_=True)
rep(o, "aSnap.documents", "aSnap", all_=True)
rep(o, "val top = snap.documents", "val top = snap")

# ---------- CoinAdminActivity ----------
c = P("CoinAdminActivity.kt")
rep(c, "    private var myRole = Role.USER\n", "    private var myRole = Role.USER\n    private var myRoles: Set<String> = emptySet()\n")
rep(c, '                myRole = Role.normalize(doc.getString("role"))\n',
       '                myRoles = Role.rolesOf(doc)\n                myRole = Role.top(myRoles)\n')
rep(c, "Role.canAccess(myRole, ", "Role.canAccessAny(myRoles, ", all_=True)
rep(c, "private fun isSeller() = myRole == Role.COINSELLER", "private fun isSeller() = Role.COINSELLER in myRoles")
rep(c, 'Role.normalize(u.getString("role")) == Role.COINSELLER', 'Role.COINSELLER in Role.rolesOf(u)')
rep(c, 'if (Role.normalize(actor.getString("role")) != Role.COINSELLER) {', 'if (Role.COINSELLER !in Role.rolesOf(actor)) {')
rep(c, 'if (!Role.canAccess(actor.getString("role"), "coin")) {', 'if (!Role.canAccessAny(Role.rolesOf(actor), "coin")) {')
rep(c, 'if (Role.normalize(target.getString("role")) != Role.COINSELLER) {', 'if (Role.COINSELLER !in Role.rolesOf(target)) {')

# ---------- UserAdminActivity ----------
u = P("UserAdminActivity.kt")
rep(u, "    private var myRole = Role.USER\n", "    private var myRole = Role.USER\n    private var myRoles: Set<String> = emptySet()\n")
rep(u, '                myRole = Role.normalize(doc.getString("role"))\n',
       '                myRoles = Role.rolesOf(doc)\n                myRole = Role.top(myRoles)\n')
rep(u, "Role.canAccess(myRole, ", "Role.canAccessAny(myRoles, ", all_=True)
rep(u, 'val role = Role.normalize(doc.getString("role"))', 'val role = Role.top(Role.rolesOf(doc))')
rep(u, '"Role: ${Role.label(doc.getString("role"))}\\n"',
       '"Role: ${Role.rolesOf(doc).joinToString(", ") { Role.label(it) }.ifEmpty { Role.label(Role.USER) }}\\n"')
rep(u, 'if (Role.rankOf(myRole) <= Role.rankOf(doc.getString("role"))) {',
       'if (Role.rankOf(Role.top(myRoles)) <= Role.rankOf(Role.top(Role.rolesOf(doc)))) {')
rep(u, 'val actorRole = Role.normalize(tx.get(actorRef).getString("role"))',
       'val actorRoles = Role.rolesOf(tx.get(actorRef))\n            val actorRole = Role.top(actorRoles)', all_=True)
rep(u, 'Role.canAccess(actorRole, "coin")', 'Role.canAccessAny(actorRoles, "coin")')
rep(u, 'Role.rankOf(actorRole) <= Role.rankOf(target.getString("role"))',
       'Role.rankOf(actorRole) <= Role.rankOf(Role.top(Role.rolesOf(target)))')

# ---------- TicketAdminActivity ----------
t = P("TicketAdminActivity.kt")
rep(t, "Role.canAccess(myRole, ", "Role.canAccessAny(myRoles, ", all_=True)
rep(t, "val onlyMine = myRole == Role.CS", "val onlyMine = Role.CS in myRoles && Role.top(myRoles) == Role.CS")

# ---------- TopUpAdminActivity ----------
tp = P("TopUpAdminActivity.kt")
rep(tp, 'val role = (user.getString("role") ?: "user").lowercase().trim()', 'val roles = Role.rolesOf(user)')
rep(tp, 'if (role != "developer" && role != "superadmin") {', 'if (Role.DEVELOPER !in roles && Role.SUPERADMIN !in roles) {')

# ---------- DevPanelActivity ----------
dv = P("DevPanelActivity.kt")
rep(dv, '                myRole = Role.normalize(doc.getString("role"))\n',
        '                val owned = Role.rolesOf(doc)\n'
        '                val want = intent.getStringExtra("role")\n'
        '                myRole = if (want != null && want in owned) want else Role.top(owned)\n')

# ---------- PanelDetailActivity ----------
pd = P("PanelDetailActivity.kt")
rep(pd, '(key == "dashboard" || key == "user") && Role.canManageRoles(role)',
        '(key == "dashboard" || key == "user" || key == "bd") && Role.canManageRoles(role)')

# ---------- UserProfileActivity / Lencana (badge & status staf) ----------
rep(P("UserProfileActivity.kt"), "val roleText = Role.badge(role)", "val roleText = Role.badgeAll(Role.rolesOf(doc))", optional=True)
rep(P("LencanaActivity.kt"), '(user.getString("role") ?: "user").lowercase().trim() != "user"', 'Role.rolesOf(user).isNotEmpty()', optional=True)

# ---------- ProfileFragment ----------
pf = P("ProfileFragment.kt")
s = get(pf)
if "applyRolesToUI" not in s:
    s = s.replace('                val role = doc.getString("role")?.lowercase()?.trim() ?: "user"\n                applyRoleToUI(rootView, role)\n',
                  '                applyRolesToUI(rootView, Role.rolesOf(doc))\n', 1)
    s = s.replace('                applyRoleToUI(rootView, "user")\n', '                applyRolesToUI(rootView, emptySet())\n', 1)
    new_fn = '''    private fun applyRolesToUI(rootView: View, roles: Set<String>) {
        if (!isAdded) return
        val badge = rootView.findViewById<TextView>(R.id.tvRoleBadgeProfile)
        val menuPanel = rootView.findViewById<View>(R.id.menu_dev_panel)
        val container = rootView.findViewById<LinearLayout>(R.id.panel_container)

        val text = Role.badgeAll(roles)
        if (text != null) {
            badge?.text = text
            badge?.visibility = View.VISIBLE
        } else {
            badge?.visibility = View.GONE
        }
        // Menu lama disembunyikan; panel sekarang dinamis per role yang dimiliki
        menuPanel?.visibility = View.GONE
        if (container != null) RolePanels.build(requireContext(), container, roles)
    }

'''
    s, n = re.subn(r'    private fun applyRoleToUI\(.*?(?=    private fun notif\()', lambda m: new_fn, s, count=1, flags=re.S)
    assert n == 1, "ProfileFragment: applyRoleToUI tidak ketemu"
    s, n = re.subn(r'        view\.findViewById<View>\(R\.id\.menu_settings\)\.setOnClickListener \{.*?\n        \}\n(?=        view\.findViewById<View>\(R\.id\.menu_help\))',
                   lambda m: '        view.findViewById<View>(R.id.menu_settings).setOnClickListener { notif("Pengaturan segera hadir ⚙️") }\n',
                   s, count=1, flags=re.S)
    assert n == 1, "ProfileFragment: menu_settings tidak ketemu"
    files[pf] = s

# ---------- Layout profil: container panel dinamis di atas tombol Keluar ----------
lay = get(LAY)
if "panel_container" not in lay:
    marker = "        <!-- LOGOUT -->"
    assert marker in lay, "Layout: penanda LOGOUT tidak ketemu"
    block = ('        <!-- PANEL AKSES DINAMIS (sesuai role) -->\n'
             '        <LinearLayout\n'
             '            android:id="@+id/panel_container"\n'
             '            android:layout_width="match_parent"\n'
             '            android:layout_height="wrap_content"\n'
             '            android:layout_marginStart="20dp"\n'
             '            android:layout_marginEnd="20dp"\n'
             '            android:layout_marginTop="14dp"\n'
             '            android:orientation="vertical"\n'
             '            android:visibility="gone" />\n\n')
    files[LAY] = lay.replace(marker, block + marker, 1)

# ---------- tulis semua (hanya jika semua berhasil) ----------
for path, text in files.items():
    open(path, "w", encoding="utf-8").write(text)
print("  ✔ %d file dipatch" % len(files))
PY

echo "▶ 3/4 Cek kurung"
for f in Role.kt RolePanels.kt RoleManagerActivity.kt ListPageActivity.kt OrgActivity.kt CoinAdminActivity.kt UserAdminActivity.kt TicketAdminActivity.kt TopUpAdminActivity.kt DevPanelActivity.kt PanelDetailActivity.kt ProfileFragment.kt; do
  o=$(tr -cd '{' < "$D/$f" | wc -c); c=$(tr -cd '}' < "$D/$f" | wc -c)
  [ "$o" = "$c" ] || { echo "❌ kurung tidak seimbang di $f ($o vs $c)"; exit 1; }
done
echo "  ✔"

echo "▶ 4/4 Git"
if [ -n "$NO_GIT" ]; then echo "  (NO_GIT)"; exit 0; fi
git add -A
git commit -m "feat(role): multi-role per user, panel dinamis di profil sesuai role, kelola role multi-pilih" || true
git push origin HEAD
echo "✅ Selesai"
