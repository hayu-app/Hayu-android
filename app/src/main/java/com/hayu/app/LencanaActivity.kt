package com.hayu.app

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.firebase.firestore.DocumentSnapshot

class LencanaActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Lencana"
    override val needsFollows: Boolean get() = true

    private class Badge(val emoji: String, val name: String, val desc: String, val unlocked: Boolean)

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val coins = (user.getLong("totalCoinsEarned") ?: 0L).toInt()
        val level = (user.getLong("level") ?: 1L).toInt()
        val role = user.getString("role")?.lowercase()?.trim() ?: "user"
        val profileDone = !user.getString("nickname").isNullOrBlank() &&
            (user.getLong("age") ?: 0L) > 0L &&
            !user.getString("gender").isNullOrBlank()

        val badges = listOf(
            Badge("🌱", "Pendatang Baru", "Bergabung di HAYU", true),
            Badge("📝", "Profil Lengkap", "Lengkapi data profil", profileDone),
            Badge("🤝", "Sosialita", "Ikuti 10 pengguna", following >= 10),
            Badge("🌟", "Populer", "Punya 5 pengikut", followers >= 5),
            Badge("💰", "Kolektor", "Kumpulkan 500 koin", coins >= 500),
            Badge("👑", "Level 5", "Capai level 5", level >= 5),
            Badge("🎁", "Dermawan", "Kirim hadiah di room", (user.getLong("giftsSent") ?: 0L) > 0L),
            Badge("💝", "Idola", "Terima hadiah di room", (user.getLong("giftsReceived") ?: 0L) > 0L),
            Badge("🛡️", "Staf HAYU", "Anggota tim HAYU", role != "user")
        )

        val total = badges.count { it.unlocked }
        container.addView(Ui.text(this, "Terbuka $total / ${badges.size}", 12f, Ui.GRAY))
        val gap = Ui.dp(this, 5)
        val top = Ui.text(this, " ", 4f)
        container.addView(top)

        var i = 0
        while (i < badges.size) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (j in i until minOf(i + 2, badges.size)) {
                val b = badges[j]
                val c = Ui.card(this)
                c.gravity = Gravity.CENTER_HORIZONTAL
                c.alpha = if (b.unlocked) 1f else 0.35f
                c.addView(Ui.text(this, b.emoji, 34f))
                c.addView(Ui.text(this, b.name, 13f, Ui.WHITE_SOFT, true))
                c.addView(Ui.text(this, if (b.unlocked) "Terbuka ✅" else b.desc, 10f, Ui.GRAY))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(gap, 0, gap, 0)
                line.addView(c, lp)
            }
            if (badges.size - i == 1) {
                line.addView(Ui.text(this, "", 1f), LinearLayout.LayoutParams(0, 1, 1f))
            }
            container.addView(line)
            i += 2
        }
    }
}
