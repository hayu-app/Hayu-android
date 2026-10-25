package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Check-in harian 7 hari berturut-turut.
 * Hadiah tiap hari diatur developer di Firestore: app_config/daily_checkin -> "rewards" (list 7 angka).
 * Data user: lastDailyClaimDate (yyyy-MM-dd WIB), dailyStreak (hari berturut-turut).
 */
class DailyCoinActivity : AppCompatActivity() {

    companion object {
        val DEFAULT_REWARDS: List<Long> = listOf(3000L, 4000L, 6000L, 8000L, 10000L, 12000L, 16000L)
        private val WIB: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
        private const val DAILY_EXP = 5L

        private fun fmtDate(cal: Calendar): String {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            f.timeZone = WIB
            return f.format(cal.time)
        }

        // Tanggal kalender WIB. Check-in tersedia lagi setelah pukul 00.00 WIB.
        fun todayWib(): String = fmtDate(Calendar.getInstance(WIB))

        fun yesterdayWib(): String {
            val c = Calendar.getInstance(WIB)
            c.add(Calendar.DAY_OF_YEAR, -1)
            return fmtDate(c)
        }

        fun parseRewards(raw: Any?): List<Long> {
            val list = (raw as? List<*>)?.map { (it as? Number)?.toLong() ?: -1L }
            return if (list != null && list.size == 7 && list.all { it >= 0 }) list else DEFAULT_REWARDS
        }
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvStreak: TextView
    private lateinit var btnClaim: TextView
    private lateinit var grid: LinearLayout

    private var rewards: List<Long> = DEFAULT_REWARDS
    private var streak = 0L
    private var claimedToday = false
    private var loaded = false

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        buildUI()
        load()
    }

