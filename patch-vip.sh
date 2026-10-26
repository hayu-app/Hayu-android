#!/data/data/com.termux/files/usr/bin/bash
# HAYU - VIP 1-8 & SVIP 1-8: logo/tag, medali, bingkai, banner, efek masuk, dll.
#  Taruh vip.zip dan svip.zip di folder Download, lalu:
#   cd ~/Hayu-android && bash patch-vip.sh
set -e
D="app/src/main/java/com/hayu/app"
A="app/src/main/assets"
[ -d "$D" ] || { echo "Jalankan dari root repo Hayu-android"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python
command -v unzip >/dev/null 2>&1 || pkg install -y unzip

find_zip() {
  for p in "$HOME/storage/downloads/$1" "$HOME/storage/shared/Download/$1" "$HOME/$1" "./$1"; do
    [ -f "$p" ] && { echo "$p"; return; }
  done
  echo ""
}
VZ="${VIP_ZIP:-$(find_zip vip.zip)}"
SZ="${SVIP_ZIP:-$(find_zip svip.zip)}"
[ -n "$VZ" ] && [ -n "$SZ" ] || { echo "vip.zip / svip.zip tidak ketemu di Download (jalankan: termux-setup-storage)"; exit 1; }

T="$(mktemp -d)"

echo "▶ 1/4 Salin aset ke $A"
cat > "$T/assets_map.py" <<'PYEOF'
import os, re, sys, shutil, zipfile, urllib.parse

# sumber (folder hasil unzip) -> tujuan di assets
VIP_CATS = {
    'VIP Tags': 'tag', 'VIP Medals': 'medal', 'VIP Frames': 'frame',
    'VIP Mic Outer Frames': 'mic', 'VIP Entry Effects': 'entry', 'VIP Profile Header': 'header',
}
SVIP_CATS = {
    'room': 'room', 'badges': 'badge', 'chat': 'chat', 'entry': 'entry', 'flyers': 'flyer',
    'frames': 'frame', 'medals': 'medal', 'mic': 'mic', 'profile': 'profile', 'rings': 'ring',
}

def lvl(name):
    m = re.search(r'(\d+)', name)
    return m.group(1) if m else None

def copy(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)

def run(vip_dir, svip_dir, out):
    n = 0
    for root, _, files in os.walk(vip_dir):
        for f in files:
            src = os.path.join(root, f)
            ext = f.rsplit('.', 1)[-1].lower()
            parts = [urllib.parse.unquote(p) for p in os.path.relpath(root, vip_dir).split(os.sep)]
            cat = next((VIP_CATS[p] for p in parts if p in VIP_CATS), None)
            if cat:
                l = lvl(f)
                if l:
                    copy(src, f'{out}/vip/{cat}/{l}.{ext}'); n += 1
            elif f == 'vip_hero_arch.webp':
                copy(src, f'{out}/vip/hero_arch.webp'); n += 1
            elif f.startswith('vip_privilege_row'):
                copy(src, f'{out}/vip/{f.replace("vip_privilege_row_", "priv_")}'); n += 1
    for root, _, files in os.walk(svip_dir):
        for f in files:
            src = os.path.join(root, f)
            ext = f.rsplit('.', 1)[-1].lower()
            folder = os.path.basename(root)
            if folder in SVIP_CATS:
                l = lvl(f)
                if l:
                    copy(src, f'{out}/svip/{SVIP_CATS[folder]}/{l}.{ext}'); n += 1
            elif re.fullmatch(r'svip_\d+\.jpg', f):
                copy(src, f'{out}/svip/banner/{lvl(f)}.jpg'); n += 1
    return n

if __name__ == '__main__':
    vz, sz, out = sys.argv[1:4]
    tmp = out + '/.tmp_unzip'
    shutil.rmtree(tmp, ignore_errors=True)
    for z, name in ((vz, 'v'), (sz, 's')):
        with zipfile.ZipFile(z) as zf:
            zf.extractall(f'{tmp}/{name}')
    n = run(f'{tmp}/v', f'{tmp}/s', out)
    shutil.rmtree(tmp, ignore_errors=True)
    print(f'{n} file aset disalin ke {out}')
PYEOF
rm -rf "$A/vip" "$A/svip"
mkdir -p "$A"
python3 "$T/assets_map.py" "$VZ" "$SZ" "$A"

echo "▶ 2/4 Kode baru"
cat > "$D/VipAssets.kt" <<'KTEOF'
package com.hayu.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.ViewGroup
import android.widget.ImageView
import coil.load
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Aset VIP & SVIP (logo, medali, bingkai, banner, dll) yang ada di app/src/main/assets/{vip,svip}.
 * Animasi .svga diputar lewat SvgaPlayer dengan URL "asset://vip/frame/3.svga".
 */
object VipAssets {
    const val PREFIX = "asset://"
    private const val ANDROID_ASSET = "file:///android_asset/"
    const val MAX_LEVEL = 8

    fun vipLevel(doc: DocumentSnapshot): Int =
        (doc.getLong("vipLevel") ?: 0L).toInt().coerceIn(0, MAX_LEVEL)

    fun svipLevel(doc: DocumentSnapshot): Int =
        VipActivity.svipLevel(doc.getLong("svipPoints") ?: 0L).coerceIn(0, MAX_LEVEL)

    /** Bingkai yang tampil: bingkai pasangan sendiri dulu; kalau kosong pakai bingkai bawaan SVIP / VIP. */
    fun frameUrl(doc: DocumentSnapshot): String {
        val own = doc.getString("equippedFrameUrl") ?: ""
        if (own.isNotEmpty()) return own
        val sv = svipLevel(doc)
        if (sv > 0) return "${PREFIX}svip/frame/$sv.svga"
        val v = vipLevel(doc)
        if (v > 0) return "${PREFIX}vip/frame/$v.svga"
        return ""
    }

    /** Efek masuk room (hanya SVIP yang punya animasi .svga). */
    fun entryUrl(doc: DocumentSnapshot): String {
        val sv = svipLevel(doc)
        return if (sv > 0) "${PREFIX}svip/entry/$sv.svga" else ""
    }

    fun tierName(doc: DocumentSnapshot): String {
        val sv = svipLevel(doc)
        if (sv > 0) return "SVIP $sv"
        val v = vipLevel(doc)
        return if (v > 0) "VIP $v" else ""
    }

    fun exists(ctx: Context, path: String): Boolean = try {
        ctx.assets.open(path).close()
        true
    } catch (e: Exception) {
        false
    }

    fun load(view: ImageView, path: String) {
        view.load(ANDROID_ASSET + path)
    }

    private fun image(ctx: Context, path: String, w: Int, h: Int): ImageView {
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.layoutParams = ViewGroup.LayoutParams(w, h)
        load(iv, path)
        return iv
    }

    /** Tag "VIP n" untuk deretan badge (tinggi 24dp). */
    fun vipTag(ctx: Context, level: Int, heightDp: Int = 24): ImageView {
        val d = ctx.resources.displayMetrics.density
        val h = (heightDp * d).toInt()
        return image(ctx, "vip/tag/${level.coerceIn(1, MAX_LEVEL)}.webp", (h * 2.35f).toInt(), h)
    }

    /** Badge "SVIP n" untuk deretan badge (tinggi 26dp). */
    fun svipBadge(ctx: Context, level: Int, heightDp: Int = 26): ImageView {
        val d = ctx.resources.displayMetrics.density
        val h = (heightDp * d).toInt()
        return image(ctx, "svip/badge/${level.coerceIn(1, MAX_LEVEL)}.webp", (h * 2.26f).toInt(), h)
    }

    /** Gambar tinggi (banner) ditampilkan dari bagian ATAS, lebar penuh. */
    fun topCrop(ctx: Context, path: String, widthPx: Int, heightPx: Int): ImageView {
        val iv = ImageView(ctx)
        iv.layoutParams = ViewGroup.LayoutParams(widthPx, heightPx)
        try {
            ctx.assets.open(path).use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                if (bmp != null) {
                    iv.scaleType = ImageView.ScaleType.MATRIX
                    iv.setImageBitmap(bmp)
                    val s = widthPx.toFloat() / bmp.width
                    iv.imageMatrix = Matrix().apply { setScale(s, s) }
                }
            }
        } catch (e: Exception) {
            // aset tidak ada -> biarkan kosong
        }
        return iv
    }
}
KTEOF
cat > "$D/VipActivity.kt" <<'KTEOF'
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
KTEOF
cat > "$D/SvipActivity.kt" <<'KTEOF'
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
KTEOF

