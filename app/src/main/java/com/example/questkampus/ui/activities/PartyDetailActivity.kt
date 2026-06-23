package com.example.questkampus.ui.activities

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.questkampus.R
import com.example.questkampus.data.model.Member
import com.example.questkampus.data.model.Party
import com.example.questkampus.data.model.Quest
import com.example.questkampus.ui.adapters.MemberAdapter
import com.example.questkampus.ui.adapters.QuestAdapter
import com.example.questkampus.databinding.ActivityPartyDetailBinding
import com.example.questkampus.utils.RpgTheme
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class PartyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPartyDetailBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    private lateinit var memberAdapter: MemberAdapter
    private lateinit var partyQuestAdapter: QuestAdapter

    private var partyListener: ListenerRegistration? = null
    private var questsListener: ListenerRegistration? = null

    private var partyId: String? = null
    private var selectedDeadline: Long = 0L

    private lateinit var supportFileLauncher: ActivityResultLauncher<String>
    private var pendingSupportFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()

        partyId = intent.getStringExtra("PARTY_ID")
        if (partyId == null) {
            Toast.makeText(this, "Data Kelompok Hilang!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup File Launcher
        supportFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                pendingSupportFileUri = it
                Toast.makeText(this, "File siap dilampirkan!", Toast.LENGTH_SHORT).show()
            }
        }

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

                val hpPct = (party.party_hp.toFloat() / party.max_hp.toFloat()) * 100

                when {
                    hpPct >= 75f -> {
                        binding.ivPartyVisual.alpha = 1.0f
                        binding.ivPartyVisual.clearColorFilter()
                    }
                    hpPct >= 25f -> {
                        binding.ivPartyVisual.alpha = 1.0f
                        binding.ivPartyVisual.setColorFilter(Color.argb(80, 255, 0, 0))
                    }
                    hpPct > 0f -> {
                        binding.ivPartyVisual.alpha = 1.0f
                        binding.ivPartyVisual.setColorFilter(Color.argb(150, 255, 0, 0))
                    }
                    else -> {
                        binding.ivPartyVisual.alpha = 0.4f
                        binding.ivPartyVisual.setColorFilter(Color.GRAY, android.graphics.PorterDuff.Mode.MULTIPLY)
                    }
                }

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

                if (quests.isNotEmpty() && quests.all { it.is_completed || it.is_failed }) {
                    binding.btnDisbandParty.visibility = View.VISIBLE
                } else {
                    binding.btnDisbandParty.visibility = View.GONE
                }
            }
    }

    private fun showInviteDialog() {
        val etInput = EditText(this).apply { hint = "Masukkan Email atau Nama Hero" }
        val layout = LinearLayout(this).apply { setPadding(60, 40, 60, 10); addView(etInput) }

        AlertDialog.Builder(this)
            .setTitle("Undang Anggota")
            .setView(layout)
            .setPositiveButton("Undang") { _, _ ->
                val input = etInput.text.toString().trim()
                if (input.isNotEmpty()) searchAndInviteUser(input)
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun searchAndInviteUser(input: String) {
        val pin = partyId ?: return
        firestore.collection("Users").whereEqualTo("email", input).get()
            .addOnSuccessListener { emailSnap ->
                if (!emailSnap.isEmpty) {
                    executeInvite(emailSnap.documents[0].id, pin)
                } else {
                    firestore.collection("Users").whereEqualTo("name", input).get()
                        .addOnSuccessListener { nameSnap ->
                            if (!nameSnap.isEmpty) executeInvite(nameSnap.documents[0].id, pin)
                            else Toast.makeText(this, "Hero tidak ditemukan!", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun executeInvite(targetUid: String, pin: String) {
        firestore.collection("Parties").document(pin)
            .update("members", FieldValue.arrayUnion(targetUid))
            .addOnSuccessListener { Toast.makeText(this, "Berhasil diundang!", Toast.LENGTH_SHORT).show() }
    }

    private fun showDisbandDialog() {
        AlertDialog.Builder(this)
            .setTitle("Pekerjaan Selesai! 🎉")
            .setMessage("Apakah kamu ingin membubarkan kelompok ini?")
            .setPositiveButton("Ya, Bubarkan") { _, _ ->
                firestore.collection("Parties").document(partyId!!).delete()
                    .addOnSuccessListener { Toast.makeText(this, "Kelompok dibubarkan.", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("Belum", null).show()
    }

    private fun showAddPartyQuestDialog() {
        selectedDeadline = 0L
        pendingSupportFileUri = null
        val dialogView  = layoutInflater.inflate(R.layout.dialog_add_quest, null)
        val etTitle     = dialogView.findViewById<TextInputEditText>(R.id.et_quest_title)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.et_quest_desc)
        val etSupLink   = dialogView.findViewById<TextInputEditText>(R.id.et_support_link)
        val tvDeadline  = dialogView.findViewById<TextView>(R.id.tv_deadline_display)
        val btnDate     = dialogView.findViewById<Button>(R.id.btn_pick_date)
        val btnAttach   = dialogView.findViewById<Button>(R.id.btn_attach_file)

        btnAttach?.visibility = View.VISIBLE
        btnAttach?.setOnClickListener {
            supportFileLauncher.launch("*/*")
        }

        val cal = Calendar.getInstance()
        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                    selectedDeadline = cal.timeInMillis
                    tvDeadline.text = "✅ ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(selectedDeadline))}"
                    tvDeadline.setTextColor(Color.parseColor("#FFD700"))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this).setView(dialogView)
            .setPositiveButton("Buat Serangan") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                val link = etSupLink?.text?.toString()?.trim() ?: ""

                if (title.isNotEmpty() && selectedDeadline > 0L) {
                    if (selectedDeadline < System.currentTimeMillis()) {
                        Toast.makeText(this, "Deadline tidak boleh di masa lalu!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val autoRank = RpgTheme.calculateAutoRank(title, selectedDeadline)

                    if (pendingSupportFileUri != null) {
                        uploadSupportFileAndSavePartyQuest(title, desc, autoRank, selectedDeadline, link)
                    } else {
                        savePartyQuest(title, desc, autoRank, selectedDeadline, link, "")
                    }
                } else {
                    Toast.makeText(this, "Judul dan Deadline wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun uploadSupportFileAndSavePartyQuest(title: String, desc: String, rank: String, deadline: Long, link: String) {
        val uri = pendingSupportFileUri ?: return
        Toast.makeText(this, "Mengunggah file, mohon tunggu...", Toast.LENGTH_SHORT).show()
        val ref = storage.reference.child("quest_support/${UUID.randomUUID()}")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                savePartyQuest(title, desc, rank, deadline, link, url.toString())
            }
        }.addOnFailureListener {
            savePartyQuest(title, desc, rank, deadline, link, "")
        }
    }

    private fun savePartyQuest(title: String, desc: String, rank: String, deadline: Long, link: String, fileUrl: String) {
        val uid = auth.currentUser?.uid ?: return
        val expReward = when(rank) { "S" -> 500; "A" -> 300; "B" -> 150; else -> 50 }

        val partyQuestData = mapOf(
            "title" to title, "desc" to desc, "rank" to rank, "exp_reward" to expReward,
            "deadline" to deadline, "is_completed" to false, "is_failed" to false,
            "creator_id" to uid, "party_id" to partyId!!, "assigned_to" to "",
            "support_link" to link, "support_file_url" to fileUrl,
            "proof_type" to "image", "penalty_applied" to false
        )

        firestore.collection("Quests").add(partyQuestData).addOnSuccessListener {
            Toast.makeText(this, "Misi Serangan berhasil dibuat!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        partyListener?.remove()
        questsListener?.remove()
    }
}