package com.mhxx.sim.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.mhxx.sim.data.DataRepository
import com.mhxx.sim.databinding.FragmentSkillSelectBinding
import com.mhxx.sim.engine.SearchEngine
import com.mhxx.sim.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SkillSelectFragment : Fragment() {

    private var _binding: FragmentSkillSelectBinding? = null
    private val binding get() = _binding!!
    private var gameData: GameData? = null
    private val selectedSkills = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSkillSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.spWeaponSlots.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf("0", "1", "2", "3")
        )
        binding.spWeaponSlots.setSelection(3)

        lifecycleScope.launch {
            gameData = DataRepository.get(requireContext())
            setupCategories()
        }
        binding.btnSearch.setOnClickListener { doSearch() }
    }

    private fun setupCategories() {
        val data = gameData ?: return
        val cats = data.categories.keys.toList()
        binding.spCategory.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, cats
        )
        if (cats.isEmpty()) return
        binding.spCategory.setSelection(0)
        binding.spCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                loadSkills(cats[pos])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        binding.rvSkills.layoutManager = LinearLayoutManager(requireContext())
        loadSkills(cats[0])
    }

    private fun loadSkills(cat: String) {
        val data = gameData ?: return
        val skillNames = data.categories[cat] ?: emptyList()
        val skills = skillNames.mapNotNull { data.skillByName[it] }
        binding.rvSkills.adapter = SkillAdapter(skills, selectedSkills) { name, checked ->
            if (checked) selectedSkills.add(name) else selectedSkills.remove(name)
            refreshChips()
        }
    }

    private fun refreshChips() {
        binding.chipSelectedSkills.removeAllViews()
        for (name in selectedSkills) {
            val chip = Chip(requireContext()).apply {
                text = name
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    selectedSkills.remove(name)
                    refreshChips()
                    binding.rvSkills.adapter?.notifyDataSetChanged()
                }
            }
            binding.chipSelectedSkills.addView(chip)
        }
    }

    private fun doSearch() {
        if (selectedSkills.isEmpty()) {
            Toast.makeText(requireContext(), "スキルを選択してください", Toast.LENGTH_SHORT).show()
            return
        }
        val data = gameData ?: return
        val act = requireActivity() as MainActivity
        val hunterType = if (binding.rbSword.isChecked) HunterType.SWORD else HunterType.GUNNER
        act.lastHunterType = hunterType

        val cond = SearchCondition(
            requiredSkills = selectedSkills.toList(),
            hunterType = hunterType,
            weaponSlots = binding.spWeaponSlots.selectedItemPosition,
            excludeEquip = act.excludeEquip,
            fixedEquip = act.fixedEquip.toMap(),
            excludeDeco = act.excludeDeco,
            ownedCharms = act.ownedCharms.toList(),
            searchVirtualCharms = act.searchVirtualCharms,
            useExcludeFixed = act.useExcludeFixed,
            maxResults = 50,
            summarize = act.summarize
        )

        act.onSearchStarted()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                SearchEngine(data).search(cond)
            }
            act.onSearchFinished(result.sets, result.virtualSets, result.summarized)
        }
    }

    /** 追加スキル検索結果からスキルを選択状態にする */
    fun addSkill(name: String) {
        selectedSkills.add(name)
        refreshChips()
        binding.rvSkills.adapter?.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
