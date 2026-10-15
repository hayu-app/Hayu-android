package com.hayu.app

import android.os.Bundle
import android.widget.ImageView
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
        val tvLevel = findViewById<TextView>(R.id.tv_profile_level)
        val tvName = findViewById<TextView>(R.id.tv_profile_name)
        val tvGenderAge = findViewById<TextView>(R.id.tv_profile_gender_age)
        val tvCountry = findViewById<TextView>(R.id.tv_profile_country)
        val tvUserId = findViewById<TextView>(R.id.tv_profile_user_id)
        val tvBio = findViewById<TextView>(R.id.tv_profile_bio)
        val tvAboutAge = findViewById<TextView>(R.id.tv_about_age)
        val tvAboutCountry = findViewById<TextView>(R.id.tv_about_country)
        val btnAddFriend = findViewById<TextView>(R.id.btn_add_friend)
        val btnChat = findViewById<TextView>(R.id.btn_chat)
        val btnReport = findViewById<TextView>(R.id.btn_report)
        val btnBlock = findViewById<TextView>(R.id.btn_block)

        btnBack.setOnClickListener { finish() }

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
                val level = (doc.getLong("level") ?: 1L).toInt()
                val userId = (doc.getLong("userId") ?: 0L)

                tvName.text = nickname
                tvLevel.text = "Lv. $level"

                val genderIcon = when (gender.lowercase()) {
                    "pria" -> "♂️"
                    "wanita" -> "♀️"
                    else -> "👤"
                }
                tvGenderAge.text = "$genderIcon$age"
                tvAboutAge.text = " $age"

                if (country.isEmpty()) {
                    tvCountry.text = "🌏 -"
                    tvAboutCountry.text = " -"
                } else {
                    tvCountry.text = country
                    tvAboutCountry.text = " $country"
                }

                if (userId > 0) {
                    tvUserId.text = "ID: $userId"
                } else {
                    tvUserId.text = "ID: -"
                }

                tvBio.text = if (bio.isEmpty()) "Belum ada bio" else bio

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

        btnAddFriend.setOnClickListener {
            Toast.makeText(this, "Fitur Tambah Teman segera hadir! 👥", Toast.LENGTH_SHORT).show()
        }

        btnChat.setOnClickListener {
            Toast.makeText(this, "Fitur Chat segera hadir! 💬", Toast.LENGTH_SHORT).show()
        }

        btnReport.setOnClickListener {
            Toast.makeText(this, "Fitur Laporkan segera hadir! 🚨", Toast.LENGTH_SHORT).show()
        }

        btnBlock.setOnClickListener {
            Toast.makeText(this, "Fitur Blokir segera hadir! 🚫", Toast.LENGTH_SHORT).show()
        }
    }
}
