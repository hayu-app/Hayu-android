package com.hayu.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Daftar panel dinamis di Profil: satu baris per role yang BENAR-BENAR dimiliki user. */
object RolePanels {

    private fun emoji(role: String) = when (role) {
        Role.DEVELOPER -> "👑"
        Role.SUPERADMIN -> "🛡️"
        Role.ADMIN -> "⭐"
        Role.BD -> "🏡"
        Role.AGENCY -> "🏢"
        Role.HOST -> "🎙️"
        Role.COINSELLER -> "🪙"
        Role.FINANCE -> "💰"
        Role.MODERATOR -> "🔍"
        Role.CS -> "🎧"
        else -> "🔐"
    }

    private fun title(role: String) = when (role) {
        Role.DEVELOPER -> "Developer"
        Role.SUPERADMIN -> "Superadmin"
        Role.ADMIN -> "Admin"
        Role.BD -> "BD"
        Role.AGENCY -> "Agency"
        Role.HOST -> "Host"
        Role.COINSELLER -> "Coinseller"
        Role.FINANCE -> "Finance"
        Role.MODERATOR -> "Moderator"
        Role.CS -> "CS"
        else -> role
    }

    fun build(ctx: Context, container: LinearLayout, roles: Set<String>) {
        container.removeAllViews()
        val owned = roles.filter { it != Role.USER }.sortedByDescending { Role.rankOf(it) }
        if (owned.isEmpty()) {
            container.visibility = View.GONE   // user biasa: tidak ada panel sama sekali
            return
        }
        container.visibility = View.VISIBLE
        val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).toInt() }

        val gd = GradientDrawable()
        gd.setColor(Color.parseColor("#12122A"))
        gd.cornerRadius = dp(14).toFloat()
        gd.setStroke(dp(1), Color.parseColor("#2E2E50"))
        container.background = gd

        val head = TextView(ctx)
        head.text = "AKSES PANEL"
        head.textSize = 11f
        head.setTextColor(Color.parseColor("#8892AA"))
        head.setPadding(dp(16), dp(12), dp(16), dp(4))
        container.addView(head)

        for ((i, role) in owned.withIndex()) {
            if (i > 0) {
                val line = View(ctx)
                line.setBackgroundColor(Color.parseColor("#1E1E40"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                lp.marginStart = dp(48)
                container.addView(line, lp)
            }
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), 0, dp(16), 0)

            val ic = TextView(ctx)
            ic.text = emoji(role)
            ic.textSize = 20f
            row.addView(ic)

            val tv = TextView(ctx)
            tv.text = "Panel ${title(role)}"
            tv.textSize = 14f
            tv.setTextColor(Color.parseColor("#F1F5F9"))
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.marginStart = dp(12)
            row.addView(tv, tvLp)

            val arrow = TextView(ctx)
            arrow.text = "›"
            arrow.textSize = 18f
            arrow.setTextColor(Color.parseColor("#8892AA"))
            row.addView(arrow)

            row.setOnClickListener { open(ctx, role, owned.toSet()) }
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        container.addView(View(ctx), LinearLayout.LayoutParams(1, dp(6)))
    }

    fun open(ctx: Context, role: String, owned: Set<String>) {
        if (role !in owned) return   // pengaman: hanya role yang dimiliki
        when (role) {
            Role.DEVELOPER, Role.SUPERADMIN, Role.ADMIN ->
                ctx.startActivity(Intent(ctx, DevPanelActivity::class.java).putExtra("role", role))
            else ->
                ctx.startActivity(
                    Intent(ctx, PanelDetailActivity::class.java)
                        .putExtra("panel_key", role)
                        .putExtra("panel_label", "Panel ${title(role)}")
                        .putExtra("my_role", role)
                )
        }
    }
}
