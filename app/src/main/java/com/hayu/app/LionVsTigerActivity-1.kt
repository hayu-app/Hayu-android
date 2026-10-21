package com.hayu.app

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class LionVsTigerActivity : AppCompatActivity() {

    private val gold = Color.rgb(255, 204, 65)
    private val amounts = listOf(1000, 10000, 100000, 200000, 500000)
    private val amountLabels = listOf("1000", "10K", "100K", "200K", "500K")
    private val sides = listOf("LION", "DRAW", "TIGER")
    private val betSeconds = 30
    private val fightMillis = 4200L
    private val resultMillis = 3500L
    private val multiplier = 3

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    private var coinListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())

    private var balance = 0
    private var profit = 0
    private var round = 1
    private var selectedAmount = 1000
    private var phase = "BET"
    private var deadline = 0L
    private var outcome = "LION"
    private var settled = true
    private var auto = false

    private val bets = HashMap<String, Int>()
    private var snapshotBets: Map<String, Int> = emptyMap()
    private var lastBets: Map<String, Int> = emptyMap()
    private val history = ArrayList<String>()
    private var fightAnim: ValueAnimator? = null

    private lateinit var roundText: TextView
    private lateinit var balanceText: TextView
    private lateinit var profitText: TextView
    private lateinit var countText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var banner: TextView
    private lateinit var arena: FrameLayout
    private lateinit var lionImage: ImageView
    private lateinit var tigerImage: ImageView
    private lateinit var flash: View
    private lateinit var historyRow: LinearLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var repeatBtn: TextView
    private val totalTexts = HashMap<String, TextView>()
    private val cards = HashMap<String, LinearLayout>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(color: Int, radius: Int, stroke: Int = gold, sw: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(sw), stroke)
        }

    private fun tv(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = true) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val me = uid
        if (me == null) {
            Toast.makeText(this, "Silakan login dulu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadRound()
        sides.forEach { bets[it] = 0 }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(24, 12, 48), Color.rgb(52, 24, 70), Color.rgb(20, 10, 36))
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        scroll.addView(root)
        setContentView(scroll)

        // ---- header ----
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(tv("‹", 34f, gold).apply { setOnClickListener { finish() } },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        roundText = tv("Round $round of Today", 18f).apply {
            background = box(Color.rgb(28, 16, 52), 20)
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        header.addView(roundText, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(View(this), LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // ---- arena ----
        arena = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(70, 40, 110), Color.rgb(230, 130, 60), Color.rgb(60, 30, 80))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), gold)
            }
        }
        val half = (resources.displayMetrics.widthPixels * 0.46f).toInt()
        lionImage = ImageView(this).apply {
            setImageResource(R.drawable.lion)
            scaleType = ImageView.ScaleType.FIT_CENTER
            cameraDistance = 12000f * resources.displayMetrics.density
        }
        tigerImage = ImageView(this).apply {
            setImageResource(R.drawable.tiger)
            scaleType = ImageView.ScaleType.FIT_CENTER
            cameraDistance = 12000f * resources.displayMetrics.density
        }
        arena.addView(lionImage, FrameLayout.LayoutParams(half, dp(250),
            Gravity.START or Gravity.TOP).apply { topMargin = dp(10) })
        arena.addView(tigerImage, FrameLayout.LayoutParams(half, dp(250),
            Gravity.END or Gravity.TOP).apply { topMargin = dp(10) })
        flash = View(this).apply { setBackgroundColor(Color.WHITE); alpha = 0f }
        arena.addView(flash, FrameLayout.LayoutParams(-1, -1))
        banner = tv("", 26f, gold).apply { visibility = View.GONE }
        arena.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        countText = tv("Please spend $betSeconds", 18f)
        arena.addView(countText, FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(30) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = betSeconds * 1000
            this.progress = max
            progressTintList = ColorStateList.valueOf(Color.rgb(40, 210, 70))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(30, 30, 40))
        }
        arena.addView(progress, FrameLayout.LayoutParams(-1, dp(10),
            Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), dp(14)) })
        root.addView(arena, LinearLayout.LayoutParams(-1, dp(270)).apply { topMargin = dp(6) })

        // ---- riwayat ----
        historyRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = box(Color.rgb(70, 20, 40), 16)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        root.addView(historyRow, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        renderHistory()

        // ---- kartu taruhan ----
        val cardRow = LinearLayout(this)
        sides.forEach { side -> cardRow.addView(buildCard(side),
            LinearLayout.LayoutParams(0, dp(170), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
        root.addView(cardRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // ---- chip nominal ----
        chipRow = LinearLayout(this)
        root.addView(chipRow, LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(8) })
        buildChips()

        // ---- bawah ----
        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = box(Color.rgb(60, 40, 20), 16)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        balanceText = tv("Coins Balance\n...", 14f, gold)
        balanceText.background = box(Color.rgb(20, 70, 70), 10)
        profitText = tv("Profits of Today\n0", 14f, gold)
        profitText.background = box(Color.rgb(20, 70, 70), 10)
        repeatBtn = tv("↻ REPEAT\nHold for Auto", 15f).apply {
            background = box(Color.rgb(30, 160, 60), 14)
            setOnClickListener { placeLastBets() }
            setOnLongClickListener {
                auto = !auto
                updateRepeatText()
                if (auto && phase == "BET") placeLastBets()
                true
            }
        }
        bottom.addView(balanceText, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        bottom.addView(profitText, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        bottom.addView(repeatBtn, LinearLayout.LayoutParams(0, dp(64), 1.1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        listenCoins(me)
        startBetting()
    }

    // ================= UI builders =================

    private fun buildCard(side: String): LinearLayout {
        val headColor = when (side) {
            "LION" -> Color.rgb(30, 90, 200)
            "DRAW" -> Color.rgb(30, 140, 60)
            else -> Color.rgb(200, 30, 40)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.rgb(90, 60, 20), 12)
            setOnClickListener { placeBet(side, selectedAmount) }
        }
        val head = tv(side.lowercase().replaceFirstChar { it.uppercase() }, 18f, gold).apply {
            background = box(headColor, 8)
        }
        card.addView(head, LinearLayout.LayoutParams(-1, dp(34)))
        val body = FrameLayout(this)
        when (side) {
            "LION" -> body.addView(ImageView(this).apply {
                setImageResource(R.drawable.lion); scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0.9f
            }, FrameLayout.LayoutParams(-1, -1))
            "TIGER" -> body.addView(ImageView(this).apply {
                setImageResource(R.drawable.tiger); scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0.9f
            }, FrameLayout.LayoutParams(-1, -1))
            else -> body.addView(tv("⚖️", 56f), FrameLayout.LayoutParams(-1, -1))
        }
        val info = tv("$multiplier times\nTotal: 0", 14f)
        totalTexts[side] = info
        body.addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        card.addView(body, LinearLayout.LayoutParams(-1, -1))
        cards[side] = card
        return card
    }

    private fun buildChips() {
        chipRow.removeAllViews()
        amounts.forEachIndexed { i, amount ->
            val sel = amount == selectedAmount
            val chip = tv(amountLabels[i], 14f, if (sel) Color.BLACK else Color.WHITE).apply {
                background = box(if (sel) gold else Color.rgb(80, 80, 95), 14)
                setOnClickListener { selectedAmount = amount; buildChips() }
            }
            chipRow.addView(chip, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun renderHistory() {
        historyRow.removeAllViews()
        for (i in 0 until 9) {
            val item = history.getOrNull(i)
            val size = dp(44)
            val view: View = when (item) {
                "LION", "TIGER" -> ImageView(this).apply {
                    setImageResource(if (item == "LION") R.drawable.lion else R.drawable.tiger)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Color.rgb(40, 30, 60)); setStroke(dp(2), gold)
                    }
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                }
                "DRAW" -> tv("⚖️", 16f).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Color.rgb(40, 30, 60)); setStroke(dp(2), gold)
                    }
                }
                else -> View(this)
            }
            historyRow.addView(view, LinearLayout.LayoutParams(0, size, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun updateRepeatText() {
        val total = lastBets.values.sum()
        repeatBtn.text = (if (auto) "↻ AUTO ON" else "↻ REPEAT") +
            "\n" + (if (auto) "Hold to stop" else if (total > 0) "${fmt(total)}" else "Hold for Auto")
    }

    private fun updateCards() {
        sides.forEach { totalTexts[it]?.text = "$multiplier times\nTotal: ${fmt(bets[it] ?: 0)}" }
    }

    private fun updateStats() {
        balanceText.text = "Coins Balance\n${fmt(balance)}"
        profitText.text = "Profits of Today\n${fmt(profit)}"
    }

    // ================= Coins (Firestore) =================

    private fun listenCoins(me: String) {
        coinListener = db.collection("users").document(me).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            balance = (snap.getLong("coins") ?: 0L).toInt()
            updateStats()
        }
    }

    private fun adjustCoins(delta: Int, onOk: () -> Unit = {}, onFail: (String) -> Unit = {}) {
        val me = uid ?: return onFail("Belum login")
        val ref = db.collection("users").document(me)
        db.runTransaction { tx ->
            val coins = tx.get(ref).getLong("coins") ?: 0L
            if (coins + delta < 0) throw IllegalStateException("Koin tidak cukup")
            tx.update(ref, "coins", coins + delta)
            null
        }.addOnSuccessListener { onOk() }
            .addOnFailureListener { onFail(it.message ?: "Gagal") }
    }

    // ================= Taruhan =================

    private fun placeBet(side: String, amount: Int) {
        if (phase != "BET") {
            Toast.makeText(this, "Tunggu ronde berikutnya", Toast.LENGTH_SHORT).show(); return
        }
        if (balance < amount) {
            Toast.makeText(this, "Koin tidak cukup", Toast.LENGTH_SHORT).show(); return
        }
        adjustCoins(-amount, onOk = {
            if (phase == "BET" && !isDestroyed) {
                bets[side] = (bets[side] ?: 0) + amount
                updateCards()
            } else {
                adjustCoins(amount) // waktu habis sebelum taruhan masuk -> kembalikan
            }
        }, onFail = { msg ->
            if (!isDestroyed) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        })
    }

    private fun placeLastBets() {
        if (phase != "BET") {
            Toast.makeText(this, "Tunggu ronde berikutnya", Toast.LENGTH_SHORT).show(); return
        }
        if (lastBets.isEmpty() || lastBets.values.sum() <= 0) {
            Toast.makeText(this, "Belum ada taruhan sebelumnya", Toast.LENGTH_SHORT).show(); return
        }
        val total = lastBets.values.sum()
        if (balance < total) {
            Toast.makeText(this, "Koin tidak cukup", Toast.LENGTH_SHORT).show(); return
        }
        val copy = HashMap(lastBets)
        adjustCoins(-total, onOk = {
            if (phase == "BET" && !isDestroyed) {
                copy.forEach { (s, a) -> bets[s] = (bets[s] ?: 0) + a }
                updateCards()
            } else {
                adjustCoins(total)
            }
        }, onFail = { msg ->
            if (!isDestroyed) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        })
    }

    // ================= Alur ronde otomatis =================

    private val tick = object : Runnable {
        override fun run() {
            if (phase != "BET") return
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) {
                startFight()
            } else {
                progress.progress = left.toInt()
                countText.text = "Please spend ${(left + 999) / 1000}"
                handler.postDelayed(this, 100)
            }
        }
    }

    private fun startBetting() {
        phase = "BET"
        settled = true
        sides.forEach { bets[it] = 0 }
        updateCards()
        resetArena()
        banner.visibility = View.GONE
        sides.forEach { cards[it]?.background = box(Color.rgb(90, 60, 20), 12) }
        roundText.text = "Round $round of Today"
        updateRepeatText()
        deadline = System.currentTimeMillis() + betSeconds * 1000L
        handler.post(tick)
        if (auto && lastBets.isNotEmpty()) placeLastBets()
    }

    private fun startFight() {
        phase = "FIGHT"
        snapshotBets = HashMap(bets)
        if (snapshotBets.values.sum() > 0) lastBets = snapshotBets
        outcome = sides[Random.nextInt(sides.size)]
        settled = false
        progress.progress = 0
        countText.text = "⚔️  Bertarung!  ⚔️"
        playFight()
        handler.postDelayed({ showResult() }, fightMillis)
    }

    private fun playFight() {
        val dist = arena.width * 0.17f
        lionImage.animate().translationX(dist).rotationY(-30f).scaleX(1.12f).scaleY(1.12f)
            .setDuration(700).start()
        tigerImage.animate().translationX(-dist).rotationY(30f).scaleX(1.12f).scaleY(1.12f)
            .setDuration(700).start()
        val shake = dp(26).toFloat()
        fightAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 700
            duration = 170
            repeatCount = 15
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val f = it.animatedValue as Float
                lionImage.translationX = dist + f * shake
                tigerImage.translationX = -dist - f * shake
                lionImage.rotationY = -30f + f * 14f
                tigerImage.rotationY = 30f - f * 14f
                arena.translationX = (f - 0.5f) * dp(6)
                flash.alpha = f * 0.22f
            }
            start()
        }
    }

    private fun resetArena() {
        fightAnim?.cancel()
        arena.translationX = 0f
        flash.alpha = 0f
        listOf(lionImage, tigerImage).forEach {
            it.animate().cancel()
            it.translationX = 0f; it.translationY = 0f
            it.rotationY = 0f; it.scaleX = 1f; it.scaleY = 1f; it.alpha = 1f
        }
    }

    private fun showResult() {
        phase = "RESULT"
        fightAnim?.cancel()
        arena.translationX = 0f
        flash.alpha = 0f
        val winner = when (outcome) { "LION" -> lionImage; "TIGER" -> tigerImage; else -> null }
        val loser = when (outcome) { "LION" -> tigerImage; "TIGER" -> lionImage; else -> null }
        winner?.animate()?.scaleX(1.4f)?.scaleY(1.4f)?.rotationY(0f)?.translationX(0f)
            ?.setDuration(500)?.start()
        loser?.animate()?.alpha(0.3f)?.scaleX(0.8f)?.scaleY(0.8f)?.translationY(dp(30).toFloat())
            ?.setDuration(500)?.start()
        if (outcome == "DRAW") {
            listOf(lionImage, tigerImage).forEach {
                it.animate().rotationY(0f).scaleX(1.1f).scaleY(1.1f).setDuration(400).start()
            }
        }
        cards[outcome]?.background = box(Color.rgb(120, 90, 20), 12, Color.WHITE, 3)

        val total = snapshotBets.values.sum()
        val pay = (snapshotBets[outcome] ?: 0) * multiplier
        val title = if (outcome == "DRAW") "SERI!" else "$outcome MENANG!"
        banner.text = when {
            total == 0 -> title
            pay > 0 -> "$title\n+${fmt(pay - total)}"
            else -> "$title\n-${fmt(total)}"
        }
        banner.visibility = View.VISIBLE
        countText.text = "Round $round selesai"
        settle()

        history.add(0, outcome)
        while (history.size > 9) history.removeAt(history.size - 1)
        renderHistory()

        handler.postDelayed({
            round++
            saveRound()
            startBetting()
        }, resultMillis)
    }

    private fun settle() {
        if (settled) return
        settled = true
        val total = snapshotBets.values.sum()
        val pay = (snapshotBets[outcome] ?: 0) * multiplier
        if (total > 0) profit += pay - total
        if (pay > 0) adjustCoins(pay)
        updateStats()
    }

    // ================= Nomor ronde harian =================

    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun loadRound() {
        val p = getSharedPreferences("lion_vs_tiger", MODE_PRIVATE)
        round = if (p.getString("date", "") == today()) p.getInt("round", 1) else 1
    }

    private fun saveRound() {
        getSharedPreferences("lion_vs_tiger", MODE_PRIVATE).edit()
            .putString("date", today()).putInt("round", round).apply()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fightAnim?.cancel()
        // Ronde belum dimulai -> kembalikan taruhan; sudah bertarung -> bayar hasilnya
        if (phase == "BET") {
            val total = bets.values.sum()
            if (total > 0) adjustCoins(total)
        } else {
            settle()
        }
        coinListener?.remove()
        super.onDestroy()
    }

    private fun fmt(value: Int): String =
        String.format(Locale.US, "%,d", value).replace(',', '.')
}
