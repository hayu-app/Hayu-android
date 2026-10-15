package com.hayu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hayu.app.R
import com.hayu.app.model.User

class UserAdapter(
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val users = mutableListOf<User>()

    fun submitList(list: List<User>) {
        users.clear()
        users.addAll(list)
        notifyDataSetChanged()
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.img_user_avatar)
        val tvName: TextView = itemView.findViewById(R.id.tv_user_name)
        val tvGenderAge: TextView = itemView.findViewById(R.id.tv_user_gender_age)
        val tvCountry: TextView = itemView.findViewById(R.id.tv_user_country)
        val tvBio: TextView = itemView.findViewById(R.id.tv_user_bio)

        fun bind(user: User) {
            tvName.text = if (user.nickname.isEmpty()) "Pengguna HAYU" else user.nickname

            val genderIcon = when (user.gender.lowercase()) {
                "pria" -> "♂️"
                "wanita" -> "♀️"
                else -> "👤"
            }
            tvGenderAge.text = "$genderIcon${user.age}"

            tvCountry.text = if (user.country.isEmpty()) "🌏 -" else user.country

            tvBio.text = if (user.bio.isEmpty()) "Belum ada bio" else user.bio

            if (user.photoUrl.isNotEmpty()) {
                imgAvatar.load(user.photoUrl) {
                    crossfade(true)
                }
            } else {
                imgAvatar.setImageResource(R.drawable.ic_profile)
            }

            itemView.setOnClickListener { onClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size
}