echo "▶ 3/4 Pasang ke profil, room, dan SVGA"
cat > "$T/patch_kt.py" <<'PYEOF'
import sys, os
D = sys.argv[1]

def rd(n):
    return open(os.path.join(D, n), encoding='utf-8').read()

edits = {}  # nama file -> isi baru

def patch(name, pairs):
    s = rd(name)
    if 'VipAssets' in s:
        print(f'- {name}: sudah terpasang, dilewati')
        return
    for a, b in pairs:
        if s.count(a) != 1:
            print(f'GAGAL: {name}: bagian kode tidak ditemukan/ganda:\n{a}')
            sys.exit(1)
        s = s.replace(a, b)
    edits[name] = s

patch('SvgaPlayer.kt', [(
    '            if (SvgaStore.isStored(url)) {',
    '            if (url.startsWith(VipAssets.PREFIX)) {\n'
    '                // aset bawaan app (VIP / SVIP) di folder assets\n'
    '                parser.decodeFromAssets(url.removePrefix(VipAssets.PREFIX), completion)\n'
    '            } else if (SvgaStore.isStored(url)) {'
)])

patch('ProfileFragment.kt', [
    ('SvgaFrame.attach(imgAvatar, doc.getString("equippedFrameUrl"))',
     'SvgaFrame.attach(imgAvatar, VipAssets.frameUrl(doc))'),
    ('ProfileTheme.chip(requireContext(), "⭐ VIP $vipLv", Color.parseColor("#7C3AED"))',
     'VipAssets.vipTag(requireContext(), vipLv)'),
    ('ProfileTheme.chip(requireContext(), "👑 SVIP $svipLv", Color.parseColor("#B45309"))',
     'VipAssets.svipBadge(requireContext(), svipLv)'),
])

