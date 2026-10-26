package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.opensource.svgaplayer.SVGAImageView

/** Halaman Bangsawan (VIP 1-8). SVIP ada di SvipActivity. */
class VipActivity : AppCompatActivity() {

    companion object {
        val VIP_PRICES = intArrayOf(100, 300, 600, 1000, 1500, 2200, 3000, 4500)
        val SVIP_POINTS = intArrayOf(1000, 3000, 6000, 10000, 15000, 25000, 40000, 60000)

        fun svipLevel(points: Long): Int {
            var lv = 0
            for (i in SVIP_POINTS.indices) if (points >= SVIP_POINTS[i]) lv = i + 1
            return lv
        }
    }

    private data class Perk(val icon: String, val label: String, val minLevel: Int)

    private val perks = listOf(
        Perk("🏅", "Ikon Baron", 1), Perk("🏆", "Peringkat lebih tinggi", 1),
        Perk("✏️", "Edit postingan", 1), Perk("📅", "Cek kehadiran", 1),
        Perk("🚀", "Percepatan Upgrade", 1), Perk("🐥", "Emoji VIP", 1),
        Perk("🖼️", "Gambar GIF", 1), Perk("🏠", "Ruang teman", 1),
        Perk("🚗", "Kendaraan", 2), Perk("🎲", "Dadu emas", 2),
        Perk("🎁", "Hadiah eksklusif", 2), Perk("🎨", "Unggah tema ruangan", 2),
        Perk("🔇", "Perlindungan mute", 3), Perk("🕵️", "Masuk ke ruangan secara diam-diam", 3),
        Perk("🎤", "Mikrofon eksklusif", 4), Perk("🔤", "Nama pengguna yang unik", 4),
        Perk("🧧", "Kulit Amplop Khusus", 5), Perk("💬", "Obrolan pribadi dengan orang asing", 5),
        Perk("🎭", "Orang Misterius peringkat", 6),
        Perk("🌟", "Efek masuk eksklusif", 7), Perk("🔥", "Bingkai & medali Legend", 8)
    )

    private val gold = Color.rgb(240, 205, 130)
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var vipLv = 0
    private var coins = 0
    private var sel = 1

