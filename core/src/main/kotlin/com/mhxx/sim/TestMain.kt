package com.mhxx.sim

import com.mhxx.sim.model.*
import com.mhxx.sim.parser.CsvParser
import com.mhxx.sim.engine.SearchEngine
import java.io.File

fun main() {
    val dataDir = File("app/src/main/assets/data")
    println("=== MHXX GanSimu Core Test (Full Features) ===")
    val data = CsvParser.parse(
        skillsIn = File(dataDir, "MHXX_SKILL.csv").inputStream(),
        headIn = File(dataDir, "MHXX_EQUIP_HEAD.csv").inputStream(),
        bodyIn = File(dataDir, "MHXX_EQUIP_BODY.csv").inputStream(),
        armIn = File(dataDir, "MHXX_EQUIP_ARM.csv").inputStream(),
        waistIn = File(dataDir, "MHXX_EQUIP_WST.csv").inputStream(),
        legIn = File(dataDir, "MHXX_EQUIP_LEG.csv").inputStream(),
        decoIn = File(dataDir, "MHXX_DECO.csv").inputStream(),
        charmIn = File(dataDir, "MHXX_CHARM.csv").inputStream(),
        fukugoIn = File(dataDir, "conf/FUKUGO.txt").inputStream(),
        categoryIn = File(dataDir, "conf/CATEGORY.txt").inputStream()
    )
    println("Skills=${data.skills.size} Equips H/B/A/W/L=${data.equipments[EquipPart.HEAD]?.size}/${data.equipments[EquipPart.BODY]?.size}/${data.equipments[EquipPart.ARM]?.size}/${data.equipments[EquipPart.WAIST]?.size}/${data.equipments[EquipPart.LEG]?.size}")
    println("Decos=${data.decorations.size} Charms=${data.charmTemplates.size} Composites=${data.composites.size}")

    val engine = SearchEngine(data)

    // Test 1: Normal search
    println("\n--- Test1: Normal Search ---")
    val cond1 = SearchCondition(
        requiredSkills = listOf("攻撃力UP【大】", "見切り+3"),
        hunterType = HunterType.SWORD,
        weaponSlots = 3,
        maxResults = 5
    )
    var t = System.currentTimeMillis()
    var r = engine.search(cond1)
    println("Found ${r.sets.size} in ${System.currentTimeMillis()-t}ms")
    r.sets.take(2).forEachIndexed { i, s ->
        println("  #$i def=${s.totalDefense} skills=${s.activatedSkills} torso=${s.isTorsoUp}")
    }

    // Test 2: Additional skills
    if (r.sets.isNotEmpty()) {
        println("\n--- Test2: Additional Skills ---")
        val add = engine.findAdditionalSkills(r.sets[0], HunterType.SWORD, 8)
        println("Additional: ${add.take(5).joinToString { "${it.skillName}(+${it.deficit})" }}")
    }

    // Test 3: Summarize
    println("\n--- Test3: Summarize ---")
    val cond3 = SearchCondition(
        requiredSkills = listOf("攻撃力UP【大】"),
        hunterType = HunterType.SWORD,
        weaponSlots = 3,
        maxResults = 30,
        summarize = true
    )
    t = System.currentTimeMillis()
    r = engine.search(cond3)
    println("Sets=${r.sets.size} Summarized=${r.summarized.size} in ${System.currentTimeMillis()-t}ms")
    r.summarized.take(3).forEach { g -> println("  ${g.label}") }

    // Test 4: Fixed equip
    println("\n--- Test4: Fixed Equip ---")
    val someHead = data.equipments[EquipPart.HEAD]?.firstOrNull { it.skills.any { s -> s.system == "攻撃" } }
    if (someHead != null) {
        val cond4 = SearchCondition(
            requiredSkills = listOf("攻撃力UP【中】"),
            hunterType = HunterType.SWORD,
            weaponSlots = 3,
            fixedEquip = mapOf(EquipPart.HEAD to someHead.name),
            maxResults = 3
        )
        r = engine.search(cond4)
        println("Fixed head=${someHead.name} -> ${r.sets.size} results")
        r.sets.forEach { println("  head=${it.head?.name}") }
    }

    // Test 5: Exclude
    println("\n--- Test5: Exclude ---")
    val excludeName = r.sets.firstOrNull()?.body?.name
    if (excludeName != null) {
        val cond5 = SearchCondition(
            requiredSkills = listOf("攻撃力UP【中】"),
            hunterType = HunterType.SWORD,
            weaponSlots = 3,
            excludeEquip = setOf(excludeName),
            maxResults = 3
        )
        r = engine.search(cond5)
        println("Excluded $excludeName -> ${r.sets.size} results, bodies=${r.sets.map { it.body?.name }}")
    }

    // Test 6: Owned charm
    println("\n--- Test6: Owned Charm ---")
    val charm = OwnedCharm(
        template = "兵士の護石",
        skill1 = SkillPoint("攻撃", 5),
        skill2 = SkillPoint("達人", 3),
        slots = 1
    )
    val cond6 = SearchCondition(
        requiredSkills = listOf("攻撃力UP【大】", "見切り+1"),
        hunterType = HunterType.SWORD,
        weaponSlots = 2,
        ownedCharms = listOf(charm),
        maxResults = 3
    )
    t = System.currentTimeMillis()
    r = engine.search(cond6)
    println("With charm -> ${r.sets.size} in ${System.currentTimeMillis()-t}ms")
    r.sets.take(2).forEach { println("  charm=${it.charm?.displayName()} skills=${it.activatedSkills}") }

    // Test 7: Virtual charm (impossible skills to force virtual)
    println("\n--- Test7: Virtual Charm ---")
    val cond7 = SearchCondition(
        requiredSkills = listOf("攻撃力UP【大】", "見切り+3", "耳栓"),
        hunterType = HunterType.SWORD,
        weaponSlots = 0,
        searchVirtualCharms = true,
        ownedCharms = emptyList(),
        maxResults = 2
    )
    t = System.currentTimeMillis()
    r = engine.search(cond7)
    println("Normal=${r.sets.size} Virtual=${r.virtualSets.size} in ${System.currentTimeMillis()-t}ms")
    r.virtualSets.take(2).forEach { println("  vcharm=${it.virtualCharm?.displayName()} skills=${it.activatedSkills}") }

    println("\n=== ALL TESTS DONE ===")
}
