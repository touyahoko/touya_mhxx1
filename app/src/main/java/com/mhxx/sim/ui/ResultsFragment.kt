package com.mhxx.sim.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mhxx.sim.data.DataRepository
import com.mhxx.sim.databinding.FragmentResultsBinding
import com.mhxx.sim.engine.SearchEngine
import com.mhxx.sim.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())

        val act = requireActivity() as MainActivity
        binding.cbSummarize.isChecked = act.summarize
        binding.cbVirtual.isChecked = act.searchVirtualCharms
        binding.cbSummarize.setOnCheckedChangeListener { _, checked -> act.summarize = checked }
        binding.cbVirtual.setOnCheckedChangeListener { _, checked -> act.searchVirtualCharms = checked }
    }

    fun showLoading() {
        _binding?.let {
            it.progress.visibility = View.VISIBLE
            it.tvEmpty.visibility = View.GONE
            it.rvResults.visibility = View.GONE
            it.tvResultInfo.text = "検索中…"
        }
    }

    fun showResults(
        results: List<EquipSet>,
        virtual: List<EquipSet>,
        summarized: List<SummarizedGroup>
    ) {
        _binding?.let { b ->
            b.progress.visibility = View.GONE
            val act = requireActivity() as MainActivity
            val displayList = when {
                summarized.isNotEmpty() -> summarized.map { it.representative }
                results.isNotEmpty() -> results
                virtual.isNotEmpty() -> virtual
                else -> emptyList()
            }
            val info = buildString {
                if (results.isNotEmpty()) append("${results.size}件")
                if (virtual.isNotEmpty()) append(" / 仮想護石${virtual.size}件")
                if (summarized.isNotEmpty()) append(" / まとめ${summarized.size}グループ")
                if (isEmpty()) append("結果なし")
            }
            b.tvResultInfo.text = info

            if (displayList.isEmpty()) {
                b.tvEmpty.visibility = View.VISIBLE
                b.rvResults.visibility = View.GONE
            } else {
                b.tvEmpty.visibility = View.GONE
                b.rvResults.visibility = View.VISIBLE
                b.rvResults.adapter = ResultAdapter(displayList, summarized) { set, action ->
                    when (action) {
                        "myset" -> promptAddMySet(set)
                        "additional" -> showAdditionalSkills(set)
                        "exclude" -> {
                            listOfNotNull(set.head, set.body, set.arm, set.waist, set.leg).forEach {
                                act.excludeEquip.add(it.name)
                            }
                            act.persistSettings()
                            Toast.makeText(requireContext(), "除外に追加しました", Toast.LENGTH_SHORT).show()
                        }
                        "expand" -> {
                            val group = summarized.find { it.representative === set || it.variants.contains(set) }
                            if (group != null) {
                                b.rvResults.adapter = ResultAdapter(group.variants, emptyList()) { s, a ->
                                    when (a) {
                                        "myset" -> promptAddMySet(s)
                                        "additional" -> showAdditionalSkills(s)
                                    }
                                }
                                b.tvResultInfo.text = "展開: ${group.label}"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun promptAddMySet(set: EquipSet) {
        val input = EditText(requireContext()).apply { hint = "セット名" }
        AlertDialog.Builder(requireContext())
            .setTitle("マイセットに追加")
            .setView(input)
            .setPositiveButton("追加") { _, _ ->
                val name = input.text.toString().ifBlank { "セット${System.currentTimeMillis() % 10000}" }
                (requireActivity() as MainActivity).addToMySet(set, name)
                Toast.makeText(requireContext(), "追加しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showAdditionalSkills(set: EquipSet) {
        val act = requireActivity() as MainActivity
        lifecycleScope.launch {
            val data = DataRepository.get(requireContext())
            val skills = withContext(Dispatchers.Default) {
                SearchEngine(data).findAdditionalSkills(set, act.lastHunterType, 10)
            }
            if (skills.isEmpty()) {
                Toast.makeText(requireContext(), "追加可能なスキルなし", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = skills.map { "${it.skillName} (あと${it.deficit}pt / 現在${it.currentPoints})" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("追加スキル検索")
                .setItems(labels) { _, which ->
                    val sk = skills[which]
                    // スキル選択タブに追加
                    Toast.makeText(requireContext(), "${sk.skillName} を選択に追加可能です", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("閉じる", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
