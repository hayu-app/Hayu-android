package com.hayu.app

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Greedy Eat — ronde GLOBAL berdasarkan jam (jalan terus walau tidak ada yang main).
 * Hasil ronde sama untuk semua pemain (deterministik dari nomor ronde).
 * 8 makanan melingkar + tombol SALAD (semua sayur) dan PIZZA (semua daging).
 * Saat hasil keluar muncul popup hasil + ranking 3 pemain dengan kemenangan terbesar.
 *
 * Firestore: greedy_rounds/{roundId}/players/{uid} -> uid, name, photo, net, expireAt
 */
class GreedyEatActivity : AppCompatActivity() {

    private data class Food(val key: String, val emoji: String, val mult: Int, val weight: Int, val veg: Boolean)

    // urutan searah jarum jam mulai dari atas (sama seperti gambar)
    private val foods = listOf(
        Food("CHICKEN", "🍗", 45, 10, false),
        Food("FISH", "🐟", 25, 18, false),
        Food("STEAK", "🥩", 15, 30, false),
        Food("HOTDOG", "🌭", 10, 45, false),
        Food("CABBAGE", "🥬", 5, 90, true),
        Food("CORN", "🌽", 5, 90, true),
        Food("CARROT", "🥕", 5, 90, true),
        Food("TOMATO", "🍅", 5, 90, true)
    )
    private val totalWeight = foods.sumOf { it.weight }
    private val allKeys = foods.map { it.key } + listOf("SALAD", "PIZZA")
    private val saladPay = 12 // x1.2 (dibagi 10)
    private val pizzaPay = 4  // x4

    private val gold = Color.rgb(255, 204, 65)
    private val brown = Color.rgb(176, 110, 50)
    private val amounts = listOf(1000, 5000, 10000, 50000, 100000)
    private val amountLabels = listOf("1K", "5K", "10K", "50K", "100K")

    private val betMs = 25_000L
    private val spinMs = 4_000L
    private val resultMs = 4_000L
    private val cycleMs = betMs + spinMs + resultMs
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    private var coinListener: ListenerRegistration? = null
    private var rankListener: ListenerRegistration? = null
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
    private var settled = true
    private var auto = false
    private var cursor = -1
    private var lastWin = 0
    private var spinAnim: ValueAnimator? = null

    private val bets = HashMap<String, Int>()
    private var lastBets: Map<String, Int> = emptyMap()

