package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DevPanelActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var btnBack: ImageButton
    private lateinit var tvPanelTitle: TextView
    private lateinit var tvPanelDesc: TextView
    private lateinit var tvRoleBadge: TextView

    private lateinit var cardMenuUser: CardView
    private lateinit var cardMenuRoom: CardView
    private lateinit var cardMenuFamily: CardView
    private lateinit var cardMenuSystem: CardView
    private lateinit var cardMenuRole: CardView
    private lateinit var btnLogout: Button

    private var currentRole: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_panel)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        bindViews()
        setupListeners()
        loadUserRole()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvPanelTitle = findViewById(R.id.tvPanelTitle)
        tvPanelDesc = findViewById(R.id.tvPanelDesc)
        tvRoleBadge = findViewById(R.id.tvRoleBadge)

        cardMenuUser = findViewById(R.id.cardMenuUser)
        cardMenuRoom = findViewById(R.id.cardMenuRoom)
        cardMenuFamily = findViewById(R.id.cardMenuFamily)
        cardMenuSystem = findViewById(R.id.cardMenuSystem)
        cardMenuRole = findViewById(R.id.cardMenuRole)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnLogout.setOnClickListener { showLogoutDialog() }

        cardMenuUser.setOnClickListener { toastSoon() }
        cardMenuRoom.setOnClickListener { toastSoon() }
        cardMenuFamily.setOnClickListener { toastSoon() }
        cardMenuSystem.setOnClickListener { startActivity(Intent(this, TopUpAdminActivity::class.java)) }

        // Menu "Tambah/Ubah Role" → buka RoleManagerActivity
        cardMenuRole.setOnClickListener {
            startActivity(Intent(this, RoleManagerActivity::class.java))
        }
    }

    private fun toastSoon() {
        Toast.makeText(this, "Segera hadir! 🚧", Toast.LENGTH_SHORT).show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keluar")
            .setMessage("Yakin mau keluar dari panel?")
            .setPositiveButton("Ya") { _, _ ->
                auth.signOut()
                finishAffinity()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadUserRole() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Kamu belum login", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.lowercase()?.trim() ?: "user"
                currentRole = role

                if (role == "user" || role == "cs" || role.isEmpty()) {
                    Toast.makeText(this, "Akses ditolak 🚫", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                applyRoleUI(role)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal load role: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

// LANJUT BAGIAN 2 DI BAWAH INI
    private fun applyRoleUI(role: String) {
        when (role) {
            "developer" -> {
                tvPanelTitle.text = "Developer Panel"
                tvPanelDesc.text = "Akses penuh ke semua fitur"
                tvRoleBadge.text = "DEV"
                tvRoleBadge.setBackgroundColor(0xFF7C3AED.toInt())

                cardMenuUser.visibility = View.VISIBLE
                cardMenuRoom.visibility = View.VISIBLE
                cardMenuFamily.visibility = View.VISIBLE
                cardMenuSystem.visibility = View.VISIBLE
                cardMenuRole.visibility = View.VISIBLE
            }
            "superadmin" -> {
                tvPanelTitle.text = "SuperAdmin Panel"
                tvPanelDesc.text = "Kelola user & family"
                tvRoleBadge.text = "SA"
                tvRoleBadge.setBackgroundColor(0xFF2563EB.toInt())

                cardMenuUser.visibility = View.VISIBLE
                cardMenuFamily.visibility = View.VISIBLE
                cardMenuRole.visibility = View.VISIBLE
            }
            "admin" -> {
                tvPanelTitle.text = "Admin Panel"
                tvPanelDesc.text = "Kelola user"
                tvRoleBadge.text = "ADM"
                tvRoleBadge.setBackgroundColor(0xFF059669.toInt())

                cardMenuUser.visibility = View.VISIBLE
                cardMenuRole.visibility = View.VISIBLE
            }
            "bd" -> {
                tvPanelTitle.text = "BD Panel"
                tvPanelDesc.text = "Kelola family & bisnis"
                tvRoleBadge.text = "BD"
                tvRoleBadge.setBackgroundColor(0xFFD97706.toInt())

                cardMenuFamily.visibility = View.VISIBLE
                cardMenuRole.visibility = View.VISIBLE
            }
            else -> {
                Toast.makeText(this, "Role tidak dikenali 🚫", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
