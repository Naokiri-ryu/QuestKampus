package com.example.questkampus

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Quest(
    @DocumentId val id: String = "",
    var title: String = "",
    var desc: String = "",
    var rank: String = "C",
    var exp_reward: Int = 0,

    // Paksa Firebase menggunakan nama persis seperti di bawah ini
    @get:PropertyName("is_completed")
    @set:PropertyName("is_completed")
    var is_completed: Boolean = false,

    @get:PropertyName("is_failed")
    @set:PropertyName("is_failed")
    var is_failed: Boolean = false,

    var creator_id: String = "",
    var deadline: Long = 0L,
    var attachment_url: String = "",
    var support_link: String = "",
    var support_file_url: String = "",
    var proof_link: String = "",
    var proof_type: String = "image",
    var penalty_applied: Boolean = false,

    // ==========================================
    // TAMBAHAN UNTUK FITUR KELOMPOK (MULTIPLAYER)
    // ==========================================
    var party_id: String = "",
    var assigned_to: String = ""

) {
    // Helper untuk mendapatkan reward gold
    fun getGoldReward(): Int = when(rank.uppercase()) {
        "S" -> 100
        "A" -> 60
        "B" -> 30
        else -> 10
    }

    // Helper untuk mendapatkan HP recovery saat selesai
    fun getHealAmount(): Int = when(rank.uppercase()) {
        "S" -> 50
        "A" -> 30
        "B" -> 15
        else -> 5
    }
}