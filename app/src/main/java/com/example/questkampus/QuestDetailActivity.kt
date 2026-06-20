package com.example.questkampus

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.questkampus.databinding.ActivityQuestDetailBinding
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
    private var selectedDeadline: Long = 0L
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()

        questId = intent.getStringExtra("QUEST_ID")

        setupToolbar()
        setupImagePicker()
        setupButtons()
        loadQuestDetails()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadProofImage(it) }
        }
    }

    private fun setupButtons() {
        binding.btnSaveQuest.setOnClickListener { saveQuestChanges() }
        binding.btnCompleteQuest.setOnClickListener { showSubmissionDialog() }
        binding.btnEditDeadline.setOnClickListener { showDeadlinePicker() }
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
        binding.etDetailTitle.setText(quest.title)
        binding.etDetailDesc.setText(quest.desc)
        binding.tvDetailRank.text = "RANK ${quest.rank}"
        selectedDeadline = quest.deadline

        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        binding.tvDetailDeadline.text = "📅 Deadline: ${if (quest.deadline > 0) sdf.format(Date(quest.deadline)) else "-"}"
        binding.tvDetailReward.text = "💰 Reward: ${quest.exp_reward} EXP, ${quest.getGoldReward()} Gold"

        // Handle Status & UI Visibility
        when {
            quest.is_completed -> {
                binding.tvDetailStatus.text = "SELESAI"
                binding.tvDetailStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                binding.layoutActions.visibility = View.GONE
                binding.tvStatusFooter.visibility = View.VISIBLE
                binding.tvStatusFooter.text = "QUEST TELAH SELESAI"
                binding.tvStatusFooter.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                disableEditing()
            }
            quest.is_failed -> {
                binding.tvDetailStatus.text = "GAGAL"
                binding.tvDetailStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                binding.layoutActions.visibility = View.GONE
                binding.tvStatusFooter.visibility = View.VISIBLE
                binding.tvStatusFooter.text = "QUEST GAGAL"
                binding.tvStatusFooter.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                disableEditing()
            }
            else -> {
                binding.tvDetailStatus.text = "AKTIF"
                binding.tvDetailStatus.setTextColor(resources.getColor(R.color.accent_gold))
                binding.layoutActions.visibility = View.VISIBLE
                binding.tvStatusFooter.visibility = View.GONE
                enableEditing()
            }
        }

        // Support Links (File Soal)
        if (quest.support_link.isNotEmpty() || quest.support_file_url.isNotEmpty()) {
            binding.btnViewSupport.visibility = View.VISIBLE
            binding.btnViewSupport.setOnClickListener {
                val url = if (quest.support_link.isNotEmpty()) quest.support_link else quest.support_file_url
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } else {
            binding.btnViewSupport.visibility = View.GONE
        }

        // Proofs (Bukti Penyelesaian)
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

    private fun disableEditing() {
        binding.etDetailTitle.isEnabled = false
        binding.etDetailDesc.isEnabled = false
        binding.btnEditDeadline.visibility = View.GONE
    }

    private fun enableEditing() {
        binding.etDetailTitle.isEnabled = true
        binding.etDetailDesc.isEnabled = true
        binding.btnEditDeadline.visibility = View.VISIBLE
    }

    private fun showDeadlinePicker() {
        val cal = Calendar.getInstance()
        if (selectedDeadline > 0) cal.timeInMillis = selectedDeadline
        
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, 0)
                selectedDeadline = cal.timeInMillis
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                binding.tvDetailDeadline.text = "📅 Deadline: ${sdf.format(Date(selectedDeadline))}"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveQuestChanges() {
        val qId = questId ?: return
        val newTitle = binding.etDetailTitle.text.toString().trim()
        val newDesc = binding.etDetailDesc.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "Judul tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingIndicator.visibility = View.VISIBLE
        val updates = mapOf(
            "title" to newTitle,
            "desc" to newDesc,
            "deadline" to selectedDeadline
        )

        firestore.collection("Quests").document(qId).update(updates)
            .addOnSuccessListener {
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(this, "Quest diperbarui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(this, "Gagal memperbarui: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSubmissionDialog() {
        val options = arrayOf("Upload Foto Bukti", "Input Link Bukti (Drive/Lainnya)")
        AlertDialog.Builder(this)
            .setTitle("Serahkan Quest")
            .setItems(options) { _, which ->
                if (which == 0) imagePickerLauncher.launch("image/*")
                else showLinkInputDialog()
            }
            .show()
    }

    private fun showLinkInputDialog() {
        val input = android.widget.EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Link Bukti")
            .setView(input)
            .setPositiveButton("Kirim") { _, _ ->
                val link = input.text.toString().trim()
                if (link.isNotEmpty()) completeQuest(null, link, "link")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun uploadProofImage(uri: Uri) {
        binding.loadingIndicator.visibility = View.VISIBLE
        val ref = storage.reference.child("quest_proofs/${UUID.randomUUID()}.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                completeQuest(url.toString(), "", "image")
            }
        }.addOnFailureListener {
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Upload Gagal", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeQuest(attachUrl: String?, proofLink: String, proofType: String) {
        val qId = questId ?: return
        val uid = auth.currentUser?.uid ?: return
        val quest = currentQuest ?: return

        binding.loadingIndicator.visibility = View.VISIBLE

        val questUpdates = mapOf(
            "is_completed" to true,
            "attachment_url" to (attachUrl ?: ""),
            "proof_link" to proofLink,
            "proof_type" to proofType
        )

        firestore.runTransaction { transaction ->
            val userRef = firestore.collection("Users").document(uid)
            val userSnap = transaction.get(userRef)

            val curExp = userSnap.getLong("exp") ?: 0L
            val curGold = userSnap.getLong("gold") ?: 0L
            val curLevel = userSnap.getLong("level")?.toInt() ?: 1
            val curHp = userSnap.getLong("hp") ?: 100L
            val maxHp = userSnap.getLong("maxHp") ?: 100L

            val newExp = curExp + quest.exp_reward
            val newGold = curGold + quest.getGoldReward()
            val healAmount = quest.getHealAmount()
            val newHp = minOf(maxHp, curHp + healAmount)

            // Level Up logic
            val maxExp = RpgTheme.maxExpForLevel(curLevel)
            if (newExp >= maxExp) {
                transaction.update(userRef, "level", curLevel + 1)
                transaction.update(userRef, "exp", newExp - maxExp)
                transaction.update(userRef, "maxExp", RpgTheme.maxExpForLevel(curLevel + 1))
            } else {
                transaction.update(userRef, "exp", newExp)
            }

            transaction.update(userRef, "gold", newGold)
            transaction.update(userRef, "hp", newHp)
            transaction.update(firestore.collection("Quests").document(qId), questUpdates)

            null
        }.addOnSuccessListener {
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "🎉 Quest Selesai! +${quest.getHealAmount()} HP", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener { e ->
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
