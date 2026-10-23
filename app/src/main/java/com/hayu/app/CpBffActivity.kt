package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot

// Firestore: bonds/{id}
//   type ("cp" / "bff"), uids [pengirim, penerima], fromUid, toUid, fromName, toName
//   status: pending -> active -> ended   |   pending -> declined / cancelled
//   price (koin dipotong dari pengirim), refunded, createdAt, acceptedAt, endedAt
class CpBffActivity : ListPageActivity() {

    companion object {
        // Ubah di sini untuk mengatur biaya undangan & batas BFF
        const val CP_PRICE = 5000L
        const val BFF_PRICE = 2000L
        const val BFF_MAX = 3
    }

    private var type = "cp"
    private var bonds: List<DocumentSnapshot> = emptyList()
    private var refundChecked = false

    private fun price(t: String = type) = if (t == "cp") CP_PRICE else BFF_PRICE
    private fun label(t: String = type) = if (t == "cp") "CP" else "BFF"
    private fun icon(t: String = type) = if (t == "cp") "💕" else "🤝"
    private fun st(b: DocumentSnapshot) = b.getString("status").orEmpty()
    private fun ty(b: DocumentSnapshot) = b.getString("type").orEmpty()
    private fun hasUid(b: DocumentSnapshot, uid: String) = (b.get("uids") as? List<*>)?.contains(uid) == true
    private fun myName() = myDoc?.let { nick(it) } ?: "Pengguna HAYU"

    override fun allowed(): Boolean = true

    override fun onReady() {
        type = if (intent.getStringExtra("mode") == "bff") "bff" else "cp"
        showSearch()
        addAction("💕 CP") { type = "cp"; load() }
        addAction("🤝 BFF") { type = "bff"; load() }
        load()
    }

    // ---------- muat & tampil ----------

    private fun load() {
        setInfo("Memuat…")
        clearList()
        db.collection("bonds").whereArrayContains("uids", myUid).get()
            .addOnSuccessListener { snap ->
                bonds = snap.documents
                render()
                if (!refundChecked) {
                    refundChecked = true
                    refundDeclined()
                }
            }
            .addOnFailureListener { toast("Gagal memuat: ${it.message}") }
    }

    private fun render() {
        clearList()
        val mine = bonds.filter { ty(it) == type }
        val active = mine.filter { st(it) == "active" }
        val incoming = mine.filter { st(it) == "pending" && it.getString("toUid") == myUid }
        val outgoing = mine.filter { st(it) == "pending" && it.getString("fromUid") == myUid }

        val rule = if (type == "cp") "maks 1 pasangan" else "maks $BFF_MAX sahabat"
        setInfo("${icon()} ${label()} ($rule). Cari user di atas lalu ketuk untuk mengundang. Biaya undangan 🪙${fmt(price())}, dikembalikan jika ditolak / dibatalkan.")

        if (active.isEmpty()) {
            addRow("Belum ada ${label()} aktif", "Cari nickname / UserID di atas untuk mengundang")
        }
        active.forEach { b ->
            val other = if (b.getString("fromUid") == myUid) b.getString("toName") else b.getString("fromName")
            addRow("${icon()} ${other ?: "-"}", "${label()} sejak ${timeStr(num(b, "acceptedAt"))} • 💗 ${fmt(num(b, "points"))} poin • ketuk untuk putuskan") {
                confirm("Putuskan ${label()}", "Putuskan ikatan ${label()} dengan ${other ?: "-"}? Biaya undangan tidak dikembalikan.") {
                    endBond(b)
                }
            }
        }
        incoming.forEach { b ->
            addRow("📩 ${b.getString("fromName") ?: "-"} mengajakmu jadi ${label()}", "Ketuk untuk terima / tolak") {
                answerDialog(b)
            }
        }
        outgoing.forEach { b ->
            addRow("⏳ Menunggu ${b.getString("toName") ?: "-"}", "Undangan ${label()} terkirim • ketuk untuk batalkan") {
                confirm("Batalkan undangan", "Batalkan undangan ke ${b.getString("toName") ?: "-"}? Koin 🪙${fmt(num(b, "price"))} dikembalikan.") {
                    cancelInvite(b)
                }
            }
        }
    }

