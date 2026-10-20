package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Google login
        findViewById<android.view.View>(R.id.btn_google).setOnClickListener {
            Toast.makeText(this, "Login Google segera hadir!", Toast.LENGTH_SHORT).show()
        }

        // Facebook login
        findViewById<android.view.View>(R.id.btn_facebook).setOnClickListener {
            Toast.makeText(this, "Login Facebook segera hadir!", Toast.LENGTH_SHORT).show()
        }

        // OTP via No HP
        findViewById<TextView>(R.id.btn_send_otp).setOnClickListener {
            val phone = findViewById<android.widget.EditText>(R.id.et_phone)
                .text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Masukkan nomor HP dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fullPhone = "+62$phone"
            sendOtp(fullPhone)
        }

        // Register
        findViewById<TextView>(R.id.tv_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun sendOtp(phone: String) {
        Toast.makeText(this, "Mengirim OTP ke $phone...", Toast.LENGTH_SHORT).show()
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                }
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    Toast.makeText(this@LoginActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    Toast.makeText(this@LoginActivity, "Kode OTP terkirim!", Toast.LENGTH_SHORT).show()
                    // TODO: buka OTP input screen
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}
