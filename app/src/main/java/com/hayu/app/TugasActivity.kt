package com.hayu.app

import android.content.Intent
import com.google.firebase.firestore.DocumentSnapshot

class TugasActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Tugas"
    override val needsFollows: Boolean get() = true

    private class Task(
        val id: String,
        val emoji: String,
        val title: String,
        val desc: String,
        val coins: Int,
        val exp: Int,
        val done: (DocumentSnapshot, Int, Int) -> Boolean
    )

    private val tasks = listOf(
        Task("profil", "📝", "Lengkapi profil", "Isi nickname, umur, dan gender", 20, 20) { d, _, _ ->
            !d.getString("nickname").isNullOrBlank() &&
                (d.getLong("age") ?: 0L) > 0L &&
                !d.getString("gender").isNullOrBlank()
        },
        Task("foto", "📷", "Pasang foto profil", "Upload foto agar lebih dikenali", 15, 15) { d, _, _ ->
            !d.getString("photoUrl").isNullOrBlank()
        },
        Task("ikuti3", "🤝", "Ikuti 3 pengguna", "Cari teman baru di Discover", 15, 15) { _, following, _ ->
            following >= 3
        },
        Task("fans1", "💜", "Dapatkan 1 pengikut", "Biarkan orang lain mengikutimu", 20, 20) { _, _, followers ->
            followers >= 1
        }
    )

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val claimed = (user.get("claimedTasks") as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()

        container.addView(Ui.text(this, "Tugas Harian & Misi", 12f, Ui.GRAY))

        val lastDaily = user.getLong("lastDailyClaim") ?: 0L
        val dailyDone = System.currentTimeMillis() - lastDaily < 24 * 60 * 60 * 1000L
        val dailyBtn = Ui.button(this, if (dailyDone) "Selesai ✅" else "Klaim", !dailyDone) {
            startActivity(Intent(this, DailyCoinActivity::class.java))
        }
        container.addView(Ui.row(this, "📅", "Absen harian", "+10 🪙  +5 EXP", dailyBtn))

        for (t in tasks) {
            val isClaimed = t.id in claimed
            val canClaim = !isClaimed && t.done(user, following, followers)
            val label = when {
                isClaimed -> "Diklaim ✅"
                canClaim -> "Klaim"
                else -> "Belum"
            }
            val btn = Ui.button(this, label, canClaim) { claim(t) }
            container.addView(Ui.row(this, t.emoji, t.title, "${t.desc}\n+${t.coins} 🪙  +${t.exp} EXP", btn))
        }
    }

    private fun claim(t: Task) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val claimed = (snap.get("claimedTasks") as? List<*>)?.map { it.toString() } ?: emptyList()
            if (t.id in claimed) throw IllegalStateException("Tugas sudah diklaim")
            val coins = (snap.getLong("coins") ?: 0L) + t.coins
            val earned = (snap.getLong("totalCoinsEarned") ?: 0L) + t.coins
            val exp = (snap.getLong("exp") ?: 0L) + t.exp
            tx.update(
                ref, mapOf(
                    "coins" to coins,
                    "totalCoinsEarned" to earned,
                    "exp" to exp,
                    "level" to Ui.levelForExp(exp.toInt()).toLong(),
                    "claimedTasks" to (claimed + t.id)
                )
            )
            null
        }.addOnSuccessListener {
            toast("+${t.coins} Koin, +${t.exp} EXP 🎉")
            reload()
        }.addOnFailureListener {
            toast("Gagal: ${it.message}")
        }
    }
}
