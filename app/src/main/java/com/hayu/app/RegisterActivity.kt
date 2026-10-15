package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail = findViewById<TextView>(R.id.et_email)
        val etPassword = findViewById<TextView>(R.id.et_password)
        val btnRegister = findViewById<MaterialButton>(R.id.btn_register)
        val tvBack = findViewById<TextView>(R.id.tv_back_login)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Email & password wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.length < 6) {
                Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    val uid = result.user!!.uid
                    val userData = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "nickname" to "",
                        "age" to 0,
                        "gender" to "",
                        "country" to "",
                        "bio" to "",
                        "photoUrl" to "",
                        "coins" to 100,
                        "level" to 1,
                        "online" to true,
                        "lastSeen" to System.currentTimeMillis(),
                        "createdAt" to System.currentTimeMillis(),
                        "onboarded" to false
                    )
                    db.collection("users").document(uid).set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Akun dibuat! Lengkapi profil dulu 🎉", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, OnboardingActivity::class.java))
                            finishAffinity()
                        }
                        .addOnFailureListener { e ->
                            btnRegister.isEnabled = true
                            Toast.makeText(this, "Gagal simpan profil: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    btnRegister.isEnabled = true
                    Toast.makeText(this, "Gagal daftar: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        tvBack.setOnClickListener { finish() }
    }
}