patch('RoomActivity.kt', [
    ('myFrameUrl = userDoc.getString("equippedFrameUrl") ?: ""',
     'myFrameUrl = VipAssets.frameUrl(userDoc)'),
    ('u.getString("equippedFrameUrl") ?: ""',
     'VipAssets.frameUrl(u)'),
    ('liveFrames[uid] = doc.getString("equippedFrameUrl") ?: ""',
     'liveFrames[uid] = VipAssets.frameUrl(doc)'),
    ('        val url = userDoc.getString("equippedCarUrl") ?: ""\n        if (url.isEmpty()) return',
     '        val carUrl = userDoc.getString("equippedCarUrl") ?: ""\n'
     '        val url = if (carUrl.isNotEmpty()) carUrl else VipAssets.entryUrl(userDoc)\n'
     '        if (url.isEmpty()) return'),
    ('        val carName = userDoc.getString("equippedCarName") ?: "kendaraan"',
     '        val carName = if (carUrl.isNotEmpty()) (userDoc.getString("equippedCarName") ?: "kendaraan") else VipAssets.tierName(userDoc)'),
])

for n, s in edits.items():
    open(os.path.join(D, n), 'w', encoding='utf-8').write(s)
    print(f'OK: {n} diperbarui')
PYEOF
python3 "$T/patch_kt.py" "$D"
rm -rf "$T"

echo "▶ 4/4 Commit & push"
BR=$(git rev-parse --abbrev-ref HEAD)
git config http.postBuffer 524288000
git add "$A" "$D/VipAssets.kt" "$D/VipActivity.kt" "$D/SvipActivity.kt" "$D/SvgaPlayer.kt" "$D/ProfileFragment.kt" "$D/RoomActivity.kt"
git commit -m "feat(vip): VIP 1-8 & SVIP 1-8 lengkap dengan logo, medali, bingkai, banner, efek masuk" || echo "(tidak ada perubahan)"
git pull --rebase origin "$BR" || true
git push origin "$BR"
echo "Selesai: dikirim ke origin/$BR"
