package com.hayu.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Halaman SVIP 1-8. Level naik otomatis lewat top up di Dompet (1 koin top up = 1 poin). */
class SvipActivity : AppCompatActivity() {

    private data class Perk(val icon: String, val label: String, val minLevel: Int)

    private val perks = listOf(
        Perk("👑", "SVIP 1", 1), Perk("🎁", "Hadiah Eksklusif", 1), Perk("🔐", "Kunci Ruangan", 1),
        Perk("🌍", "Sembunyikan negara", 2), Perk("📢", "Pengumuman Upgrade", 2),
        Perk("✨", "Sorotan Identitas", 2), Perk("🚪", "Sembunyikan Ruangan", 2),
        Perk("🎊", "Banner Global Hadiah", 3), Perk("🏷️", "Gelar Eksklusif", 3),
        Perk("✍️", "Tanda tangan pribadi", 3), Perk("🖼️", "Desain Custom Bingkai Family", 3),
        Perk("🎨", "Skin Ruangan", 4), Perk("🆔", "Lencana Custom ID", 4),
        Perk("🎞️", "Sampul Ruangan GIF", 4), Perk("🛡️", "Anti-Kick", 4),
        Perk("🚙", "Kendaraan Family", 5), Perk("⛔", "Blokir Akun", 5),
        Perk("✅", "Buka blokir akun", 5), Perk("🧩", "Skin Sampul Ruangan", 5),
        Perk("🪪", "Skin kartu profil eksklusif", 6),
        Perk("🎤", "Cegah penghapusan dari mikrofon dan pembisuan", 6),
        Perk("🔖", "Label SVIP dinamis", 7), Perk("🚫", "Larangan pengguna", 7),
        Perk("💫", "Efek nama panggilan", 7),
        Perk("🦄", "Skin eksklusif SVIP 8", 8)
    )

    private val gold = Color.rgb(251, 191, 36)
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var points = 0L
    private var svLv = 0
    private var sel = 1

