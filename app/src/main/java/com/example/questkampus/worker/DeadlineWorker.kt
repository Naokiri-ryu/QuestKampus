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
            // Tarik semua quest yang belum selesai
            val snapshot = firestore.collection("Quests")
                .whereEqualTo("creator_id", uid)
                .whereEqualTo("is_completed", false)
                .get().await()

            val batch = firestore.batch()
            var totalDamage = 0L
            var hasNewFails = false

            for (doc in snapshot.documents) {
                val deadline = doc.getLong("deadline") ?: 0L
                val isFailed = doc.getBoolean("is_failed") ?: false

                // CEK: Sudah lewat deadline DAN belum gagal
                if (deadline in 1 until now && !isFailed) {
                    hasNewFails = true
                    val rank = doc.getString("rank") ?: "C"

                    // Hitung akumulasi damage
                    totalDamage += when (rank.uppercase()) {
                        "S" -> 50L; "A" -> 30L; "B" -> 15L; else -> 5L
                    }

                    // Tandai quest sebagai gagal
                    batch.update(doc.reference, "is_failed", true)
                    batch.update(doc.reference, "penalty_applied", true)
                }
            }

            // KITA LAKUKAN TRANSAKSI PADA USER (Untuk Regen & Damage)
            firestore.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val currentHp = userSnap.getLong("hp") ?: 100L
                val maxHp = userSnap.getLong("maxHp") ?: 100L
                val role = userSnap.getString("role") ?: "Adventurer"

                var newHp = currentHp

                // 1. REGENERASI ALAMI (Berjalan setiap 1 jam)
                if (newHp > 0) { // Hanya regenerasi kalau belum mati/burnout
                    // Saint dapat Regen lebih cepat (4 HP/jam), yang lain (2 HP/jam)
                    val regenAmount = if (role.contains("Saint")) 4L else 2L
                    newHp = minOf(maxHp, newHp + regenAmount)
                }

                // 2. PENALTI DAMAGE (Jika ada quest yang kelewat)
                if (hasNewFails && totalDamage > 0) {
                    var finalDamage = totalDamage

                    // SKILL PASIF PALADIN: Tahan Banting di Background!
                    if (role.contains("Paladin")) {
                        finalDamage /= 2L
                    }

                    newHp = maxOf(0L, newHp - finalDamage)
                }

                // Simpan HP terbaru ke database
                tx.update(userRef, "hp", newHp)
                null
            }.await()

            // Jika ada quest yang diubah jadi gagal, commit batch-nya
            if (hasNewFails) {
                batch.commit().await()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}