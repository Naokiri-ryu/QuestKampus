package com.example.questkampus.utils

object RpgTheme {

    fun maxExpForLevel(level: Int): Int {
        // Scaling EXP eksponensial sederhana
        return 100 * (level * level)
    }

    fun rankIcon(rank: String): String = when (rank.uppercase()) {
        "S" -> "🐉"
        "A" -> "⚔️"
        "B" -> "🛡️"
        "C" -> "🌿"
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
}