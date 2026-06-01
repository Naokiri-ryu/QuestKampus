package com.example.questkampus

import com.google.firebase.firestore.DocumentId

data class Quest(
    @DocumentId val id: String = "",
    val title: String = "",
    val desc: String = "",
    val rank: String = "C",
    val exp_reward: Int = 10,
    val is_completed: Boolean = false,
    val creator_id: String = "",
    val attachment_url: String = "",
    val deadline: Long = 0L,     // TAMBAHAN: Menyimpan batas waktu dalam format Timestamp (Long)
    val is_failed: Boolean = false // TAMBAHAN: Penanda jika tugas ini gagal diselesaikan
)