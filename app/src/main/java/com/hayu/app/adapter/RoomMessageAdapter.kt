package com.hayu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hayu.app.R
import com.hayu.app.model.RoomMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoomMessageAdapter : RecyclerView.Adapter<RoomMessageAdapter.MsgViewHolder>() {

    private val messages = mutableListOf<RoomMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<RoomMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    inner class MsgViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSender: TextView = itemView.findViewById(R.id.tv_msg_sender)
        val tvTime: TextView = itemView.findViewById(R.id.tv_msg_time)
        val tvText: TextView = itemView.findViewById(R.id.tv_msg_text)

        fun bind(msg: RoomMessage) {
            tvSender.text = msg.senderName
            tvText.text = msg.text
            if (msg.timestamp > 0) {
                tvTime.text = timeFormat.format(Date(msg.timestamp))
            } else {
                tvTime.text = ""
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_message, parent, false)
        return MsgViewHolder(view)
    }

    override fun onBindViewHolder(holder: MsgViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size
}
