package com.hayu.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.graphics.Typeface
import kotlin.math.sqrt

/**
 * Kurva EXP -> level.
 *  - Level 1..10  : kurva lama (mudah)  EXP(L) = 50 * (L-1)^2   -> Lv10 = 4.050 EXP
 *  - Level 11..   : jauh lebih berat. Lv10->Lv11 butuh HARD_BASE EXP, tiap level berikutnya
 *                   butuh HARD_STEP EXP lebih banyak dari level sebelumnya.
 * Mau lebih mudah/sulit? Ubah HARD_BASE dan HARD_STEP saja.
 */
object LevelCurve {
    const val EASY_LEVEL_CAP = 10
    const val MAX_LEVEL = 220
    private const val HARD_BASE = 5000L
    private const val HARD_STEP = 1000L

    fun expForLevel(level: Int): Int {
        if (level <= 1) return 0
        if (level <= EASY_LEVEL_CAP) return 50 * (level - 1) * (level - 1)
        val n = (level - EASY_LEVEL_CAP).toLong()
        val easyTotal = 50L * (EASY_LEVEL_CAP - 1) * (EASY_LEVEL_CAP - 1)
        val total = easyTotal + HARD_BASE * n + HARD_STEP * n * (n - 1) / 2
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun levelForExp(exp: Int): Int {
        val e = exp.coerceAtLeast(0)
        if (e < expForLevel(EASY_LEVEL_CAP)) return sqrt(e / 50.0).toInt() + 1
        var lv = EASY_LEVEL_CAP
        while (lv < MAX_LEVEL && expForLevel(lv + 1) <= e) lv++
        return lv
    }
}

/**
 * Lencana level bergambar (res/drawable-nodpi/bg_level_*).
 * Tier yang gambarnya belum ada otomatis memakai tier di bawahnya.
 * Tinggal tambah file bg_level_100_119.webp / bg_level_180_199.webp untuk melengkapi.
 */
object LevelBadge {

    private val tiers = listOf(
        1 to "bg_level_1_9",
        10 to "bg_level_10_19",
        20 to "bg_level_20_39",
        40 to "bg_level_40_59",
        60 to "bg_level_60_79",
        80 to "bg_level_80_99",
        100 to "bg_level_100_119",
        120 to "bg_level_120_139",
        140 to "bg_level_140_159",
        160 to "bg_level_160_179",
        180 to "bg_level_180_199",
        200 to "bg_level_200_220"
    )

    private fun byName(ctx: Context, name: String): Drawable? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) ContextCompat.getDrawable(ctx, id)?.mutate() else null
    }

    fun drawableFor(ctx: Context, level: Int): Drawable? {
        if (level <= 0) return byName(ctx, "bg_level_0")
        var i = tiers.indexOfLast { level >= it.first }
        while (i >= 0) {
            byName(ctx, tiers[i].second)?.let { return it }
            i--
        }
        return null
    }

    /** Pasang gambar lencana + angka level ke TextView. Gambar asli tinggi 48px; area angka ada di kanan ikon. */
    fun style(tv: TextView, level: Int, heightDp: Int = 22) {
        val ctx = tv.context
        val bg = drawableFor(ctx, level)
        if (bg == null) {
            tv.text = "Lv. $level"
            return
        }
        val h = (heightDp * ctx.resources.displayMetrics.density).toInt()
        val ratio = bg.intrinsicWidth.toFloat() / bg.intrinsicHeight.coerceAtLeast(1)
        val w = (h * ratio).toInt()
        val s = h / 48f

        tv.background = bg
        tv.text = level.toString()
        tv.setTextColor(Color.WHITE)
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.46f)
        tv.includeFontPadding = false
        tv.gravity = Gravity.CENTER
        tv.setPadding((46 * s).toInt(), 0, (14 * s).toInt(), 0)

        val lp = tv.layoutParams ?: ViewGroup.LayoutParams(w, h)
        lp.width = w
        lp.height = h
        tv.layoutParams = lp
    }

    fun view(ctx: Context, level: Int, heightDp: Int = 22): TextView {
        val tv = TextView(ctx)
        style(tv, level, heightDp)
        return tv
    }
}
