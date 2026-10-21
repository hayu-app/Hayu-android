package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Transaction

/** Sisi CS. Mode: tickets (extra "cat" untuk filter kategori), history, stats. */
class TicketAdminActivity : ListPageActivity() {

    private var cat = ""

    override fun allowed(): Boolean = Role.canAccessAny(myRoles, "support") || Role.canAccessAny(myRoles, "cs")

    override fun onReady() {
        cat = intent.getStringExtra("cat") ?: ""
        when (mode) {
            "tickets" -> loadOpen()
            "history" -> loadHistory()
            "stats" -> loadStats()
        }
    }

    private fun loadOpen() {
        db.collection("tickets").whereEqualTo("status", "open").limit(200).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                    .filter { cat.isEmpty() || it.getString("category") == cat }
                    .sortedBy { num(it, "createdAt") }
                setInfo(if (docs.isEmpty()) "Tidak ada tiket menunggu 🎉" else "${docs.size} tiket menunggu (terlama di atas)")
                for (t in docs) {
                    addRow(
                        "${TicketCat.label(t.getString("category"))} • ${t.getString("nickname").orEmpty()}",
                        "${timeStr(num(t, "createdAt"))} • ${t.getString("message").orEmpty().take(60)}"
                    ) { ticketDialog(t) }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun ticketDialog(t: DocumentSnapshot) {
        AlertDialog.Builder(this)
            .setTitle("${TicketCat.label(t.getString("category"))} • ${t.getString("nickname").orEmpty()}")
            .setMessage(t.getString("message").orEmpty())
            .setPositiveButton("Balas & tutup") { _, _ ->
                textDialog("Balasan untuk user", "Tulis balasan") { reply -> replyTicket(t.id, reply.take(1000)) }
            }
            .setNegativeButton("Kembali", null)
            .show()
    }

    private fun replyTicket(ticketId: String, reply: String) {
        val ref = db.collection("tickets").document(ticketId)
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val t = tx.get(ref)
            if (t.getString("status") != "open") throw IllegalStateException("Sudah ditangani CS lain")
            tx.update(
                ref, mapOf<String, Any>(
                    "status" to "closed", "reply" to reply,
                    "repliedBy" to myUid, "repliedAt" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ Balasan terkirim"); loadOpen() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadHistory() {
        db.collection("tickets").whereEqualTo("status", "closed").limit(300).get()
            .addOnSuccessListener { snap ->
                clearList()
                val onlyMine = Role.CS in myRoles && Role.top(myRoles) == Role.CS
                val docs = snap.documents
                    .filter { !onlyMine || it.getString("repliedBy") == myUid }
                    .sortedByDescending { num(it, "repliedAt") }
                    .take(50)
                setInfo(if (docs.isEmpty()) "Belum ada riwayat balasan" else "${docs.size} balasan terakhir")
                for (t in docs) {
                    addRow(
                        "${TicketCat.label(t.getString("category"))} • ${t.getString("nickname").orEmpty()}",
                        "${timeStr(num(t, "repliedAt"))} • ${t.getString("reply").orEmpty().take(60)}"
                    ) {
                        AlertDialog.Builder(this)
                            .setMessage("Pesan:\n${t.getString("message").orEmpty()}\n\nBalasan:\n${t.getString("reply").orEmpty()}")
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun loadStats() {
        db.collection("tickets").limit(500).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                val open = docs.count { it.getString("status") == "open" }
                val closed = docs.size - open
                val mine = docs.count { it.getString("repliedBy") == myUid }
                setInfo("Total ${docs.size} tiket • menunggu $open • selesai $closed")
                addRow("🎧 Dibalas olehmu", "$mine tiket")
                for (c in TicketCat.all) {
                    val n = docs.count { it.getString("category") == c }
                    val o = docs.count { it.getString("category") == c && it.getString("status") == "open" }
                    addRow(TicketCat.label(c), "$n tiket • $o menunggu")
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
