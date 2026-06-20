package com.example.questkampus

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeadlineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid ?: return Result.success()

        val now = System.currentTimeMillis()
        val userRef = firestore.collection("Users").document(uid)

        return try {
            // Cari quest yang telat, gagal, dan belum dikenakan penalti
            val snapshot = firestore.collection("Quests")
                .whereEqualTo("creator_id", uid)
                .whereEqualTo("is_completed", false)
                .whereEqualTo("penalty_applied", false)
                .get().await()

            var totalDamage = 0
            val batch = firestore.batch()
            var hasNewFails = false

            for (doc in snapshot.documents) {
                val deadline = doc.getLong("deadline") ?: 0L
                val rank = doc.getString("rank") ?: "C"
                val isFailed = doc.getBoolean("is_failed") ?: false

                // Jika sudah lewat deadline atau memang sudah ditandai gagal tapi belum kena penalti
                if ((deadline in 1..<now) || isFailed) {
                    hasNewFails = true
                    totalDamage += when (rank.uppercase()) { 
                        "S" -> 50; "A" -> 30; "B" -> 15; else -> 5 
                    }
                    batch.update(doc.reference, "is_failed", true)
                    batch.update(doc.reference, "penalty_applied", true)
                }
            }

            if (hasNewFails && totalDamage > 0) {
                // Gunakan transaksi untuk memastikan HP tidak negatif dan konsisten
                firestore.runTransaction { tx ->
                    val userSnap = tx.get(userRef)
                    val currentHp = userSnap.getLong("hp") ?: 100L
                    tx.update(userRef, "hp", maxOf(0L, currentHp - totalDamage))
                }.await()
                
                batch.commit().await()
                Log.d("DeadlineWorker", "Berhasil menerapkan penalti -$totalDamage HP")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DeadlineWorker", "Error: ${e.message}")
            Result.retry()
        }
    }
}