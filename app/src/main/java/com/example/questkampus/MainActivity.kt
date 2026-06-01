package com.example.questkampus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.questkampus.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var statsListener: ListenerRegistration? = null

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private var currentQuestId: String? = null
    private var currentQuestExp: Int = 0

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
        storage = FirebaseStorage.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupImagePicker()
        setupUI()
        fetchPlayerStats()
    }

    private fun setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val questId = currentQuestId
                if (questId != null) {
                    uploadProofAndCompleteQuest(questId, currentQuestExp, it)
                }
            } ?: run {
                Toast.makeText(this, "Pemilihan gambar dibatalkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupUI() {
        binding.fabAddQuest.setOnClickListener {
            addNewQuest()
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun addNewQuest() {
                val questBaru = hashMapOf(
                )

                firestore.collection("Quests")
                    .add(questBaru)
                    }
            }
    }

    fun startQuestCompletionFlow(questId: String, expReward: Int) {
        currentQuestId = questId
        currentQuestExp = expReward
        imagePickerLauncher.launch("image/*")
    }

    private fun uploadProofAndCompleteQuest(questId: String, expReward: Int, imageUri: Uri) {
        binding.loadingIndicator.visibility = View.VISIBLE
        
        val fileName = "quest_proofs/${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child(fileName)

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    completeQuest(questId, expReward, downloadUri.toString())
                }
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Log.e("MainActivity", "Upload failed", e)
                Toast.makeText(this, "Gagal mengunggah bukti: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchPlayerStats() {
        val currentUser = auth.currentUser ?: return
        val userDocRef = firestore.collection("Users").document(currentUser.uid)
        
        statsListener = userDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("MainActivity", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val name = snapshot.getString("name") ?: "Hero"
                val level = snapshot.getLong("level")?.toInt() ?: 1
                val hp = snapshot.getLong("hp")?.toInt() ?: 100
                val maxHp = snapshot.getLong("maxHp")?.toInt() ?: 100
                val exp = snapshot.getLong("exp")?.toInt() ?: 0
                val maxExp = snapshot.getLong("maxExp")?.toInt() ?: 100

                binding.tvPlayerName.text = name
                binding.tvPlayerLevel.text = "Level $level"
                
                binding.pbHp.max = maxHp
                binding.pbHp.progress = hp
                
                binding.pbExp.max = maxExp
                binding.pbExp.progress = exp
                
                Log.d("MainActivity", "Stats updated: HP=$hp, EXP=$exp")
            }
        }
    }

    private fun completeQuest(questId: String, expReward: Int, attachmentUrl: String) {
        val currentUser = auth.currentUser ?: return
        val userDocRef = firestore.collection("Users").document(currentUser.uid)
        val questDocRef = firestore.collection("Quests").document(questId)

        val questUpdates = hashMapOf<String, Any>(
            "is_completed" to true,
            "attachment_url" to attachmentUrl
        )

        questDocRef.update(questUpdates)
            .addOnSuccessListener {
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(userDocRef)
                    val currentExp = snapshot.getLong("exp") ?: 0L
                    val currentLevel = snapshot.getLong("level") ?: 1L
                    val newTotalExp = currentExp + expReward
                    val newLevel = (newTotalExp / 1000) + 1
                    
                    transaction.update(userDocRef, "exp", newTotalExp)
                    transaction.update(userDocRef, "level", newLevel)
                    
                    newLevel > currentLevel
                }.addOnSuccessListener { leveledUp ->
                    binding.loadingIndicator.visibility = View.GONE
                    if (leveledUp) {
                        Toast.makeText(this, "CONGRATULATIONS! LEVEL UP!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Quest Complete! +$expReward EXP", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener { e ->
                    binding.loadingIndicator.visibility = View.GONE
                    Log.e("MainActivity", "Failed to update player stats", e)
                }
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                Log.e("MainActivity", "Failed to complete quest", e)
                Toast.makeText(this, "Error completing quest", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        statsListener?.remove()
    }
}