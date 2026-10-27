package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hayu.app.util.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var floatingIcon: LinearLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session = SessionManager(this)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        floatingIcon = findViewById(R.id.floating_room_icon)
        bottomNav = findViewById(R.id.bottom_nav)

        auth.currentUser?.uid?.let { myUid ->
            db.collection("users").document(myUid).get().addOnSuccessListener { d ->
                val until = d.getLong("bannedUntil") ?: 0L
                if (d.getBoolean("banned") == true && (until == 0L || until > System.currentTimeMillis())) {
                    auth.signOut()
                    android.widget.Toast.makeText(this, "Akun kamu diblokir 🚫", android.widget.Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                } else if (savedInstanceState == null && d.getString("lastDailyClaimDate") != DailyCoinActivity.todayWib()) {
                    startActivity(Intent(this, DailyCoinActivity::class.java))
                }
            }
        }

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_chat -> ChatFragment()
                R.id.nav_discover -> DiscoverFragment()
                R.id.nav_notif -> NotificationFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)

            // Kalau buka tab notifikasi, hilangkan badge
            if (item.itemId == R.id.nav_notif) {
                hideNotifBadge()
            }
            true
        }

        // Ada room yang di-minimize: tombol Back menyembunyikan aplikasi (bukan membuka room di bawahnya)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!session.getActiveRoomId().isNullOrEmpty()) {
                    moveTaskToBack(true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        floatingIcon.setOnClickListener {
            val roomId = session.getActiveRoomId()
            if (!roomId.isNullOrEmpty()) {
                val intent = Intent(this, RoomActivity::class.java)
                intent.putExtra("roomId", roomId)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)  // kembali ke room yang masih hidup
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateFloatingIcon()
        updateNotifBadge()
        ReferralManager.checkActivation()
    }

    private fun updateFloatingIcon() {
        val roomId = session.getActiveRoomId()
        val roomName = session.getActiveRoomName()

        if (!roomId.isNullOrEmpty()) {
            floatingIcon.visibility = View.VISIBLE
            findViewById<TextView>(R.id.floating_room_name).text = roomName
        } else {
            floatingIcon.visibility = View.GONE
        }
    }

    // Cek notifikasi belum dibaca → tampilkan badge
    private fun updateNotifBadge() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .whereEqualTo("read", false)
            .limit(99)
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                if (count > 0) {
                    showNotifBadge(count)
                } else {
                    hideNotifBadge()
                }
            }
    }

    private fun showNotifBadge(count: Int) {
        val badge = bottomNav.getOrCreateBadge(R.id.nav_notif)
        badge.isVisible = true
        badge.number = count
        badge.backgroundColor = 0xFFEF4444.toInt() // merah
    }

    private fun hideNotifBadge() {
        bottomNav.removeBadge(R.id.nav_notif)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
