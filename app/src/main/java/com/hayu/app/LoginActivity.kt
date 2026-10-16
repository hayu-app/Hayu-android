package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isPasswordVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // CEK DULU: kalau sudah login, langsung lanjut
        if (auth.currentUser != null) {
            checkUserData(auth.currentUser!!.uid)
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextView>(R.id.et_email)
        val etPassword = findViewById<TextView>(R.id.et_password)
        val btnTogglePassword = findViewById<TextView>(R.id.btn_toggle_password)
        val btnLogin = findViewById<TextView>(R.id.btn_login)
        val tvRegister = findViewById<TextView>(R.id.tv_register)

        // Toggle show/hide password
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnTogglePassword.text = "🙈"
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnTogglePassword.text = "👁️"
            }
            // Pindahkan kursor ke akhir teks
            etPassword.setSelection(etPassword.text.length)
        }

        btnLogin.setOnClickListener {
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

            btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    Toast.makeText(this, "Login berhasil! 👋", Toast.LENGTH_SHORT).show()
                    checkUserData(result.user!!.uid)
                }
                .addOnFailureListener { e ->
                    btnLogin.isEnabled = true
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun checkUserData(uid: String) {
        val userRef = db.collection("users").document(uid)

        userRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                    finishAffinity()
                    return@addOnSuccessListener
                }

                val userId = doc.getLong("userId") ?: 0L
                val onboarded = doc.getBoolean("onboarded") ?: false

                if (userId == 0L) {
                    generateUniqueIdForUser(uid, userRef, onboarded)
                } else {
                    navigateBasedOnOnboarding(onboarded)
                }
            }
            .addOnFailureListener {
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
    }

    private fun generateUniqueIdForUser(
        uid: String,
        userRef: com.google.firebase.firestore.DocumentReference,
        onboarded: Boolean
    ) {
        val candidateId = (1000000L..9999999L).random()

        db.collection("users")
            .whereEqualTo("userId", candidateId)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    userRef.update("userId", candidateId)
                        .addOnSuccessListener {
                            navigateBasedOnOnboarding(onboarded)
                        }
                        .addOnFailureListener {
                            navigateBasedOnOnboarding(onboarded)
                        }
                } else {
                    generateUniqueIdForUser(uid, userRef, onboarded)
                }
            }
            .addOnFailureListener {
                navigateBasedOnOnboarding(onboarded)
            }
    }

    private fun navigateBasedOnOnboarding(onboarded: Boolean) {
        val target = if (onboarded) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, target))
        finishAffinity()
    }
}
