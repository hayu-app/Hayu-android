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
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private var bannerPager: ViewPager2? = null
    private var bannerRunnable: Runnable? = null
    private var roomsListener: ListenerRegistration? = null

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
                Intent(requireContext(), CpBffRankActivity::class.java)
                    .putExtra("title", "Peringkat CP & BFF")
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

        setupFilters(view)
        setupBanner(view)

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

    private fun setupFilters(root: View) {
        val search = root.findViewById<EditText>(R.id.et_search_room)
        searchInput = search
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        mapOf(
            "Populer" to R.id.filter_populer,
            "Terbaru" to R.id.filter_terbaru,
            "Favorit" to R.id.filter_favorit
        ).forEach { (mode, id) ->
            val chip = root.findViewById<TextView>(id)
            sortButtons[mode] = chip
            chip.setOnClickListener {
                sortMode = mode
                updateSortStyles()
                applyFilters()
            }
        }
        updateSortStyles()
    }

    private fun updateSortStyles() {
        sortButtons.forEach { (mode, chip) ->
            val selected = mode == sortMode
            chip.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            chip.setTextColor(if (selected) Color.WHITE else Color.parseColor("#9AA3BD"))
        }
    }

    private fun applyBanners(pager: ViewPager2, dots: LinearLayout, banners: List<HomeBannerAdapter.Banner>) {
        pager.adapter = HomeBannerAdapter(banners)
        dots.removeAllViews()
        banners.forEachIndexed { i, _ ->
            val dot = View(requireContext())
            dot.setBackgroundResource(R.drawable.bg_banner_dot)
            dot.alpha = if (i == 0) 1f else 0.4f
            dots.addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        dots.visibility = if (banners.size > 1) View.VISIBLE else View.GONE
    }

    private fun setupBanner(root: View) {
        val pager = root.findViewById<ViewPager2>(R.id.banner_pager)
        val dots = root.findViewById<LinearLayout>(R.id.banner_dots)
        pager.offscreenPageLimit = 1

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dots.childCount) dots.getChildAt(i).alpha = if (i == position) 1f else 0.4f
            }
        })

        applyBanners(pager, dots, HomeBannerAdapter.defaultBanners())

        // Banner dari panel developer (kalau ada)
        db.collection("app_config").document("home_banners").get()
            .addOnSuccessListener { doc ->
                if (!isAdded || view == null) return@addOnSuccessListener
                val list = HomeBannerAdapter.fromConfig(doc.get("items"))
                if (list != null) applyBanners(pager, dots, list)
            }

        bannerRunnable = object : Runnable {
            override fun run() {
                val count = pager.adapter?.itemCount ?: 0
                if (count > 1 && isAdded) pager.setCurrentItem((pager.currentItem + 1) % count, true)
                pager.postDelayed(this, 4000L)
            }
        }
        bannerPager = pager
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

    override fun onPause() {
        super.onPause()
        bannerRunnable?.let { bannerPager?.removeCallbacks(it) }
    }

    override fun onDestroyView() {
        bannerRunnable?.let { bannerPager?.removeCallbacks(it) }
        bannerPager = null
        bannerRunnable = null
        roomsListener?.remove()   // stop realtime listener saat fragment destroyed
        roomsListener = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        bannerRunnable?.let {
            bannerPager?.removeCallbacks(it)
            bannerPager?.postDelayed(it, 4000L)
        }
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
        // Hentikan listener lama kalau ada (cegah duplikat saat onResume)
        roomsListener?.remove()

        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

        // addSnapshotListener → update otomatis setiap ada perubahan di Firestore
        // Termasuk saat currentUsers berubah karena user masuk/keluar room
        roomsListener = db.collection("rooms")
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    layoutEmpty.visibility = View.VISIBLE
                    if (::adapter.isInitialized) adapter.submitList(emptyList())
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val rooms = snapshot.documents
                    .mapNotNull { doc ->
                        doc.data?.let { Room.fromDoc(doc.id, it) }
                    }
                    .filter {
                        it.active &&
                        it.lastActiveAt > now - (5 * 60 * 1000) &&
                        isPublicRoom(it)
                    }
                    .sortedByDescending { it.createdAt }

                allRooms.clear()
                allRooms.addAll(rooms)
                layoutEmpty.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
    }
}
