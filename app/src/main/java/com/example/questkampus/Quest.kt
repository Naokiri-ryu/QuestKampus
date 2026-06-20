package com.example.questkampus

import com.google.firebase.firestore.DocumentId

data class Quest(
    @DocumentId val id: String = "",
    val title: String = "",
    val desc: String = "",
    val rank: String = "C",
    val exp_reward: Int = 0,
    val is_completed: Boolean = false,
    val is_failed: Boolean = false,
    val creator_id: String = "",
    val deadline: Long = 0L,
    val attachment_url: String = "", // Bukti upload (legacy/alias)
    val support_link: String = "",
    val support_file_url: String = "",
    val proof_link: String = "",
    val proof_type: String = "image",
    val penalty_applied: Boolean = false // Mencegah pengurangan HP berulang
) {
    // Helper untuk mendapatkan reward gold (logic dipindah ke sini agar konsisten)
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