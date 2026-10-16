package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.adapter.ChatListAdapter
import com.hayu.app.model.DMChat

class ChatFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ChatListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val rvChats = view.findViewById<RecyclerView>(R.id.rv_chat_list)
        val layoutEmpty = view.findViewById<LinearLayout>(R.id.layout_empty_chat)

        adapter = ChatListAdapter { chat ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("peerUid", chat.peerUid)
            startActivity(intent)
        }

        rvChats.layoutManager = LinearLayoutManager(requireContext())
        rvChats.adapter = adapter

        loadChats(layoutEmpty)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            val layoutEmpty = it.findViewById<LinearLayout>(R.id.layout_empty_chat)
            loadChats(layoutEmpty)
        }
    }

    private fun loadChats(layoutEmpty: LinearLayout) {
        val myUid = auth.currentUser?.uid ?: return

        // Query chat yang saya ikut (user1)
        db.collection("dm_chats")
            .whereEqualTo("user1Uid", myUid)
            .get()
            .addOnSuccessListener { snapshot1 ->
                val list1 = snapshot1.documents.mapNotNull { doc ->
                    doc.data?.let { DMChat.fromDoc(doc.id, it, myUid) }
                }

                // Query kedua (user2)
                db.collection("dm_chats")
                    .whereEqualTo("user2Uid", myUid)
                    .get()
                    .addOnSuccessListener { snapshot2 ->
                        val list2 = snapshot2.documents.mapNotNull { doc ->
                            doc.data?.let { DMChat.fromDoc(doc.id, it, myUid) }
                        }

                        val allChats = (list1 + list2)
                            .distinctBy { it.chatId }
                            .sortedByDescending { it.lastTimestamp }

                        adapter.submitList(allChats)

                        if (allChats.isEmpty()) {
                            layoutEmpty.visibility = View.VISIBLE
                        } else {
                            layoutEmpty.visibility = View.GONE
                        }
                    }
                    .addOnFailureListener {
                        layoutEmpty.visibility = View.VISIBLE
                    }
            }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
            }
    }
}
