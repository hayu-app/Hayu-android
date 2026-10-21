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
