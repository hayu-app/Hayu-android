#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch 6: DOMPET + TOP UP KOIN (pilih paket & harga)
#  Klik "Dompet" di profil -> pilih paket: 15.000 koin = Rp5.000, dst (rasio 3 koin : Rp1)
#  Paket diambil dari "Paket & Harga Koin" (admin bisa ubah); kalau kosong dipakai paket standar.
#   cd ~/Hayu-android && bash patch-wallet.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
[ -f "$D/CoinAdminActivity.kt" ] || { echo "❌ Jalankan patch-koin.sh dulu"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python

echo "▶ 1/4 WalletActivity.kt"
cat > "$D/WalletActivity.kt" <<'EOF'
package com.hayu.app

import android.content.Intent
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dompet: saldo, klaim koin harian, pilih paket top up (koin + harga), dan riwayat top up. */
class WalletActivity : SimplePageActivity() {

    override val pageTitle: String get() = "Dompet"

    private class Pack(val coins: Long, val price: Long)

    // Paket terkecil yang ditampilkan: 15.000 koin = Rp5.000 (ubah angka ini kalau perlu)
    private val MIN_COINS = 15_000L

    // Paket standar (rasio 3 koin = Rp1). Dipakai kalau admin belum mengisi "Paket & Harga Koin".
    private val defaults = listOf(
        Pack(15_000, 5_000),
        Pack(30_000, 10_000),
        Pack(75_000, 25_000),
        Pack(150_000, 50_000),
        Pack(300_000, 100_000),
        Pack(750_000, 250_000),
        Pack(1_500_000, 500_000)
    )

    private fun fmt(v: Long): String = String.format(Locale("id", "ID"), "%,d", v)
    private fun rp(v: Long): String = "Rp ${fmt(v)}"
    private fun timeStr(ms: Long): String = SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(ms))

    override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
        val coins = user.getLong("coins") ?: 0L
        val nick = user.getString("nickname") ?: ""

        container.addView(Ui.text(this, "Saldo kamu", 12f, Ui.GRAY))
        container.addView(Ui.text(this, "🪙 ${fmt(coins)} koin", 26f, Ui.GOLD, true))

        container.addView(
            Ui.row(
                this, "🎁", "Koin Harian", "Klaim koin gratis tiap hari",
                Ui.button(this, "Klaim") { startActivity(Intent(this, DailyCoinActivity::class.java)) }
            )
        )

        container.addView(Ui.text(this, "\nTop Up Koin", 12f, Ui.GRAY))
        val packBox = LinearLayout(this)
        packBox.orientation = LinearLayout.VERTICAL
        container.addView(packBox)

        val histBox = LinearLayout(this)
        histBox.orientation = LinearLayout.VERTICAL
        container.addView(histBox)

        loadPackages(packBox, nick)
        loadHistory(histBox)
    }

    private fun loadPackages(box: LinearLayout, nick: String) {
        db.collection("coin_packages").get()
            .addOnSuccessListener { snap ->
                val fromDb = snap.documents
                    .filter { it.getBoolean("active") != false }
                    .map {
                        Pack(
                            (it.get("coins") as? Number)?.toLong() ?: 0L,
                            (it.get("price") as? Number)?.toLong() ?: 0L
                        )
                    }
                    .filter { it.coins >= MIN_COINS && it.price > 0L }
                    .sortedBy { it.coins }
                showPacks(box, if (fromDb.isEmpty()) defaults else fromDb, nick)
            }
            .addOnFailureListener { showPacks(box, defaults, nick) }
    }

    private fun showPacks(box: LinearLayout, packs: List<Pack>, nick: String) {
        for (p in packs) {
            box.addView(
                Ui.row(
                    this, "🪙", "${fmt(p.coins)} koin", "Harga ${rp(p.price)}",
                    Ui.button(this, rp(p.price)) { confirmBuy(p, nick) }
                )
            )
        }
    }

    private fun confirmBuy(p: Pack, nick: String) {
        AlertDialog.Builder(this)
            .setTitle("Beli ${fmt(p.coins)} koin")
            .setMessage("Harga ${rp(p.price)}.\nPesanan akan dikirim ke admin untuk dikonfirmasi setelah pembayaran.")
            .setPositiveButton("Buat Pesanan") { _, _ -> createOrder(p, nick) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createOrder(p: Pack, nick: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("topupRequests").whereEqualTo("uid", uid).limit(50).get()
            .addOnSuccessListener { snap ->
                val pending = snap.documents.count { it.getString("status") == "pending" }
                if (pending >= 3) {
                    toast("Masih ada $pending pesanan menunggu. Selesaikan dulu ya.")
                    return@addOnSuccessListener
                }
                val req = hashMapOf<String, Any>(
                    "uid" to uid,
                    "nickname" to nick,
                    "amount" to p.coins,
                    "price" to p.price,
                    "status" to "pending",
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("topupRequests").add(req)
                    .addOnSuccessListener { ref ->
                        showInstruction(ref.id, p)
                        reload()
                    }
                    .addOnFailureListener { toast("Gagal: ${it.message}") }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // Instruksi pembayaran bisa diatur admin di Firestore: app_config/payment -> field "info"
    private fun showInstruction(orderId: String, p: Pack) {
        val head = "Kode pesanan: ${orderId.take(6).uppercase()}\nTotal bayar: ${rp(p.price)}\n\n"
        val fallback = "Lakukan pembayaran sesuai instruksi admin/coinseller. " +
            "Koin masuk setelah pembayaran dikonfirmasi."
        db.collection("app_config").document("payment").get()
            .addOnSuccessListener { doc -> infoDialog(head + (doc.getString("info") ?: fallback)) }
            .addOnFailureListener { infoDialog(head + fallback) }
    }

    private fun infoDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Pesanan terkirim ✅")
            .setMessage(msg)
            .setPositiveButton("Mengerti", null)
            .show()
    }

    private fun loadHistory(box: LinearLayout) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("topupRequests").whereEqualTo("uid", uid).limit(30).get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }.take(10)
                if (docs.isEmpty()) return@addOnSuccessListener
                box.addView(Ui.text(this, "\nRiwayat top up", 12f, Ui.GRAY))
                for (d in docs) {
                    val amount = d.getLong("amount") ?: 0L
                    val price = d.getLong("price") ?: 0L
                    val status = when (d.getString("status")) {
                        "approved" -> "✅ Berhasil"
                        "rejected" -> "❌ Ditolak"
                        else -> "⏳ Menunggu"
                    }
                    val title = "${fmt(amount)} koin" + if (price > 0L) " • ${rp(price)}" else ""
                    box.addView(Ui.row(this, "🧾", title, "$status • ${timeStr(d.getLong("createdAt") ?: 0L)}", null))
                }
            }
    }
}
EOF

