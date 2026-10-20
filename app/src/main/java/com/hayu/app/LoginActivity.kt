package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val RC_GOOGLE = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            checkUserData(auth.currentUser!!.uid)
            return
        }

        setContentView(R.layout.activity_login)

        // --- GOOGLE ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleClient = GoogleSignIn.getClient(this, gso)

        findViewById<android.view.View>(R.id.btn_google).setOnClickListener {
            startActivityForResult(googleClient.signInIntent, RC_GOOGLE)
        }

        // --- FACEBOOK (placeholder — butuh FB SDK di build.gradle) ---
        findViewById<android.view.View>(R.id.btn_facebook).setOnClickListener {
            Toast.makeText(this, "Login Facebook segera hadir!", Toast.LENGTH_SHORT).show()
        }

        // --- NO HP + OTP ---
        findViewById<TextView>(R.id.btn_send_otp).setOnClickListener {
            val phone = findViewById<EditText>(R.id.et_phone).text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Masukkan nomor HP dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // hilangkan 0 di depan kalau ada
            val clean = if (phone.startsWith("0")) phone.substring(1) else phone
            val fullPhone = "+62$clean"
            sendOtp(fullPhone)
        }

        // --- REGISTER ---
        findViewById<TextView>(R.id.tv_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ── Google callback ──────────────────────────────────────────
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { result ->
                        Toast.makeText(this, "Login Google berhasil! 👋", Toast.LENGTH_SHORT).show()
                        checkUserData(result.user!!.uid)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── OTP ─────────────────────────────────────────────────────
    private fun sendOtp(phone: String) {
        Toast.makeText(this, "Mengirim OTP ke $phone...", Toast.LENGTH_SHORT).show()
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // auto-verify (jarang terjadi)
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { result ->
                            checkUserData(result.user!!.uid)
                        }
                }
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    Toast.makeText(this@LoginActivity, "OTP gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    // buka OtpActivity untuk input kode
                    val i = Intent(this@LoginActivity, OtpActivity::class.java)
                    i.putExtra("verificationId", verificationId)
                    i.putExtra("phone", phone)
                    startActivity(i)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // ── checkUserData (sama seperti sebelumnya) ──────────────────
    private fun checkUserData(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                    finishAffinity()
                    return@addOnSuccessListener
                }
                val userId = doc.getLong("userId") ?: 0L
                val onboarded = doc.getBoolean("onboarded") ?: false
                if (userId == 0L) {
                    generateUniqueIdForUser(uid, db.collection("users").document(uid), onboarded)
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
        db.collection("users").whereEqualTo("userId", candidateId).limit(1).get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    userRef.update("userId", candidateId)
                        .addOnSuccessListener { navigateBasedOnOnboarding(onboarded) }
                        .addOnFailureListener { navigateBasedOnOnboarding(onboarded) }
                } else {
                    generateUniqueIdForUser(uid, userRef, onboarded)
                }
            }
            .addOnFailureListener { navigateBasedOnOnboarding(onboarded) }
    }

    private fun navigateBasedOnOnboarding(onboarded: Boolean) {
        val target = if (onboarded) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, target))
        finishAffinity()
    }
}
