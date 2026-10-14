package com.hayu.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res -> onSignInResult(res) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<TextView>(R.id.et_email)
        val etPassword = findViewById<TextView>(R.id.et_password)
        val btnLogin = findViewById<TextView>(R.id.btn_login)
        val tvRegister = findViewById<TextView>(R.id.tv_register)
        val btnGoogle = findViewById<LinearLayout>(R.id.btn_google)
        val btnPhone = findViewById<LinearLayout>(R.id.btn_phone)

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

            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    Toast.makeText(this, "Login berhasil! 👋", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnGoogle.setOnClickListener {
            startFirebaseUI(listOf(AuthUI.IdpConfig.GoogleBuilder().build()))
        }

        btnPhone.setOnClickListener {
            startFirebaseUI(listOf(AuthUI.IdpConfig.PhoneBuilder().build()))
        }
    }

    private fun startFirebaseUI(providers: List<AuthUI.IdpConfig>) {
        val intent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setTheme(R.style.Theme_HAYU)
            .build()
        signInLauncher.launch(intent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Login berhasil! 👋", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        } else {
            val err = result.idpResponse?.error?.message ?: "dibatalkan"
            Toast.makeText(this, "Login gagal: $err", Toast.LENGTH_LONG).show()
        }
    }
}
