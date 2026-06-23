package com.example.questkampus.utils

object RpgTheme {
    fun maxExpForLevel(level: Int): Int = 100 * (level * level)

    fun rankIcon(rank: String): String = when (rank.uppercase()) {
        "S" -> "👑"
        "A" -> "💎"
        "B" -> "⚔️"
        "C" -> "📜"
        else -> "❓"
    }

    fun rankTitle(rank: String): String = when (rank.uppercase()) {
        "S" -> "Legendary Quest"
        "A" -> "Epic Challenge"
        "B" -> "Elite Mission"
        "C" -> "Common Task"
        else -> "Mystery Quest"
    }

    fun rankDescription(rank: String): String = when (rank.uppercase()) {
        "S" -> "Boss Fight: Sangat sulit & berisiko tinggi."
        "A" -> "Elite: Membutuhkan usaha ekstra."
        "B" -> "Advanced: Tugas dengan kesulitan sedang."
        "C" -> "Basic: Tugas harian ringan."
        else -> "Rank tidak diketahui."
    }

    fun suggestRank(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("uas") || t.contains("project") || t.contains("besar") -> "S"
            t.contains("uts") || t.contains("laporan") -> "A"
            t.contains("tugas") || t.contains("quiz") -> "B"
            else -> "C"
        }
    }

    fun completionFlavor(rank: String, title: String): String = when (rank.uppercase()) {
        "S" -> "Luar biasa! Boss '$title' telah ditaklukkan!"
        "A" -> "Kemenangan besar! '$title' berhasil diselesaikan."
        "B" -> "Bagus! Misi '$title' tuntas."
        else -> "Tugas '$title' telah selesai dikerjakan."
    }

    // 1. FUNGSI AUTO-RANK (ANTI CURANG)
    fun calculateAutoRank(title: String, deadlineMillis: Long): String {
        val timeLeft = deadlineMillis - System.currentTimeMillis()
        val daysLeft = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(timeLeft)
        val titleLower = title.lowercase()

        return when {
            // Rank S: Tugas raksasa (UAS, Skripsi) ATAU waktu pengerjaan lebih dari 14 hari
            titleLower.contains("uas") || titleLower.contains("skripsi") || daysLeft > 14 -> "S"
            // Rank A: Tugas besar (UTS, Project) ATAU waktu pengerjaan 7-14 hari
            titleLower.contains("uts") || titleLower.contains("project") || daysLeft in 7..14 -> "A"
            // Rank B: Tugas menengah (Laporan, Makalah) ATAU waktu pengerjaan 3-6 hari
            titleLower.contains("laporan") || titleLower.contains("makalah") || daysLeft in 3..6 -> "B"
            // Rank C: Tugas harian/cepat
            else -> "C"
        }
    }

    // 2. FUNGSI PERINGKAT PETUALANG (USER RANK)
    fun getUserRankString(level: Int): String {
        return when {
            level >= 100 -> "👑 Rank S (Legendary)"
            level >= 75  -> "💎 Rank A (Mithril)"
            level >= 50  -> "🥇 Rank B (Platinum)"
            level >= 25  -> "🥈 Rank C (Gold)"
            level >= 10  -> "🥉 Rank D (Silver)"
            else         -> "🪵 Rank E (Novice)"
        }
    }
}