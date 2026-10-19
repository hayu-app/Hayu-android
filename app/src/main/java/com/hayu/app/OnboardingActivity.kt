package com.hayu.app

import com.hayu.app.util.ImgbbUploader
import com.hayu.app.util.ImageUtil
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.ImageView
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
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
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val img = findViewById<ImageView>(R.id.img_onboarding_avatar)
            img.setPadding(0, 0, 0, 0)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.clipToOutline = true
            img.setImageURI(uri)
        }
    }

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

        etNickname.setText(randomNames.random() + (1..999).random())

        btnRandomName.setOnClickListener {
            etNickname.setText(randomNames.random() + (1..999).random())
        }

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

        btnChangeAvatar.setOnClickListener { pickImage.launch("image/*") }
        findViewById<ImageView>(R.id.img_onboarding_avatar).setOnClickListener { pickImage.launch("image/*") }

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

            uploadThenSave(nickname, age, btnFinish)
        }
    }

    private fun uploadThenSave(nickname: String, age: Int, btn: TextView) {
        val uri = selectedImageUri
        if (uri == null) {
            saveProfile(nickname, age)
            return
        }
        val file = ImageUtil.uriToFile(this, uri)
        if (file == null) {
            Toast.makeText(this, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
            return
        }
        val oldText = btn.text
        btn.isEnabled = false
        btn.text = "Upload foto..."
        ImgbbUploader.uploadImage(
            imageFile = file,
            onSuccess = { url ->
                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = oldText
                    saveProfile(nickname, age, url)
                }
            },
            onError = { err ->
                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = oldText
                    Toast.makeText(this, "Gagal upload: $err", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun saveProfile(nickname: String, age: Int, photoUrl: String? = null) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Sesi habis, login ulang", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "nickname" to nickname,
            "age" to age,
            "gender" to selectedGender,
            "country" to selectedCountry,
            "onboarded" to true
        )
        if (photoUrl != null) updates["photoUrl"] = photoUrl

        // Pakai set() dengan merge — kalau dokumen belum ada, dibuat baru
        db.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge())
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
