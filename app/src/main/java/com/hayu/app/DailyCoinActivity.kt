package com.hayu.app

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DailyCoinActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvCoinAmount: TextView
    private lateinit var tvCoinStatus: TextView
    private lateinit var btnClaim: TextView
    private lateinit var btnClose: TextView

    private val dailyCoin = 10L
    private val dailyExp = 5L
    private val wib = TimeZone.getTimeZone("Asia/Jakarta")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_coin)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvCoinAmount = findViewById(R.id.tv_coin_amount)
        tvCoinStatus = findViewById(R.id.tv_coin_status)
        btnClaim = findViewById(R.id.btn_claim_coin)
        btnClose = findViewById(R.id.btn_close_daily)

        tvCoinAmount.text = "+$dailyCoin Koin"

        btnClose.setOnClickListener { finish() }
        btnClaim.setOnClickListener { claimDailyCoin() }

        checkCanClaim()
    }

    // Tanggal kalender WIB. Klaim tersedia kembali setelah pukul 00.00 WIB.
    private fun todayWib(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = wib
        return format.format(Date())
    }

    private fun checkCanClaim() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            tvCoinStatus.text = "Silakan login terlebih dahulu"
            btnClaim.isEnabled = false
            btnClaim.alpha = 0.5f
            return
        }

        btnClaim.isEnabled = false
        tvCoinStatus.text = "Memeriksa klaim harian..."

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val lastDate = doc.getString("lastDailyClaimDate")
                val alreadyClaimed = lastDate == todayWib()

                if (alreadyClaimed) {
                    tvCoinStatus.text = "Kamu sudah klaim hari ini. Kembali besok!"
                    tvCoinStatus.setTextColor(Color.rgb(239, 180, 68))
                    btnClaim.text = "✓ Sudah Diklaim"
                    btnClaim.isEnabled = false
                    btnClaim.alpha = 0.6f
                } else {
                    tvCoinStatus.text = "Klaim hadiah harianmu sekarang!"
                    tvCoinStatus.setTextColor(Color.rgb(16, 185, 129))
                    btnClaim.text = "🎁 Klaim Sekarang"
                    btnClaim.isEnabled = true
                    btnClaim.alpha = 1f
                }
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                tvCoinStatus.text = "Gagal memuat data. Coba lagi."
                btnClaim.text = "↻ Coba Lagi"
                btnClaim.isEnabled = true
                btnClaim.alpha = 1f
            }
    }

    private fun claimDailyCoin() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(
                this,
                "Silakan login terlebih dahulu",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        btnClaim.isEnabled = false
        btnClaim.alpha = 0.5f
        btnClaim.text = "Memproses..."
        tvCoinStatus.text = "Memproses hadiah harian..."

        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)

            if (!snapshot.exists()) {
                throw IllegalStateException("Akun pengguna tidak ditemukan")
            }

            // Semua proses klaim harus memakai transaksi ini.
            val today = todayWib()
            val lastDate = snapshot.getString("lastDailyClaimDate")

            if (lastDate == today) {
                return@runTransaction false
            }

            val coins = snapshot.getLong("coins") ?: 0L
            val totalEarned = snapshot.getLong("totalCoinsEarned") ?: 0L
            val exp = snapshot.getLong("exp") ?: 0L

            transaction.update(
                userRef,
                mapOf(
                    "coins" to coins + dailyCoin,
                    "totalCoinsEarned" to totalEarned + dailyCoin,
                    "exp" to exp + dailyExp,
                    "lastDailyClaimDate" to today
                )
            )

            true
        }.addOnSuccessListener { claimed ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            if (claimed == true) {
                Toast.makeText(
                    this,
                    "Berhasil! +$dailyCoin Koin dan +$dailyExp EXP 🎉",
                    Toast.LENGTH_LONG
                ).show()

                tvCoinStatus.text = "Hadiah berhasil diklaim! ✓"
                tvCoinStatus.setTextColor(Color.rgb(16, 185, 129))
                btnClaim.text = "✓ Sudah Diklaim"
                btnClaim.isEnabled = false
                btnClaim.alpha = 0.6f
            } else {
                tvCoinStatus.text = "Kamu sudah klaim hari ini. Kembali besok!"
                btnClaim.text = "✓ Sudah Diklaim"
                btnClaim.isEnabled = false
                btnClaim.alpha = 0.6f
            }
        }.addOnFailureListener { error ->
            if (isFinishing || isDestroyed) return@addOnFailureListener

            tvCoinStatus.text = "Klaim gagal. Periksa koneksi lalu coba lagi."
            btnClaim.text = "↻ Coba Lagi"
            btnClaim.isEnabled = true
            btnClaim.alpha = 1f

            Toast.makeText(
                this,
                "Klaim gagal: ${error.localizedMessage ?: "Kesalahan tidak diketahui"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::auth.isInitialized) checkCanClaim()
    }
}
