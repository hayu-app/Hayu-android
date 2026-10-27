package com.hayu.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import coil.load
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.opensource.svgaplayer.SVGAImageView

/** "Hiasan Saya" (Milikku): item yang dimiliki per kategori, bisa dipakai / dilepas. */
class HiasanActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var tabHolder: FrameLayout
    private lateinit var content: LinearLayout

    private var user: DocumentSnapshot? = null
    private var assets: List<SvgaCatalog.Asset> = emptyList()
    private var clearedFields: Set<String> = emptySet()
    private var tab = "car"
    private val players = ArrayList<SVGAImageView>()

    // Hanya Kendaraan, Bingkai, dan Gelar yang sudah punya data; sisanya tampil kosong.
    private val tabs = listOf(
        ShopUtil.Tab("car", "🚗", "Kendaraan"),
        ShopUtil.Tab("frame", "🖼️", "Bingkai"),
        ShopUtil.Tab("room", "🎨", "Tema Ruangan"),
        ShopUtil.Tab("bubble", "💬", "Bubble"),
        ShopUtil.Tab("profilfx", "🎁", "Efek Profil"),
        ShopUtil.Tab("card", "🎴", "Kartu Profil"),
        ShopUtil.Tab("skin", "🪑", "Skin Ruangan"),
        ShopUtil.Tab("momen", "👕", "Tema Momen"),
        ShopUtil.Tab("entry", "🏅", "Efek Masuk"),
        ShopUtil.Tab("wave", "🔊", "Gelombang suara"),
        ShopUtil.Tab("title", "🏷️", "Gelar")
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

    private fun goShop() {
        startActivity(Intent(this, TokoActivity::class.java).putExtra("tab", tab))
        finish()
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_shop_root)

        root.addView(
            ShopUtil.header(this, "Hiasan Saya", "🏬 Toko",
                onBack = { finish() }, onRight = { goShop() }),
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
                    clearedFields = ShopUtil.cleanExpired(db, uid, doc, all)
                    render()
                }
            }
            .addOnFailureListener { toast("Gagal memuat") }
    }

    private fun render() {
        val u = user ?: return
        tabHolder.removeAllViews()
        tabHolder.addView(ShopUtil.tabBar(this, tabs, tab) { key ->
            tab = key
            render()
        })
        players.forEach { it.stopAnimation() }
        players.clear()
        content.removeAllViews()

        val label = tabs.firstOrNull { it.key == tab }?.label ?: ""
        val cards: List<View> = when (tab) {
            "car" -> assetCards(u)
            "frame" -> assetCards(u) + vipFrameCards(u)
            "title" -> titleCards(u)
            else -> perkCards(u)
        }
        if (cards.isEmpty()) {
            val lower = label.lowercase()
            content.addView(
                ShopUtil.emptyState(
                    this,
                    "Belum ada $lower yang tersedia sekarang. Dengan $lower Anda akan terlihat lebih menarik di dalam ruangan",
                    "Pergi membeli"
                ) { goShop() }
            )
        } else {
            content.addView(ShopUtil.grid(this, cards))
        }
    }

    private fun assetCards(u: DocumentSnapshot): List<View> {
        val type = ShopUtil.svgaTypeOf(tab) ?: return emptyList()
        val field = ShopUtil.equippedField(tab) ?: return emptyList()
        val owned = ShopUtil.ownedSet(u)
        val expiry = ShopUtil.expiryMap(u)
        val equippedUrl = if (field in clearedFields) "" else (u.getString("${field}Url") ?: "")

        return assets
            .filter { it.type == type && (it.price == 0 || ShopUtil.isActive("svga_${it.id}", owned, expiry)) }
            .map { a ->
                val on = equippedUrl == a.url
                val status = if (a.price == 0) "Gratis" else ShopUtil.leftText(expiry["svga_${a.id}"])
                itemCard(
                    status = status,
                    emoji = a.emoji.ifBlank { if (a.type == SvgaCatalog.FRAME) "🖼️" else "🚗" },
                    name = a.name,
                    svgaUrl = a.url,
                    on = on
                ) { if (on) equipSvga(field, "", "") else equipSvga(field, a.url, a.name) }
            }
    }

    private fun titleCards(u: DocumentSnapshot): List<View> = shopTitleCards(u) + vipTitleCards(u)

    private fun shopTitleCards(u: DocumentSnapshot): List<View> {
        val owned = ShopUtil.ownedSet(u)
        val equipped = u.getString("equippedTitle") ?: ""
        return ShopUtil.TITLES.filter { it.id in owned }.map { t ->
            val on = equipped == t.label
            itemCard("Permanen", t.emoji, t.name, null, on) {
                val uid = auth.currentUser?.uid ?: return@itemCard
                db.collection("users").document(uid)
                    .update("equippedTitle", if (on) "" else t.label)
                    .addOnSuccessListener { load() }
                    .addOnFailureListener { toast("Gagal: ${it.message}") }
            }
        }
    }

    private fun itemCard(
        status: String, emoji: String, name: String, svgaUrl: String?, on: Boolean,
        imgUrl: String? = null, onToggle: (() -> Unit)?
    ): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_shop_card)
        card.setPadding(d(12), d(12), d(12), d(12))

        val st = TextView(this)
        st.text = "🕒 $status"
        st.textSize = 13f
        st.setTextColor(Color.parseColor("#B0B0B5"))
        card.addView(st)

        val stage = FrameLayout(this)
        val fb = TextView(this)
        fb.text = emoji
        fb.textSize = 48f
        fb.gravity = Gravity.CENTER
        stage.addView(fb, FrameLayout.LayoutParams(-1, -1))
        if (svgaUrl != null) {
            val anim = SVGAImageView(this)
            anim.scaleType = ImageView.ScaleType.FIT_CENTER
            anim.isClickable = false
            stage.addView(anim, FrameLayout.LayoutParams(-1, -1))
            players.add(anim)
            SvgaPlayer.play(anim, svgaUrl, 0)
        }
        if (imgUrl != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.load(imgUrl)
            stage.addView(iv, FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(stage, LinearLayout.LayoutParams(-1, d(110)).apply { topMargin = d(6) })

        val n = TextView(this)
        n.text = name
        n.textSize = 14f
        n.gravity = Gravity.CENTER
        n.maxLines = 1
        n.setTextColor(Color.WHITE)
        n.setTypeface(n.typeface, Typeface.BOLD)
        card.addView(n, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })

        val btn = TextView(this)
        btn.text = if (onToggle == null) "Aktif otomatis ✓" else if (on) "Dipakai • Lepas" else "Pakai"
        btn.textSize = 14f
        btn.gravity = Gravity.CENTER
        btn.setTextColor(Color.WHITE)
        btn.setTypeface(btn.typeface, Typeface.BOLD)
        btn.setBackgroundResource(if (on || onToggle == null) R.drawable.bg_shop_pill else R.drawable.bg_button_orange)
        btn.setOnClickListener { onToggle?.invoke() }
        card.addView(btn, LinearLayout.LayoutParams(-1, d(38)).apply { topMargin = d(8) })
        return card
    }

    // ───────── hadiah VIP / SVIP (otomatis dimiliki sesuai level) ─────────

    private fun vipFrameCards(u: DocumentSnapshot): List<View> {
        val equippedUrl = if ("equippedFrame" in clearedFields) "" else (u.getString("equippedFrameUrl") ?: "")
        val out = ArrayList<View>()
        for ((tier, lvl) in listOf("svip" to VipAssets.svipLevel(u), "vip" to VipAssets.vipLevel(u))) {
            for (l in 1..lvl) {
                val path = "$tier/frame/$l.svga"
                if (!VipAssets.exists(this, path)) continue
                val url = "${VipAssets.PREFIX}$path"
                val on = equippedUrl == url
                val name = "Bingkai ${tier.uppercase()} $l"
                out.add(itemCard("Dari ${tier.uppercase()} $l", "🖼️", name, url, on) {
                    if (on) equipSvga("equippedFrame", "", "") else equipSvga("equippedFrame", url, name)
                })
            }
        }
        return out
    }

    private fun setTitle(label: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("equippedTitle", label)
            .addOnSuccessListener { load() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun vipTitleCards(u: DocumentSnapshot): List<View> {
        val equipped = u.getString("equippedTitle") ?: ""
        val defs = ArrayList<Triple<String, String, String>>()
        for (l in 1..VipAssets.vipLevel(u)) defs.add(Triple("⭐ VIP$l", "⭐", "VIP $l"))
        for (l in 1..VipAssets.svipLevel(u)) defs.add(Triple("👑 SVIP$l", "👑", "SVIP $l"))
        return defs.map { (label, emoji, name) ->
            val on = equipped == label
            itemCard("Dari $name", emoji, "Gelar $name", null, on) { setTitle(if (on) "" else label) }
        }
    }

    // tier, folder, ekstensi
    private fun perkSpecs(): List<Triple<String, String, String>> = when (tab) {
        "entry" -> listOf(Triple("svip", "entry", "svga"), Triple("vip", "entry", "webp"))
        "room" -> listOf(Triple("svip", "room", "svga"))
        "bubble" -> listOf(Triple("svip", "chat", "webp"))
        "profilfx" -> listOf(Triple("svip", "profile", "svga"))
        "wave" -> listOf(
            Triple("svip", "ring", "svga"), Triple("svip", "mic", "svga"), Triple("vip", "mic", "svga")
        )
        else -> emptyList()
    }

    private fun perkCards(u: DocumentSnapshot): List<View> {
        val labels = mapOf(
            "entry" to "Efek Masuk", "room" to "Tema Ruangan", "chat" to "Bubble Chat",
            "profile" to "Efek Profil", "ring" to "Ring Mic", "mic" to "Efek Mic"
        )
        val out = ArrayList<View>()
        for ((tier, folder, ext) in perkSpecs()) {
            val lvl = if (tier == "svip") VipAssets.svipLevel(u) else VipAssets.vipLevel(u)
            for (l in 1..lvl) {
                val path = "$tier/$folder/$l.$ext"
                if (!VipAssets.exists(this, path)) continue
                val svga = if (ext == "svga") "${VipAssets.PREFIX}$path" else null
                val img = if (ext != "svga") VipAssets.url(path) else null
                out.add(
                    itemCard(
                        status = "Dari ${tier.uppercase()} $l",
                        emoji = "🎁",
                        name = "${labels[folder] ?: folder} ${tier.uppercase()} $l",
                        svgaUrl = svga,
                        on = false,
                        imgUrl = img,
                        onToggle = null
                    )
                )
            }
        }
        return out
    }

    private fun equipSvga(field: String, url: String, name: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update(mapOf("${field}Url" to url, "${field}Name" to name))
            .addOnSuccessListener { clearedFields = clearedFields - field; load() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
