#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch fitur ROLE (jalankan di root repo Hayu-android)
#   cd ~/Hayu-android && bash patch-role.sh
set -e

REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"

if [ ! -d "$D" ]; then
  echo "❌ Jalankan di root repo Hayu-android"; exit 1
fi
command -v python3 >/dev/null 2>&1 || pkg install -y python
command -v git >/dev/null 2>&1 || pkg install -y git

echo "▶ 1/6 Role.kt"
cat > "$D/Role.kt" <<'EOF'
package com.hayu.app

object Role {
    const val DEVELOPER    = "developer"
    const val SUPERADMIN   = "superadmin"
    const val ADMIN        = "admin"
    const val BD           = "bd"
    const val AGENCY       = "agency"
    const val HOST         = "host"
    const val COINSELLER   = "coinseller"
    const val FINANCE      = "finance"
    const val MODERATOR    = "moderator"
    const val CS           = "cs"
    const val USER         = "user"

    val access = mapOf(
        DEVELOPER   to listOf("all"),
        SUPERADMIN  to listOf("dashboard","user","admin","bd","agency","host","coinseller","finance","moderator","cs","room","coin","vip","event","security","notif","system","report"),
        ADMIN       to listOf("dashboard","user","room","host","agency","bd","coinseller","report","ban","event","transaction","cs","verify","logs"),
        BD          to listOf("dashboard","agency","host","commission","report","recruit"),
        AGENCY      to listOf("dashboard","host","attendance","performance","commission","withdrawal","member"),
        HOST        to listOf("dashboard","room","gift","earnings","level","task","attendance","ranking","withdrawal","wallet"),
        COINSELLER  to listOf("dashboard","customer","topup","order","wallet","commission","report"),
        FINANCE     to listOf("dashboard","revenue","transaction","commission","withdrawal","refund","report"),
        MODERATOR   to listOf("dashboard","room","report","ban","chat","appeal","history"),
        CS          to listOf("dashboard","complaint","recovery","refund","ticket","support"),
        USER        to listOf()
    )

    // Hirarki: angka lebih besar = lebih tinggi
    private val rank = mapOf(
        DEVELOPER to 100, SUPERADMIN to 90, ADMIN to 80, BD to 60, AGENCY to 50,
        FINANCE to 45, MODERATOR to 40, COINSELLER to 35, CS to 30, HOST to 20, USER to 0
    )

    // Role apa saja yang boleh DIBERIKAN oleh masing-masing role
    private val assignable = mapOf(
        DEVELOPER  to listOf(SUPERADMIN, ADMIN, BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
        SUPERADMIN to listOf(ADMIN, BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
        ADMIN      to listOf(BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
        BD         to listOf(AGENCY, HOST, USER)
    )

    fun normalize(raw: String?): String {
        val r = (raw ?: USER).trim().lowercase()
        return if (r.isEmpty()) USER else r
    }

    fun canAccess(role: String?, panel: String): Boolean {
        val normalizedRole = normalize(role)
        val perms = access[normalizedRole] ?: return false
        return perms.contains("all") || perms.contains(panel)
    }

    fun rankOf(role: String?): Int = rank[normalize(role)] ?: 0

    fun assignableBy(actorRole: String?): List<String> = assignable[normalize(actorRole)] ?: emptyList()

    fun canManageRoles(role: String?): Boolean = assignableBy(role).isNotEmpty()

    /** Actor boleh mengubah role target hanya jika pangkat actor LEBIH TINGGI dari target. */
    fun canManageTarget(actorRole: String?, targetRole: String?): Boolean =
        canManageRoles(actorRole) && rankOf(actorRole) > rankOf(targetRole)

    fun label(role: String?): String = when (normalize(role)) {
        DEVELOPER -> "👑 Developer"
        SUPERADMIN -> "🛡️ SuperAdmin"
        ADMIN -> "⭐ Admin"
        BD -> "🏡 BD"
        AGENCY -> "🏢 Agency"
        HOST -> "🎙️ Host"
        COINSELLER -> "🪙 Coinseller"
        FINANCE -> "💰 Finance"
        MODERATOR -> "🔍 Moderator"
        CS -> "🎧 CS"
        else -> "👤 User (tanpa role)"
    }

    /** Teks badge panjang (profil). null = user biasa, badge disembunyikan. */
    fun badge(role: String?): String? = when (normalize(role)) {
        DEVELOPER -> "🛡️ DEVELOPER"
        SUPERADMIN -> "🛡️ SUPERADMIN"
        ADMIN -> "⭐ ADMIN"
        BD -> "🏡 BD"
        AGENCY -> "🏢 AGENCY"
        HOST -> "🎙️ HOST"
        COINSELLER -> "🪙 COINSELLER"
        FINANCE -> "💰 FINANCE"
        MODERATOR -> "🔍 MODERATOR"
        CS -> "🎧 CS"
        else -> null
    }

    /** Teks badge pendek (list user). */
    fun badgeShort(role: String?): String? = when (normalize(role)) {
        DEVELOPER -> "🛡️ DEV"
        SUPERADMIN -> "🛡️ SA"
        ADMIN -> "⭐ ADM"
        BD -> "🏡 BD"
        AGENCY -> "🏢 AGN"
        HOST -> "🎙️ HOST"
        COINSELLER -> "🪙 COIN"
        FINANCE -> "💰 FIN"
        MODERATOR -> "🔍 MOD"
        CS -> "🎧 CS"
        else -> null
    }
}
EOF

echo "▶ 2/6 RoleManagerActivity.kt"
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.hayu.app.adapter.UserAdapter
import com.hayu.app.model.User

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
    private var currentRole: String = Role.USER
    private var accessOk = false
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_manager)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        bindViews()
        setupRecyclerView()
        setupListeners()
        checkAccess()
    }