    private fun buildUI() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#B9000000"))
        root.setOnClickListener { finish() }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(d(16), d(18), d(16), d(18))
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#FF9A6B"), Color.parseColor("#FFD9B8"))
        ).apply { cornerRadius = d(24).toFloat() }
        card.isClickable = true

        val title = TextView(this)
        title.text = "Reward Check-in\nHarian"
        title.textSize = 24f
        title.setTextColor(Color.WHITE)
        title.setTypeface(title.typeface, Typeface.BOLD)
        card.addView(title)

        val sub = TextView(this)
        sub.text = "Check-in tiap hari tanpa putus untuk hadiah lebih besar"
        sub.textSize = 12f
        sub.setTextColor(Color.parseColor("#7A2E10"))
        sub.setPadding(0, d(6), 0, d(12))
        card.addView(sub)

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        card.addView(grid)

        tvStreak = TextView(this)
        tvStreak.textSize = 13f
        tvStreak.gravity = Gravity.CENTER
        tvStreak.setTextColor(Color.parseColor("#8A4B00"))
        tvStreak.setTypeface(tvStreak.typeface, Typeface.BOLD)
        tvStreak.setPadding(0, d(14), 0, d(10))
        card.addView(tvStreak)

        btnClaim = TextView(this)
        btnClaim.textSize = 18f
        btnClaim.gravity = Gravity.CENTER
        btnClaim.setTextColor(Color.WHITE)
        btnClaim.setTypeface(btnClaim.typeface, Typeface.BOLD)
        btnClaim.setPadding(0, d(14), 0, d(14))
        btnClaim.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#FF9A6B"), Color.parseColor("#FF6E9A"))
        ).apply { cornerRadius = d(30).toFloat() }
        btnClaim.setOnClickListener { onClaimClick() }
        card.addView(btnClaim)

        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            leftMargin = d(24); rightMargin = d(24)
        })

        val close = TextView(this)
        close.text = "✕"
        close.textSize = 18f
        close.gravity = Gravity.CENTER
        close.setTextColor(Color.WHITE)
        close.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#66000000"))
            setStroke(d(1), Color.WHITE)
        }
        close.setOnClickListener { finish() }
        root.addView(close, FrameLayout.LayoutParams(d(36), d(36), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            bottomMargin = d(70)
        })

        setContentView(root)
        render()
    }

    private fun load() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            tvStreak.text = "Silakan login terlebih dahulu"
            setButton("Login dulu", false)
            return
        }
        setButton("Memuat...", false)

        db.collection("app_config").document("daily_checkin").get()
            .addOnSuccessListener { cfg ->
                rewards = parseRewards(cfg.get("rewards"))
                loadUser(uid)
            }
            .addOnFailureListener {
                rewards = DEFAULT_REWARDS
                loadUser(uid)
            }
    }

    private fun loadUser(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val last = doc.getString("lastDailyClaimDate")
                val s = doc.getLong("dailyStreak") ?: 0L
                claimedToday = last == todayWib()
                streak = when (last) {
                    todayWib(), yesterdayWib() -> maxOf(s, 1L)
                    else -> 0L
                }
                loaded = true
                render()
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                tvStreak.text = "Gagal memuat data. Coba lagi."
                setButton("↻ Coba Lagi", true)
            }
    }

    // Posisi terakhir yang sudah diklaim di dalam siklus 7 hari (0..7)
    private fun cyclePos(): Int {
        if (streak <= 0L) return 0
        val p = (((streak - 1) % 7) + 1).toInt()
        // hari ke-7 sudah lewat dan belum klaim hari ini -> siklus baru
        return if (!claimedToday && p == 7) 0 else p
    }

    private fun render() {
        val pos = cyclePos()
        grid.removeAllViews()
        grid.addView(tileRow(1..4, pos), rowLp())
        grid.addView(tileRow(5..7, pos), rowLp())

        tvStreak.text = "Kamu telah check-in selama $streak hari berturut-turut"
        if (!loaded) return
        if (claimedToday) setButton("✓ Sudah Check-in", false)
        else setButton("Cek-in", true)
    }

    private fun rowLp() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) }

    private fun tileRow(days: IntRange, pos: Int): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (day in days) {
            val done = day <= pos
            val next = !claimedToday && day == pos + 1
            val weight = if (day == 7) 2f else 1f

            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.setPadding(d(4), d(10), d(4), d(10))
            tile.background = GradientDrawable().apply {
                cornerRadius = d(10).toFloat()
                setColor(
                    when {
                        next -> Color.parseColor("#FF4B3E")
                        done -> Color.parseColor("#EDEDED")
                        else -> Color.parseColor("#FFF3DC")
                    }
                )
            }

            val tag = TextView(this)
            tag.text = "$day Hari"
            tag.textSize = 13f
            tag.gravity = Gravity.CENTER
            tag.setTextColor(if (next) Color.WHITE else Color.parseColor("#7A6A5A"))
            tile.addView(tag)

            val icon = TextView(this)
            icon.text = if (done) "✔" else "🪙"
            icon.textSize = 20f
            icon.gravity = Gravity.CENTER
            icon.setTextColor(Color.parseColor("#9A9A9A"))
            tile.addView(icon)

            val amt = TextView(this)
            amt.text = "× ${rewards[day - 1]}"
            amt.textSize = 14f
            amt.gravity = Gravity.CENTER
            amt.setTypeface(amt.typeface, Typeface.BOLD)
            amt.setTextColor(if (next) Color.WHITE else Color.parseColor("#8A5A20"))
            tile.addView(amt)

            row.addView(tile, LinearLayout.LayoutParams(0, -2, weight).apply {
                if (day != days.first) leftMargin = d(8)
            })
        }
        return row
    }

    private fun setButton(label: String, enabled: Boolean) {
        btnClaim.text = label
        btnClaim.isEnabled = enabled
        btnClaim.alpha = if (enabled) 1f else 0.55f
    }

    private fun onClaimClick() {
        if (!loaded) { load(); return }
        claim()
    }

    private fun claim() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        setButton("Memproses...", false)

        val userRef = db.collection("users").document(uid)
        val cfgRef = db.collection("app_config").document("daily_checkin")

        db.runTransaction { tx ->
            // semua read dulu, baru write
            val snap = tx.get(userRef)
            val cfg = tx.get(cfgRef)
            if (!snap.exists()) throw IllegalStateException("Akun pengguna tidak ditemukan")

            val today = todayWib()
            val last = snap.getString("lastDailyClaimDate")
            if (last == today) return@runTransaction 0L

            val oldStreak = snap.getLong("dailyStreak") ?: 0L
            val newStreak = if (last == yesterdayWib()) oldStreak + 1 else 1L
            val day = (((newStreak - 1) % 7) + 1).toInt()
            val reward = parseRewards(cfg.get("rewards"))[day - 1]

            val coins = snap.getLong("coins") ?: 0L
            val totalEarned = snap.getLong("totalCoinsEarned") ?: 0L
            val exp = snap.getLong("exp") ?: 0L

            tx.update(
                userRef,
                mapOf(
                    "coins" to coins + reward,
                    "totalCoinsEarned" to totalEarned + reward,
                    "exp" to exp + DAILY_EXP,
                    "lastDailyClaimDate" to today,
                    "lastDailyClaim" to System.currentTimeMillis(),
                    "dailyStreak" to newStreak,
                    "checkinTotal" to ((snap.getLong("checkinTotal") ?: 0L) + 1L)
                )
            )
            reward
        }.addOnSuccessListener { reward ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            if (reward != null && reward > 0L) {
                ReferralManager.checkActivation(force = true)
                showReward(reward)
                load()
            } else {
                claimedToday = true
                render()
            }
        }.addOnFailureListener { error ->
            if (isFinishing || isDestroyed) return@addOnFailureListener
            setButton("↻ Coba Lagi", true)
            Toast.makeText(
                this,
                "Check-in gagal: ${error.localizedMessage ?: "Kesalahan tidak diketahui"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showReward(amount: Long) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(d(24), d(28), d(24), d(28))
        box.setBackgroundColor(Color.parseColor("#FFF4EC"))

        val coin = TextView(this)
        coin.text = "🪙"
        coin.textSize = 64f
        coin.gravity = Gravity.CENTER
        box.addView(coin)

        val amt = TextView(this)
        amt.text = "× $amount"
        amt.textSize = 32f
        amt.gravity = Gravity.CENTER
        amt.setTypeface(amt.typeface, Typeface.BOLD_ITALIC)
        amt.setTextColor(Color.parseColor("#F59E0B"))
        box.addView(amt)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Selamat")
            .setView(box)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::auth.isInitialized && loaded) load()
    }
}
