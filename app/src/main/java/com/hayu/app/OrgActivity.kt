package com.hayu.app

import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jalur rekrutmen: BD -> Agency -> Host.
 * Mode: register_agency, verify_agency, agencies_{dash|perf|report|rank},
 *       recruit_host, verify_host, hosts_{dash|perf|manage}, attendance_agency,
 *       host_home, ranking_host
 */
class OrgActivity : ListPageActivity() {

    private val orgAdminRoles = setOf(Role.DEVELOPER, Role.SUPERADMIN, Role.ADMIN)
    private val ymd = SimpleDateFormat("yyyyMMdd", Locale.US)

    private fun isBd() = Role.BD in myRoles
    private fun isAgency() = Role.AGENCY in myRoles
    private fun isOrgAdmin() = myRoles.any { it in orgAdminRoles }

    override fun allowed(): Boolean = when {
        mode == "register_agency" || mode == "verify_agency" || mode.startsWith("agencies_") -> isBd() || isOrgAdmin()
        mode == "recruit_host" || mode == "attendance_agency" || mode.startsWith("hosts_") -> isAgency()
        mode == "verify_host" -> isAgency() || isOrgAdmin()
        mode == "host_home" -> Role.HOST in myRoles
        mode == "ranking_host" -> isAgency() || isBd() || isOrgAdmin() || Role.HOST in myRoles
        else -> false
    }

    override fun onReady() {
        when {
            mode == "register_agency" -> { showSearch(); setInfo("Cari user biasa untuk dijadikan agency") }
            mode == "recruit_host" -> { showSearch(); setInfo("Cari user biasa untuk direkrut jadi host") }
            mode == "verify_agency" -> loadPendingAgencies()
            mode.startsWith("agencies_") -> loadAgencies(mode.substringAfter("_"))
            mode == "verify_host" -> loadPendingHosts()
            mode.startsWith("hosts_") -> loadHosts(mode.substringAfter("_"))
            mode == "attendance_agency" -> loadAttendance()
            mode == "host_home" -> loadHostHome()
            mode == "ranking_host" -> loadRanking()
        }
    }

    // ---------- hasil cari user ----------
    override fun onUserResults(docs: List<DocumentSnapshot>) {
        clearList()
        val wanted = if (mode == "register_agency") Role.AGENCY else Role.HOST
        val shown = docs.filter { it.id != myUid && wanted !in Role.rolesOf(it) }
        if (shown.isEmpty()) {
            setInfo("Tidak ada user yang cocok (atau sudah memegang role ini)")
            return
        }
        setInfo("Ditemukan ${shown.size} user — tap untuk pilih")
        for (u in shown) {
            addRow("${nick(u)} • ID ${num(u, "userId")}", "🪙 ${fmt(num(u, "coins"))}") {
                if (mode == "register_agency") {
                    textDialog("Nama agency", "Nama agency", nick(u)) { name -> registerAgency(u, name) }
                } else {
                    confirm("Rekrut ${nick(u)}", "Jadikan host di agency kamu?") { recruitHost(u) }
                }
            }
        }
    }

