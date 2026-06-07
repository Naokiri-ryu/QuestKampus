package com.example.questkampus

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.questkampus.databinding.ItemQuestBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class QuestAdapter(
    private var questList: List<Quest>,
    private val onQuestCompleted: (Quest) -> Unit
) : RecyclerView.Adapter<QuestAdapter.QuestViewHolder>() {

    // =========================================================
    //  ViewHolder
    // =========================================================

    inner class QuestViewHolder(val binding: ItemQuestBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder {
        val binding = ItemQuestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return QuestViewHolder(binding)
    }

    // =========================================================
    //  Bind Data
    // =========================================================

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        val quest = questList[position]
        val b     = holder.binding

        // --- Title ---
        b.tvQuestTitle.text = quest.title

        // --- Rank Badge ---
        b.tvQuestRank.text = quest.rank
        b.tvQuestRank.backgroundTintList = ColorStateList.valueOf(getRankColor(quest.rank))
        b.tvQuestRank.setTextColor(if (quest.rank == "C") Color.WHITE else Color.BLACK)

        // --- Deadline ---
        bindDeadline(b.tvQuestDeadline, quest)

        // --- Completed / Failed visual state ---
        when {
            quest.is_completed -> {
                b.tvQuestTitle.paintFlags = b.tvQuestTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.tvQuestTitle.setTextColor(Color.parseColor("#888888"))
                holder.itemView.alpha = 0.65f
                b.cbQuestDone.isChecked = true
                b.cbQuestDone.isEnabled = false
            }
            quest.is_failed -> {
                b.tvQuestTitle.paintFlags = b.tvQuestTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.tvQuestTitle.setTextColor(Color.parseColor("#FF4444"))
                holder.itemView.alpha = 0.55f
                b.cbQuestDone.isChecked = false
                b.cbQuestDone.isEnabled = false
            }
            else -> {
                b.tvQuestTitle.paintFlags = b.tvQuestTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                b.tvQuestTitle.setTextColor(Color.WHITE)
                holder.itemView.alpha = 1.0f
                b.cbQuestDone.isChecked = false
                b.cbQuestDone.isEnabled = true
            }
        }

        // --- Checkbox listener ---
        b.cbQuestDone.setOnCheckedChangeListener(null)
        b.cbQuestDone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !quest.is_completed && !quest.is_failed) {
                onQuestCompleted(quest)
            }
        }
    }

    // =========================================================
    //  Deadline display
    // =========================================================

    private fun bindDeadline(tv: TextView, quest: Quest) {
        if (quest.deadline <= 0L) {
            tv.text = "Tidak ada deadline"
            tv.setTextColor(Color.parseColor("#888888"))
            return
        }

        val now       = System.currentTimeMillis()
        val sdf       = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val formatted = sdf.format(Date(quest.deadline))

        when {
            quest.is_completed -> {
                tv.text = "✅ Selesai · $formatted"
                tv.setTextColor(Color.parseColor("#4CAF50"))
            }
            quest.is_failed -> {
                tv.text = "💀 Gagal · $formatted"
                tv.setTextColor(Color.parseColor("#FF4444"))
            }
            now > quest.deadline -> {
                val overdueMins = TimeUnit.MILLISECONDS.toMinutes(now - quest.deadline)
                tv.text = "⚠ Terlambat ${overdueMins}m · $formatted"
                tv.setTextColor(Color.parseColor("#FF4444"))
            }
            else -> {
                val remaining = quest.deadline - now
                val days  = TimeUnit.MILLISECONDS.toDays(remaining)
                val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
                val mins  = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60

                val timeLabel = when {
                    days  > 0 -> "Sisa $days hari"
                    hours > 0 -> "Sisa ${hours}j ${mins}m"
                    else      -> "Sisa ${mins} menit"
                }
                tv.text = "📅 $formatted · $timeLabel"
                tv.setTextColor(
                    if (days == 0L && hours < 3) Color.parseColor("#FF8800")
                    else Color.parseColor("#B0B0B0")
                )
            }
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================

    private fun getRankColor(rank: String): Int = when (rank) {
        "S"  -> Color.parseColor("#FFD700")
        "A"  -> Color.parseColor("#AA44FF")
        "B"  -> Color.parseColor("#2196F3")
        else -> Color.parseColor("#757575")
    }

    override fun getItemCount() = questList.size

    fun updateData(newList: List<Quest>) {
        questList = newList
        notifyDataSetChanged()
    }

    /** Untuk swipe-to-delete: ambil quest berdasarkan posisi */
    fun getQuestAt(position: Int): Quest = questList[position]

    /** Untuk MainActivity: cari rank berdasarkan quest ID */
    fun getRankForId(questId: String): String? =
        questList.find { it.id == questId }?.rank
}
