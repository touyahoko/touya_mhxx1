package com.mhxx.sim.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.mhxx.sim.data.MySetStore
import com.mhxx.sim.databinding.ActivityMainBinding
import com.mhxx.sim.model.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var store: MySetStore

    private val skillFragment = SkillSelectFragment()
    private val resultsFragment = ResultsFragment()
    private val excludeFragment = ExcludeFixedFragment()
    private val charmFragment = CharmFragment()
    private val mySetFragment = MySetFragment()

    var lastResults: List<EquipSet> = emptyList()
    var lastVirtualResults: List<EquipSet> = emptyList()
    var lastSummarized: List<SummarizedGroup> = emptyList()
    var lastHunterType: HunterType = HunterType.SWORD
    var isSearching = false

    // 共有設定
    var excludeEquip: MutableSet<String> = mutableSetOf()
    var fixedEquip: MutableMap<EquipPart, String> = mutableMapOf()
    var excludeDeco: MutableSet<String> = mutableSetOf()
    var ownedCharms: MutableList<OwnedCharm> = mutableListOf()
    var useExcludeFixed = true
    var searchVirtualCharms = true
    var summarize = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        store = MySetStore(this)
        loadSettings()

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> skillFragment
                1 -> resultsFragment
                2 -> excludeFragment
                3 -> charmFragment
                else -> mySetFragment
            }
        }
        binding.viewPager.offscreenPageLimit = 4

        val titles = listOf("スキル", "結果", "除外/固定", "護石", "マイセット")
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = titles[pos]
        }.attach()
    }

    private fun loadSettings() {
        excludeEquip = store.loadExcludeEquip().toMutableSet()
        excludeDeco = store.loadExcludeDeco().toMutableSet()
        ownedCharms = store.loadCharms().toMutableList()
        val fixed = store.loadFixedEquip()
        fixedEquip.clear()
        for ((k, v) in fixed) {
            try { fixedEquip[EquipPart.valueOf(k)] = v } catch (_: Exception) {}
        }
    }

    fun persistSettings() {
        store.saveExcludeEquip(excludeEquip)
        store.saveExcludeDeco(excludeDeco)
        store.saveCharms(ownedCharms)
        store.saveFixedEquip(fixedEquip.mapKeys { it.key.name })
    }

    fun onSearchStarted() {
        isSearching = true
        binding.viewPager.currentItem = 1
        resultsFragment.showLoading()
    }

    fun onSearchFinished(
        results: List<EquipSet>,
        virtual: List<EquipSet>,
        summarized: List<SummarizedGroup>
    ) {
        isSearching = false
        lastResults = results
        lastVirtualResults = virtual
        lastSummarized = summarized
        resultsFragment.showResults(results, virtual, summarized)
    }

    fun addToMySet(set: EquipSet, name: String) {
        val mySet = MySet(
            name = name,
            head = set.head?.name,
            body = set.body?.name,
            arm = set.arm?.name,
            waist = set.waist?.name,
            leg = set.leg?.name,
            decorations = set.decorations.map { it.name },
            charmDisplay = set.charm?.displayName() ?: set.virtualCharm?.displayName(),
            activatedSkills = set.activatedSkills,
            totalDefense = set.totalDefense
        )
        store.addMySet(mySet)
        mySetFragment.refresh()
    }
}
