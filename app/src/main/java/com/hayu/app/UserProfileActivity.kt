package com.hayu.app

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import coil.load

class UserProfileActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var targetUid: String? = null
    private var isFollowing: Boolean = false      // A follow B
    private var isFollowedBy: Boolean = false     // B follow A (mutual)
    private var isSelf: Boolean = false

    private lateinit var tvFollowingCount: TextView
    private lateinit var tvFollowersCount: TextView
    private lateinit var btnFollow: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        targetUid = intent.getStringExtra("uid")

        if (targetUid.isNullOrEmpty()) {
            Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        isSelf = targetUid == auth.currentUser?.uid

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
        btnFollow = findViewById<TextView>(R.id.btn_follow)
        val btnSpecialInvite = findViewById<LinearLayout>(R.id.btn_special_invite)
        tvFollowingCount = findViewById(R.id.tv_following_count)
        tvFollowersCount = findViewById(R.id.tv_followers_count)

        btnBack.setOnClickListener { finish() }

        if (isSelf) {
            btnChat.isEnabled = false
            btnChat.alpha = 0.5f

            btnAddFriend.isEnabled = false
            btnAddFriend.alpha = 0.5f
            btnAddFriend.text = "✨  Ini Kamu"

            btnFollow.isEnabled = false
            btnFollow.alpha = 0.5f
            btnFollow.text = "✨  Ini Kamu"

            btnSpecialInvite.isEnabled = false
            btnSpecialInvite.alpha = 0.5f
        }

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

                tvCountry.text = if (country.isEmpty()) "🌏 -" else country

                if (userId > 0) {
                    tvUserId.text = "ID: $userId"
                } else {
                    tvUserId.text = "ID: -"
                }

                tvBio.text = if (bio.isEmpty()) "Belum ada bio" else bio

                if (createdAt > 0) {
                    val days = ((System.currentTimeMillis() - createdAt) / 86400000L).toInt()
                    val dayText = if (days <= 0) "1 hari" else "$days hari"
                    tvJoinedDays.text = "📅 Hari bergabung: $dayText"
                } else {
                    tvJoinedDays.text = "📅 Hari bergabung: 1 hari"
                }

                if (photoUrl.isNotEmpty()) {
                    imgAvatar.load(photoUrl) { crossfade(true) }
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_profile)
                }

                if (!isSelf) {
                    checkFollowStatus()
                }
                loadFollowCounts()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                finish()
            }

        tabProfile.setOnClickListener {
            tabProfile.setTextColor(getColor(R.color.hayu_text))
            tabProfile.setTypeface(null, android.graphics.Typeface.BOLD)
            tabMomen.setTextColor(getColor(R.color.hayu_text_secondary))
            tabMomen.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        tabMomen.setOnClickListener {
            tabMomen.setTextColor(getColor(R.color.hayu_text))
            tabMomen.setTypeface(null, android.graphics.Typeface.BOLD)
            tabProfile.setTextColor(getColor(R.color.hayu_text_secondary))
            tabProfile.setTypeface(null, android.graphics.Typeface.NORMAL)
            Toast.makeText(this, "Momen segera hadir! 📸", Toast.LENGTH_SHORT).show()
        }

        btnChat.setOnClickListener {
            if (!isSelf) Toast.makeText(this, "Fitur Chat segera hadir! 💬", Toast.LENGTH_SHORT).show()
        }

        btnAddFriend.setOnClickListener {
            if (!isSelf) {
                val status = if (isFollowing && isFollowedBy) "Kalian sudah berteman 👥"
                    else "Teman otomatis saat saling mengikuti ❤️"
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
            }
        }

        btnFollow.setOnClickListener {
            if (!isSelf) toggleFollow()
        }

        btnSpecialInvite.setOnClickListener {
            if (!isSelf) Toast.makeText(this, "Fitur Hubungan Spesial segera hadir! 💕", Toast.LENGTH_SHORT).show()
        }
    }

    // Cek apakah saya follow target, dan apakah target follow saya
    private fun checkFollowStatus() {
        val myUid = auth.currentUser?.uid ?: return
        val followId = "${myUid}_${targetUid}"      // saya follow target
        val reverseId = "${targetUid}_${myUid}"     // target follow saya

        // Cek 1: apakah saya follow target?
        db.collection("follows").document(followId).get()
            .addOnSuccessListener { doc ->
                isFollowing = doc.exists()

                // Cek 2: apakah target follow saya?
                db.collection("follows").document(reverseId).get()
                    .addOnSuccessListener { doc2 ->
                        isFollowedBy = doc2.exists()
                        updateFollowButton()
                    }
                    .addOnFailureListener {
                        isFollowedBy = false
                        updateFollowButton()
                    }
            }
            .addOnFailureListener {
                isFollowing = false
                updateFollowButton()
            }
    }

    // Update tampilan tombol (3 state)
    private fun updateFollowButton() {
        when {
            isFollowing && isFollowedBy -> {
                // Mutual → Teman
                btnFollow.text = "👥  Teman"
                btnFollow.background = getDrawable(R.drawable.bg_button_primary)
                btnFollow.setTextColor(0xFFFFFFFF.toInt())
            }
            isFollowing -> {
                // Sudah follow, tapi belum follow balik
                btnFollow.text = "❤️  Mengikuti"
                btnFollow.background = getDrawable(R.drawable.bg_button_primary)
                btnFollow.setTextColor(0xFFFFFFFF.toInt())
            }
            else -> {
                // Belum follow
                btnFollow.text = "🤍  Ikuti"
                btnFollow.background = getDrawable(R.drawable.bg_input)
                btnFollow.setTextColor(getColor(R.color.hayu_text))
            }
        }
    }

    private fun toggleFollow() {
        val myUid = auth.currentUser?.uid ?: return
        val followId = "${myUid}_${targetUid}"
        val followRef = db.collection("follows").document(followId)

        btnFollow.isEnabled = false

        if (isFollowing) {
            // Unfollow
            followRef.delete()
                .addOnSuccessListener {
                    isFollowing = false
                    checkFollowStatus()  // refresh
                    loadFollowCounts()
                    Toast.makeText(this, "Berhenti mengikuti", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    btnFollow.isEnabled = true
                }
        } else {
            // Follow
            val followData = hashMapOf(
                "followerUid" to myUid,
                "followingUid" to targetUid,
                "createdAt" to System.currentTimeMillis()
            )
            followRef.set(followData)
                .addOnSuccessListener {
                    isFollowing = true
                    // Cek apakah sekarang jadi mutual
                    checkFollowStatus()
                    loadFollowCounts()
                    sendFollowNotification(myUid)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    btnFollow.isEnabled = true
                }
        }
    }

    // Kirim notifikasi — cek apakah mutual (jadi teman) atau cuma follow
    private fun sendFollowNotification(myUid: String) {
        val reverseId = "${targetUid}_${myUid}"

        db.collection("follows").document(reverseId).get()
            .addOnSuccessListener { doc ->
                val isMutualNow = doc.exists()

                db.collection("users").document(myUid).get()
                    .addOnSuccessListener { myDoc ->
                        val myName = myDoc.getString("nickname") ?: "Pengguna HAYU"

                        val notifData = hashMapOf(
                            "userId" to targetUid,
                            "type" to if (isMutualNow) "friend" else "follow",
                            "fromUid" to myUid,
                            "fromName" to myName,
                            "message" to if (isMutualNow)
                                "sekarang berteman dengan kamu 👥"
                            else
                                "mulai mengikuti kamu",
                            "read" to false,
                            "timestamp" to System.currentTimeMillis()
                        )
                        db.collection("notifications").add(notifData)

                        // Toast ke user yang tap
                        Toast.makeText(
                            this,
                            if (isMutualNow) "Kalian sekarang berteman! 👥" else "Berhasil mengikuti! ❤️",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
    }

    private fun loadFollowCounts() {
        val uid = targetUid ?: return

        db.collection("follows")
            .whereEqualTo("followerUid", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                tvFollowingCount.text = snapshot.size().toString()
            }

        db.collection("follows")
            .whereEqualTo("followingUid", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                tvFollowersCount.text = snapshot.size().toString()
            }
    }
}
