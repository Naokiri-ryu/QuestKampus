package com.example.questkampus

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.questkampus.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth      = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnRegister.setOnClickListener { registerUser() }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name     = binding.etName.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        when {
            name.isEmpty() || email.isEmpty() || password.isEmpty() ->
                Toast.makeText(this, "⚠ Harap isi semua field!", Toast.LENGTH_SHORT).show()
            password.length < 6 ->
                Toast.makeText(this, "⚠ Password minimal 6 karakter!", Toast.LENGTH_SHORT).show()
            else -> {
                binding.loading.visibility  = View.VISIBLE
                binding.btnRegister.isEnabled = false

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val userId = result.user?.uid ?: return@addOnSuccessListener
                        saveUserInfo(userId, name, email)
                    }
                    .addOnFailureListener { e ->
                        binding.loading.visibility    = View.GONE
                        binding.btnRegister.isEnabled = true
                        Toast.makeText(this, "Gagal daftar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun saveUserInfo(userId: String, name: String, email: String) {
        // Initial player stats — level 1, full HP, 0 EXP, 0 Gold
        val initialStats = hashMapOf(
            "name"       to name,
            "email"      to email,
            "level"      to 1,
            "hp"         to 100,
            "maxHp"      to 100,
            "exp"        to 0,
            "maxExp"     to 100,   // Level 1 requires 100 EXP to advance
            "gold"       to 0,
            "avatar_url" to ""
        )

        firestore.collection("Users").document(userId)
            .set(initialStats)
            .addOnSuccessListener {
                binding.loading.visibility = View.GONE
                Toast.makeText(this, "🎉 Selamat datang, Hero $name!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                binding.loading.visibility    = View.GONE
                binding.btnRegister.isEnabled = true
                Toast.makeText(this, "Gagal simpan data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
