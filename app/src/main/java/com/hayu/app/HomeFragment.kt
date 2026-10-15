package com.hayu.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCreateRoom = view.findViewById<TextView>(R.id.btn_create_room)
        btnCreateRoom.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur Buat Room segera hadir! 🎈", Toast.LENGTH_SHORT).show()
        }
    }
}
