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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.questkampus.data.model.Quest
import com.example.questkampus.databinding.ActivityQuestDetailBinding
import com.example.questkampus.utils.RpgTheme
import com.example.questkampus.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class QuestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestDetailBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth

    private var questId: String? = null
    private var currentQuest: Quest? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()
        questId = intent.getStringExtra("QUEST_ID")

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupImagePicker()
        setupButtons()
        loadQuestDetails()
    }

    private fun setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadProofImage(it) }
        }
    }

    private fun setupButtons() {
        binding.btnCompleteQuest.setOnClickListener { showSubmissionDialog() }
        binding.btnTakeQuest.setOnClickListener { takePartyQuest() }
        binding.fabEditQuest.setOnClickListener { showEditQuestDialog() }
    }

    private fun loadQuestDetails() {
        val qId = questId ?: return
        firestore.collection("Quests").document(qId).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val quest = snapshot.toObject(Quest::class.java) ?: return@addSnapshotListener
            currentQuest = quest
            displayQuest(quest)
        }
    }

    private fun displayQuest(quest: Quest) {
        val currentUserUid = auth.currentUser?.uid ?: ""

        val now = System.currentTimeMillis()
        val isOverdue = quest.deadline in 1 until now

        binding.tvDetailTitle.text = quest.title
        binding.tvDetailDesc.text = quest.desc
        binding.tvDetailRank.text = "RANK ${quest.rank}"

        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        binding.tvDetailDeadline.text = "📅 Deadline: ${if (quest.deadline > 0) sdf.format(Date(quest.deadline)) else "-"}"
        binding.tvDetailReward.text = "💎 Reward: ${quest.exp_reward} EXP, ${quest.getGoldReward()} Gold"

        when {
            quest.is_completed -> {
                binding.tvDetailStatus.text = "SELESAI"
                binding.tvDetailStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.layoutActions.visibility = View.GONE
                binding.tvStatusFooter.visibility = View.VISIBLE
                binding.tvStatusFooter.text = "QUEST TELAH SELESAI"
                binding.tvStatusFooter.setTextColor(Color.parseColor("#4CAF50"))
                binding.fabEditQuest.visibility = View.GONE
            }
            quest.is_failed || isOverdue -> {
                binding.tvDetailStatus.text = "GAGAL"
                binding.tvDetailStatus.setTextColor(Color.parseColor("#F44336"))
                binding.layoutActions.visibility = View.GONE
                binding.tvStatusFooter.visibility = View.VISIBLE
                binding.tvStatusFooter.text = "WAKTU HABIS! MISI GAGAL"
                binding.tvStatusFooter.setTextColor(Color.parseColor("#F44336"))
                binding.fabEditQuest.visibility = View.GONE

                if (isOverdue && !quest.is_failed && !quest.penalty_applied) {
                    applyFailurePenalty(questId!!)
                }
            }
            else -> {
                binding.tvDetailStatus.text = "AKTIF"
                binding.tvDetailStatus.setTextColor(Color.parseColor("#B71C1C"))
                binding.layoutActions.visibility = View.VISIBLE
                binding.tvStatusFooter.visibility = View.GONE

                if (quest.party_id.isNotEmpty()) {
                    binding.fabEditQuest.visibility = View.GONE
                    if (quest.assigned_to.isEmpty()) {
                        binding.btnTakeQuest.visibility = View.VISIBLE
                        binding.btnCompleteQuest.visibility = View.GONE
                        binding.tvAssignedTo.visibility = View.GONE
                    } else if (quest.assigned_to == currentUserUid) {
                        binding.btnTakeQuest.visibility = View.GONE
                        binding.btnCompleteQuest.visibility = View.VISIBLE
                        binding.tvAssignedTo.text = "Misi ini sedang kamu kerjakan"
                        binding.tvAssignedTo.visibility = View.VISIBLE
                    } else {
                        binding.btnTakeQuest.visibility = View.GONE
                        binding.btnCompleteQuest.visibility = View.GONE
                        binding.tvAssignedTo.text = "Sedang dikerjakan oleh anggota lain"
                        binding.tvAssignedTo.visibility = View.VISIBLE
                    }
                } else {
                    binding.fabEditQuest.visibility = View.VISIBLE
                    binding.btnTakeQuest.visibility = View.GONE
                    binding.btnCompleteQuest.visibility = View.VISIBLE
                    binding.tvAssignedTo.visibility = View.GONE
                }
            }
        }

        if (quest.support_link.isNotEmpty() || quest.support_file_url.isNotEmpty()) {
            binding.btnViewSupport.visibility = View.VISIBLE
            binding.btnViewSupport.setOnClickListener {
                val url = if (quest.support_link.isNotEmpty()) quest.support_link else quest.support_file_url
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } else {
            binding.btnViewSupport.visibility = View.GONE
        }

        val proofUrl = quest.proof_link.ifEmpty { quest.attachment_url }
        if (proofUrl.isNotEmpty()) {
            binding.labelProof.visibility = View.VISIBLE
            binding.cvProofContainer.visibility = View.VISIBLE
            if (quest.proof_type == "image" || proofUrl.contains(".jpg") || proofUrl.contains(".png")) {
                binding.ivProofPreview.visibility = View.VISIBLE
                binding.btnViewProofLink.visibility = View.GONE
                Glide.with(this).load(proofUrl).into(binding.ivProofPreview)
            } else {
                binding.ivProofPreview.visibility = View.GONE
                binding.btnViewProofLink.visibility = View.VISIBLE
                binding.btnViewProofLink.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proofUrl)))
                }
            }
        } else {
            binding.labelProof.visibility = View.GONE
            binding.cvProofContainer.visibility = View.GONE
        }
    }

    // =======================================================
    // LOGIKA PENALTI GAGAL TUGAS BERDASARKAN ROLE PALADIN
    // =======================================================
    private fun applyFailurePenalty(qId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.runTransaction { transaction ->
            val userRef = firestore.collection("Users").document(uid)
            val questRef = firestore.collection("Quests").document(qId)

            val userSnap = transaction.get(userRef)
            val role = userSnap.getString("role") ?: "Adventurer"
            val curHp = userSnap.getLong("hp") ?: 100L

            // Base Penalty HP = 20. Jika Paladin, diskon jadi 10!
            var penalty = 20L
            if (role.contains("Paladin")) penalty = 10L

            transaction.update(userRef, "hp", maxOf(0, curHp - penalty))
            transaction.update(questRef, "is_failed", true)
            transaction.update(questRef, "penalty_applied", true)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Waktu Habis! Kamu terkena penalti HP.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditQuestDialog() {
        val quest = currentQuest ?: return
        var editDeadline = quest.deadline

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_quest, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_quest_title)
        val etDesc = dialogView.findViewById<TextInputEditText>(R.id.et_quest_desc)
        val tvDeadline = dialogView.findViewById<TextView>(R.id.tv_deadline_display)
        val btnDate = dialogView.findViewById<Button>(R.id.btn_pick_date)

        dialogView.findViewById<View>(R.id.btn_attach_file)?.visibility = View.GONE
        dialogView.findViewById<View>(R.id.et_support_link)?.visibility = View.GONE

        etTitle.setText(quest.title)
        etDesc.setText(quest.desc)

        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        tvDeadline.text = "⏳ ${if (editDeadline > 0) sdf.format(Date(editDeadline)) else "Belum dipilih"}"

        val cal = Calendar.getInstance()
        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                    editDeadline = cal.timeInMillis
                    tvDeadline.text = "⏳ ${sdf.format(Date(editDeadline))}"
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Jurnal Quest")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newDesc = etDesc.text.toString().trim()

                if (editDeadline < System.currentTimeMillis()) {
                    Toast.makeText(this, "Tidak bisa mundur ke masa lalu!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newTitle.isNotEmpty()) {
                    val newRank = RpgTheme.calculateAutoRank(newTitle, editDeadline)
                    val updates = mapOf(
                        "title" to newTitle,
                        "desc" to newDesc,
                        "deadline" to editDeadline,
                        "rank" to newRank
                    )
                    firestore.collection("Quests").document(questId!!).update(updates)
                        .addOnSuccessListener { Toast.makeText(this, "Jurnal diperbarui!", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun takePartyQuest() {
        val qId = questId ?: return
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("Quests").document(qId)
            .update("assigned_to", uid)
            .addOnSuccessListener { Toast.makeText(this, "Misi berhasil diambil!", Toast.LENGTH_SHORT).show() }
    }

    private fun showSubmissionDialog() {
        val options = arrayOf("📸 Upload Foto Bukti", "🔗 Input Link Bukti")
        AlertDialog.Builder(this)
            .setTitle("Serahkan Quest")
            .setItems(options) { _, which ->
                if (which == 0) imagePickerLauncher.launch("image/*")
                else showLinkInputDialog()
            }.show()
    }

    private fun showLinkInputDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Link Bukti")
            .setView(input)
            .setPositiveButton("Kirim") { _, _ ->
                val link = input.text.toString().trim()
                if (link.isNotEmpty()) completeQuest(null, link, "link")
            }.setNegativeButton("Batal", null).show()
    }

    private fun uploadProofImage(uri: Uri) {
        val ref = storage.reference.child("quest_proofs/${UUID.randomUUID()}.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                completeQuest(url.toString(), "", "image")
            }
        }.addOnFailureListener { Toast.makeText(this, "Upload Gagal", Toast.LENGTH_SHORT).show() }
    }

    // =======================================================
    // LOGIKA PERHITUNGAN REWARD (TERMASUK SKILL DARI ROLE)
    // =======================================================
    private fun completeQuest(attachUrl: String?, proofLink: String, proofType: String) {
        val qId = questId ?: return
        val uid = auth.currentUser?.uid ?: return
        val quest = currentQuest ?: return

        val questUpdates = mapOf(
            "is_completed" to true,
            "attachment_url" to (attachUrl ?: ""),
            "proof_link" to proofLink,
            "proof_type" to proofType
        )

        firestore.runTransaction { transaction ->
            val userRef = firestore.collection("Users").document(uid)
            val userSnap = transaction.get(userRef)

            // AMBIL ROLE DARI DATABASE
            val role = userSnap.getString("role") ?: "Adventurer"

            // BASE MULTIPLIER (Pengganda Dasar)
            var dmgMult = 1
            var expMult = 1.0
            var goldMult = 1.0
            var healMult = 1

            // DETEKSI SKILL CLASS
            when {
                role.contains("Swordmaster") -> dmgMult = 2    // Damage x2
                role.contains("Mage") -> expMult = 1.2         // EXP +20%
                role.contains("Saint") -> healMult = 2         // Heal x2
                role.contains("Archer") -> goldMult = 1.5      // Gold +50%
            }

            // HITUNG DAMAGE KE BOS KELOMPOK
            var partyHpToUpdate: Int? = null
            val partyRef = if (quest.party_id.isNotEmpty()) firestore.collection("Parties").document(quest.party_id) else null

            if (partyRef != null) {
                val partySnap = transaction.get(partyRef)
                if (partySnap.exists()) {
                    val curPartyHp = partySnap.getLong("party_hp") ?: 500L
                    val damageToBoss = quest.getHealAmount() * 2 * dmgMult // Apply DMG Buff
                    partyHpToUpdate = maxOf(0, (curPartyHp - damageToBoss).toInt())
                }
            }

            val curExp = userSnap.getLong("exp") ?: 0L
            val curGold = userSnap.getLong("gold") ?: 0L
            val curLevel = userSnap.getLong("level")?.toInt() ?: 1
            val curHp = userSnap.getLong("hp") ?: 100L
            val maxHp = userSnap.getLong("maxHp") ?: 100L

            // KALKULASI REWARD AKHIR DENGAN BUFF CLASS
            val finalExp = (quest.exp_reward * expMult).toLong()
            val finalGold = (quest.getGoldReward() * goldMult).toLong()
            val finalHeal = quest.getHealAmount() * healMult

            val newExp = curExp + finalExp
            val newGold = curGold + finalGold
            val newHp = minOf(maxHp, curHp + finalHeal)

            // SISTEM NAIK LEVEL
            val maxExpThreshold = RpgTheme.maxExpForLevel(curLevel)
            if (newExp >= maxExpThreshold) {
                transaction.update(userRef, "level", curLevel + 1)
                transaction.update(userRef, "exp", newExp - maxExpThreshold)
                transaction.update(userRef, "maxExp", RpgTheme.maxExpForLevel(curLevel + 1))
            } else {
                transaction.update(userRef, "exp", newExp)
            }
            transaction.update(userRef, "gold", newGold)
            transaction.update(userRef, "hp", newHp)

            transaction.update(firestore.collection("Quests").document(qId), questUpdates)

            if (partyRef != null && partyHpToUpdate != null) {
                transaction.update(partyRef, "party_hp", partyHpToUpdate)
            }

            // Return nilai yang didapat agar bisa dimunculkan di Toast
            Pair(finalHeal, role)
        }.addOnSuccessListener { result ->
            val healGot = result?.first ?: 0
            val roleName = result?.second ?: "Adventurer"

            var notif = "Quest Selesai! +$healGot HP"
            if (roleName.contains("Swordmaster")) notif += "\n⚔️ Swordmaster: Double Damage!"
            if (roleName.contains("Mage")) notif += "\n🧙‍♂️ Mage: Bonus EXP +20%!"
            if (roleName.contains("Archer")) notif += "\n🏹 Archer: Bonus Gold +50%!"
            if (roleName.contains("Saint")) notif += "\n👼 Saint: Double Heal!"

            Toast.makeText(this, notif, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}