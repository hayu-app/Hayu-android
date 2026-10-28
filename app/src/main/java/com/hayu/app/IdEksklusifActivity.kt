package com.hayu.app

import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.Locale

// Struktur data ID:
//  users/{uid}.userId       : Long   -> ID angka (tetap ada, dipakai seluruh aplikasi)
//  users/{uid}.customId     : String -> ID teks (hanya bisa diatur lewat Panel Dev)
//  users/{uid}.customIdKey  : String -> customId huruf kecil (untuk pencarian & anti-kembar)
//  userIds/{kunci}          : { uid } -> pengunci supaya dua orang tidak bisa mengambil ID yang sama
object IdTools {
    const val USER_MIN = 6          // panjang ID angka untuk user biasa
    const val USER_MAX = 8
    const val PRICE = 50_000L       // biaya ganti ID (koin)
    const val MIN_VIP = 4           // butuh VIP / SVIP minimal level ini

    fun key(s: String) = s.trim().lowercase(Locale.ROOT)

    /** ID yang tampil: ID teks kalau ada, kalau tidak ID angka. */
    fun display(doc: DocumentSnapshot): String {
        val c = doc.getString("customId").orEmpty()
        if (c.isNotEmpty()) return c
        val n = (doc.get("userId") as? Number)?.toLong() ?: 0L
        return if (n > 0L) n.toString() else "-"
    }

    /** Validasi untuk Panel Dev: angka (1–15 digit, tidak diawali 0) atau teks 3–20 karakter. */
    fun validateAdmin(raw: String): String? {
        if (raw.isEmpty()) return "ID tidak boleh kosong"
        if (raw.all { it in '0'..'9' }) {
            if (raw.length > 15) return "ID angka maksimal 15 digit"
            if (raw[0] == '0') return "ID angka tidak boleh diawali 0"
            return null
        }
        if (!Regex("[A-Za-z0-9_.\\-]{3,20}").matches(raw)) {
            return "ID teks 3–20 karakter: huruf, angka, _ . -"
        }
        return null
    }

    /** Cek ID masih kosong (atau memang milik [selfUid]). */
    fun checkFree(
        db: FirebaseFirestore, raw: String, selfUid: String,
        onResult: (Boolean) -> Unit, onErr: (String) -> Unit
    ) {
        val k = key(raw)
        val users = db.collection("users")
        val tasks = mutableListOf<Task<QuerySnapshot>>()
        k.toLongOrNull()?.let { tasks.add(users.whereEqualTo("userId", it).limit(3).get()) }
        tasks.add(users.whereEqualTo("customIdKey", k).limit(3).get())
        Tasks.whenAllSuccess<QuerySnapshot>(tasks)
            .addOnSuccessListener { snaps ->
                val usedByOther = snaps.any { s -> s.documents.any { it.id != selfUid } }
                if (usedByOther) {
                    onResult(false)
                    return@addOnSuccessListener
                }
                db.collection("userIds").document(k).get()
                    .addOnSuccessListener { r ->
                        onResult(!r.exists() || r.getString("uid") == selfUid)
                    }
                    .addOnFailureListener { onErr(it.message ?: "gagal") }
            }
            .addOnFailureListener { onErr(it.message ?: "gagal") }
    }

    /**
     * Terapkan ID baru ke [targetUid]. Angka -> mengubah userId. Teks -> mengisi customId.
     * [price] > 0 = dipotong dari koin user. [requireVip] = syarat VIP/SVIP (untuk ganti ID sendiri).
     */
    fun apply(
        db: FirebaseFirestore, targetUid: String, raw: String,
        price: Long, requireVip: Boolean,
        onOk: () -> Unit, onFail: (String) -> Unit
    ) {
        val value = raw.trim()
        val k = key(value)
        val num = k.toLongOrNull()
        val userRef = db.collection("users").document(targetUid)
        val newRes = db.collection("userIds").document(k)

        db.runTransaction { tx ->
            val u = tx.get(userRef)
            val res = tx.get(newRes)
            if (res.exists() && res.getString("uid") != targetUid) {
                throw IllegalStateException("ID sudah dipakai")
            }
            if (requireVip) {
                val vip = (u.getLong("vipLevel") ?: 0L).toInt()
                val sv = VipActivity.svipLevel(u.getLong("svipPoints") ?: 0L)
                if (vip < MIN_VIP && sv < MIN_VIP) {
                    throw IllegalStateException("Butuh VIP $MIN_VIP atau SVIP $MIN_VIP ke atas")
                }
            }
            val oldNum = (u.get("userId") as? Number)?.toLong() ?: 0L
            val oldCustom = u.getString("customId").orEmpty()

            val upd = HashMap<String, Any>()
            var balanceAfter = 0L
            if (price > 0L) {
                val coins = u.getLong("coins") ?: 0L
                if (coins < price) throw IllegalStateException("Koin tidak cukup (butuh 🪙$price)")
                balanceAfter = coins - price
                upd["coins"] = balanceAfter
            }
            if (num != null) {
                upd["userId"] = num
                upd["customId"] = FieldValue.delete()
                upd["customIdKey"] = FieldValue.delete()
            } else {
                upd["customId"] = value
                upd["customIdKey"] = k
            }
            tx.update(userRef, upd)

            // lepas pengunci ID lama
            if (num != null && oldNum > 0L && oldNum != num) {
                tx.delete(db.collection("userIds").document(oldNum.toString()))
            }
            if (oldCustom.isNotEmpty() && key(oldCustom) != k) {
                tx.delete(db.collection("userIds").document(key(oldCustom)))
            }
            tx.set(newRes, hashMapOf<String, Any>("uid" to targetUid, "at" to System.currentTimeMillis()))

            if (price > 0L) {
                tx.set(
                    db.collection("coin_ledger").document(), hashMapOf<String, Any>(
                        "type" to "id_change", "uid" to targetUid,
                        "name" to (u.getString("nickname") ?: ""),
                        "delta" to -price, "balanceAfter" to balanceAfter,
                        "at" to System.currentTimeMillis()
                    )
                )
            }
            null
        }.addOnSuccessListener { onOk() }
            .addOnFailureListener { onFail(it.message ?: "gagal") }
    }
}

