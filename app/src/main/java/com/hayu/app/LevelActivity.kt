package com.hayu.app

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.firebase.firestore.DocumentSnapshot

class LevelActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Level"

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val exp = (user.getLong("exp") ?: 0L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val level = Ui.levelForExp(exp)
        val maxed = level >= LevelCurve.MAX_LEVEL
        val cur = Ui.expForLevel(level)
        val next = Ui.expForLevel(level + 1)

        if ((user.getLong("level") ?: 1L).toInt() != level) {
            user.reference.update("level", level.toLong())
        }

        val c = Ui.card(this)
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(LevelBadge.view(this, level, 40))
        c.addView(Ui.text(this, "Level $level", 28f, Ui.GOLD, true))

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 10))
        lp.topMargin = Ui.dp(this, 12)
        if (maxed) {
            c.addView(Ui.text(this, "$exp EXP", 13f, Ui.GRAY))
            bar.max = 1
            bar.progress = 1
            c.addView(bar, lp)
            c.addView(Ui.text(this, "Level maksimal 🎉", 11f, Ui.GRAY))
        } else {
            c.addView(Ui.text(this, "$exp / $next EXP", 13f, Ui.GRAY))
            bar.max = next - cur
            bar.progress = exp - cur
            c.addView(bar, lp)
            c.addView(Ui.text(this, "${next - exp} EXP lagi menuju Level ${level + 1}", 11f, Ui.GRAY))
        }
        container.addView(c)

        val how = Ui.card(this)
        how.addView(Ui.text(this, "Cara mendapat EXP", 14f, Ui.WHITE_SOFT, true))
        how.addView(
            Ui.text(
                this,
                "• Absen harian: +5 EXP\n• Selesaikan Tugas: +15 sampai +20 EXP\n• Kirim hadiah: +1 EXP tiap 10 koin",
                12f, Ui.GRAY
            )
        )
        container.addView(how)

        val hard = Ui.card(this)
        hard.addView(Ui.text(this, "ℹ️ Setelah Level ${LevelCurve.EASY_LEVEL_CAP}", 14f, Ui.WHITE_SOFT, true))
        hard.addView(
            Ui.text(
                this,
                "Naik level makin berat: butuh jauh lebih banyak EXP di setiap level berikutnya.",
                12f, Ui.GRAY
            )
        )
        container.addView(hard)

        container.addView(Ui.text(this, "Jalur level", 12f, Ui.GRAY))
        for (lv in 2..minOf(level + 4, LevelCurve.MAX_LEVEL)) {
            val need = Ui.expForLevel(lv)
            val reached = lv <= level
            val tag = Ui.text(this, if (reached) "✅" else "🔒", 14f)
            container.addView(Ui.row(this, "⭐", "Level $lv", "Butuh $need EXP", tag))
        }
    }
}
