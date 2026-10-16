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
import com.hayu.app.util.SessionManager

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
            val session = SessionManager(requireContext())
            session.setActiveRoomId(room.roomId)
            session.setActiveRoomName(room.name)

            val intent = Intent(requireContext(), RoomActivity::class.java)
            intent.putExtra("roomId", room.roomId)
            startActivity(intent)
        }
        rvRooms.adapter = adapter

        btnAddRoomHeader.setOnClickListener { checkUserRoom() }

        loadRooms(layoutEmpty)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val layoutEmpty = it.findViewById<LinearLayout>(R.id.layout_empty)
            loadRooms(layoutEmpty)
        }
    }

    private fun checkUserRoom() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Sesi habis, login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

        db.collection("rooms")
            .whereEqualTo("ownerUid", uid)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val allMyRooms = snapshot.documents
                    .mapNotNull { doc -> doc.data?.let { Room.fromDoc(doc.id, it) } }

                val activeRoom = allMyRooms.firstOrNull {
                    it.active && it.lastActiveAt > fiveMinutesAgo
                }

                allMyRooms.filter { it.active && it.lastActiveAt <= fiveMinutesAgo }
                    .forEach { oldRoom ->
                        db.collection("rooms").document(oldRoom.roomId)
                            .update("active", false)
                    }

                if (activeRoom != null) {
                    val session = SessionManager(requireContext())
                    session.setActiveRoomId(activeRoom.roomId)
                    session.setActiveRoomName(activeRoom.name)

                    val intent = Intent(requireContext(), RoomActivity::class.java)
                    intent.putExtra("roomId", activeRoom.roomId)
                    startActivity(intent)
                } else {
                    startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
                }
            }
            .addOnFailureListener {
                startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
            }
    }

    private fun loadRooms(layoutEmpty: LinearLayout) {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

        db.collection("rooms")
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val rooms = snapshot.documents
                    .mapNotNull { doc -> doc.data?.let { Room.fromDoc(doc.id, it) } }
                    .filter { it.active && it.lastActiveAt > fiveMinutesAgo }
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
