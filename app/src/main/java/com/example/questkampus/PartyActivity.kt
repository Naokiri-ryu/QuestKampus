package com.example.questkampus

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.questkampus.databinding.ActivityPartyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.content.Intent
class PartyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPartyBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var partyAdapter: PartyAdapter
    private var partiesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Tombol kembali di pojok kiri atas
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupButtons()
        loadMyParties()
    }

    private fun setupRecyclerView() {
        partyAdapter = PartyAdapter(emptyList()) { selectedParty ->
            val intent = Intent(this, PartyDetailActivity::class.java)
            intent.putExtra("PARTY_ID", selectedParty.id)
            startActivity(intent)
        }
        binding.rvParties.layoutManager = LinearLayoutManager(this)
        binding.rvParties.adapter = partyAdapter
    }

    private fun setupButtons() {
        binding.fabCreateParty.setOnClickListener { showCreatePartyDialog() }
        binding.fabJoinParty.setOnClickListener { showJoinPartyDialog() }
    }

    private fun loadMyParties() {
        val uid = auth.currentUser?.uid ?: return

        partiesListener?.remove()

        // Menarik SEMUA party di mana user ini adalah anggotanya
        partiesListener = firestore.collection("Parties")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val parties = snapshot?.toObjects(Party::class.java) ?: emptyList()
                partyAdapter.updateData(parties)

                if (parties.isEmpty()) {
                    binding.tvEmptyParty.visibility = View.VISIBLE
                    binding.rvParties.visibility = View.GONE
                } else {
                    binding.tvEmptyParty.visibility = View.GONE
                    binding.rvParties.visibility = View.VISIBLE
                }
            }
    }

    // ===============================================
    // DIALOG & FUNGSI BUAT KELOMPOK
    // ===============================================
    private fun showCreatePartyDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 40, 60, 10)

        val etName = EditText(this).apply { hint = "Nama Kelompok / Matkul" }
        val etDesc = EditText(this).apply { hint = "Deskripsi Singkat" }

        layout.addView(etName)
        layout.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Buat Kelompok Baru")
            .setView(layout)
            .setPositiveButton("Buat") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isNotEmpty()) createParty(name, desc)
                else Toast.makeText(this, "Nama kelompok wajib diisi!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createParty(name: String, desc: String) {
        val uid = auth.currentUser?.uid ?: return
        val pin = (100000..999999).random().toString()

        val newParty = Party(
            party_name = name,
            description = desc,
            party_hp = 500,
            max_hp = 500,
            pin_code = pin,
            creator_id = uid,
            members = listOf(uid) // User yang membuat langsung jadi anggota
        )

        firestore.collection("Parties").document(pin).set(newParty)
            .addOnSuccessListener {
                Toast.makeText(this, "Kelompok '$name' dibuat!\nPIN: $pin", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal membuat kelompok", Toast.LENGTH_SHORT).show()
            }
    }

    // ===============================================
    // DIALOG & FUNGSI GABUNG KELOMPOK
    // ===============================================
    private fun showJoinPartyDialog() {
        val etPin = EditText(this).apply {
            hint = "Masukkan 6 Digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val layout = LinearLayout(this).apply {
            setPadding(60, 40, 60, 10)
            addView(etPin)
        }

        AlertDialog.Builder(this)
            .setTitle("Gabung Kelompok")
            .setView(layout)
            .setPositiveButton("Gabung") { _, _ ->
                val pin = etPin.text.toString().trim()
                if (pin.length == 6) joinParty(pin)
                else Toast.makeText(this, "PIN harus 6 digit!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun joinParty(pin: String) {
        val uid = auth.currentUser?.uid ?: return
        val partyRef = firestore.collection("Parties").document(pin)

        partyRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                // Tambahkan UID kita ke dalam array 'members' milik kelompok tersebut
                partyRef.update("members", FieldValue.arrayUnion(uid)).addOnSuccessListener {
                    Toast.makeText(this, "Berhasil bergabung ke kelompok!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Kelompok dengan PIN $pin tidak ditemukan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        partiesListener?.remove() // Cegah memory leak
    }
}