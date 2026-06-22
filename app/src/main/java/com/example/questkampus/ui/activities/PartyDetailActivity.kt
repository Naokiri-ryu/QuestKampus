package com.example.questkampus.ui.activities

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.questkampus.ui.adapters.MemberAdapter
import com.example.questkampus.ui.adapters.QuestAdapter
import com.example.questkampus.ui.activities.QuestDetailActivity
import com.example.questkampus.R
import com.example.questkampus.data.model.Member
import com.example.questkampus.data.model.Party
import com.example.questkampus.data.model.Quest
import com.example.questkampus.databinding.ActivityPartyDetailBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PartyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPartyDetailBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var memberAdapter: MemberAdapter
    private lateinit var partyQuestAdapter: QuestAdapter

    private var partyListener: ListenerRegistration? = null
    private var questsListener: ListenerRegistration? = null

    private var partyId: String? = null // Ini adalah PIN kelompok
    private var selectedDeadline: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        partyId = intent.getStringExtra("PARTY_ID")
        if (partyId == null) {
            Toast.makeText(this, "Data Kelompok Hilang!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerViews()

        binding.fabAddPartyQuest.setOnClickListener { showAddPartyQuestDialog() }
        binding.btnInviteMember.setOnClickListener { showInviteDialog() }
        binding.btnDisbandParty.setOnClickListener { showDisbandDialog() }

        loadPartyData()
        loadPartyQuests()
    }

    private fun setupRecyclerViews() {
        memberAdapter = MemberAdapter(emptyList())
        binding.rvPartyMembers.layoutManager = LinearLayoutManager(this)
        binding.rvPartyMembers.adapter = memberAdapter

        partyQuestAdapter = QuestAdapter(emptyList()) { quest ->
            val intent = Intent(this, QuestDetailActivity::class.java)
            intent.putExtra("QUEST_ID", quest.id)
            startActivity(intent)
        }
        binding.rvPartyQuests.layoutManager = LinearLayoutManager(this)
        binding.rvPartyQuests.adapter = partyQuestAdapter
    }

    private fun loadPartyData() {
        val pin = partyId ?: return
        partyListener = firestore.collection("Parties").document(pin)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null || !snap.exists()) {
                    // Jika dokumen dihapus (party dibubarkan), otomatis tutup halaman
                    Toast.makeText(this, "Kelompok ini sudah dibubarkan.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addSnapshotListener
                }

                val party = snap.toObject(Party::class.java) ?: return@addSnapshotListener

                binding.tvDetailPartyName.text = "⚔ ${party.party_name}"
                binding.tvDetailPartyPin.text = "PIN: ${party.pin_code}"

                binding.pbDetailPartyHp.max = party.max_hp
                binding.pbDetailPartyHp.progress = party.party_hp
                binding.tvDetailPartyHpValue.text = "${party.party_hp} / ${party.max_hp}"

                val hpPct = party.party_hp.toFloat() / party.max_hp.toFloat()
                binding.pbDetailPartyHp.progressTintList = ColorStateList.valueOf(when {
                    hpPct <= 0.25f -> Color.parseColor("#FF2200")
                    hpPct <= 0.50f -> Color.parseColor("#FF8800")
                    else           -> Color.parseColor("#FF4444")
                })

                if (party.members.isNotEmpty()) {
                    loadMembers(party.members)
                }
            }
    }

    private fun loadMembers(uids: List<String>) {
        val validUids = uids.take(10)
        firestore.collection("Users").whereIn(FieldPath.documentId(), validUids)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                val membersList = snap.toObjects(Member::class.java)
                memberAdapter.updateData(membersList.sortedByDescending { it.level })
            }
    }

    private fun loadPartyQuests() {
        val pin = partyId ?: return
        questsListener = firestore.collection("Quests")
            .whereEqualTo("party_id", pin)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener

                val quests = snap.toObjects(Quest::class.java)
                partyQuestAdapter.updateData(quests.sortedBy { it.deadline })

                // ==========================================
                // LOGIKA: CEK APAKAH SEMUA MISI SUDAH TIDAK AKTIF (SELESAI ATAU GAGAL)
                // ==========================================
                // Jika daftar quest tidak kosong DAN semua quest memenuhi syarat (completed ATAU failed)
                if (quests.isNotEmpty() && quests.all { it.is_completed || it.is_failed }) {
                    binding.btnDisbandParty.visibility = View.VISIBLE
                } else {
                    binding.btnDisbandParty.visibility = View.GONE
                }
            }
    }

    // ==========================================
    // LOGIKA UNDANG TEMAN VIA EMAIL ATAU NAMA
    // ==========================================
    private fun showInviteDialog() {
        val etInput = EditText(this).apply {
            hint = "Masukkan Email atau Nama Hero"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val layout = LinearLayout(this).apply {
            setPadding(60, 40, 60, 10)
            addView(etInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Undang Anggota")
            .setMessage("Kamu bisa mencari teman berdasarkan Email atau Nama Hero mereka.")
            .setView(layout)
            .setPositiveButton("Cari & Undang") { _, _ ->
                val input = etInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    searchAndInviteUser(input)
                } else {
                    Toast.makeText(this, "Input tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun searchAndInviteUser(input: String) {
        val pin = partyId ?: return

        // 1. Coba cari berdasarkan Email terlebih dahulu
        firestore.collection("Users").whereEqualTo("email", input).get()
            .addOnSuccessListener { emailSnapshot ->
                if (!emailSnapshot.isEmpty) {
                    // Ditemukan via Email
                    executeInvite(emailSnapshot.documents[0].id, emailSnapshot.documents[0].getString("name") ?: "Hero", pin)
                } else {
                    // 2. Jika Email tidak ketemu, cari berdasarkan Nama Hero
                    firestore.collection("Users").whereEqualTo("name", input).get()
                        .addOnSuccessListener { nameSnapshot ->
                            if (!nameSnapshot.isEmpty) {
                                // Jika ada nama kembar, beri tahu user
                                if (nameSnapshot.size() > 1) {
                                    Toast.makeText(this, "Ada beberapa Hero dengan nama ini. Mengundang yang pertama...", Toast.LENGTH_LONG).show()
                                }
                                // Ditemukan via Nama
                                executeInvite(nameSnapshot.documents[0].id, nameSnapshot.documents[0].getString("name") ?: "Hero", pin)
                            } else {
                                Toast.makeText(this, "Hero '$input' tidak ditemukan!", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal melakukan pencarian.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun executeInvite(targetUid: String, targetName: String, pin: String) {
        // Masukkan UID tersebut ke dalam array members di Party
        firestore.collection("Parties").document(pin)
            .update("members", FieldValue.arrayUnion(targetUid))
            .addOnSuccessListener {
                Toast.makeText(this, "$targetName berhasil diundang ke kelompok!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengundang anggota.", Toast.LENGTH_SHORT).show()
            }
    }

    // ==========================================
    // LOGIKA BUBARKAN KELOMPOK
    // ==========================================
    private fun showDisbandDialog() {
        AlertDialog.Builder(this)
            .setTitle("Pekerjaan Selesai! 🎉")
            .setMessage("Semua misi kelompok telah diselesaikan dengan baik. Apakah kamu ingin membubarkan/menghapus kelompok ini dari daftar?")
            .setPositiveButton("Ya, Bubarkan") { _, _ ->
                val pin = partyId ?: return@setPositiveButton

                // Menghapus data Party dari Firestore
                firestore.collection("Parties").document(pin).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Kelompok berhasil dibubarkan.", Toast.LENGTH_SHORT).show()
                        // Tidak perlu panggil finish() karena Listener di atas akan mendeteksi dokumen hilang dan menutup halaman otomatis.
                    }
            }
            .setNegativeButton("Belum, Biarkan Saja", null)
            .show()
    }

    // --- FUNGSI BUAT MISI KELOMPOK (TIDAK BERUBAH) ---
    private fun showAddPartyQuestDialog() {
        selectedDeadline = 0L
        val dialogView  = layoutInflater.inflate(R.layout.dialog_add_quest, null)
        val etTitle     = dialogView.findViewById<TextInputEditText>(R.id.et_quest_title)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.et_quest_desc)
        val etSupLink   = dialogView.findViewById<TextInputEditText>(R.id.et_support_link)
        val spinnerRank = dialogView.findViewById<Spinner>(R.id.spinner_rank)
        val tvDeadline  = dialogView.findViewById<TextView>(R.id.tv_deadline_display)
        val btnDate     = dialogView.findViewById<Button>(R.id.btn_pick_date)
        val btnAttach   = dialogView.findViewById<Button>(R.id.btn_attach_file)

        btnAttach.visibility = View.GONE
        dialogView.findViewById<TextView>(R.id.tv_file_status).visibility = View.GONE

        val rankAdapter = ArrayAdapter.createFromResource(this, R.array.rank_options, android.R.layout.simple_spinner_item)
        rankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRank.adapter = rankAdapter

        val cal = Calendar.getInstance()
        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(
                    Calendar.SECOND,
                    0
                )
                    selectedDeadline = cal.timeInMillis
                    tvDeadline.text = "✅ ${
                        SimpleDateFormat(
                            "dd MMM yyyy, HH:mm",
                            Locale.getDefault()
                        ).format(Date(selectedDeadline))
                    }"
                    tvDeadline.setTextColor(Color.parseColor("#FFD700"))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this).setView(dialogView)
            .setPositiveButton("Buat Misi Kelompok") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                val rank = spinnerRank.selectedItem.toString().take(1)
                val link = etSupLink.text.toString().trim()

                if (title.isNotEmpty() && selectedDeadline > 0L) {
                    savePartyQuest(title, desc, rank, selectedDeadline, link)
                } else {
                    Toast.makeText(this, "Judul dan Deadline wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun savePartyQuest(title: String, desc: String, rank: String, deadline: Long, link: String) {
        val uid = auth.currentUser?.uid ?: return
        val expReward = when(rank) { "S" -> 500; "A" -> 300; "B" -> 150; else -> 50 }

        val partyQuestData = mapOf(
            "title" to title,
            "desc" to desc,
            "rank" to rank,
            "exp_reward" to expReward,
            "deadline" to deadline,
            "is_completed" to false,
            "is_failed" to false,
            "creator_id" to uid,
            "party_id" to partyId!!,
            "assigned_to" to "",
            "support_link" to link,
            "proof_type" to "image",
            "penalty_applied" to false
        )

        firestore.collection("Quests").add(partyQuestData).addOnSuccessListener {
            Toast.makeText(this, "Misi ditambahkan ke Papan Kelompok!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        partyListener?.remove()
        questsListener?.remove()
    }
}