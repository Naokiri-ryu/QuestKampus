package com.example.questkampus

import com.google.firebase.firestore.DocumentId

data class Quest(
    @DocumentId val id: String = "",
    val title: String = "",
    val desc: String = "",
    val rank: String = "C",
    val exp_reward: Int = 10,
    val is_completed: Boolean = false,
    val is_failed: Boolean = false,
    val creator_id: String = "",
    val deadline: Long = 0L,
    // Bukti penyelesaian
    val attachment_url: String = "",   // URL gambar bukti di Storage
    val proof_link: String = "",       // Link bukti (opsional, alternatif gambar)
    val proof_type: String = "image",  // "image" atau "link"
    // File/link pendukung saat buat quest
    val support_file_url: String = "", // URL file soal/pendukung di Storage
    val support_link: String = "",     // Link pendukung (Google Drive, dll)
    // Party
    val party_id: String = "",         // PIN party jika quest milik party
    val assigned_to: String = ""       // UID member yang ditugaskan
)
