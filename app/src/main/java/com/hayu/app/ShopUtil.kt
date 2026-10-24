package com.hayu.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

/** Logika & komponen UI bersama untuk Toko dan Hiasan Saya. */
object ShopUtil {
    const val DAY_MS = 86_400_000L

    class Tab(val key: String, val icon: String, val label: String)
    class Title(val id: String, val emoji: String, val name: String, val price: Int) {
        val label: String get() = "$emoji $name"
    }

    val TITLES = listOf(
        Title("t_pemula", "🌱", "Pemula", 20),
        Title("t_bintang", "⭐", "Bintang", 50),
        Title("t_api", "🔥", "Api", 120),
        Title("t_raja", "👑", "Raja", 200),
        Title("t_berlian", "💎", "Berlian", 500)
    )

    /** Kategori yang sudah punya data/fungsi: key = jenis aset SVGA, atau "title" untuk gelar. */
    fun svgaTypeOf(tabKey: String): String? = when (tabKey) {
        "car" -> SvgaCatalog.CAR
        "frame" -> SvgaCatalog.FRAME
        else -> null
    }

    fun equippedField(tabKey: String): String? = when (tabKey) {
        "car" -> "equippedCar"
        "frame" -> "equippedFrame"
        else -> null
    }

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    // ───────── data kepemilikan ─────────

    fun ownedSet(u: DocumentSnapshot): Set<String> =
        (u.get("ownedItems") as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()

    fun expiryMap(u: DocumentSnapshot): Map<String, Long> {
        val m = u.get("ownedExpiry") as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, Long>()
        for ((k, v) in m) if (k is String && v is Number) out[k] = v.toLong()
        return out
    }

    fun isActive(itemId: String, owned: Set<String>, expiry: Map<String, Long>): Boolean {
        if (itemId !in owned) return false
        val e = expiry[itemId] ?: return true
        return e > System.currentTimeMillis()
    }

    fun durationText(days: Int) = if (days > 0) "$days hari" else "Permanen"

    fun leftText(expiresAt: Long?): String {
        if (expiresAt == null) return "Permanen"
        val d = Math.ceil((expiresAt - System.currentTimeMillis()).toDouble() / DAY_MS).toLong()
        return "Sisa ${d.coerceAtLeast(1)} hari"
    }

    fun compact(n: Long): String = when {
        n >= 1_000_000L -> String.format(Locale.US, "%.1f", n / 1_000_000.0).removeSuffix(".0") + "M"
        n >= 1_000L -> String.format(Locale.US, "%.1f", n / 1_000.0).removeSuffix(".0") + "K"
        else -> n.toString()
    }

    /** Beli / perpanjang. days > 0 = masa berlaku (hari), 0 = permanen. */
    fun buy(
        db: FirebaseFirestore, uid: String, itemId: String, price: Int, days: Int,
        onOk: () -> Unit, onFail: (String) -> Unit
    ) {
        val ref = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val owned = (snap.get("ownedItems") as? List<*>)?.map { it.toString() } ?: emptyList()
            val coins = snap.getLong("coins") ?: 0L
            val now = System.currentTimeMillis()
            val exp = (snap.get("ownedExpiry") as? Map<*, *>)?.get(itemId) as? Number
            val active = itemId in owned && (exp == null || exp.toLong() > now)
            if (days <= 0 && active && exp == null) throw IllegalStateException("Sudah dimiliki")
            if (coins < price) throw IllegalStateException("Koin tidak cukup")

            val upd = HashMap<String, Any>()
            upd["coins"] = coins - price
            if (itemId !in owned) upd["ownedItems"] = owned + itemId
            if (days > 0) {
                val base = if (exp != null && exp.toLong() > now) exp.toLong() else now
                upd["ownedExpiry.$itemId"] = base + days * DAY_MS
            } else if (exp != null) {
                upd["ownedExpiry.$itemId"] = FieldValue.delete()
            }
            tx.update(ref, upd)
            Unit
        }.addOnSuccessListener { onOk() }
            .addOnFailureListener { onFail(it.message ?: "gagal") }
    }

    /** Lepas otomatis item (bingkai/kendaraan) yang masa berlakunya habis. Mengembalikan field yang dilepas. */
    fun cleanExpired(
        db: FirebaseFirestore, uid: String, user: DocumentSnapshot, assets: List<SvgaCatalog.Asset>
    ): Set<String> {
        val owned = ownedSet(user)
        val expiry = expiryMap(user)
        val upd = HashMap<String, Any>()
        val cleared = HashSet<String>()
        for (field in listOf("equippedFrame", "equippedCar")) {
            val url = user.getString("${field}Url") ?: ""
            if (url.isEmpty()) continue
            val a = assets.firstOrNull { it.url == url } ?: continue
            if (a.price == 0) continue
            if (!isActive("svga_${a.id}", owned, expiry)) {
                upd["${field}Url"] = ""
                upd["${field}Name"] = ""
                cleared.add(field)
            }
        }
        if (upd.isNotEmpty()) db.collection("users").document(uid).update(upd)
        return cleared
    }

