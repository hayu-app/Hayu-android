package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val tvEmail = view.findViewById<TextView>(R.id.tv_email)
        val btnEditProfile = view.findViewById<TextView>(R.id.btn_edit_profile)
        val menuSettings = view.findViewById<TextView>(R.id.menu_settings)
        val menuHelp = view.findViewById<TextView>(R.id.menu_help)
        val menuAbout = view.findViewById<TextView>(R.id.menu_about)
        val btnLogout = view.findViewById<TextView>(R.id.btn_logout)

        // Ambil data user dari Firebase
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Belum login → balik ke LoginActivity
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finishAffinity()
            return
        }

        tvEmail.text = currentUser.email ?: ""

        // Ambil nickname dari Firestore
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                val nickname = doc.getString("nickname") ?: "Pengguna HAYU"
                tvNickname.text = nickname
            }
            .addOnFailureListener {
                tvNickname.text = "Pengguna HAYU"
            }

        // Tombol Edit Profil
        btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profil segera hadir! ✏️", Toast.LENGTH_SHORT).show()
        }

        // Menu
        menuSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Pengaturan segera hadir! ⚙️", Toast.LENGTH_SHORT).show()
        }

        menuHelp.setOnClickListener {
            Toast.makeText(requireContext(), "Bantuan segera hadir! ❓", Toast.LENGTH_SHORT).show()
        }

        menuAbout.setOnClickListener {
            Toast.makeText(requireContext(), "HAYU v1.0 — Teman baru, obrolan seru 💜", Toast.LENGTH_LONG).show()
        }

        // Tombol Logout
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Berhasil logout 👋", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finishAffinity()
        }
    }
}
