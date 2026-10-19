package com.hayu.app

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DailyCoinActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val DAILY_COIN = 10
    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_coin)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val tvCoinAmount = findViewById<TextView>(R.id.tv_coin_amount)
        val tvCoinStatus = findViewById<TextView>(R.id.tv_coin_status)
        val btnClaim = findViewById<TextView>(R.id.btn_claim_coin)
        val btnClose = findViewById<TextView>(R.id.btn_close_daily)

        tvCoinAmount.text = "+$DAILY_COIN Koin"

        btnClose.setOnClickListener { finish() }

        checkCanClaim(tvCoinStatus, btnClaim)

        btnClaim.setOnClickListener {
            claimDailyCoin(tvCoinStatus, btnClaim)
        }
    }

    private fun checkCanClaim(tvStatus: TextView, btnClaim: TextView) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val lastClaim = doc.getLong("lastDailyClaim") ?: 0L
                val now = System.currentTimeMillis()
                val timeSince = now - lastClaim

                if (timeSince < ONE_DAY_MS) {
                    val remainingMs = ONE_DAY_MS - timeSince
                    val hours = remainingMs / (60 * 60 * 1000)
                    val minutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000)

                    tvStatus.text = "Sudah klaim hari ini. Tunggu ${hours}j ${minutes}m lagi"
                    tvStatus.setTextColor(0xFFEF4444.toInt())

                    btnClaim.isEnabled = false
                    btnClaim.alpha = 0.5f
                    btnClaim.text = "⏳  Sudah Diklaim"
                } else {
                    tvStatus.text = "Klaim 1x setiap hari"
                    btnClaim.isEnabled = true
                    btnClaim.alpha = 1.0f
                    btnClaim.text = "🎁  Klaim Sekarang"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun claimDailyCoin(tvStatus: TextView, btnClaim: TextView) {
        val uid = auth.currentUser?.uid ?: return

        btnClaim.isEnabled = false
        btnClaim.text = "Memproses..."

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val currentCoins = (doc.getLong("coins") ?: 0L).toInt()
                val totalEarned = (doc.getLong("totalCoinsEarned") ?: 0L).toInt()
                val lastClaim = doc.getLong("lastDailyClaim") ?: 0L
                val now = System.currentTimeMillis()

                if (now - lastClaim < ONE_DAY_MS) {
                    Toast.makeText(this, "Kamu sudah klaim hari ini!", Toast.LENGTH_SHORT).show()
                    checkCanClaim(tvStatus, btnClaim)
                    return@addOnSuccessListener
                }

                val updates = mapOf(
                    "coins" to (currentCoins + DAILY_COIN),
                    "totalCoinsEarned" to (totalEarned + DAILY_COIN),
                    "lastDailyClaim" to now,
                    "exp" to ((doc.getLong("exp") ?: 0L).toInt() + 5)
                )

                db.collection("users").document(uid).update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Berhasil! +$DAILY_COIN Koin 🎉", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "Koin berhasil diklaim! ✅"
                        tvStatus.setTextColor(0xFF10B981.toInt())
                        btnClaim.text = "✅  Diklaim"
                    }
                    .addOnFailureListener { e ->
                        btnClaim.isEnabled = true
                        btnClaim.text = "🎁  Klaim Sekarang"
                        Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                btnClaim.isEnabled = true
                btnClaim.text = "🎁  Klaim Sekarang"
                Toast.makeText(this, "Gagal memuat", Toast.LENGTH_SHORT).show()
            }
    }
}
