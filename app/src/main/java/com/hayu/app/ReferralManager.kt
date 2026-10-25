package com.hayu.app

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import java.util.Calendar
import java.util.TimeZone

class ReferralException(message: String) : Exception(message)

/**
 * Fitur "Undang Teman Dapat Koin".
 *
 * Alur:
 * 1. Tiap user punya kode undangan 6 karakter  -> users/{uid}.inviteCode + invite_codes/{CODE}
 * 2. Teman baru mengisi kode (maks. redeemWindowHours jam setelah daftar)
 *    -> referrals/{inviteeUid} status "pending", teman langsung dapat inviteeReward.
 * 3. Teman jadi AKTIF kalau profil lengkap + check-in >= activeCheckinDays hari
 *    -> status "active", pengundang dapat inviterReward.
 * 4. Top up teman: bonus top up pertama + persen dari tiap top up (lihat prepareTopup/commitTopup).
 *
 * Semua angka bisa diubah lewat Firestore: app_config/referral (tanpa update aplikasi).
 */
object ReferralManager {

    const val CODE_LEN = 6
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // tanpa 0/O/1/I
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    private val WIB: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    data class Config(
        val inviterReward: Long = 5000L,       // untuk pengundang saat teman aktif
        val inviteeReward: Long = 3000L,       // untuk teman saat mengisi kode
        val redeemWindowHours: Long = 24L,     // batas isi kode setelah daftar
        val activeCheckinDays: Long = 3L,      // syarat aktif: total hari check-in
        val activeWithinDays: Long = 7L,       // syarat aktif harus tercapai dalam N hari
        val topupFirstMin: Long = 50000L,      // top up pertama minimal
        val topupFirstBonus: Long = 10000L,    // bonus top up pertama
        val topupFirstDays: Long = 7L,         // jendela waktu top up pertama
        val topupPercent: Long = 5L,           // % dari tiap top up teman
        val topupPercentDays: Long = 90L       // jendela waktu persen
    )

    fun parseConfig(d: DocumentSnapshot?): Config {
        val def = Config()
        fun n(key: String, fallback: Long): Long {
            val v = (d?.get(key) as? Number)?.toLong() ?: return fallback
            return if (v >= 0L) v else fallback
        }
        return Config(
            inviterReward = n("inviterReward", def.inviterReward),
            inviteeReward = n("inviteeReward", def.inviteeReward),
            redeemWindowHours = n("redeemWindowHours", def.redeemWindowHours),
            activeCheckinDays = n("activeCheckinDays", def.activeCheckinDays),
            activeWithinDays = n("activeWithinDays", def.activeWithinDays),
            topupFirstMin = n("topupFirstMin", def.topupFirstMin),
            topupFirstBonus = n("topupFirstBonus", def.topupFirstBonus),
            topupFirstDays = n("topupFirstDays", def.topupFirstDays),
            topupPercent = n("topupPercent", def.topupPercent),
            topupPercentDays = n("topupPercentDays", def.topupPercentDays)
        )
    }

    fun friendlyError(e: Exception?): String {
        var t: Throwable? = e
        while (t != null) {
            if (t is ReferralException) return t.message ?: "Gagal"
            t = t.cause
        }
        return e?.message ?: "Gagal"
    }

