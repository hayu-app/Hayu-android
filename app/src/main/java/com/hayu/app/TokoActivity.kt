package com.hayu.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.opensource.svgaplayer.SVGAImageView

/** Toko: kategori di atas, grid kartu 2 kolom, saldo + Isi Ulang di bawah. Milikku = Hiasan Saya. */
class TokoActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var tabHolder: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var tvCoins: TextView

    private var user: DocumentSnapshot? = null
    private var assets: List<SvgaCatalog.Asset> = emptyList()
    private var tab = "car"
    private val players = ArrayList<SVGAImageView>()

    private val tabs = listOf(
        ShopUtil.Tab("car", "🚗", "Kendaraan"),
        ShopUtil.Tab("frame", "🖼️", "Bingkai"),
        ShopUtil.Tab("title", "🏷️", "Gelar"),
        ShopUtil.Tab("room", "🎨", "Tema Ruangan"),
        ShopUtil.Tab("bond", "💞", "Hubungan")
    )

    private fun d(v: Int) = ShopUtil.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = intent.getStringExtra("tab") ?: "car"
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onPause() {
        super.onPause()
        players.forEach { it.stopAnimation() }
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_shop_root)

        root.addView(
            ShopUtil.header(this, "Toko", "👤 Milikku",
                onBack = { finish() },
                onRight = {
                    startActivity(Intent(this, HiasanActivity::class.java).putExtra("tab", tab))
                    finish()
                }),
            LinearLayout.LayoutParams(-1, d(56))
        )

        tabHolder = FrameLayout(this)
        root.addView(tabHolder, LinearLayout.LayoutParams(-1, -2))

        val sv = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(d(10), d(6), d(10), d(16))
        sv.addView(content)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        // bar bawah: saldo + Isi Ulang
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.parseColor("#2E2E30"))
        bar.setPadding(d(20), d(10), d(20), d(10))

        tvCoins = TextView(this)
        tvCoins.text = "🪙 …"
        tvCoins.textSize = 24f
        tvCoins.setTextColor(Color.parseColor("#FFD54F"))
        tvCoins.setTypeface(tvCoins.typeface, Typeface.BOLD)
        bar.addView(tvCoins, LinearLayout.LayoutParams(0, -2, 1f))

        val topUp = TextView(this)
        topUp.text = "Isi Ulang"
        topUp.textSize = 17f
        topUp.gravity = Gravity.CENTER
        topUp.setTextColor(Color.parseColor("#6B4500"))
        topUp.setTypeface(topUp.typeface, Typeface.BOLD)
        topUp.setBackgroundResource(R.drawable.bg_pill_yellow)
        topUp.setOnClickListener { startActivity(Intent(this, WalletActivity::class.java)) }
        bar.addView(topUp, LinearLayout.LayoutParams(d(220), d(48)))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun load() {
        val uid = auth.currentUser?.uid ?: run { finish(); return }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                user = doc
                SvgaCatalog.load(null) { all ->
                    if (isFinishing || isDestroyed) return@load
                    assets = all
                    ShopUtil.cleanExpired(db, uid, doc, all)
                    render()
                }
            }
            .addOnFailureListener { toast("Gagal memuat toko") }
    }

    private fun render() {
        val u = user ?: return
        tvCoins.text = "🪙 " + ShopUtil.compact(u.getLong("coins") ?: 0L)

        tabHolder.removeAllViews()
        tabHolder.addView(ShopUtil.tabBar(this, tabs, tab) { key ->
            tab = key
            render()
        })

        players.forEach { it.stopAnimation() }
        players.clear()
        content.removeAllViews()

        when (tab) {
            "title" -> renderTitles(u)
            "car", "frame" -> renderAssets(u)
            else -> content.addView(
                ShopUtil.emptyState(this, "Segera hadir ✨\nKategori ini belum tersedia di toko.", null) {}
            )
        }
    }

    // ───────── kendaraan & bingkai (SVGA unggahan Developer) ─────────

    private fun renderAssets(u: DocumentSnapshot) {
        val type = ShopUtil.svgaTypeOf(tab) ?: return
        val list = assets.filter { it.type == type }
        if (list.isEmpty()) {
            content.addView(ShopUtil.emptyState(this, "Belum ada item di kategori ini.", null) {})
            return
        }
        val owned = ShopUtil.ownedSet(u)
        val expiry = ShopUtil.expiryMap(u)
        content.addView(ShopUtil.grid(this, list.map { assetCard(it, owned, expiry) }))
    }

    private fun assetCard(a: SvgaCatalog.Asset, owned: Set<String>, expiry: Map<String, Long>): View {
        val itemId = "svga_${a.id}"
        val have = a.price == 0 || ShopUtil.isActive(itemId, owned, expiry)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_shop_card)
        card.setPadding(d(12), d(12), d(12), d(12))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val dur = TextView(this)
        dur.text = "🕒 " + ShopUtil.durationText(a.days)
        dur.textSize = 13f
        dur.setTextColor(Color.parseColor("#B0B0B5"))
        top.addView(dur, LinearLayout.LayoutParams(0, -2, 1f))
        val coba = TextView(this)
        coba.text = "Coba"
        coba.textSize = 13f
        coba.setTextColor(Color.WHITE)
        coba.setBackgroundResource(R.drawable.bg_shop_pill)
        coba.setPadding(d(12), d(3), d(12), d(3))
        coba.setOnClickListener { SvgaOverlay.show(this, a.url) }
        top.addView(coba)
        card.addView(top)

        val stage = FrameLayout(this)
        val fallback = TextView(this)
        fallback.text = a.emoji.ifBlank { if (a.type == SvgaCatalog.FRAME) "🖼️" else "🚗" }
        fallback.textSize = 48f
        fallback.gravity = Gravity.CENTER
        stage.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        val anim = SVGAImageView(this)
        anim.scaleType = ImageView.ScaleType.FIT_CENTER
        anim.isClickable = false
        stage.addView(anim, FrameLayout.LayoutParams(-1, -1))
        players.add(anim)
        SvgaPlayer.play(anim, a.url, 0)
        card.addView(stage, LinearLayout.LayoutParams(-1, d(120)).apply { topMargin = d(8) })

        val price = TextView(this)
        price.text = if (a.price == 0) "Gratis" else "🪙 ${a.price}"
        price.textSize = 19f
        price.gravity = Gravity.CENTER
        price.setTextColor(Color.parseColor("#FFD54F"))
        price.setTypeface(price.typeface, Typeface.BOLD)
        card.addView(price, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        if (have && a.price > 0) {
            val st = TextView(this)
            st.text = "✓ Dimiliki • " + ShopUtil.leftText(expiry[itemId])
            st.textSize = 11f
            st.gravity = Gravity.CENTER
            st.setTextColor(Color.parseColor("#7CF29A"))
            card.addView(st)
        }

        card.setOnClickListener { onAssetClick(a, itemId, have, expiry[itemId]) }
        return card
    }

    private fun onAssetClick(a: SvgaCatalog.Asset, itemId: String, have: Boolean, expiresAt: Long?) {
        if (a.price == 0 || (have && a.days == 0)) {
            toast("Sudah dimiliki — pakai lewat Milikku")
            return
        }
        val verb = if (have) "Perpanjang" else "Beli"
        AlertDialog.Builder(this)
            .setTitle("$verb ${a.name}")
            .setMessage("${ShopUtil.durationText(a.days)} • 🪙 ${a.price}" +
                (if (have) "\n(masa berlaku ditambah dari sisa waktu sekarang)" else ""))
            .setPositiveButton(verb) { _, _ -> buy(itemId, a.name, a.price, a.days) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ───────── gelar ─────────

    private fun renderTitles(u: DocumentSnapshot) {
        val owned = ShopUtil.ownedSet(u)
        val cards = ShopUtil.TITLES.map { t ->
            val have = t.id in owned
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.gravity = Gravity.CENTER_HORIZONTAL
            card.setBackgroundResource(R.drawable.bg_shop_card)
            card.setPadding(d(12), d(18), d(12), d(14))

            val e = TextView(this)
            e.text = t.emoji
            e.textSize = 48f
            e.gravity = Gravity.CENTER
            card.addView(e)

            val n = TextView(this)
            n.text = t.name
            n.textSize = 15f
            n.setTextColor(Color.WHITE)
            n.setTypeface(n.typeface, Typeface.BOLD)
            n.gravity = Gravity.CENTER
            n.setPadding(0, d(8), 0, 0)
            card.addView(n)

            val p = TextView(this)
            p.text = if (have) "✓ Dimiliki" else "🪙 ${t.price}"
            p.textSize = 17f
            p.gravity = Gravity.CENTER
            p.setTextColor(Color.parseColor(if (have) "#7CF29A" else "#FFD54F"))
            p.setTypeface(p.typeface, Typeface.BOLD)
            p.setPadding(0, d(6), 0, 0)
            card.addView(p)

            card.setOnClickListener {
                if (have) toast("Sudah dimiliki — pakai lewat Milikku")
                else AlertDialog.Builder(this)
                    .setTitle("Beli gelar ${t.name}")
                    .setMessage("Permanen • 🪙 ${t.price}")
                    .setPositiveButton("Beli") { _, _ -> buy(t.id, t.name, t.price, 0) }
                    .setNegativeButton("Batal", null)
                    .show()
            }
            card as View
        }
        content.addView(ShopUtil.grid(this, cards))
    }

    private fun buy(itemId: String, name: String, price: Int, days: Int) {
        val uid = auth.currentUser?.uid ?: return
        ShopUtil.buy(db, uid, itemId, price, days,
            onOk = { toast("Berhasil: $name 🎉"); load() },
            onFail = { toast("Gagal: $it") })
    }
}
