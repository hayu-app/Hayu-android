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
