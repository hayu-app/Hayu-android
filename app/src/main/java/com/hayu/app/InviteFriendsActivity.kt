package com.hayu.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Halaman "Undang Teman Dapat Koin".
 * Kode undangan + bagikan, isi kode (teman baru), statistik pendapatan, daftar teman, peringkat, aturan.
 */
class InviteFriendsActivity : AppCompatActivity() {

    private companion object {
        const val BG = "#2A0808"
        const val CARD = "#4A1212"
        const val GOLD = "#FBBF24"
        const val CREAM = "#FFE9B8"
        const val MUTED = "#D9A38F"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var content: LinearLayout

    private var cfg = ReferralManager.Config()
    private var me: DocumentSnapshot? = null
    private var myCode = ""
    private var referrals: List<DocumentSnapshot> = emptyList()
    private var events: List<DocumentSnapshot> = emptyList()
    private var ranking: List<DocumentSnapshot>? = null
    private var tab = 0 // 0 = pendapatan saya, 1 = peringkat

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(n: Long) = "%,d".format(n)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor(BG))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(d(8), d(8), d(16), d(8))
        val back = txt("←", 22f, "#FFFFFF", false)
        back.setPadding(d(10), d(6), d(14), d(6))
        back.setOnClickListener { finish() }
        bar.addView(back)
        bar.addView(txt("Undang Teman", 18f, "#FFFFFF", true))
        root.addView(bar)

        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(d(16), d(4), d(16), d(24))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        load()
    }

    // ───────────────────────── DATA ─────────────────────────

    private fun load() {
        val uid = auth.currentUser?.uid
        if (uid == null) { finish(); return }
        content.removeAllViews()
        content.addView(txt("Memuat...", 14f, MUTED, false).apply { setPadding(0, d(30), 0, 0) })

        db.collection("app_config").document("referral").get().addOnCompleteListener { cfgTask ->
            cfg = ReferralManager.parseConfig(if (cfgTask.isSuccessful) cfgTask.result else null)
            db.collection("users").document(uid).get()
                .addOnSuccessListener { u ->
                    me = u
                    ReferralManager.ensureInviteCode(db, uid) { code ->
                        myCode = code ?: ""
                        db.collection("referrals").whereEqualTo("inviterUid", uid).get()
                            .addOnSuccessListener { r ->
                                referrals = r.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                                db.collection("referral_events").whereEqualTo("inviterUid", uid).get()
                                    .addOnSuccessListener { e ->
                                        events = e.documents
                                        render()
                                    }
                                    .addOnFailureListener { fail(it.message) }
                            }
                            .addOnFailureListener { fail(it.message) }
                    }
                }
                .addOnFailureListener { fail(it.message) }
        }
    }

    private fun fail(msg: String?) {
        if (isFinishing || isDestroyed) return
        content.removeAllViews()
        content.addView(txt("Gagal memuat: ${msg ?: "coba lagi"}", 13f, MUTED, false).apply { setPadding(0, d(30), 0, d(12)) })
        content.addView(pill("↻ Coba lagi", true) { load() })
    }

    private fun loadRanking() {
        db.collection("users").orderBy("referralEarned", Query.Direction.DESCENDING).limit(10).get()
            .addOnSuccessListener { s ->
                ranking = s.documents.filter { (it.getLong("referralEarned") ?: 0L) > 0L }
                if (!isFinishing && !isDestroyed) render()
            }
            .addOnFailureListener {
                ranking = emptyList()
                toast("Gagal memuat peringkat")
                if (!isFinishing && !isDestroyed) render()
            }
    }

    // ───────────────────────── UI ─────────────────────────

    private fun render() {
        if (isFinishing || isDestroyed) return
        content.removeAllViews()

        // Judul
        content.addView(txt("Undang Teman\nDapat Koin 🎁", 28f, GOLD, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, d(6), 0, d(6))
        }, matchWrap())
        content.addView(txt("Dapat koin saat temanmu benar-benar aktif", 13f, CREAM, false).apply {
            gravity = Gravity.CENTER
        }, matchWrap())

        // Tiga hadiah
        val trio = LinearLayout(this)
        trio.orientation = LinearLayout.HORIZONTAL
        trio.setPadding(0, d(16), 0, d(12))
        trio.addView(rewardBox("🪙", fmt(cfg.inviterReward), "Teman aktif"), weightLp(0))
        trio.addView(rewardBox("🎉", fmt(cfg.inviteeReward), "Untuk temanmu"), weightLp(8))
        trio.addView(rewardBox("💳", "${cfg.topupPercent}%", "Top up teman"), weightLp(8))
        content.addView(trio, matchWrap())

        // Kode undangan
        val codeRow = LinearLayout(this)
        codeRow.orientation = LinearLayout.HORIZONTAL
        codeRow.gravity = Gravity.CENTER_VERTICAL
        codeRow.setPadding(d(16), d(10), d(10), d(10))
        codeRow.background = round(CARD, 30, GOLD)
        codeRow.addView(txt("Kode undanganku", 12f, MUTED, false))
        codeRow.addView(txt(if (myCode.isEmpty()) "..." else myCode, 20f, GOLD, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        codeRow.addView(pill("Salin", myCode.isNotEmpty()) { copyCode() })
        content.addView(codeRow, matchWrap())

        // Tombol undang
        content.addView(pill("Undang teman ↗", myCode.isNotEmpty(), big = true) { share() }, matchWrap().apply {
            topMargin = d(12)
        })

        // Isi kode (hanya teman baru)
        if (canRedeem()) content.addView(redeemCard(), matchWrap().apply { topMargin = d(16) })

        // Tab
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setPadding(0, d(20), 0, d(10))
        tabs.addView(tabBtn("Pendapatan saya", tab == 0) { tab = 0; render() }, weightLp(0))
        tabs.addView(tabBtn("Peringkat", tab == 1) {
            tab = 1
            if (ranking == null) { render(); loadRanking() } else render()
        }, weightLp(8))
        content.addView(tabs, matchWrap())

        if (tab == 0) renderMine() else renderRanking()
        renderRules()
    }

    private fun renderMine() {
        val todayStart = ReferralManager.startOfTodayWib()
        val totalEarn = events.sumOf { it.getLong("amount") ?: 0L }
        val todayEarn = events.filter { (it.getLong("at") ?: 0L) >= todayStart }.sumOf { it.getLong("amount") ?: 0L }
        val totalInv = referrals.size.toLong()
        val todayInv = referrals.count { (it.getLong("createdAt") ?: 0L) >= todayStart }.toLong()
        val activeInv = referrals.count { it.getString("status") == "active" }.toLong()

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(d(12), d(14), d(12), d(14))
        box.background = round(CARD, 14, "#8A5A20")
        val r1 = LinearLayout(this); r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(stat(fmt(totalEarn), "Total pendapatan"), weightLp(0))
        r1.addView(stat(fmt(todayEarn), "Pendapatan hari ini"), weightLp(0))
        val r2 = LinearLayout(this); r2.orientation = LinearLayout.HORIZONTAL
        r2.setPadding(0, d(12), 0, 0)
        r2.addView(stat("$totalInv ($activeInv aktif)", "Total undangan"), weightLp(0))
        r2.addView(stat(todayInv.toString(), "Undangan hari ini"), weightLp(0))
        box.addView(r1); box.addView(r2)
        content.addView(box, matchWrap())

        content.addView(txt("Teman yang kamu undang", 14f, GOLD, true).apply {
            setPadding(0, d(18), 0, d(6))
        })
        if (referrals.isEmpty()) {
            content.addView(txt("Belum ada. Bagikan kodemu ke teman 👆", 12f, MUTED, false))
            return
        }
        val now = System.currentTimeMillis()
        for (r in referrals) {
            val status = r.getString("status")
            val expired = status == "expired" || (status == "pending" && (r.getLong("expiresAt") ?: Long.MAX_VALUE) < now)
            val (label, color) = when {
                status == "active" -> "Aktif ✅" to "#4ADE80"
                expired -> "Kedaluwarsa" to "#9CA3AF"
                else -> "Menunggu aktif ⏳" to GOLD
            }
            val name = r.getString("inviteeName").orEmpty().ifBlank { "Pengguna baru" }
            val earned = r.getLong("inviterEarned") ?: 0L
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(d(14), d(10), d(14), d(10))
            row.background = round(CARD, 12, null)
            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(txt(name, 14f, CREAM, true))
            left.addView(txt(label, 11f, color, false))
            row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(txt(if (earned > 0) "+${fmt(earned)} 🪙" else "-", 13f, GOLD, true))
            content.addView(row, matchWrap().apply { bottomMargin = d(6) })
        }
    }

    private fun renderRanking() {
        content.addView(txt("Top 10 pendapatan undangan", 14f, GOLD, true).apply { setPadding(0, 0, 0, d(6)) })
        val list = ranking
        if (list == null) { content.addView(txt("Memuat...", 12f, MUTED, false)); return }
        if (list.isEmpty()) { content.addView(txt("Belum ada peringkat.", 12f, MUTED, false)); return }
        val myUid = auth.currentUser?.uid
        list.forEachIndexed { i, u ->
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(d(14), d(10), d(14), d(10))
            row.background = round(CARD, 12, if (u.id == myUid) GOLD else null)
            row.addView(txt(medal, 16f, CREAM, true).apply { minWidth = d(36) })
            row.addView(txt(u.getString("nickname").orEmpty().ifBlank { "Pengguna" }, 14f, CREAM, false),
                LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(txt("${fmt(u.getLong("referralEarned") ?: 0L)} 🪙", 13f, GOLD, true))
            content.addView(row, matchWrap().apply { bottomMargin = d(6) })
        }
    }

    private fun renderRules() {
        content.addView(txt("Aturan", 16f, GOLD, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, d(24), 0, d(10))
        }, matchWrap())

        val steps = LinearLayout(this)
        steps.orientation = LinearLayout.HORIZONTAL
        steps.addView(stepBox("📤", "Bagikan kode ke teman"), weightLp(0))
        steps.addView(stepBox("📲", "Teman daftar & isi kode"), weightLp(8))
        steps.addView(stepBox("✅", "Teman aktif, kamu dapat koin"), weightLp(8))
        content.addView(steps, matchWrap())

        val rules = listOf(
            "① Teman harus pengguna baru. Satu perangkat hanya bisa 1 akun yang memakai kode undangan.",
            "② Teman wajib mengisi kode maksimal ${cfg.redeemWindowHours} jam setelah mendaftar (saat melengkapi profil, atau lewat halaman ini). Lewat dari itu, kode tidak berlaku.",
            "③ Begitu kode berhasil diisi, temanmu langsung dapat ${fmt(cfg.inviteeReward)} koin.",
            "④ Kamu dapat ${fmt(cfg.inviterReward)} koin saat temanmu AKTIF: profil sudah lengkap dan check-in harian minimal ${cfg.activeCheckinDays} hari, dalam ${cfg.activeWithinDays} hari sejak mengisi kode.",
            "⑤ Jika top up pertama temanmu minimal ${fmt(cfg.topupFirstMin)} koin dalam ${cfg.topupFirstDays} hari setelah diundang, kamu dapat bonus ${fmt(cfg.topupFirstBonus)} koin.",
            "⑥ Selama ${cfg.topupPercentDays} hari setelah diundang, kamu dapat ${cfg.topupPercent}% koin dari setiap top up temanmu (termasuk top up pertama).",
            "Contoh: teman top up pertama ${fmt(cfg.topupFirstMin)} koin di hari ke-3 → kamu dapat ${fmt(cfg.topupFirstBonus)} + ${fmt(cfg.topupFirstMin * cfg.topupPercent / 100)} = ${fmt(cfg.topupFirstBonus + cfg.topupFirstMin * cfg.topupPercent / 100)} koin."
        )
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(d(14), d(14), d(14), d(14))
        box.background = round(CARD, 14, null)
        for (r in rules) box.addView(txt(r, 12f, CREAM, false).apply { setPadding(0, 0, 0, d(8)) })
        content.addView(box, matchWrap().apply { topMargin = d(12) })
    }

    // ───────────────────────── AKSI ─────────────────────────

    private fun canRedeem(): Boolean {
        val u = me ?: return false
        if (!u.getString("invitedBy").isNullOrBlank()) return false
        val created = auth.currentUser?.metadata?.creationTimestamp ?: return false
        return System.currentTimeMillis() - created <= cfg.redeemWindowHours * 3_600_000L
    }

    private fun redeemCard(): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(d(14), d(14), d(14), d(14))
        c.background = round(CARD, 14, GOLD)
        c.addView(txt("Punya kode undangan dari teman?", 14f, CREAM, true))
        c.addView(txt("Isi sekarang dan dapat ${fmt(cfg.inviteeReward)} koin. Batas ${cfg.redeemWindowHours} jam setelah daftar.", 11f, MUTED, false).apply {
            setPadding(0, d(2), 0, d(8))
        })
        val et = EditText(this)
        et.hint = "Kode undangan"
        et.setHintTextColor(Color.parseColor(MUTED))
        et.setTextColor(Color.WHITE)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        et.filters = arrayOf(InputFilter.LengthFilter(ReferralManager.CODE_LEN), InputFilter.AllCaps())
        et.background = round("#2A0808", 10, null)
        et.setPadding(d(12), d(10), d(12), d(10))
        c.addView(et, matchWrap())
        c.addView(pill("Klaim koin", true) {
            ReferralManager.redeem(this, et.text.toString()) { ok, msg ->
                toast(msg)
                if (ok) load()
            }
        }, matchWrap().apply { topMargin = d(8) })
        return c
    }

    private fun copyCode() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Kode undangan", myCode))
        toast("Kode disalin ✅")
    }

    private fun share() {
        val text = "Ayo gabung di HAYU! 🎉\n" +
            "Pakai kode undanganku $myCode saat daftar (maks. ${cfg.redeemWindowHours} jam setelah daftar) " +
            "dan dapat ${fmt(cfg.inviteeReward)} koin gratis."
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(i, "Undang teman via"))
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ───────────────────────── KOMPONEN ─────────────────────────

    private fun txt(s: String, size: Float, color: String, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun round(fill: String, radius: Int, stroke: String?): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            cornerRadius = d(radius).toFloat()
            if (stroke != null) setStroke(d(1), Color.parseColor(stroke))
        }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)

    private fun weightLp(leftMargin: Int) = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = d(leftMargin) }

    private fun pill(label: String, enabled: Boolean, big: Boolean = false, onClick: () -> Unit): TextView {
        val b = txt(label, if (big) 17f else 13f, "#3A1500", true)
        b.gravity = Gravity.CENTER
        b.setPadding(d(18), d(if (big) 14 else 8), d(18), d(if (big) 14 else 8))
        b.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#FFD34E"), Color.parseColor("#FF9A3D"))
        ).apply { cornerRadius = d(30).toFloat() }
        b.alpha = if (enabled) 1f else 0.5f
        if (enabled) b.setOnClickListener { onClick() }
        return b
    }

    private fun tabBtn(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val b = txt(label, 13f, if (selected) "#3A1500" else CREAM, true)
        b.gravity = Gravity.CENTER
        b.setPadding(d(8), d(10), d(8), d(10))
        b.background = if (selected) round(GOLD, 24, null) else round(CARD, 24, "#8A5A20")
        b.setOnClickListener { onClick() }
        return b
    }

    private fun rewardBox(icon: String, big: String, small: String): View {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER
        b.setPadding(d(6), d(12), d(6), d(12))
        b.background = round(CARD, 12, GOLD)
        b.addView(txt(icon, 24f, CREAM, false))
        b.addView(txt(big, 17f, GOLD, true).apply { gravity = Gravity.CENTER })
        b.addView(txt(small, 11f, MUTED, false).apply { gravity = Gravity.CENTER })
        return b
    }

    private fun stat(value: String, label: String): View {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER
        b.addView(txt(value, 18f, GOLD, true).apply { gravity = Gravity.CENTER })
        b.addView(txt(label, 11f, MUTED, false).apply { gravity = Gravity.CENTER })
        return b
    }

    private fun stepBox(icon: String, label: String): View {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER_HORIZONTAL
        b.setPadding(d(4), 0, d(4), 0)
        b.addView(txt(icon, 28f, CREAM, false).apply {
            gravity = Gravity.CENTER
            setPadding(d(14), d(12), d(14), d(12))
            background = round("#D8B7A3", 12, null)
        })
        b.addView(txt(label, 11f, CREAM, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, d(6), 0, 0)
        })
        return b
    }
}
