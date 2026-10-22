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

/** Halaman SVIP. Level naik otomatis lewat top up di Dompet (1 koin top up = 1 poin). */
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
        Perk("💫", "Efek nama panggilan", 7)
    )

    private val decor = listOf("🏅" to "Lencana", "🦌" to "Avatar", "💬" to "Bubble chat", "🎀" to "Banner masuk")

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
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(46)))

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return finish()
        db.collection("users").document(uid).get().addOnSuccessListener { d ->
            points = d.getLong("svipPoints") ?: 0L
            svLv = VipActivity.svipLevel(points)
            if (sel == 1 && svLv in 1..6) sel = svLv + 1 else if (svLv >= 7) sel = 7
            render()
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render() {
        tabRow.removeAllViews()
        for (i in 1..7) {
            val on = i == sel
            tabRow.addView(tv("SVIP$i", if (on) 24f else 19f,
                if (on) gold else Color.rgb(225, 215, 190), true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                setOnClickListener { sel = i; render() }
            }, LinearLayout.LayoutParams(-2, -1))
        }

        content.removeAllViews()

        // hero hijau
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 40, 24), Color.rgb(12, 28, 16), Color.BLACK)
            )
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(tv("SVIP$sel", 40f, gold, true))
        val need = VipActivity.SVIP_POINTS[sel - 1]
        left.addView(tv(
            if (svLv >= sel) "Level ini sudah tercapai ✅"
            else "Isi ulang lebih banyak koin untuk mencapai level SVIP ini\n\nPoin top up: $points / $need",
            13f, Color.rgb(210, 210, 200)).apply { typeface = Typeface.DEFAULT },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        hero.addView(left, FrameLayout.LayoutParams(dp(190), -2, Gravity.START or Gravity.TOP)
            .apply { setMargins(dp(18), dp(24), 0, 0) })
        hero.addView(tv("🦌", 96f).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(dp(150), dp(150), Gravity.END or Gravity.TOP)
                .apply { setMargins(0, dp(14), dp(14), 0) })
        if (svLv < sel) {
            hero.addView(tv("Upgrade", 15f, Color.rgb(70, 40, 0), false).apply {
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(18).toFloat() }
                setOnClickListener { startActivity(Intent(this@SvipActivity, WalletActivity::class.java)) }
            }, FrameLayout.LayoutParams(dp(110), dp(36), Gravity.END or Gravity.BOTTOM)
                .apply { setMargins(0, 0, dp(40), dp(18)) })
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(260)))

        content.addView(tv("SVIP$sel", 26f, Color.rgb(230, 200, 90), true).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(10, 50, 28))
        }, LinearLayout.LayoutParams(-1, dp(46)))

        // Hiasan
        content.addView(section("Hiasan"))
        val decorRow = LinearLayout(this).apply { setPadding(dp(14), 0, dp(14), 0) }
        decor.forEach { (icon, name) ->
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.rgb(54, 44, 24)); cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(110, 90, 40))
                }
            }
            c.addView(tv(icon, 34f).apply { gravity = Gravity.CENTER })
            c.addView(tv(name, 11f, Color.rgb(230, 215, 170)).apply {
                gravity = Gravity.CENTER; typeface = Typeface.DEFAULT
            })
            decorRow.addView(c, LinearLayout.LayoutParams(0, dp(96), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        content.addView(decorRow)

        // Hak istimewa (3 kolom)
        content.addView(section("Hak Istimewa"))
        var rowLayout: LinearLayout? = null
        perks.forEachIndexed { i, p ->
            if (i % 3 == 0) {
                rowLayout = LinearLayout(this).apply { setPadding(dp(8), 0, dp(8), 0) }
                content.addView(rowLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            }
            val lit = p.minLevel <= sel
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            }
            cell.addView(tv(if (lit) p.icon else "🔒", 30f).apply {
                gravity = Gravity.CENTER; alpha = if (lit) 1f else 0.55f
                background = GradientDrawable().apply {
                    setColor(Color.rgb(28, 24, 18)); cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (lit) Color.rgb(150, 110, 30) else Color.rgb(60, 56, 50))
                }
            }, LinearLayout.LayoutParams(dp(68), dp(68)))
            cell.addView(tv(p.label, 12f, if (lit) gold else Color.rgb(120, 118, 112)).apply {
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
