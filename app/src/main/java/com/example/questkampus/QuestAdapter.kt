package com.example.questkampus

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuestAdapter(
    private var questList: List<Quest>,
    private val onQuestCompleted: (Quest) -> Unit
) : RecyclerView.Adapter<QuestAdapter.QuestViewHolder>() {

    class QuestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_quest_title)
        val tvRank: TextView = view.findViewById(R.id.tv_quest_rank)
        val cbComplete: CheckBox = view.findViewById(R.id.cb_quest_done)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quest, parent, false)
        return QuestViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        val quest = questList[position]
        holder.tvTitle.text = quest.title
        holder.tvRank.text = quest.rank

        holder.cbComplete.setOnCheckedChangeListener(null)
        holder.cbComplete.isChecked = quest.is_completed
        holder.cbComplete.isEnabled = !quest.is_completed

        holder.cbComplete.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !quest.is_completed) {
                onQuestCompleted(quest)
            }
        }
    }

    override fun getItemCount() = questList.size

    fun updateData(newList: List<Quest>) {
        questList = newList
        notifyDataSetChanged()
    }
}