package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot

object TicketCat {
    val all = listOf("complaint", "topup", "transaksi", "refund", "other")
    fun label(c: String?): String = when (c) {
        "complaint" -> "📩 Komplain"
        "topup" -> "🪙 Top-up"
        "transaksi" -> "💳 Transaksi"
        "refund" -> "↩️ Refund"
        else -> "❓ Lainnya"
    }
}

/** Sisi USER: kirim tiket bantuan & lihat balasan CS. */
class SupportActivity : ListPageActivity() {

    override fun allowed(): Boolean = true

    override fun onReady() {
        addAction("➕ Tiket baru") { pickCategory() }
        loadMine()
    }

    private fun pickCategory() {
        val labels = TicketCat.all.map { TicketCat.label(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Kategori masalah")
            .setItems(labels) { _, which ->
                val cat = TicketCat.all[which]
                textDialog("Jelaskan masalahmu", "Tulis detail (maks 1000 karakter)") { msg ->
                    createTicket(cat, msg.take(1000))
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createTicket(cat: String, msg: String) {
        val data = mapOf<String, Any>(
            "uid" to myUid,
            "nickname" to (myDoc?.getString("nickname") ?: ""),
            "category" to cat,
            "message" to msg,
            "status" to "open",
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("tickets").add(data)
            .addOnSuccessListener { toast("✅ Tiket terkirim, tunggu balasan CS"); loadMine() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadMine() {
        db.collection("tickets").whereEqualTo("uid", myUid).limit(50).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents.sortedByDescending { num(it, "createdAt") }
                setInfo(if (docs.isEmpty()) "Belum ada tiket. Tap ➕ untuk membuat." else "${docs.size} tiket")
                for (t in docs) {
                    val done = t.getString("status") == "closed"
                    addRow(
                        "${TicketCat.label(t.getString("category"))} • ${if (done) "✅ Dibalas" else "⏳ Menunggu"}",
                        t.getString("message").orEmpty().take(70)
                    ) { showTicket(t) }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun showTicket(t: DocumentSnapshot) {
        val reply = t.getString("reply").orEmpty()
        val msg = "Pesan:\n${t.getString("message").orEmpty()}\n\n" +
            if (reply.isEmpty()) "Belum ada balasan dari CS." else "Balasan CS:\n$reply"
        AlertDialog.Builder(this)
            .setTitle(TicketCat.label(t.getString("category")))
            .setMessage(msg)
            .setPositiveButton("Tutup", null)
            .show()
    }
}
