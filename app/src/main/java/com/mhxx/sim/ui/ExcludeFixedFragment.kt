package com.mhxx.sim.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mhxx.sim.data.DataRepository
import com.mhxx.sim.databinding.FragmentExcludeFixedBinding
import com.mhxx.sim.model.EquipPart
import com.mhxx.sim.model.GameData
import kotlinx.coroutines.launch

class ExcludeFixedFragment : Fragment() {

    private var _binding: FragmentExcludeFixedBinding? = null
    private val binding get() = _binding!!
    private var gameData: GameData? = null
    private var allNames: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExcludeFixedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val act = requireActivity() as MainActivity

        binding.cbUseExcludeFixed.isChecked = act.useExcludeFixed
        binding.cbUseExcludeFixed.setOnCheckedChangeListener { _, c ->
            act.useExcludeFixed = c
        }

        binding.spPart.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            EquipPart.entries.map { it.label }
        )

        lifecycleScope.launch {
            gameData = DataRepository.get(requireContext())
            allNames = gameData!!.allEquipNames().map { it.second }.distinct().sorted()
            binding.etEquipName.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allNames)
            )
            refreshLists()
        }

        binding.btnFix.setOnClickListener {
            val name = binding.etEquipName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            val part = EquipPart.entries[binding.spPart.selectedItemPosition]
            // 存在確認
            val exists = gameData?.findEquip(part, name) != null
            if (!exists) {
                // 他部位も探す
                val found = EquipPart.entries.firstOrNull { gameData?.findEquip(it, name) != null }
                if (found == null) {
                    Toast.makeText(requireContext(), "装備が見つかりません", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                act.fixedEquip[found] = name
            } else {
                act.fixedEquip[part] = name
            }
            act.persistSettings()
            refreshLists()
            Toast.makeText(requireContext(), "固定しました", Toast.LENGTH_SHORT).show()
        }

        binding.btnExclude.setOnClickListener {
            val name = binding.etEquipName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            act.excludeEquip.add(name)
            act.persistSettings()
            refreshLists()
        }

        binding.btnClearAll.setOnClickListener {
            act.fixedEquip.clear()
            act.excludeEquip.clear()
            act.excludeDeco.clear()
            act.persistSettings()
            refreshLists()
        }

        binding.rvFixed.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExclude.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun refreshLists() {
        val act = requireActivity() as MainActivity
        binding.rvFixed.adapter = SimpleStringAdapter(
            act.fixedEquip.map { "${it.key.label}: ${it.value}" }
        ) { idx ->
            val key = act.fixedEquip.keys.elementAtOrNull(idx) ?: return@SimpleStringAdapter
            act.fixedEquip.remove(key)
            act.persistSettings()
            refreshLists()
        }
        binding.rvExclude.adapter = SimpleStringAdapter(act.excludeEquip.toList().sorted()) { idx ->
            val name = act.excludeEquip.toList().sorted().getOrNull(idx) ?: return@SimpleStringAdapter
            act.excludeEquip.remove(name)
            act.persistSettings()
            refreshLists()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SimpleStringAdapter(
    private val items: List<String>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<SimpleStringAdapter.VH>() {
    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            setPadding(16, 12, 16, 12)
            textSize = 14f
        }
        return VH(tv)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = "✕  ${items[position]}"
        holder.tv.setOnClickListener { onRemove(position) }
    }
    override fun getItemCount() = items.size
}
