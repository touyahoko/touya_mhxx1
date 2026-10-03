package com.mhxx.sim.engine

import com.mhxx.sim.model.*

class SearchEngine(private val data: GameData) {

    private val skillEngine = SkillEngine(data)

    fun search(cond: SearchCondition): SearchResult {
        val reqMap = skillEngine.buildRequirementMap(cond.requiredSkills)
        if (reqMap.isEmpty() && cond.requiredSkills.isNotEmpty()) {
            return SearchResult(emptyList(), emptyList(), emptyList())
        }

        val requiredSystems = reqMap.keys
        val maxResults = cond.maxResults.coerceIn(1, 500)

        val excludeEquip = if (cond.useExcludeFixed) cond.excludeEquip else emptySet()
        val fixedEquip = if (cond.useExcludeFixed) cond.fixedEquip else emptyMap()
        val excludeDeco = if (cond.useExcludeFixed) cond.excludeDeco else emptySet()

        val effectiveCond = cond.copy(
            excludeEquip = excludeEquip,
            fixedEquip = fixedEquip,
            excludeDeco = excludeDeco
        )

        val topN = when {
            cond.requiredSkills.size >= 4 -> 25
            cond.requiredSkills.size >= 3 -> 40
            else -> 60
        }
        val candidates = EquipPart.entries.associateWith { part ->
            val fixedName = fixedEquip[part]
            if (fixedName != null) {
                data.equipments[part]?.filter { it.name == fixedName } ?: emptyList()
            } else {
                filterAndRank(part, effectiveCond, requiredSystems, topN = topN)
            }
        }

        val results = mutableListOf<EquipSet>()
        val current = arrayOfNulls<Equipment>(5)
        val explored = intArrayOf(0)
        val maxExplore = when {
            cond.requiredSkills.size >= 3 -> 50000
            else -> 200000
        }

        val maxContrib = EquipPart.entries.map { part ->
            val cans = candidates[part] ?: emptyList()
            requiredSystems.associateWith { sys ->
                cans.maxOfOrNull { eq -> eq.skills.filter { it.system == sys }.sumOf { it.value } } ?: 0
            }
        }

        dfs(0, candidates, current, reqMap, requiredSystems, maxContrib, effectiveCond, results, maxResults, explored, maxExplore)

        var sorted = results.sortedWith(
            compareBy<EquipSet> { it.emptyParts }
                .thenByDescending { it.totalDefense }
                .thenByDescending { it.totalRes.values.sum() }
        )

        // 仮想護石検索: 通常結果が0件のとき
        val virtualResults = mutableListOf<EquipSet>()
        if (sorted.isEmpty() && cond.searchVirtualCharms && cond.ownedCharms.isEmpty()) {
            virtualResults.addAll(searchWithVirtualCharms(effectiveCond, reqMap, candidates, maxContrib, maxResults))
        }

        // まとめ装備検索
        val summarized = if (cond.summarize && sorted.isNotEmpty()) {
            summarizeResults(sorted)
        } else emptyList()

        if (cond.summarize && summarized.isNotEmpty()) {
            sorted = summarized.map { it.representative }
        }

        return SearchResult(sorted, virtualResults, summarized)
    }

    data class SearchResult(
        val sets: List<EquipSet>,
        val virtualSets: List<EquipSet>,
        val summarized: List<SummarizedGroup>
    )

    private fun filterAndRank(
        part: EquipPart,
        cond: SearchCondition,
        requiredSystems: Set<String>,
        topN: Int
    ): List<Equipment> {
        val all = (data.equipments[part] ?: emptyList()).filter { eq ->
            eq.matchesType(cond.hunterType) &&
            eq.matchesGender(cond.gender) &&
            eq.availableAt(cond.hrLimit, cond.villageLimit) &&
            eq.name !in cond.excludeEquip
        }
        val scored = all.map { eq ->
            val contrib = eq.skills.filter { it.system in requiredSystems }.sumOf { it.value }
            val torsoBonus = if (eq.hasTorsoUp) 50 else 0
            eq to (contrib * 100 + eq.slots * 10 + eq.defFinal + torsoBonus)
        }.sortedByDescending { it.second }

        val positive = scored.filter { it.second >= 100 }.take(topN)
        val fillers = scored.filter { it.second < 100 }.take(15)
        return (positive + fillers).map { it.first }.distinctBy { it.name }
    }

