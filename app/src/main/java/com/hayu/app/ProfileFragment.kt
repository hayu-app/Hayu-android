package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import coil.load

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val tvNickname = view.findViewById<TextView>(R.id.tv_nickname)
        val tvUserId = view.findViewById<TextView>(R.id.tv_user_id)
        val tvEmail = view.findViewById<TextView>(R.id.tv_email)
        val tvProfileGenderAge = view.findViewById<TextView>(R.id.tv_profile_gender_age)
        val tvLevelBadge = view.findViewById<TextView>(R.id.tv_level_badge)
        val tvCoins = view.findViewById<TextView>(R.id.tv_coins)
        val tvFriendsCount = view.findViewById<TextView>(R.id.tv_friends_count)
        val tvFollowingCount = view.findViewById<TextView>(R.id.tv_following_count)
        val tvFollowersCount = view.findViewById<TextView>(R.id.tv_followers_count)
        val btnEditProfile = view.findViewById<TextView>(R.id.btn_edit_profile)
        val btnLogout = view.findViewById<TextView>(R.id.btn_logout)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finishAffinity()
            return
        }

        tvEmail.text = currentUser.email ?: ""

        loadProfile(view, tvNickname, tvUserId, tvProfileGenderAge, tvLevelBadge, tvCoins)
        loadFollowStats(tvFriendsCount, tvFollowingCount, tvFollowersCount)
        loadRoleBadge(view)

        // Profil akun sendiri sudah ditampilkan di halaman ini.
        // UserProfileActivity tetap digunakan untuk profil pengguna lain.
        tvNickname.setOnClickListener { }

        btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        btnLogout.setOnClickListener { doLogout() }

        view.findViewById<LinearLayout>(R.id.quick_badge).setOnClickListener { startActivity(Intent(requireContext(), LencanaActivity::class.java)) }
        view.findViewById<LinearLayout>(R.id.quick_shop).setOnClickListener { startActivity(Intent(requireContext(), TokoActivity::class.java)) }
        view.findViewById<LinearLayout>(R.id.quick_relations).setOnClickListener { notif("Hubungan segera hadir! 🤝") }
        view.findViewById<LinearLayout>(R.id.quick_level).setOnClickListener { startActivity(Intent(requireContext(), LevelActivity::class.java)) }

        view.findViewById<LinearLayout>(R.id.card_svip).setOnClickListener { startActivity(Intent(requireContext(), VipActivity::class.java)) }
        view.findViewById<LinearLayout>(R.id.card_vip).setOnClickListener { startActivity(Intent(requireContext(), VipActivity::class.java)) }

        view.findViewById<LinearLayout>(R.id.card_wallet).setOnClickListener {
            startActivity(Intent(requireContext(), DailyCoinActivity::class.java))
        }

        view.findViewById<View>(R.id.menu_tugas).setOnClickListener { startActivity(Intent(requireContext(), TugasActivity::class.java)) }
        view.findViewById<View>(R.id.menu_family).setOnClickListener { notif("Family segera hadir! 👨‍👩‍👧") }
        view.findViewById<View>(R.id.menu_kartu_pass).setOnClickListener { notif("Kartu Pass segera hadir! 🎫") }
        view.findViewById<View>(R.id.menu_invite).setOnClickListener { notif("Invite Friends segera hadir! 👥") }
        view.findViewById<View>(R.id.menu_gift).setOnClickListener { notif("Kotak Hadiah segera hadir! 🎁") }

        view.findViewById<View>(R.id.menu_settings).setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val role = Role.normalize(doc.getString("role"))
                    if (Role.canAccess(role, "dashboard")) {
                        startActivity(Intent(requireContext(), DevPanelActivity::class.java))
                    } else {
                        Toast.makeText(requireContext(), "Akses ditolak", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Gagal cek akses", Toast.LENGTH_SHORT).show()
                }
        }
        view.findViewById<View>(R.id.menu_help).setOnClickListener { notif("Bantuan segera hadir! ❓") }
        view.findViewById<View>(R.id.menu_about).setOnClickListener {
            Toast.makeText(requireContext(), "HAYU v1.0 — Teman baru, obrolan seru 💜", Toast.LENGTH_LONG).show()
        }

        // Menu Panel Role (dengan pengaman ?. agar tidak crash)
        view.findViewById<View>(R.id.menu_dev_panel)?.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val role = Role.normalize(doc.getString("role"))
                    if (Role.canAccess(role, "dashboard")) {
                        startActivity(Intent(requireContext(), DevPanelActivity::class.java))
                    } else {
                        Toast.makeText(requireContext(), "Akses ditolak", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val tvNickname = it.findViewById<TextView>(R.id.tv_nickname)
            val tvUserId = it.findViewById<TextView>(R.id.tv_user_id)
            val tvProfileGenderAge = it.findViewById<TextView>(R.id.tv_profile_gender_age)
            val tvLevelBadge = it.findViewById<TextView>(R.id.tv_level_badge)
            val tvCoins = it.findViewById<TextView>(R.id.tv_coins)
            val tvFriendsCount = it.findViewById<TextView>(R.id.tv_friends_count)
            val tvFollowingCount = it.findViewById<TextView>(R.id.tv_following_count)
            val tvFollowersCount = it.findViewById<TextView>(R.id.tv_followers_count)
            loadProfile(it, tvNickname, tvUserId, tvProfileGenderAge, tvLevelBadge, tvCoins)
            loadFollowStats(tvFriendsCount, tvFollowingCount, tvFollowersCount)
            loadRoleBadge(it)
        }
    }