    private fun bindViews() {
        btnBackRole = findViewById(R.id.btnBackRole)
        etSearchUser = findViewById(R.id.etSearchUser)
        btnSearch = findViewById(R.id.btnSearch)
        tvResultInfo = findViewById(R.id.tvResultInfo)
        progressBarRole = findViewById(R.id.progressBarRole)
        rvRoleUser = findViewById(R.id.rvRoleUser)
        tvEmptyState = findViewById(R.id.tvEmptyState)
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter { user -> onUserClicked(user) }
        rvRoleUser.layoutManager = LinearLayoutManager(this)
        rvRoleUser.adapter = userAdapter
    }

    private fun setupListeners() {
        btnBackRole.setOnClickListener { finish() }
        btnSearch.setOnClickListener { doSearch() }
        etSearchUser.setOnEditorActionListener { _, _, _ -> doSearch(); true }
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
                currentRole = Role.normalize(doc.getString("role"))
                if (!Role.canManageRoles(currentRole)) {
                    Toast.makeText(this, "Akses ditolak 🚫", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    accessOk = true
                    tvResultInfo.text = "Login sebagai ${Role.label(currentRole)}"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal cek akses", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    // Cari by: UserID (angka), UID (exact), atau Nickname (prefix, tidak sensitif huruf besar/kecil)
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
        val tasks = mutableListOf<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>>()

        query.toLongOrNull()?.let { tasks.add(users.whereEqualTo("userId", it).limit(10).get()) }

        if (query.length >= 20 && !query.contains(' ')) {
            tasks.add(users.whereEqualTo(FieldPath.documentId(), query).get())
        }

        val variants = linkedSetOf(
            query,
            query.lowercase(),
            query.replaceFirstChar { it.uppercase() }
        )
        for (v in variants) {
            tasks.add(
                users.whereGreaterThanOrEqualTo("nickname", v)
                    .whereLessThanOrEqualTo("nickname", v + "\uf8ff")
                    .limit(30).get()
            )
        }

        Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(tasks)
            .addOnSuccessListener { snaps ->
                val seen = linkedMapOf<String, DocumentSnapshot>()
                snaps.forEach { s -> s.documents.forEach { seen.putIfAbsent(it.id, it) } }
                val result = seen.values.map { doc ->
                    User.fromDoc(doc.id, doc.data ?: emptyMap())
                }
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
        if (!Role.canManageTarget(currentRole, user.role)) {
            Toast.makeText(
                this,
                "Role ${Role.label(user.role)} setara/lebih tinggi dari kamu",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        showRolePicker(user)
    }

    private fun showRolePicker(targetUser: User) {
        val targetNow = Role.normalize(targetUser.role)
        val options = Role.assignableBy(currentRole).filter { it != targetNow }
        if (options.isEmpty()) {
            Toast.makeText(this, "Tidak ada role yang bisa diberikan", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = options.map { Role.label(it) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${targetUser.nickname.ifEmpty { "User" }} (${Role.label(targetNow)})")
            .setItems(labels) { _, which -> confirmRoleChange(targetUser, options[which]) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmRoleChange(targetUser: User, newRole: String) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi")
            .setMessage("Ubah role ${targetUser.nickname.ifEmpty { "user ini" }} menjadi ${Role.label(newRole)}?")
            .setPositiveButton("Ya") { _, _ -> updateUserRole(targetUser, newRole) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Validasi ulang di server (transaction) + tulis audit log
    private fun updateUserRole(targetUser: User, newRole: String) {
        val actorUid = auth.currentUser?.uid ?: return
        showLoading(true)

        val actorRef = db.collection("users").document(actorUid)
        val targetRef = db.collection("users").document(targetUser.uid)
        val logRef = db.collection("role_logs").document()

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actorRole = Role.normalize(tx.get(actorRef).getString("role"))
            val oldRole = Role.normalize(tx.get(targetRef).getString("role"))

            if (!Role.assignableBy(actorRole).contains(newRole)) {
                throw IllegalStateException("Kamu tidak boleh memberi role ini")
            }
            if (!Role.canManageTarget(actorRole, oldRole)) {
                throw IllegalStateException("Role target setara/lebih tinggi dari kamu")
            }

            tx.update(
                targetRef, mapOf(
                    "role" to newRole,
                    "roleUpdatedBy" to actorUid,
                    "roleUpdatedAt" to Timestamp.now()
                )
            )
            tx.set(
                logRef, mapOf(
                    "targetUid" to targetUser.uid,
                    "targetName" to targetUser.nickname,
                    "oldRole" to oldRole,
                    "newRole" to newRole,
                    "byUid" to actorUid,
                    "byRole" to actorRole,
                    "at" to Timestamp.now()
                )
            )
            null
        })
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(
                    this,
                    "✅ Role ${targetUser.nickname} → ${Role.label(newRole)}",
                    Toast.LENGTH_LONG
                ).show()
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

echo "▶ 3/6 Patch badge & akses (UserAdapter, UserProfileActivity, ProfileFragment, PanelDetailActivity)"
D="$D" python3 - <<'PY'
import os, re
D = os.environ["D"]

def rw(name, fn):
    p = os.path.join(D, name)
    s = open(p, encoding="utf-8").read()
    n = fn(s)
    assert n != s or "Role.badge" in s or "Kelola Role" in s, f"patch gagal/sudah beda: {name}"
    open(p, "w", encoding="utf-8").write(n)
    print("  ok", name)

# --- UserAdapter ---
def f_adapter(s):
    if "Role.badgeShort" in s: return s
    s = s.replace("import com.hayu.app.R\n", "import com.hayu.app.R\nimport com.hayu.app.Role\n", 1)
    new = ('// === BADGE ROLE ===\n'
           '            val badgeText = Role.badgeShort(user.role)\n'
           '            if (badgeText != null) {\n'
           '                tvRoleBadge.text = badgeText\n'
           '                tvRoleBadge.visibility = View.VISIBLE\n'
           '            } else {\n'
           '                tvRoleBadge.visibility = View.GONE\n'
           '            }\n\n'
           '            ')
    s2, c = re.subn(r'// === BADGE ROLE ===.*?(?=if \(user\.photoUrl\.isNotEmpty\(\)\))', new, s, count=1, flags=re.S)
    assert c == 1, "UserAdapter blok badge tidak ketemu"
    return s2
rw("adapter/UserAdapter.kt", f_adapter)

# --- UserProfileActivity ---
def f_uprof(s):
    if "Role.badge(role)" in s: return s
    new = ('// Tampilkan badge role\n'
           '                val roleText = Role.badge(role)\n'
           '                if (roleText != null) {\n'
           '                    tvRoleBadgeUser.text = roleText\n'
           '                    tvRoleBadgeUser.visibility = View.VISIBLE\n'
           '                } else {\n'
           '                    tvRoleBadgeUser.visibility = View.GONE\n'
           '                }\n')
    s2, c = re.subn(r'// Tampilkan badge role\n\s*when \(role\) \{.*?\n\s*\}\n', new, s, count=1, flags=re.S)
    assert c == 1, "UserProfileActivity blok badge tidak ketemu"
    return s2
rw("UserProfileActivity.kt", f_uprof)

# --- ProfileFragment ---
def f_pf(s):
    if "Role.badge(r)" in s: return s
    new = ('    private fun applyRoleToUI(rootView: View, role: String) {\n'
           '        val r = Role.normalize(role)\n'
           '        val badge = rootView.findViewById<TextView>(R.id.tvRoleBadgeProfile)\n'
           '        val menuPanel = rootView.findViewById<View>(R.id.menu_dev_panel)\n\n'
           '        val text = Role.badge(r)\n'
           '        if (text != null) {\n'
           '            badge?.text = text\n'
           '            badge?.visibility = View.VISIBLE\n'
           '        } else {\n'
           '            badge?.visibility = View.GONE\n'
           '        }\n'
           '        // Semua role yang punya akses dashboard bisa lihat menu panel\n'
           '        menuPanel?.visibility = if (Role.canAccess(r, "dashboard")) View.VISIBLE else View.GONE\n'
           '    }\n\n')
    s2, c = re.subn(r'    private fun applyRoleToUI\(.*?(?=    private fun notif\()', new, s, count=1, flags=re.S)
    assert c == 1, "ProfileFragment applyRoleToUI tidak ketemu"
    return s2
rw("ProfileFragment.kt", f_pf)

# --- PanelDetailActivity: tombol Kelola Role ---
def f_panel(s):
    if "Kelola Role" in s: return s
    if "import android.content.Intent" not in s:
        s = s.replace("import android.graphics.Color\n", "import android.content.Intent\nimport android.graphics.Color\n", 1)
    old_items = 'val items = panelContent[key] ?: listOf("🚧 Panel dalam pengembangan")'
    assert old_items in s, "PanelDetail items tidak ketemu"
    new_items = ('val items = (panelContent[key] ?: listOf("🚧 Panel dalam pengembangan")).toMutableList()\n'
                 '        val roleItem = "🎭 Kelola Role"\n'
                 '        if ((key == "dashboard" || key == "user") && Role.canManageRoles(role)) {\n'
                 '            items.add(0, roleItem)\n'
                 '        }')
    s = s.replace(old_items, new_items, 1)
    old_click = ('row.setOnClickListener {\n'
                 '                Toast.makeText(this, "$item — segera tersedia", Toast.LENGTH_SHORT).show()\n'
                 '            }')
    assert old_click in s, "PanelDetail click tidak ketemu"
    new_click = ('row.setOnClickListener {\n'
                 '                if (item == roleItem) {\n'
                 '                    startActivity(Intent(this, RoleManagerActivity::class.java))\n'
                 '                } else {\n'
                 '                    Toast.makeText(this, "$item — segera tersedia", Toast.LENGTH_SHORT).show()\n'
                 '                }\n'
                 '            }')
    return s.replace(old_click, new_click, 1)
rw("PanelDetailActivity.kt", f_panel)
PY

echo "▶ 4/6 Cek sintaks cepat"
for f in Role.kt RoleManagerActivity.kt adapter/UserAdapter.kt UserProfileActivity.kt ProfileFragment.kt PanelDetailActivity.kt; do
  o=$(tr -cd '{' < "$D/$f" | wc -c); c=$(tr -cd '}' < "$D/$f" | wc -c)
  [ "$o" = "$c" ] || { echo "❌ kurung { } tidak seimbang di $f ($o vs $c)"; exit 1; }
done
echo "  kurung seimbang ✔"

echo "▶ 5/6 Git commit"
if [ -n "$NO_GIT" ]; then echo "  (NO_GIT diset, lewati git)"; exit 0; fi
git add -A
git commit -m "feat(role): hirarki role lengkap, role manager aman + audit log, badge semua role, akses panel" || echo "  (tidak ada perubahan baru)"

echo "▶ 6/6 Push"
git push origin HEAD
echo "✅ Selesai. GitHub Actions akan build APK otomatis."
