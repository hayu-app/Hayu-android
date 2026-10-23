package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Banner geser di beranda. Isi bisa diganti lewat defaultBanners(). */
class HomeBannerAdapter(private val items: List<Banner>) :
    RecyclerView.Adapter<HomeBannerAdapter.VH>() {

    data class Banner(
        val title: String,
        val subtitle: String,
        val startColor: String,
        val endColor: String
    )

    companion object {
        fun defaultBanners() = listOf(
            Banner("HAYU STAR ⭐", "BFF Weekly Star", "#3B1FA8", "#12083F"),
            Banner("CP & BFF 💕", "Cari pasangan & sahabatmu di HAYU", "#C2307E", "#4A1560"),
            Banner("EVENT 🎉", "Ikuti event dan menangkan hadiah", "#B8791A", "#4A2A08")
        )
    }

    class VH(val box: LinearLayout, val title: TextView, val sub: TextView) : RecyclerView.ViewHolder(box)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val title = TextView(ctx)
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.letterSpacing = 0.08f
        box.addView(title)

        val sub = TextView(ctx)
        sub.textSize = 13f
        sub.setTextColor(Color.parseColor("#E6E0FF"))
        sub.setPadding(0, (4 * d).toInt(), 0, 0)
        box.addView(sub)

        return VH(box, title, sub)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        val d = holder.box.resources.displayMetrics.density
        holder.title.text = b.title
        holder.sub.text = b.subtitle
        holder.box.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(b.startColor), Color.parseColor(b.endColor))
        ).apply { cornerRadius = 18 * d }
    }

    override fun getItemCount(): Int = items.size
}
