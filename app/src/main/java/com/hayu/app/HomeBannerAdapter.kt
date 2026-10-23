package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** Banner geser di beranda. Isi diatur developer di Firestore app_config/home_banners (field "items"). */
class HomeBannerAdapter(private val items: List<Banner>) :
    RecyclerView.Adapter<HomeBannerAdapter.VH>() {

    data class Banner(
        val title: String,
        val subtitle: String,
        val startColor: String,
        val endColor: String,
        val imageUrl: String = ""
    )

    companion object {
        fun defaultBanners() = listOf(
            Banner("HAYU STAR ⭐", "BFF Weekly Star", "#3B1FA8", "#12083F"),
            Banner("CP & BFF 💕", "Cari pasangan & sahabatmu di HAYU", "#C2307E", "#4A1560"),
            Banner("EVENT 🎉", "Ikuti event dan menangkan hadiah", "#B8791A", "#4A2A08")
        )

        /** Preset warna untuk panel developer: nama -> (awal, akhir) */
        val PRESETS: List<Triple<String, String, String>> = listOf(
            Triple("Ungu", "#3B1FA8", "#12083F"),
            Triple("Pink", "#C2307E", "#4A1560"),
            Triple("Emas", "#B8791A", "#4A2A08"),
            Triple("Biru", "#1F5FD0", "#0A1A4A"),
            Triple("Hijau", "#14935E", "#06301F")
        )

        private fun colorOk(c: String?, fallback: String): String =
            try { Color.parseColor(c); c!! } catch (e: Exception) { fallback }

        fun toMap(b: Banner): Map<String, Any> = mapOf(
            "title" to b.title,
            "subtitle" to b.subtitle,
            "startColor" to b.startColor,
            "endColor" to b.endColor,
            "imageUrl" to b.imageUrl
        )

        /** null kalau config kosong/tidak valid -> pakai default */
        fun fromConfig(raw: Any?): List<Banner>? {
            val list = (raw as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: return null
            val out = list.map {
                Banner(
                    title = it["title"] as? String ?: "",
                    subtitle = it["subtitle"] as? String ?: "",
                    startColor = colorOk(it["startColor"] as? String, "#3B1FA8"),
                    endColor = colorOk(it["endColor"] as? String, "#12083F"),
                    imageUrl = it["imageUrl"] as? String ?: ""
                )
            }
            return if (out.isEmpty()) null else out
        }
    }

    class VH(
        val box: FrameLayout,
        val img: ImageView,
        val textBox: LinearLayout,
        val title: TextView,
        val sub: TextView
    ) : RecyclerView.ViewHolder(box)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density

        val box = FrameLayout(ctx)
        box.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        box.clipToOutline = true

        val img = ImageView(ctx)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        box.addView(img, FrameLayout.LayoutParams(-1, -1))

        val textBox = LinearLayout(ctx)
        textBox.orientation = LinearLayout.VERTICAL
        textBox.gravity = Gravity.CENTER
        box.addView(textBox, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(ctx)
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.letterSpacing = 0.08f
        title.setShadowLayer(4f, 0f, 2f, Color.parseColor("#99000000"))
        textBox.addView(title)

        val sub = TextView(ctx)
        sub.textSize = 13f
        sub.setTextColor(Color.parseColor("#E6E0FF"))
        sub.setPadding(0, (4 * d).toInt(), 0, 0)
        sub.setShadowLayer(4f, 0f, 2f, Color.parseColor("#99000000"))
        textBox.addView(sub)

        return VH(box, img, textBox, title, sub)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        val d = holder.box.resources.displayMetrics.density
        holder.title.text = b.title
        holder.sub.text = b.subtitle
        holder.title.visibility = if (b.title.isBlank()) View.GONE else View.VISIBLE
        holder.sub.visibility = if (b.subtitle.isBlank()) View.GONE else View.VISIBLE
        holder.box.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(b.startColor), Color.parseColor(b.endColor))
        ).apply { cornerRadius = 18 * d }

        if (b.imageUrl.isNotBlank()) {
            holder.img.visibility = View.VISIBLE
            holder.img.load(b.imageUrl) { crossfade(true) }
        } else {
            holder.img.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}