    // ---------- BD: daftarkan agency ----------
    private fun registerAgency(target: DocumentSnapshot, name: String) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(target.id)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            val ar = Role.rolesOf(actor)
            if (Role.BD !in ar && ar.none { it in orgAdminRoles }) throw IllegalStateException("Tidak punya akses")
            if (Role.AGENCY in Role.rolesOf(t)) throw IllegalStateException("User ini sudah menjadi agency")
            val newRoles = Role.rolesOf(t) + Role.AGENCY
            tx.update(
                targetRef, mapOf<String, Any>(
                    "role" to Role.top(newRoles), "roles" to newRoles.toList(), "agencyStatus" to "pending", "agencyName" to name,
                    "bdUid" to myUid, "recruitedAt" to System.currentTimeMillis()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "register_agency", "targetUid" to target.id,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ ${nick(target)} didaftarkan sebagai agency (menunggu verifikasi)"); clearList(); doSearch() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- BD/Admin: verifikasi agency ----------
    private fun loadPendingAgencies() {
        db.collection("users").whereEqualTo("agencyStatus", "pending").limit(100).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                    .filter { isOrgAdmin() || it.getString("bdUid") == myUid }
                    .sortedBy { num(it, "recruitedAt") }
                setInfo(if (docs.isEmpty()) "Tidak ada agency yang menunggu 🎉" else "${docs.size} agency menunggu verifikasi")
                for (a in docs) {
                    addRow("🏢 ${a.getString("agencyName").orEmpty().ifEmpty { nick(a) }}", "Pemilik: ${nick(a)} • ${timeStr(num(a, "recruitedAt"))}") {
                        AlertDialog.Builder(this)
                            .setTitle(nick(a))
                            .setItems(arrayOf("✅ Verifikasi", "❌ Tolak")) { _, which -> decideAgency(a.id, which == 0) }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun decideAgency(agencyUid: String, approve: Boolean) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(agencyUid)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            val ar = Role.rolesOf(actor)
            val ownBd = Role.BD in ar && t.getString("bdUid") == myUid
            if (!ownBd && ar.none { it in orgAdminRoles }) throw IllegalStateException("Bukan agency di bawah kamu")
            if (t.getString("agencyStatus") != "pending") throw IllegalStateException("Sudah diproses")
            if (approve) {
                tx.update(targetRef, mapOf<String, Any>("agencyStatus" to "verified", "verifiedAt" to System.currentTimeMillis()))
            } else {
                tx.update(targetRef, mapOf<String, Any>("role" to Role.top(Role.rolesOf(t) - Role.AGENCY), "roles" to (Role.rolesOf(t) - Role.AGENCY).toList(), "agencyStatus" to "rejected"))
            }
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to (if (approve) "verify_agency" else "reject_agency"),
                    "targetUid" to agencyUid, "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast(if (approve) "✅ Agency diverifikasi" else "Agency ditolak"); loadPendingAgencies() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- BD: dashboard / performa / laporan / ranking agency ----------
    private fun loadAgencies(variant: String) {
        usersByRole(Role.AGENCY, 200)
            .addOnSuccessListener { aSnap ->
                usersByRole(Role.HOST, 1000)
                    .addOnSuccessListener { hSnap ->
                        val verified = hSnap.filter { it.getString("hostStatus") == "verified" }
                            .groupingBy { it.getString("agencyUid").orEmpty() }.eachCount()
                        val pending = hSnap.filter { it.getString("hostStatus") == "pending" }
                            .groupingBy { it.getString("agencyUid").orEmpty() }.eachCount()

                        val mine = aSnap.filter { isOrgAdmin() || it.getString("bdUid") == myUid }
                        val agencies = if (variant == "report") {
                            mine.sortedByDescending { num(it, "recruitedAt") }
                        } else {
                            mine.sortedByDescending { verified[it.id] ?: 0 }
                        }

                        val totalHosts = agencies.sumOf { (verified[it.id] ?: 0).toLong() }
                        val okAgency = agencies.count { it.getString("agencyStatus") == "verified" }
                        setInfo(
                            "Agency: ${agencies.size} (terverifikasi $okAgency) • Host aktif: $totalHosts"
                        )
                        clearList()
                        if (agencies.isEmpty()) setInfo("Belum ada agency")
                        for ((i, a) in agencies.withIndex()) {
                            val name = a.getString("agencyName").orEmpty().ifEmpty { nick(a) }
                            val prefix = if (variant == "rank") "${medal(i)} " else ""
                            val status = when (a.getString("agencyStatus")) {
                                "verified" -> "✅ Terverifikasi"
                                "pending" -> "⏳ Menunggu"
                                else -> "—"
                            }
                            val extra = if (variant == "report") " • direkrut ${timeStr(num(a, "recruitedAt"))}" else ""
                            addRow(
                                "$prefix🏢 $name",
                                "👥 ${verified[a.id] ?: 0} host (+${pending[a.id] ?: 0} menunggu) • $status$extra"
                            )
                        }
                    }
                    .addOnFailureListener { toast("Gagal: ${it.message}") }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency: rekrut host ----------
    private fun recruitHost(target: DocumentSnapshot) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(target.id)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            if (Role.AGENCY !in Role.rolesOf(actor)) throw IllegalStateException("Hanya agency yang boleh merekrut")
            if (actor.getString("agencyStatus") != "verified") throw IllegalStateException("Agency kamu belum diverifikasi")
            if (Role.HOST in Role.rolesOf(t)) throw IllegalStateException("User ini sudah menjadi host")
            val newRoles = Role.rolesOf(t) + Role.HOST
            tx.update(
                targetRef, mapOf<String, Any>(
                    "role" to Role.top(newRoles), "roles" to newRoles.toList(), "hostStatus" to "pending", "agencyUid" to myUid,
                    "agencyName" to (actor.getString("agencyName") ?: ""),
                    "recruitedAt" to System.currentTimeMillis()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "recruit_host", "targetUid" to target.id,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("✅ ${nick(target)} direkrut (menunggu verifikasi)"); clearList(); doSearch() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency/Admin: verifikasi host ----------
    private fun loadPendingHosts() {
        db.collection("users").whereEqualTo("hostStatus", "pending").limit(100).get()
            .addOnSuccessListener { snap ->
                clearList()
                val docs = snap.documents
                    .filter { isOrgAdmin() || it.getString("agencyUid") == myUid }
                    .sortedBy { num(it, "recruitedAt") }
                setInfo(if (docs.isEmpty()) "Tidak ada host yang menunggu 🎉" else "${docs.size} host menunggu verifikasi")
                for (h in docs) {
                    addRow("🎙️ ${nick(h)} • ID ${num(h, "userId")}", "Direkrut ${timeStr(num(h, "recruitedAt"))}") {
                        AlertDialog.Builder(this)
                            .setTitle(nick(h))
                            .setItems(arrayOf("✅ Verifikasi", "❌ Tolak")) { _, which -> decideHost(h.id, which == 0) }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun decideHost(hostUid: String, approve: Boolean) {
        val actorRef = db.collection("users").document(myUid)
        val targetRef = db.collection("users").document(hostUid)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val actor = tx.get(actorRef)
            val t = tx.get(targetRef)
            val ar = Role.rolesOf(actor)
            val own = Role.AGENCY in ar && t.getString("agencyUid") == myUid
            if (!own && ar.none { it in orgAdminRoles }) throw IllegalStateException("Bukan host di bawah kamu")
            if (t.getString("hostStatus") != "pending") throw IllegalStateException("Sudah diproses")
            if (approve) {
                tx.update(targetRef, mapOf<String, Any>("hostStatus" to "verified", "verifiedAt" to System.currentTimeMillis()))
            } else {
                tx.update(
                    targetRef, mapOf<String, Any>(
                        "role" to Role.top(Role.rolesOf(t) - Role.HOST), "roles" to (Role.rolesOf(t) - Role.HOST).toList(), "hostStatus" to "rejected", "agencyUid" to FieldValue.delete()
                    )
                )
            }
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to (if (approve) "verify_host" else "reject_host"),
                    "targetUid" to hostUid, "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast(if (approve) "✅ Host diverifikasi" else "Host ditolak"); loadPendingHosts() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency: dashboard / performa / manajemen member ----------
    private fun myHosts(onDone: (List<DocumentSnapshot>) -> Unit) {
        db.collection("users").whereEqualTo("agencyUid", myUid).limit(200).get()
            .addOnSuccessListener { snap ->
                onDone(snap.documents.filter { Role.HOST in Role.rolesOf(it) })
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun lv(doc: DocumentSnapshot): Long = maxOf(1L, num(doc, "level"))

    private fun loadHosts(variant: String) {
        myHosts { hosts ->
            clearList()
            val sorted = if (variant == "perf") {
                hosts.sortedWith(compareByDescending<DocumentSnapshot> { lv(it) }.thenByDescending { num(it, "coins") })
            } else {
                hosts.sortedBy { nick(it).lowercase() }
            }
            val ok = hosts.count { it.getString("hostStatus") == "verified" }
            val wait = hosts.count { it.getString("hostStatus") == "pending" }
            val hint = if (variant == "manage") " — tap untuk keluarkan" else ""
            setInfo("Host: ${hosts.size} (aktif $ok, menunggu $wait)$hint")
            for ((i, h) in sorted.withIndex()) {
                val status = if (h.getString("hostStatus") == "verified") "✅ Aktif" else "⏳ Menunggu"
                val prefix = if (variant == "perf") "${medal(i)} " else ""
                addRow("$prefix🎙️ ${nick(h)} • Lv ${lv(h)}", "$status • 🪙 ${fmt(num(h, "coins"))}") {
                    if (variant == "manage") {
                        confirm("Keluarkan ${nick(h)}?", "Host akan kembali menjadi user biasa.") { removeHost(h.id) }
                    }
                }
            }
        }
    }

    private fun removeHost(hostUid: String) {
        val targetRef = db.collection("users").document(hostUid)
        val logRef = db.collection("org_logs").document()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val t = tx.get(targetRef)
            if (t.getString("agencyUid") != myUid || Role.HOST !in Role.rolesOf(t)) {
                throw IllegalStateException("Bukan host di bawah kamu")
            }
            tx.update(
                targetRef, mapOf<String, Any>(
                    "role" to Role.top(Role.rolesOf(t) - Role.HOST), "roles" to (Role.rolesOf(t) - Role.HOST).toList(), "hostStatus" to FieldValue.delete(), "agencyUid" to FieldValue.delete()
                )
            )
            tx.set(
                logRef, mapOf<String, Any>(
                    "action" to "remove_host", "targetUid" to hostUid,
                    "byUid" to myUid, "at" to System.currentTimeMillis()
                )
            )
            null
        })
            .addOnSuccessListener { toast("Host dikeluarkan"); loadHosts("manage") }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Agency: absensi host ----------
    private fun loadAttendance() {
        val today = ymd.format(Date())
        val month = today.substring(0, 6)
        myHosts { hosts ->
            db.collection("host_attendance").whereEqualTo("agencyUid", myUid).limit(1000).get()
                .addOnSuccessListener { snap ->
                    clearList()
                    val byUid = snap.documents.groupBy { it.getString("uid").orEmpty() }
                    val active = hosts.filter { it.getString("hostStatus") == "verified" }
                    val present = active.count { h -> byUid[h.id].orEmpty().any { it.getString("date") == today } }
                    setInfo("Absensi hari ini: $present / ${active.size} host hadir")
                    for (h in active) {
                        val rec = byUid[h.id].orEmpty()
                        val hadir = rec.any { it.getString("date") == today }
                        val bulan = rec.count { it.getString("date").orEmpty().startsWith(month) }
                        addRow("🎙️ ${nick(h)}", "Hari ini: ${if (hadir) "✅ hadir" else "❌ belum"} • Bulan ini: $bulan hari")
                    }
                }
                .addOnFailureListener { toast("Gagal: ${it.message}") }
        }
    }

    // ---------- Host: dashboard + absen ----------
    private fun loadHostHome() {
        clearActions()
        val me = myDoc ?: return
        val today = ymd.format(Date())
        val month = today.substring(0, 6)
        val verified = me.getString("hostStatus") == "verified"

        db.collection("host_attendance").whereEqualTo("uid", myUid).limit(100).get()
            .addOnSuccessListener { snap ->
                clearList()
                val dates = snap.documents.mapNotNull { it.getString("date") }
                val hadir = dates.contains(today)
                val bulan = dates.count { it.startsWith(month) }

                setInfo("🎙️ ${nick(me)} • Lv ${lv(me)} • 🪙 ${fmt(num(me, "coins"))}")
                addRow(
                    "Status host",
                    if (verified) "✅ Aktif" else "⏳ Menunggu verifikasi agency"
                )
                addRow("Agency", me.getString("agencyName").orEmpty().ifEmpty { "—" })
                addRow("Absensi", "Hari ini: ${if (hadir) "✅ sudah absen" else "❌ belum"} • Bulan ini: $bulan hari")
                if (verified && !hadir) addAction("📅 Absen hari ini") { absen(today) }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun absen(today: String) {
        val data = mapOf<String, Any>(
            "uid" to myUid,
            "agencyUid" to (myDoc?.getString("agencyUid") ?: ""),
            "date" to today,
            "at" to System.currentTimeMillis()
        )
        db.collection("host_attendance").document("${myUid}_$today").set(data)
            .addOnSuccessListener { toast("✅ Absen tercatat"); loadHostHome() }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------- Ranking host ----------
    private fun loadRanking() {
        usersByRole(Role.HOST, 200)
            .addOnSuccessListener { snap ->
                clearList()
                val top = snap
                    .filter { it.getString("hostStatus") != "pending" }
                    .sortedWith(compareByDescending<DocumentSnapshot> { lv(it) }.thenByDescending { num(it, "coins") })
                    .take(50)
                setInfo(if (top.isEmpty()) "Belum ada host" else "Top ${top.size} host berdasarkan level")
                for ((i, h) in top.withIndex()) {
                    addRow("${medal(i)} ${nick(h)}", "Lv ${lv(h)} • ${h.getString("agencyName").orEmpty()}")
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }
}