    private lateinit var roundText: TextView
    private lateinit var centerTitle: TextView
    private lateinit var centerValue: TextView
    private lateinit var banner: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var resultRow: LinearLayout
    private lateinit var myBetText: TextView
    private lateinit var nameText: TextView
    private lateinit var coinText: TextView
    private lateinit var revenueText: TextView
    private lateinit var autoBtn: TextView
    private lateinit var avatar: ImageView
    private lateinit var resultPanel: LinearLayout
    private lateinit var ovCountdown: TextView
    private lateinit var ovTitle: TextView
    private lateinit var ovEmoji: TextView
    private lateinit var ovEarn: TextView
    private lateinit var ovBets: TextView
    private lateinit var ovRank: LinearLayout
    private val slots = ArrayList<FrameLayout>()
    private val catViews = HashMap<String, LinearLayout>()
    private val youTexts = HashMap<String, TextView>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(color: Int, radius: Int, stroke: Int = gold, sw: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(sw), stroke)
        }

    private fun ovalBg(fill: Int, stroke: Int, sw: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(sw), stroke)
    }

    private fun txt(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = true) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
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
        allKeys.forEach { bets[it] = 0 }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(104, 190, 255), Color.rgb(176, 226, 255), Color.rgb(150, 215, 110),
                    Color.rgb(222, 70, 80), Color.rgb(190, 40, 60))
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
        }
        scroll.addView(root)
        val host = FrameLayout(this)
        host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        buildOverlay(host)
        setContentView(host)

        // ---- header ----
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(txt("‹", 28f, Color.rgb(120, 70, 10)).apply {
            background = ovalBg(gold, Color.rgb(200, 140, 20), 2)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        roundText = txt("Today Round: …", 17f)
        header.addView(roundText, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(txt("?", 22f, Color.rgb(120, 70, 10)).apply {
            background = ovalBg(gold, Color.rgb(200, 140, 20), 2)
            setOnClickListener { showRules() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // ---- roda makanan ----
        val sw = resources.displayMetrics.widthPixels
        val s = minOf(sw - dp(16), dp(420))
        val d = (s * 0.27f).toInt()
        val c = (s * 0.36f).toInt()
        val r = s * 0.5f - d * 0.5f - dp(2)
        val wheel = FrameLayout(this).apply {
            background = ovalBg(Color.argb(70, 150, 90, 30), Color.argb(120, 150, 90, 30), 6)
        }
        foods.forEachIndexed { i, f ->
            val ang = Math.toRadians(-90.0 + i * 45.0)
            val cx = s / 2f + r * Math.cos(ang).toFloat()
            val cy = s / 2f + r * Math.sin(ang).toFloat()
            val slot = buildSlot(f, d)
            wheel.addView(slot, FrameLayout.LayoutParams(d, d).apply {
                leftMargin = (cx - d / 2f).toInt()
                topMargin = (cy - d / 2f).toInt()
            })
            slots.add(slot)
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ovalBg(Color.rgb(196, 130, 60), Color.rgb(120, 70, 20), 5)
        }
        val chick = TextView(this).apply {
            text = "🐥"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, c * 0.34f)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        center.addView(chick, LinearLayout.LayoutParams(-2, -2))
        centerTitle = txt("SelectTime", 12f)
        centerValue = txt("--", 20f)
        center.addView(centerTitle, LinearLayout.LayoutParams(-2, -2))
        center.addView(centerValue, LinearLayout.LayoutParams(-2, -2))
        wheel.addView(center, FrameLayout.LayoutParams(c, c, Gravity.CENTER))
        banner = txt("", 24f, gold).apply {
            visibility = View.GONE
            background = box(Color.argb(210, 40, 20, 10), 18, gold, 2)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        wheel.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(wheel, LinearLayout.LayoutParams(s, s).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6)
        })

        // ---- panel taruhan ----
        val tray = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.rgb(224, 170, 90), 18, Color.rgb(150, 90, 30), 3)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val catRow = LinearLayout(this)
        catRow.addView(buildCat("SALAD", "🥗", "SALAD  x1.2"),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        catRow.addView(buildCat("PIZZA", "🍕", "PIZZA  x4"),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        tray.addView(catRow, LinearLayout.LayoutParams(-1, -2))
        tray.addView(txt("Steps: Select Amount > Tap makanan untuk BET", 12f, Color.rgb(90, 50, 10)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        chipRow = LinearLayout(this)
        val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        chipScroll.addView(chipRow)
        tray.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(6) })
        buildChips()
        root.addView(tray, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // ---- taruhanku & riwayat ----
        val infoRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        myBetText = txt("Taruhanmu: 0", 14f, Color.rgb(90, 50, 10)).apply {
            background = box(Color.rgb(255, 244, 224), 24, Color.rgb(150, 90, 30), 2)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val histBtn = txt("My History  ›", 14f, Color.rgb(90, 50, 10)).apply {
            background = box(Color.rgb(255, 244, 224), 24, Color.rgb(150, 90, 30), 2)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { showHistory() }
        }
        infoRow.addView(myBetText, LinearLayout.LayoutParams(0, -2, 1.3f).apply { setMargins(0, 0, dp(6), 0) })
        infoRow.addView(histBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(infoRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ---- hasil ----
        val resBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = box(Color.argb(120, 255, 120, 130), 16, Color.TRANSPARENT, 0)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        resBox.addView(txt("Results", 14f), LinearLayout.LayoutParams(dp(64), -2))
        resultRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        resBox.addView(resultRow, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(resBox, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })

        // ---- bawah: profil, revenue, auto ----
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val me1 = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = box(Color.rgb(235, 85, 105), 14, Color.TRANSPARENT, 0)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = box(Color.rgb(255, 230, 150), 10, gold, 2)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setImageResource(R.drawable.lion)
        }
        me1.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nameText = txt("Player", 13f).apply { gravity = Gravity.START; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        coinText = txt("🪙 0", 13f, Color.rgb(90, 50, 10)).apply {
            background = box(Color.WHITE, 12, Color.TRANSPARENT, 0)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        col.addView(nameText, LinearLayout.LayoutParams(-1, -2))
        col.addView(coinText, LinearLayout.LayoutParams(-2, -2))
        me1.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        bottom.addView(me1, LinearLayout.LayoutParams(0, dp(66), 1.15f))

        val rev = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = box(Color.rgb(235, 85, 105), 14, Color.TRANSPARENT, 0)
        }
        rev.addView(txt("Today's Revenue:", 12f), LinearLayout.LayoutParams(-2, -2))
        revenueText = txt("0", 14f, Color.rgb(90, 50, 10)).apply {
            background = box(Color.WHITE, 12, Color.TRANSPARENT, 0)
            setPadding(dp(14), dp(3), dp(14), dp(3))
        }
        rev.addView(revenueText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        bottom.addView(rev, LinearLayout.LayoutParams(0, dp(66), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })

        autoBtn = txt("Auto\nPlay", 16f, Color.rgb(110, 60, 0)).apply {
            background = box(gold, 14, Color.rgb(230, 150, 20), 3)
            setOnClickListener {
                auto = !auto
                updateAutoText()
                if (auto && phase == "BET") placeLastBets()
            }
        }
        bottom.addView(autoBtn, LinearLayout.LayoutParams(0, dp(66), 0.8f))
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        profit = loadProfit()
        renderResults(0L, false)
        updateAll()
        listenCoins(me)

        syncClock { handler.post(tick) }
    }

    // ================= UI builders =================

    private fun slotBg(h: Boolean) = ovalBg(
        if (h) Color.rgb(255, 236, 150) else Color.rgb(255, 246, 228),
        if (h) Color.rgb(255, 160, 0) else brown,
        if (h) 5 else 4
    )

    private fun buildSlot(f: Food, d: Int): FrameLayout {
        val slot = FrameLayout(this).apply {
            background = slotBg(false)
            setOnClickListener { placeBet(f.key) }
        }
        val em = TextView(this).apply {
            text = f.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_PX, d * 0.40f)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        slot.addView(em, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP)
            .apply { topMargin = (d * 0.13f).toInt() })
        slot.addView(txt("x${f.mult}", 12f, Color.rgb(120, 60, 20)),
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
                .apply { topMargin = (d * 0.12f).toInt(); rightMargin = (d * 0.04f).toInt() })
        val you = txt("You: 0", 11f, Color.rgb(220, 70, 70))
        youTexts[f.key] = you
        slot.addView(you, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = (d * 0.10f).toInt() })
        return slot
    }

    private fun buildCat(key: String, emoji: String, label: String): LinearLayout {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = box(Color.rgb(255, 244, 224), 24, brown, 3)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { placeBet(key) }
        }
        v.addView(txt(emoji, 28f), LinearLayout.LayoutParams(-2, -2))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(txt(label, 13f, Color.rgb(90, 50, 10)).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(-2, -2))
        val you = txt("You: 0", 11f, Color.rgb(220, 70, 70)).apply { gravity = Gravity.START }
        youTexts[key] = you
        col.addView(you, LinearLayout.LayoutParams(-2, -2))
        v.addView(col, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        catViews[key] = v
        return v
    }

    private fun setCatHighlight(key: String?) {
        listOf("SALAD", "PIZZA").forEach {
            catViews[it]?.background = if (it == key) box(Color.rgb(255, 236, 150), 24, Color.rgb(255, 160, 0), 4)
            else box(Color.rgb(255, 244, 224), 24, brown, 3)
        }
    }

    private fun buildChips() {
        chipRow.removeAllViews()
        amounts.forEachIndexed { i, amount ->
            val sel = amount == selectedAmount
            val chip = txt(amountLabels[i], 15f, Color.rgb(90, 50, 10)).apply {
                background = box(if (sel) Color.rgb(255, 244, 224) else Color.rgb(200, 130, 40), 14,
                    if (sel) Color.rgb(255, 230, 40) else Color.rgb(150, 90, 30), if (sel) 3 else 1)
                setOnClickListener { selectedAmount = amount; buildChips() }
            }
            chipRow.addView(chip, LinearLayout.LayoutParams(dp(78), -1).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
    }

    private fun setCursor(idx: Int) {
        if (cursor in slots.indices) {
            slots[cursor].background = slotBg(false)
            slots[cursor].scaleX = 1f; slots[cursor].scaleY = 1f
        }
        cursor = idx
        if (cursor in slots.indices) {
            slots[cursor].background = slotBg(true)
            slots[cursor].scaleX = 1.1f; slots[cursor].scaleY = 1.1f
        }
    }

    private fun renderResults(rid: Long, includeCurrent: Boolean) {
        resultRow.removeAllViews()
        for (i in 0 until 7) {
            val off = if (includeCurrent) i else i + 1
            val item = if (rid == 0L && !includeCurrent) "" else foods[outcomeIdx(rid - off)].emoji
            val v = TextView(this).apply {
                text = item
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = ovalBg(Color.rgb(255, 246, 228), brown, 2)
            }
            resultRow.addView(v, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun updateAll() {
        allKeys.forEach { youTexts[it]?.text = "You: ${compact((bets[it] ?: 0).toLong())}" }
        myBetText.text = "Taruhanmu: ${fmt(bets.values.sum())}"
        coinText.text = "🪙 ${fmt(balance)}"
        nameText.text = myName
        revenueText.text = fmt(profit)
    }

    private fun updateAutoText() {
        autoBtn.text = if (auto) "Auto\nON" else "Auto\nPlay"
    }

    private fun showRules() {
        val sb = StringBuilder()
        foods.forEach { sb.append("${it.emoji}  x${it.mult}\n") }
        sb.append("\n🥗 SALAD x1.2 → menang jika hasilnya sayur (🥬🌽🥕🍅)")
        sb.append("\n🍕 PIZZA x4 → menang jika hasilnya daging (🍗🐟🥩🌭)")
        sb.append("\n\nTap makanan untuk BET sebesar nominal yang dipilih. Ronde berjalan terus dan hasilnya sama untuk semua pemain.")
        AlertDialog.Builder(this).setTitle("Cara main").setMessage(sb.toString())
            .setPositiveButton("OK", null).show()
    }

    // ================= Koin =================

    private fun listenCoins(me: String) {
        coinListener = db.collection("users").document(me).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            balance = (snap.getLong("coins") ?: 0L).toInt()
            myName = snap.getString("nickname") ?: myName
            val photo = snap.getString("photoUrl") ?: ""
            myPhoto = photo
            if (photo.isNotEmpty()) avatar.load(photo)
            updateAll()
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

    private fun placeBet(key: String) {
        if (phase != "BET") {
            Toast.makeText(this, "Tunggu ronde berikutnya", Toast.LENGTH_SHORT).show(); return
        }
        if (balance < selectedAmount) {
            Toast.makeText(this, "Koin tidak cukup", Toast.LENGTH_SHORT).show(); return
        }
        addBets(mapOf(key to selectedAmount))
    }

    private fun placeLastBets() {
        if (phase != "BET") return
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
        val total = add.values.sum()
        adjustCoins(-total, onOk = {
            if (!isDestroyed && curRid == rid && phase == "BET") {
                add.forEach { (k, a) -> bets[k] = (bets[k] ?: 0) + a }
                settled = false
                updateAll()
            } else {
                adjustCoins(total) // waktu habis sebelum taruhan masuk -> kembalikan
            }
        }, onFail = { msg ->
            if (!isDestroyed) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        })
    }

    // ================= Hasil ronde (deterministik) =================

    private fun outcomeIdx(rid: Long): Int {
        val r = java.util.Random(rid * 6364136223846793005L + 1442695040888963407L)
        var x = r.nextInt(totalWeight)
        for (i in foods.indices) {
            x -= foods[i].weight
            if (x < 0) return i
        }
        return 0
    }

    private fun payoutOf(win: Food, b: Map<String, Int>): Int {
        var pay = (b[win.key] ?: 0) * win.mult
        pay += if (win.veg) (b["SALAD"] ?: 0) * saladPay / 10 else (b["PIZZA"] ?: 0) * pizzaPay
        return pay
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

    // ================= Alur ronde global =================

    private val tick = object : Runnable {
        override fun run() {
            val now = nowMs()
            val rid = now / cycleMs
            val pos = now - rid * cycleMs
            val ph = when {
                pos < betMs -> "BET"
                pos < betMs + spinMs -> "SPIN"
                else -> "RESULT"
            }
            if (rid != curRid) enterRound(rid)
            if (ph != phase) { phase = ph; enterPhase(ph) }
            if (ph == "BET") {
                val left = betMs - pos
                centerValue.text = "${(left + 999) / 1000}s"
            }
            if (ph == "RESULT" && resultPanel.visibility == View.VISIBLE) {
                ovCountdown.text = "${(cycleMs - pos + 999) / 1000}s"
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun enterRound(rid: Long) {
        if (betsRid >= 0 && !settled) settleRound(betsRid)
        curRid = rid
        betsRid = rid
        phase = ""
        settled = true
        allKeys.forEach { bets[it] = 0 }
        spinAnim?.cancel()
        setCursor(-1)
        setCatHighlight(null)
        banner.visibility = View.GONE
        resultPanel.visibility = View.GONE
        rankListener?.remove(); rankListener = null
        centerTitle.text = "SelectTime"
        roundText.text = "Today Round: ${roundNumber(rid)}"
        renderResults(rid, false)
        updateAll()
    }

    private fun enterPhase(ph: String) {
        when (ph) {
            "BET" -> if (auto && lastBets.isNotEmpty()) placeLastBets()
            "SPIN" -> {
                if (bets.values.sum() > 0) lastBets = HashMap(bets)
                centerTitle.text = "Memutar…"
                centerValue.text = "🎡"
                playSpin()
            }
            "RESULT" -> showResult()
        }
    }

    private fun playSpin() {
        spinAnim?.cancel()
        val win = outcomeIdx(curRid)
        val start = lastWin
        val total = 24 + ((win - start + 8) % 8)
        spinAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = spinMs - 300
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val step = (total * (it.animatedValue as Float)).toInt()
                val idx = (start + step) % 8
                if (idx != cursor) setCursor(idx)
            }
            start()
        }
    }

    private fun showResult() {
        spinAnim?.cancel()
        val wi = outcomeIdx(curRid)
        val win = foods[wi]
        setCursor(wi)
        lastWin = wi
        setCatHighlight(if (win.veg) "SALAD" else "PIZZA")
        val total = bets.values.sum()
        val pay = payoutOf(win, bets)
        centerTitle.text = "Hasil"
        centerValue.text = "${win.emoji} x${win.mult}"
        banner.text = when {
            total == 0 -> "${win.emoji} x${win.mult}"
            pay > 0 -> "${win.emoji} x${win.mult}\n+${fmt(pay - total)}"
            else -> "${win.emoji} x${win.mult}\n-${fmt(total)}"
        }
        banner.visibility = View.VISIBLE
        settleRound(curRid)
        renderResults(curRid, true)
        showResultOverlay(win, total, pay)
    }

    // ================= Popup hasil + ranking =================

    private fun buildOverlay(host: FrameLayout) {
        val h = (resources.displayMetrics.heightPixels * 0.78f).toInt()
        val r = dp(28).toFloat()
        resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            isClickable = true
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 120, 20, 40))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        val top = FrameLayout(this)
        ovCountdown = txt("", 16f)
        top.addView(ovCountdown, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        ovTitle = txt("", 16f)
        top.addView(ovTitle, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        top.addView(txt("✕", 24f).apply { setOnClickListener { resultPanel.visibility = View.GONE } },
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.CENTER_VERTICAL))
        resultPanel.addView(top, LinearLayout.LayoutParams(-1, dp(44)))

        val glow = FrameLayout(this).apply { background = ovalBg(Color.argb(70, 255, 220, 80), Color.TRANSPARENT, 0) }
        ovEmoji = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 84f)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        glow.addView(ovEmoji, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        resultPanel.addView(glow, LinearLayout.LayoutParams(dp(160), dp(160)).apply { topMargin = dp(8) })

        ovEarn = txt("", 17f)
        ovBets = txt("", 17f)
        resultPanel.addView(ovEarn, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) })
        resultPanel.addView(ovBets, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        resultPanel.addView(txt("This Round's Ranking:", 17f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        ovRank = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        resultPanel.addView(ovRank, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        host.addView(resultPanel, FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM))
    }

    private fun showResultOverlay(win: Food, total: Int, pay: Int) {
        val net = pay - total
        ovTitle.text = "Round ${roundNumber(curRid)}'s Result:"
        ovEmoji.text = win.emoji
        ovEarn.text = "This Round Earnings:  🪙 " + (if (net < 0) "-${fmt(-net)}" else fmt(net))
        ovBets.text = "This Round Bets:  🪙 ${fmt(total)}"
        ovCountdown.text = "${resultMs / 1000}s"
        renderRank(emptyList())
        resultPanel.visibility = View.VISIBLE
        listenRank(curRid)
    }

    private fun listenRank(rid: Long) {
        rankListener?.remove()
        rankListener = db.collection("greedy_rounds").document(rid.toString()).collection("players")
            .orderBy("net", Query.Direction.DESCENDING)
            .limit(3)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || rid != curRid) return@addSnapshotListener
                renderRank(snap.documents)
            }
    }

    private fun renderRank(docs: List<DocumentSnapshot>) {
        ovRank.removeAllViews()
        val rows = docs.filter { (it.getLong("net") ?: 0L) > 0L }
        if (rows.isEmpty()) {
            ovRank.addView(txt("Belum ada pemenang ronde ini", 14f, Color.LTGRAY, false),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24) })
            return
        }
        rows.forEach { d ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val av = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = ovalBg(Color.rgb(60, 20, 40), Color.WHITE, 3)
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }
            val photo = d.getString("photo") ?: ""
            if (photo.isNotEmpty()) av.load(photo)
            col.addView(av, LinearLayout.LayoutParams(dp(84), dp(84)))
            col.addView(txt(d.getString("name") ?: "Player", 15f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(dp(100), -2).apply { topMargin = dp(6) })
            col.addView(txt("🪙 ${fmt(d.getLong("net") ?: 0L)}", 14f, Color.rgb(255, 230, 120)),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            ovRank.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun settleRound(rid: Long) {
        if (settled || rid != betsRid) return
        settled = true
        val total = bets.values.sum()
        if (total <= 0) return
        val win = foods[outcomeIdx(rid)]
        val pay = payoutOf(win, bets)
        profit += pay - total
        saveProfit()
        saveHistory(rid, win, total, pay)
        if (pay > 0) adjustCoins(pay)
        if (pay > total) reportWin(rid, (pay - total).toLong())
        updateAll()
    }

    private fun reportWin(rid: Long, net: Long) {
        val me = uid ?: return
        val data = hashMapOf<String, Any>(
            "uid" to me,
            "name" to myName,
            "photo" to myPhoto,
            "net" to net,
            "expireAt" to Date(nowMs() + 2L * 24 * 3600 * 1000)
        )
        db.collection("greedy_rounds").document(rid.toString())
            .collection("players").document(me).set(data)
    }

    // ================= Riwayat & profit (lokal) =================

    private fun prefs() = getSharedPreferences("greedy_eat", MODE_PRIVATE)

    private fun dayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }.format(Date(nowMs()))

    private fun loadProfit(): Int =
        if (prefs().getString("pdate", "") == dayKey()) prefs().getInt("profit", 0) else 0

    private fun saveProfit() {
        prefs().edit().putString("pdate", dayKey()).putInt("profit", profit).apply()
    }

    private fun saveHistory(rid: Long, win: Food, total: Int, pay: Int) {
        val delta = pay - total
        val line = "Ronde ${roundNumber(rid)} • ${win.emoji} x${win.mult} • Taruhan ${fmt(total)} • " +
            (if (delta >= 0) "+${fmt(delta)}" else "-${fmt(-delta)}")
        val old = prefs().getString("hist", "") ?: ""
        val list = (listOf(line) + old.split("\n").filter { it.isNotBlank() }).take(30)
        prefs().edit().putString("hist", list.joinToString("\n")).apply()
    }

    private fun showHistory() {
        val h = prefs().getString("hist", "") ?: ""
        AlertDialog.Builder(this).setTitle("My History")
            .setMessage(if (h.isBlank()) "Belum ada riwayat" else h)
            .setPositiveButton("Tutup", null).show()
    }

    // ================= Sinkron jam =================

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
        spinAnim?.cancel()
        if (phase == "BET") {
            val total = bets.values.sum()
            if (total > 0 && !settled) {
                settled = true
                adjustCoins(total) // ronde belum jalan -> kembalikan taruhan
            }
        } else {
            settleRound(betsRid)
        }
        coinListener?.remove()
        rankListener?.remove()
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