    // ---------- cari user & undang ----------

    override fun onUserResults(docs: List<DocumentSnapshot>) {
        clearList()
        val others = docs.filter { it.id != myUid }
        setInfo(if (others.isEmpty()) "User tidak ditemukan" else "Ketuk user untuk mengundang ${label()} (🪙${fmt(price())})")
        others.forEach { u ->
            addRow(nick(u), "ID: ${u.get("userId") ?: "-"}") {
                confirm("Undang ${label()}", "Kirim undangan ${label()} ke ${nick(u)}? Biaya 🪙${fmt(price())}.") {
                    invite(u)
                }
            }
        }
        addRow("‹ Kembali", "") { load() }
    }

    private fun invite(u: DocumentSnapshot) {
        val t = type
        val myActive = bonds.filter { ty(it) == t && st(it) == "active" }
        if (t == "cp" && myActive.isNotEmpty()) { toast("Kamu sudah punya CP. Putuskan dulu."); return }
        if (t == "bff" && myActive.size >= BFF_MAX) { toast("BFF kamu sudah penuh (maks $BFF_MAX)"); return }
        val dup = bonds.any { ty(it) == t && (st(it) == "active" || st(it) == "pending") && hasUid(it, u.id) }
        if (dup) { toast("Sudah ada ikatan atau undangan dengan ${nick(u)}"); return }

        setInfo("Memeriksa…")
        db.collection("bonds").whereArrayContains("uids", u.id).get()
            .addOnSuccessListener { snap ->
                val theirActive = snap.documents.count { ty(it) == t && st(it) == "active" }
                if (t == "cp" && theirActive > 0) { toast("${nick(u)} sudah punya CP"); return@addOnSuccessListener }
                if (t == "bff" && theirActive >= BFF_MAX) { toast("BFF ${nick(u)} sudah penuh"); return@addOnSuccessListener }
                createInvite(u, t)
            }
            .addOnFailureListener { toast("Gagal memeriksa: ${it.message}") }
    }