/** Profil -> ID Eksklusif: ganti ID angka 6–8 digit (butuh VIP 4 / SVIP 4, berbayar). */
class IdEksklusifActivity : SimplePageActivity() {

    override val pageTitle: String get() = "ID Eksklusif"

    private fun fmt(v: Long): String = String.format(Locale.US, "%,d", v).replace(',', '.')

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val vip = (user.getLong("vipLevel") ?: 0L).toInt()
        val sv = VipActivity.svipLevel(user.getLong("svipPoints") ?: 0L)
        val eligible = vip >= IdTools.MIN_VIP || sv >= IdTools.MIN_VIP
        val coins = user.getLong("coins") ?: 0L
        val current = IdTools.display(user)

        container.addView(Ui.text(this, "🆔 ID Eksklusif", 22f, Ui.WHITE_SOFT, true))
        container.addView(Ui.text(this, "Ganti ID-mu dengan angka pilihan sendiri.", 12f, Ui.GRAY))

        val cur = Ui.card(this)
        cur.addView(Ui.text(this, "ID kamu sekarang", 12f, Ui.GRAY))
        cur.addView(Ui.text(this, current, 26f, Ui.GOLD, true))
        container.addView(cur)

        val rules = Ui.card(this)
        rules.addView(Ui.text(this, "Syarat & biaya", 14f, Ui.WHITE_SOFT, true))
        rules.addView(
            Ui.text(
                this,
                "• ID berupa angka ${IdTools.USER_MIN}–${IdTools.USER_MAX} digit, tidak diawali 0\n" +
                    "• Butuh VIP ${IdTools.MIN_VIP} atau SVIP ${IdTools.MIN_VIP} ke atas  ${if (eligible) "✅" else "🔒"}\n" +
                    "• Biaya 🪙${fmt(IdTools.PRICE)}  (saldomu 🪙${fmt(coins)})\n" +
                    "• ID harus belum dipakai user lain",
                12f, Ui.GRAY
            )
        )
        container.addView(rules)

        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(IdTools.USER_MAX))
        et.hint = "ID baru (${IdTools.USER_MIN}–${IdTools.USER_MAX} angka)"
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Color.parseColor("#8892AA"))
        et.textSize = 18f
        container.addView(et)

        val btn = Ui.button(this, "Ganti ID", eligible) { submit(et.text.toString().trim(), current) }
        container.addView(btn)
        if (!eligible) {
            container.addView(Ui.text(this, "\nBeli VIP atau SVIP level ${IdTools.MIN_VIP} dulu untuk membuka fitur ini.", 12f, Ui.GRAY))
        }
    }

    private fun submit(s: String, current: String) {
        val ok = Regex("[1-9][0-9]{${IdTools.USER_MIN - 1},${IdTools.USER_MAX - 1}}").matches(s)
        if (!ok) {
            toast("ID harus ${IdTools.USER_MIN}–${IdTools.USER_MAX} digit angka dan tidak diawali 0")
            return
        }
        if (s == current) { toast("Itu sudah ID kamu"); return }
        val uid = auth.currentUser?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Ganti ID ke $s?")
            .setMessage("Biaya 🪙${fmt(IdTools.PRICE)}. ID lamamu akan dilepas dan bisa dipakai orang lain.")
            .setPositiveButton("Ganti") { _, _ ->
                IdTools.checkFree(db, s, uid,
                    onResult = { free ->
                        if (!free) {
                            toast("ID $s sudah dipakai, coba yang lain")
                        } else {
                            IdTools.apply(db, uid, s, IdTools.PRICE, true,
                                onOk = { toast("ID berhasil diganti 🎉"); reload() },
                                onFail = { toast("Gagal: $it") })
                        }
                    },
                    onErr = { toast("Gagal cek ID: $it") })
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