// LANJUT BAGIAN 2 DI BAWAH INI
    private fun loadProfile(
        rootView: View,
        tvNickname: TextView,
        tvUserId: TextView,
        tvProfileGenderAge: TextView,
        tvLevelBadge: TextView,
        tvCoins: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        val imgAvatar = rootView.findViewById<ImageView>(R.id.img_avatar)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val nickname = doc.getString("nickname") ?: "Pengguna HAYU"
                val gelar = doc.getString("equippedTitle") ?: ""
                tvNickname.text = if (gelar.isNotBlank()) "$gelar $nickname" else nickname

                val age = (doc.getLong("age") ?: 0L).toInt()
                val gender = doc.getString("gender") ?: ""

                val genderIcon = when (gender.lowercase()) {
                    "pria" -> "♂️"
                    "wanita" -> "♀️"
                    else -> "👤"
                }
                tvProfileGenderAge.text = "$genderIcon$age"

                if (gender.lowercase() == "wanita") {
                    tvProfileGenderAge.setBackgroundResource(R.drawable.bg_gender_female)
                } else {
                    tvProfileGenderAge.setBackgroundResource(R.drawable.bg_gender_male)
                }

                val userId = doc.getLong("userId") ?: 0L
                if (userId > 0) {
                    tvUserId.text = "ID: $userId"
                } else {
                    tvUserId.text = "ID: -"
                }

                val coins = (doc.getLong("coins") ?: 100L).toInt()
                tvCoins.text = "🪙 $coins"

                val level = (doc.getLong("level") ?: 1L).toInt()
                tvLevelBadge.text = "Lv. $level"

                val photoUrl = doc.getString("photoUrl") ?: ""
                if (photoUrl.isNotEmpty()) {
                    imgAvatar.setPadding(0, 0, 0, 0)
                    imgAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                    imgAvatar.clipToOutline = true
                    imgAvatar.load(photoUrl) { crossfade(true) }
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_profile)
                }
            }
            .addOnFailureListener {
                tvNickname.text = "Pengguna HAYU"
                tvUserId.text = "ID: -"
            }
    }

    private fun loadFollowStats(tvFriends: TextView, tvFollowing: TextView, tvFollowers: TextView) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("follows")
            .whereEqualTo("followerUid", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val followingUids = snapshot.documents.mapNotNull { it.getString("followingUid") }
                tvFollowing.text = followingUids.size.toString()

                db.collection("follows")
                    .whereEqualTo("followingUid", uid)
                    .get()
                    .addOnSuccessListener { snapshot2 ->
                        val followerUids = snapshot2.documents.mapNotNull { it.getString("followerUid") }
                        tvFollowers.text = followerUids.size.toString()

                        val mutualCount = followingUids.intersect(followerUids.toSet()).size
                        tvFriends.text = mutualCount.toString()
                    }
                    .addOnFailureListener {
                        tvFollowers.text = "0"
                        tvFriends.text = "0"
                    }
            }
            .addOnFailureListener {
                tvFollowing.text = "0"
                tvFollowers.text = "0"
                tvFriends.text = "0"
            }
    }

    private fun loadRoleBadge(rootView: View) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.lowercase()?.trim() ?: "user"
                applyRoleToUI(rootView, role)
            }
            .addOnFailureListener {
                applyRoleToUI(rootView, "user")
            }
    }

    private fun applyRoleToUI(rootView: View, role: String) {
        val badge = rootView.findViewById<TextView>(R.id.tvRoleBadgeProfile)
        val menuPanel = rootView.findViewById<View>(R.id.menu_dev_panel)

        // Sembunyikan default
        badge?.visibility = View.GONE
        menuPanel?.visibility = View.GONE

        when (role) {
            "developer" -> {
                badge?.text = "🛡️ DEVELOPER"
                badge?.visibility = View.VISIBLE
                menuPanel?.visibility = View.VISIBLE
            }
            "superadmin" -> {
                badge?.text = "🛡️ SUPERADMIN"
                badge?.visibility = View.VISIBLE
                menuPanel?.visibility = View.VISIBLE
            }
            "admin" -> {
                badge?.text = "⭐ ADMIN"
                badge?.visibility = View.VISIBLE
                menuPanel?.visibility = View.VISIBLE
            }
            "bd" -> {
                badge?.text = "🏡 BD"
                badge?.visibility = View.VISIBLE
                menuPanel?.visibility = View.VISIBLE
            }
            "cs" -> {
                badge?.text = "🎧 CS"
                badge?.visibility = View.VISIBLE
                // CS tidak dapat menu panel
            }
            else -> {
                // user biasa
            }
        }
    }

    private fun notif(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun doLogout() {
        auth.signOut()
        Toast.makeText(requireContext(), "Berhasil logout 👋", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finishAffinity()
    }
}