    // ───────── komponen UI ─────────

    fun header(
        ctx: Context, title: String, rightLabel: String, onBack: () -> Unit, onRight: () -> Unit
    ): FrameLayout {
        val h = FrameLayout(ctx)
        val back = TextView(ctx)
        back.text = "‹"
        back.textSize = 34f
        back.setTextColor(Color.WHITE)
        back.gravity = Gravity.CENTER
        back.setOnClickListener { onBack() }
        h.addView(back, FrameLayout.LayoutParams(dp(ctx, 52), dp(ctx, 52), Gravity.START or Gravity.CENTER_VERTICAL))

        val t = TextView(ctx)
        t.text = title
        t.textSize = 20f
        t.setTextColor(Color.WHITE)
        t.setTypeface(t.typeface, Typeface.BOLD)
        h.addView(t, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val r = TextView(ctx)
        r.text = rightLabel
        r.textSize = 14f
        r.setTextColor(Color.WHITE)
        r.setTypeface(r.typeface, Typeface.BOLD)
        r.setBackgroundResource(R.drawable.bg_shop_pill)
        r.setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7))
        r.setOnClickListener { onRight() }
        h.addView(r, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(ctx, 16)
        })
        return h
    }

    fun tabBar(ctx: Context, tabs: List<Tab>, selected: String, onSelect: (String) -> Unit): HorizontalScrollView {
        val sv = HorizontalScrollView(ctx)
        sv.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 8), dp(ctx, 8))
        for (tab in tabs) {
            val on = tab.key == selected
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.VERTICAL
            card.gravity = Gravity.CENTER_HORIZONTAL
            card.setPadding(dp(ctx, 4), dp(ctx, 10), dp(ctx, 4), dp(ctx, 8))
            card.setBackgroundResource(if (on) R.drawable.bg_shop_tab_selected else R.drawable.bg_shop_tab)

            val icon = TextView(ctx)
            icon.text = tab.icon
            icon.textSize = 24f
            icon.gravity = Gravity.CENTER
            icon.setBackgroundResource(R.drawable.bg_shop_icon)
            card.addView(icon, LinearLayout.LayoutParams(dp(ctx, 54), dp(ctx, 54)))

            val label = TextView(ctx)
            label.text = tab.label
            label.textSize = 12f
            label.gravity = Gravity.CENTER
            label.maxLines = 2
            label.setTextColor(if (on) Color.parseColor("#FFE27A") else Color.parseColor("#8A8A8E"))
            label.setTypeface(label.typeface, Typeface.BOLD)
            label.setPadding(0, dp(ctx, 6), 0, 0)
            card.addView(label, LinearLayout.LayoutParams(-1, -2))

            card.setOnClickListener { onSelect(tab.key) }
            row.addView(card, LinearLayout.LayoutParams(dp(ctx, 78), dp(ctx, 104)).apply { marginEnd = dp(ctx, 8) })
        }
        sv.addView(row)
        return sv
    }

    /** Susun kartu jadi grid 2 kolom. */
    fun grid(ctx: Context, cards: List<View>): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        var i = 0
        while (i < cards.size) {
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            for (j in i until i + 2) {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
                if (j < cards.size) line.addView(cards[j], lp) else line.addView(View(ctx), lp)
            }
            box.addView(line)
            i += 2
        }
        return box
    }

    fun emptyState(ctx: Context, text: String, buttonLabel: String?, onButton: () -> Unit): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(ctx, 32), dp(ctx, 90), dp(ctx, 32), dp(ctx, 40))

        val mascot = TextView(ctx)
        mascot.text = "🐣"
        mascot.textSize = 84f
        mascot.gravity = Gravity.CENTER
        box.addView(mascot)

        val msg = TextView(ctx)
        msg.text = text
        msg.textSize = 15f
        msg.gravity = Gravity.CENTER
        msg.setTextColor(Color.parseColor("#9A9AA0"))
        msg.setPadding(0, dp(ctx, 16), 0, dp(ctx, 24))
        box.addView(msg)

        if (buttonLabel != null) {
            val b = TextView(ctx)
            b.text = buttonLabel
            b.textSize = 17f
            b.gravity = Gravity.CENTER
            b.setTextColor(Color.WHITE)
            b.setTypeface(b.typeface, Typeface.BOLD)
            b.setBackgroundResource(R.drawable.bg_button_orange)
            b.setOnClickListener { onButton() }
            box.addView(b, LinearLayout.LayoutParams(dp(ctx, 220), dp(ctx, 50)))
        }
        return box
    }
}
