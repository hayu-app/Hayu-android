package com.hayu.app

import android.content.Context
import android.graphics.Matrix
import android.view.ViewGroup
import android.widget.ImageView
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Aset VIP & SVIP (logo, medali, bingkai, banner, dll).
 *
 * Filenya TIDAK lagi di dalam APK (supaya APK kecil). Letaknya di folder remote-assets/{vip,svip}
 * di repo GitHub dan diambil online lewat baseUrl, lalu di-cache otomatis (Coil & SVGAParser).
 *  - Animasi .svga diputar lewat SvgaPlayer dengan URL logis "asset://vip/frame/3.svga"
 *  - Daftar file yang ada: assets/remote_index.txt (kecil, dibuat otomatis oleh pasang-aset-remote.sh)
 *  - baseUrl bisa diganti tanpa update app: Firestore  app_config/assets  field  baseUrl
 */
object VipAssets {
    const val PREFIX = "asset://"
    const val MAX_LEVEL = 8

    private const val DEFAULT_BASE_URL = "https://cdn.jsdelivr.net/gh/hayu-app/Hayu-android@main/remote-assets/"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    /** Ganti alamat hosting aset lewat Firestore (app_config/assets.baseUrl), tanpa perlu rilis app baru. */
    fun refreshConfig() {
        FirebaseFirestore.getInstance().collection("app_config").document("assets").get()
            .addOnSuccessListener { d ->
                val u = d.getString("baseUrl")?.trim().orEmpty()
                if (u.startsWith("http")) baseUrl = if (u.endsWith("/")) u else "$u/"
            }
    }

    /** URL lengkap untuk path aset, mis. "svip/frame/3.svga". */
    fun url(path: String): String = baseUrl + path

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

    // Daftar file yang tersedia online (dibaca sekali dari assets/remote_index.txt)
    @Volatile
    private var index: Set<String>? = null

    private fun index(ctx: Context): Set<String> {
        index?.let { return it }
        val set: Set<String> = try {
            ctx.applicationContext.assets.open("remote_index.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
        index = set
        return set
    }

    /** Apakah aset ada? Kalau daftar tidak ditemukan, dianggap ada. */
    fun exists(ctx: Context, path: String): Boolean {
        val idx = index(ctx)
        return idx.isEmpty() || path in idx
    }

    fun load(view: ImageView, path: String) {
        view.load(url(path))
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
        iv.scaleType = ImageView.ScaleType.MATRIX
        val request = ImageRequest.Builder(ctx)
            .data(url(path))
            .size(Size(widthPx, Dimension.Undefined))
            .target(onSuccess = { drawable ->
                iv.setImageDrawable(drawable)
                val w = drawable.intrinsicWidth
                if (w > 0) {
                    val s = widthPx.toFloat() / w
                    iv.imageMatrix = Matrix().apply { setScale(s, s) }
                }
            })
            .build()
        ctx.imageLoader.enqueue(request)
        return iv
    }
}
