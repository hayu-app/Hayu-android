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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    private var currentRole: String = ""

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
        userAdapter = UserAdapter { user ->
            onUserClicked(user)
        }
        rvRoleUser.layoutManager = LinearLayoutManager(this)
        rvRoleUser.adapter = userAdapter
    }

    private fun setupListeners() {
        btnBackRole.setOnClickListener { finish() }
        btnSearch.setOnClickListener { doSearch() }
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
                val role = Role.normalize(doc.getString("role"))
                currentRole = role

                val allowed = arrayOf("developer", "superadmin", "admin", "bd")
                if (role !in allowed) {
                    Toast.makeText(this, "Akses ditolak 🚫", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal cek akses", Toast.LENGTH_LONG).show()
                finish()
            }
    }

// LANJUT BAGIAN 2 DI BAWAH INI
    private fun doSearch() {
        val query = etSearchUser.text.toString().trim()

        if (query.isEmpty()) {
            Toast.makeText(this, "Ketik nama atau ID dulu", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        tvResultInfo.text = ""

        // Cari by userId (angka) atau nickname (prefix)
        val queryLower = query.lowercase()

        db.collection("users")
            .whereGreaterThanOrEqualTo("nickname", query)
            .whereLessThanOrEqualTo("nickname", query + "\uf8ff")
            .limit(30)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val nickname = doc.getString("nickname") ?: ""
                    val age = (doc.getLong("age") ?: 0L).toInt()
                    val gender = doc.getString("gender") ?: ""
                    val country = doc.getString("country") ?: ""
                    val bio = doc.getString("bio") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    val role = doc.getString("role") ?: "user"

                    User(
                        uid = uid,
                        nickname = nickname,
                        age = age,
                        gender = gender,
                        country = country,
                        bio = bio,
                        photoUrl = photoUrl,
                        role = role
                    )
                }

                showLoading(false)

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
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Gagal cari: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun onUserClicked(user: User) {
        showRolePicker(user)
    }

    private fun showRolePicker(targetUser: User) {
        val allowedRoles = when (currentRole) {
            "developer" -> arrayOf("superadmin", "admin", "cs", "bd", "user")
            "superadmin" -> arrayOf("admin", "cs", "bd", "user")
            "admin" -> arrayOf("cs", "bd", "user")
            "bd" -> arrayOf("user")
            else -> {
                Toast.makeText(this, "Tidak bisa ubah role", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val labels = allowedRoles.map { role ->
            when (role) {
                "superadmin" -> "🛡️ SuperAdmin"
                "admin" -> "⭐ Admin"
                "cs" -> "🎧 CS"
                "bd" -> "🏡 BD"
                "user" -> "👤 User (Hapus Role)"
                "developer" -> "👑 Developer"
                else -> role
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Ubah role: ${targetUser.nickname}")
            .setItems(labels) { _, which ->
                val chosenRole = allowedRoles[which]
                confirmRoleChange(targetUser, chosenRole)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmRoleChange(targetUser: User, newRole: String) {
        val roleLabel = when (newRole) {
            "superadmin" -> "SuperAdmin"
            "admin" -> "Admin"
            "cs" -> "CS"
            "bd" -> "BD"
            "user" -> "User biasa (hapus role)"
            else -> newRole
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi")
            .setMessage("Ubah role ${targetUser.nickname} menjadi $roleLabel?")
            .setPositiveButton("Ya") { _, _ ->
                updateUserRole(targetUser, newRole)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateUserRole(targetUser: User, newRole: String) {
        showLoading(true)

        db.collection("users").document(targetUser.uid)
            .update("role", newRole)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(
                    this,
                    "✅ Role ${targetUser.nickname} → $newRole",
                    Toast.LENGTH_LONG
                ).show()
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
