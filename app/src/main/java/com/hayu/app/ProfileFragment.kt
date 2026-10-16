package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
        val btnEditProfile = view.findViewById<TextView>(R.id.btn_edit_profile)
        val btnLogout = view.findViewById<TextView>(R.id.btn_logout)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finishAffinity()
            return
        }

        tvEmail.text = currentUser.email ?: ""

        loadProfile(tvNickname, tvUserId, tvProfileGenderAge, tvLevelBadge, tvCoins)

        // Tap nama → buka profil sendiri (view-only)
        tvNickname.setOnClickListener {
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra("uid", currentUser.uid)
            startActivity(intent)
        }

        // Tombol Edit Profil
        btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        btnLogout.setOnClickListener { doLogout() }

        // Quick menu
        view.findViewById<LinearLayout>(R.id.quick_badge).setOnClickListener { notif("Lencana segera hadir! 🏆") }
        view.findViewById<LinearLayout>(R.id.quick_shop).setOnClickListener { notif("Toko segera hadir! 🏪") }
        view.findViewById<LinearLayout>(R.id.quick_relations).setOnClickListener { notif("Hubungan segera hadir! 🤝") }
        view.findViewById<LinearLayout>(R.id.quick_level).setOnClickListener { notif("Level segera hadir! 👑") }

        // Kartu SVIP/VIP
        view.findViewById<LinearLayout>(R.id.card_svip).setOnClickListener { notif("SVIP segera hadir! 👑") }
        view.findViewById<LinearLayout>(R.id.card_vip).setOnClickListener { notif("VIP segera hadir! ⭐") }

        // DOMPET → buka DailyCoinActivity (Klaim Koin Harian)
        view.findViewById<LinearLayout>(R.id.card_wallet).setOnClickListener {
            startActivity(Intent(requireContext(), DailyCoinActivity::class.java))
        }

        // Menu list
        view.findViewById<TextView>(R.id.menu_tugas).setOnClickListener { notif("Tugas segera hadir! 📋") }
        view.findViewById<TextView>(R.id.menu_family).setOnClickListener { notif("Family segera hadir! 👨‍👩‍👧") }
        view.findViewById<TextView>(R.id.menu_kartu_pass).setOnClickListener { notif("Kartu Pass segera hadir! 🎫") }
        view.findViewById<TextView>(R.id.menu_invite).setOnClickListener { notif("Invite Friends segera hadir! 👥") }
        view.findViewById<TextView>(R.id.menu_gift).setOnClickListener { notif("Kotak Hadiah segera hadir! 🎁") }

        // Menu bawah
        view.findViewById<TextView>(R.id.menu_settings).setOnClickListener { notif("Pengaturan segera hadir! ⚙️") }
        view.findViewById<TextView>(R.id.menu_help).setOnClickListener { notif("Bantuan segera hadir! ❓") }
        view.findViewById<TextView>(R.id.menu_about).setOnClickListener {
            Toast.makeText(requireContext(), "HAYU v1.0 — Teman baru, obrolan seru 💜", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data setiap balik dari Edit Profile / Klaim Koin
        view?.let {
            val tvNickname = it.findViewById<TextView>(R.id.tv_nickname)
            val tvUserId = it.findViewById<TextView>(R.id.tv_user_id)
            val tvProfileGenderAge = it.findViewById<TextView>(R.id.tv_profile_gender_age)
            val tvLevelBadge = it.findViewById<TextView>(R.id.tv_level_badge)
            val tvCoins = it.findViewById<TextView>(R.id.tv_coins)
            loadProfile(tvNickname, tvUserId, tvProfileGenderAge, tvLevelBadge, tvCoins)
        }
    }

    private fun loadProfile(
        tvNickname: TextView,
        tvUserId: TextView,
        tvProfileGenderAge: TextView,
        tvLevelBadge: TextView,
        tvCoins: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val nickname = doc.getString("nickname") ?: "Pengguna HAYU"
                tvNickname.text = nickname

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
            }
            .addOnFailureListener {
                tvNickname.text = "Pengguna HAYU"
                tvUserId.text = "ID: -"
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