    private lateinit var tabRow: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var buyBtn: TextView
    private lateinit var sendBtn: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(48, 38, 18), Color.rgb(22, 18, 10), Color.rgb(14, 12, 8))
            )
        }
        setContentView(root)

        val top = FrameLayout(this)
        top.addView(tv("‹", 34f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
        top.addView(tv("Bangsawan", 18f, Color.WHITE, true),
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(52)))

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabRow = LinearLayout(this)
        tabScroll.addView(tabRow)
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(52)))

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(16))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // bar bawah
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(52, 42, 24))
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(),
                    dp(22).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        statusText = tv("", 14f, Color.rgb(200, 190, 160))
        bar.addView(statusText)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        priceText = tv("", 17f, gold, true)
        row.addView(priceText, LinearLayout.LayoutParams(0, -2, 1f))
        sendBtn = tv("Kirim", 18f, gold, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); setStroke(dp(2), gold); cornerRadius = dp(26).toFloat()
            }
            setOnClickListener { sendVip() }
        }
        buyBtn = tv("Beli", 18f, Color.rgb(60, 40, 10), true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(250, 230, 170), Color.rgb(214, 178, 100))
            ).apply { cornerRadius = dp(26).toFloat() }
            setOnClickListener { buyVip() }
        }
        row.addView(sendBtn, LinearLayout.LayoutParams(dp(110), dp(48)).apply { rightMargin = dp(10) })
        row.addView(buyBtn, LinearLayout.LayoutParams(dp(110), dp(48)))
        bar.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val uid = auth.currentUser?.uid ?: return finish()
        db.collection("users").document(uid).get().addOnSuccessListener { d ->
            vipLv = (d.getLong("vipLevel") ?: 0L).toInt()
            coins = (d.getLong("coins") ?: 0L).toInt()
            if (sel == 1 && vipLv < 8) sel = vipLv + 1 else if (vipLv >= 8) sel = 8
            render()
        }.addOnFailureListener { toast("Gagal memuat data") }
    }

    private fun section(title: String) = tv("✦  $title  ✦", 16f, gold, true).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(10))
    }

    /** Kartu pratinjau satu hiasan. Ketuk = putar animasi (kalau ada .svga). Null kalau gambarnya tidak ada. */
    private fun showcase(label: String, path: String, heightDp: Int, svga: String?, lit: Boolean): LinearLayout? {
        if (!VipAssets.exists(this, path)) return null
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(44, 36, 20)); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(110, 90, 40))
            }
            setPadding(dp(8), dp(10), dp(8), dp(8))
            alpha = if (lit) 1f else 0.55f
            if (svga != null) setOnClickListener { SvgaOverlay.show(this@VipActivity, VipAssets.PREFIX + svga) }
        }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        VipAssets.load(iv, path)
        c.addView(iv, LinearLayout.LayoutParams(-1, dp(heightDp)))
        c.addView(tv(label, 12f, gold).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        return c
    }

    private fun render() {
        // tab: tag VIP 1..8
        tabRow.removeAllViews()
        for (i in 1..8) {
            val on = i == sel
            val h = dp(if (on) 38 else 28)
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = if (on) 1f else 0.55f
                setOnClickListener { sel = i; render() }
            }
            VipAssets.load(iv, "vip/tag/$i.webp")
            tabRow.addView(iv, LinearLayout.LayoutParams((h * 2.35f).toInt(), h).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dp(8), 0, dp(8), 0)
            })
        }

        content.removeAllViews()
        val unlocked = perks.count { it.minLevel <= sel }
        val lit = sel <= vipLv

        // hero: lengkung emas + medali (animasi)
        val hero = FrameLayout(this)
        val arch = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0.9f }
        VipAssets.load(arch, "vip/hero_arch.webp")
        hero.addView(arch, FrameLayout.LayoutParams(-1, -1))
        val medalParams = { FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER_HORIZONTAL or Gravity.TOP)
            .apply { topMargin = dp(44) } }
        val medalStatic = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        VipAssets.load(medalStatic, "vip/medal/$sel.webp")
        hero.addView(medalStatic, medalParams())
        val medal = SVGAImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        hero.addView(medal, medalParams())
        SvgaPlayer.play(medal, VipAssets.PREFIX + "vip/medal/$sel.svga", 0)
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(330)))

        val tag = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        VipAssets.load(tag, "vip/tag/$sel.webp")
        content.addView(tag, LinearLayout.LayoutParams(dp(120), dp(50)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // hiasan
        content.addView(section("Hiasan VIP $sel"))
        val cells = listOfNotNull(
            showcase("Bingkai Avatar", "vip/frame/$sel.webp", 110, "vip/frame/$sel.svga", lit),
            showcase("Bingkai Mic", "vip/mic/$sel.webp", 110, "vip/mic/$sel.svga", lit)
        )
        if (cells.isNotEmpty()) {
            val r = LinearLayout(this)
            cells.forEach { r.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }
            content.addView(r, LinearLayout.LayoutParams(-1, -2))
        }
        showcase("Efek Masuk", "vip/entry/$sel.webp", 56, null, lit)?.let {
            content.addView(it, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(8), dp(4), 0) })
        }
        showcase("Header Profil", "vip/header/$sel.webp", 150, null, lit)?.let {
            content.addView(it, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(8), dp(4), 0) })
        }

        content.addView(tv("✦  Jumlah hak istimewa $unlocked/${perks.size}  ✦", 15f, gold, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(14) })

        // grid hak istimewa (4 kolom)
        var rowLayout: LinearLayout? = null
        perks.forEachIndexed { i, p ->
            if (i % 4 == 0) {
                rowLayout = LinearLayout(this)
                content.addView(rowLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            val on = p.minLevel <= sel
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                alpha = if (on) 1f else 0.35f
            }
            cell.addView(tv(p.icon, 28f).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.rgb(60, 50, 34))
                    setStroke(dp(2), if (on) gold else Color.rgb(90, 80, 60))
                }
            }, LinearLayout.LayoutParams(dp(62), dp(62)))
            cell.addView(tv(p.label, 11f, gold).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            rowLayout!!.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val rem = perks.size % 4
        if (rem != 0) repeat(4 - rem) { rowLayout!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }

        // bar bawah
        val price = VIP_PRICES[sel - 1]
        statusText.text = if (vipLv == 0) "Anda belum menjadi bangsawan" else "Bangsawan aktif: VIP $vipLv"
        priceText.text = "$price koin"
        buyBtn.text = if (sel <= vipLv) "Dimiliki" else "Beli"
        buyBtn.alpha = if (sel <= vipLv) 0.5f else 1f
    }

    private fun buyVip() {
        val uid = auth.currentUser?.uid ?: return
        if (sel <= vipLv) { toast("VIP $sel sudah dimiliki"); return }
        if (sel != vipLv + 1) { toast("Beli VIP ${vipLv + 1} dulu"); return }
        val level = sel
        val price = VIP_PRICES[level - 1]
        val ref = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val s = tx.get(ref)
            val c = s.getLong("coins") ?: 0L
            val cur = (s.getLong("vipLevel") ?: 0L).toInt()
            if (level != cur + 1) throw IllegalStateException("Beli VIP ${cur + 1} dulu")
            if (c < price) throw IllegalStateException("Koin tidak cukup")
            val upd = hashMapOf<String, Any>("coins" to (c - price), "vipLevel" to level.toLong())
            val title = s.getString("equippedTitle") ?: ""
            if (title.isBlank() || title.startsWith("⭐ VIP")) upd["equippedTitle"] = "⭐ VIP$level"
            tx.update(ref, upd)
            null
        }.addOnSuccessListener { toast("Berhasil! VIP $level aktif 🎉"); reload() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    /** Kirim VIP yang sedang dipilih ke user lain (koin dipotong dari pengirim). */
    private fun sendVip() {
        val uid = auth.currentUser?.uid ?: return
        val level = sel
        val price = VIP_PRICES[level - 1]
        val input = EditText(this).apply {
            hint = "UserID penerima"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("Kirim VIP $level ($price koin)")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Kirim") { _, _ ->
                val target = input.text.toString().trim().toLongOrNull()
                if (target == null) { toast("UserID tidak valid"); return@setPositiveButton }
                db.collection("users").whereEqualTo("userId", target).limit(1).get()
                    .addOnSuccessListener { q ->
                        val doc = q.documents.firstOrNull()
                        if (doc == null) { toast("User tidak ditemukan"); return@addOnSuccessListener }
                        if (doc.id == uid) { toast("Tidak bisa kirim ke diri sendiri"); return@addOnSuccessListener }
                        val me = db.collection("users").document(uid)
                        val to = db.collection("users").document(doc.id)
                        db.runTransaction { tx ->
                            val a = tx.get(me)
                            val b = tx.get(to)
                            val c = a.getLong("coins") ?: 0L
                            if (c < price) throw IllegalStateException("Koin tidak cukup")
                            val tl = (b.getLong("vipLevel") ?: 0L).toInt()
                            if (tl >= level) throw IllegalStateException("Penerima sudah punya VIP $tl")
                            tx.update(me, "coins", c - price)
                            tx.update(to, "vipLevel", level.toLong())
                            null
                        }.addOnSuccessListener { toast("VIP $level terkirim ✅"); reload() }
                            .addOnFailureListener { toast("Gagal: ${it.message}") }
                    }.addOnFailureListener { toast("Gagal mencari user") }
            }.show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
