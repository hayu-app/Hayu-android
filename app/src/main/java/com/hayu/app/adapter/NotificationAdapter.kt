package com.hayu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hayu.app.R
import com.hayu.app.model.Notification

class NotificationAdapter(
    private val onClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotifViewHolder>() {

    private val items = mutableListOf<Notification>()

    fun submitList(list: List<Notification>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class NotifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIcon: TextView = itemView.findViewById(R.id.tv_notif_icon)
        val tvName: TextView = itemView.findViewById(R.id.tv_notif_name)
        val tvMessage: TextView = itemView.findViewById(R.id.tv_notif_message)
        val tvTime: TextView = itemView.findViewById(R.id.tv_notif_time)
        val dotUnread: View = itemView.findViewById(R.id.dot_unread)

        fun bind(notif: Notification) {
            // Ikon berdasarkan tipe
            tvIcon.text = when (notif.type) {
                "follow" -> "❤️"
                "friend_request" -> "👥"
                "friend_accept" -> "✅"
                "message" -> "💬"
                "room_invite" -> "🎙️"
                "gift" -> "🎁"
                else -> "🔔"
            }

            tvName.text = notif.fromName.ifEmpty { "Pengguna HAYU" }
            tvMessage.text = notif.message
            tvTime.text = getTimeAgo(notif.timestamp)

            // Dot merah kalau belum dibaca
            dotUnread.visibility = if (notif.read) View.GONE else View.VISIBLE

            itemView.setOnClickListener { onClick(notif) }
        }
    }

    // Convert timestamp → "Baru saja", "5m", "2j", "3h"
    private fun getTimeAgo(timestamp: Long): String {
        if (timestamp <= 0) return ""

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Baru saja"
            minutes < 60 -> "${minutes}m lalu"
            hours < 24 -> "${hours}j lalu"
            days < 7 -> "${days}h lalu"
            else -> "${days / 7}mgg lalu"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotifViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotifViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
