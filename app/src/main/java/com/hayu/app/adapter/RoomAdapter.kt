package com.hayu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hayu.app.R
import com.hayu.app.model.Room

class RoomAdapter(
    private val onClick: (Room) -> Unit
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    private val rooms = mutableListOf<Room>()
    private var favoriteIds: Set<String> = emptySet()
    private var onFavoriteClick: ((Room) -> Unit)? = null

    fun submitList(list: List<Room>) {
        rooms.clear()
        rooms.addAll(list)
        notifyDataSetChanged()
    }

    fun setFavorites(
        ids: Set<String>,
        onClick: (Room) -> Unit
    ) {
        favoriteIds = ids.toSet()
        onFavoriteClick = onClick
        notifyDataSetChanged()
    }

    inner class RoomViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val tvRoomName: TextView =
            itemView.findViewById(R.id.tv_room_name)
        private val tvRoomTopic: TextView =
            itemView.findViewById(R.id.tv_room_topic)
        private val tvRoomOwner: TextView =
            itemView.findViewById(R.id.tv_room_owner)
        private val tvRoomMic: TextView =
            itemView.findViewById(R.id.tv_room_mic)
        private val tvRoomUsers: TextView =
            itemView.findViewById(R.id.tv_room_users)
        private val tvRoomPrivacy: TextView =
            itemView.findViewById(R.id.tv_room_privacy)
        private val ivCover: ImageView =
            itemView.findViewById(R.id.iv_room_cover)
        private val tvCoverIcon: TextView =
            itemView.findViewById(R.id.tv_room_cover_icon)
        private val tvFavorite: TextView =
            itemView.findViewById(R.id.tv_room_favorite)

        fun bind(room: Room) {
            tvRoomName.text =
                if (room.name.isEmpty()) "Room Tanpa Nama" else room.name
            tvRoomTopic.text =
                if (room.topic.isEmpty()) "Belum ada topik" else room.topic
            tvRoomOwner.text =
                "👑 " + room.ownerName.ifBlank { "Pemilik room" }
            tvRoomMic.text = "🪑 ${room.maxMic}"
            tvRoomUsers.text = "👤 ${room.currentUsers}"

            val isPrivate = room.privacy.trim().lowercase() in
                setOf("privat", "private", "pribadi", "locked")
            tvRoomPrivacy.text =
                if (isPrivate) "🔒 Privat" else "🌍 Publik"

            tvFavorite.text =
                if (room.roomId in favoriteIds) "★" else "☆"
            tvFavorite.contentDescription =
                if (room.roomId in favoriteIds) {
                    "Hapus dari favorit"
                } else {
                    "Tambahkan ke favorit"
                }
            tvFavorite.setOnClickListener {
                onFavoriteClick?.invoke(room)
            }

            if (room.coverUrl.isNotBlank()) {
                ivCover.visibility = View.VISIBLE
                tvCoverIcon.visibility = View.GONE
                ivCover.load(room.coverUrl) {
                    crossfade(true)
                }
            } else {
                ivCover.visibility = View.GONE
                tvCoverIcon.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                onClick(room)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount(): Int = rooms.size
}
