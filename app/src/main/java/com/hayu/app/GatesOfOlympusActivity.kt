package com.hayu.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gates of Olympus — slot 6×5.
 * Simbol digambar pakai vector drawable (tanpa emoji).
 * Mekanik: tumble, multiplier bertumpuk, scatter → free spins, buy free spins, turbo.
 */
class GatesOfOlympusActivity : AppCompatActivity() {

    // ── Simbol ────────────────────────────────────────────────────────────────

    data class Sym(
        val key: String,
        val drawableRes: Int,
        val glowColor: Int,
        val payTable: Map<Int, Float>   // minCount → multiplier taruhan
    )

    private val symList by lazy { listOf(
        Sym("GOLD",    R.drawable.sym_diamond_gold,   Color.rgb(255, 200, 0),  mapOf(8 to 0.5f, 10 to 1f, 12 to 2f)),
        Sym("PURPLE",  R.drawable.sym_diamond_purple, Color.rgb(180, 60, 220), mapOf(8 to 0.5f, 10 to 1f, 12 to 2f)),
        Sym("GREEN",   R.drawable.sym_diamond_green,  Color.rgb(50, 200, 80),  mapOf(8 to 0.5f, 10 to 1f, 12 to 2f)),
        Sym("BLUE",    R.drawable.sym_diamond_blue,   Color.rgb(30, 130, 255), mapOf(8 to 0.5f, 10 to 1f, 12 to 2f)),
        Sym("RED",     R.drawable.sym_diamond_red,    Color.rgb(220, 40, 40),  mapOf(8 to 1f, 10 to 2f, 12 to 5f)),
        Sym("TEAL",    R.drawable.sym_diamond_teal,   Color.rgb(0, 200, 200),  mapOf(8 to 1f, 10 to 2f, 12 to 5f)),
        Sym("RING",    R.drawable.sym_ring,           Color.rgb(200, 140, 255),mapOf(8 to 5f, 10 to 10f, 12 to 25f)),
        Sym("CROWN",   R.drawable.sym_crown,          Color.rgb(255, 220, 0),  mapOf(8 to 10f, 10 to 25f, 12 to 50f)),
        Sym("SCATTER", R.drawable.sym_scatter,        Color.rgb(255, 200, 50), mapOf(4 to 2f, 5 to 5f, 6 to 10f))
    ) }

    private val SCATTER = "SCATTER"
    private val COLS = 6
    private val ROWS = 5
    private val CELL = 48   // dp per cell

    // taruhan
    private val betAmounts = listOf(1_000, 5_000, 10_000, 50_000, 100_000, 500_000)
    private val betLabels  = listOf("1K", "5K", "10K", "50K", "100K", "500K")

    // warna
    private val bgDark   = Color.rgb(18, 4, 45)
    private val bgMid    = Color.rgb(45, 12, 90)
    private val bgCell   = Color.rgb(38, 14, 75)
    private val gold     = Color.rgb(255, 195, 0)
    private val goldDark = Color.rgb(170, 120, 0)
    private val white    = Color.WHITE
    private val grayTxt  = Color.rgb(160, 160, 190)

    // Firebase
    private val db  by lazy { FirebaseFirestore.getInstance() }
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private var coinListener: ListenerRegistration? = null

    // State
    private var balance    = 0L
    private var currentBet = 1_000
    private var myName     = "Player"
    private var myPhoto    = ""
    private var spinning   = false
    private var turbo      = false
    private var freeSpins  = 0
    private var totalMulti = 0
    private var roundWin   = 0
    private val handler    = Handler(Looper.getMainLooper())
    private val rng        = Random()

    // Grid: grid[col][row] = key atau ""
    private val grid      = Array(COLS) { Array(ROWS) { "" } }
    private val cellViews = Array(COLS) { arrayOfNulls<FrameLayout>(ROWS) }

