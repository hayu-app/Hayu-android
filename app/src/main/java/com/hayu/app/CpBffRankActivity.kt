package com.hayu.app

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Peringkat CP & BFF: pasangan aktif diurutkan dari Poin Keharmonisan tertinggi (seri: paling lama bersama).
 * Sumber: bonds (status = active), tab CP / BFF.
 */
class CpBffRankActivity : ListPageActivity() {

    private var type = "cp"

    override fun allowed(): Boolean = true

    override fun onReady() {
        type = if (intent.getStringExtra("mode") == "bff") "bff" else "cp"
        addAction("💕 CP") { type = "cp"; load() }
        addAction("🤝 BFF") { type = "bff"; load() }
        load()
    }

    private fun load() {
        setInfo("Memuat peringkat…")
        clearList()
        db.collection("bonds").whereEqualTo("status", "active").limit(300).get()
            .addOnSuccessListener { snap -> render(snap.documents) }
            .addOnFailureListener { setInfo("Gagal memuat: ${it.message}") }
    }

    private fun render(all: List<DocumentSnapshot>) {
        clearList()
        val now = System.currentTimeMillis()
        val ranked = all.filter { it.getString("type") == type }
            .sortedWith(
                compareByDescending<DocumentSnapshot> { num(it, "points") }
                    .thenBy { num(it, "acceptedAt").let { t -> if (t <= 0L) Long.MAX_VALUE else t } }
            )
            .take(50)

        val label = if (type == "cp") "CP" else "BFF"
        if (ranked.isEmpty()) {
            setInfo("Belum ada pasangan $label aktif")
            return
        }
        setInfo("Peringkat $label • Poin Keharmonisan tertinggi")
        ranked.forEachIndexed { i, b ->
            val since = num(b, "acceptedAt")
            val days = if (since > 0L) ((now - since) / 86_400_000L).coerceAtLeast(0L) else 0L
            val icon = if (type == "cp") "💕" else "🤝"
            addRow(
                "${medal(i)}  ${b.getString("fromName") ?: "-"} $icon ${b.getString("toName") ?: "-"}",
                "💗 ${fmt(num(b, "points"))} poin • bersama $days hari"
            )
        }
    }
}