    private fun dfs(
        partIndex: Int,
        candidates: Map<EquipPart, List<Equipment>>,
        current: Array<Equipment?>,
        reqMap: Map<String, Int>,
        requiredSystems: Set<String>,
        maxContrib: List<Map<String, Int>>,
        cond: SearchCondition,
        results: MutableList<EquipSet>,
        maxResults: Int,
        explored: IntArray = intArrayOf(0),
        maxExplore: Int = 200000
    ) {
        if (results.size >= maxResults) return
        if (explored[0]++ > maxExplore) return
        if (partIndex >= 5) {
            tryFill(current, reqMap, cond, results, maxResults, virtual = false)
            return
        }
        val part = EquipPart.entries[partIndex]
        val list = candidates[part] ?: emptyList()
        for (eq in list) {
            if (results.size >= maxResults || explored[0] > maxExplore) return
            current[partIndex] = eq
            if (canReach(partIndex, current, reqMap, requiredSystems, maxContrib)) {
                dfs(partIndex + 1, candidates, current, reqMap, requiredSystems, maxContrib, cond, results, maxResults, explored, maxExplore)
            }
        }
        if (cond.fixedEquip[part] == null && results.size < maxResults && explored[0] <= maxExplore) {
            current[partIndex] = null
            if (canReach(partIndex, current, reqMap, requiredSystems, maxContrib)) {
                dfs(partIndex + 1, candidates, current, reqMap, requiredSystems, maxContrib, cond, results, maxResults, explored, maxExplore)
            }
        }
        current[partIndex] = null
    }

    private fun canReach(
        partIndex: Int,
        current: Array<Equipment?>,
        reqMap: Map<String, Int>,
        requiredSystems: Set<String>,
        maxContrib: List<Map<String, Int>>
    ): Boolean {
        val pts = mutableMapOf<String, Int>()
        for (i in 0..partIndex) {
            current[i]?.skills?.forEach { sp ->
                pts[sp.system] = (pts[sp.system] ?: 0) + sp.value
            }
        }
        for (sys in requiredSystems) {
            val need = reqMap[sys] ?: continue
            if (need <= 0) continue
            var have = pts[sys] ?: 0
            for (i in (partIndex + 1)..4) {
                have += maxContrib[i][sys] ?: 0
            }
            have += 15 // 装飾品見積
            if (have < need) return false
        }
        return true
    }