    // UI
    private lateinit var tvBalance : TextView
    private lateinit var tvBet     : TextView
    private lateinit var tvWin     : TextView
    private lateinit var tvFree    : TextView
    private lateinit var tvMulti   : TextView
    private lateinit var btnSpin   : TextView
    private lateinit var btnTurbo  : TextView
    private lateinit var chipRow   : LinearLayout
    private lateinit var ivAvatar  : ImageView
    private lateinit var tvName    : TextView

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameMusic.attach(this, R.raw.bgm_olympus)
        window.statusBarColor = bgDark
        val me = uid ?: run { finish(); return }

        val sv   = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
            setPadding(dp(8), dp(4), dp(8), dp(24))
        }
        sv.addView(root)
        setContentView(sv)

        buildHeader(root)
        buildTitle(root)
        buildGrid(root)
        buildMultiBar(root)
        buildChipsRow(root)
        buildWinBar(root)
        buildControls(root)

        fillRandom()
        renderGrid()
        listenCoins(me)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        coinListener?.remove()
        super.onDestroy()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildHeader(root: LinearLayout) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        val back = tv("‹", 26f, gold).apply {
            gravity = Gravity.CENTER
            background = circleBg(bgMid, gold, 2)
            setOnClickListener { finish() }
        }
        row.addView(back, LP(dp(40), dp(40)).apply { rightMargin = dp(8) })

        ivAvatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circleBg(gold, goldDark, 2)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        row.addView(ivAvatar, LP(dp(36), dp(36)).apply { rightMargin = dp(6) })

        tvName = tv("Player", 12f, grayTxt).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(tvName, LP(0, -2, 1f))

        tvBalance = tv("0", 14f, gold).apply {
            background = roundBg(bgMid, gold, 16, 1)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity = Gravity.CENTER
        }
        row.addView(tvBalance, LP(-2, -2))

        val help = tv("?", 18f, gold).apply {
            gravity = Gravity.CENTER
            background = circleBg(bgMid, gold, 2)
            setOnClickListener { showPayTable() }
        }
        row.addView(help, LP(dp(36), dp(36)).apply { leftMargin = dp(8) })
        root.addView(row, LP(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun buildTitle(root: LinearLayout) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = gradBg(Color.rgb(110, 65, 5), Color.rgb(35, 15, 0), 14)
            setPadding(0, dp(8), 0, dp(8))
        }
        wrap.addView(tv("GATES of OLYMPUS", 20f, gold).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        wrap.addView(tv("SYMBOLS PAY ANYWHERE ON THE SCREEN", 10f, Color.rgb(255, 215, 100)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0)
        })
        root.addView(wrap, LP(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun buildGrid(root: LinearLayout) {
        // border emas
        val border = FrameLayout(this).apply {
            background = gradBg(Color.rgb(180, 130, 20), Color.rgb(90, 60, 5), 16)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val inner = FrameLayout(this).apply {
            background = roundBg(Color.rgb(25, 8, 55), Color.TRANSPARENT, 12, 0)
        }

        val cellSz = dp(CELL)
        val gap    = dp(3)
        val totalW = COLS * cellSz + (COLS - 1) * gap
        val totalH = ROWS * cellSz + (ROWS - 1) * gap

        for (c in 0 until COLS) {
            for (r in 0 until ROWS) {
                val frame = FrameLayout(this).apply {
                    background = roundBg(bgCell, Color.rgb(70, 30, 120), 8, 1)
                }
                // ImageView simbol
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                }
                frame.addView(iv, FrameLayout.LayoutParams(-1, -1))
                cellViews[c][r] = frame
                // tag imageview di frame
                frame.tag = iv

                val fp = FrameLayout.LayoutParams(cellSz, cellSz).apply {
                    leftMargin = c * (cellSz + gap)
                    topMargin  = r * (cellSz + gap)
                }
                inner.addView(frame, fp)
            }
        }

        border.addView(inner, LP(totalW, totalH))
        root.addView(border, LP(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8)
        })
    }

    private fun buildMultiBar(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundBg(bgMid, gold, 12, 1)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        tvMulti = tv("Multiplier  ×0", 14f, gold).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        tvFree = tv("", 14f, Color.rgb(100, 220, 255)).apply { gravity = Gravity.CENTER }
        row.addView(tvMulti, LP(0, -2, 1f))
        row.addView(tvFree,  LP(0, -2, 1f))
        root.addView(row, LP(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun buildChipsRow(root: LinearLayout) {
        val label = tv("BET:", 12f, grayTxt).apply { gravity = Gravity.CENTER_VERTICAL }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        chipRow = LinearLayout(this)
        rebuildChips()
        hs.addView(chipRow)

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label, LP(-2, -2).apply { rightMargin = dp(8) })
        row.addView(hs, LP(0, dp(46), 1f))
        root.addView(row, LP(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun rebuildChips() {
        chipRow.removeAllViews()
        betAmounts.forEachIndexed { i, amt ->
            val sel = amt == currentBet
            val chip = tv(betLabels[i], 13f, if (sel) bgDark else white).apply {
                gravity = Gravity.CENTER
                typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = roundBg(if (sel) gold else bgMid, if (sel) goldDark else Color.rgb(100, 50, 180), 20, 2)
                setOnClickListener { if (!spinning) { currentBet = amt; rebuildChips(); tvBet.text = "PLAY  ${fmt(amt)}" } }
            }
            chipRow.addView(chip, LP(dp(62), dp(38)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun buildWinBar(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundBg(bgMid, Color.TRANSPARENT, 10, 0)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        tvBet = tv("PLAY  ${fmt(currentBet)}", 13f, grayTxt).apply { gravity = Gravity.CENTER }
        tvWin = tv("WIN  0", 16f, gold).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        row.addView(tvBet, LP(0, -2, 1f))
        row.addView(tvWin, LP(0, -2, 1f))
        root.addView(row, LP(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun buildControls(root: LinearLayout) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }

        // Buy Free Spins
        val buyFs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundBg(Color.rgb(18, 35, 100), gold, 12, 2)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { buyFreeSpins() }
        }
        buyFs.addView(tv("BUY", 11f, gold).apply { gravity = Gravity.CENTER })
        buyFs.addView(tv("FREE SPINS", 10f, gold).apply { gravity = Gravity.CENTER })
        buyFs.addView(tv(fmt(currentBet * 100), 11f, white).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        })
        row.addView(buyFs, LP(dp(95), dp(66)).apply { rightMargin = dp(10) })

        // Turbo
        btnTurbo = tv("TURBO", 12f, if (turbo) bgDark else gold).apply {
            gravity = Gravity.CENTER
            background = roundBg(if (turbo) gold else bgMid, gold, 28, 2)
            setOnClickListener {
                turbo = !turbo
                btnTurbo.setTextColor(if (turbo) bgDark else gold)
                btnTurbo.background = roundBg(if (turbo) gold else bgMid, gold, 28, 2)
            }
        }
        row.addView(btnTurbo, LP(dp(66), dp(66)).apply { rightMargin = dp(10) })

        // SPIN
        btnSpin = tv("SPIN", 20f, bgDark).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = circleBg(gold, goldDark, 4)
            setOnClickListener { doSpin() }
        }
        row.addView(btnSpin, LP(dp(82), dp(82)).apply { rightMargin = dp(10) })

        // Auto
        val auto = tv("AUTO", 12f, gold).apply {
            gravity = Gravity.CENTER
            background = roundBg(bgMid, gold, 28, 2)
            setOnClickListener { toast("Segera hadir!") }
        }
        row.addView(auto, LP(dp(66), dp(66)))
        root.addView(row, LP(-1, -2))
    }

    // ── Grid rendering ────────────────────────────────────────────────────────

    private fun fillRandom() {
        for (c in 0 until COLS)
            for (r in 0 until ROWS)
                grid[c][r] = randomSym(false)
    }

    private fun renderGrid(hits: Set<Pair<Int,Int>> = emptySet(), dim: Boolean = false) {
        for (c in 0 until COLS) {
            for (r in 0 until ROWS) {
                val frame = cellViews[c][r] ?: continue
                val iv    = frame.tag as? ImageView ?: continue
                val key   = grid[c][r]
                val sym   = symList.firstOrNull { it.key == key }

                if (sym != null) {
                    iv.setImageResource(sym.drawableRes)
                    iv.alpha = if (dim && Pair(c, r) !in hits) 0.3f else 1f
                } else {
                    iv.setImageDrawable(null)
                }

                val isHit = Pair(c, r) in hits
                frame.background = when {
                    isHit -> roundBg(Color.argb(180, 200, 160, 0), Color.rgb(255, 220, 50), 8, 3)
                    dim   -> roundBg(Color.rgb(20, 6, 40), Color.rgb(50, 20, 80), 8, 1)
                    else  -> roundBg(bgCell, Color.rgb(70, 30, 120), 8, 1)
                }
            }
        }
    }

    // ── Spin engine ───────────────────────────────────────────────────────────

    private fun doSpin() {
        if (spinning) return
        val cost = if (freeSpins > 0) 0 else currentBet
        if (cost > 0 && balance < cost) { toast("Koin tidak cukup!"); return }
        spinning  = true
        roundWin  = 0
        totalMulti = 0
        tvWin.text = "WIN  0"
        btnSpin.isEnabled = false
        btnSpin.alpha = 0.5f

        if (cost > 0) deductCoins(cost) { spinAnim() }
        else spinAnim()
    }

    private fun spinAnim() {
        val stepMs = if (turbo) 60L else 130L
        var col = 0
        val r = object : Runnable {
            override fun run() {
                if (col < COLS) {
                    for (row in 0 until ROWS) grid[col][row] = randomSym(false)
                    renderGrid()
                    col++
                    handler.postDelayed(this, stepMs)
                } else {
                    landSymbols()
                }
            }
        }
        handler.post(r)
    }

    private fun landSymbols() {
        for (c in 0 until COLS)
            for (r in 0 until ROWS)
                grid[c][r] = randomSym(true)
        renderGrid()
        handler.postDelayed({ evalWin() }, if (turbo) 150L else 350L)
    }

    // ── Evaluasi & tumble ────────────────────────────────────────────────────

    private fun evalWin() {
        val hits = mutableSetOf<Pair<Int,Int>>()
        var winAmt = 0

        symList.filter { it.key != SCATTER }.forEach { sym ->
            val pos = (0 until COLS).flatMap { c -> (0 until ROWS).map { r -> Pair(c, r) } }
                .filter { grid[it.first][it.second] == sym.key }
            val cnt  = pos.size
            val mult = sym.payTable.entries.filter { cnt >= it.key }.maxByOrNull { it.key }?.value ?: 0f
            if (mult > 0f) { winAmt += (currentBet * mult).toInt(); hits.addAll(pos) }
        }

        // scatter
        val scPos = (0 until COLS).flatMap { c -> (0 until ROWS).map { r -> Pair(c, r) } }
            .filter { grid[it.first][it.second] == SCATTER }
        val scMult = symList.first { it.key == SCATTER }.payTable.entries
            .filter { scPos.size >= it.key }.maxByOrNull { it.key }?.value ?: 0f
        if (scMult > 0f) {
            winAmt += (currentBet * scMult).toInt()
            hits.addAll(scPos)
            if (scPos.size >= 4) triggerFreeSpins(scPos.size)
        }

        if (hits.isNotEmpty()) {
            totalMulti += rng.nextInt(3) + 1
            tvMulti.text = "Multiplier  ×$totalMulti"
            val effective = winAmt * maxOf(1, totalMulti)
            roundWin += effective
            tvWin.text = "WIN  ${fmt(roundWin)}"
            renderGrid(hits, dim = true)
            handler.postDelayed({
                addCoins(effective)
                tumble(hits)
            }, if (turbo) 250L else 600L)
        } else {
            endSpin()
        }
    }

    private fun tumble(hits: Set<Pair<Int,Int>>) {
        for (c in 0 until COLS) {
            hits.filter { it.first == c }.forEach { grid[c][it.second] = "" }
            val keep = (0 until ROWS).map { grid[c][it] }.filter { it.isNotEmpty() }.toMutableList()
            val fill = ROWS - keep.size
            repeat(fill) { keep.add(0, randomSym(true)) }
            for (r in 0 until ROWS) grid[c][r] = keep[r]
        }
        renderGrid()
        handler.postDelayed({ evalWin() }, if (turbo) 200L else 400L)
    }

    private fun endSpin() {
        if (freeSpins > 0) {
            freeSpins--
            tvFree.text = if (freeSpins > 0) "FREE SPINS  $freeSpins" else ""
            if (freeSpins > 0) { handler.postDelayed({ spinAnim() }, if (turbo) 300L else 500L); return }
        }
        spinning = false
        btnSpin.isEnabled = true
        btnSpin.alpha = 1f
        if (roundWin > 0) showWinOverlay(roundWin)
        totalMulti = 0
        tvMulti.text = "Multiplier  ×0"
    }

    // ── Free Spins ────────────────────────────────────────────────────────────

    private fun triggerFreeSpins(cnt: Int) {
        val spins = when { cnt >= 6 -> 15; cnt >= 5 -> 12; else -> 10 }
        freeSpins += spins
        tvFree.text = "FREE SPINS  $freeSpins"
        toast("$spins FREE SPINS!")
    }

    private fun buyFreeSpins() {
        if (spinning) return
        val cost = currentBet * 100
        if (balance < cost) { toast("Butuh ${fmt(cost)} koin"); return }
        AlertDialog.Builder(this)
            .setTitle("Buy Free Spins")
            .setMessage("Beli 10 Free Spins seharga ${fmt(cost)} koin?")
            .setPositiveButton("Beli") { _, _ ->
                deductCoins(cost) { freeSpins += 10; tvFree.text = "FREE SPINS  $freeSpins" }
            }
            .setNegativeButton("Batal", null).show()
    }

    // ── Win overlay ───────────────────────────────────────────────────────────

    private fun showWinOverlay(win: Int) {
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(Color.argb(190, 0, 0, 0))
        overlay.elevation = dp(20).toFloat()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = gradBg(Color.rgb(120, 70, 5), Color.rgb(40, 15, 0), 20)
            setPadding(dp(36), dp(28), dp(36), dp(28))
        }
        card.addView(tv("MENANG!", 22f, gold).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
        card.addView(tv(fmt(win), 38f, white).apply  { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
        card.addView(tv("KOIN", 14f, grayTxt).apply  { gravity = Gravity.CENTER })

        val fp = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
        fp.setMargins(dp(50), 0, dp(50), 0)
        overlay.addView(card, fp)

        val root = window.decorView as FrameLayout
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        card.scaleX = 0.5f; card.scaleY = 0.5f; card.alpha = 0f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "scaleX", 0.5f, 1.08f, 1f),
                ObjectAnimator.ofFloat(card, "scaleY", 0.5f, 1.08f, 1f),
                ObjectAnimator.ofFloat(card, "alpha",  0f, 1f)
            )
            duration = 380; interpolator = DecelerateInterpolator(); start()
        }
        handler.postDelayed({ root.removeView(overlay) }, 2400)
    }

    // ── Pay table dialog ──────────────────────────────────────────────────────

    private fun showPayTable() {
        val sb = StringBuilder("GATES OF OLYMPUS\nSimbol bayar di mana saja.\n\n")
        symList.forEach { sym ->
            sb.append("${sym.key}\n")
            sym.payTable.forEach { (cnt, m) -> sb.append("  $cnt+ → ×$m bet\n") }
            sb.append("\n")
        }
        sb.append("SCATTER 4+ = 10 Free Spins\nTumble: simbol menang dihapus, yang baru jatuh.\nMultiplier bertumpuk tiap tumble!")
        AlertDialog.Builder(this).setTitle("Pay Table").setMessage(sb).setPositiveButton("OK", null).show()
    }

    // ── Coins ─────────────────────────────────────────────────────────────────

    private fun listenCoins(me: String) {
        coinListener = db.collection("users").document(me)
            .addSnapshotListener { snap, _ ->
                if (snap == null || isDestroyed) return@addSnapshotListener
                balance = (snap.getLong("coins") ?: 0L)
                myName  = snap.getString("nickname") ?: myName
                val ph  = snap.getString("photoUrl") ?: ""
                if (ph.isNotEmpty()) ivAvatar.load(ph)
                tvBalance.text = fmt(balance)
                tvName.text    = myName
            }
    }

    private fun deductCoins(amount: Int, onOk: () -> Unit) {
        val me  = uid ?: return
        val ref = db.collection("users").document(me)
        db.runTransaction { tx ->
            val coins = tx.get(ref).getLong("coins") ?: 0L
            if (coins < amount) throw IllegalStateException("Koin tidak cukup")
            tx.update(ref, "coins", coins - amount); null
        }.addOnSuccessListener { onOk() }
         .addOnFailureListener {
             spinning = false; btnSpin.isEnabled = true; btnSpin.alpha = 1f
             toast(it.message ?: "Gagal")
         }
    }

    private fun addCoins(amount: Int) {
        val me  = uid ?: return
        val ref = db.collection("users").document(me)
        db.runTransaction { tx ->
            val coins = tx.get(ref).getLong("coins") ?: 0L
            tx.update(ref, "coins", coins + amount); null
        }
    }

    // ── Random simbol ─────────────────────────────────────────────────────────

    private fun randomSym(allowScatter: Boolean): String {
        val pool = buildList {
            repeat(28) { add("GOLD"); add("PURPLE"); add("GREEN"); add("BLUE") }
            repeat(14) { add("RED"); add("TEAL") }
            repeat(6)  { add("RING") }
            repeat(3)  { add("CROWN") }
            if (allowScatter) repeat(2) { add("SCATTER") }
        }
        return pool[rng.nextInt(pool.size)]
    }

    // ── Drawable helpers ──────────────────────────────────────────────────────

    private fun roundBg(fill: Int, stroke: Int, radius: Int, strokeW: Int): GradientDrawable {
        val gd = GradientDrawable()
        gd.setColor(fill)
        gd.cornerRadius = dp(radius).toFloat()
        if (strokeW > 0) gd.setStroke(dp(strokeW), stroke)
        return gd
    }

    private fun circleBg(fill: Int, stroke: Int, strokeW: Int): GradientDrawable {
        val gd = GradientDrawable(); gd.shape = GradientDrawable.OVAL
        gd.setColor(fill)
        if (strokeW > 0) gd.setStroke(dp(strokeW), stroke)
        return gd
    }

    private fun gradBg(top: Int, bottom: Int, radius: Int): GradientDrawable {
        val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        gd.cornerRadius = dp(radius).toFloat()
        return gd
    }

    private fun LP(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(s: String, sz: Float, clr: Int = white) = TextView(this).apply {
        text = s; textSize = sz; setTextColor(clr); includeFontPadding = false
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun fmt(v: Int)  = fmt(v.toLong())
    private fun fmt(v: Long) = String.format(Locale.US, "%,d", v).replace(',', '.')
}