    fun startOfTodayWib(): Long {
        val c = Calendar.getInstance(WIB)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun cfgRef(db: FirebaseFirestore) = db.collection("app_config").document("referral")

    // ───────────────────────── KODE UNDANGAN ─────────────────────────

    private fun randomCode(): String =
        (1..CODE_LEN).map { CODE_CHARS.random() }.joinToString("")

    /** Ambil kode undangan user; kalau belum punya, dibuat (unik) lalu disimpan. */
    fun ensureInviteCode(db: FirebaseFirestore, uid: String, onDone: (String?) -> Unit) {
        val userRef = db.collection("users").document(uid)
        userRef.get().addOnSuccessListener { u ->
            val existing = u.getString("inviteCode")
            if (!existing.isNullOrBlank()) {
                onDone(existing)
            } else {
                createCode(db, uid, 0, onDone)
            }
        }.addOnFailureListener { onDone(null) }
    }

    private fun createCode(db: FirebaseFirestore, uid: String, attempt: Int, onDone: (String?) -> Unit) {
        if (attempt >= 6) { onDone(null); return }
        val code = randomCode()
        val codeRef = db.collection("invite_codes").document(code)
        val userRef = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val c = tx.get(codeRef)
            if (c.exists()) throw ReferralException("kode bentrok")
            tx.set(codeRef, mapOf<String, Any>("uid" to uid, "createdAt" to System.currentTimeMillis()))
            tx.set(userRef, mapOf<String, Any>("inviteCode" to code), SetOptions.merge())
            code
        }.addOnSuccessListener { onDone(code) }
            .addOnFailureListener { createCode(db, uid, attempt + 1, onDone) }
    }

    // ───────────────────────── ISI KODE (TEMAN BARU) ─────────────────────────

    fun redeem(ctx: Context, rawCode: String, onResult: (Boolean, String) -> Unit) {
        val fbUser = FirebaseAuth.getInstance().currentUser
        if (fbUser == null) { onResult(false, "Silakan login dulu"); return }
        val code = rawCode.trim().uppercase()
        if (code.length != CODE_LEN) { onResult(false, "Kode undangan harus $CODE_LEN karakter"); return }

        val db = FirebaseFirestore.getInstance()
        val uid = fbUser.uid
        val created = fbUser.metadata?.creationTimestamp ?: 0L
        val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            .takeUnless { it.isNullOrBlank() } ?: uid

        val codeRef = db.collection("invite_codes").document(code)
        val meRef = db.collection("users").document(uid)
        val refRef = db.collection("referrals").document(uid)
        val devRef = db.collection("referral_devices").document(deviceId)

        db.runTransaction { tx ->
            // ── semua READ dulu ──
            val cfg = parseConfig(tx.get(cfgRef(db)))
            val codeSnap = tx.get(codeRef)
            val me = tx.get(meRef)
            val refSnap = tx.get(refRef)
            val dev = tx.get(devRef)

            val inviterUid = codeSnap.getString("uid")
            if (!codeSnap.exists() || inviterUid.isNullOrBlank()) throw ReferralException("Kode undangan tidak ditemukan")
            if (inviterUid == uid) throw ReferralException("Tidak bisa memakai kode sendiri")
            if (!me.exists()) throw ReferralException("Akun tidak ditemukan")
            if (refSnap.exists() || !me.getString("invitedBy").isNullOrBlank())
                throw ReferralException("Kamu sudah pernah mengisi kode undangan")

            val now = System.currentTimeMillis()
            if (created > 0L && now - created > cfg.redeemWindowHours * HOUR)
                throw ReferralException("Kode hanya bisa diisi maks. ${cfg.redeemWindowHours} jam setelah daftar")
            if (dev.exists() && dev.getString("uid") != uid)
                throw ReferralException("Perangkat ini sudah dipakai akun lain untuk program undangan")

            val inviter = tx.get(db.collection("users").document(inviterUid))
            if (!inviter.exists()) throw ReferralException("Pemilik kode tidak ditemukan")

            // ── baru WRITE ──
            val name = me.getString("nickname").orEmpty()
            val coins = (me.getLong("coins") ?: 0L) + cfg.inviteeReward
            tx.update(
                meRef, mapOf<String, Any>(
                    "coins" to coins,
                    "totalCoinsEarned" to ((me.getLong("totalCoinsEarned") ?: 0L) + cfg.inviteeReward),
                    "invitedBy" to inviterUid
                )
            )
            tx.set(
                refRef, mapOf<String, Any>(
                    "inviterUid" to inviterUid,
                    "inviteeUid" to uid,
                    "inviteeName" to name,
                    "code" to code,
                    "status" to "pending",
                    "createdAt" to now,
                    "expiresAt" to now + cfg.activeWithinDays * DAY,
                    "deviceId" to deviceId,
                    "inviteeReward" to cfg.inviteeReward,
                    "inviterEarned" to 0L,
                    "topupTotal" to 0L,
                    "firstTopupDone" to false
                )
            )
            tx.set(devRef, mapOf<String, Any>("uid" to uid, "at" to now))
            ledger(db, tx, "referral_invitee", uid, name, cfg.inviteeReward, coins)
            cfg.inviteeReward
        }.addOnSuccessListener { reward ->
            onResult(true, "Kode berhasil dipakai! +$reward koin 🎉")
        }.addOnFailureListener { e ->
            onResult(false, friendlyError(e))
        }
    }

