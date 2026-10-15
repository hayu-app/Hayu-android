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
        val shortId = currentUser.uid.take(6).uppercase()
        tvUserId.text = "ID: $shortId"

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                val nickname = doc.getString("nickname") ?: "Pengguna HAYU"
                tvNickname.text = nickname

                val coins = (doc.getLong("coins") ?: 100L).toInt()
                tvCoins.text = "🪙 $coins"

                val level = (doc.getLong("level") ?: 1L).toInt()
                tvLevelBadge.text = "Lv. $level"
            }
            .addOnFailureListener {
                tvNickname.text = "Pengguna HAYU"
            }

        btnEditProfile.setOnClickListener { notif("Edit Profil segera hadir! ✏️") }
        btnLogout.setOnClickListener { doLogout() }

        // Quick menu
        view.findViewById<LinearLayout>(R.id.quick_badge).setOnClickListener { notif("Lencana segera hadir! 🏆") }
        view.findViewById<LinearLayout>(R.id.quick_shop).setOnClickListener { notif("Toko segera hadir! 🏪") }
        view.findViewById<LinearLayout>(R.id.quick_relations).setOnClickListener { notif("Hubungan segera hadir! 🤝") }
        view.findViewById<LinearLayout>(R.id.quick_level).setOnClickListener { notif("Level segera hadir! 👑") }

        // Kartu SVIP/VIP
        view.findViewById<LinearLayout>(R.id.card_svip).setOnClickListener { notif("SVIP segera hadir! 👑") }
        view.findViewById<LinearLayout>(R.id.card_vip).setOnClickListener { notif("VIP segera hadir! ⭐") }

        // Dompet
        view.findViewById<LinearLayout>(R.id.card_wallet).setOnClickListener { notif("Dompet segera hadir! 💰") }

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