    private lateinit var tabRow: LinearLayout
    private lateinit var content: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        val top = FrameLayout(this)
        top.addView(tv("‹", 34f).apply {
            gravity = Gravity.CENTER; setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
        top.addView(tv("SVIP", 18f).apply { typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        top.addView(tv("?", 16f, Color.BLACK).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(gold) }
            setOnClickListener {
                Toast.makeText(this@SvipActivity,
                    "Level SVIP naik lewat top up di Dompet. 1 koin top up = 1 poin.", Toast.LENGTH_LONG).show()
            }
        }, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(14) })
        root.addView(top, LinearLayout.LayoutParams(-1, dp(52)))

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabRow = LinearLayout(this)
        tabScroll.addView(tabRow)
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(52)))

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun onResume() {
        super.onResume()
        VipActivity.loadConfig { loadSvipData() }
    }

    private fun loadSvipData() {
        val uid = auth.currentUser?.uid ?: return finish()
        db.collection("users").document(uid).get().addOnSuccessListener { d ->
            points = d.getLong("svipPoints") ?: 0L
            svLv = VipActivity.svipLevel(points)
            if (sel == 1 && svLv in 1..7) sel = svLv + 1 else if (svLv >= 8) sel = 8
            render()
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
        }
    }

    /** Kartu pratinjau satu hiasan. Ketuk = putar animasinya (kalau ada .svga). Null kalau gambarnya tidak ada. */
    private fun showcase(label: String, path: String, heightDp: Int, svga: String?, lit: Boolean): LinearLayout? {
        if (!VipAssets.exists(this, path)) return null
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 24, 18)); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(150, 110, 30))
            }
            setPadding(dp(8), dp(10), dp(8), dp(8))
            alpha = if (lit) 1f else 0.55f
            if (svga != null && VipAssets.exists(this@SvipActivity, svga)) {
                setOnClickListener { SvgaOverlay.show(this@SvipActivity, VipAssets.PREFIX + svga) }
            }
        }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        VipAssets.load(iv, path)
        c.addView(iv, LinearLayout.LayoutParams(-1, dp(heightDp)))
        c.addView(tv(label, 12f, Color.rgb(230, 215, 170)).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        return c
    }

    private fun render() {
        // tab: badge SVIP 1..8
        tabRow.removeAllViews()
        for (i in 1..8) {
            val on = i == sel
            val h = dp(if (on) 40 else 30)
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = if (on) 1f else 0.55f
                setOnClickListener { sel = i; render() }
            }
            VipAssets.load(iv, "svip/badge/$i.webp")
            tabRow.addView(iv, LinearLayout.LayoutParams((h * 2.26f).toInt(), h).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dp(6), 0, dp(6), 0)
            })
        }

        content.removeAllViews()
        val lit = sel <= svLv

        // banner utama (gambar dari aset, ditampilkan dari bagian atas)
        val sw = resources.displayMetrics.widthPixels
        val heroH = (sw * 1.1f).toInt()
        content.addView(VipAssets.topCrop(this, "svip/banner/$sel.jpg", sw, heroH),
            LinearLayout.LayoutParams(sw, heroH))

        // progres top up
        val need = VipActivity.SVIP_POINTS[sel - 1]
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 20, 14)); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(150, 110, 30))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        if (svLv >= sel) {
            card.addView(tv("SVIP$sel — level ini sudah tercapai ✅", 15f, gold, true).apply { gravity = Gravity.CENTER })
        } else {
            card.addView(tv("Isi ulang lebih banyak koin untuk mencapai SVIP$sel", 13f, Color.rgb(210, 210, 200)))
            card.addView(tv("Poin top up: $points / $need", 14f, gold, true),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (points * 100 / need).toInt().coerceIn(0, 100)
            }
            card.addView(bar, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(8) })
            card.addView(tv("Upgrade", 15f, Color.rgb(70, 40, 0)).apply {
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(20).toFloat() }
                setOnClickListener { startActivity(Intent(this@SvipActivity, WalletActivity::class.java)) }
            }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(10) })
        }
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(10), dp(14), 0) })

        // hiasan
        content.addView(section("Hiasan SVIP$sel"))
        val items = listOfNotNull(
            showcase("Lencana", "svip/badge/$sel.webp", 70, null, lit),
            showcase("Medali", "svip/medal/$sel.webp", 110, "svip/medal/$sel.svga", lit),
            showcase("Bingkai Avatar", "svip/frame/$sel.webp", 110, "svip/frame/$sel.svga", lit),
            showcase("Bubble Chat", "svip/chat/$sel.webp", 90, null, lit),
            showcase("Ring Mic", "svip/ring/$sel.webp", 100, "svip/ring/$sel.svga", lit),
            showcase("Bingkai Mic", "svip/mic/$sel.webp", 80, "svip/mic/$sel.svga", lit),
            showcase("Efek Masuk", "svip/entry/$sel.webp", 90, "svip/entry/$sel.svga", lit),
            showcase("Efek Ruangan", "svip/room/$sel.png", 110, "svip/room/$sel.svga", lit),
            showcase("Flyer", "svip/flyer/$sel.webp", 70, "svip/flyer/$sel.svga", lit),
            showcase("Kartu Profil", "svip/profile/$sel.webp", 160, "svip/profile/$sel.svga", lit)
        )
        items.chunked(2).forEach { pair ->
            val r = LinearLayout(this).apply { setPadding(dp(10), 0, dp(10), 0) }
            pair.forEach { r.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            if (pair.size == 1) r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(r, LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(tv("Ketuk hiasan untuk melihat animasinya", 11f, Color.rgb(140, 135, 120)).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        // Hak istimewa (3 kolom)
        content.addView(section("Hak Istimewa"))
        var rowLayout: LinearLayout? = null
        perks.forEachIndexed { i, p ->
            if (i % 3 == 0) {
                rowLayout = LinearLayout(this).apply { setPadding(dp(8), 0, dp(8), 0) }
                content.addView(rowLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            }
            val on = p.minLevel <= sel
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            }
            cell.addView(tv(if (on) p.icon else "🔒", 30f).apply {
                gravity = Gravity.CENTER; alpha = if (on) 1f else 0.55f
                background = GradientDrawable().apply {
                    setColor(Color.rgb(28, 24, 18)); cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (on) Color.rgb(150, 110, 30) else Color.rgb(60, 56, 50))
                }
            }, LinearLayout.LayoutParams(dp(68), dp(68)))
            cell.addView(tv(p.label, 12f, if (on) gold else Color.rgb(120, 118, 112)).apply {
                gravity = Gravity.CENTER; typeface = Typeface.DEFAULT
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            rowLayout!!.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        val rem = perks.size % 3
        if (rem != 0) repeat(3 - rem) { rowLayout!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
        content.addView(View(this), LinearLayout.LayoutParams(-1, dp(24)))
    }

    private fun section(title: String) = tv("— $title —", 22f, gold, true).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(18), 0, dp(14))
    }
}
