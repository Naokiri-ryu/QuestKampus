package com.example.questkampus.ui.activities

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.example.questkampus.R
import com.example.questkampus.data.model.Quest
import com.example.questkampus.ui.adapters.QuestAdapter
import com.example.questkampus.databinding.ActivityMainBinding
import com.example.questkampus.utils.RpgTheme
import com.example.questkampus.worker.DeadlineWorker
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var questAdapter: QuestAdapter
    private lateinit var supportFileLauncher: ActivityResultLauncher<String>

    private var pendingSupportFileUri: Uri? = null

    // Variabel List & Listener
    private var allQuests = listOf<Quest>()
    private var personalQuests = listOf<Quest>()
    private var partyQuests = listOf<Quest>()
    private var personalQuestsListener: ListenerRegistration? = null
    private var partyQuestsListener: ListenerRegistration? = null

    private var currentPlayerGold = 0L
    private var currentPlayerHp = 0
    private var currentPlayerMaxHp = 100
    private var selectedDeadline = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        supportFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                pendingSupportFileUri = it
                Toast.makeText(this, "File siap dilampirkan!", Toast.LENGTH_SHORT).show()
            }
        }

        setupUI()
        setupClickListeners()
        setupBackgroundWorker()
        setupRandomTip()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        loadUserQuests()
    }

    private fun setupUI() {
        questAdapter = QuestAdapter(emptyList()) { quest ->
            val intent = Intent(this, QuestDetailActivity::class.java)
            intent.putExtra("QUEST_ID", quest.id)
            startActivity(intent)
        }
        binding.rvQuests.layoutManager = LinearLayoutManager(this)
        binding.rvQuests.adapter = questAdapter

        binding.bottomNav.setOnItemSelectedListener { item ->
            val title = item.title.toString().lowercase()
            when {
                title.contains("dashboard") || title.contains("home") -> {
                    binding.panelDashboard.visibility = View.VISIBLE
                    binding.panelQuests.visibility = View.GONE
                    binding.fabAddQuest.visibility = View.GONE
                    true
                }
                title.contains("quest") || title.contains("misi") -> {
                    binding.panelDashboard.visibility = View.GONE
                    binding.panelQuests.visibility = View.VISIBLE
                    binding.fabAddQuest.visibility = View.VISIBLE
                    true
                }
                title.contains("party") || title.contains("kelompok") -> {
                    startActivity(Intent(this, PartyActivity::class.java))
                    false
                }
                title.contains("toko") || title.contains("shop") -> {
                    showShopDialog()
                    false
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAddQuest.setOnClickListener { showAddQuestDialog() }
        binding.btnParty.setOnClickListener { startActivity(Intent(this, PartyActivity::class.java)) }
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            filterQuests(checkedIds[0])
        }
        binding.btnOpenShop.setOnClickListener { showShopDialog() }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("Users").document(uid).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val name = snap.getString("name") ?: "Adventurer"
                    val level = snap.getLong("level")?.toInt() ?: 1
                    val exp = snap.getLong("exp")?.toInt() ?: 0
                    currentPlayerHp = snap.getLong("hp")?.toInt() ?: 100
                    currentPlayerMaxHp = snap.getLong("maxHp")?.toInt() ?: 100
                    currentPlayerGold = snap.getLong("gold") ?: 0L
                    val avatarUrl = snap.getString("avatar_url") ?: ""

                    // BACA ROLE
                    val role = snap.getString("role") ?: "Adventurer (Pemula)"

                    // Update UI Atas
                    binding.tvPlayerName.text = name
                    binding.tvPlayerLevel.text = "Lv.$level | ${RpgTheme.getUserRankString(level)}"
                    binding.tvPlayerGold.text = "🪙 $currentPlayerGold"
                    binding.pbHp.max = currentPlayerMaxHp
                    binding.pbHp.progress = currentPlayerHp
                    binding.pbExp.max = RpgTheme.maxExpForLevel(level)
                    binding.pbExp.progress = exp

                    // UPDATE KARTU DASHBOARD ROLE
                    binding.tvDashboardRole.text = "Class: $role"
                    binding.tvDashboardBuff.text = when {
                        role.contains("Swordmaster") -> "⚔️ Buff: Damage ke Bos Kelompok x2"
                        role.contains("Mage") -> "🧙‍♂️ Buff: +20% EXP dari setiap Misi"
                        role.contains("Saint") -> "👼 Buff: +100% Pemulihan HP (Heal x2)"
                        role.contains("Archer") -> "🏹 Buff: +50% Gold dari setiap Misi"
                        role.contains("Paladin") -> "🛡️ Buff: Diskon Penalti HP 50% saat Gagal"
                        else -> "🪵 Buff: Tidak ada keuntungan khusus."
                    }

                    if (avatarUrl.isNotEmpty() && !isDestroyed) {
                        Glide.with(this).load(avatarUrl).circleCrop().into(binding.ivAvatar)
                    }

                    if (currentPlayerHp <= 0) showBurnoutDialog()
                }
            }
    }

    private fun loadUserQuests() {
        val uid = auth.currentUser?.uid ?: return
        binding.loadingIndicator.visibility = View.VISIBLE

        personalQuestsListener = firestore.collection("Quests")
            .whereEqualTo("creator_id", uid)
            .whereEqualTo("party_id", "")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                personalQuests = snap.toObjects(Quest::class.java)
                combineAndUpdateQuests()
            }

        partyQuestsListener = firestore.collection("Quests")
            .whereEqualTo("assigned_to", uid)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                partyQuests = snap.toObjects(Quest::class.java)
                combineAndUpdateQuests()
            }
    }

    private fun combineAndUpdateQuests() {
        binding.loadingIndicator.visibility = View.GONE

        // Gabungkan list misi, hapus duplikat, dan urutkan berdasarkan deadline terdekat
        allQuests = (personalQuests + partyQuests).distinctBy { it.id }.sortedBy { it.deadline }

        // Update Statistik Papan Dashboard RPG
        val activeCount = allQuests.count { !it.is_completed && !it.is_failed }
        val doneCount = allQuests.count { it.is_completed }
        val failedCount = allQuests.count { it.is_failed }

        binding.tvCountActive.text = activeCount.toString()
        binding.tvCountDone.text = doneCount.toString()
        binding.tvCountFailed.text = failedCount.toString()

        // UPDATE MISI MENDESAK DI DASHBOARD
        updateUrgentQuests()

        // Terapkan filter yang sedang aktif untuk tab Quest Board
        val checkedId = if (binding.chipGroupFilter.checkedChipIds.isNotEmpty()) {
            binding.chipGroupFilter.checkedChipIds[0]
        } else R.id.chip_all
        filterQuests(checkedId)
    }

    private fun filterQuests(checkedId: Int) {
        val filtered = when (checkedId) {
            R.id.chip_active -> allQuests.filter { !it.is_completed && !it.is_failed }
            R.id.chip_done -> allQuests.filter { it.is_completed }
            R.id.chip_failed -> allQuests.filter { it.is_failed }
            else -> allQuests
        }
        questAdapter.updateData(filtered)
    }

    private fun updateUrgentQuests() {
        binding.layoutUrgentQuests.removeAllViews()

        // Ambil misi yang belum selesai/gagal
        val activeQuests = allQuests.filter { !it.is_completed && !it.is_failed }

        if (activeQuests.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Belum ada tugas yang menanti. Kamu bisa bersantai!"
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(16, 16, 16, 16)
            }
            binding.layoutUrgentQuests.addView(emptyText)
            return
        }

        // Ambil maksimal 3 misi teratas (yang deadline-nya paling dekat)
        val urgentQuests = activeQuests.take(3)
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        for (quest in urgentQuests) {
            // Gunakan layout kartu misi RPG milikmu sendiri!
            val questView = layoutInflater.inflate(R.layout.item_quest, binding.layoutUrgentQuests, false)

            val tvTitle = questView.findViewById<TextView>(R.id.tv_quest_title)
            val tvRank = questView.findViewById<TextView>(R.id.tv_quest_rank)
            val tvFlavor = questView.findViewById<TextView>(R.id.tv_quest_flavor)
            val tvDeadline = questView.findViewById<TextView>(R.id.tv_quest_deadline)

            tvTitle.text = quest.title
            tvRank.text = quest.rank

            // Atur warna Badge Rank
            val bgDrawable = when(quest.rank.uppercase()) {
                "S" -> R.drawable.bg_rank_s
                "A" -> R.drawable.bg_rank_a
                "B" -> R.drawable.bg_rank_b
                else -> R.drawable.bg_rank_c
            }
            tvRank.background = getDrawable(bgDrawable)
            tvRank.setTextColor(if (quest.rank == "C") Color.WHITE else Color.BLACK)

            tvFlavor.text = "🔥 Misi Mendesak"
            tvFlavor.setTextColor(Color.parseColor("#FF4444"))

            tvDeadline.text = "⏳ Deadline: ${if (quest.deadline > 0) sdf.format(Date(quest.deadline)) else "-"}"

            // Jika diklik, buka detail jurnal
            questView.setOnClickListener {
                val intent = Intent(this, QuestDetailActivity::class.java)
                intent.putExtra("QUEST_ID", quest.id)
                startActivity(intent)
            }

            binding.layoutUrgentQuests.addView(questView)
        }
    }

    private fun showShopDialog() {
        val options = arrayOf("🧪 Potion Kecil (+20 HP) - 30 Gold", "🧪 Potion Besar (+50 HP) - 60 Gold")
        AlertDialog.Builder(this)
            .setTitle("Toko Rumah Sakit 🏥")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> buyPotion(20, 30)
                    1 -> buyPotion(50, 60)
                }
            }
            .setNegativeButton("Tutup", null).show()
    }

    private fun buyPotion(healAmount: Int, cost: Int) {
        if (currentPlayerGold < cost) {
            Toast.makeText(this, "Gold tidak cukup!", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("Users").document(uid)
        firestore.runTransaction { transaction ->
            val snap = transaction.get(userRef)
            val currentGold = snap.getLong("gold") ?: 0L
            val currentHp = snap.getLong("hp") ?: 100L
            val maxHp = snap.getLong("maxHp") ?: 100L
            if (currentGold >= cost) {
                transaction.update(userRef, "gold", currentGold - cost)
                transaction.update(userRef, "hp", minOf(maxHp, currentHp + healAmount))
            }
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Berhasil membeli Potion!", Toast.LENGTH_SHORT).show()
            loadUserData()
        }
    }

    private fun showBurnoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔥 BURNOUT!")
            .setMessage("HP kamu habis karena banyak tugas yang terlewat!\n\nKamu harus dirawat. Biaya perawatan memotong 50% Gold.")
            .setCancelable(false)
            .setPositiveButton("Terima Perawatan") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val userRef = firestore.collection("Users").document(uid)
                firestore.runTransaction { tx ->
                    val snap = tx.get(userRef)
                    val currentGold = snap.getLong("gold") ?: 0L
                    val maxHp = snap.getLong("maxHp") ?: 100L
                    tx.update(userRef, "gold", currentGold / 2)
                    tx.update(userRef, "hp", maxHp)
                    null
                }.addOnSuccessListener { loadUserData() }
            }.show()
    }

    private fun showAddQuestDialog() {
        selectedDeadline = 0L
        pendingSupportFileUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_quest, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_quest_title)
        val etDesc = dialogView.findViewById<TextInputEditText>(R.id.et_quest_desc)
        val etSupLink = dialogView.findViewById<TextInputEditText>(R.id.et_support_link)
        val tvDeadline = dialogView.findViewById<TextView>(R.id.tv_deadline_display)
        val btnDate = dialogView.findViewById<Button>(R.id.btn_pick_date)
        val btnAttach = dialogView.findViewById<Button>(R.id.btn_attach_file)

        btnAttach?.setOnClickListener {
            supportFileLauncher.launch("*/*")
            Toast.makeText(this, "File dilampirkan", Toast.LENGTH_SHORT).show()
        }

        val cal = Calendar.getInstance()
        btnDate?.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                    selectedDeadline = cal.timeInMillis
                    tvDeadline?.text = "⏳ ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(selectedDeadline))}"
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Buat Misi") { _, _ ->
                val title = etTitle?.text.toString().trim()
                val desc = etDesc?.text.toString().trim()
                val supLink = etSupLink?.text?.toString()?.trim() ?: ""

                if (title.isNotEmpty() && selectedDeadline > 0L) {

                    // ANTI CURANG PRIBADI: CEK APAKAH WAKTU SUDAH LEWAT
                    if (selectedDeadline < System.currentTimeMillis()) {
                        Toast.makeText(this, "Deadline tidak boleh di masa lalu!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val autoRank = RpgTheme.calculateAutoRank(title, selectedDeadline)
                    if (pendingSupportFileUri != null) {
                        uploadSupportFileAndSave(title, desc, autoRank, selectedDeadline, supLink)
                    } else {
                        // MENGGUNAKAN FUNGSI YANG BENAR UNTUK MAIN ACTIVITY
                        saveQuestToFirestore(title, desc, autoRank, selectedDeadline, supLink, "")
                    }
                } else {
                    Toast.makeText(this, "Judul dan Deadline wajib diisi!", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun uploadSupportFileAndSave(title: String, desc: String, rank: String, deadline: Long, supLink: String) {
        val uri = pendingSupportFileUri ?: return
        binding.loadingIndicator.visibility = View.VISIBLE
        val ref = storage.reference.child("quest_support/${UUID.randomUUID()}")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                saveQuestToFirestore(title, desc, rank, deadline, supLink, url.toString())
            }
        }.addOnFailureListener {
            binding.loadingIndicator.visibility = View.GONE
            saveQuestToFirestore(title, desc, rank, deadline, supLink, "")
        }
    }

    private fun saveQuestToFirestore(title: String, desc: String, rank: String, deadline: Long, supLink: String, supFileUrl: String) {
        val uid = auth.currentUser?.uid ?: return
        val (expReward, _) = rankToRewards(rank)

        val newQuest = mapOf(
            "title" to title, "desc" to desc, "rank" to rank,
            "exp_reward" to expReward, "deadline" to deadline,
            "is_completed" to false, "is_failed" to false,
            "creator_id" to uid, "party_id" to "",
            "support_link" to supLink, "support_file_url" to supFileUrl,
            "proof_type" to "image", "proof_link" to "", "attachment_url" to "",
            "penalty_applied" to false
        )

        firestore.collection("Quests").add(newQuest).addOnSuccessListener {
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Misi Rank $rank berhasil dibuat!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rankToRewards(rank: String): Pair<Int, Int> = when(rank) {
        "S" -> Pair(500, 100); "A" -> Pair(300, 60); "B" -> Pair(150, 30); else -> Pair(50, 10)
    }

    private fun setupBackgroundWorker() {
        val request = PeriodicWorkRequestBuilder<DeadlineWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("DeadlineWorkerCheck", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun onDestroy() {
        super.onDestroy()
        personalQuestsListener?.remove()
        partyQuestsListener?.remove()
    }

    private fun setupRandomTip() {
        val tips = listOf(
            "⚔ Tip: Selesaikan quest Rank S untuk EXP dan Gold terbanyak!",
            "🛡️ Tip: Class Paladin mengurangi penalti HP saat quest gagal sebesar 50%.",
            "🧙‍♂️ Tip: Mage mendapatkan bonus EXP +20% dari setiap misi yang selesai!",
            "👼 Tip: Saint memulihkan HP 2x lipat lebih banyak saat menyelesaikan tugas.",
            "🏹 Tip: Archer mendapatkan ekstra Gold +50% untuk jajan Potion.",
            "🏥 Tip: Jangan lupa kunjungi Toko (Rumah Sakit) jika HP-mu menipis!",
            "🤝 Tip: Gabung ke Kelompok untuk mengalahkan Monster Bos bersama teman!",
            "⏳ Tip: Hati-hati! Quest yang kelewatan deadline akan melukai HP-mu.",
            "📜 Tip: Judul tugas dengan kata 'UAS' atau 'Skripsi' otomatis menjadi Rank S!"
        )
        binding.tvTip.text = tips.random()
    }
}