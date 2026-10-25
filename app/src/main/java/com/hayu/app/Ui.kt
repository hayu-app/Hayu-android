package com.hayu.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import kotlin.math.sqrt

object Ui {
    const val GRAY = 0xFF8892AA.toInt()
    const val GOLD = 0xFFFBBF24.toInt()
    const val WHITE_SOFT = 0xFFF1F5F9.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // Level 2 = 50 EXP, level 3 = 200, level 4 = 450, level 5 = 800, dst.
    fun levelForExp(exp: Int): Int = sqrt(exp / 50.0).toInt() + 1
    fun expForLevel(level: Int): Int = 50 * (level - 1) * (level - 1)

    fun text(ctx: Context, s: String, size: Float = 14f, color: Int = WHITE_SOFT, bold: Boolean = false): TextView {
        val tv = TextView(ctx)
        tv.text = s
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        return tv
    }

    /** Ikon gambar (iconUrl) dengan emoji sebagai cadangan: emoji hilang begitu gambar berhasil dimuat. */
    fun iconView(ctx: Context, emoji: String, iconUrl: String, sizeDp: Int, emojiSize: Float): View {
        val box = FrameLayout(ctx)
        val px = dp(ctx, sizeDp)
        val tv = text(ctx, emoji, emojiSize)
        tv.gravity = Gravity.CENTER
        box.addView(tv, FrameLayout.LayoutParams(px, px, Gravity.CENTER))
        if (iconUrl.isNotBlank()) {
            val img = ImageView(ctx)
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            box.addView(img, FrameLayout.LayoutParams(px, px, Gravity.CENTER))
            img.load(iconUrl) {
                listener(onSuccess = { _, _ -> tv.visibility = View.GONE })
            }
        }
        return box
    }

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.setBackgroundResource(R.drawable.bg_card_category)
        c.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(ctx, 10)
        c.layoutParams = lp
        return c
    }

    fun button(ctx: Context, label: String, enabled: Boolean = true, onClick: () -> Unit): TextView {
        val b = TextView(ctx)
        b.text = label
        b.textSize = 12f
        b.setTextColor(Color.WHITE)
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setBackgroundResource(R.drawable.bg_button_primary)
        b.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        b.alpha = if (enabled) 1f else 0.45f
        b.isClickable = enabled
        if (enabled) b.setOnClickListener { onClick() }
        return b
    }

    fun row(ctx: Context, emoji: String, title: String, desc: String, right: View?, iconUrl: String = ""): LinearLayout {
        val c = card(ctx)
        val h = LinearLayout(ctx)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.addView(if (iconUrl.isBlank()) text(ctx, emoji, 26f) else iconView(ctx, emoji, iconUrl, 36, 26f))
        val mid = LinearLayout(ctx)
        mid.orientation = LinearLayout.VERTICAL
        mid.setPadding(dp(ctx, 12), 0, dp(ctx, 8), 0)
        mid.addView(text(ctx, title, 14f, WHITE_SOFT, true))
        mid.addView(text(ctx, desc, 11f, GRAY))
        h.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) h.addView(right)
        c.addView(h)
        return c
    }
}