    private fun tryFill(
        equips: Array<Equipment?>,
        reqMap: Map<String, Int>,
        cond: SearchCondition,
        results: MutableList<EquipSet>,
        maxResults: Int,
        virtual: Boolean
    ) {
        val head = equips[0]; val body = equips[1]; val arm = equips[2]
        val waist = equips[3]; val leg = equips[4]
        val isTorsoUp = listOf(head, arm, waist, leg).any { it?.hasTorsoUp == true }

        val basePoints = skillEngine.calcSkillPoints(head, body, arm, waist, leg, emptyList(), null)
        val deficit = mutableMapOf<String, Int>()
        for ((sys, need) in reqMap) {
            if (need > 0) {
                val have = basePoints[sys] ?: 0
                if (have < need) deficit[sys] = need - have
            }
        }

        val equipSlots = listOf(head, body, arm, waist, leg).sumOf { it?.slots ?: 0 }
        val weaponSlots = cond.weaponSlots.coerceAtLeast(0)
        var remainingSlots = equipSlots + weaponSlots

        val decoCands = data.decorations.filter {
            it.name !in cond.excludeDeco && it.availableAt(cond.hrLimit, cond.villageLimit)
        }

        val usedDecos = mutableListOf<Decoration>()
        val points = basePoints.toMutableMap()

        val sortedDef = deficit.entries.sortedByDescending { it.value }
        for ((sys, _) in sortedDef) {
            var still = (reqMap[sys] ?: 0) - (points[sys] ?: 0)
            if (still <= 0) continue
            val useful = decoCands
                .filter { d -> d.slots <= remainingSlots && d.skills.any { it.system == sys && it.value > 0 } }
                .sortedWith(
                    compareByDescending<Decoration> { d ->
                        d.skills.filter { it.system == sys }.sumOf { it.value }.toDouble() / d.slots.coerceAtLeast(1)
                    }.thenBy { it.slots }
                )
            for (d in useful) {
                if (still <= 0 || d.slots > remainingSlots) break
                usedDecos.add(d)
                remainingSlots -= d.slots
                d.skills.forEach { sp ->
                    points[sp.system] = (points[sp.system] ?: 0) + sp.value
                    if (sp.system == sys) still -= sp.value
                }
            }
        }

        // 護石: 登録済み or なし
        val charms: List<OwnedCharm?> = if (cond.ownedCharms.isNotEmpty()) {
            cond.ownedCharms
        } else listOf(null)

        for (charm in charms) {
            if (results.size >= maxResults) return
            if (charm != null) {
                val p = points.toMutableMap()
                charm.skill1?.let { p[it.system] = (p[it.system] ?: 0) + it.value }
                charm.skill2?.let { p[it.system] = (p[it.system] ?: 0) + it.value }
                var charmSlots = charm.slots
                val extraDecos = usedDecos.toMutableList()
                if (charmSlots > 0) {
                    for ((sys, need) in reqMap) {
                        if (need <= 0) continue
                        var still = need - (p[sys] ?: 0)
                        if (still <= 0) continue
                        val useful = decoCands.filter {
                            it.slots <= charmSlots && it.skills.any { s -> s.system == sys && s.value > 0 }
                            && it.name !in cond.excludeDeco
                        }.sortedByDescending { d ->
                            d.skills.filter { it.system == sys }.sumOf { it.value }
                        }
                        for (d in useful) {
                            if (still <= 0 || d.slots > charmSlots) break
                            extraDecos.add(d)
                            charmSlots -= d.slots
                            d.skills.forEach { sp ->
                                p[sp.system] = (p[sp.system] ?: 0) + sp.value
                                if (sp.system == sys) still -= sp.value
                            }
                        }
                    }
                }
                if (skillEngine.satisfies(p, cond.requiredSkills, cond.hunterType)) {
                    results.add(buildSet(head, body, arm, waist, leg, extraDecos, charm, null, weaponSlots, p, cond.hunterType, isTorsoUp))
                }
            } else {
                if (skillEngine.satisfies(points, cond.requiredSkills, cond.hunterType)) {
                    results.add(buildSet(head, body, arm, waist, leg, usedDecos, null, null, weaponSlots, points, cond.hunterType, isTorsoUp))
                }
            }
        }
    }

    private fun buildSet(
        head: Equipment?, body: Equipment?, arm: Equipment?, waist: Equipment?, leg: Equipment?,
        decos: List<Decoration>, charm: OwnedCharm?, vCharm: VirtualCharm?,
        weaponSlots: Int, points: Map<String, Int>, hunterType: HunterType, isTorsoUp: Boolean
    ): EquipSet {
        val activated = skillEngine.resolveActivated(points, hunterType)
        val def = listOfNotNull(head, body, arm, waist, leg).sumOf { it.defFinal }
        val res = mapOf(
            "火" to listOfNotNull(head, body, arm, waist, leg).sumOf { it.resFire },
            "水" to listOfNotNull(head, body, arm, waist, leg).sumOf { it.resWater },
            "雷" to listOfNotNull(head, body, arm, waist, leg).sumOf { it.resThunder },
            "氷" to listOfNotNull(head, body, arm, waist, leg).sumOf { it.resIce },
            "龍" to listOfNotNull(head, body, arm, waist, leg).sumOf { it.resDragon }
        )
        return EquipSet(head, body, arm, waist, leg, decos, charm, vCharm, weaponSlots, def, res, activated, points, isTorsoUp)
    }

