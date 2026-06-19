package com.example.questkampus

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.questkampus.databinding.ActivityPartyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class PartyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPartyBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var partyListener: ListenerRegistration? = null
    private val PARTY_MAX_HP = 500
    private val PREFS = "qk_prefs"
    private val KEY_PARTY_PIN = "current_party_pin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityPartyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth      = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        binding.pbPartyHp.max = PARTY_MAX_HP
        setupButtons()

        // FIX: Restore party yang tersimpan
        val savedPin = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PARTY_PIN, null)
        if (savedPin != null) listenToPartyHP(savedPin)
    }

    private fun setupButtons() {
        binding.btnCreateParty.setOnClickListener {
            val name = binding.etPartyName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "⚠ Masukkan nama party!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            createParty(name)
        }
        binding.btnJoinParty.setOnClickListener {
            val pin = binding.etPinCode.text.toString().trim()
            when {
                pin.isEmpty()    -> Toast.makeText(this, "⚠ Masukkan PIN!", Toast.LENGTH_SHORT).show()
                pin.length != 6  -> Toast.makeText(this, "⚠ PIN harus 6 digit!", Toast.LENGTH_SHORT).show()
                else             -> joinParty(pin)
            }
        }
    }

    private fun createParty(partyName: String) {
        val uid = auth.currentUser?.uid ?: return
        binding.btnCreateParty.isEnabled = false
        val pin = (100000..999999).random().toString()
        firestore.collection("Parties").document(pin)
            .set(hashMapOf("party_name" to partyName, "party_hp" to PARTY_MAX_HP, "members" to arrayListOf(uid), "pin_code" to pin))
            .addOnSuccessListener {
                binding.btnCreateParty.isEnabled = true
                Toast.makeText(this, "⚔ Party '$partyName' dibuat!\n📌 PIN: $pin", Toast.LENGTH_LONG).show()
                savePin(pin)
                listenToPartyHP(pin)
            }
            .addOnFailureListener { e ->
                binding.btnCreateParty.isEnabled = true
                Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun joinParty(pin: String) {
        val uid = auth.currentUser?.uid ?: return
        binding.btnJoinParty.isEnabled = false
        val ref = firestore.collection("Parties").document(pin)
        ref.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                ref.update("members", FieldValue.arrayUnion(uid)).addOnSuccessListener {
                    binding.btnJoinParty.isEnabled = true
                    Toast.makeText(this, "✅ Bergabung ke '${doc.getString("party_name")}'!", Toast.LENGTH_SHORT).show()
                    savePin(pin)
                    listenToPartyHP(pin)
                }.addOnFailureListener { e ->
                    binding.btnJoinParty.isEnabled = true
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.btnJoinParty.isEnabled = true
                Toast.makeText(this, "❌ Party PIN '$pin' tidak ditemukan!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            binding.btnJoinParty.isEnabled = true
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun listenToPartyHP(pin: String) {
        binding.cvPartyStatus.visibility = View.VISIBLE
        partyListener?.remove()
        partyListener = firestore.collection("Parties").document(pin)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w("PartyActivity", "Listen failed", e); return@addSnapshotListener }
                if (snap != null && snap.exists()) {
                    val hp    = snap.getLong("party_hp")?.toInt() ?: 0
                    val name  = snap.getString("party_name") ?: "Party"
                    val count = (snap.get("members") as? List<*>)?.size ?: 0
                    binding.tvActivePartyName.text = "⚔ $name"
                    binding.tvPartyPin.text        = "PIN: $pin"
                    binding.tvPartyPin.visibility  = View.VISIBLE
                    binding.tvMemberCount.text     = "👥 $count Anggota"
                    binding.pbPartyHp.progress     = hp
                    binding.tvPartyHpValue.text    = "$hp / $PARTY_MAX_HP"
                    val hpPct = hp.toFloat() / PARTY_MAX_HP
                    binding.pbPartyHp.progressTintList = ColorStateList.valueOf(when {
                        hpPct <= 0.25f -> Color.parseColor("#FF2200")
                        hpPct <= 0.50f -> Color.parseColor("#FF8800")
                        else           -> Color.parseColor("#FF4444")
                    })
                    if (hp <= 0) Toast.makeText(this, "💀 HP Party habis!", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun savePin(pin: String) =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PARTY_PIN, pin).apply()

    override fun onDestroy() { super.onDestroy(); partyListener?.remove() }
}
