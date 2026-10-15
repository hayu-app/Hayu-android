package com.hayu.app

import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import coil.load

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedCountry: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_edit_profile)
        } catch (e: Exception) {
            Toast.makeText(this, "ERROR LAYOUT: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            val btnBack = findViewById<TextView>(R.id.btn_back)
            val imgAvatar = findViewById<ImageView>(R.id.img_edit_avatar)
            val btnChangePhoto = findViewById<FrameLayout>(R.id.btn_change_photo)
            val etNickname = findViewById<EditText>(R.id.et_edit_nickname)
            val etAge = findViewById<EditText>(R.id.et_edit_age)
            val tvGender = findViewById<TextView>(R.id.tv_edit_gender)
            val btnCountry = findViewById<LinearLayout>(R.id.btn_edit_country)
            val tvCountry = findViewById<TextView>(R.id.tv_edit_country)
            val etBio = findViewById<EditText>(R.id.et_edit_bio)
            val btnSave = findViewById<TextView>(R.id.btn_save_profile)

            val currentUser = auth.currentUser
            if (currentUser == null) {
                finish()
                return
            }

            btnBack.setOnClickListener { finish() }

            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { doc ->
                    try {
                        val nickname = doc.getString("nickname") ?: ""
                        val age = (doc.getLong("age") ?: 0L).toInt()
                        val gender = doc.getString("gender") ?: ""
                        val country = doc.getString("country") ?: ""
                        val bio = doc.getString("bio") ?: ""
                        val photoUrl = doc.getString("photoUrl") ?: ""

                        etNickname.setText(nickname)
                        if (age > 0) etAge.setText(age.toString())

                        val genderIcon = when (gender.lowercase()) {
                            "pria" -> "♂️"
                            "wanita" -> "♀️"
                            else -> "👤"
                        }
                        val genderLabel = when (gender.lowercase()) {
                            "pria" -> "Pria"
                            "wanita" -> "Wanita"
                            else -> "-"
                        }
                        tvGender.text = "$genderIcon $genderLabel"

                        selectedCountry = country
                        if (country.isNotEmpty()) {
                            tvCountry.text = country
                            tvCountry.setTextColor(getColor(R.color.hayu_text))
                        }

                        etBio.setText(bio)

                        if (photoUrl.isNotEmpty()) {
                            imgAvatar.load(photoUrl) { crossfade(true) }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "ERROR LOAD: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal memuat data profil", Toast.LENGTH_SHORT).show()
                }

            btnChangePhoto.setOnClickListener {
                Toast.makeText(this, "Upload foto segera hadir! 📷", Toast.LENGTH_SHORT).show()
            }

            btnCountry.setOnClickListener {
                try {
                    val countries = resources.getStringArray(R.array.countries)
                    AlertDialog.Builder(this)
                        .setTitle("Pilih Negara")
                        .setItems(countries) { _, which ->
                            selectedCountry = countries[which]
                            tvCountry.text = selectedCountry
                            tvCountry.setTextColor(getColor(R.color.hayu_text))
                        }
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(this, "ERROR NEGARA: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            btnSave.setOnClickListener {
                try {
                    val nickname = etNickname.text.toString().trim()
                    val ageStr = etAge.text.toString().trim()
                    val bio = etBio.text.toString().trim()

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

                    btnSave.isEnabled = false
                    btnSave.text = "Menyimpan..."

                    val updates = mapOf(
                        "nickname" to nickname,
                        "age" to age,
                        "country" to selectedCountry,
                        "bio" to bio
                    )

                    db.collection("users").document(currentUser.uid)
                        .update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profil berhasil disimpan! ✅", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnSave.isEnabled = true
                            btnSave.text = "Simpan Perubahan"
                            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } catch (e: Exception) {
                    Toast.makeText(this, "ERROR SAVE: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "ERROR INIT: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
