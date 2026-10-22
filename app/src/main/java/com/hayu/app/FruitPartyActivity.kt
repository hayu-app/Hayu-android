package com.hayu.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fruit Party — ronde GLOBAL berdasarkan jam.
 *
 * Grid 3×3: 8 buah + tengah = timer countdown.
 * Satu buah terpilih per ronde (deterministik dari nomor ronde).
 * Pemain memasang taruhan pada salah satu buah sebelum timer habis.
 *
 * Mode Normal : taruhan 1K / 10K / 100K / 200K / 500K
 * Mode Premium: taruhan 10K / 50K / 100K / 500K / 1M  (multiplier ×1.5)
 *
 * Firestore:
 *   fp_rounds/{roundId}/players/{uid}  → uid, name, photo, bet, fruit, net, expireAt
 *   fp_daily/{YYYYMMDD}/players/{uid}  → uid, name, photo, profit
 */
class FruitPartyActivity : AppCompatActivity() {

    // ── Data ──────────────────────────────────────────────────────────────────

    private data class Fruit(
        val key: String,
        val emoji: String,
        val mult: Int,          // multiplier
        val weight: Int,        // bobot untuk hasil deterministik
        val timesLabel: String  // label "X kali" di bawah tiap slot
    )

    private val fruitsNormal = listOf(
        Fruit("BANANA",     "🍌",  5,  150, "5 kali"),
        Fruit("APPLE",      "🍎",  5,  150, "5 kali"),
        Fruit("STRAWBERRY", "🍓",  5,  150, "5 kali"),
        Fruit("AVOCADO",    "🥑", 45,   25, "45 kali"),
        Fruit("WATERMELON", "🍉",  5,  150, "5 kali"),
        Fruit("DATES",      "🌰", 25,   55, "25 kali"),
        Fruit("LEMON",      "🍋", 15,   80, "15 kali"),
        Fruit("GRAPE",      "🍇", 10,  100, "10 kali")
    )

    private val fruitsPremium = listOf(
        Fruit("PEAR",         "🍐",  5,  150, "5 kali"),
        Fruit("STRAWBERRY2",  "🍓",  5,  150, "5 kali"),
        Fruit("KIWI",         "🥝",  5,  150, "5 kali"),
        Fruit("PEACH",        "🍑", 66,   18, "66 kali"),
        Fruit("POMEGRANATE",  "🍈",  5,  150, "5 kali"),
        Fruit("BLUEBERRY",    "🫐", 25,   55, "25 kali"),
        Fruit("CHERRY",       "🍒", 15,   80, "15 kali"),
        Fruit("GOLDENMELON",  "🍈", 10,  100, "10 kali")
    )

    // mode aktif
    private var isPremium = false
    private val fruits get() = if (isPremium) fruitsPremium else fruitsNormal
    private val totalWeight get() = fruits.sumOf { it.weight }

    // taruhan
    private val amountsNormal  = listOf(1_000, 10_000, 100_000, 200_000, 500_000)
    private val labelNormal    = listOf("1K", "10K", "100K", "200K", "500K")
    private val amountsPremium = listOf(10_000, 50_000, 100_000, 500_000, 1_000_000)
    private val labelPremium   = listOf("10K", "50K", "100K", "500K", "1M")
    private val amounts  get() = if (isPremium) amountsPremium else amountsNormal
    private val amLabels get() = if (isPremium) labelPremium  else labelNormal
    private val premBonus = 1.5f   // Premium ×1.5 multiplier

    // warna tema
    private val purple      = Color.rgb(108, 46, 182)
    private val purpleDark  = Color.rgb(68, 22, 130)
    private val gold        = Color.rgb(255, 195, 0)
    private val goldDark    = Color.rgb(200, 140, 10)
    private val white       = Color.WHITE
    private val gray        = Color.rgb(180, 180, 200)

