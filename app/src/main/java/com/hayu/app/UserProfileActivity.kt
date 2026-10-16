package com.hayu.app

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import coil.load

class UserProfileActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var targetUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        db = FirebaseFirestore.getInstance()
        targetUid = intent.getStringExtra("uid")

        if (targetUid.isNullOrEmpty()) {
            Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnBack = findViewById<TextView>(R.id.btn_back)
        val imgAvatar = findViewById<ImageView>(R.id.img_profile_avatar)
        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvGenderAge = findViewById<TextView>(R.id.tv_profile_gender_age)
        val tvCountry = findViewById<TextView>(R.id.tv_profile_country)
        val tvUserId = findViewById<TextView>(R.id.tv_profile_user_id)
        val tvBio = findViewById<TextView>(R.id.tv_profile_bio)
        val tvJoinedDays = findViewById<TextView>(R.id.tv_joined_days)
        val tvSpecialName = findViewById<TextView>(R.id.tv_special_name)
        val tabProfile = findViewById<TextView>(R.id.tab_profile)
        val tabMomen = findViewById<TextView>(R.id.tab_momen)
        val btnChat = findViewById<TextView>(R.id.btn_chat)
        val btnAddFriend = findViewById<TextView>(R.id.btn_add_friend)
        val btnFollow = findViewById<TextView>(R.id.btn_follow)
        val btnSpecialInvite = findViewById<LinearLayout>(R.id.btn_special_invite)

        btnBack.setOnClickListener { finish() }

        // Load data user
        db.collection("users").document(targetUid!!).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val nickname = doc.getString("nickname") ?: "Pengguna HAYU"
                val age = (doc.getLong("age") ?: 0L).toInt()
                val gender = doc.getString("gender") ?: ""
                val country = doc.getString("country") ?: ""
                val bio = doc.getString("bio") ?: ""
                val photoUrl = doc.getString("photoUrl") ?: ""
                val userId = (doc.getLong("userId") ?: 0L)
                val createdAt = (doc.getLong("createdAt") ?: 0L)

                tvName.text = nickname
                tvSpecialName.text = nickname

                // Gender badge
                val genderIcon = when (gender.lowercase()) {
                    "pria" -> "♂️"
                    "wanita" -> "♀️"
                    else -> "👤"
                }
                tvGenderAge.text = "$genderIcon$age"

                if (gender.lowercase() == "wanita") {
                    tvGenderAge.setBackgroundResource(R.drawable.bg_gender_female)
                } else {
                    tvGenderAge.setBackgroundResource(R.drawable.bg_gender_male)
                }

                // Country
                tvCountry.text = if (country.isEmpty()) "🌏 -" else country

                // ID
                if (userId > 0) {
                    tvUserId.text = "ID: $userId"
                } else {
                    tvUserId.text = "ID: -"
                }

                // Bio
                tvBio.text = if (bio.isEmpty()) "Belum ada bio" else bio

                // Hari bergabung
                if (createdAt > 0) {
                    val days = ((System.currentTimeMillis() - createdAt) / 86400000L).toInt()
                    val dayText = if (days <= 0) "1 hari" else "$days hari"
                    tvJoinedDays.text = "📅 Hari bergabung: $dayText"
                } else {
                    tvJoinedDays.text = "📅 Hari bergabung: 1 hari"
                }

                // Foto
                if (photoUrl.isNotEmpty()) {
                    imgAvatar.load(photoUrl) { crossfade(true) }
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_profile)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                finish()
            }

        // Tab Profil / Momen
        tabProfile.setOnClickListener {
            tabProfile.setTextColor(getColor(R.color.hayu_text))
            tabProfile.setTypeface(null, android.graphics.Typeface.BOLD)
            tabMomen.setTextColor(getColor(R.color.hayu_text_secondary))
            tabMomen.setTypeface(null, android.graphics.Typeface.NORMAL)
            Toast.makeText(this, "Tab Profil", Toast.LENGTH_SHORT).show()
        }

        tabMomen.setOnClickListener {
            tabMomen.setTextColor(getColor(R.color.hayu_text))
            tabMomen.setTypeface(null, android.graphics.Typeface.BOLD)
            tabProfile.setTextColor(getColor(R.color.hayu_text_secondary))
            tabProfile.setTypeface(null, android.graphics.Typeface.NORMAL)
            Toast.makeText(this, "Momen segera hadir! 📸", Toast.LENGTH_SHORT).show()
        }

        // Tombol aksi
        btnChat.setOnClickListener {
            Toast.makeText(this, "Fitur Chat segera hadir! 💬", Toast.LENGTH_SHORT).show()
        }

        btnAddFriend.setOnClickListener {
            Toast.makeText(this, "Fitur Tambah Teman segera hadir! 👥", Toast.LENGTH_SHORT).show()
        }

        btnFollow.setOnClickListener {
            Toast.makeText(this, "Fitur Ikuti segera hadir! ❤️", Toast.LENGTH_SHORT).show()
        }

        btnSpecialInvite.setOnClickListener {
            Toast.makeText(this, "Fitur Hubungan Spesial segera hadir! 💕", Toast.LENGTH_SHORT).show()
        }
    }
}
