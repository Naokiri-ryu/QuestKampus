package com.example.questkampus

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

    // Max HP for a party (used in progress bar and display)
    private val PARTY_MAX_HP = 500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth      = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Set max on progress bar right away
        binding.pbPartyHp.max = PARTY_MAX_HP

        setupButtons()
    }

    // =========================================================
    //  Button Setup
    // =========================================================

    private fun setupButtons() {
        binding.btnCreateParty.setOnClickListener {
            val partyName = binding.etPartyName.text.toString().trim()
            if (partyName.isEmpty()) {
                Toast.makeText(this, "⚠ Masukkan nama party terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createParty(partyName)
        }

        binding.btnJoinParty.setOnClickListener {
            val pinCode = binding.etPinCode.text.toString().trim()
            when {
                pinCode.isEmpty() ->
                    Toast.makeText(this, "⚠ Masukkan kode PIN party!", Toast.LENGTH_SHORT).show()
                pinCode.length != 6 ->
                    Toast.makeText(this, "⚠ PIN harus 6 digit!", Toast.LENGTH_SHORT).show()
                else -> joinParty(pinCode)
            }
        }
    }

    // =========================================================
    //  Create Party
    // =========================================================

    private fun createParty(partyName: String) {
        val currentUser = auth.currentUser ?: return
        binding.btnCreateParty.isEnabled = false

        val pinCode = (100000..999999).random().toString()

        val partyData = hashMapOf(
            "party_name" to partyName,
            "party_hp"   to PARTY_MAX_HP,
            "members"    to arrayListOf(currentUser.uid),
            "pin_code"   to pinCode
        )

        firestore.collection("Parties").document(pinCode)
            .set(partyData)
            .addOnSuccessListener {
                binding.btnCreateParty.isEnabled = true
                Toast.makeText(
                    this,
                    "⚔ Party '$partyName' berhasil dibuat!\n📌 Kode PIN: $pinCode",
                    Toast.LENGTH_LONG
                ).show()
                // Show pin code in UI
                binding.tvActivePartyName.text = "⚔ $partyName"
                binding.tvPartyPin.text        = "PIN: $pinCode"
                binding.tvPartyPin.visibility  = View.VISIBLE
                listenToPartyHP(pinCode)
            }
            .addOnFailureListener { e ->
                binding.btnCreateParty.isEnabled = true
                Log.e("PartyActivity", "Error creating party", e)
                Toast.makeText(this, "Gagal membuat party: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================================================
    //  Join Party
    // =========================================================

    private fun joinParty(pinCode: String) {
        val currentUser = auth.currentUser ?: return
        binding.btnJoinParty.isEnabled = false

        val partyRef = firestore.collection("Parties").document(pinCode)

        partyRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    partyRef.update("members", FieldValue.arrayUnion(currentUser.uid))
                        .addOnSuccessListener {
                            binding.btnJoinParty.isEnabled = true
                            val partyName = document.getString("party_name") ?: "Party"
                            Toast.makeText(
                                this,
                                "✅ Berhasil bergabung ke party '$partyName'!",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.tvActivePartyName.text = "⚔ $partyName"
                            binding.tvPartyPin.text        = "PIN: $pinCode"
                            binding.tvPartyPin.visibility  = View.VISIBLE
                            listenToPartyHP(pinCode)
                        }
                        .addOnFailureListener { e ->
                            binding.btnJoinParty.isEnabled = true
                            Log.e("PartyActivity", "Error joining party", e)
                            Toast.makeText(this, "Gagal bergabung: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    binding.btnJoinParty.isEnabled = true
                    Toast.makeText(
                        this,
                        "❌ Party dengan PIN '$pinCode' tidak ditemukan!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                binding.btnJoinParty.isEnabled = true
                Log.e("PartyActivity", "Error fetching party", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================================================
    //  Real-time Party HP Listener
    // =========================================================

    private fun listenToPartyHP(pinCode: String) {
        // Show the party status card
        binding.cvPartyStatus.visibility = View.VISIBLE

        partyListener?.remove() // Remove any previous listener

        partyListener = firestore.collection("Parties").document(pinCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("PartyActivity", "Party listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val hp        = snapshot.getLong("party_hp")?.toInt()  ?: 0
                    val partyName = snapshot.getString("party_name") ?: "Party"
                    val members   = snapshot.get("members") as? List<*>
                    val memberCount = members?.size ?: 0

                    // Update UI
                    binding.tvActivePartyName.text = "⚔ $partyName"
                    binding.pbPartyHp.max          = PARTY_MAX_HP
                    binding.pbPartyHp.progress     = hp
                    binding.tvPartyHpValue.text    = "$hp / $PARTY_MAX_HP"
                    binding.tvMemberCount.text     = "👥 $memberCount Anggota"

                    // HP bar color changes with HP level
                    val hpPercent = hp.toFloat() / PARTY_MAX_HP.toFloat()
                    val hpColor   = when {
                        hpPercent <= 0.25f -> Color.parseColor("#FF2200")
                        hpPercent <= 0.50f -> Color.parseColor("#FF8800")
                        else               -> Color.parseColor("#FF4444")
                    }
                    binding.pbPartyHp.progressTintList = ColorStateList.valueOf(hpColor)

                    if (hp <= 0) {
                        Toast.makeText(
                            this,
                            "💀 HP Party habis! Ayo selesaikan tugas lebih cepat!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }

    // =========================================================
    //  Lifecycle
    // =========================================================

    override fun onDestroy() {
        super.onDestroy()
        partyListener?.remove()
    }
}