echo "▶ 2/4 Patch file lama (atomik)"
D="$D" python3 - <<'PY'
import os
D = os.environ["D"]
M = "app/src/main/AndroidManifest.xml"
files = {}
def get(p):
    if p not in files: files[p] = open(p, encoding="utf-8").read()
    return files[p]
def rep(p, old, new, optional=False):
    s = get(p)
    if new in s: pass
    elif old in s: files[p] = s.replace(old, new, 1)
    elif optional: print("  (lewati)", os.path.basename(p), old[:40])
    else: raise SystemExit("❌ GAGAL: %s -> tidak ketemu: %s" % (p, old[:70]))
def P(n): return os.path.join(D, n)

# Manifest
tag = '<activity android:name=".TopUpAdminActivity" android:exported="false" />'
rep(M, tag, tag + '\n            <activity android:name=".WalletActivity" android:exported="false" />')

# Klik Dompet di profil -> WalletActivity
rep(P("ProfileFragment.kt"),
    'view.findViewById<LinearLayout>(R.id.card_wallet).setOnClickListener {\n            startActivity(Intent(requireContext(), DailyCoinActivity::class.java))\n        }',
    'view.findViewById<LinearLayout>(R.id.card_wallet).setOnClickListener {\n            startActivity(Intent(requireContext(), WalletActivity::class.java))\n        }')

# Tombol Top Up di halaman VIP -> Dompet
rep(P("VipActivity.kt"),
    'Ui.button(this, "Top Up") { chooseTopUp() }',
    'Ui.button(this, "Top Up") { startActivity(android.content.Intent(this, WalletActivity::class.java)) }')

# Admin konfirmasi top up: tampilkan harga pesanan
t = P("TopUpAdminActivity.kt")
rep(t, 'val amount = (d.getLong("amount") ?: 0L).toInt()\n',
       'val amount = (d.getLong("amount") ?: 0L).toInt()\n                    val price = d.getLong("price") ?: 0L\n')
rep(t, '"ID: $idNum  •  🪙 $amount koin"',
       '"ID: $idNum  •  🪙 $amount koin" + (if (price > 0L) "  •  Rp $price" else "")')

# Paket & Harga Koin (admin): tombol isi paket standar
c = P("CoinAdminActivity.kt")
rep(c, '                if (isCoinAdmin()) addAction("➕ Paket baru") { packageDialog(null) }\n',
       '                if (isCoinAdmin()) addAction("➕ Paket baru") { packageDialog(null) }\n'
       '                if (isCoinAdmin()) addAction("🌱 Paket standar") { seedDefaults() }\n')
rep(c, '    // ---------------- LEDGER / PENJUALAN ----------------\n',
'''    // Isi paket standar (rasio 3 koin = Rp1) hanya jika koleksi masih kosong
    private fun seedDefaults() {
        db.collection("coin_packages").limit(1).get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    toast("Paket sudah ada. Hapus dulu kalau mau isi ulang.")
                    return@addOnSuccessListener
                }
                val batch = db.batch()
                val data = listOf(
                    15000L to 5000L, 30000L to 10000L, 75000L to 25000L, 150000L to 50000L,
                    300000L to 100000L, 750000L to 250000L, 1500000L to 500000L
                )
                for ((coins, price) in data) {
                    batch.set(
                        db.collection("coin_packages").document(),
                        mapOf<String, Any>(
                            "coins" to coins, "price" to price, "active" to true,
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                }
                batch.commit()
                    .addOnSuccessListener { toast("✅ Paket standar ditambahkan"); loadPackages() }
                    .addOnFailureListener { toast("Gagal: ${it.message}") }
            }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    // ---------------- LEDGER / PENJUALAN ----------------
''')

for p, s in files.items():
    open(p, "w", encoding="utf-8").write(s)
print("  ✔ %d file dipatch" % len(files))
PY

echo "▶ 3/4 Cek kurung"
for f in WalletActivity.kt CoinAdminActivity.kt TopUpAdminActivity.kt VipActivity.kt ProfileFragment.kt; do
  o=$(tr -cd '{' < "$D/$f" | wc -c); c=$(tr -cd '}' < "$D/$f" | wc -c)
  [ "$o" = "$c" ] || { echo "❌ kurung tidak seimbang di $f"; exit 1; }
done
echo "  ✔"

echo "▶ 4/4 Git"
if [ -n "$NO_GIT" ]; then echo "  (NO_GIT)"; exit 0; fi
git add -A
git commit -m "feat(wallet): Dompet pilih paket koin & harga (15.000 koin = Rp5.000), pesanan top-up + riwayat" || true
git push origin HEAD
echo "✅ Selesai"
