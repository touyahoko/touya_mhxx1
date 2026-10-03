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
import com.mhxx.sim.data.DataRepository
import com.mhxx.sim.databinding.FragmentCharmBinding
import com.mhxx.sim.model.OwnedCharm
import com.mhxx.sim.model.SkillPoint
import kotlinx.coroutines.launch

class CharmFragment : Fragment() {

    private var _binding: FragmentCharmBinding? = null
    private val binding get() = _binding!!
    private var systems: List<String> = emptyList()
    private var templates: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCharmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.spSlots.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf("スロ0", "スロ1", "スロ2", "スロ3")
        )

        lifecycleScope.launch {
            val data = DataRepository.get(requireContext())
            systems = data.systems.keys.sorted()
            templates = data.charmTemplates.map { it.name }
            binding.spTemplate.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, templates
            )
            val sysAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, systems)
            binding.etSkill1Sys.setAdapter(sysAdapter)
            binding.etSkill2Sys.setAdapter(sysAdapter)
            refreshList()
        }

        binding.btnAddCharm.setOnClickListener { addCharm() }
        binding.rvCharms.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun addCharm() {
        val act = requireActivity() as MainActivity
        val template = templates.getOrNull(binding.spTemplate.selectedItemPosition) ?: "兵士の護石"
        val sys1 = binding.etSkill1Sys.text.toString().trim()
        val val1 = binding.etSkill1Val.text.toString().toIntOrNull()
        if (sys1.isEmpty() || val1 == null) {
            Toast.makeText(requireContext(), "スキル1を入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        val sys2 = binding.etSkill2Sys.text.toString().trim()
        val val2 = binding.etSkill2Val.text.toString().toIntOrNull()
        val charm = OwnedCharm(
            template = template,
            skill1 = SkillPoint(sys1, val1),
            skill2 = if (sys2.isNotEmpty() && val2 != null) SkillPoint(sys2, val2) else null,
            slots = binding.spSlots.selectedItemPosition
        )
        act.ownedCharms.add(charm)
        act.persistSettings()
        refreshList()
        Toast.makeText(requireContext(), "登録しました", Toast.LENGTH_SHORT).show()
    }

    private fun refreshList() {
        val act = requireActivity() as MainActivity
        binding.rvCharms.adapter = SimpleStringAdapter(
            act.ownedCharms.map { it.displayName() }
        ) { idx ->
            if (idx in act.ownedCharms.indices) {
                act.ownedCharms.removeAt(idx)
                act.persistSettings()
                refreshList()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
