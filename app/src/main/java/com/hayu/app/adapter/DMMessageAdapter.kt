package com.hayu.app.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hayu.app.R
import com.hayu.app.model.DMMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DMMessageAdapter(
    private val myUid: String
) : RecyclerView.Adapter<DMMessageAdapter.VH>() {

    private val items = mutableListOf<DMMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<DMMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.bubble_container)
        val tvText: TextView = itemView.findViewById(R.id.tv_dm_text)
        val tvTime: TextView = itemView.findViewById(R.id.tv_dm_time)

        fun bind(msg: DMMessage) {
            tvText.text = msg.text

            if (msg.timestamp > 0) {
                tvTime.text = timeFormat.format(Date(msg.timestamp))
            } else {
                tvTime.text = ""
            }

            // Atur alignment & warna bubble berdasarkan pengirim
            val parent = itemView as LinearLayout
            if (msg.senderUid == myUid) {
                // Pesan saya → kanan, ungu
                parent.gravity = Gravity.END
                container.background = itemView.context.getDrawable(R.drawable.bg_button_primary)
                tvText.setTextColor(0xFFFFFFFF.toInt())
                tvTime.setTextColor(0xCCFFFFFF.toInt())
            } else {
                // Pesan orang lain → kiri, abu
                parent.gravity = Gravity.START
                container.background = itemView.context.getDrawable(R.drawable.bg_quick_item)
                tvText.setTextColor(itemView.context.getColor(R.color.hayu_text))
                tvTime.setTextColor(itemView.context.getColor(R.color.hayu_text_secondary))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dm_message, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
