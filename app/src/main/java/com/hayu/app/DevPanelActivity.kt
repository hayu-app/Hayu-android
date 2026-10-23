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

class DevPanelActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var myRole = Role.USER

    data class PanelItem(val emoji: String, val label: String, val key: String)

    private val allPanels = listOf(
        PanelItem("📊", "Master Dashboard",    "dashboard"),
        PanelItem("👑", "Superadmin",          "superadmin"),
        PanelItem("🛡️", "Admin",              "admin"),
        PanelItem("💼", "BD Management",       "bd"),
        PanelItem("🏢", "Agency Management",   "agency"),
        PanelItem("🎙️", "Host Management",    "host"),
        PanelItem("🪙", "Coinseller",          "coinseller"),
        PanelItem("💰", "Finance",             "finance"),
        PanelItem("🔍", "Moderator",           "moderator"),
        PanelItem("🎧", "Customer Service",    "cs"),
        PanelItem("👥", "User Management",     "user"),
        PanelItem("🏠", "Room Management",     "room"),
        PanelItem("🎁", "Coin & Gift",         "coin"),
        PanelItem("⭐", "VIP & Level",          "vip"),
        PanelItem("🎉", "Event & Mission",     "event"),
        PanelItem("🔒", "Security",            "security"),
        PanelItem("🔔", "Notifikasi",          "notif"),
        PanelItem("📈", "Reports",             "report"),
        PanelItem("⚙️", "System & Dev Tools", "system"),
        PanelItem("🎞️", "SVGA Manager",       "svga"),
        PanelItem("📅", "Check-in Harian",    "checkin"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: run { finish(); return }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val owned = Role.rolesOf(doc)
                val want = intent.getStringExtra("role")
                myRole = if (want != null && want in owned) want else Role.top(owned)
                if (!Role.canAccess(myRole, "dashboard")) {
                    Toast.makeText(this, "Akses ditolak", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                buildUI()
            }
            .addOnFailureListener {
                myRole = Role.USER
                buildUI()
            }
    }

    private fun buildUI() {
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
        btnBack.text = "‹"
        btnBack.textSize = 28f
        btnBack.setTextColor(Color.WHITE)
        btnBack.setOnClickListener { finish() }
        header.addView(btnBack)

        val tvTitle = TextView(this)
        tvTitle.text = "  Panel Manajemen"
        tvTitle.textSize = 18f
        tvTitle.setTextColor(Color.WHITE)
        tvTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val titleLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(tvTitle, titleLp)

        val tvRole = TextView(this)
        tvRole.text = myRole.uppercase()
        tvRole.textSize = 11f
        tvRole.setTextColor(Color.parseColor("#A78BFA"))
        tvRole.setBackgroundColor(Color.parseColor("#1C1C35"))
        tvRole.setPadding(d(8), d(4), d(8), d(4))
        header.addView(tvRole)
        root.addView(header)

        // panel grid
        val sv = ScrollView(this)
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(d(12), d(12), d(12), d(80))

        val visiblePanels = allPanels.filter {
            Role.canAccess(myRole, it.key) || Role.canAccess(myRole, "all")
        }

        var i = 0
        while (i < visiblePanels.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rowLp.bottomMargin = d(8)

            for (j in i until i + 3) {
                val lp = LinearLayout.LayoutParams(0, d(90), 1f)
                lp.setMargins(d(4), 0, d(4), 0)

                if (j >= visiblePanels.size) {
                    row.addView(TextView(this), lp)
                    continue
                }

                val p = visiblePanels[j]
                val tile = LinearLayout(this)
                tile.orientation = LinearLayout.VERTICAL
                tile.gravity = Gravity.CENTER
                tile.setBackgroundColor(Color.parseColor("#12122A"))
                tile.setPadding(d(8), d(8), d(8), d(8))

                val icon = TextView(this)
                icon.text = p.emoji
                icon.textSize = 26f
                icon.gravity = Gravity.CENTER

                val name = TextView(this)
                name.text = p.label
                name.textSize = 10f
                name.setTextColor(Color.parseColor("#C4CAFF"))
                name.gravity = Gravity.CENTER
                name.maxLines = 2
                name.setPadding(0, d(4), 0, 0)

                tile.addView(icon)
                tile.addView(name)
                tile.setOnClickListener { openPanel(p.key, p.label) }

                // rounded corner manual
                val gd = android.graphics.drawable.GradientDrawable()
                gd.setColor(Color.parseColor("#12122A"))
                gd.cornerRadius = d(12).toFloat()
                gd.setStroke(d(1), Color.parseColor("#2E2E50"))
                tile.background = gd

                row.addView(tile, lp)
            }
            container.addView(row, rowLp)
            i += 3
        }

        sv.addView(container)
        val svLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(sv, svLp)
        setContentView(root)
    }

    private fun openPanel(key: String, label: String) {
        if (key == "checkin") {
            startActivity(Intent(this, CheckinConfigActivity::class.java))
            return
        }
        if (key == "svga") {
            startActivity(Intent(this, SvgaManagerActivity::class.java))
            return
        }
        val intent = Intent(this, PanelDetailActivity::class.java)
        intent.putExtra("panel_key", key)
        intent.putExtra("panel_label", label)
        intent.putExtra("my_role", myRole)
        startActivity(intent)
    }
}
