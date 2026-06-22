package com.example.questkampus.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.questkampus.R
import com.example.questkampus.data.model.Party

class PartyAdapter(
    private var partyList: List<Party>,
    private val onPartyClick: (Party) -> Unit
) : RecyclerView.Adapter<PartyAdapter.PartyViewHolder>() {

    class PartyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_item_party_name)
        val tvDesc: TextView = view.findViewById(R.id.tv_item_party_desc)
        val tvCount: TextView = view.findViewById(R.id.tv_item_party_member_count)
        val pbHp: ProgressBar = view.findViewById(R.id.pb_item_party_hp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_party, parent, false)
        return PartyViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = partyList[position]
        holder.tvName.text = "⚔ ${party.party_name}"
        holder.tvDesc.text = if (party.description.isNotEmpty()) party.description else "Tidak ada deskripsi."
        holder.tvCount.text = "👥 ${party.members.size}"

        holder.pbHp.max = party.max_hp
        holder.pbHp.progress = party.party_hp

        holder.itemView.setOnClickListener {
            onPartyClick(party)
        }
    }

    override fun getItemCount() = partyList.size

    fun updateData(newList: List<Party>) {
        partyList = newList
        notifyDataSetChanged()
    }
}