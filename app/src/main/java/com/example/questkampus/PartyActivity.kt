package com.example.questkampus

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnCreateParty.setOnClickListener {
            val partyName = binding.etPartyName.text.toString().trim()
            if (partyName.isNotEmpty()) {
                createParty(partyName)
            } else {
                Toast.makeText(this, "Please enter a party name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnJoinParty.setOnClickListener {
            val pinCode = binding.etPinCode.text.toString().trim()
            if (pinCode.length == 6) {
                joinParty(pinCode)
            } else {
                Toast.makeText(this, "Please enter a 6-digit PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Membuat party baru dengan PIN 6 digit acak
     */
    private fun createParty(partyName: String) {
        val currentUser = auth.currentUser ?: return
        val pinCode = (100000..999999).random().toString()
        
        val partyData = hashMapOf(
            "party_name" to partyName,
            "party_hp" to 500,
            "members" to arrayListOf(currentUser.uid)
        )

        firestore.collection("Parties").document(pinCode)
            .set(partyData)
            .addOnSuccessListener {
                Toast.makeText(this, "Party created! PIN: $pinCode", Toast.LENGTH_LONG).show()
                listenToPartyHP(pinCode)
                binding.cvPartyStatus.visibility = View.VISIBLE
                binding.tvActivePartyName.text = "Party: $partyName (PIN: $pinCode)"
            }
            .addOnFailureListener { e ->
                Log.e("PartyActivity", "Error creating party", e)
                Toast.makeText(this, "Failed to create party", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Bergabung ke party menggunakan PIN
     */
    private fun joinParty(pinCode: String) {
        val currentUser = auth.currentUser ?: return
        val partyRef = firestore.collection("Parties").document(pinCode)

        partyRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                partyRef.update("members", FieldValue.arrayUnion(currentUser.uid))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Joined party successfully!", Toast.LENGTH_SHORT).show()
                        listenToPartyHP(pinCode)
                        binding.cvPartyStatus.visibility = View.VISIBLE
                        binding.tvActivePartyName.text = "Party: ${document.getString("party_name")} (PIN: $pinCode)"
                    }
                    .addOnFailureListener { e ->
                        Log.e("PartyActivity", "Error joining party", e)
                    }
            } else {
                Toast.makeText(this, "Party not found! Check the PIN.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Log.e("PartyActivity", "Error fetching party", e)
        }
    }

    /**
     * Memantau HP Party secara real-time
     */
    private fun listenToPartyHP(pinCode: String) {
        partyListener?.remove() // Hapus listener sebelumnya jika ada

        partyListener = firestore.collection("Parties").document(pinCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("PartyActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val hp = snapshot.getLong("party_hp")?.toInt() ?: 0
                    val maxHp = 500 // Sesuai default awal

                    binding.pbPartyHp.max = maxHp
                    binding.pbPartyHp.progress = hp
                    binding.tvPartyHpValue.text = "$hp / $maxHp"
                    
                    if (hp <= 0) {
                        Toast.makeText(this, "PARTY DEFEATED!", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        partyListener?.remove()
    }
}
