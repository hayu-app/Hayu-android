package com.hayu.app.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hayu.app.FlowRow
import com.hayu.app.ProfileTheme
import com.hayu.app.R
import com.hayu.app.Role
import com.hayu.app.model.RoomMessage
import com.hayu.app.util.UserInfoCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dua tipe baris:
 *  - pesan user  : avatar kiri atas, di kanannya badge role + nama, di bawahnya isi chat
 *  - pesan sistem: gelembung ringkas (sistem, gift, dadu, mobil masuk, red packet)
 */
class RoomMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val T_SYSTEM = 0
        const val T_USER = 1
        val SPECIAL_NAMES = setOf("🔔 Sistem", "🎁 GIFT", "🎲 DADU", "🚗 MASUK", "🧧 RED PACKET")
    }

    private val messages = mutableListOf<RoomMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<RoomMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    private fun isSystem(m: RoomMessage): Boolean =
        m.senderUid.isEmpty() || m.senderUid == "SYSTEM" || m.senderName in SPECIAL_NAMES

    override fun getItemViewType(position: Int): Int =
        if (isSystem(messages[position])) T_SYSTEM else T_USER

    private fun timeText(msg: RoomMessage): String =
        if (msg.timestamp > 0) timeFormat.format(Date(msg.timestamp)) else ""

    // ---------- pesan sistem (tampilan lama) ----------
    inner class SystemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tv_msg_sender)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_msg_time)
        private val tvText: TextView = itemView.findViewById(R.id.tv_msg_text)

        fun bind(msg: RoomMessage) {
            tvSender.text = msg.senderName
            tvText.text = msg.text
            tvTime.text = timeText(msg)
        }
    }

    // ---------- pesan user ----------
    inner class UserHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.img_chat_avatar)
        private val header: FlowRow = itemView.findViewById(R.id.chat_header)
        private val tvText: TextView = itemView.findViewById(R.id.tv_chat_text)

        fun bind(msg: RoomMessage) {
            val ctx = itemView.context
            val d = ctx.resources.displayMetrics.density
            val info = UserInfoCache.peek(msg.senderUid)

            // avatar
            val photo = info?.photoUrl ?: ""
            if (photo.isNotEmpty()) {
                avatar.load(photo) { crossfade(true) }
            } else {
                avatar.setImageResource(R.drawable.ic_profile)
            }

            // header: [badge role ...] nama  jam
            header.hGap = (5 * d).toInt()
            header.vGap = (3 * d).toInt()
            header.removeAllViews()
            val roles = (info?.roles ?: emptySet()).sortedByDescending { Role.rankOf(it) }
            for (r in roles) {
                val label = Role.badgeShort(r) ?: continue
                header.addView(ProfileTheme.chip(ctx, label, ProfileTheme.roleColor(r), compact = true))
            }
            val name = TextView(ctx)
            name.text = msg.senderName
            name.textSize = 12.5f
            name.typeface = Typeface.DEFAULT_BOLD
            name.setTextColor(Color.parseColor("#67E8F9"))
            header.addView(name)
            val time = TextView(ctx)
            time.text = timeText(msg)
            time.textSize = 10f
            time.setTextColor(Color.parseColor("#8892AA"))
            header.addView(time)

            tvText.text = msg.text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == T_USER) {
            UserHolder(inflater.inflate(R.layout.item_room_chat, parent, false))
        } else {
            SystemHolder(inflater.inflate(R.layout.item_room_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is UserHolder) {
            holder.bind(msg)
            // ambil foto & role pengirim sekali, lalu segarkan baris-baris miliknya
            UserInfoCache.request(msg.senderUid) {
                for (i in messages.indices) {
                    if (messages[i].senderUid == msg.senderUid) notifyItemChanged(i)
                }
            }
        } else if (holder is SystemHolder) {
            holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messages.size
}
