package com.hayu.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Level hubungan dari Poin Keharmonisan (bonds/{id}.points). Ubah angka di sini untuk mengatur syarat level. */
object RelationLevels {
    class Lvl(val label: String, val min: Long, val mark: String, val color: String)

    val CP = listOf(
        Lvl("Lv.0", 0L, "♥", "#FF6FA8"),
        Lvl("Lv.1", 1_000L, "I", "#2D9CFF"),
        Lvl("Lv.2", 5_000L, "II", "#8B5CF6"),
        Lvl("Lv.3", 15_000L, "III", "#F04545"),
        Lvl("Lv.4", 40_000L, "IV", "#22A559"),
        Lvl("CP Sehati", 100_000L, "VIII", "#C2185B"),
        Lvl("CP Cinta Membara", 250_000L, "IX", "#9C27B0")
    )
    val BFF = listOf(
        Lvl("Lv.0", 0L, "♦", "#9E7B5A"),
        Lvl("Lv.1", 500L, "I", "#D8923A"),
        Lvl("Lv.2", 2_500L, "II", "#2EAE5E"),
        Lvl("Lv.3", 10_000L, "III", "#2D7BE0"),
        Lvl("Lv.4", 30_000L, "IV", "#8B5CF6")
    )

    fun index(list: List<Lvl>, points: Long): Int = list.indexOfLast { points >= it.min }.coerceAtLeast(0)
}

/** "Hubungan" di profil: CP Space & BFF Space. Undang / kelola lewat CpBffActivity. */
class HubunganActivity : AppCompatActivity() {

    private class Theme(
        val pageBg: Int, val heroTop: Int, val heroBottom: Int, val cardFill: Int, val border: Int,
        val ribbonA: Int, val ribbonB: Int, val ribbonStroke: Int, val btnText: Int, val title: String,
        val subtitle: String
    )

    private val cpTheme = Theme(
        Color.parseColor("#FF5C9E"), Color.parseColor("#FFB3D3"), Color.parseColor("#FF5C9E"),
        Color.parseColor("#FF4A92"), Color.parseColor("#FFE45C"),
        Color.parseColor("#FF3F9F"), Color.parseColor("#FF8AC4"), Color.parseColor("#FFD6EA"),
        Color.parseColor("#A0185A"), "CP Space", "Bangun hubungan CP-mu 💕"
    )
    private val bffTheme = Theme(
        Color.parseColor("#5A0B08"), Color.parseColor("#F08A1E"), Color.parseColor("#5A0B08"),
        Color.parseColor("#7A140E"), Color.parseColor("#E8B545"),
        Color.parseColor("#A80E0E"), Color.parseColor("#E52A2A"), Color.parseColor("#F5C96B"),
        Color.parseColor("#7A4A00"), "BFF Space", "Sahabat sejati, selalu bersama 🤝"
    )

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var frame: FrameLayout
    private lateinit var body: LinearLayout
    private lateinit var topBar: FrameLayout

    private var mode = "cp"
    private var advanced = false
    private var pickedLevel = -1
    private var myUid = ""
    private var myName = "Saya"
    private var myPhoto = ""
    private var bonds: List<DocumentSnapshot> = emptyList()
    private val photos = HashMap<String, String>()

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = if (intent.getStringExtra("mode") == "bff") "bff" else "cp"
        myUid = auth.currentUser?.uid ?: run { finish(); return }

