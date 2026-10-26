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

    fun exists(ctx: Context, path: String): Boolean {
        if (HayuAssetManager.getFile(ctx, path)?.isFile == true) return true
        return try {
            ctx.assets.open(path).use { true }
        } catch (e: Exception) {
            false
        }
    }

    fun load(view: ImageView, path: String) {
        val downloaded = HayuAssetManager.getFile(view.context, path)
        if (downloaded != null) {
            view.load(downloaded)
        } else {
            view.load(ANDROID_ASSET + path)
        }
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
            val downloaded = HayuAssetManager.getFile(ctx, path)
            val bmp = if (downloaded != null) {
                downloaded.inputStream().use { BitmapFactory.decodeStream(it) }
            } else {
                ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }
            if (bmp != null) {
                iv.scaleType = ImageView.ScaleType.MATRIX
                iv.setImageBitmap(bmp)
                val s = widthPx.toFloat() / bmp.width
                iv.imageMatrix = Matrix().apply { setScale(s, s) }
            }
        } catch (e: Exception) {
            // Jika aset gagal dibaca, biarkan kosong.
        }
        return iv
    }
}
