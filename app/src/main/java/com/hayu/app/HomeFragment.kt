package com.hayu.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    private val allRooms = mutableListOf<Room>()
    private val favoriteIds = mutableSetOf<String>()
    private val sortButtons = mutableMapOf<String, TextView>()

    private var searchQuery = ""
    private var searchInput: EditText? = null
    private var sortMode = "Populer"
    private var emptyLayout: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val btnAddRoomHeader =
            view.findViewById<ImageView>(R.id.btn_add_room_header)
        val btnSearchRoom = view.findViewById<TextView>(R.id.btn_search_room)
        val rvRooms = view.findViewById<RecyclerView>(R.id.rv_rooms)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layout_empty)

        emptyLayout = layoutEmpty

        view.findViewById<LinearLayout>(R.id.menu_cp_bff).setOnClickListener {
            startActivity(
                Intent(requireContext(), CpBffActivity::class.java)
                    .putExtra("title", "CP & BFF")
                    .putExtra("mode", "cp")
            )
        }

        requireContext()
            .getSharedPreferences("hayu_room_favorites", Context.MODE_PRIVATE)
            .getStringSet("room_ids", emptySet())
            ?.let { favoriteIds.addAll(it) }

        rvRooms.layoutManager = GridLayoutManager(requireContext(), 2)

        adapter = RoomAdapter { room ->
            val session = SessionManager(requireContext())
            session.setActiveRoomId(room.roomId)
            session.setActiveRoomName(room.name)

            val intent = Intent(requireContext(), RoomActivity::class.java)
            intent.putExtra("roomId", room.roomId)
            startActivity(intent)
        }

        adapter.setFavorites(favoriteIds) { room ->
            toggleFavorite(room)
        }
        rvRooms.adapter = adapter

        addSearchAndFilters(rvRooms)

        btnSearchRoom.setOnClickListener {
            val input = searchInput ?: return@setOnClickListener
            val imm = requireContext().getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as android.view.inputmethod.InputMethodManager

            if (input.visibility == View.VISIBLE) {
                input.setText("")
                input.visibility = View.GONE
                input.clearFocus()
                imm.hideSoftInputFromWindow(input.windowToken, 0)
            } else {
                input.visibility = View.VISIBLE
                input.requestFocus()
                imm.showSoftInput(
                    input,
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                )
            }
        }

        btnAddRoomHeader.setOnClickListener { checkUserRoom() }

        loadRooms(layoutEmpty)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun roundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun addSearchAndFilters(rvRooms: RecyclerView) {
        val parent = rvRooms.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(rvRooms)
        if (index < 0) return

        val search = EditText(requireContext()).apply {
            hint = "Cari nama room atau topik..."
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBackground(Color.rgb(39, 39, 54))
            visibility = View.GONE
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        searchInput = search

        parent.addView(
            search,
            index,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                setMargins(dp(12), dp(10), dp(12), dp(6))
            }
        )

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                searchQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                applyFilters()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        val sortRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        parent.addView(
            sortRow,
            index + 1,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                setMargins(dp(12), dp(12), dp(12), dp(8))
            }
        )

        listOf("Populer", "Terbaru", "Favorit").forEach { mode ->
            val button = TextView(requireContext()).apply {
                text = mode
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBackground(Color.rgb(39, 39, 54))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    sortMode = mode
                    updateSortStyles()
                    applyFilters()
                }
            }

            sortButtons[mode] = button
            sortRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }

        updateSortStyles()
    }

    private fun updateSortStyles() {
        sortButtons.forEach { (mode, button) ->
            button.background = roundedBackground(
                if (mode == sortMode) Color.rgb(105, 75, 230)
                else Color.rgb(39, 39, 54)
            )
        }
    }

    private fun isPublicRoom(room: Room): Boolean {
        return room.privacy.trim().lowercase() !in
            setOf("privat", "private", "pribadi", "locked")
    }

    private fun applyFilters() {
        if (!::adapter.isInitialized) return

        val filtered = allRooms.filter { room ->
            val matchesSearch =
                searchQuery.isEmpty() ||
                room.name.contains(searchQuery, ignoreCase = true) ||
                room.topic.contains(searchQuery, ignoreCase = true)

            val matchesFavorite =
                sortMode != "Favorit" || room.roomId in favoriteIds

            matchesSearch && matchesFavorite && isPublicRoom(room)
        }

        adapter.setFavorites(favoriteIds) { room ->
            toggleFavorite(room)
        }

        val sorted = when (sortMode) {
            "Terbaru" -> filtered.sortedByDescending { it.createdAt }
            else -> filtered.sortedWith(
                compareByDescending<Room> { it.currentUsers }
                    .thenByDescending { it.lastActiveAt }
            )
        }

        adapter.submitList(sorted)
        emptyLayout?.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleFavorite(room: Room) {
        if (room.roomId in favoriteIds) {
            favoriteIds.remove(room.roomId)
            Toast.makeText(
                requireContext(),
                "Dihapus dari favorit",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            favoriteIds.add(room.roomId)
            Toast.makeText(
                requireContext(),
                "Ditambahkan ke favorit ⭐",
                Toast.LENGTH_SHORT
            ).show()
        }

        requireContext()
            .getSharedPreferences("hayu_room_favorites", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("room_ids", HashSet(favoriteIds))
            .apply()

        applyFilters()
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val layoutEmpty = it.findViewById<LinearLayout>(R.id.layout_empty)
            if (::db.isInitialized && ::adapter.isInitialized) {
                loadRooms(layoutEmpty)
            }
        }
    }

    // Pertahankan logika room milik user dan pemulihan room lama.
    private fun checkUserRoom() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(
                requireContext(),
                "Sesi habis, login ulang",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

        db.collection("rooms")
            .whereEqualTo("ownerUid", uid)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val allMyRooms = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { Room.fromDoc(doc.id, it) }
                }

                val activeRoom = allMyRooms.firstOrNull {
                    it.active && it.lastActiveAt > fiveMinutesAgo
                }

                if (activeRoom != null) {
                    masukRoom(activeRoom)
                    return@addOnSuccessListener
                }

                val oldRoom = allMyRooms.maxByOrNull { it.createdAt }

                if (oldRoom != null) {
                    val newTime = System.currentTimeMillis()
                    db.collection("rooms").document(oldRoom.roomId)
                        .update(
                            "active", true,
                            "lastActiveAt", newTime
                        )
                        .addOnSuccessListener {
                            Toast.makeText(
                                requireContext(),
                                "Room lama diaktifkan kembali! 🔄",
                                Toast.LENGTH_SHORT
                            ).show()
                            masukRoom(
                                oldRoom.copy(active = true, lastActiveAt = newTime)
                            )
                        }
                        .addOnFailureListener {
                            startActivity(
                                Intent(requireContext(), CreateRoomActivity::class.java)
                            )
                        }
                    return@addOnSuccessListener
                }

                startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
            }
            .addOnFailureListener {
                startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
            }
    }

    private fun masukRoom(room: Room) {
        val session = SessionManager(requireContext())
        session.setActiveRoomId(room.roomId)
        session.setActiveRoomName(room.name)

        val intent = Intent(requireContext(), RoomActivity::class.java)
        intent.putExtra("roomId", room.roomId)
        startActivity(intent)
    }

    private fun loadRooms(layoutEmpty: LinearLayout) {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

        db.collection("rooms")
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val rooms = snapshot.documents
                    .mapNotNull { doc ->
                        doc.data?.let { Room.fromDoc(doc.id, it) }
                    }
                    .filter {
                        it.active &&
                        it.lastActiveAt > fiveMinutesAgo &&
                        isPublicRoom(it)
                    }
                    .sortedByDescending { it.createdAt }

                allRooms.clear()
                allRooms.addAll(rooms)
                applyFilters()
            }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
                if (::adapter.isInitialized) adapter.submitList(emptyList())
            }
    }
}
