package com.hayu.app

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextView>(R.id.et_email)
        val etPassword = findViewById<TextView>(R.id.et_password)
        val btnLogin = findViewById<TextView>(R.id.btn_login)
        val tvRegister = findViewById<TextView>(R.id.tv_register)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Email & password wajib diisi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Login belum aktif — tunggu update berikutnya 😊", Toast.LENGTH_SHORT).show()
            }
        }

        tvRegister.setOnClickListener {
            Toast.makeText(this, "Halaman Daftar segera hadir 🚀", Toast.LENGTH_SHORT).show()
        }
    }
}
