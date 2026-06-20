package com.example.questkampus

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MemberAdapter(private var memberList: List<Member>) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.iv_member_avatar)
        val tvName: TextView = view.findViewById(R.id.tv_member_name)
        val tvLevel: TextView = view.findViewById(R.id.tv_member_level)
        val pbHp: ProgressBar = view.findViewById(R.id.pb_member_hp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = memberList[position]
        holder.tvName.text = member.name
        holder.tvLevel.text = "⚔ Level ${member.level}"

        holder.pbHp.max = member.maxHp
        holder.pbHp.progress = member.hp

        if (member.avatar_url.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(member.avatar_url)
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivAvatar)
        }
    }

    override fun getItemCount() = memberList.size

    fun updateData(newList: List<Member>) {
        memberList = newList
        notifyDataSetChanged()
    }
}