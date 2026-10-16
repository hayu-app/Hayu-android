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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.adapter.NotificationAdapter
import com.hayu.app.model.Notification

class NotificationFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val rvNotif = view.findViewById<RecyclerView>(R.id.rv_notifications)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layout_empty_notif)
        val tvMarkAllRead = view.findViewById<TextView>(R.id.tv_mark_all_read)

        adapter = NotificationAdapter { notif ->
            // Tap notifikasi → tandai dibaca + buka profil pengirim
            if (!notif.read) {
                db.collection("notifications").document(notif.notificationId)
                    .update("read", true)
            }

            // Buka profil pengirim kalau ada
            if (notif.fromUid.isNotEmpty()) {
                val intent = Intent(requireContext(), UserProfileActivity::class.java)
                intent.putExtra("uid", notif.fromUid)
                startActivity(intent)
            }
        }

        rvNotif.layoutManager = LinearLayoutManager(requireContext())
        rvNotif.adapter = adapter

        tvMarkAllRead.setOnClickListener { markAllRead() }

        loadNotifications(layoutEmpty)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val layoutEmpty = it.findViewById<LinearLayout>(R.id.layout_empty_notif)
            loadNotifications(layoutEmpty)
        }
    }

    private fun loadNotifications(layoutEmpty: LinearLayout) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { doc ->
                        doc.data?.let { Notification.fromDoc(doc.id, it) }
                    }
                    .sortedByDescending { it.timestamp }

                adapter.submitList(list)

                if (list.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                } else {
                    layoutEmpty.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
            }
    }

    private fun markAllRead() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .whereEqualTo("read", false)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(requireContext(), "Semua sudah dibaca", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (doc in snapshot.documents) {
                    doc.reference.update("read", true)
                }
                Toast.makeText(requireContext(), "Semua ditandai dibaca ✅", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
