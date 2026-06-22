package com.example.questkampus.data.model

import com.google.firebase.firestore.DocumentId

data class Party(
    @DocumentId val id: String = "",
    var party_name: String = "",
    var description: String = "",     // Deskripsi tugas kelompok
    var party_hp: Int = 500,
    var max_hp: Int = 500,
    var pin_code: String = "",
    var creator_id: String = "",
    var members: List<String> = emptyList(), // Daftar UID anggota
    var attachment_url: String = ""   // Jika ada file tugas/soal kelompok
)