        frame = FrameLayout(this)
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 0, 0, d(40))
        sv.addView(body)
        frame.addView(sv, FrameLayout.LayoutParams(-1, -1))
        topBar = FrameLayout(this)
        frame.addView(topBar, FrameLayout.LayoutParams(-1, d(64), Gravity.TOP))
        setContentView(frame)
        render()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    // ───────── data ─────────

    private fun st(b: DocumentSnapshot) = b.getString("status").orEmpty()
    private fun ty(b: DocumentSnapshot) = b.getString("type").orEmpty()
    private fun pts(b: DocumentSnapshot) = (b.get("points") as? Number)?.toLong() ?: 0L
    private fun otherUid(b: DocumentSnapshot) =
        if (b.getString("fromUid") == myUid) b.getString("toUid").orEmpty() else b.getString("fromUid").orEmpty()
    private fun otherName(b: DocumentSnapshot) =
        (if (b.getString("fromUid") == myUid) b.getString("toName") else b.getString("fromName")) ?: "-"
    private fun active(t: String) = bonds.filter { ty(it) == t && st(it) == "active" }
    private fun incoming(t: String) =
        bonds.filter { ty(it) == t && st(it) == "pending" && it.getString("toUid") == myUid }

    private fun load() {
        db.collection("users").document(myUid).get().addOnSuccessListener { me ->
            myPhoto = me.getString("photoUrl") ?: ""
            myName = me.getString("nickname") ?: "Saya"
            db.collection("bonds").whereArrayContains("uids", myUid).get()
                .addOnSuccessListener { snap ->
                    bonds = snap.documents
                    val partners = bonds.filter { st(it) == "active" }.map { otherUid(it) }
                        .filter { it.isNotEmpty() }.distinct()
                    if (partners.isEmpty()) { render(); return@addOnSuccessListener }
                    var left = partners.size
                    for (p in partners) {
                        db.collection("users").document(p).get()
                            .addOnSuccessListener { u ->
                                photos[p] = u.getString("photoUrl") ?: ""
                                left -= 1
                                if (left == 0) render()
                            }
                            .addOnFailureListener {
                                left -= 1
                                if (left == 0) render()
                            }
                    }
                }
                .addOnFailureListener { toast("Gagal memuat: ${it.message}"); render() }
        }
    }

    private fun openInvite() {
        startActivity(
            Intent(this, CpBffActivity::class.java).putExtra("title", "CP & BFF").putExtra("mode", mode)
        )
    }

    // ───────── gambar bantu ─────────

    private fun fill(color: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(radius).toFloat()
        if (strokeW > 0) setStroke(d(strokeW), stroke)
    }

    private fun grad(a: Int, b: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0, horizontal: Boolean = false) =
        GradientDrawable(
            if (horizontal) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(a, b)
        ).apply {
            cornerRadius = d(radius).toFloat()
            if (strokeW > 0) setStroke(d(strokeW), stroke)
        }

    private fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun goldButton(t: Theme, label: String, onClick: () -> Unit): TextView {
        val b = text(label, 16f, t.btnText, true)
        b.gravity = Gravity.CENTER
        b.background = grad(Color.parseColor("#FFE9A0"), Color.parseColor("#FFBE2E"), 24, Color.parseColor("#FFF6D0"), 2)
        b.setPadding(d(28), d(10), d(28), d(10))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun section(t: Theme, title: String): LinearLayout {
        val outer = FrameLayout(this)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.background = fill(t.cardFill, 18, t.border, 2)
        inner.setPadding(d(14), d(42), d(14), d(18))
        outer.addView(inner, FrameLayout.LayoutParams(-1, -2).apply { topMargin = d(22) })

        val ribbon = text(title, 17f, Color.WHITE, true)
        ribbon.gravity = Gravity.CENTER
        ribbon.setPadding(d(34), d(10), d(34), d(10))
        ribbon.background = grad(t.ribbonA, t.ribbonB, 22, t.ribbonStroke, 2, horizontal = true)
        outer.addView(ribbon, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        body.addView(outer, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(d(16), d(14), d(16), 0)
        })
        return inner
    }

    private fun avatar(url: String, size: Int): ImageView {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#33000000")) }
        iv.outlineProvider = ViewOutlineProvider.BACKGROUND
        iv.clipToOutline = true
        if (url.isNotEmpty()) iv.load(url) { crossfade(true) } else iv.setImageResource(R.drawable.ic_profile)
        iv.layoutParams = FrameLayout.LayoutParams(d(size), d(size), Gravity.CENTER)
        return iv
    }

    /** Lingkaran emas berisi foto, atau tanda + kalau kosong. */
    private fun slot(t: Theme, url: String?, size: Int, onClick: () -> Unit): FrameLayout {
        val box = FrameLayout(this)
        box.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#FFF3E0"))
            setStroke(d(4), Color.parseColor("#F2B63D"))
        }
        if (url != null) {
            box.addView(avatar(url, size - 22))
        } else {
            val plus = text("+", 44f, Color.parseColor("#FF9A1F"), true)
            plus.gravity = Gravity.CENTER
            box.addView(plus, FrameLayout.LayoutParams(-1, -1))
        }
        box.setOnClickListener { onClick() }
        return box
    }

    // ───────── tampilan ─────────

    private fun render() {
        val t = if (mode == "cp") cpTheme else bffTheme
        frame.setBackgroundColor(t.pageBg)
        body.removeAllViews()
        body.addView(hero(t))
        if (mode == "cp") renderCp(t) else renderBff(t)
        renderTopBar(t)
    }

    private fun hero(t: Theme): View {
        val h = FrameLayout(this)
        h.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.heroTop, t.heroBottom)
        ).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, d(40).toFloat(), d(40).toFloat(), d(40).toFloat(), d(40).toFloat())
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val crown = text("👑", 44f)
        crown.gravity = Gravity.CENTER
        col.addView(crown)
        val title = text(t.title, 44f, Color.parseColor("#FFE27A"), true)
        title.setShadowLayer(10f, 0f, 4f, Color.parseColor("#AA7A0033"))
        col.addView(title)
        val sub = text(t.subtitle, 14f, Color.WHITE, true)
        sub.setPadding(0, d(6), 0, 0)
        col.addView(sub)
        h.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { topMargin = d(30) })

        val rules = text("Aturan", 14f, Color.WHITE, true)
        rules.background = grad(t.ribbonA, t.ribbonB, 16, t.ribbonStroke, 1, horizontal = true)
        rules.setPadding(d(14), d(6), d(14), d(6))
        rules.setOnClickListener { showRules() }
        h.addView(rules, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply {
            bottomMargin = d(50); marginEnd = d(0)
        })
        h.layoutParams = LinearLayout.LayoutParams(-1, d(290))
        return h
    }

    private fun showRules() {
        AlertDialog.Builder(this)
            .setTitle("Aturan CP & BFF")
            .setMessage(
                "• Biaya undangan CP 🪙${CpBffActivity.CP_PRICE}, BFF 🪙${CpBffActivity.BFF_PRICE}. " +
                    "Dikembalikan kalau ditolak atau dibatalkan.\n" +
                    "• Maksimal 1 CP dan ${CpBffActivity.BFF_MAX} BFF.\n" +
                    "• Poin Keharmonisan didapat dari mengirim hadiah CP/BFF ke pasanganmu di room " +
                    "(poin = harga hadiah ÷ 10).\n" +
                    "• Peringkat ada di Beranda → CP&BFF."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderTopBar(t: Theme) {
        topBar.removeAllViews()
        val back = text("‹", 28f, Color.WHITE, true)
        back.gravity = Gravity.CENTER
        back.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (mode == "cp") Color.parseColor("#FF4D94") else Color.parseColor("#F5A800"))
            setStroke(d(2), Color.parseColor("#FFE9A0"))
        }
        back.setOnClickListener { finish() }
        topBar.addView(back, FrameLayout.LayoutParams(d(40), d(40), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = d(12)
        })

        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.background = fill(Color.parseColor("#66000000"), 24, Color.parseColor("#FFE9A0"), 2)
        pill.setPadding(d(3), d(3), d(3), d(3))
        for ((key, label) in listOf("cp" to "CP", "bff" to "Sahabat")) {
            val on = key == mode
            val seg = text(label, 16f, if (on) Color.WHITE else Color.parseColor("#C9C0C4"), true)
            seg.gravity = Gravity.CENTER
            if (on) {
                seg.background = if (key == "cp")
                    grad(Color.parseColor("#FF4D94"), Color.parseColor("#FF86BE"), 20, Color.parseColor("#FFD6EA"), 2)
                else
                    grad(Color.parseColor("#FFD84A"), Color.parseColor("#F5A800"), 20, Color.parseColor("#FFF1B0"), 2)
                if (key == "bff") seg.setTextColor(Color.parseColor("#7A4A00"))
            }
            seg.setOnClickListener {
                if (mode != key) {
                    mode = key
                    advanced = false
                    pickedLevel = -1
                    render()
                }
            }
            pill.addView(seg, LinearLayout.LayoutParams(0, -1, 1f))
        }
        topBar.addView(pill, FrameLayout.LayoutParams(-1, d(46), Gravity.CENTER_VERTICAL).apply {
            marginStart = d(64); marginEnd = d(16)
        })
    }

    // ───────── CP ─────────

    private fun renderCp(t: Theme) {
        val mine = active("cp").firstOrNull()
        val card = section(t, "CP")

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val left = slot(t, myPhoto, 112) { toast("Ini kamu 💕") }
        val partnerPhoto = mine?.let { photos[otherUid(it)] ?: "" }
        val right = slot(t, partnerPhoto, 112) {
            if (mine == null) openInvite() else toast("${otherName(mine)} 💕")
        }
        row.addView(left, LinearLayout.LayoutParams(d(112), d(112)).apply { marginEnd = d(36) })
        row.addView(right, LinearLayout.LayoutParams(d(112), d(112)))
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(4) })

        val cap = text("", 14f, Color.WHITE, true)
        cap.gravity = Gravity.CENTER
        cap.setPadding(0, d(14), 0, d(10))
        if (mine != null) {
            val p = pts(mine)
            val lv = RelationLevels.CP[RelationLevels.index(RelationLevels.CP, p)]
            val days = ((System.currentTimeMillis() - ((mine.get("acceptedAt") as? Number)?.toLong() ?: System.currentTimeMillis())) / 86_400_000L).coerceAtLeast(0L)
            cap.text = "$myName 💕 ${otherName(mine)}\n💗 $p poin • ${lv.label} • bersama $days hari"
        } else {
            cap.text = "Belum punya CP"
        }
        card.addView(cap, LinearLayout.LayoutParams(-1, -2))

        val inc = incoming("cp")
        if (inc.isNotEmpty()) {
            val badge = text("📩 ${inc.size} undangan masuk — ketuk untuk menjawab", 13f, Color.WHITE, true)
            badge.gravity = Gravity.CENTER
            badge.background = fill(Color.parseColor("#33000000"), 14)
            badge.setPadding(d(12), d(8), d(12), d(8))
            badge.setOnClickListener { openInvite() }
            card.addView(badge, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(10) })
        }

        card.addView(
            goldButton(t, if (mine == null) "Ayo mengundang" else "Kelola CP") { openInvite() },
            LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        renderHowToUpdate(t)
        renderCpRights(t, mine?.let { pts(it) } ?: 0L)
    }

    private fun renderHowToUpdate(t: Theme) {
        val card = section(t, "Bagaimana cara memperbarui?")
        val tv = text(
            "Kirim hadiah CP/BFF ke pasanganmu di room untuk menambah Poin Keharmonisan. " +
                "Saat poin mencapai nilai tertentu, level naik dan hak eksklusif terbuka.",
            15f, Color.WHITE
        )
        tv.setLineSpacing(0f, 1.25f)
        card.addView(tv)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val items = listOf(
            Triple("🎤", "Mic", "Segera hadir"),
            Triple("💬", "Chat", "Segera hadir"),
            Triple("🎮", "Game", "Segera hadir"),
            Triple("🎁", "Hadiah", "Buka panel Hadiah di room, lalu tab CP/BFF")
        )
        for ((emoji, label, info) in items) {
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.background = fill(Color.parseColor("#22FFFFFF"), 14, t.border, 2)
            tile.setPadding(d(4), d(12), d(4), d(10))
            val e = text(emoji, 34f)
            e.gravity = Gravity.CENTER
            tile.addView(e)
            val l = text(label, 14f, Color.WHITE, true)
            l.gravity = Gravity.CENTER
            l.setPadding(0, d(4), 0, d(6))
            tile.addView(l)
            val go = text("›", 14f, t.cardFill, true)
            go.gravity = Gravity.CENTER
            go.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            tile.addView(go, LinearLayout.LayoutParams(d(20), d(20)))
            tile.setOnClickListener { toast(info) }
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(d(4), d(16), d(4), 0) })
        }
        card.addView(row)
    }

    private fun renderCpRights(t: Theme, points: Long) {
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        for ((adv, label) in listOf(false to "Hak Dasar", true to "Hak Tingkat Lanjut")) {
            val on = adv == advanced
            val b = text(label, 15f, if (on) t.btnText else Color.WHITE, true)
            b.gravity = Gravity.CENTER
            b.background = if (on)
                grad(Color.parseColor("#FFE9A0"), Color.parseColor("#FFBE2E"), 20, Color.parseColor("#FFF6D0"), 2)
            else
                grad(t.ribbonA, t.ribbonB, 20, t.ribbonStroke, 2, horizontal = true)
            b.setPadding(d(8), d(12), d(8), d(12))
            b.setOnClickListener { advanced = adv; pickedLevel = -1; render() }
            tabs.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(d(6), 0, d(6), 0) })
        }
        body.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { setMargins(d(10), d(22), d(10), 0) })

        val group = if (advanced) RelationLevels.CP.drop(5) else RelationLevels.CP.take(5)
        val offset = if (advanced) 5 else 0
        val rights = if (advanced) listOf(
            "🏰" to "Ruang CP", "🎀" to "Banner Menjalin Hubungan", "📣" to "Pengumuman Peningkatan",
            "📜" to "Sertifikat", "⭐" to "Hak Istimewa Level", "🌐" to "Banner Global",
            "🎤" to "Efek Mic", "🖼️" to "Bingkai", "🃏" to "Kartu Background CP"
        ) else listOf("🏰" to "Ruang CP", "🎀" to "Banner Menjalin Hubungan")
        renderLevelCard(t, RelationLevels.CP, group, offset, points, rights)
    }

    // ───────── BFF ─────────

    private fun renderBff(t: Theme) {
        val how = section(t, "Cara Menjalin Hubungan")
        val steps = LinearLayout(this)
        steps.orientation = LinearLayout.HORIZONTAL
        steps.gravity = Gravity.CENTER_VERTICAL
        val list = listOf("🔎" to "Cari teman", "✉️" to "Kirim undangan", "🤝" to "Jalin hubungan")
        list.forEachIndexed { i, (emoji, label) ->
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val ring = text(emoji, 30f)
            ring.gravity = Gravity.CENTER
            ring.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#5A2A10"))
                setStroke(d(3), Color.parseColor("#F2B63D"))
            }
            col.addView(ring, LinearLayout.LayoutParams(d(66), d(66)))
            val l = text(label, 14f, Color.parseColor("#FFE9B0"))
            l.gravity = Gravity.CENTER
            l.setPadding(0, d(8), 0, 0)
            col.addView(l)
            steps.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            if (i < list.size - 1) {
                val arrow = text("▶", 16f, Color.parseColor("#F2B63D"))
                steps.addView(arrow, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = d(26) })
            }
        }
        how.addView(steps)

        val wall = section(t, "Dinding Sahabat")
        val mine = active("bff")
        for (r in 0 until 2) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 3) {
                val idx = r * 3 + c
                line.addView(friendSlot(t, idx, mine), LinearLayout.LayoutParams(0, d(170), 1f).apply {
                    setMargins(d(4), d(6), d(4), d(6))
                })
            }
            wall.addView(line)
        }
        val inc = incoming("bff")
        if (inc.isNotEmpty()) {
            val badge = text("📩 ${inc.size} undangan masuk — ketuk untuk menjawab", 13f, Color.WHITE, true)
            badge.gravity = Gravity.CENTER
            badge.background = fill(Color.parseColor("#33000000"), 14)
            badge.setPadding(d(12), d(8), d(12), d(8))
            badge.setOnClickListener { openInvite() }
            wall.addView(badge, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })
        }

        renderHowToUpdate(t)

        val best = mine.maxOfOrNull { pts(it) } ?: 0L
        renderLevelCard(
            t, RelationLevels.BFF, RelationLevels.BFF, 0, best,
            listOf("🏯" to "Ruang BFF", "🎀" to "Banner Menjalin Hubungan"),
            title = "Hak eksklusif"
        )
    }

    private fun friendSlot(t: Theme, idx: Int, mine: List<DocumentSnapshot>): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.background = grad(Color.parseColor("#E01414"), Color.parseColor("#B50E0E"), 16, Color.parseColor("#F2B63D"), 2)
        card.setPadding(d(4), d(8), d(4), d(8))

        val bond = mine.getOrNull(idx)
        when {
            bond != null -> {
                val a = slot(t, photos[otherUid(bond)] ?: "", 80) { toast("${otherName(bond)} 🤝") }
                card.addView(a, LinearLayout.LayoutParams(d(80), d(80)))
                val n = text(otherName(bond), 13f, Color.WHITE, true)
                n.gravity = Gravity.CENTER
                n.maxLines = 1
                n.setPadding(d(4), d(8), d(4), 0)
                card.addView(n)
                val p = text("💗 ${pts(bond)}", 12f, Color.parseColor("#FFE9A0"), true)
                p.gravity = Gravity.CENTER
                card.addView(p)
            }
            idx < CpBffActivity.BFF_MAX -> {
                card.addView(slot(t, null, 80) { openInvite() }, LinearLayout.LayoutParams(d(80), d(80)))
                val b = goldSmall("Ayo mengundang") { openInvite() }
                card.addView(b, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(10) })
            }
            else -> {
                val q = slot(t, null, 80) { toast("Slot tambahan segera hadir") }
                (q.getChildAt(0) as? TextView)?.text = "?"
                card.addView(q, LinearLayout.LayoutParams(d(80), d(80)))
                val b = goldSmall("Buka kunci") { toast("Slot tambahan segera hadir") }
                card.addView(b, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(10) })
            }
        }
        return card
    }

    private fun goldSmall(label: String, onClick: () -> Unit): TextView {
        val b = text(label, 11f, Color.parseColor("#7A4A00"), true)
        b.gravity = Gravity.CENTER
        b.maxLines = 1
        b.background = grad(Color.parseColor("#FFE9A0"), Color.parseColor("#FFBE2E"), 14, Color.parseColor("#FFF6D0"), 1)
        b.setPadding(d(8), d(5), d(8), d(5))
        b.setOnClickListener { onClick() }
        return b
    }

    // ───────── kartu level + hak (dipakai CP & BFF) ─────────

    private fun renderLevelCard(
        t: Theme,
        all: List<RelationLevels.Lvl>,
        group: List<RelationLevels.Lvl>,
        offset: Int,
        points: Long,
        rights: List<Pair<String, String>>,
        title: String? = null
    ) {
        val card: LinearLayout = if (title != null) {
            section(t, title)
        } else {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.background = fill(t.cardFill, 18, t.border, 2)
            c.setPadding(d(12), d(16), d(12), d(18))
            body.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(d(16), d(14), d(16), 0) })
            c
        }

        val current = RelationLevels.index(all, points)
        val selected = if (pickedLevel in offset until offset + group.size) pickedLevel
        else if (current in offset until offset + group.size) current else offset

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        group.forEachIndexed { i, lv ->
            val gi = offset + i
            val on = gi == selected
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL

            val badge = text(lv.mark, if (lv.mark.length > 2) 15f else 20f, Color.WHITE, true)
            badge.gravity = Gravity.CENTER
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(lv.color))
                setStroke(d(if (on) 4 else 2), Color.parseColor(if (on) "#FFE27A" else "#FFFFFF"))
            }
            badge.alpha = if (gi <= current) 1f else 0.7f
            col.addView(badge, LinearLayout.LayoutParams(d(56), d(56)))

            val l = text(lv.label, if (group.size > 3) 15f else 14f,
                if (on) Color.parseColor("#FFE27A") else Color.parseColor("#FFD3E4"), true)
            l.gravity = Gravity.CENTER
            l.setPadding(0, d(6), 0, 0)
            col.addView(l)

            col.setOnClickListener { pickedLevel = gi; render() }
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(row)

        val need = all[selected].min
        val cap = text(
            "Syarat: ≥ $need poin keharmonisan  •  Poinmu: $points",
            12f, Color.WHITE
        )
        cap.gravity = Gravity.CENTER
        cap.setPadding(0, d(10), 0, d(8))
        card.addView(cap)

        var i = 0
        while (i < rights.size) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_HORIZONTAL
            for (j in i until i + 3) {
                if (j >= rights.size) {
                    if (rights.size >= 3) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    continue
                }
                val (emoji, label) = rights[j]
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                val tile = text(emoji, 34f)
                tile.gravity = Gravity.CENTER
                tile.background = fill(Color.parseColor("#2AFFFFFF"), 12, t.border, 2)
                cell.addView(tile, LinearLayout.LayoutParams(d(86), d(86)))
                val l = text(label, 12f, Color.WHITE, true)
                l.gravity = Gravity.CENTER
                l.setPadding(0, d(6), 0, 0)
                cell.addView(l, LinearLayout.LayoutParams(d(96), -2))
                cell.setOnClickListener { toast("Hak ini akan dibuka bertahap — segera hadir") }
                line.addView(cell, if (rights.size >= 3) LinearLayout.LayoutParams(0, -2, 1f)
                else LinearLayout.LayoutParams(-2, -2).apply { setMargins(d(10), 0, d(10), 0) })
            }
            card.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
            i += 3
        }
    }
}
