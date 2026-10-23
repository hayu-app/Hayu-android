package com.hayu.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Tema header profil (background VIP / SVIP) dan pembuat badge. */
object ProfileTheme {

    // Warna atas -> bawah. Makin tinggi level makin mencolok.
    private val vipColors = listOf(
        "#1E40AF", "#1D4ED8", "#4338CA", "#6D28D9", "#A21CAF", "#BE185D", "#E11D48"
    )
    private val svipColors = listOf(
        "#B45309", "#D97706", "#F59E0B", "#EAB308", "#F97316", "#DC2626", "#FACC15"
    )
    private val bottomColor = Color.parseColor("#080818")

    /** SVIP didahulukan kalau ada; kalau tidak ada pakai VIP; kalau keduanya 0 -> tampilan bawaan. */
    fun background(ctx: Context, vipLevel: Int, svipLevel: Int): Drawable {
        val top: Int? = when {
            svipLevel > 0 -> Color.parseColor(svipColors[(svipLevel - 1).coerceIn(0, svipColors.lastIndex)])
            vipLevel > 0 -> Color.parseColor(vipColors[(vipLevel - 1).coerceIn(0, vipColors.lastIndex)])
            else -> null
        }
        if (top == null) {
            val base = ContextCompat.getDrawable(ctx, R.drawable.bg_profile_header)!!.mutate()
            val cover = ContextCompat.getDrawable(ctx, R.drawable.bg_cover_gradient)!!.mutate()
            cover.alpha = 153
            return LayerDrawable(arrayOf(base, cover))
        }
        val r = 24 * ctx.resources.displayMetrics.density
        val mid = blend(top, bottomColor, 0.55f)
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, mid, bottomColor))
        g.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        return g
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a)
        val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b)
        return Color.rgb(
            (ar + (br - ar) * t).toInt(),
            (ag + (bg - ag) * t).toInt(),
            (ab + (bb - ab) * t).toInt()
        )
    }

    fun roleColor(role: String): Int = Color.parseColor(
        when (role) {
            Role.DEVELOPER -> "#DC2626"
            Role.SUPERADMIN -> "#7C3AED"
            Role.ADMIN -> "#D97706"
            Role.BD -> "#059669"
            Role.AGENCY -> "#2563EB"
            Role.HOST -> "#DB2777"
            Role.COINSELLER -> "#CA8A04"
            Role.FINANCE -> "#0D9488"
            Role.MODERATOR -> "#0891B2"
            Role.CS -> "#0284C7"
            else -> "#6B7280"
        }
    )

    /** Badge bulat-lonjong sederhana. */
    fun chip(ctx: Context, text: String, bgColor: Int, fgColor: Int = Color.WHITE): TextView {
        val d = ctx.resources.displayMetrics.density
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(fgColor)
        tv.textSize = 11f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setColor(bgColor)
        bg.cornerRadius = 10 * d
        tv.background = bg
        tv.setPadding((9 * d).toInt(), (3 * d).toInt(), (9 * d).toInt(), (3 * d).toInt())
        return tv
    }
}
