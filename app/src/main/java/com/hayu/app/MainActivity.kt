package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hayu.app.util.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var floatingIcon: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session = SessionManager(this)
        floatingIcon = findViewById(R.id.floating_room_icon)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
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
            true
        }

        // Tap floating icon → balik ke room
        floatingIcon.setOnClickListener {
            val roomId = session.getActiveRoomId()
            if (!roomId.isNullOrEmpty()) {
                val intent = Intent(this, RoomActivity::class.java)
                intent.putExtra("roomId", roomId)
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateFloatingIcon()
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

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
