package com.mhxx.sim.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.mhxx.sim.databinding.ItemResultBinding
import com.mhxx.sim.model.EquipSet
import com.mhxx.sim.model.SummarizedGroup

class ResultAdapter(
    private val results: List<EquipSet>,
    private val summarized: List<SummarizedGroup> = emptyList(),
    private val onAction: (EquipSet, String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<ResultAdapter.VH>() {

    class VH(val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val set = results[position]
        val group = summarized.find { it.representative === set }

        val title = buildString {
            append("#${position + 1}  防御${set.totalDefense}")
            if (set.isTorsoUp) append(" 【胴倍】")
            if (set.virtualCharm != null) append(" 【仮想護石】")
            if (group != null && group.variants.size > 1) append(" (${group.variants.size}件)")
        }
        holder.binding.tvResultTitle.text = title

        val equips = buildString {
            append("頭: ${set.head?.name ?: "─"}\n")
            append("胴: ${set.body?.name ?: "─"}\n")
            append("腕: ${set.arm?.name ?: "─"}\n")
            append("腰: ${set.waist?.name ?: "─"}\n")
            append("脚: ${set.leg?.name ?: "─"}")
        }
        holder.binding.tvEquips.text = equips

        val decoText = when {
            set.decorations.isEmpty() && set.virtualCharm == null && set.charm == null -> "装飾品: なし"
            else -> buildString {
                if (set.decorations.isNotEmpty()) {
                    append("装飾品: ")
                    append(set.decorations.groupingBy { it.name }.eachCount()
                        .entries.joinToString { "${it.key}×${it.value}" })
                }
                set.charm?.let {
                    if (isNotEmpty()) append("\n")
                    append("護石: ${it.displayName()}")
                }
                set.virtualCharm?.let {
                    if (isNotEmpty()) append("\n")
                    append(it.displayName())
                }
            }
        }
        holder.binding.tvDecos.text = decoText
        holder.binding.tvSkills.text = "発動: " + set.activatedSkills.joinToString(", ")
        val res = set.totalRes.entries.joinToString(" ") { "${it.key}${if (it.value >= 0) "+" else ""}${it.value}" }
        holder.binding.tvStats.text = "耐性: $res"

        holder.binding.root.setOnLongClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menu.add("マイセットに追加").setOnMenuItemClickListener {
                onAction(set, "myset"); true
            }
            popup.menu.add("追加スキル検索").setOnMenuItemClickListener {
                onAction(set, "additional"); true
            }
            popup.menu.add("この装備を除外").setOnMenuItemClickListener {
                onAction(set, "exclude"); true
            }
            if (group != null && group.variants.size > 1) {
                popup.menu.add("展開する").setOnMenuItemClickListener {
                    onAction(set, "expand"); true
                }
            }
            popup.show()
            true
        }
    }

    override fun getItemCount() = results.size
}
