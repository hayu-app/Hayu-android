package com.hayu.app

import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.firebase.firestore.DocumentSnapshot

class TopUpAdminActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Konfirmasi Top Up"

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val roles = Role.rolesOf(user)
        if (Role.DEVELOPER !in roles && Role.SUPERADMIN !in roles) {
            toast("Kamu tidak punya akses")
            finish()
            return
        }

        container.addView(Ui.text(this, "Permintaan top up menunggu konfirmasi", 12f, Ui.GRAY))

        db.collection("topupRequests").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    container.addView(Ui.text(this, "\nTidak ada permintaan 🎉", 13f))
                    return@addOnSuccessListener
                }
                val gap = Ui.dp(this, 6)
                for (d in snap.documents.sortedBy { it.getLong("createdAt") ?: 0L }) {
                    val uid = d.getString("uid") ?: continue
                    val amount = (d.getLong("amount") ?: 0L).toInt()
                    val price = d.getLong("price") ?: 0L
                    val reqId = d.id
                    db.collection("users").document(uid).get().addOnSuccessListener { u ->
                        val name = u.getString("nickname") ?: uid.take(6)
                        val idNum = u.getLong("userId") ?: 0L

                        val box = LinearLayout(this)
                        box.orientation = LinearLayout.HORIZONTAL
                        box.addView(Ui.button(this, "Tolak") { decide(reqId, uid, false) })
                        val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        lp.marginStart = gap
                        box.addView(Ui.button(this, "Terima") { decide(reqId, uid, true) }, lp)

                        container.addView(Ui.row(this, "💳", name, "ID: $idNum  •  🪙 $amount koin" + (if (price > 0L) "  •  Rp $price" else ""), box))
                    }
                }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun decide(reqId: String, uid: String, approve: Boolean) {
        val reqRef = db.collection("topupRequests").document(reqId)
        val userRef = db.collection("users").document(uid)
        val adminUid = auth.currentUser?.uid ?: ""

        db.runTransaction { tx ->
            val r = tx.get(reqRef)
            if (r.getString("status") != "pending") throw IllegalStateException("Sudah diproses")
            val amount = r.getLong("amount") ?: 0L

            if (approve) {
                val u = tx.get(userRef)
                val oldPts = u.getLong("svipPoints") ?: 0L
                val newPts = oldPts + amount
                val oldLv = VipActivity.svipLevel(oldPts)
                val newLv = VipActivity.svipLevel(newPts)
                val upd = hashMapOf<String, Any>(
                    "coins" to ((u.getLong("coins") ?: 0L) + amount),
                    "svipPoints" to newPts,
                    "svipLevel" to newLv.toLong()
                )
                if (newLv > oldLv) upd["equippedTitle"] = "👑 SVIP$newLv"
                tx.update(userRef, upd)
                tx.set(
                    db.collection("coin_ledger").document(), mapOf<String, Any>(
                        "type" to "topup_approved", "uid" to uid,
                        "name" to (u.getString("nickname") ?: ""),
                        "delta" to amount,
                        "balanceAfter" to ((u.getLong("coins") ?: 0L) + amount),
                        "byUid" to adminUid, "at" to System.currentTimeMillis()
                    )
                )
            }

            tx.update(
                reqRef, mapOf(
                    "status" to (if (approve) "approved" else "rejected"),
                    "processedBy" to adminUid,
                    "processedAt" to System.currentTimeMillis()
                )
            )
            null
        }.addOnSuccessListener {
            toast(if (approve) "Top up disetujui ✅" else "Top up ditolak")
            reload()
        }.addOnFailureListener {
            toast("Gagal: ${it.message}")
            reload()
        }
    }
}
