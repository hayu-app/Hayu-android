package com.hayu.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

abstract class SimplePageActivity : AppCompatActivity() {

    protected lateinit var auth: FirebaseAuth
    protected lateinit var db: FirebaseFirestore
    protected lateinit var container: LinearLayout
    private lateinit var tvCoins: TextView

    protected abstract val pageTitle: String
    protected open val needsFollows: Boolean = false
    protected abstract fun render(user: DocumentSnapshot, following: Int, followers: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_page)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        container = findViewById(R.id.page_container)
        tvCoins = findViewById(R.id.tv_page_coins)
        findViewById<TextView>(R.id.tv_page_title).text = pageTitle
        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    protected fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    protected fun reload() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!needsFollows) {
                    show(doc, 0, 0)
                } else {
                    db.collection("follows").whereEqualTo("followerUid", uid).get()
                        .addOnSuccessListener { s1 ->
                            db.collection("follows").whereEqualTo("followingUid", uid).get()
                                .addOnSuccessListener { s2 -> show(doc, s1.size(), s2.size()) }
                                .addOnFailureListener { show(doc, s1.size(), 0) }
                        }
                        .addOnFailureListener { show(doc, 0, 0) }
                }
            }
            .addOnFailureListener { toast("Gagal memuat data") }
    }

    private fun show(doc: DocumentSnapshot, following: Int, followers: Int) {
        val coins = doc.getLong("coins") ?: 0L
        tvCoins.text = "🪙 $coins"
        container.removeAllViews()
        render(doc, following, followers)
    }
}
