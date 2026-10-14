package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<TextView>(R.id.et_email)
        val etPassword = findViewById<TextView>(R.id.et_password)
        val btnLogin = findViewById<TextView>(R.id.btn_login)
        val tvRegister = findViewById<TextView>(R.id.tv_register)

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
    }
}
