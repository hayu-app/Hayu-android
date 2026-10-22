package com.hayu.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dompet: saldo + pilihan top up langsung tampil dalam grid 3 kolom.
 * Tap paket -> buat pesanan -> instruksi pembayaran. "Tagihan" = riwayat top up.
 * Top up (dan poin SVIP) hanya lewat halaman ini.
 *
 * Paket diambil dari koleksi coin_packages (field: coins, price, active, opsional oldCoins);
 * kalau kosong dipakai paket standar.
 */
class WalletActivity : AppCompatActivity() {

    private class Pack(val coins: Long, val price: Long, val oldCoins: Long = 0L)

    // Paket terkecil yang ditampilkan: 15.000 koin = Rp5.000
    private val minCoins = 15_000L

    // Paket standar (rasio 3 koin = Rp1). Dipakai kalau admin belum mengisi "Paket & Harga Koin".
    private val defaults = listOf(
        Pack(15_000, 5_000),
        Pack(30_000, 10_000),
        Pack(75_000, 25_000),
        Pack(150_000, 50_000),
        Pack(300_000, 100_000),
        Pack(750_000, 250_000),
        Pack(1_500_000, 500_000),
        Pack(3_000_000, 1_000_000),
        Pack(7_500_000, 2_500_000)
    )

    private val yellow = Color.rgb(255, 204, 96)
    private val ink = Color.rgb(45, 45, 50)

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var userListener: ListenerRegistration? = null
    private var nick = ""

