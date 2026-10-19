package com.hayu.app

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.firebase.firestore.DocumentSnapshot

class LevelActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Level"

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val exp = (user.getLong("exp") ?: 0L).toInt()
        val level = Ui.levelForExp(exp)
        val cur = Ui.expForLevel(level)
        val next = Ui.expForLevel(level + 1)

        if ((user.getLong("level") ?: 1L).toInt() != level) {
            user.reference.update("level", level.toLong())
        }

        val c = Ui.card(this)
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(Ui.text(this, "👑", 48f))
        c.addView(Ui.text(this, "Level $level", 28f, Ui.GOLD, true))
        c.addView(Ui.text(this, "$exp / $next EXP", 13f, Ui.GRAY))

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = next - cur
        bar.progress = exp - cur
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 10))
        lp.topMargin = Ui.dp(this, 12)
        c.addView(bar, lp)
        c.addView(Ui.text(this, "${next - exp} EXP lagi menuju Level ${level + 1}", 11f, Ui.GRAY))
        container.addView(c)

        val how = Ui.card(this)
        how.addView(Ui.text(this, "Cara mendapat EXP", 14f, Ui.WHITE_SOFT, true))
        how.addView(Ui.text(this, "• Absen harian: +5 EXP\n• Selesaikan Tugas: +15 sampai +20 EXP", 12f, Ui.GRAY))
        container.addView(how)

        container.addView(Ui.text(this, "Jalur level", 12f, Ui.GRAY))
        for (lv in 2..(level + 4)) {
            val need = Ui.expForLevel(lv)
            val reached = lv <= level
            val tag = Ui.text(this, if (reached) "✅" else "🔒", 14f)
            container.addView(Ui.row(this, "⭐", "Level $lv", "Butuh $need EXP", tag))
        }
    }
}