    private fun createInvite(u: DocumentSnapshot, t: String) {
        val cost = price(t)
        val meRef = db.collection("users").document(myUid)
        val ref = db.collection("bonds").document()
        val fromName = myName()
        val toName = nick(u)
        val toUid = u.id

        db.runTransaction { tx ->
            val me = tx.get(meRef)
            val coins = me.getLong("coins") ?: 0L
            if (coins < cost) throw IllegalStateException("Koin tidak cukup (butuh 🪙${fmt(cost)})")
            if (cost > 0L) tx.update(meRef, mapOf("coins" to (coins - cost)))
            tx.set(
                ref, hashMapOf<String, Any>(
                    "type" to t,
                    "uids" to listOf(myUid, toUid),
                    "fromUid" to myUid,
                    "toUid" to toUid,
                    "fromName" to fromName,
                    "toName" to toName,
                    "status" to "pending",
                    "price" to cost,
                    "refunded" to false,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            null
        }.addOnSuccessListener {
            toast("Undangan ${label(t)} terkirim 💌")
            load()
        }.addOnFailureListener {
            toast(it.message ?: "Gagal mengirim undangan")
            load()
        }
    }

    // ---------- jawab undangan ----------

    private fun answerDialog(b: DocumentSnapshot) {
        AlertDialog.Builder(this)
            .setTitle("Undangan ${label(ty(b))}")
            .setMessage("${b.getString("fromName") ?: "-"} mengajakmu jadi ${label(ty(b))}.")
            .setPositiveButton("Terima") { _, _ -> accept(b) }
            .setNegativeButton("Tolak") { _, _ -> decline(b) }
            .setNeutralButton("Nanti", null)
            .show()
    }

    private fun accept(b: DocumentSnapshot) {
        val t = ty(b)
        val myActive = bonds.count { ty(it) == t && st(it) == "active" }
        if (t == "cp" && myActive > 0) { toast("Kamu sudah punya CP. Putuskan dulu."); return }
        if (t == "bff" && myActive >= BFF_MAX) { toast("BFF kamu sudah penuh (maks $BFF_MAX)"); return }

        val fromUid = b.getString("fromUid").orEmpty()
        db.collection("bonds").whereArrayContains("uids", fromUid).get()
            .addOnSuccessListener { snap ->
                val theirActive = snap.documents.count { ty(it) == t && st(it) == "active" }
                if (t == "cp" && theirActive > 0) { toast("Pengirim sudah punya CP lain"); return@addOnSuccessListener }
                if (t == "bff" && theirActive >= BFF_MAX) { toast("BFF pengirim sudah penuh"); return@addOnSuccessListener }
                changeStatus(
                    b, "pending", "active",
                    mapOf("acceptedAt" to System.currentTimeMillis()),
                    "${label(t)} aktif! ${icon(t)}"
                )
            }
            .addOnFailureListener { toast("Gagal memeriksa: ${it.message}") }
    }

    private fun decline(b: DocumentSnapshot) {
        changeStatus(b, "pending", "declined", mapOf("declinedAt" to System.currentTimeMillis()), "Undangan ditolak")
    }

    private fun endBond(b: DocumentSnapshot) {
        changeStatus(
            b, "active", "ended",
            mapOf("endedAt" to System.currentTimeMillis(), "endedBy" to myUid),
            "Ikatan ${label(ty(b))} diputuskan"
        )
    }

    /** Ubah status hanya jika status saat ini sesuai (aman dari balapan). */
    private fun changeStatus(b: DocumentSnapshot, from: String, to: String, extra: Map<String, Any>, msg: String) {
        db.runTransaction { tx ->
            val cur = tx.get(b.reference)
            if (cur.getString("status") != from) throw IllegalStateException("Status sudah berubah, muat ulang")
            tx.update(b.reference, extra + mapOf<String, Any>("status" to to))
            null
        }.addOnSuccessListener {
            toast(msg)
            load()
        }.addOnFailureListener {
            toast(it.message ?: "Gagal")
            load()
        }
    }

    // ---------- batal & refund ----------

    private fun cancelInvite(b: DocumentSnapshot) {
        val meRef = db.collection("users").document(myUid)
        db.runTransaction { tx ->
            val cur = tx.get(b.reference)
            val me = tx.get(meRef)
            if (cur.getString("status") != "pending") throw IllegalStateException("Undangan sudah tidak berlaku")
            val cost = (cur.get("price") as? Number)?.toLong() ?: 0L
            tx.update(b.reference, mapOf("status" to "cancelled", "refunded" to true, "cancelledAt" to System.currentTimeMillis()))
            if (cost > 0L) tx.update(meRef, mapOf("coins" to ((me.getLong("coins") ?: 0L) + cost)))
            null
        }.addOnSuccessListener {
            toast("Undangan dibatalkan, koin dikembalikan")
            load()
        }.addOnFailureListener {
            toast(it.message ?: "Gagal")
            load()
        }
    }

    // Undangan yang ditolak penerima: koin dikembalikan ke pengirim saat pengirim membuka layar ini
    private fun refundDeclined() {
        val meRef = db.collection("users").document(myUid)
        val todo = bonds.filter {
            st(it) == "declined" && it.getString("fromUid") == myUid &&
                it.getBoolean("refunded") != true && num(it, "price") > 0L
        }
        for (b in todo) {
            db.runTransaction { tx ->
                val cur = tx.get(b.reference)
                val me = tx.get(meRef)
                if (cur.getString("status") != "declined" || cur.getBoolean("refunded") == true) {
                    return@runTransaction 0L
                }
                val cost = (cur.get("price") as? Number)?.toLong() ?: 0L
                tx.update(b.reference, mapOf("refunded" to true))
                tx.update(meRef, mapOf("coins" to ((me.getLong("coins") ?: 0L) + cost)))
                cost
            }.addOnSuccessListener { back ->
                if (back > 0L) toast("Undangan ditolak, 🪙${fmt(back)} dikembalikan")
            }
        }
    }
}
