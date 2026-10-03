package com.mhxx.sim.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mhxx.sim.databinding.FragmentMysetBinding
import com.mhxx.sim.databinding.ItemResultBinding
import com.mhxx.sim.model.MySet

class MySetFragment : Fragment() {

    private var _binding: FragmentMysetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMysetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvMySets.layoutManager = LinearLayoutManager(requireContext())
        refresh()
    }

    fun refresh() {
        _binding ?: return
        val act = activity as? MainActivity ?: return
        val sets = act.store.loadMySets()
        if (sets.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvMySets.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvMySets.visibility = View.VISIBLE
            binding.rvMySets.adapter = MySetAdapter(sets) { mySet ->
                AlertDialog.Builder(requireContext())
                    .setTitle(mySet.name)
                    .setMessage(buildDetail(mySet))
                    .setPositiveButton("削除") { _, _ ->
                        act.store.removeMySet(mySet.id)
                        refresh()
                    }
                    .setNegativeButton("閉じる", null)
                    .show()
            }
        }
    }

    private fun buildDetail(s: MySet): String = buildString {
        append("頭: ${s.head ?: "─"}\n")
        append("胴: ${s.body ?: "─"}\n")
        append("腕: ${s.arm ?: "─"}\n")
        append("腰: ${s.waist ?: "─"}\n")
        append("脚: ${s.leg ?: "─"}\n")
        if (s.decorations.isNotEmpty()) append("装飾品: ${s.decorations.joinToString()}\n")
        s.charmDisplay?.let { append("護石: $it\n") }
        append("防御: ${s.totalDefense}\n")
        append("発動: ${s.activatedSkills.joinToString(", ")}")
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MySetAdapter(
    private val sets: List<MySet>,
    private val onClick: (MySet) -> Unit
) : RecyclerView.Adapter<MySetAdapter.VH>() {
    class VH(val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = sets[position]
        holder.binding.tvResultTitle.text = s.name
        holder.binding.tvEquips.text = "頭:${s.head ?: "─"} 胴:${s.body ?: "─"} 腕:${s.arm ?: "─"}\n腰:${s.waist ?: "─"} 脚:${s.leg ?: "─"}"
        holder.binding.tvDecos.text = s.charmDisplay ?: ""
        holder.binding.tvSkills.text = "発動: ${s.activatedSkills.joinToString(", ")}"
        holder.binding.tvStats.text = "防御${s.totalDefense}"
        holder.binding.root.setOnClickListener { onClick(s) }
    }
    override fun getItemCount() = sets.size
}
