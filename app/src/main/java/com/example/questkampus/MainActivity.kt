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
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
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
import com.bumptech.glide.Glide
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var questAdapter: QuestAdapter

    private var statsListener: ListenerRegistration? = null
    private var questsListener: ListenerRegistration? = null

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var supportFileLauncher: ActivityResultLauncher<String>

    // Quest state
    private var allQuests     = listOf<Quest>()
    private var currentFilter = "all"

    // Pending quest completion
    private var pendingQuestId    : String? = null
    private var pendingQuestExp   : Int     = 0
    private var pendingProofLink  : String  = ""

    // Support file upload (saat add quest)
    private var pendingSupportFileUri: Uri? = null

    private val TIPS = listOf(
        "🐉 Quest Rank S = Boss Fight. Reward EXP & Gold terbanyak!",
        "⚔️ Selesaikan quest sebelum deadline agar HP tidak berkurang.",
        "🛡 Bergabung ke Party dan kerjakan quest bersama teman!",
        "📎 Lampirkan file soal saat membuat quest agar mudah diakses.",
        "🔗 Bukti penyelesaian bisa berupa foto ATAU link Google Drive.",
        "🌿 Quest Rank C cocok untuk tugas harian yang ringan.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()
        storage   = FirebaseStorage.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, 0); insets
        }
        NotificationHelper.createChannels(this)
        requestNotificationPermission()
        setupLaunchers()
        setupUI()
        setupBottomNav()
        setupFilterChips()
        setupRecyclerView()
        setupSwipeToDelete()
        fetchPlayerStats()
        loadQuests()
        binding.tvTip.text = TIPS.random()
    }

    override fun onDestroy() {
        super.onDestroy()
        statsListener?.remove()
        questsListener?.remove()
    }

    // =========================================================
    //  Permission
    // =========================================================

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
    }
    // =========================================================
    //  Image / File Launchers
    // =========================================================

    private fun setupLaunchers() {
        // Launcher untuk bukti quest atau avatar
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                if (pendingQuestId == null) uploadAvatar(it)
                else showCompleteQuestDialog(pendingQuestId!!, pendingQuestExp, it)
            }
        }
        // Launcher untuk file pendukung quest (lampiran soal)
        supportFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { pendingSupportFileUri = it }
        }
    }

    // =========================================================
    //  UI Setup
    // =========================================================

    private fun setupUI() {
        binding.fabAddQuest.setOnClickListener { showAddQuestDialog() }
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java)); finish()
        }
        binding.btnParty.setOnClickListener { startActivity(Intent(this, PartyActivity::class.java)) }
        binding.cvProfile.setOnClickListener { pendingQuestId = null; imagePickerLauncher.launch("image/*") }
    }

    // =========================================================
    //  Bottom Navigation
    // =========================================================

    private fun setupBottomNav() {
        showTab("dashboard")
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { showTab("dashboard"); true }
                R.id.nav_quests   -> { showTab("quests");    true }
                R.id.nav_party    -> { startActivity(Intent(this, PartyActivity::class.java)); false }
                else -> false
            }
        }
    }

    private fun showTab(tab: String) {
        binding.panelDashboard.visibility = if (tab == "dashboard") View.VISIBLE else View.GONE
        binding.panelQuests.visibility    = if (tab == "quests")    View.VISIBLE else View.GONE
        binding.fabAddQuest.visibility    = if (tab == "quests")    View.VISIBLE else View.GONE
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
                else -> "all"
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
        binding.tvQuestCount.text = "${filtered.size} quest"
        binding.tvEmptyQuests.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // =========================================================
    //  RecyclerView + Swipe Delete
    // =========================================================

    private fun setupRecyclerView() {
        questAdapter = QuestAdapter(emptyList()) { quest ->
            pendingQuestId  = quest.id
            pendingQuestExp = quest.exp_reward
            imagePickerLauncher.launch("image/*")
        }
        binding.rvQuests.layoutManager = LinearLayoutManager(this)
        binding.rvQuests.adapter = questAdapter
    }

    private fun setupSwipeToDelete() {
        val deletePaint = Paint().apply { color = Color.parseColor("#FF4444") }
        val cb = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, dir: Int) =
                deleteQuest(questAdapter.getQuestAt(viewHolder.adapterPosition))
            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
                if (state == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val iv = vh.itemView
                    c.drawRect(iv.right + dX, iv.top.toFloat(), iv.right.toFloat(), iv.bottom.toFloat(), deletePaint)
                    c.drawText("🗑 Hapus", iv.right - 120f, (iv.top + iv.bottom) / 2f + 12f,
                        Paint().apply { color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER })
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }
        ItemTouchHelper(cb).attachToRecyclerView(binding.rvQuests)
    }

    private fun deleteQuest(quest: Quest) {
        allQuests = allQuests.filter { it.id != quest.id }
        applyFilter()
        firestore.collection("Quests").document(quest.id).delete()
            .addOnSuccessListener {
                Snackbar.make(binding.coordinatorRoot, "Quest '${quest.title}' dihapus", Snackbar.LENGTH_LONG)
                    .setAction("Undo") { restoreQuest(quest) }
                    .setActionTextColor(Color.parseColor("#FFD700")).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal hapus: ${e.message}", Toast.LENGTH_SHORT).show()
                allQuests = (allQuests + quest).sortedBy { it.deadline }; applyFilter()
            }
    }

    private fun restoreQuest(quest: Quest) {
        firestore.collection("Quests").document(quest.id).set(mapOf(
            "title" to quest.title, "desc" to quest.desc, "rank" to quest.rank,
            "exp_reward" to quest.exp_reward, "deadline" to quest.deadline,
            "is_completed" to quest.is_completed, "is_failed" to quest.is_failed,
            "creator_id" to quest.creator_id, "attachment_url" to quest.attachment_url,
            "support_link" to quest.support_link, "support_file_url" to quest.support_file_url
        )).addOnSuccessListener { Toast.makeText(this, "Quest dipulihkan.", Toast.LENGTH_SHORT).show() }
    }

    // =========================================================
    //  Firebase: Stats (real-time) — FIX: load avatar dengan Glide
    // =========================================================

    private fun fetchPlayerStats() {
        val uid = auth.currentUser?.uid ?: return
        statsListener = firestore.collection("Users").document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null || !snap.exists()) return@addSnapshotListener
                val name   = snap.getString("name")        ?: "Hero"
                val level  = snap.getLong("level")?.toInt()  ?: 1
                val hp     = snap.getLong("hp")?.toInt()     ?: 100
                val maxHp  = snap.getLong("maxHp")?.toInt()  ?: 100
                val exp    = snap.getLong("exp")?.toInt()    ?: 0
                val maxExp = snap.getLong("maxExp")?.toInt() ?: RpgTheme.maxExpForLevel(level)
                val gold   = snap.getLong("gold")?.toInt()   ?: 0
                val avatar = snap.getString("avatar_url")    ?: ""

                binding.tvPlayerName.text  = name
                binding.tvPlayerLevel.text = "⚔ Level $level"
                binding.tvPlayerGold.text  = "🪙 $gold"
                binding.pbHp.max  = maxHp;  binding.pbHp.progress  = hp
                binding.pbExp.max = maxExp; binding.pbExp.progress = exp

                val hpPct = hp.toFloat() / maxHp.toFloat()
                binding.pbHp.progressTintList = ColorStateList.valueOf(when {
                    hpPct <= 0.25f -> Color.parseColor("#FF2200")
                    hpPct <= 0.50f -> Color.parseColor("#FF8800")
                    else           -> Color.parseColor("#FF4444")
                })

                // ✅ FIX: Load avatar dengan Glide
                if (avatar.isNotEmpty()) {
                    Glide.with(this).load(avatar).circleCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(binding.ivAvatar)
                }
            }
    }

    // =========================================================
    //  Firebase: Load Quests — FIX: hapus orderBy, sort lokal
    // =========================================================

    private fun loadQuests() {
        val uid = auth.currentUser?.uid ?: return
        questsListener = firestore.collection("Quests")
            .whereEqualTo("creator_id", uid)
            // FIX: hapus .orderBy("deadline") → butuh index, sort lokal saja
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w("MainActivity", "Quests listen failed", e); return@addSnapshotListener }
                allQuests = (snapshot?.toObjects(Quest::class.java) ?: emptyList())
                    .sortedBy { it.deadline } // sort lokal

                binding.tvCountActive.text = allQuests.count { !it.is_completed && !it.is_failed }.toString()
                binding.tvCountDone.text   = allQuests.count { it.is_completed }.toString()
                binding.tvCountFailed.text = allQuests.count { it.is_failed }.toString()
                binding.cvStartHint.visibility = if (allQuests.isEmpty()) View.VISIBLE else View.GONE

                applyFilter()
                checkDeadlinePenalties(allQuests)
            }
    }

    // =========================================================
    //  Deadline Penalty
    // =========================================================

    private fun checkDeadlinePenalties(quests: List<Quest>) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val userRef = firestore.collection("Users").document(uid)
        quests.filter { !it.is_completed && !it.is_failed && it.deadline > 0 && now > it.deadline }.forEach { fq ->
            firestore.collection("Quests").document(fq.id).update("is_failed", true).addOnSuccessListener {
                val p = when (fq.rank) { "S" -> 50; "A" -> 30; "B" -> 15; else -> 5 }
                firestore.runTransaction { tx ->
                    val hp = (tx.get(userRef).getLong("hp") ?: 100).toInt()
                    tx.update(userRef, "hp", maxOf(0, hp - p))
                }.addOnSuccessListener {
                    Toast.makeText(this, "💀 Quest '${fq.title}' gagal! -$p HP", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // =========================================================
    //  Add Quest Dialog — dengan RPG theming + auto rank + support file
    // =========================================================

    private fun showAddQuestDialog() {
        pendingSupportFileUri = null
        val dialogView  = layoutInflater.inflate(R.layout.dialog_add_quest, null)
        val etTitle     = dialogView.findViewById<TextInputEditText>(R.id.et_quest_title)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.et_quest_desc)
        val etSupLink   = dialogView.findViewById<TextInputEditText>(R.id.et_support_link)
        val spinnerRank = dialogView.findViewById<Spinner>(R.id.spinner_rank)
        val tvDeadline  = dialogView.findViewById<TextView>(R.id.tv_deadline_display)
        val tvPreview   = dialogView.findViewById<TextView>(R.id.tv_exp_preview)
        val tvSuggest   = dialogView.findViewById<TextView>(R.id.tv_rank_suggestion)
        val tvRankDesc  = dialogView.findViewById<TextView>(R.id.tv_rank_description)
        val tvFileStatus = dialogView.findViewById<TextView>(R.id.tv_file_status)
        val btnDate     = dialogView.findViewById<Button>(R.id.btn_pick_date)
        val btnAttach   = dialogView.findViewById<Button>(R.id.btn_attach_file)

        val rankAdapter = ArrayAdapter.createFromResource(this, R.array.rank_options, android.R.layout.simple_spinner_item)
        rankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRank.adapter = rankAdapter

        // Auto-suggest rank dari judul
        etTitle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val suggested = RpgTheme.suggestRank(s.toString())
                tvSuggest.text = if (s.toString().length > 3) "💡 Saran: Rank $suggested" else ""
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Update preview saat rank berubah
        spinnerRank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val rank = spinnerRank.selectedItem.toString()
                val (exp, gold) = rankToRewards(rank)
                tvPreview.text  = "Reward: $exp EXP · $gold Gold"
                tvRankDesc.text = "${RpgTheme.rankIcon(rank)} ${RpgTheme.rankDescription(rank)}"
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // File lampiran
        btnAttach.setOnClickListener {
            supportFileLauncher.launch("*/*")
            tvFileStatus.text = "📁 File dipilih (akan diupload saat simpan)"
        }

        // Deadline picker
        var selectedDeadline = 0L
        val cal = Calendar.getInstance()
        btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                TimePickerDialog(this, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0)
                    selectedDeadline = cal.timeInMillis
                    tvDeadline.text = "✅ ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(selectedDeadline))}"
                    tvDeadline.setTextColor(Color.parseColor("#FFD700"))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this).setView(dialogView)
            .setPositiveButton("Tambah Quest") { _, _ ->
                val title   = etTitle.text.toString().trim()
                val desc    = etDesc.text.toString().trim()
                val rank    = spinnerRank.selectedItem.toString()
                val supLink = etSupLink.text.toString().trim()
                when {
                    title.isEmpty()        -> Toast.makeText(this, "⚠ Judul tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                    selectedDeadline == 0L -> Toast.makeText(this, "⚠ Harap pilih deadline!", Toast.LENGTH_SHORT).show()
                    else -> {
                        if (pendingSupportFileUri != null) uploadSupportFileAndSave(title, desc, rank, selectedDeadline, supLink)
                        else saveNewQuest(title, desc, rank, selectedDeadline, supLink, "")
                    }
                }
            }
            .setNegativeButton("Batal", null).create()
            .also { d -> d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FFD700")) } }
            .show()
    }

    private fun rankToRewards(rank: String): Pair<Int, Int> = when(rank) {
        "S" -> Pair(500, 100); "A" -> Pair(300, 60); "B" -> Pair(150, 30); else -> Pair(50, 10)
    }

    private fun uploadSupportFileAndSave(title: String, desc: String, rank: String, deadline: Long, supLink: String) {
        val uri = pendingSupportFileUri ?: return
        binding.loadingIndicator.visibility = View.VISIBLE
        val ref = storage.reference.child("quest_support/${UUID.randomUUID()}")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                saveNewQuest(title, desc, rank, deadline, supLink, url.toString())
            }
        }.addOnFailureListener {
            binding.loadingIndicator.visibility = View.GONE
            saveNewQuest(title, desc, rank, deadline, supLink, "") // simpan tanpa file
        }
    }

    private fun saveNewQuest(title: String, desc: String, rank: String, deadline: Long, supLink: String, supFileUrl: String) {
        val uid = auth.currentUser?.uid ?: return
        val (expReward, _) = rankToRewards(rank)
        firestore.collection("Quests").add(mapOf(
            "title" to title, "desc" to desc, "rank" to rank,
            "exp_reward" to expReward, "deadline" to deadline,
            "is_completed" to false, "is_failed" to false,
            "creator_id" to uid,
            "attachment_url" to "", "proof_link" to "", "proof_type" to "image",
            "support_link" to supLink, "support_file_url" to supFileUrl,
            "party_id" to "", "assigned_to" to ""
        )).addOnSuccessListener {
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "${RpgTheme.rankIcon(rank)} Quest '$title' ditambahkan!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================
    //  Quest Completion — FIX: dialog bukti, reset pendingQuestId cepat
    // =========================================================

    private fun showCompleteQuestDialog(questId: String, expReward: Int, imageUri: Uri?) {
        val quest = allQuests.find { it.id == questId } ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_complete_quest, null)
        val tvFlavor   = dialogView.findViewById<TextView>(R.id.tv_quest_flavor)
        val btnUpload  = dialogView.findViewById<Button>(R.id.btn_upload_image)
        val etLink     = dialogView.findViewById<TextInputEditText>(R.id.et_proof_link)
        val tvStatus   = dialogView.findViewById<TextView>(R.id.tv_proof_status)

        tvFlavor.text = RpgTheme.completionFlavor(quest.rank, quest.title)

        var selectedUri = imageUri
        if (selectedUri != null) tvStatus.text = "✅ Foto bukti siap diupload"

        btnUpload.setOnClickListener {
            pendingQuestId  = questId
            pendingQuestExp = expReward
            imagePickerLauncher.launch("image/*")
        }

        AlertDialog.Builder(this).setView(dialogView)
            .setPositiveButton("Selesaikan!") { _, _ ->
                // ✅ FIX: reset SEBELUM async apapun
                pendingQuestId = null
                val proofLink = etLink.text.toString().trim()
                when {
                    selectedUri != null -> uploadProofAndComplete(questId, expReward, selectedUri!!)
                    proofLink.isNotEmpty() -> completeQuest(questId, expReward, "", proofLink, "link")
                    else -> completeQuest(questId, expReward, "", "", "none")
                }
            }
            .setNegativeButton("Batal") { _, _ -> pendingQuestId = null }
            .create().also { d ->
                d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FFD700")) }
            }.show()
    }

    private fun uploadProofAndComplete(questId: String, expReward: Int, imageUri: Uri) {
        binding.loadingIndicator.visibility = View.VISIBLE
        val ref = storage.reference.child("quest_proofs/${UUID.randomUUID()}.jpg")
        ref.putFile(imageUri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                completeQuest(questId, expReward, url.toString(), "", "image")
            }
        }.addOnFailureListener { e ->
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeQuest(questId: String, expReward: Int, attachUrl: String, proofLink: String, proofType: String) {
        val uid = auth.currentUser?.uid ?: return
        val userRef  = firestore.collection("Users").document(uid)
        val questRef = firestore.collection("Quests").document(questId)
        val (_, goldReward) = rankToRewards(questAdapter.getRankForId(questId) ?: "C")

        // ✅ FIX: Update quest langsung
        questRef.update(mapOf(
            "is_completed"   to true,
            "attachment_url" to attachUrl,
            "proof_link"     to proofLink,
            "proof_type"     to proofType
        )).addOnSuccessListener {
            firestore.runTransaction { tx ->
                val snap    = tx.get(userRef)
                val level   = snap.getLong("level")?.toInt() ?: 1
                val curExp  = snap.getLong("exp")?.toInt()   ?: 0
                val maxExp  = snap.getLong("maxExp")?.toInt() ?: RpgTheme.maxExpForLevel(level)
                val curGold = snap.getLong("gold")?.toInt()  ?: 0
                val newExp  = curExp + expReward
                val newGold = curGold + goldReward
                if (newExp >= maxExp) {
                    val newLevel   = level + 1
                    val newMaxExp  = RpgTheme.maxExpForLevel(newLevel) // EXP scaling eksponensial
                    tx.update(userRef, "level", newLevel)
                    tx.update(userRef, "exp",    newExp - maxExp)
                    tx.update(userRef, "maxExp", newMaxExp)
                    tx.update(userRef, "gold",   newGold)
                    true
                } else {
                    tx.update(userRef, "exp",  newExp)
                    tx.update(userRef, "gold", newGold)
                    false
                }
            }.addOnSuccessListener { leveledUp ->
                binding.loadingIndicator.visibility = View.GONE
                if (leveledUp) {
                    Toast.makeText(this, "🎉 LEVEL UP! +$expReward EXP +$goldReward Gold", Toast.LENGTH_LONG).show()
                    NotificationHelper.notifyLevelUp(this, 0)
                } else {
                    Toast.makeText(this, "✅ Quest selesai! +$expReward EXP +$goldReward Gold", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Log.e("MainActivity", "Stats update failed", e)
            }
        }.addOnFailureListener { e ->
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================
    //  Avatar Upload (Glide akan load setelah stats listener update)
    // =========================================================

    private fun uploadAvatar(imageUri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        binding.loadingIndicator.visibility = View.VISIBLE
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putFile(imageUri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                firestore.collection("Users").document(uid)
                    .update("avatar_url", url.toString())
                    .addOnSuccessListener {
                        binding.loadingIndicator.visibility = View.GONE
                        Toast.makeText(this, "Avatar diperbarui!", Toast.LENGTH_SHORT).show()
                        // Langsung load juga ke ImageView
                        Glide.with(this).load(url.toString()).circleCrop().into(binding.ivAvatar)
                    }
            }
        }.addOnFailureListener { e ->
            binding.loadingIndicator.visibility = View.GONE
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
