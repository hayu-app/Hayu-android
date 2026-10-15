package com.hayu.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OnboardingActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedGender: String = "pria"
    private var selectedCountry: String = ""

    private val randomNames = listOf(
        "Bunga", "Langit", "Bintang", "Senja", "Angin",
        "Mentari", "Embun", "Pelangi", "Awan", "Samudra",
        "Petir", "Kilat", "Fajar", "Gelombang", "Cahaya"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnGenderMale = findViewById<TextView>(R.id.btn_gender_male)
        val btnGenderFemale = findViewById<TextView>(R.id.btn_gender_female)
        val etNickname = findViewById<EditText>(R.id.et_nickname)
        val etAge = findViewById<EditText>(R.id.et_age)
        val btnRandomName = findViewById<TextView>(R.id.btn_random_name)
        val btnCountry = findViewById<LinearLayout>(R.id.btn_country)
        val tvCountry = findViewById<TextView>(R.id.tv_country)
        val btnChangeAvatar = findViewById<TextView>(R.id.btn_change_avatar)
        val btnFinish = findViewById<TextView>(R.id.btn_finish_onboarding)

        // Set nama random pertama kali
        etNickname.setText(randomNames.random() + (1..999).random())

        // Tombol ganti nama random
        btnRandomName.setOnClickListener {
            etNickname.setText(randomNames.random() + (1..999).random())
        }

        // Pilih gender
        btnGenderMale.setOnClickListener {
            selectedGender = "pria"
            btnGenderMale.setBackgroundResource(R.drawable.bg_button_primary)
            btnGenderMale.setTextColor(0xFFFFFFFF.toInt())
            btnGenderFemale.setBackgroundResource(R.drawable.bg_input)
            btnGenderFemale.setTextColor(getColor(R.color.hayu_text_secondary))
        }

        btnGenderFemale.setOnClickListener {
            selectedGender = "wanita"
            btnGenderFemale.setBackgroundResource(R.drawable.bg_button_primary)
            btnGenderFemale.setTextColor(0xFFFFFFFF.toInt())
            btnGenderMale.setBackgroundResource(R.drawable.bg_input)
            btnGenderMale.setTextColor(getColor(R.color.hayu_text_secondary))
        }

        // Pilih negara via dialog
        btnCountry.setOnClickListener {
            val countries = resources.getStringArray(R.array.countries)
            AlertDialog.Builder(this)
                .setTitle("Pilih Negara")
                .setItems(countries) { _, which ->
                    selectedCountry = countries[which]
                    tvCountry.text = selectedCountry
                    tvCountry.setTextColor(getColor(R.color.hayu_text))
                }
                .show()
        }

        // Ganti foto (placeholder dulu)
        btnChangeAvatar.setOnClickListener {
            Toast.makeText(this, "Upload foto segera hadir! 📷", Toast.LENGTH_SHORT).show()
        }

        // Tombol selesai
        btnFinish.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            val ageStr = etAge.text.toString().trim()

            if (nickname.isEmpty()) {
                Toast.makeText(this, "Nama panggilan wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ageStr.isEmpty()) {
                Toast.makeText(this, "Umur wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val age = ageStr.toIntOrNull() ?: 0
            if (age < 10 || age > 100) {
                Toast.makeText(this, "Umur tidak valid (10-100)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedCountry.isEmpty()) {
                Toast.makeText(this, "Pilih negara dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProfile(nickname, age)
        }
    }

    private fun saveProfile(nickname: String, age: Int) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Sesi habis, login ulang", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
            return
        }

        val updates = mapOf(
            "nickname" to nickname,
            "age" to age,
            "gender" to selectedGender,
            "country" to selectedCountry,
            "onboarded" to true
        )

        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profil siap! Selamat datang 🎉", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
