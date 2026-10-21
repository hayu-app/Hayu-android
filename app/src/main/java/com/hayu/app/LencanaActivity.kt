package com.hayu.app

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.firebase.firestore.DocumentSnapshot

class LencanaActivity : SimplePageActivity() {

    override val pageTitle: String
        get() = "Statistik & Lencana"

    override val needsFollows: Boolean
        get() = true

    private data class Badge(
        val emoji: String,
        val name: String,
        val description: String,
        val progress: Int,
        val target: Int
    ) {
        val unlocked: Boolean
            get() = progress >= target

        val percent: Int
            get() = if (target <= 0) 100
            else ((progress.coerceAtLeast(0).toFloat() / target) * 100)
                .toInt().coerceIn(0, 100)
    }

    override fun render(
        user: DocumentSnapshot,
        following: Int,
        followers: Int
    ) {
        val exp = (user.getLong("exp") ?: 0L)
            .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val level = Ui.levelForExp(exp)
        val coins = (user.getLong("totalCoinsEarned") ?: 0L).coerceAtLeast(0L)
        val giftsSent = (user.getLong("giftsSent") ?: 0L).coerceAtLeast(0L)
        val giftsReceived = (user.getLong("giftsReceived") ?: 0L).coerceAtLeast(0L)

        val nickname = user.getString("nickname").orEmpty()
        val age = user.getLong("age") ?: 0L
        val gender = user.getString("gender").orEmpty()
        val profileDone = nickname.isNotBlank() && age > 0L && gender.isNotBlank()

        val badges = listOf(
            Badge("🌱", "Pendatang Baru", "Mulai perjalanan di HAYU", 1, 1),
            Badge("📝", "Profil Lengkap", "Lengkapi profil pengguna", if (profileDone) 1 else 0, 1),
            Badge("🤝", "Sosialita", "Ikuti 10 pengguna", following, 10),
            Badge("🌟", "Populer", "Miliki 5 pengikut", followers, 5),
            Badge("💰", "Kolektor", "Kumpulkan 500 koin", coins.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 500),
            Badge("👑", "Level 5", "Capai level 5", level, 5),
            Badge("🎁", "Dermawan", "Kirim hadiah pertama", giftsSent.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 1),
            Badge("💝", "Idola", "Terima hadiah pertama", giftsReceived.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 1),
            Badge("🛡️", "Staf HAYU", "Bergabung dengan tim HAYU",
                if (Role.rolesOf(user).isNotEmpty()) 1 else 0, 1)
        )

        val unlocked = badges.count { it.unlocked }
        val overall = (unlocked * 100 / badges.size)

        container.addView(Ui.text(this, "🏆 Pencapaian kamu", 22f, Ui.WHITE_SOFT, true))
        container.addView(Ui.text(
            this,
            "Buka lencana dengan menyelesaikan aktivitas di HAYU.",
            12f,
            Ui.GRAY
        ))

        val summary = Ui.card(this)
        summary.addView(Ui.text(this, "📊 Statistik akun", 16f, Ui.WHITE_SOFT, true))
        summary.addView(Ui.text(this, "⭐ Level $level  •  ✨ $exp EXP", 14f, Ui.GOLD, true))
        summary.addView(Ui.text(this, "🪙 ${formatNumber(coins)} total koin diperoleh", 12f, Ui.GRAY))
        summary.addView(Ui.text(this, "🎁 ${formatNumber(giftsSent)} hadiah dikirim", 12f, Ui.GRAY))
        summary.addView(Ui.text(this, "💝 ${formatNumber(giftsReceived)} hadiah diterima", 12f, Ui.GRAY))
        summary.addView(Ui.text(this, "👥 $following mengikuti  •  $followers pengikut", 12f, Ui.GRAY))
        container.addView(summary)

        val progressCard = Ui.card(this)
        progressCard.addView(Ui.text(this, "Koleksi lencana", 16f, Ui.WHITE_SOFT, true))
        progressCard.addView(Ui.text(
            this,
            "$unlocked dari ${badges.size} lencana terbuka ($overall%)",
            13f,
            Ui.GOLD,
            true
        ))

        val overallBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        )
        overallBar.max = 100
        overallBar.progress = overall
        val barParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Ui.dp(this, 8)
        )
        barParams.topMargin = Ui.dp(this, 8)
        progressCard.addView(overallBar, barParams)
        container.addView(progressCard)

        container.addView(Ui.text(this, "Semua lencana", 17f, Ui.WHITE_SOFT, true))

        badges.forEach { badge ->
            val card = Ui.card(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.alpha = if (badge.unlocked) 1f else 0.82f

            val emoji = Ui.text(
                this,
                if (badge.unlocked) badge.emoji else "🔒",
                30f
            )
            emoji.gravity = Gravity.CENTER
            card.addView(
                emoji,
                LinearLayout.LayoutParams(Ui.dp(this, 46), ViewGroup.LayoutParams.WRAP_CONTENT)
            )

            val details = LinearLayout(this)
            details.orientation = LinearLayout.VERTICAL
            details.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 6), 0)

            details.addView(Ui.text(
                this,
                badge.name,
                14f,
                if (badge.unlocked) Ui.GOLD else Ui.WHITE_SOFT,
                true
            ))
            details.addView(Ui.text(
                this,
                if (badge.unlocked) "Terbuka ✅" else badge.description,
                11f,
                Ui.GRAY
            ))

            if (!badge.unlocked) {
                details.addView(Ui.text(
                    this,
                    "${formatNumber(badge.progress.toLong())} / ${formatNumber(badge.target.toLong())} • ${badge.percent}%",
                    10f,
                    Ui.GRAY
                ))

                val progressBar = ProgressBar(
                    this,
                    null,
                    android.R.attr.progressBarStyleHorizontal
                )
                progressBar.max = 100
                progressBar.progress = badge.percent

                val progressParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 5)
                )
                progressParams.topMargin = Ui.dp(this, 5)
                details.addView(progressBar, progressParams)
            }

            card.addView(
                details,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            container.addView(card)
        }

        container.addView(Ui.text(
            this,
            "ℹ️ Statistik dihitung dari data akun yang tersedia. " +
                "Koin diperoleh dan saldo koin adalah dua hal berbeda.",
            11f,
            Ui.GRAY
        ))
    }

    private fun formatNumber(value: Long): String {
        return "%,d".format(java.util.Locale("id", "ID"), value)
    }
}
