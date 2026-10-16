package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.adapter.RoomAdapter
import com.hayu.app.model.Room

class HomeFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: RoomAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val btnAddRoomHeader = view.findViewById<ImageView>(R.id.btn_add_room_header)
        val rvRooms = view.findViewById<RecyclerView>(R.id.rv_rooms)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layout_empty)

        rvRooms.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = RoomAdapter { room ->
            // Tap room → masuk room
            val intent = Intent(requireContext(), RoomActivity::class.java)
            intent.putExtra("roomId", room.roomId)
            startActivity(intent)
        }
        rvRooms.adapter = adapter

        btnAddRoomHeader.setOnClickListener {
            checkUserRoom()
        }

        loadRooms(layoutEmpty)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val layoutEmpty = it.findViewById<LinearLayout>(R.id.layout_empty)
            loadRooms(layoutEmpty)
        }
    }

    // Cek apakah user sudah punya room aktif
    private fun checkUserRoom() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Sesi habis, login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("rooms")
            .whereEqualTo("ownerUid", uid)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val myRoom = snapshot.documents
                    .mapNotNull { doc -> doc.data?.let { Room.fromDoc(doc.id, it) } }
                    .firstOrNull { it.active }

                if (myRoom != null) {
                    // Sudah punya room → langsung masuk room
                    val intent = Intent(requireContext(), RoomActivity::class.java)
                    intent.putExtra("roomId", myRoom.roomId)
                    startActivity(intent)
                } else {
                    // Belum punya room → form buat room
                    startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
                }
            }
            .addOnFailureListener {
                // Kalau gagal cek, tetap buka form
                startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
            }
    }

    private fun loadRooms(layoutEmpty: LinearLayout) {
        db.collection("rooms")
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val rooms = snapshot.documents
                    .mapNotNull { doc -> doc.data?.let { Room.fromDoc(doc.id, it) } }
                    .filter { it.active }
                    .sortedByDescending { it.createdAt }

                adapter.submitList(rooms)

                if (rooms.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                } else {
                    layoutEmpty.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
            }
    }
}
