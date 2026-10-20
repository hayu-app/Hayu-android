package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class OtpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // layout inline programmatic — sederhana
        val root = android.widget.LinearLayout(this)
        root.orientation = android.widget.LinearLayout.VERTICAL
        root.gravity = android.view.Gravity.CENTER
        root.setPadding(64, 0, 64, 0)
        root.setBackgroundColor(android.graphics.Color.parseColor("#080818"))

        val tvTitle = TextView(this)
        tvTitle.text = "Masukkan Kode OTP"
        tvTitle.textSize = 22f
        tvTitle.setTextColor(android.graphics.Color.WHITE)
        tvTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tvTitle.gravity = android.view.Gravity.CENTER
        root.addView(tvTitle)

        val phone = intent.getStringExtra("phone") ?: ""
        val tvSub = TextView(this)
        tvSub.text = "Kode dikirim ke $phone"
        tvSub.textSize = 13f
        tvSub.setTextColor(android.graphics.Color.parseColor("#8892AA"))
        tvSub.gravity = android.view.Gravity.CENTER
        val subLp = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        subLp.topMargin = 8
        subLp.bottomMargin = 32
        root.addView(tvSub, subLp)

        val etOtp = EditText(this)
        etOtp.hint = "Kode 6 digit"
        etOtp.textSize = 24f
        etOtp.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        etOtp.gravity = android.view.Gravity.CENTER
        etOtp.setTextColor(android.graphics.Color.WHITE)
        etOtp.setHintTextColor(android.graphics.Color.parseColor("#8892AA"))
        etOtp.setBackgroundResource(R.drawable.bg_input)
        val otpLp = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 140
        )
        otpLp.bottomMargin = 20
        root.addView(etOtp, otpLp)

        val btnVerify = TextView(this)
        btnVerify.text = "Verifikasi"
        btnVerify.textSize = 16f
        btnVerify.typeface = android.graphics.Typeface.DEFAULT_BOLD
        btnVerify.setTextColor(android.graphics.Color.WHITE)
        btnVerify.gravity = android.view.Gravity.CENTER
        btnVerify.setBackgroundResource(R.drawable.bg_button_primary)
        val btnLp = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 140
        )
        root.addView(btnVerify, btnLp)

        setContentView(root)

        val verificationId = intent.getStringExtra("verificationId") ?: ""

        btnVerify.setOnClickListener {
            val code = etOtp.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "Kode harus 6 digit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnVerify.isEnabled = false
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    Toast.makeText(this, "Login berhasil! 👋", Toast.LENGTH_SHORT).show()
                    val uid = result.user!!.uid
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            val target = if (doc.exists() && doc.getBoolean("onboarded") == true)
                                MainActivity::class.java else OnboardingActivity::class.java
                            startActivity(Intent(this, target))
                            finishAffinity()
                        }
                        .addOnFailureListener {
                            startActivity(Intent(this, MainActivity::class.java))
                            finishAffinity()
                        }
                }
                .addOnFailureListener { e ->
                    btnVerify.isEnabled = true
                    Toast.makeText(this, "Kode salah: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