    /** 仮想護石検索 (貪欲法で高速化) */
    private fun searchWithVirtualCharms(
        cond: SearchCondition,
        reqMap: Map<String, Int>,
        candidates: Map<EquipPart, List<Equipment>>,
        maxContrib: List<Map<String, Int>>,
        maxResults: Int
    ): List<EquipSet> {
        // 各部位から必要系統への貢献が大きい上位装備を1つずつ選ぶ
        val bestEquips = arrayOfNulls<Equipment>(5)
        val usedPoints = mutableMapOf<String, Int>()
        for ((idx, part) in EquipPart.entries.withIndex()) {
            val cans = candidates[part] ?: emptyList()
            val best = cans.maxByOrNull { eq ->
                eq.skills.filter { it.system in reqMap.keys }.sumOf { it.value } * 10 + eq.slots + eq.defFinal / 10
            }
            bestEquips[idx] = best
            best?.skills?.forEach { sp ->
                usedPoints[sp.system] = (usedPoints[sp.system] ?: 0) + sp.value
            }
        }
        // 胴倍チェック
        val head = bestEquips[0]; val body = bestEquips[1]; val arm = bestEquips[2]
        val waist = bestEquips[3]; val leg = bestEquips[4]
        val isTorsoUp = listOf(head, arm, waist, leg).any { it?.hasTorsoUp == true }
        val base = skillEngine.calcSkillPoints(head, body, arm, waist, leg, emptyList(), null)

        // 装飾品で埋める
        var remainingSlots = listOf(head, body, arm, waist, leg).sumOf { it?.slots ?: 0 } + cond.weaponSlots.coerceAtLeast(0)
        val points = base.toMutableMap()
        val usedDecos = mutableListOf<Decoration>()
        val decoCands = data.decorations.filter {
            it.name !in cond.excludeDeco && it.availableAt(cond.hrLimit, cond.villageLimit)
        }
        for ((sys, need) in reqMap.entries.sortedByDescending { it.value }) {
            if (need <= 0) continue
            var still = need - (points[sys] ?: 0)
            if (still <= 0) continue
            for (d in decoCands.filter { it.slots <= remainingSlots && it.skills.any { s -> s.system == sys && s.value > 0 } }
                .sortedByDescending { d -> d.skills.filter { it.system == sys }.sumOf { it.value }.toDouble() / d.slots }) {
                if (still <= 0 || d.slots > remainingSlots) break
                usedDecos.add(d)
                remainingSlots -= d.slots
                d.skills.forEach { sp ->
                    points[sp.system] = (points[sp.system] ?: 0) + sp.value
                    if (sp.system == sys) still -= sp.value
                }
            }
        }
        val stillDeficit = mutableMapOf<String, Int>()
        for ((sys, need) in reqMap) {
            if (need > 0) {
                val have = points[sys] ?: 0
                if (have < need) stillDeficit[sys] = need - have
            }
        }
        if (stillDeficit.isEmpty()) return emptyList()

        val results = mutableListOf<EquipSet>()
        val vCharms = skillEngine.generateVirtualCharms(stillDeficit).take(maxResults)
        for (vc in vCharms) {
            val finalPts = points.toMutableMap()
            finalPts[vc.skill1.system] = (finalPts[vc.skill1.system] ?: 0) + vc.skill1.value
            vc.skill2?.let { finalPts[it.system] = (finalPts[it.system] ?: 0) + it.value }
            if (skillEngine.satisfies(finalPts, cond.requiredSkills, cond.hunterType)) {
                results.add(buildSet(head, body, arm, waist, leg, usedDecos, null, vc,
                    cond.weaponSlots.coerceAtLeast(0), finalPts, cond.hunterType, isTorsoUp))
            }
        }
        return results
    }

    /** まとめ装備検索: 胴系統倍加や同一スキル構成をグループ化 */
    fun summarizeResults(sets: List<EquipSet>): List<SummarizedGroup> {
        val groups = linkedMapOf<String, MutableList<EquipSet>>()
        for (set in sets) {
            val key = if (set.isTorsoUp) {
                "TORSO_UP:${set.body?.name}:${set.activatedSkills.sorted().joinToString(",")}"
            } else {
                // スロット構成が近いものをまとめる (発動スキル同一)
                "NORMAL:${set.activatedSkills.sorted().joinToString(",")}:${set.emptyParts}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(set)
        }
        return groups.map { (key, list) ->
            val rep = list.maxByOrNull { it.totalDefense } ?: list.first()
            val label = if (key.startsWith("TORSO_UP")) {
                "【胴系統倍加】${rep.body?.name ?: ""} 他${list.size}件"
            } else {
                "発動:${rep.activatedSkills.take(3).joinToString(",")} 他${list.size}件"
            }
            SummarizedGroup(key, rep, list, label)
        }.sortedByDescending { it.representative.totalDefense }
    }

    /** 追加スキル検索 (既存セットから) */
    fun findAdditionalSkills(set: EquipSet, hunterType: HunterType, maxDeficit: Int = 10): List<AdditionalSkill> {
        return skillEngine.findAdditionalSkills(set.skillPoints, hunterType, maxDeficit)
    }

    /** 追加スキル検索 (ポイントマップから) */
    fun findAdditionalSkills(points: Map<String, Int>, hunterType: HunterType, maxDeficit: Int = 10): List<AdditionalSkill> {
        return skillEngine.findAdditionalSkills(points, hunterType, maxDeficit)
    }
}
