package com.example.questkampus

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
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
    private val onQuestClick: (Quest) -> Unit
) : RecyclerView.Adapter<QuestAdapter.QuestViewHolder>() {

    inner class QuestViewHolder(val binding: ItemQuestBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onQuestClick(questList[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder =
        QuestViewHolder(ItemQuestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        val quest = questList[position]
        val b     = holder.binding
        val ctx   = holder.itemView.context

        // --- Rank Badge ---
        b.tvQuestRank.text = quest.rank
        b.tvQuestRank.background = ctx.getDrawable(when(quest.rank.uppercase()) {
            "S"  -> R.drawable.bg_rank_s
            "A"  -> R.drawable.bg_rank_a
            "B"  -> R.drawable.bg_rank_b
            else -> R.drawable.bg_rank_c
        })
        b.tvQuestRank.setTextColor(if (quest.rank == "C") Color.WHITE else Color.BLACK)

        // --- Title ---
        b.tvQuestTitle.text = quest.title

        // --- RPG Flavor ---
        b.tvQuestFlavor.text = "${RpgTheme.rankIcon(quest.rank)} ${RpgTheme.rankTitle(quest.rank)}"

        // --- Deadline ---
        bindDeadline(b.tvQuestDeadline, quest)

        // --- Status Badge ---
        b.tvStatusBadge.visibility = View.VISIBLE
        when {
            quest.is_completed -> {
                b.tvStatusBadge.text = "SELESAI"
                b.tvStatusBadge.setBackgroundColor(Color.parseColor("#4CAF50"))
                b.tvStatusBadge.setTextColor(Color.WHITE)
                b.tvQuestTitle.paintFlags = b.tvQuestTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.tvQuestTitle.setTextColor(Color.parseColor("#888888"))
                holder.itemView.alpha = 0.7f
            }
            quest.is_failed -> {
                b.tvStatusBadge.text = "GAGAL"
                b.tvStatusBadge.setBackgroundColor(Color.parseColor("#FF4444"))
                b.tvStatusBadge.setTextColor(Color.WHITE)
                b.tvQuestTitle.paintFlags = b.tvQuestTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.tvQuestTitle.setTextColor(Color.parseColor("#FF8888"))
                holder.itemView.alpha = 0.7f
            }
            else -> {
                b.tvStatusBadge.text = "AKTIF"
                b.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFD700"))
                b.tvStatusBadge.setTextColor(Color.BLACK)
                b.tvQuestTitle.paintFlags = b.tvQuestTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                b.tvQuestTitle.setTextColor(Color.WHITE)
                holder.itemView.alpha = 1.0f
            }
        }
    }

    private fun bindDeadline(tv: TextView, quest: Quest) {
        if (quest.deadline <= 0L) {
            tv.text = "Tidak ada deadline"; tv.setTextColor(Color.parseColor("#888888")); return
        }
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val fmt = sdf.format(Date(quest.deadline))
        when {
            quest.is_completed -> { tv.text = "✅ $fmt"; tv.setTextColor(Color.parseColor("#4CAF50")) }
            quest.is_failed    -> { tv.text = "💀 $fmt";   tv.setTextColor(Color.parseColor("#FF4444")) }
            now > quest.deadline -> {
                tv.text = "⚠ Terlambat · $fmt"
                tv.setTextColor(Color.parseColor("#FF4444"))
            }
            else -> {
                val r = quest.deadline - now
                val d = TimeUnit.MILLISECONDS.toDays(r)
                val h = TimeUnit.MILLISECONDS.toHours(r) % 24
                val m = TimeUnit.MILLISECONDS.toMinutes(r) % 60
                tv.text = "📅 $fmt · ${if (d > 0) "Sisa $d hari" else if (h > 0) "Sisa ${h}j ${m}m" else "Sisa ${m}m"}"
                tv.setTextColor(if (d == 0L && h < 3) Color.parseColor("#FF8800") else Color.parseColor("#B0B0B0"))
            }
        }
    }

    override fun getItemCount() = questList.size

    fun updateData(newList: List<Quest>) {
        questList = newList
        notifyDataSetChanged()
    }

    fun getQuestAt(position: Int): Quest = questList[position]

    fun getRankForId(questId: String): String? = questList.find { it.id == questId }?.rank
}