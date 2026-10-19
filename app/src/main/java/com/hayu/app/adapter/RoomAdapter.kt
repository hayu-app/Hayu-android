package com.hayu.app.adapter

import coil.load
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hayu.app.R
import com.hayu.app.model.Room

class RoomAdapter(
    private val onClick: (Room) -> Unit
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    private val rooms = mutableListOf<Room>()

    fun submitList(list: List<Room>) {
        rooms.clear()
        rooms.addAll(list)
        notifyDataSetChanged()
    }

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        val tvRoomTopic: TextView = itemView.findViewById(R.id.tv_room_topic)
        val tvRoomMic: TextView = itemView.findViewById(R.id.tv_room_mic)
        val tvRoomUsers: TextView = itemView.findViewById(R.id.tv_room_users)
        val tvRoomPrivacy: TextView = itemView.findViewById(R.id.tv_room_privacy)
        val ivCover: ImageView = itemView.findViewById(R.id.iv_room_cover)
        val tvCoverIcon: TextView = itemView.findViewById(R.id.tv_room_cover_icon)

        fun bind(room: Room) {
            tvRoomName.text = if (room.name.isEmpty()) "Room Tanpa Nama" else room.name
            tvRoomTopic.text = if (room.topic.isEmpty()) "Belum ada topik" else room.topic
            tvRoomMic.text = "🪑 ${room.maxMic}"
            tvRoomUsers.text = "👤 ${room.currentUsers}"
            tvRoomPrivacy.text = if (room.privacy == "privat") "🔒 Privat" else "🌍 Publik"

            if (room.coverUrl.isNotBlank()) {
                ivCover.visibility = View.VISIBLE
                tvCoverIcon.visibility = View.GONE
                ivCover.load(room.coverUrl) { crossfade(true) }
            } else {
                ivCover.visibility = View.GONE
                tvCoverIcon.visibility = View.VISIBLE
            }

            itemView.setOnClickListener { onClick(room) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount(): Int = rooms.size
}
