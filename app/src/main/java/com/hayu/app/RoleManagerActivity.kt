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
                val role = doc.getString("role")?.lowercase()?.trim() ?: "user"
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
