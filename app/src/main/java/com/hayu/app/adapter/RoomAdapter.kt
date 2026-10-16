package com.hayu.app.adapter

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
        val tvRoomUsers: TextView = itemView.findViewById(R.id.tv_room_users)
        val tvRoomPrivacy: TextView = itemView.findViewById(R.id.tv_room_privacy)

        fun bind(room: Room) {
            tvRoomName.text = if (room.name.isEmpty()) "Room Tanpa Nama" else room.name
            tvRoomTopic.text = if (room.topic.isEmpty()) "Belum ada topik" else room.topic
            tvRoomUsers.text = "👤 ${room.currentUsers}"

            tvRoomPrivacy.text = if (room.privacy == "privat") "🔒 Privat" else "🌍 Publik"

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
