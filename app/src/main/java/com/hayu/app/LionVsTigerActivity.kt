package com.hayu.app

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lion vs Tiger — ronde GLOBAL.
 *  - Ronde berjalan terus berdasarkan jam (tidak peduli ada yang main atau tidak).
 *  - Hasil ronde sama untuk semua pemain (deterministik dari nomor ronde).
 *  - Total koin di tiap kartu = gabungan semua pemain (Firestore).
 *  - Leaderboard profit mingguan, otomatis reset tiap Senin 00:00 WIB.
 *
 * Firestore:
 *  lvt_rounds/{roundId}/bets/{uid}   -> uid, name, LION, DRAW, TIGER, expireAt
 *  lvt_weekly/{wYYYYMMDD}/players/{uid} -> uid, name, photo, profit
 */
class LionVsTigerActivity : AppCompatActivity() {

    private val gold = Color.rgb(255, 204, 65)
    private val amounts = listOf(1000, 10000, 100000, 200000, 500000)
    private val amountLabels = listOf("1000", "10K", "100K", "200K", "500K")
    private val sides = listOf("LION", "DRAW", "TIGER")
    private val betMs = 15_000L
    private val fightMs = 1_500L
    private val resultMs = 3_500L
    private val cycleMs = betMs + fightMs + resultMs
    private val multiplier = 3
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    private var coinListener: ListenerRegistration? = null
    private var betsListener: ListenerRegistration? = null
    private var boardListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())

    private var clockOffset = 0L
    private var balance = 0
    private var profit = 0
    private var myName = "Player"
    private var myPhoto = ""
    private var selectedAmount = 1000
    private var phase = ""
    private var curRid = -1L
    private var betsRid = -1L
    private var outcome = "LION"
    private var settled = true
    private var auto = false
    private var weekId = ""
    private var lastWeekCheck = 0L
    private var lastResetText = ""

    private val bets = HashMap<String, Int>()          // taruhan SAYA di ronde betsRid
    private var lastBets: Map<String, Int> = emptyMap()
    private val sideTotal = HashMap<String, Long>()    // total koin per sisi (semua pemain)
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
    private lateinit var boardList: LinearLayout
    private lateinit var resetText: TextView
    private val totalTexts = HashMap<String, TextView>()
    private val mineTexts = HashMap<String, TextView>()
    private val cards = HashMap<String, LinearLayout>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(color: Int, radius: Int, stroke: Int = gold, sw: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(sw), stroke)
        }

    private fun oval(fill: Int = Color.rgb(40, 30, 60)) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(2), gold)
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

    private fun nowMs() = System.currentTimeMillis() + clockOffset

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val me = uid
        if (me == null) {
            Toast.makeText(this, "Silakan login dulu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
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
            setPadding(dp(8), dp(8), dp(8), dp(16))
        }
        scroll.addView(root)
        setContentView(scroll)

        // ---- header ----
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(tv("‹", 34f, gold).apply { setOnClickListener { finish() } },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        roundText = tv("Round … of Today", 18f).apply {
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
        countText = tv("Menyambungkan…", 18f)
        arena.addView(countText, FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(30) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = betMs.toInt()
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
            LinearLayout.LayoutParams(0, dp(215), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
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

        // ---- leaderboard profit mingguan ----
        val boardCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.rgb(34, 18, 58), 16)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val boardHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        boardHead.addView(tv("🏆 Leaderboard Profit Mingguan", 15f, gold).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        resetText = tv("", 11f, Color.LTGRAY, false)
        boardHead.addView(resetText, LinearLayout.LayoutParams(-2, -2))
        boardCard.addView(boardHead, LinearLayout.LayoutParams(-1, -2))
        boardList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        boardCard.addView(boardList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        root.addView(boardCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        renderBoard(emptyList())

        profit = loadProfit()
        updateStats()
        listenCoins(me)

        // Sinkron jam dulu supaya semua pemain ada di ronde yang sama, lalu jalankan ronde.
        syncClock {
            weekId = weekIdOf(nowMs())
            listenBoard()
            handler.post(tick)
        }
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

        card.addView(tv("$multiplier times", 13f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val total = tv("Total: 0", 13f, gold).apply { maxLines = 1 }
        totalTexts[side] = total
        card.addView(total, LinearLayout.LayoutParams(-1, -2))

        val body = FrameLayout(this)
        when (side) {
            "LION" -> body.addView(ImageView(this).apply {
                setImageResource(R.drawable.lion); scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0.95f
            }, FrameLayout.LayoutParams(-1, -1))
            "TIGER" -> body.addView(ImageView(this).apply {
                setImageResource(R.drawable.tiger); scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0.95f
            }, FrameLayout.LayoutParams(-1, -1))
            else -> body.addView(tv("⚖️", 48f), FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val mine = tv(" ", 12f, Color.rgb(120, 255, 150)).apply { maxLines = 1 }
        mineTexts[side] = mine
        card.addView(mine, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
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
                    background = oval()
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                }
                "DRAW" -> tv("⚖️", 16f).apply { background = oval() }
                else -> View(this)
            }
            historyRow.addView(view, LinearLayout.LayoutParams(0, size, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun updateRepeatText() {
        val total = lastBets.values.sum()
        repeatBtn.text = (if (auto) "↻ AUTO ON" else "↻ REPEAT") +
            "\n" + (if (auto) "Hold to stop" else if (total > 0) fmt(total) else "Hold for Auto")
    }

    private fun updateCards() {
        sides.forEach {
            totalTexts[it]?.text = "Total: ${compact(sideTotal[it] ?: 0L)}"
            val mine = bets[it] ?: 0
            mineTexts[it]?.text = if (mine > 0) "Kamu: ${compact(mine.toLong())}" else " "
        }
    }

    private fun updateStats() {
        balanceText.text = "Coins Balance\n${fmt(balance)}"
        profitText.text = "Profits of Today\n${fmt(profit)}"
    }

    // ================= Leaderboard mingguan =================

    private fun weekStartCal(ms: Long): Calendar = Calendar.getInstance(tz).apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }

    private fun weekIdOf(ms: Long): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
        return "w" + f.format(weekStartCal(ms).time)
    }

    private fun playersRef(week: String) =
        db.collection("lvt_weekly").document(week).collection("players")

    private fun listenBoard() {
        boardListener?.remove()
        boardListener = playersRef(weekId)
            .orderBy("profit", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                renderBoard(snap.documents)
            }
    }

    private fun renderBoard(docs: List<DocumentSnapshot>) {
        boardList.removeAllViews()
        val rows = docs.filter { (it.getLong("profit") ?: 0L) > 0L }
        if (rows.isEmpty()) {
            boardList.addView(tv("Belum ada yang profit minggu ini", 13f, Color.LTGRAY, false)
                .apply { setPadding(0, dp(14), 0, dp(14)) }, LinearLayout.LayoutParams(-1, -2))
            return
        }
        rows.forEachIndexed { i, d ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                if (d.id == uid) background = box(Color.argb(70, 255, 204, 65), 10, gold, 1)
            }
            val rankLabel = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" }
            row.addView(tv(rankLabel, 16f, gold), LinearLayout.LayoutParams(dp(36), -2))
            val av = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = oval()
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }
            val photo = d.getString("photo") ?: ""
            if (photo.isNotEmpty()) av.load(photo)
            row.addView(av, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(0, 0, dp(10), 0) })
            val name = tv(d.getString("name") ?: "Player", 14f, Color.WHITE).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val p = d.getLong("profit") ?: 0L
            row.addView(tv("+${fmt(p)}", 14f, Color.rgb(90, 230, 120)), LinearLayout.LayoutParams(-2, -2))
            boardList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
    }

    private fun recordWeekly(delta: Int) {
        val me = uid ?: return
        val data = hashMapOf<String, Any>(
            "uid" to me,
            "name" to myName,
            "photo" to myPhoto,
            "profit" to FieldValue.increment(delta.toLong())
        )
        playersRef(weekIdOf(nowMs())).document(me).set(data, SetOptions.merge())
    }

    private fun checkWeek(now: Long) {
        if (now - lastWeekCheck < 1000) return
        lastWeekCheck = now
        val w = weekIdOf(now)
        if (w != weekId) { weekId = w; listenBoard() }
        val left = weekStartCal(now).timeInMillis + 7L * 24 * 3600 * 1000 - now
        val days = left / 86_400_000L
        val hours = (left % 86_400_000L) / 3_600_000L
        val mins = (left % 3_600_000L) / 60_000L
        val t = if (days > 0) "Reset $days hari $hours jam" else "Reset ${hours} jam ${mins} mnt"
        if (t != lastResetText) { lastResetText = t; resetText.text = t }
    }

    // ================= Koin (Firestore) =================

    private fun listenCoins(me: String) {
        coinListener = db.collection("users").document(me).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            balance = (snap.getLong("coins") ?: 0L).toInt()
            myName = snap.getString("nickname") ?: myName
            myPhoto = snap.getString("photoUrl") ?: ""
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

    /** sign = +1 pasang taruhan (potong koin), sign = -1 batalkan (kembalikan koin). */
    private fun applyBet(
        rid: Long, add: Map<String, Int>, sign: Int,
        onOk: () -> Unit = {}, onFail: (String) -> Unit = {}
    ) {
        val me = uid ?: return onFail("Belum login")
        val total = add.values.sum()
        val userRef = db.collection("users").document(me)
        val betRef = db.collection("lvt_rounds").document(rid.toString())
            .collection("bets").document(me)
        val expire = Date(nowMs() + 2L * 24 * 3600 * 1000)
        val name = myName
        db.runTransaction { tx ->
            val coins = tx.get(userRef).getLong("coins") ?: 0L
            if (sign > 0 && coins < total) throw IllegalStateException("Koin tidak cukup")
            tx.update(userRef, "coins", coins - sign * total)
            val data = HashMap<String, Any>()
            data["uid"] = me
            data["name"] = name
            data["expireAt"] = expire
            add.forEach { (s, a) -> if (a > 0) data[s] = FieldValue.increment((sign * a).toLong()) }
            tx.set(betRef, data, SetOptions.merge())
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
        addBets(mapOf(side to amount))
    }

    private fun placeLastBets() {
        if (phase != "BET") {
            Toast.makeText(this, "Tunggu ronde berikutnya", Toast.LENGTH_SHORT).show(); return
        }
        val total = lastBets.values.sum()
        if (total <= 0) {
            Toast.makeText(this, "Belum ada taruhan sebelumnya", Toast.LENGTH_SHORT).show(); return
        }
        if (balance < total) {
            Toast.makeText(this, "Koin tidak cukup", Toast.LENGTH_SHORT).show(); return
        }
        addBets(HashMap(lastBets))
    }

    private fun addBets(add: Map<String, Int>) {
        val rid = curRid
        applyBet(rid, add, +1, onOk = {
            if (!isDestroyed && curRid == rid && phase == "BET") {
                add.forEach { (s, a) -> bets[s] = (bets[s] ?: 0) + a }
                settled = false
                updateCards()
            } else {
                applyBet(rid, add, -1) // waktu habis sebelum taruhan masuk -> kembalikan
            }
        }, onFail = { msg ->
            if (!isDestroyed) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        })
    }

    // ================= Alur ronde global (berdasarkan jam) =================

    // Hasil ronde mengikuti POLA per blok 12 ronde (sama untuk semua pemain).
    // Tiap blok berisi LION/DRAW/TIGER masing-masing 4x, urutannya berpola:
    //  0 = pasangan (LL TT DD ...), 1 = selang-seling (L T D L T D ...),
    //  2 = cermin (abccba), 3 = naga (LLLL TTTT DDDD acak urutan),
    // lalu diacak sedikit (2 tukar posisi) supaya tidak 100% bisa ditebak.
    private val blockSize = 12L

    private fun blockPattern(block: Long): List<String> {
        val r = java.util.Random(block * 6364136223846793005L + 1442695040888963407L)
        fun perm(): MutableList<String> {
            val l = mutableListOf("LION", "DRAW", "TIGER")
            for (i in l.size - 1 downTo 1) {
                val j = r.nextInt(i + 1)
                val t = l[i]; l[i] = l[j]; l[j] = t
            }
            return l
        }
        val out = ArrayList<String>()
        when (r.nextInt(4)) {
            0 -> (perm() + perm()).forEach { out.add(it); out.add(it) }
            1 -> { val a = perm(); repeat(4) { out.addAll(a) } }
            2 -> {
                val h = ArrayList<String>()
                perm().forEach { h.add(it); h.add(it) }
                out.addAll(h); out.addAll(h.reversed())
            }
            else -> perm().forEach { sd -> repeat(4) { out.add(sd) } }
        }
        repeat(2) {
            val i = r.nextInt(out.size)
            val j = r.nextInt(out.size)
            val t = out[i]; out[i] = out[j]; out[j] = t
        }
        return out
    }

    private fun outcomeOf(rid: Long): String {
        val block = Math.floorDiv(rid, blockSize)
        val idx = Math.floorMod(rid, blockSize).toInt()
        return blockPattern(block)[idx]
    }

    private fun roundNumber(rid: Long): Int {
        val start = rid * cycleMs
        val day = Calendar.getInstance(tz).apply {
            timeInMillis = start
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return ((start - day.timeInMillis) / cycleMs).toInt() + 1
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = nowMs()
            val rid = now / cycleMs
            val pos = now - rid * cycleMs
            val ph = when {
                pos < betMs -> "BET"
                pos < betMs + fightMs -> "FIGHT"
                else -> "RESULT"
            }
            if (rid != curRid) enterRound(rid)
            if (ph != phase) { phase = ph; enterPhase(ph) }
            if (ph == "BET") {
                val left = betMs - pos
                progress.progress = left.toInt()
                countText.text = "Please spend ${(left + 999) / 1000}"
            }
            checkWeek(now)
            handler.postDelayed(this, 100)
        }
    }

    private fun enterRound(rid: Long) {
        if (betsRid >= 0 && !settled) settleRound(betsRid) // pengaman: ronde lama belum dibayar
        curRid = rid
        betsRid = rid
        phase = ""
        settled = true
        outcome = outcomeOf(rid)
        sides.forEach { bets[it] = 0 }
        history.clear()
        for (i in 1..9) history.add(outcomeOf(rid - i))
        renderHistory()
        resetArena()
        banner.visibility = View.GONE
        sides.forEach { cards[it]?.background = box(Color.rgb(90, 60, 20), 12) }
        roundText.text = "Round ${roundNumber(rid)} of Today"
        listenBets(rid)
        updateRepeatText()
    }

    private fun enterPhase(ph: String) {
        when (ph) {
            "BET" -> {
                updateRepeatText()
                if (auto && lastBets.isNotEmpty()) placeLastBets()
            }
            "FIGHT" -> {
                if (bets.values.sum() > 0) lastBets = HashMap(bets)
                progress.progress = 0
                countText.text = "⚔️  Bertarung!  ⚔️"
                playFight()
            }
            "RESULT" -> showResult()
        }
    }

    private fun listenBets(rid: Long) {
        betsListener?.remove()
        sideTotal.clear()
        updateCards()
        betsListener = db.collection("lvt_rounds").document(rid.toString()).collection("bets")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || rid != curRid) return@addSnapshotListener
                val tot = HashMap<String, Long>()
                snap.documents.forEach { d ->
                    sides.forEach { s ->
                        val a = d.getLong(s) ?: 0L
                        if (a > 0L) {
                            tot[s] = (tot[s] ?: 0L) + a
                        }
                    }
                }
                sideTotal.clear(); sideTotal.putAll(tot)
                updateCards()
            }
    }

    private fun playFight() {
        val dist = resources.displayMetrics.widthPixels * 0.17f
        lionImage.animate().translationX(dist).rotationY(-30f).scaleX(1.12f).scaleY(1.12f)
            .setDuration(300).start()
        tigerImage.animate().translationX(-dist).rotationY(30f).scaleX(1.12f).scaleY(1.12f)
            .setDuration(300).start()
        val shake = dp(26).toFloat()
        fightAnim?.cancel()
        fightAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 300
            duration = 100
            repeatCount = 8
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
        fightAnim?.cancel()
        arena.translationX = 0f
        flash.alpha = 0f
        progress.progress = 0
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

        val total = bets.values.sum()
        val pay = (bets[outcome] ?: 0) * multiplier
        val title = if (outcome == "DRAW") "SERI!" else "$outcome MENANG!"
        banner.text = when {
            total == 0 -> title
            pay > 0 -> "$title\n+${fmt(pay - total)}"
            else -> "$title\n-${fmt(total)}"
        }
        banner.visibility = View.VISIBLE
        countText.text = "Round ${roundNumber(curRid)} selesai"
        settleRound(curRid)

        history.clear()
        for (i in 0..8) history.add(outcomeOf(curRid - i))
        renderHistory()
    }

    private fun settleRound(rid: Long) {
        if (settled || rid != betsRid) return
        settled = true
        val total = bets.values.sum()
        if (total <= 0) return
        val pay = (bets[outcomeOf(rid)] ?: 0) * multiplier
        val delta = pay - total
        profit += delta
        saveProfit()
        if (pay > 0) adjustCoins(pay)
        recordWeekly(delta)
        updateStats()
    }

    // ================= Profit harian (lokal) =================

    private fun dayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }.format(Date(nowMs()))

    private fun loadProfit(): Int {
        val p = getSharedPreferences("lion_vs_tiger", MODE_PRIVATE)
        return if (p.getString("pdate", "") == dayKey()) p.getInt("profit", 0) else 0
    }

    private fun saveProfit() {
        getSharedPreferences("lion_vs_tiger", MODE_PRIVATE).edit()
            .putString("pdate", dayKey()).putInt("profit", profit).apply()
    }

    // ================= Sinkron jam server =================

    private fun syncClock(done: () -> Unit) {
        Thread {
            try {
                val t0 = System.currentTimeMillis()
                val c = URL("https://www.google.com").openConnection() as HttpURLConnection
                c.requestMethod = "HEAD"
                c.connectTimeout = 4000
                c.readTimeout = 4000
                c.connect()
                val t1 = System.currentTimeMillis()
                val serverMs = c.date
                c.disconnect()
                if (serverMs > 0L) clockOffset = serverMs - (t0 + t1) / 2
            } catch (e: Exception) {
                // tanpa internet: pakai jam HP
            } finally {
                runOnUiThread { if (!isDestroyed) done() }
            }
        }.start()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fightAnim?.cancel()
        if (phase == "BET") {
            // Ronde belum dimulai -> kembalikan taruhan
            val total = bets.values.sum()
            if (total > 0 && !settled) {
                settled = true
                applyBet(betsRid, HashMap(bets), -1)
            }
        } else {
            settleRound(betsRid) // sudah bertarung -> bayar hasilnya
        }
        coinListener?.remove()
        betsListener?.remove()
        boardListener?.remove()
        super.onDestroy()
    }

    private fun fmt(value: Int): String = fmt(value.toLong())

    private fun fmt(value: Long): String =
        String.format(Locale.US, "%,d", value).replace(',', '.')

    private fun compact(v: Long): String = when {
        v >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", v / 1_000_000_000.0)
        v >= 10_000_000L -> String.format(Locale.US, "%.1fM", v / 1_000_000.0)
        else -> fmt(v)
    }
}
