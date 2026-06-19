package com.example.questkampus

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
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
    private val onQuestCompleted: (Quest) -> Unit
) : RecyclerView.Adapter<QuestAdapter.QuestViewHolder>() {

    inner class QuestViewHolder(val binding: ItemQuestBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder =
        QuestViewHolder(ItemQuestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        val quest = questList[position]
        val b     = holder.binding
        val ctx   = holder.itemView.context

        // --- Rank Badge ---
        b.tvQuestRank.text = quest.rank
        b.tvQuestRank.background = ctx.getDrawable(when(quest.rank) {
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

        // --- Support link ---
        if (quest.support_link.isNotEmpty()) {
            b.tvSupportLink.visibility = View.VISIBLE
            b.tvSupportLink.text = "🔗 File Soal/Pendukung"
            b.tvSupportLink.setOnClickListener {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(quest.support_link)))
            }
        } else if (quest.support_file_url.isNotEmpty()) {
            b.tvSupportLink.visibility = View.VISIBLE
            b.tvSupportLink.text = "📁 Lihat File Soal"
            b.tvSupportLink.setOnClickListener {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(quest.support_file_url)))
            }
        } else {
            b.tvSupportLink.visibility = View.GONE
        }

        // --- Proof indicator (setelah selesai) ---
        if (quest.is_completed) {
            val proofUrl = quest.proof_link.ifEmpty { quest.attachment_url }
            if (proofUrl.isNotEmpty()) {
                b.tvProofIndicator.visibility = View.VISIBLE
                b.tvProofIndicator.text = "📎 Lihat Bukti Penyelesaian"
                b.tvProofIndicator.setOnClickListener {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proofUrl)))
                }
            } else {
                b.tvProofIndicator.visibility = View.GONE
            }
        } else {
            b.tvProofIndicator.visibility = View.GONE
        }

        // --- State: selesai / gagal / aktif ---
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

        // --- Checkbox ---
        b.cbQuestDone.setOnCheckedChangeListener(null)
        b.cbQuestDone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !quest.is_completed && !quest.is_failed) {
                onQuestCompleted(quest)
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
            quest.is_completed -> { tv.text = "✅ Selesai · $fmt"; tv.setTextColor(Color.parseColor("#4CAF50")) }
            quest.is_failed    -> { tv.text = "💀 Gagal · $fmt";   tv.setTextColor(Color.parseColor("#FF4444")) }
            now > quest.deadline -> {
                tv.text = "⚠ Terlambat ${TimeUnit.MILLISECONDS.toMinutes(now - quest.deadline)}m · $fmt"
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
