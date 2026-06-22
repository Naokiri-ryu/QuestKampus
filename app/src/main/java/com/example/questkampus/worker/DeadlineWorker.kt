package com.example.questkampus.worker

import android.content.Context
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
            // Tarik semua quest yang belum selesai milik user ini
            val snapshot = firestore.collection("Quests")
                .whereEqualTo("creator_id", uid)
                .whereEqualTo("is_completed", false)
                .get().await()

            val batch = firestore.batch()
            var totalDamage = 0
            var hasNewFails = false

            for (doc in snapshot.documents) {
                val deadline = doc.getLong("deadline") ?: 0L
                val isFailed = doc.getBoolean("is_failed") ?: false
                val penaltyApplied = doc.getBoolean("penalty_applied") ?: false

                // CEK: Sudah lewat deadline DAN belum gagal
                if (deadline in 1 until now && !isFailed) {
                    hasNewFails = true
                    val rank = doc.getString("rank") ?: "C"

                    // Hitung damage sesuai rank
                    totalDamage += when (rank.uppercase()) {
                        "S" -> 50; "A" -> 30; "B" -> 15; else -> 5
                    }

                    // Tandai gagal dan penalti
                    batch.update(doc.reference, "is_failed", true)
                    batch.update(doc.reference, "penalty_applied", true)
                }
            }

            if (hasNewFails && totalDamage > 0) {
                firestore.runTransaction { tx ->
                    val userSnap = tx.get(userRef)
                    val currentHp = userSnap.getLong("hp") ?: 100L
                    tx.update(userRef, "hp", maxOf(0L, currentHp - totalDamage))
                }.await()
                batch.commit().await()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}