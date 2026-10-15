package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.adapter.UserAdapter
import com.hayu.app.model.User

class DiscoverFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UserAdapter
    private val allUsers = mutableListOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etSearch = view.findViewById<EditText>(R.id.et_search)
        val rvUsers = view.findViewById<RecyclerView>(R.id.rv_users)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layout_empty)
        val tvUserCount = view.findViewById<TextView>(R.id.tv_user_count)

        adapter = UserAdapter { user ->
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra("uid", user.uid)
            startActivity(intent)
        }

        rvUsers.layoutManager = LinearLayoutManager(requireContext())
        rvUsers.adapter = adapter

        loadUsers(layoutEmpty, tvUserCount)

        // Search filter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim().lowercase()
                filterUsers(query, layoutEmpty, tvUserCount)
            }
        })
    }

    private fun loadUsers(layoutEmpty: LinearLayout, tvUserCount: TextView) {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("onboarded", true)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                allUsers.clear()
                for (doc in snapshot.documents) {
                    if (doc.id == myUid) continue
                    val data = doc.data ?: continue
                    allUsers.add(User.fromDoc(doc.id, data))
                }

                // Urutkan: online dulu, lalu berdasarkan level desc
                allUsers.sortWith(compareByDescending<User> { it.online }
                    .thenByDescending { it.level })

                adapter.submitList(allUsers)
                tvUserCount.text = "${allUsers.size} user"
                layoutEmpty.visibility = if (allUsers.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
            }
    }

    private fun filterUsers(query: String, layoutEmpty: LinearLayout, tvUserCount: TextView) {
        if (query.isEmpty()) {
            adapter.submitList(allUsers)
            tvUserCount.text = "${allUsers.size} user"
            layoutEmpty.visibility = if (allUsers.isEmpty()) View.VISIBLE else View.GONE
            return
        }

        val filtered = allUsers.filter { user ->
            user.nickname.lowercase().contains(query) ||
            user.userId.toString().contains(query)
        }

        adapter.submitList(filtered)
        tvUserCount.text = "${filtered.size} user"
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
