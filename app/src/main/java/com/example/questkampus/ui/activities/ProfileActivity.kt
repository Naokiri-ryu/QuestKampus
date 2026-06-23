package com.example.questkampus.ui.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.questkampus.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // Daftar Class (Role)
    private val roles = arrayOf("Adventurer (Pemula)", "Swordmaster", "Paladin", "Mage", "Saint", "Archer")
    private val roleDescriptions = arrayOf(
        "Tidak ada keuntungan khusus. Berjuanglah murni dengan kekuatanmu!",
        "🗡️ Swordmaster: Damage (Serangan) ke Bos Kelompok dikali 2.",
        "🛡️ Paladin: Penalti HP akibat gagal tugas berkurang 50%.",
        "🧙‍♂️ Mage: Mendapatkan bonus +20% EXP setiap menyelesaikan misi.",
        "👼 Saint: Pemulihan HP (Heal) setiap selesai tugas dikali 2.",
        "🏹 Archer: Mendapatkan bonus +50% Gold setiap menyelesaikan misi."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupUI()
        loadProfileData()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup Dropdown (Spinner) untuk Role
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, roles) {
            // Agar teks di dalam spinner putih
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as android.widget.TextView).setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = adapter

        // Ubah teks deskripsi setiap kali role dipilih
        binding.spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tvRoleDesc.text = roleDescriptions[position]
                // Pastikan teks yang dipilih tetap putih
                (parent?.getChildAt(0) as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Launcher Ganti Foto
        val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadAvatar(it) }
        }
        binding.ivProfileAvatar.setOnClickListener { imagePicker.launch("image/*") }

        // Tombol Simpan
        binding.btnSaveProfile.setOnClickListener { saveProfileData() }
    }

    private fun loadProfileData() {
        val uid = auth.currentUser?.uid ?: return
        binding.pbProfileLoading.visibility = View.VISIBLE

        firestore.collection("Users").document(uid).get().addOnSuccessListener { snap ->
            binding.pbProfileLoading.visibility = View.GONE
            if (snap.exists()) {
                val name = snap.getString("name") ?: ""
                val level = snap.getLong("level") ?: 1
                val gold = snap.getLong("gold") ?: 0
                val hp = snap.getLong("hp") ?: 100
                val maxHp = snap.getLong("maxHp") ?: 100
                val role = snap.getString("role") ?: "Adventurer (Pemula)"
                val avatarUrl = snap.getString("avatar_url") ?: ""

                binding.etProfileName.setText(name)
                binding.tvProfileStats.text = "⭐ Lv. $level | 🪙 $gold Gold\nHP: $hp/$maxHp"

                if (avatarUrl.isNotEmpty()) {
                    Glide.with(this).load(avatarUrl).circleCrop().into(binding.ivProfileAvatar)
                }

                // Set pilihan spinner sesuai database
                val roleIndex = roles.indexOf(role)
                if (roleIndex >= 0) {
                    binding.spinnerRole.setSelection(roleIndex)
                }
            }
        }.addOnFailureListener { binding.pbProfileLoading.visibility = View.GONE }
    }

    private fun saveProfileData() {
        val uid = auth.currentUser?.uid ?: return
        val newName = binding.etProfileName.text.toString().trim()
        val newRole = binding.spinnerRole.selectedItem.toString()

        if (newName.isEmpty()) {
            Toast.makeText(this, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.pbProfileLoading.visibility = View.VISIBLE
        val updates = mapOf(
            "name" to newName,
            "role" to newRole
        )

        firestore.collection("Users").document(uid).update(updates)
            .addOnSuccessListener {
                binding.pbProfileLoading.visibility = View.GONE
                Toast.makeText(this, "Profil berhasil disimpan!", Toast.LENGTH_SHORT).show()
                finish() // Kembali ke menu utama
            }
            .addOnFailureListener {
                binding.pbProfileLoading.visibility = View.GONE
                Toast.makeText(this, "Gagal menyimpan profil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadAvatar(imageUri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        binding.pbProfileLoading.visibility = View.VISIBLE
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putFile(imageUri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                firestore.collection("Users").document(uid).update("avatar_url", url.toString())
                    .addOnSuccessListener {
                        binding.pbProfileLoading.visibility = View.GONE
                        Glide.with(this).load(url.toString()).circleCrop().into(binding.ivProfileAvatar)
                        Toast.makeText(this, "Foto berhasil diubah!", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}