    // timing ronde (detik)
    private val betSec    = 30L
    private val spinSec   = 3L
    private val resultSec = 5L
    private val cycleSec  = betSec + spinSec + resultSec
    private val cycleMs   = cycleSec * 1000L
    private val tz        = TimeZone.getTimeZone("Asia/Jakarta")

    // Firebase
    private val db  by lazy { FirebaseFirestore.getInstance() }
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private var coinListener: ListenerRegistration? = null
    private var boardListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())

    // state
    private var clockOffset  = 0L
    private var balance      = 0
    private var profit       = 0
    private var myName       = "Player"
    private var myPhoto      = ""
    private var selectedAmt  = 1_000
    private var phase        = ""     // "BET" | "SPIN" | "RESULT"
    private var curRid       = -1L
    private var betsRid      = -1L
    private var settled      = true
    private var betKey       = ""     // kunci buah yang dibet ronde ini
    private var betAmt       = 0

    // UI refs
    private lateinit var tvRound:     TextView
    private lateinit var tvMode:      TextView
    private lateinit var tvTimer:     TextView
    private lateinit var chipRow:     LinearLayout
    private lateinit var tvBalance:   TextView
    private lateinit var tvProfit:    TextView
    private lateinit var tvMyBet:     TextView
    private lateinit var resultRow:   LinearLayout
    private lateinit var ivAvatar:    ImageView
    private lateinit var tvName:      TextView
    private lateinit var btnNormal:   TextView
    private lateinit var btnPremium:  TextView
    private val slotViews     = ArrayList<FrameLayout>()
    private val slotLabels    = ArrayList<TextView>()      // "X kali"
    private val slotBetTexts  = ArrayList<TextView>()      // "You: …"
    private var highlightAnim: ValueAnimator? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = purpleDark
        val me = uid ?: run { finish(); return }

        val scroll = ScrollView(this)
        val root   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(purpleDark)
            setPadding(dp(10), dp(8), dp(10), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        buildHeader(root)
        buildModeSwitch(root)
        buildBoard(root)
        buildChipRow(root)
        buildInfoRow(root)
        buildResultRow(root)
        buildBottomBar(root)

        profit = loadProfit()
        updateAll()
        listenCoins(me)
        listenLeaderboard()
        syncClock { handler.post(tick) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        highlightAnim?.cancel()
        // kembalikan taruhan jika ronde belum jalan
        if (phase == "BET" && betKey.isNotEmpty() && betAmt > 0 && !settled) {
            settled = true
            adjustCoins(betAmt)
        }
        coinListener?.remove()
        boardListener?.remove()
        super.onDestroy()
    }

    // ── UI Builders ───────────────────────────────────────────────────────────

    private fun buildHeader(root: LinearLayout) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        // tombol back
        val back = tv("‹", 26f, gold).apply {
            gravity = Gravity.CENTER
            background = roundBg(purpleDark, gold, 22, 2)
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))

        // judul round
        tvRound = tv("Ronde … Hari Ini", 15f, gold).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(tvRound, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })

        // tombol trofeo / leaderboard (ikon)
        val trophy = tv("🏆", 22f, gold).apply {
            gravity = Gravity.CENTER
            background = roundBg(purpleDark, gold, 22, 2)
            setOnClickListener { showLeaderboard() }
        }
        row.addView(trophy, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(6) })

        // tombol ?
        val help = tv("?", 20f, gold).apply {
            gravity = Gravity.CENTER
            background = roundBg(purpleDark, gold, 22, 2)
            setOnClickListener { showRules() }
        }
        row.addView(help, LinearLayout.LayoutParams(dp(40), dp(40)))

        root.addView(row, lp(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun buildModeSwitch(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundBg(Color.argb(80, 255, 255, 255), Color.TRANSPARENT, 30, 0)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        btnNormal = tv("Normal", 15f, white).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(28), dp(8), dp(28), dp(8))
            setOnClickListener { if (isPremium) switchMode(false) }
        }
        btnPremium = tv("Premium", 15f, gray).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(28), dp(8), dp(28), dp(8))
            setOnClickListener { if (!isPremium) switchMode(true) }
        }

        row.addView(btnNormal, LinearLayout.LayoutParams(-2, -2))
        row.addView(btnPremium, LinearLayout.LayoutParams(-2, -2))
        root.addView(row, lp(-1, -2).apply { bottomMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL })
        updateModeSwitch()
    }

    private fun buildBoard(root: LinearLayout) {
        // papan utama (background bergradient ungu)
        val board = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBg(Color.rgb(130, 55, 210), Color.rgb(80, 20, 140), 20)
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }

        // Judul game di atas board
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val titleBg = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundBg(Color.rgb(80, 20, 140), gold, 16, 2)
            setPadding(dp(24), dp(6), dp(24), dp(6))
        }
        titleBg.addView(tv("🍉 FOOD PARTY", 18f, gold).apply { typeface = Typeface.DEFAULT_BOLD })
        titleRow.addView(titleBg)
        board.addView(titleRow, lp(-1, -2).apply { bottomMargin = dp(4) })

        // sub-judul ronde + timer
        val subRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        tvTimer = tv("Ronde … Hari Ini", 13f, gold).apply {
            background = roundBg(Color.argb(100, 255, 200, 0), Color.TRANSPARENT, 20, 0)
            setPadding(dp(14), dp(4), dp(14), dp(4))
            gravity = Gravity.CENTER
        }
        subRow.addView(tvTimer)
        board.addView(subRow, lp(-1, -2).apply { bottomMargin = dp(10) })

        // grid 3×3
        slotViews.clear(); slotLabels.clear(); slotBetTexts.clear()
        for (row3 in 0 until 3) {
            val rowLl = LinearLayout(this).apply { gravity = Gravity.CENTER }
            for (col in 0 until 3) {
                val idx = row3 * 3 + col
                if (idx == 4) {
                    // tengah = display counter / timer
                    tvMode = tv("36", 32f, gold).apply {
                        gravity = Gravity.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        background = roundBg(Color.rgb(50, 10, 90), gold, 12, 3)
                    }
                    rowLl.addView(tvMode, LinearLayout.LayoutParams(dp(90), dp(90)).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    })
                } else {
                    val fi = if (idx < 4) idx else idx - 1
                    val slot = buildSlot(fi)
                    slotViews.add(slot)
                    rowLl.addView(slot, LinearLayout.LayoutParams(dp(90), dp(90)).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    })
                }
            }
            board.addView(rowLl, lp(-1, -2))
        }
        root.addView(board, lp(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun buildSlot(fi: Int): FrameLayout {
        val fruit = fruits.getOrNull(fi) ?: fruitsNormal[fi]
        val slot = FrameLayout(this).apply {
            background = roundBg(Color.rgb(100, 40, 170), Color.rgb(150, 80, 230), 14, 2)
            setOnClickListener { placeBet(fi) }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        inner.addView(tv(fruit.emoji, 28f).apply { gravity = Gravity.CENTER })

        val lblTimes = tv(fruit.timesLabel, 10f, gold).apply { gravity = Gravity.CENTER }
        slotLabels.add(lblTimes)
        inner.addView(lblTimes)

        val lblBet = tv("", 9f, Color.rgb(200, 200, 255)).apply { gravity = Gravity.CENTER }
        slotBetTexts.add(lblBet)
        inner.addView(lblBet)

        slot.addView(inner, FrameLayout.LayoutParams(-1, -1))
        return slot
    }

    private fun buildChipRow(root: LinearLayout) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        chipRow = LinearLayout(this)
        scroll.addView(chipRow)
        rebuildChips()
        root.addView(scroll, lp(-1, dp(54)).apply { bottomMargin = dp(8) })
    }

    private fun rebuildChips() {
        chipRow.removeAllViews()
        amounts.forEachIndexed { i, amt ->
            val sel = amt == selectedAmt
            val chip = tv(amLabels[i], 14f, if (sel) Color.rgb(30, 10, 60) else white).apply {
                gravity = Gravity.CENTER
                background = roundBg(
                    if (sel) gold else Color.rgb(120, 60, 190),
                    if (sel) goldDark else Color.rgb(160, 90, 230),
                    22, 2
                )
                typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setOnClickListener {
                    selectedAmt = amt
                    rebuildChips()
                }
            }
            chipRow.addView(chip, LinearLayout.LayoutParams(dp(72), dp(44)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
    }

    private fun buildInfoRow(root: LinearLayout) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        tvMyBet = tv("Taruhanmu: -", 13f, white).apply {
            background = roundBg(Color.rgb(80, 30, 140), gold, 14, 1)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val histBtn = tv("Riwayat ›", 13f, gold).apply {
            background = roundBg(Color.rgb(80, 30, 140), gold, 14, 1)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { showHistory() }
        }

        row.addView(tvMyBet, LinearLayout.LayoutParams(0, -2, 1.4f).apply { rightMargin = dp(8) })
        row.addView(histBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row, lp(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun buildResultRow(root: LinearLayout) {
        val box = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(Color.argb(100, 255, 120, 180), Color.TRANSPARENT, 14, 0)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        box.addView(tv("Hasil:", 13f, white).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(56), -2))
        resultRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        box.addView(resultRow, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(box, lp(-1, dp(52)).apply { bottomMargin = dp(8) })
        renderResults(0L, false)
    }

    private fun buildBottomBar(root: LinearLayout) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        // profil
        val profCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(Color.rgb(80, 30, 140), Color.TRANSPARENT, 14, 0)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        ivAvatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundBg(gold, goldDark, 22, 2)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        profCard.addView(ivAvatar, LinearLayout.LayoutParams(dp(40), dp(40)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvName = tv("Player", 12f, white).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tvBalance = tv("🪙 0", 12f, gold).apply {
            background = roundBg(Color.argb(80, 255, 255, 255), Color.TRANSPARENT, 10, 0)
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        col.addView(tvName, lp(-1, -2))
        col.addView(tvBalance, lp(-2, -2).apply { topMargin = dp(2) })
        profCard.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        row.addView(profCard, LinearLayout.LayoutParams(0, dp(62), 1.2f).apply { rightMargin = dp(6) })

        // profit hari ini
        val revCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundBg(Color.rgb(80, 30, 140), Color.TRANSPARENT, 14, 0)
        }
        revCard.addView(tv("Profit Hari Ini", 11f, gray), lp(-2, -2))
        tvProfit = tv("0", 14f, gold).apply {
            background = roundBg(Color.argb(80, 255, 255, 255), Color.TRANSPARENT, 10, 0)
            setPadding(dp(10), dp(2), dp(10), dp(2))
            gravity = Gravity.CENTER
        }
        revCard.addView(tvProfit, lp(-2, -2).apply { topMargin = dp(4) })
        row.addView(revCard, LinearLayout.LayoutParams(0, dp(62), 1f))

        root.addView(row, lp(-1, -2).apply { topMargin = dp(4) })
    }

    // ── Mode switch ───────────────────────────────────────────────────────────

    private fun switchMode(premium: Boolean) {
        if (isPremium == premium) return
        isPremium = premium
        selectedAmt = amounts.first()
        updateModeSwitch()
        rebuildChips()
        refreshSlotContent()
        clearMyBet()
    }

    private fun updateModeSwitch() {
        btnNormal.setTextColor(if (!isPremium) white else gray)
        btnNormal.background = if (!isPremium)
            roundBg(Color.argb(200, 255, 255, 255), Color.TRANSPARENT, 26, 0)
        else null
        btnPremium.setTextColor(if (isPremium) white else gray)
        btnPremium.background = if (isPremium)
            roundBg(gold, goldDark, 26, 0)
        else null
    }

    private fun refreshSlotContent() {
        // rebuild slot emoji + label setelah mode berganti
        // slotViews terisi urutan: 0-3 = atas-kiri sampai atas-kanan skip tengah, lanjut bawah
        fruits.forEachIndexed { fi, fruit ->
            val sv = slotViews.getOrNull(fi) ?: return@forEachIndexed
            // update emoji (child 0 dari inner LinearLayout)
            val inner = sv.getChildAt(0) as? LinearLayout ?: return@forEachIndexed
            (inner.getChildAt(0) as? TextView)?.text = fruit.emoji
            slotLabels.getOrNull(fi)?.text = fruit.timesLabel
            slotBetTexts.getOrNull(fi)?.text = ""
            sv.setOnClickListener { placeBet(fi) }
        }
    }

    // ── Game tick ─────────────────────────────────────────────────────────────

    private val tick = object : Runnable {
        override fun run() {
            val now  = System.currentTimeMillis() + clockOffset
            val rid  = now / cycleMs
            val pos  = now % cycleMs
            val newPhase = when {
                pos < betSec * 1000    -> "BET"
                pos < (betSec + spinSec) * 1000 -> "SPIN"
                else                   -> "RESULT"
            }
            val secLeft = when (newPhase) {
                "BET"    -> (betSec * 1000 - pos) / 1000
                "SPIN"   -> (betSec + spinSec) * 1000 / 1000 - pos / 1000
                else     -> (cycleSec * 1000 - pos) / 1000
            }

            if (rid != curRid || newPhase != phase) onPhaseChange(rid, newPhase, secLeft)
            else updateTimer(newPhase, secLeft)

            handler.postDelayed(this, 250)
        }
    }

    private fun onPhaseChange(rid: Long, newPhase: String, secLeft: Long) {
        // settle ronde lama
        if (phase == "RESULT" && curRid != -1L && curRid == betsRid) settleRound(curRid)

        if (rid != curRid) {
            curRid = rid
            // ronde baru → clear taruhan
            if (phase != "") clearMyBet()
        }
        phase = newPhase
        updateTimer(newPhase, secLeft)
        updateRoundLabel(rid)

        if (newPhase == "SPIN") startSpinAnim(rid)
        if (newPhase == "RESULT") {
            highlightAnim?.cancel()
            highlightWinner(rid)
            renderResults(rid, true)
        }
        if (newPhase == "BET") {
            slotViews.forEach { it.background = slotNormalBg() }
            renderResults(curRid, false)
        }
    }

    private fun updateTimer(ph: String, sec: Long) {
        tvMode.text = sec.toString()
        tvMode.setTextColor(when {
            ph == "BET" && sec <= 5 -> Color.RED
            ph == "BET"             -> gold
            ph == "SPIN"            -> Color.WHITE
            else                    -> Color.rgb(100, 230, 100)
        })
    }

    private fun updateRoundLabel(rid: Long) {
        val rNum = roundNumber(rid)
        tvRound.text  = "Ronde $rNum Hari Ini"
        tvTimer.text  = "Ronde $rNum Hari Ini"
    }

    // ── Animasi spin ──────────────────────────────────────────────────────────

    private fun startSpinAnim(rid: Long) {
        highlightAnim?.cancel()
        val winIdx = outcomeIdx(rid)
        var cur = 0
        val total = 20
        var step = 0
        highlightAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (spinSec * 1000)
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                val frac = it.animatedFraction
                val idx  = (frac * total).toInt() % slotViews.size
                if (idx != cur) {
                    slotViews[cur].background = slotNormalBg()
                    cur = idx
                    slotViews[cur].background = slotHighlightBg()
                }
                step++
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    slotViews.forEach { it.background = slotNormalBg() }
                    slotViews.getOrNull(winIdx)?.background = slotWinBg()
                }
            })
            start()
        }
    }

    private fun highlightWinner(rid: Long) {
        val winIdx = outcomeIdx(rid)
        slotViews.forEachIndexed { i, slot ->
            slot.background = if (i == winIdx) slotWinBg() else slotDimBg()
        }
    }

    // ── Slot backgrounds ─────────────────────────────────────────────────────

    private fun slotNormalBg()    = roundBg(Color.rgb(100, 40, 170), Color.rgb(150, 80, 230), 14, 2)
    private fun slotHighlightBg() = roundBg(Color.rgb(200, 180, 50), gold, 14, 3)
    private fun slotWinBg()       = roundBg(Color.rgb(50, 160, 50), Color.rgb(100, 220, 80), 14, 3)
    private fun slotDimBg()       = roundBg(Color.rgb(60, 20, 100), Color.rgb(80, 35, 130), 14, 2)

    // ── Taruhan ───────────────────────────────────────────────────────────────

    private fun placeBet(fi: Int) {
        if (phase != "BET") {
            toast("Tunggu ronde berikutnya"); return
        }
        if (betKey.isNotEmpty()) {
            toast("Sudah memasang taruhan ronde ini"); return
        }
        if (balance < selectedAmt) {
            toast("Koin tidak cukup"); return
        }
        val fruit = fruits.getOrNull(fi) ?: return
        val rid = curRid
        adjustCoins(-selectedAmt, onOk = {
            if (!isDestroyed && curRid == rid && phase == "BET") {
                betKey   = fruit.key
                betAmt   = selectedAmt
                betsRid  = rid
                settled  = false
                saveBetToFirestore(rid, fruit.key, selectedAmt)
                slotBetTexts.getOrNull(fi)?.text = "You: ${compact(selectedAmt.toLong())}"
                updateAll()
                toast("Taruhan ${fmt(selectedAmt)} pada ${fruit.emoji}!")
            } else {
                adjustCoins(selectedAmt) // phase berubah sebelum ack → kembalikan
            }
        }, onFail = { toast(it) })
    }

    private fun saveBetToFirestore(rid: Long, key: String, amt: Int) {
        val me = uid ?: return
        val ref = db.collection("fp_rounds").document(rid.toString())
            .collection("players").document(me)
        ref.set(mapOf(
            "uid"      to me,
            "name"     to myName,
            "photo"    to myPhoto,
            "bet"      to amt,
            "fruit"    to key,
            "net"      to 0,
            "expireAt" to System.currentTimeMillis() + 7 * 24 * 3600 * 1000L
        ))
    }

    // ── Settle ronde ──────────────────────────────────────────────────────────

    private fun settleRound(rid: Long) {
        if (settled || betKey.isEmpty() || betAmt == 0) { settled = true; return }
        settled = true

        val winIdx   = outcomeIdx(rid)
        val winFruit = fruits[winIdx]
        val mult     = if (isPremium) (winFruit.mult * premBonus).toInt() else winFruit.mult
        val payout   = if (betKey == winFruit.key) betAmt * mult else 0
        val net      = payout - betAmt

        if (payout > 0) adjustCoins(payout)

        profit += net
        saveProfit(profit)
        saveHistory(rid, winFruit, betAmt, net)
        updateDailyLeaderboard(net)

        val resultMsg = if (payout > 0)
            "${winFruit.emoji} menang! +${fmt(net.toLong())}"
        else "Hasil: ${winFruit.emoji}. Kamu: ${fruits.firstOrNull { it.key == betKey }?.emoji ?: "?"} (-${fmt(betAmt.toLong())})"
        toast(resultMsg)
        clearMyBet()
        updateAll()
    }

    private fun clearMyBet() {
        betKey  = ""
        betAmt  = 0
        slotBetTexts.forEach { it.text = "" }
        tvMyBet.text = "Taruhanmu: -"
    }

    // ── Deterministik ─────────────────────────────────────────────────────────

    private fun outcomeIdx(rid: Long): Int {
        val r  = Random(rid * 6364136223846793005L + 1442695040888963407L)
        var x  = r.nextInt(totalWeight)
        for (i in fruits.indices) {
            x -= fruits[i].weight
            if (x < 0) return i
        }
        return 0
    }

    private fun roundNumber(rid: Long): Int {
        val startMs = rid * cycleMs
        val day = Calendar.getInstance(tz).apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return ((startMs - day.timeInMillis) / cycleMs).toInt() + 1
    }

    // ── Results bar ───────────────────────────────────────────────────────────

    private fun renderResults(rid: Long, includeCurrent: Boolean) {
        resultRow.removeAllViews()
        for (i in 0 until 7) {
            val off  = if (includeCurrent) i else i + 1
            val text = if (rid == 0L && !includeCurrent) "?" else fruits[outcomeIdx(rid - off)].emoji
            val badge = TextView(this).apply {
                this.text = text
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = roundBg(Color.rgb(80, 30, 140), gold, 20, 1)
            }
            resultRow.addView(badge, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    private fun listenLeaderboard() {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
            .format(Date())
        boardListener = db.collection("fp_daily").document(day)
            .collection("players")
            .orderBy("profit", Query.Direction.DESCENDING)
            .limit(3)
            .addSnapshotListener { snap, _ -> /* silent refresh */ }
    }

    private fun showLeaderboard() {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
            .format(Date())
        db.collection("fp_daily").document(day)
            .collection("players")
            .orderBy("profit", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { snap ->
                if (isDestroyed) return@addOnSuccessListener
                val sb = StringBuilder("🏆 Top pemain hari ini\n\n")
                snap.documents.forEachIndexed { i, doc ->
                    val nm = doc.getString("name") ?: "?"
                    val pr = doc.getLong("profit") ?: 0L
                    val medal = listOf("🥇", "🥈", "🥉").getOrElse(i) { "▸" }
                    sb.append("$medal $nm  —  ${fmt(pr)}\n")
                }
                AlertDialog.Builder(this)
                    .setTitle("Leaderboard Fruit Party")
                    .setMessage(if (snap.isEmpty) "Belum ada pemain hari ini" else sb.toString())
                    .setPositiveButton("Tutup", null)
                    .show()
            }
    }

    private fun updateDailyLeaderboard(net: Int) {
        val me = uid ?: return
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
            .format(Date())
        val ref = db.collection("fp_daily").document(day)
            .collection("players").document(me)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val prev = snap.getLong("profit") ?: 0L
            tx.set(ref, mapOf(
                "uid"    to me,
                "name"   to myName,
                "photo"  to myPhoto,
                "profit" to prev + net
            ))
            null
        }
    }

    // ── Koin ──────────────────────────────────────────────────────────────────

    private fun listenCoins(me: String) {
        coinListener = db.collection("users").document(me)
            .addSnapshotListener { snap, _ ->
                if (snap == null || isDestroyed) return@addSnapshotListener
                balance = (snap.getLong("coins") ?: 0L).toInt()
                myName  = snap.getString("nickname") ?: myName
                val ph  = snap.getString("photoUrl") ?: ""
                myPhoto = ph
                if (ph.isNotEmpty()) ivAvatar.load(ph)
                updateAll()
            }
    }

    private fun adjustCoins(delta: Int, onOk: () -> Unit = {}, onFail: (String) -> Unit = {}) {
        val me  = uid ?: return onFail("Belum login")
        val ref = db.collection("users").document(me)
        db.runTransaction { tx ->
            val coins = tx.get(ref).getLong("coins") ?: 0L
            if (coins + delta < 0) throw IllegalStateException("Koin tidak cukup")
            tx.update(ref, "coins", coins + delta)
            null
        }.addOnSuccessListener { onOk() }
            .addOnFailureListener { onFail(it.message ?: "Gagal") }
    }

    // ── Persist profit & history ──────────────────────────────────────────────

    private fun prefs() = getSharedPreferences("fp_prefs", Context.MODE_PRIVATE)

    private fun loadProfit(): Int {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
            .format(Date())
        val saved = prefs().getString("profitDay", "")
        return if (saved == today) prefs().getInt("profit", 0) else 0
    }

    private fun saveProfit(p: Int) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
            .format(Date())
        prefs().edit().putInt("profit", p).putString("profitDay", today).apply()
    }

    private fun saveHistory(rid: Long, win: Fruit, bet: Int, net: Int) {
        val rNum  = roundNumber(rid)
        val time  = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }.format(Date())
        val line  = "Ronde $rNum ($time) • ${win.emoji} ${win.key} • Taruhan ${fmt(bet.toLong())} • " +
                (if (net >= 0) "+${fmt(net.toLong())}" else "-${fmt(-net.toLong())}")
        val old   = prefs().getString("hist", "") ?: ""
        val lines = (listOf(line) + old.lines()).take(30).joinToString("\n")
        prefs().edit().putString("hist", lines).apply()
    }

    private fun showHistory() {
        val h = prefs().getString("hist", "") ?: ""
        AlertDialog.Builder(this).setTitle("Riwayat Saya")
            .setMessage(if (h.isBlank()) "Belum ada riwayat" else h)
            .setPositiveButton("Tutup", null).show()
    }

    // ── Info / rules ─────────────────────────────────────────────────────────

    private fun showRules() {
        val mode = if (isPremium) "Premium (×1.5 bonus)" else "Normal"
        val sb = StringBuilder("Mode: $mode\n\n")
        fruits.forEach { sb.append("${it.emoji}  ×${it.mult}  — bobot: ${it.weight}\n") }
        sb.append("\nPilih satu buah sebelum timer habis. Hasil ronde sama untuk semua pemain.")
        sb.append("\nPremium: multiplier ×1.5, minimal taruhan 10K.")
        AlertDialog.Builder(this).setTitle("Cara Main Fruit Party")
            .setMessage(sb.toString()).setPositiveButton("OK", null).show()
    }

    // ── Update UI ─────────────────────────────────────────────────────────────

    private fun updateAll() {
        tvBalance.text  = "🪙 ${fmt(balance.toLong())}"
        tvProfit.text   = fmt(profit.toLong())
        tvName.text     = myName
        if (betKey.isNotEmpty()) {
            val f = fruits.firstOrNull { it.key == betKey }
            tvMyBet.text = "Taruhanmu: ${f?.emoji ?: ""} ${fmt(betAmt.toLong())}"
        } else {
            tvMyBet.text = "Taruhanmu: -"
        }
    }

    // ── Sync clock ────────────────────────────────────────────────────────────

    private fun syncClock(done: () -> Unit) {
        Thread {
            try {
                val t0 = System.currentTimeMillis()
                val c  = URL("https://www.google.com").openConnection() as HttpURLConnection
                c.requestMethod  = "HEAD"
                c.connectTimeout = 4000
                c.readTimeout    = 4000
                c.connect()
                val t1       = System.currentTimeMillis()
                val serverMs = c.date
                c.disconnect()
                if (serverMs > 0L) clockOffset = serverMs - (t0 + t1) / 2
            } catch (e: Exception) { /* fallback ke jam HP */ }
            finally { runOnUiThread { if (!isDestroyed) done() } }
        }.start()
    }

    // ── Drawable helpers ─────────────────────────────────────────────────────

    private fun roundBg(fill: Int, stroke: Int, radius: Int, strokeW: Int): GradientDrawable {
        val gd = GradientDrawable()
        gd.setColor(fill)
        gd.cornerRadius = dp(radius).toFloat()
        if (strokeW > 0) gd.setStroke(dp(strokeW), stroke)
        return gd
    }

    private fun gradientBg(top: Int, bottom: Int, radius: Int): GradientDrawable {
        val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        gd.cornerRadius = dp(radius).toFloat()
        return gd
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float, color: Int = white) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun fmt(v: Int)  = fmt(v.toLong())
    private fun fmt(v: Long) = String.format(Locale.US, "%,d", v).replace(',', '.')
    private fun compact(v: Long) = when {
        v >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", v / 1_000_000_000.0)
        v >= 10_000_000L    -> String.format(Locale.US, "%.1fM", v / 1_000_000.0)
        else                -> fmt(v)
    }
}
