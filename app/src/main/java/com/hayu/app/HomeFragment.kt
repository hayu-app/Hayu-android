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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hayu.app.adapter.RoomAdapter
import com.hayu.app.model.Room

class HomeFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: RoomAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        val btnAddRoomHeader = view.findViewById<ImageView>(R.id.btn_add_room_header)
        val rvRooms = view.findViewById<RecyclerView>(R.id.rv_rooms)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layout_empty)

        // Setup grid 2 kolom
        rvRooms.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = RoomAdapter { room ->
            val intent = Intent(requireContext(), RoomActivity::class.java)
            intent.putExtra("roomId", room.roomId)
            startActivity(intent)
        }
        rvRooms.adapter = adapter

        btnAddRoomHeader.setOnClickListener {
            startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
        }

        loadRooms(layoutEmpty)
    }

    override fun onResume() {
        super.onResume()
        // Refresh setiap balik dari Buat Room
        view?.let {
            val layoutEmpty = it.findViewById<LinearLayout>(R.id.layout_empty)
            loadRooms(layoutEmpty)
        }
    }

    private fun loadRooms(layoutEmpty: LinearLayout) {
        db.collection("rooms")
            .whereEqualTo("active", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val rooms = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { Room.fromDoc(doc.id, it) }
                }

                adapter.submitList(rooms)

                if (rooms.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                } else {
                    layoutEmpty.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Gagal memuat room", Toast.LENGTH_SHORT).show()
            }
    }
}
