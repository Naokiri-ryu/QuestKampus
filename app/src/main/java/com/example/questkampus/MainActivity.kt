package com.example.questkampus

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.questkampus.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private var statsListener: ListenerRegistration? = null
    private var questsListener: ListenerRegistration? = null

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private var pendingQuestId: String? = null
    private var pendingQuestExp: Int    = 0

    // Quest data — full list from Firestore, filtered list for adapter
    private var allQuests      = listOf<Quest>()
    private var currentFilter  = "all" // "all" | "active" | "done" | "failed"

    private val TIPS = listOf(
        "⚔ Tip: Selesaikan quest Rank S untuk EXP dan Gold terbanyak!",
        "💡 Tip: Quest yang melewati deadline akan memotong HP kamu secara otomatis.",
        "🛡 Tip: Bergabunglah ke Party untuk berbagi HP dan mengerjakan quest bersama.",
        "⏰ Tip: Notifikasi akan muncul 3 jam sebelum deadline quest.",
        "🎯 Tip: Upload foto bukti saat menyelesaikan quest untuk verifikasi.",
        "🪙 Tip: Kumpulkan Gold dari quest untuk milestone berikutnya.",
    )

    // =========================================================
    //  Lifecycle
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        storage   = FirebaseStorage.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        // Create notification channels
        NotificationHelper.createChannels(this)

        // Ask for notification permission on Android 13+
        requestNotificationPermission()

        // Schedule background deadline worker
        scheduleDeadlineWorker()

        setupImagePicker()
        setupUI()
        setupBottomNav()
        setupFilterChips()
        setupRecyclerView()
        setupSwipeToDelete()
        fetchPlayerStats()
        loadQuests()

        // Show random tip
        binding.tvTip.text = TIPS.random()
    }

    override fun onDestroy() {
        super.onDestroy()
        statsListener?.remove()
        questsListener?.remove()
    }

    // =========================================================
    //  Notification Permission (Android 13+)
    // =========================================================

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101
            )
        }
    }

    // =========================================================
    //  WorkManager — background deadline check every 15 min
    // =========================================================

    private fun scheduleDeadlineWorker() {
        val workRequest = PeriodicWorkRequestBuilder<DeadlineWorker>(15, TimeUnit.MINUTES)
            .addTag(DeadlineWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DeadlineWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // =========================================================
    //  Setup
    // =========================================================

    private fun setupImagePicker() {
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    if (pendingQuestId == null) {
                        uploadAvatar(it)
                    } else {
                        uploadProofAndCompleteQuest(pendingQuestId!!, pendingQuestExp, it)
                    }
                } ?: Toast.makeText(this, "Pemilihan gambar dibatalkan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupUI() {
        binding.fabAddQuest.setOnClickListener { showAddQuestDialog() }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            WorkManager.getInstance(this).cancelAllWorkByTag(DeadlineWorker.WORK_TAG)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnParty.setOnClickListener {
            startActivity(Intent(this, PartyActivity::class.java))
        }

        binding.cvProfile.setOnClickListener {
            pendingQuestId = null
            imagePickerLauncher.launch("image/*")
        }
    }

    // =========================================================
    //  Bottom Navigation
    // =========================================================

    private fun setupBottomNav() {
        showTab("dashboard") // Default tab

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { showTab("dashboard"); true }
                R.id.nav_quests   -> { showTab("quests");    true }
                R.id.nav_party    -> {
                    startActivity(Intent(this, PartyActivity::class.java))
                    false // Don't select the party tab — we launch separate activity
                }
                else -> false
            }
        }
    }

    private fun showTab(tab: String) {
        when (tab) {
            "dashboard" -> {
                binding.panelDashboard.visibility = View.VISIBLE
                binding.panelQuests.visibility    = View.GONE
                binding.fabAddQuest.visibility    = View.GONE
            }
            "quests" -> {
                binding.panelDashboard.visibility = View.GONE
                binding.panelQuests.visibility    = View.VISIBLE
                binding.fabAddQuest.visibility    = View.VISIBLE
            }
        }
    }

    // =========================================================
    //  Filter Chips
    // =========================================================

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chip_active)  -> "active"
                checkedIds.contains(R.id.chip_done)    -> "done"
                checkedIds.contains(R.id.chip_failed)  -> "failed"
                else                                    -> "all"
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            "active"  -> allQuests.filter { !it.is_completed && !it.is_failed }
            "done"    -> allQuests.filter { it.is_completed }
            "failed"  -> allQuests.filter { it.is_failed }
            else      -> allQuests
        }
        questAdapter.updateData(filtered)

        // Update count label
        binding.tvQuestCount.text = "${filtered.size} quest"

        // Empty state
        binding.tvEmptyQuests.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // =========================================================
    //  RecyclerView
    // =========================================================

    private fun setupRecyclerView() {
        questAdapter = QuestAdapter(emptyList()) { quest ->
            startQuestCompletionFlow(quest.id, quest.exp_reward)
        }
        binding.rvQuests.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter       = questAdapter
        }
    }

    // =========================================================
    //  Swipe to Delete
    // =========================================================

    private fun setupSwipeToDelete() {
        val deletePaint = Paint().apply {
            color = Color.parseColor("#FF4444")
        }
        val deleteCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val quest    = questAdapter.getQuestAt(position)
                deleteQuest(quest)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    // Draw red background
                    c.drawRect(
                        itemView.right + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat(),
                        deletePaint
                    )
                    // Draw delete label
                    val textPaint = Paint().apply {
                        color     = Color.WHITE
                        textSize  = 36f
                        textAlign = Paint.Align.CENTER
                    }
                    val textY = (itemView.top + itemView.bottom) / 2f + textPaint.textSize / 3
                    c.drawText(
                        "🗑 Hapus",
                        itemView.right - 120f,
                        textY,
                        textPaint
                    )
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(deleteCallback).attachToRecyclerView(binding.rvQuests)
    }

    private fun deleteQuest(quest: Quest) {
        // Optimistically remove from local list
        val updatedAll = allQuests.toMutableList().also { it.removeIf { q -> q.id == quest.id } }
        allQuests = updatedAll
        applyFilter()

        // Delete from Firestore
        firestore.collection("Quests").document(quest.id)
            .delete()
            .addOnSuccessListener {
                Snackbar.make(
                    binding.coordinatorRoot,
                    "Quest '${quest.title}' dihapus",
                    Snackbar.LENGTH_LONG
                ).setAction("Undo") {
                    // Undo: restore quest to Firestore
                    restoreQuest(quest)
                }.setActionTextColor(Color.parseColor("#FFD700"))
                    .show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal hapus: ${e.message}", Toast.LENGTH_SHORT).show()
                // Restore in list on failure
                allQuests = allQuests.toMutableList().also { it.add(quest) }.sortedBy { it.deadline }
                applyFilter()
            }
    }

    private fun restoreQuest(quest: Quest) {
        val data = mapOf(
            "title"          to quest.title,
            "desc"           to quest.desc,
            "rank"           to quest.rank,
            "exp_reward"     to quest.exp_reward,
            "deadline"       to quest.deadline,
            "is_completed"   to quest.is_completed,
            "is_failed"      to quest.is_failed,
            "creator_id"     to quest.creator_id,
            "attachment_url" to quest.attachment_url
        )
        firestore.collection("Quests").document(quest.id)
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Quest dipulihkan.", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================================================
    //  Firebase: Player Stats (real-time)
    // =========================================================

    private fun fetchPlayerStats() {
        val currentUser = auth.currentUser ?: return
        val userDocRef  = firestore.collection("Users").document(currentUser.uid)

        statsListener = userDocRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val name   = snapshot.getString("name")         ?: "Hero"
            val level  = snapshot.getLong("level")?.toInt() ?: 1
            val hp     = snapshot.getLong("hp")?.toInt()    ?: 100
            val maxHp  = snapshot.getLong("maxHp")?.toInt() ?: 100
            val exp    = snapshot.getLong("exp")?.toInt()   ?: 0
            val maxExp = snapshot.getLong("maxExp")?.toInt() ?: 100
            val gold   = snapshot.getLong("gold")?.toInt()  ?: 0

            binding.tvPlayerName.text  = name
            binding.tvPlayerLevel.text = "⚔ Level $level"
            binding.tvPlayerGold.text  = "🪙 $gold"

            binding.pbHp.max      = maxHp
            binding.pbHp.progress = hp

            binding.pbExp.max      = maxExp
            binding.pbExp.progress = exp

            // HP bar color
            val hpPct  = hp.toFloat() / maxHp.toFloat()
            binding.pbHp.progressTintList = ColorStateList.valueOf(
                when {
                    hpPct <= 0.25f -> Color.parseColor("#FF2200")
                    hpPct <= 0.50f -> Color.parseColor("#FF8800")
                    else           -> Color.parseColor("#FF4444")
                }
            )
        }
    }

    // =========================================================
    //  Firebase: Load Quests (real-time)
    // =========================================================

    private fun loadQuests() {
        val currentUser = auth.currentUser ?: return

        questsListener = firestore.collection("Quests")
            .whereEqualTo("creator_id", currentUser.uid)
            .orderBy("deadline")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("MainActivity", "Quests listen failed", e)
                    return@addSnapshotListener
                }

                allQuests = snapshot?.toObjects(Quest::class.java) ?: emptyList()

                // Update dashboard stat counts
                val active  = allQuests.count { !it.is_completed && !it.is_failed }
                val done    = allQuests.count { it.is_completed }
                val failed  = allQuests.count { it.is_failed }
                binding.tvCountActive.text = active.toString()
                binding.tvCountDone.text   = done.toString()
                binding.tvCountFailed.text = failed.toString()

                // Show hint if no quests at all
                binding.cvStartHint.visibility =
                    if (allQuests.isEmpty()) View.VISIBLE else View.GONE

                applyFilter()
                checkDeadlinePenalties(allQuests)
            }
    }

    // =========================================================
    //  Game Logic: Deadline Penalty (in-app check on open)
    // =========================================================

    private fun checkDeadlinePenalties(quests: List<Quest>) {
        val currentUser = auth.currentUser ?: return
        val now         = System.currentTimeMillis()
        val userDocRef  = firestore.collection("Users").document(currentUser.uid)

        quests.filter { !it.is_completed && !it.is_failed && it.deadline > 0 && now > it.deadline }
            .forEach { failedQuest ->
                firestore.collection("Quests").document(failedQuest.id)
                    .update("is_failed", true)
                    .addOnSuccessListener {
                        val penalty = when (failedQuest.rank) {
                            "S" -> 50; "A" -> 30; "B" -> 15; else -> 5
                        }
                        firestore.runTransaction { tx ->
                            val hp    = (tx.get(userDocRef).getLong("hp") ?: 100).toInt()
                            val newHp = maxOf(0, hp - penalty)
                            tx.update(userDocRef, "hp", newHp)
                        }.addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "💀 Quest '${failedQuest.title}' gagal! -$penalty HP",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
    }

    // =========================================================
    //  Add Quest Dialog
    // =========================================================

    private fun showAddQuestDialog() {
        val dialogView  = layoutInflater.inflate(R.layout.dialog_add_quest, null)
        val etTitle     = dialogView.findViewById<TextInputEditText>(R.id.et_quest_title)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.et_quest_desc)
        val spinnerRank = dialogView.findViewById<Spinner>(R.id.spinner_rank)
        val tvDeadline  = dialogView.findViewById<TextView>(R.id.tv_deadline_display)
        val tvPreview   = dialogView.findViewById<TextView>(R.id.tv_exp_preview)
        val btnDate     = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_pick_date)

        val rankAdapter = ArrayAdapter.createFromResource(
            this, R.array.rank_options, android.R.layout.simple_spinner_item
        )
        rankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRank.adapter = rankAdapter

        spinnerRank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val (exp, gold) = rankToRewards(spinnerRank.selectedItem.toString())
                tvPreview.text  = "Reward: $exp EXP · $gold Gold"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        var selectedDeadline = 0L
        val calendar = Calendar.getInstance()

        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                TimePickerDialog(this, { _, hour, min ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, min)
                    calendar.set(Calendar.SECOND, 0)
                    selectedDeadline = calendar.timeInMillis
                    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    tvDeadline.text = "✅ ${fmt.format(Date(selectedDeadline))}"
                    tvDeadline.setTextColor(Color.parseColor("#FFD700"))
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Tambah Quest") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc  = etDesc.text.toString().trim()
                val rank  = spinnerRank.selectedItem.toString()
                when {
                    title.isEmpty()       -> Toast.makeText(this, "⚠ Judul tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                    selectedDeadline == 0L -> Toast.makeText(this, "⚠ Harap pilih deadline!", Toast.LENGTH_SHORT).show()
                    else                  -> saveNewQuest(title, desc, rank, selectedDeadline)
                }
            }
            .setNegativeButton("Batal", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FFD700"))
                }
            }
            .show()
    }

    private fun rankToRewards(rank: String): Pair<Int, Int> = when (rank) {
        "S"  -> Pair(500, 100)
        "A"  -> Pair(300, 60)
        "B"  -> Pair(150, 30)
        else -> Pair(50, 10)
    }

    private fun saveNewQuest(title: String, desc: String, rank: String, deadline: Long) {
        val currentUser  = auth.currentUser ?: return
        val (expReward, _) = rankToRewards(rank)

        firestore.collection("Quests")
            .add(mapOf(
                "title"          to title,
                "desc"           to desc,
                "rank"           to rank,
                "exp_reward"     to expReward,
                "deadline"       to deadline,
                "is_completed"   to false,
                "is_failed"      to false,
                "creator_id"     to currentUser.uid,
                "attachment_url" to ""
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "⚔ Quest '$title' ditambahkan!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================================================
    //  Quest Completion Flow
    // =========================================================

    fun startQuestCompletionFlow(questId: String, expReward: Int) {
        pendingQuestId  = questId
        pendingQuestExp = expReward
        imagePickerLauncher.launch("image/*")
    }

    private fun uploadProofAndCompleteQuest(questId: String, expReward: Int, imageUri: Uri) {
        binding.loadingIndicator.visibility = View.VISIBLE
        val storageRef = storage.reference.child("quest_proofs/${UUID.randomUUID()}.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { url ->
                    completeQuest(questId, expReward, url.toString())
                }
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(this, "Gagal upload bukti: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun completeQuest(questId: String, expReward: Int, attachmentUrl: String) {
        val currentUser  = auth.currentUser ?: return
        val userDocRef   = firestore.collection("Users").document(currentUser.uid)
        val questDocRef  = firestore.collection("Quests").document(questId)
        val (_, goldReward) = rankToRewards(questAdapter.getRankForId(questId) ?: "C")

        questDocRef.update("is_completed", true, "attachment_url", attachmentUrl)
            .addOnSuccessListener {
                firestore.runTransaction { tx ->
                    val snap        = tx.get(userDocRef)
                    val currentExp  = snap.getLong("exp")    ?: 0L
                    val currentLvl  = snap.getLong("level")  ?: 1L
                    val maxExp      = snap.getLong("maxExp") ?: (currentLvl * 100)
                    val currentGold = snap.getLong("gold")   ?: 0L
                    val newExp      = currentExp + expReward
                    val newGold     = currentGold + goldReward

                    if (newExp >= maxExp) {
                        val newLevel  = currentLvl + 1
                        tx.update(userDocRef, "level",  newLevel)
                        tx.update(userDocRef, "exp",    newExp - maxExp)
                        tx.update(userDocRef, "maxExp", newLevel * 100)
                        tx.update(userDocRef, "gold",   newGold)
                        true
                    } else {
                        tx.update(userDocRef, "exp",  newExp)
                        tx.update(userDocRef, "gold", newGold)
                        false
                    }
                }.addOnSuccessListener { leveledUp ->
                    binding.loadingIndicator.visibility = View.GONE
                    if (leveledUp) {
                        val newLevel = (auth.currentUser?.let {
                            firestore.collection("Users").document(it.uid) // hint only
                        })
                        Toast.makeText(this, "🎉 LEVEL UP! +$expReward EXP +$goldReward Gold", Toast.LENGTH_LONG).show()
                        NotificationHelper.notifyLevelUp(this, 0) // actual level from stats listener
                    } else {
                        Toast.makeText(this, "✅ Quest selesai! +$expReward EXP +$goldReward Gold", Toast.LENGTH_SHORT).show()
                    }
                    pendingQuestId = null
                }.addOnFailureListener { e ->
                    binding.loadingIndicator.visibility = View.GONE
                    Log.e("MainActivity", "Failed to update stats", e)
                }
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================================================
    //  Avatar Upload
    // =========================================================

    private fun uploadAvatar(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return
        binding.loadingIndicator.visibility = View.VISIBLE
        val storageRef = storage.reference.child("avatars/${currentUser.uid}.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { url ->
                    firestore.collection("Users").document(currentUser.uid)
                        .update("avatar_url", url.toString())
                        .addOnSuccessListener {
                            binding.loadingIndicator.visibility = View.GONE
                            Toast.makeText(this, "Avatar diperbarui!", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(this, "Gagal upload avatar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