    // ───────────────────────── AKTIVASI (TEMAN AKTIF) ─────────────────────────

    private var lastCheck = 0L

    /** Dipanggil dari sisi TEMAN (aman dipanggil sering; ringan & di-throttle). */
    fun checkActivation(force: Boolean = false) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 60_000L) return
        lastCheck = now
        val db = FirebaseFirestore.getInstance()
        db.collection("referrals").document(uid).get().addOnSuccessListener { r ->
            if (r.exists() && r.getString("status") == "pending") activate(db, uid)
        }
    }

    private fun activate(db: FirebaseFirestore, inviteeUid: String) {
        val refRef = db.collection("referrals").document(inviteeUid)
        val meRef = db.collection("users").document(inviteeUid)
        db.runTransaction { tx ->
            val ref = tx.get(refRef)
            if (!ref.exists() || ref.getString("status") != "pending") return@runTransaction 0L
            val inviterUid = ref.getString("inviterUid") ?: return@runTransaction 0L
            val cfg = parseConfig(tx.get(cfgRef(db)))
            val me = tx.get(meRef)
            val inviterRef = db.collection("users").document(inviterUid)
            val inviter = tx.get(inviterRef)

            val now = System.currentTimeMillis()
            val expiresAt = ref.getLong("expiresAt") ?: 0L
            if (expiresAt > 0L && now > expiresAt) {
                tx.update(refRef, mapOf<String, Any>("status" to "expired"))
                return@runTransaction 0L
            }

            val onboarded = me.getBoolean("onboarded") ?: false
            val days = me.getLong("checkinTotal") ?: 0L
            if (!onboarded || days < cfg.activeCheckinDays || !inviter.exists()) return@runTransaction 0L

            val reward = cfg.inviterReward
            val name = me.getString("nickname").orEmpty()
            val coins = (inviter.getLong("coins") ?: 0L) + reward
            tx.update(
                inviterRef, mapOf<String, Any>(
                    "coins" to coins,
                    "totalCoinsEarned" to ((inviter.getLong("totalCoinsEarned") ?: 0L) + reward),
                    "referralEarned" to ((inviter.getLong("referralEarned") ?: 0L) + reward),
                    "referralActiveCount" to ((inviter.getLong("referralActiveCount") ?: 0L) + 1L)
                )
            )
            tx.update(
                refRef, mapOf<String, Any>(
                    "status" to "active",
                    "activatedAt" to now,
                    "inviteeName" to name,
                    "inviterEarned" to ((ref.getLong("inviterEarned") ?: 0L) + reward)
                )
            )
            ledger(db, tx, "referral_inviter", inviterUid, inviter.getString("nickname").orEmpty(), reward, coins)
            event(db, tx, inviterUid, inviteeUid, name, "active", reward)
            reward
        }
    }

    // ───────────────────────── BONUS TOP UP ─────────────────────────

    class TopupPlan(
        val refRef: DocumentReference,
        val inviterRef: DocumentReference,
        val inviter: DocumentSnapshot,
        val inviteeUid: String,
        val inviteeName: String,
        val firstBonus: Long,
        val percentReward: Long,
        val prevEarned: Long,
        val prevTopupTotal: Long
    )

    /** Panggil di dalam transaksi top up SEBELUM ada write. Mengembalikan null kalau tidak ada undangan. */
    fun prepareTopup(db: FirebaseFirestore, tx: Transaction, inviteeUid: String, amount: Long): TopupPlan? {
        val refRef = db.collection("referrals").document(inviteeUid)
        val ref = tx.get(refRef)
        if (!ref.exists()) return null
        if (ref.getString("status") == "expired") return null
        val inviterUid = ref.getString("inviterUid") ?: return null
        val cfg = parseConfig(tx.get(cfgRef(db)))
        val inviterRef = db.collection("users").document(inviterUid)
        val inviter = tx.get(inviterRef)
        if (!inviter.exists()) return null

        val elapsed = System.currentTimeMillis() - (ref.getLong("createdAt") ?: 0L)
        val percent =
            if (cfg.topupPercent > 0L && elapsed <= cfg.topupPercentDays * DAY) amount * cfg.topupPercent / 100L else 0L
        val firstDone = ref.getBoolean("firstTopupDone") ?: false
        val bonus =
            if (!firstDone && amount >= cfg.topupFirstMin && elapsed <= cfg.topupFirstDays * DAY) cfg.topupFirstBonus else 0L

        return TopupPlan(
            refRef, inviterRef, inviter, inviteeUid, ref.getString("inviteeName").orEmpty(),
            bonus, percent, ref.getLong("inviterEarned") ?: 0L, ref.getLong("topupTotal") ?: 0L
        )
    }

    /** Panggil di dalam transaksi top up SETELAH semua read selesai. */
    fun commitTopup(db: FirebaseFirestore, tx: Transaction, plan: TopupPlan, amount: Long) {
        val total = plan.firstBonus + plan.percentReward
        tx.update(
            plan.refRef, mapOf<String, Any>(
                "firstTopupDone" to true,
                "topupTotal" to plan.prevTopupTotal + amount,
                "inviterEarned" to plan.prevEarned + total
            )
        )
        if (total <= 0L) return

        val coins = (plan.inviter.getLong("coins") ?: 0L) + total
        tx.update(
            plan.inviterRef, mapOf<String, Any>(
                "coins" to coins,
                "totalCoinsEarned" to ((plan.inviter.getLong("totalCoinsEarned") ?: 0L) + total),
                "referralEarned" to ((plan.inviter.getLong("referralEarned") ?: 0L) + total)
            )
        )
        val inviterName = plan.inviter.getString("nickname").orEmpty()
        val inviterUid = plan.inviterRef.id
        if (plan.firstBonus > 0L) {
            ledger(db, tx, "referral_topup_first", inviterUid, inviterName, plan.firstBonus, coins - plan.percentReward)
            event(db, tx, inviterUid, plan.inviteeUid, plan.inviteeName, "topup_first", plan.firstBonus)
        }
        if (plan.percentReward > 0L) {
            ledger(db, tx, "referral_topup_percent", inviterUid, inviterName, plan.percentReward, coins)
            event(db, tx, inviterUid, plan.inviteeUid, plan.inviteeName, "topup_percent", plan.percentReward)
        }
    }

    // ───────────────────────── HELPER TULIS ─────────────────────────

    private fun ledger(
        db: FirebaseFirestore, tx: Transaction, type: String,
        uid: String, name: String, delta: Long, balanceAfter: Long
    ) {
        tx.set(
            db.collection("coin_ledger").document(), mapOf<String, Any>(
                "type" to type, "uid" to uid, "name" to name,
                "delta" to delta, "balanceAfter" to balanceAfter,
                "byUid" to "", "at" to System.currentTimeMillis()
            )
        )
    }

    private fun event(
        db: FirebaseFirestore, tx: Transaction, inviterUid: String,
        inviteeUid: String, inviteeName: String, type: String, amount: Long
    ) {
        tx.set(
            db.collection("referral_events").document(), mapOf<String, Any>(
                "inviterUid" to inviterUid, "inviteeUid" to inviteeUid,
                "inviteeName" to inviteeName, "type" to type,
                "amount" to amount, "at" to System.currentTimeMillis()
            )
        )
    }
}
