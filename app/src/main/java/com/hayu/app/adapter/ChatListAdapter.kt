package com.hayu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hayu.app.R
import com.hayu.app.model.DMChat

class ChatListAdapter(
    private val onClick: (DMChat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private val items = mutableListOf<DMChat>()

    fun submitList(list: List<DMChat>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.img_chat_avatar)
        val tvName: TextView = itemView.findViewById(R.id.tv_chat_name)
        val tvLast: TextView = itemView.findViewById(R.id.tv_chat_last)
        val tvTime: TextView = itemView.findViewById(R.id.tv_chat_time)

        fun bind(chat: DMChat) {
            tvName.text = chat.peerName.ifEmpty { "Pengguna HAYU" }
            tvLast.text = chat.lastMessage.ifEmpty { "Belum ada pesan" }

            if (chat.peerPhoto.isNotEmpty()) {
                imgAvatar.load(chat.peerPhoto) { crossfade(true) }
            } else {
                imgAvatar.setImageResource(R.drawable.ic_profile)
            }

            tvTime.text = getTimeAgo(chat.lastTimestamp)

            itemView.setOnClickListener { onClick(chat) }
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Baru"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}j"
            days < 7 -> "${days}h"
            else -> "${days / 7}mgg"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_list, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