    private lateinit var balanceText: TextView
    private lateinit var gridBox: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Long): String = String.format(Locale("id", "ID"), "%,d", v)
    private fun rp(v: Long): String = "Rp ${fmt(v)}"
    private fun timeStr(ms: Long): String =
        SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(ms))

    private fun txt(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = true) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        window.statusBarColor = yellow

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(yellow)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root, FrameLayout.LayoutParams(-1, -2))
        setContentView(scroll)

        // ---------- header kuning ----------
        val head = FrameLayout(this).apply { setPadding(dp(8), dp(8), dp(16), dp(44)) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(txt("‹", 34f).apply { setOnClickListener { finish() } },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(txt("Dompet", 19f), LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(txt("Tagihan", 15f, Color.WHITE, false).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setOnClickListener { showHistory() }
        }, LinearLayout.LayoutParams(dp(78), dp(48)))
        head.addView(bar, FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP))
        val bigCoin = TextView(this).apply {
            text = "🪙"
            textSize = 110f
            alpha = 0.28f
            includeFontPadding = false
        }
        head.addView(bigCoin, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(8); bottomMargin = dp(8)
        })
        balanceText = txt("…", 38f).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        head.addView(balanceText, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply {
            leftMargin = dp(16); bottomMargin = dp(8)
        })
        head.minimumHeight = dp(190)
        root.addView(head, LinearLayout.LayoutParams(-1, -2))

        // ---------- lembar putih ----------
        val r = dp(28).toFloat()
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(14), dp(18), dp(14), dp(24))
        }
        root.addView(sheet, LinearLayout.LayoutParams(-1, -2, 1f).apply { topMargin = -dp(28) })

        // banner
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(150, 55, 20), Color.rgb(230, 120, 30), Color.rgb(150, 55, 20))
            ).apply { cornerRadius = dp(12).toFloat() }
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        banner.addView(txt("💰 Top Up Koin", 20f, Color.rgb(255, 235, 170)))
        banner.addView(txt("1 koin top up = 1 poin SVIP", 12f, Color.WHITE, false))
        sheet.addView(banner, LinearLayout.LayoutParams(-1, -2))

        // grid paket (3 kolom)
        gridBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheet.addView(gridBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        gridBox.addView(txt("Memuat paket…", 13f, Color.GRAY, false))

        // koin harian
        val daily = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 244, 224)); cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { startActivity(Intent(this@WalletActivity, DailyCoinActivity::class.java)) }
        }
        daily.addView(txt("🎁", 26f), LinearLayout.LayoutParams(-2, -2))
        val dailyCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dailyCol.addView(txt("Koin Harian", 15f, ink).apply { gravity = Gravity.START })
        dailyCol.addView(txt("Klaim koin gratis tiap hari", 12f, Color.GRAY, false).apply { gravity = Gravity.START })
        daily.addView(dailyCol, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        daily.addView(txt("Klaim ›", 14f, Color.rgb(255, 110, 40)), LinearLayout.LayoutParams(-2, -2))
        sheet.addView(daily, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // umpan balik
        sheet.addView(txt("🎧  Umpan balik masalah", 15f, ink).apply {
            setPadding(0, dp(18), 0, dp(8))
            setOnClickListener { startActivity(Intent(this@WalletActivity, SupportActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // saldo realtime
        userListener = db.collection("users").document(uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            balanceText.text = fmt(snap.getLong("coins") ?: 0L)
            nick = snap.getString("nickname") ?: ""
        }
        loadPackages()
    }

    // ================= Paket =================

    private fun loadPackages() {
        db.collection("coin_packages").get()
            .addOnSuccessListener { snap ->
                val fromDb = snap.documents
                    .filter { it.getBoolean("active") != false }
                    .map {
                        Pack(
                            (it.get("coins") as? Number)?.toLong() ?: 0L,
                            (it.get("price") as? Number)?.toLong() ?: 0L,
                            (it.get("oldCoins") as? Number)?.toLong() ?: 0L
                        )
                    }
                    .filter { it.coins >= minCoins && it.price > 0L }
                    .sortedBy { it.coins }
                showPacks(if (fromDb.isEmpty()) defaults else fromDb)
            }
            .addOnFailureListener { showPacks(defaults) }
    }

    private fun showPacks(packs: List<Pack>) {
        gridBox.removeAllViews()
        packs.chunked(3).forEachIndexed { rowIdx, rowPacks ->
            val row = LinearLayout(this)
            rowPacks.forEachIndexed { col, p ->
                row.addView(packCard(p, rowIdx * 3 + col),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            }
            repeat(3 - rowPacks.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            }
            gridBox.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun packCard(p: Pack, idx: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(255, 246, 222), Color.rgb(253, 249, 240))
            ).apply { cornerRadius = dp(14).toFloat() }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setOnClickListener { confirmBuy(p) }
        }
        val icon = when {
            idx < 2 -> "🪙"
            idx < 4 -> "🪙🪙"
            idx < 6 -> "🪙🪙🪙"
            else -> "💰"
        }
        card.addView(txt(icon, 28f).apply { setPadding(0, dp(16), 0, dp(6)) },
            LinearLayout.LayoutParams(-2, -2))
        card.addView(txt(fmt(p.coins), 15f, ink).apply { maxLines = 1 },
            LinearLayout.LayoutParams(-2, -2))
        val old = txt(if (p.oldCoins > 0L) fmt(p.oldCoins) else " ", 12f, Color.GRAY, false).apply {
            if (p.oldCoins > 0L) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            maxLines = 1
        }
        card.addView(old, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2); bottomMargin = dp(12) })
        val price = txt(rp(p.price), 14f).apply {
            maxLines = 1
            setPadding(0, dp(10), 0, dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(255, 150, 100), Color.rgb(255, 95, 140))
            )
        }
        card.addView(price, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    // ================= Pesanan =================

    private fun confirmBuy(p: Pack) {
        AlertDialog.Builder(this)
            .setTitle("Beli ${fmt(p.coins)} koin")
            .setMessage("Harga ${rp(p.price)}.\nPesanan akan dikirim ke admin untuk dikonfirmasi setelah pembayaran.")
            .setPositiveButton("Buat Pesanan") { _, _ -> createOrder(p) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createOrder(p: Pack) {
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
                    .addOnSuccessListener { ref -> showInstruction(ref.id, p) }
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

    // ================= Tagihan (riwayat) =================

    private fun showHistory() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("topupRequests").whereEqualTo("uid", uid).limit(30).get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }.take(15)
                val msg = if (docs.isEmpty()) "Belum ada riwayat top up" else docs.joinToString("\n\n") { d ->
                    val amount = d.getLong("amount") ?: 0L
                    val price = d.getLong("price") ?: 0L
                    val status = when (d.getString("status")) {
                        "approved" -> "✅ Berhasil"
                        "rejected" -> "❌ Ditolak"
                        else -> "⏳ Menunggu"
                    }
                    "$status\n${fmt(amount)} koin" + (if (price > 0L) " • ${rp(price)}" else "") +
                        "\n${timeStr(d.getLong("createdAt") ?: 0L)}"
                }
                AlertDialog.Builder(this).setTitle("Tagihan / Riwayat top up")
                    .setMessage(msg).setPositiveButton("Tutup", null).show()
            }
            .addOnFailureListener { toast("Gagal memuat riwayat") }
    }

    override fun onDestroy() {
        userListener?.remove()
        super.onDestroy()
    }
}
