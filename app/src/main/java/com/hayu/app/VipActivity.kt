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

/** Halaman Bangsawan (VIP). SVIP ada di SvipActivity. */
class VipActivity : AppCompatActivity() {

    companion object {
        val VIP_PRICES = intArrayOf(100, 300, 600, 1000, 1500, 2200, 3000)
        val SVIP_POINTS = intArrayOf(1000, 3000, 6000, 10000, 15000, 25000, 40000)

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
        Perk("🎭", "Orang Misterius peringkat", 6)
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
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(48)))

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
            if (sel == 1 && vipLv < 7) sel = vipLv + 1 else if (vipLv >= 7) sel = 7
            render()
        }.addOnFailureListener { toast("Gagal memuat data") }
    }

    private fun render() {
        // tab
        tabRow.removeAllViews()
        for (i in 1..7) {
            val on = i == sel
            tabRow.addView(tv("VIP $i", if (on) 24f else 17f,
                if (on) Color.WHITE else Color.rgb(150, 140, 120), on).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { sel = i; render() }
            }, LinearLayout.LayoutParams(-2, -1))
        }

        content.removeAllViews()
        val unlocked = perks.count { it.minLevel <= sel }

        // hero
        val hero = FrameLayout(this)
        val badge = tv("🛡️", 84f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(70, 56, 28)); setStroke(dp(4), gold)
            }
        }
        hero.addView(badge, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER_HORIZONTAL))
        hero.addView(tv("VIP $sel", 20f, Color.rgb(40, 28, 8), true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(14).toFloat() }
            setPadding(dp(14), dp(2), dp(14), dp(2))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(190)))

        content.addView(tv("✦  Jumlah hak istimewa $unlocked/${perks.size}  ✦", 15f, gold, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(14) })

        // grid hak istimewa (4 kolom)
        var rowLayout: LinearLayout? = null
        perks.forEachIndexed { i, p ->
            if (i % 4 == 0) {
                rowLayout = LinearLayout(this)
                content.addView(rowLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            val lit = p.minLevel <= sel
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                alpha = if (lit) 1f else 0.35f
            }
            cell.addView(tv(p.icon, 28f).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.rgb(60, 50, 34))
                    setStroke(dp(2), if (lit) gold else Color.rgb(90, 80, 60))
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
