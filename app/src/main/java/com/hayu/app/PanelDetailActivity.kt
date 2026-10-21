package com.hayu.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PanelDetailActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // konten per panel
    private val panelContent = mapOf(
        "dashboard" to listOf(
            "📊 Total User Terdaftar",
            "👥 User Aktif Hari Ini",
            "🏠 Room Aktif",
            "🪙 Total Koin Beredar",
            "💰 Pendapatan Hari Ini",
            "📈 Grafik Pertumbuhan",
            "🎁 Gift Terkirim Hari Ini",
            "⚠️ Laporan Masuk"
        ),
        "user" to listOf(
            "🔍 Cari User by UID",
            "👤 Profil & Avatar",
            "🔐 Status Akun",
            "⭐ Level & EXP",
            "👑 Status VIP",
            "🟢 Status Online",
            "👫 Friend & Follow",
            "🚫 Block & Mute List",
            "✅ Verifikasi Akun",
            "🔨 Ban & Suspension",
            "📱 Login History",
            "🗑️ Hapus Akun"
        ),
        "room" to listOf(
            "📡 Monitor Room Aktif",
            "📂 Kategori Room",
            "🔓 Room Publik & Privat",
            "➕ Buat & Edit Room",
            "👑 Owner & Moderator",
            "🎤 Kelola Mic & Seat",
            "💬 Chat Room",
            "🔒 Password & Kunci Room",
            "🎁 Gift Room",
            "📊 Popularitas Room",
            "⚠️ Laporan Room"
        ),
        "coin" to listOf(
            "📦 Paket & Harga Koin",
            "💳 Saldo Koin User",
            "🛒 Top-up & Pembelian",
            "📋 Ledger Transaksi",
            "🎁 Toko Gift",
            "✨ Animasi Gift",
            "📤 Kirim & Terima Gift",
            "🏆 Ranking Gift",
            "💎 Bonus & Reward Koin",
            "↩️ Refund Transaksi"
        ),
        "host" to listOf(
            "🎙️ Dashboard Host",
            "🏠 Kelola Room",
            "🎁 Gift Diterima",
            "💎 Pendapatan",
            "⭐ Level & EXP Host",
            "✅ Tugas Harian",
            "📅 Absensi & Jam Aktif",
            "🏆 Ranking Host",
            "💰 Komisi & Reward",
            "🏦 Penarikan Dana"
        ),
        "agency" to listOf(
            "🏢 Dashboard Agency",
            "➕ Rekrut Host",
            "✅ Verifikasi Host",
            "📅 Absensi Host",
            "📊 Performa Host",
            "🎯 Target Agency",
            "💰 Komisi & Reward",
            "🏦 Penarikan Dana",
            "👥 Manajemen Member"
        ),
        "bd" to listOf(
            "💼 Dashboard BD",
            "🏢 Daftarkan Agency",
            "✅ Verifikasi Agency",
            "📊 Performa Agency & Host",
            "🎯 Target BD",
            "💰 Komisi & Bonus",
            "📋 Laporan Rekrutmen",
            "🏆 Ranking Agency"
        ),
        "coinseller" to listOf(
            "🪙 Dashboard Coinseller",
            "🔍 Cari UID Customer",
            "📦 Paket Koin",
            "📋 Order Masuk",
            "✅ Verifikasi Pembayaran",
            "📤 Kirim Koin",
            "💼 Wallet Coinseller",
            "📊 Laporan Penjualan",
            "↩️ Refund Request"
        ),
        "finance" to listOf(
            "💰 Total Pendapatan",
            "📋 Ledger Transaksi",
            "💳 Rekonsiliasi Pembayaran",
            "🏦 Kelola Deposit",
            "💼 Komisi BD/Agency/Host",
            "↩️ Manajemen Refund",
            "✅ Persetujuan Penarikan",
            "📊 Laporan Keuangan",
            "📤 Export Data"
        ),
        "moderator" to listOf(
            "📡 Monitor Room Aktif",
            "⚠️ Laporan User & Room",
            "💬 Laporan Chat",
            "🔇 Mute & Kick",
            "🔨 Temporary Ban",
            "🚫 Laporan Spam & Abuse",
            "⚡ Warning Management",
            "📋 Appeal Management",
            "📅 Histori Moderasi"
        ),
        "cs" to listOf(
            "🎧 Dashboard CS",
            "📩 Komplain User",
            "🔐 Pemulihan Akun",
            "🪙 Komplain Top-up",
            "💳 Komplain Transaksi",
            "↩️ Request Refund",
            "🎫 Support Ticket",
            "📋 Histori Response",
            "📊 Laporan Support"
        ),
        "vip" to listOf(
            "👑 Paket & Benefit VIP",
            "📅 Langganan & Kadaluarsa",
            "⭐ Aturan Level & EXP",
            "🎙️ Level & Reward Host",
            "🏆 Ranking Kekayaan",
            "🌟 Ranking Popularitas",
            "📊 Leaderboard Global",
            "🏅 Achievement & Badge"
        ),
        "event" to listOf(
            "🎉 Dashboard Event",
            "➕ Buat & Edit Event",
            "📅 Jadwal & Banner",
            "👥 Peserta Event",
            "🏆 Ranking & Reward Event",
            "✅ Misi Harian/Mingguan",
            "📅 Reward Check-in",
            "🎟️ Lucky Draw",
            "📊 Laporan Event"
        ),
        "security" to listOf(
            "⚠️ Laporan User & Room",
            "🔍 Deteksi Fraud",
            "🚫 Deteksi Spam",
            "✅ Verifikasi Akun",
            "🔨 Warning & Ban",
            "📋 Unban & Appeal",
            "🔐 Login Protection",
            "📊 Admin Activity Logs",
            "🔒 Audit Trail"
        ),
        "notif" to listOf(
            "📲 Push Notification",
            "🔔 In-App Notification",
            "📢 Pengumuman Sistem",
            "🛠️ Pengumuman Maintenance",
            "🎉 Notif Event",
            "💳 Notif Transaksi",
            "🎁 Notif Gift",
            "📋 Template Notifikasi",
            "📅 Histori Notifikasi"
        ),
        "system" to listOf(
            "⚙️ Konfigurasi Aplikasi",
            "🔌 API Management",
            "📚 API Documentation",
            "🗄️ Status Database",
            "💾 Storage Management",
            "📦 Cache Management",
            "📡 Server Monitoring",
            "🐛 Error Logs",
            "💾 Backup & Restore",
            "🛠️ Maintenance Mode",
            "📱 Versi Aplikasi",
            "🏳️ Feature Flags"
        ),
        "report" to listOf(
            "👥 Total User Terdaftar",
            "📈 DAU & MAU",
            "🏠 Room Aktif",
            "📊 Pertumbuhan User",
            "🎙️ Performa Host & Agency",
            "💼 Performa BD",
            "🪙 Analitik Penjualan Koin",
            "🎁 Revenue Gift",
            "💰 Analitik Transaksi",
            "📤 Export CSV / Excel / PDF"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key   = intent.getStringExtra("panel_key") ?: "dashboard"
        val label = intent.getStringExtra("panel_label") ?: "Panel"
        val role  = intent.getStringExtra("my_role") ?: Role.USER

        val d = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#080818"))

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(Color.parseColor("#0D0D3D"))
        header.setPadding(d(16), d(14), d(16), d(14))

        val btnBack = TextView(this)
        btnBack.text = "‹  $label"
        btnBack.textSize = 18f
        btnBack.typeface = android.graphics.Typeface.DEFAULT_BOLD
        btnBack.setTextColor(Color.WHITE)
        btnBack.setOnClickListener { finish() }
        val backLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(btnBack, backLp)
        root.addView(header)

        // stats row (dashboard only)
        if (key == "dashboard") {
            buildDashboardStats(root, d)
        }

        // menu items
        val sv = ScrollView(this)
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(d(16), d(12), d(16), d(80))

        val items = (panelContent[key] ?: listOf("🚧 Panel dalam pengembangan")).toMutableList()
        val roleItem = "🎭 Kelola Role"
        val adminModes = mapOf(
            "Cari User by UID" to "search", "Cari UID Customer" to "search", "Status Akun" to "search",
            "Ban & Suspension" to "ban", "Temporary Ban" to "ban", "Warning & Ban" to "ban",
            "Verifikasi Akun" to "verify", "Saldo Koin User" to "coin", "Pemulihan Akun" to "recover"
        )
        val coinModes = mapOf(
            "Paket & Harga Koin" to "packages", "Paket Koin" to "packages",
            "Ledger Transaksi" to "ledger", "Dashboard Coinseller" to "wallet",
            "Wallet Coinseller" to "wallet", "Kirim Koin" to "send",
            "Laporan Penjualan" to "sales", "Top-up & Pembelian" to "topup"
        )
        val orgModes = mapOf(
            "Dashboard BD" to "agencies_dash", "Daftarkan Agency" to "register_agency",
            "Verifikasi Agency" to "verify_agency", "Performa Agency & Host" to "agencies_perf",
            "Laporan Rekrutmen" to "agencies_report", "Ranking Agency" to "agencies_rank",
            "Dashboard Agency" to "hosts_dash", "Rekrut Host" to "recruit_host",
            "Verifikasi Host" to "verify_host", "Absensi Host" to "attendance_agency",
            "Performa Host" to "hosts_perf", "Manajemen Member" to "hosts_manage",
            "Dashboard Host" to "host_home", "Absensi & Jam Aktif" to "host_home",
            "Ranking Host" to "ranking_host"
        )
        val csModes = mapOf(
            "Dashboard CS" to "stats", "Laporan Support" to "stats",
            "Support Ticket" to "tickets|", "Komplain User" to "tickets|complaint",
            "Komplain Top-up" to "tickets|topup", "Komplain Transaksi" to "tickets|transaksi",
            "Request Refund" to "tickets|refund", "Histori Response" to "history"
        )
        if ((key == "dashboard" || key == "user") && Role.canManageRoles(role)) {
            items.add(0, roleItem)
        }
        for (item in items) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(d(16), d(14), d(16), d(14))

            val gd = android.graphics.drawable.GradientDrawable()
            gd.setColor(Color.parseColor("#12122A"))
            gd.cornerRadius = d(12).toFloat()
            gd.setStroke(d(1), Color.parseColor("#2E2E50"))
            row.background = gd

            val tv = TextView(this)
            tv.text = item
            tv.textSize = 14f
            tv.setTextColor(Color.parseColor("#F1F5F9"))
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(tv, tvLp)

            val arrow = TextView(this)
            arrow.text = "›"
            arrow.textSize = 20f
            arrow.setTextColor(Color.parseColor("#8892AA"))
            row.addView(arrow)

            row.setOnClickListener {
                val adminMode = adminModes[item.substringAfter(' ')]
                val coinMode = coinModes[item.substringAfter(' ')]
                val orgMode = orgModes[item.substringAfter(' ')]
                val csMode = csModes[item.substringAfter(' ')]
                val plainName = item.substringAfter(' ')
                if (item == roleItem) {
                    startActivity(Intent(this, RoleManagerActivity::class.java))
                } else if (orgMode != null) {
                    val oi = Intent(this, OrgActivity::class.java)
                    oi.putExtra("mode", orgMode)
                    oi.putExtra("title", item)
                    startActivity(oi)
                } else if (csMode != null) {
                    val parts = csMode.split("|")
                    val ti = Intent(this, TicketAdminActivity::class.java)
                    ti.putExtra("mode", parts[0])
                    ti.putExtra("cat", parts.getOrElse(1) { "" })
                    ti.putExtra("title", item)
                    startActivity(ti)
                } else if (plainName == "Level & EXP Host") {
                    startActivity(Intent(this, LevelActivity::class.java))
                } else if (plainName == "Tugas Harian") {
                    startActivity(Intent(this, TugasActivity::class.java))
                } else if (coinMode == "topup") {
                    startActivity(Intent(this, TopUpAdminActivity::class.java))
                } else if (coinMode != null) {
                    val ci = Intent(this, CoinAdminActivity::class.java)
                    ci.putExtra("mode", coinMode)
                    ci.putExtra("title", item)
                    startActivity(ci)
                } else if (adminMode != null) {
                    val i = Intent(this, UserAdminActivity::class.java)
                    i.putExtra("mode", adminMode)
                    i.putExtra("title", item)
                    startActivity(i)
                } else {
                    Toast.makeText(this, "$item — segera tersedia", Toast.LENGTH_SHORT).show()
                }
            }

            val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rowLp.bottomMargin = d(8)
            container.addView(row, rowLp)
        }

        sv.addView(container)
        val svLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(sv, svLp)
        setContentView(root)
    }

    private fun buildDashboardStats(root: LinearLayout, d: (Int) -> Int) {
        val uid = auth.currentUser?.uid ?: return
        val statsRow = LinearLayout(this)
        statsRow.orientation = LinearLayout.HORIZONTAL
        statsRow.setPadding(d(12), d(12), d(12), 0)

        val stats = listOf("👥\nUser", "🏠\nRoom", "🪙\nKoin", "💰\nRevenue")
        for (s in stats) {
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.setPadding(d(8), d(12), d(8), d(12))
            val gd = android.graphics.drawable.GradientDrawable()
            gd.setColor(Color.parseColor("#12122A"))
            gd.cornerRadius = d(12).toFloat()
            gd.setStroke(d(1), Color.parseColor("#2E2E50"))
            tile.background = gd

            val tv = TextView(this)
            tv.text = s
            tv.textSize = 12f
            tv.setTextColor(Color.parseColor("#C4CAFF"))
            tv.gravity = Gravity.CENTER
            tile.addView(tv)

            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(d(4), 0, d(4), 0)
            statsRow.addView(tile, lp)
        }

        val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(statsRow, rowLp)

        // load real stats dari Firestore
        db.collection("users").get().addOnSuccessListener { users ->
            val count = users.size()
            (statsRow.getChildAt(0) as? LinearLayout)?.let { tile ->
                (tile.getChildAt(0) as? TextView)?.text = "👥\n$count\nUser"
            }
        }
        db.collection("rooms").whereEqualTo("isActive", true).get().addOnSuccessListener { rooms ->
            val count = rooms.size()
            (statsRow.getChildAt(1) as? LinearLayout)?.let { tile ->
                (tile.getChildAt(0) as? TextView)?.text = "🏠\n$count\nRoom"
            }
        }
    }
}
