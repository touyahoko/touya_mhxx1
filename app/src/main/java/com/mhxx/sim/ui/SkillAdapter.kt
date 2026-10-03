package com.mhxx.sim.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mhxx.sim.databinding.ItemSkillBinding
import com.mhxx.sim.model.SkillActivation

class SkillAdapter(
    private val skills: List<SkillActivation>,
    private val selected: Set<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SkillAdapter.VH>() {

    class VH(val binding: ItemSkillBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSkillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sk = skills[position]
        holder.binding.tvSkillName.text = sk.name
        holder.binding.tvSkillInfo.text = "${sk.system} ${if (sk.points > 0) "+" else ""}${sk.points}"
        holder.binding.cbSkill.setOnCheckedChangeListener(null)
        holder.binding.cbSkill.isChecked = sk.name in selected
        holder.binding.cbSkill.setOnCheckedChangeListener { _, checked ->
            onToggle(sk.name, checked)
        }
        holder.binding.root.setOnClickListener {
            holder.binding.cbSkill.isChecked = !holder.binding.cbSkill.isChecked
        }
    }

    override fun getItemCount() = skills.size
}
