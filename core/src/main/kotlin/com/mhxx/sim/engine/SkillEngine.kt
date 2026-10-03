package com.mhxx.sim.engine

import com.mhxx.sim.model.*

class SkillEngine(private val data: GameData) {

    fun requiredPoints(skillName: String): Pair<String, Int>? {
        val sk = data.skillByName[skillName] ?: return null
        return sk.system to sk.points
    }

    fun calcSkillPoints(
        head: Equipment?, body: Equipment?, arm: Equipment?,
        waist: Equipment?, leg: Equipment?,
        decos: List<Decoration>,
        charm: OwnedCharm?,
        virtualCharm: VirtualCharm? = null
    ): Map<String, Int> {
        val points = mutableMapOf<String, Int>()
        fun add(sys: String, v: Int) { points[sys] = (points[sys] ?: 0) + v }

        val torsoUp = listOf(head, arm, waist, leg).any { it?.hasTorsoUp == true }
        val mult = if (torsoUp) 2 else 1

        listOf(head, arm, waist, leg).forEach { eq ->
            eq?.skills?.forEach { add(it.system, it.value) }
        }
        body?.skills?.forEach { add(it.system, it.value * mult) }

        decos.forEach { d -> d.skills.forEach { add(it.system, it.value) } }
        charm?.skill1?.let { add(it.system, it.value) }
        charm?.skill2?.let { add(it.system, it.value) }
        virtualCharm?.skill1?.let { add(it.system, it.value) }
        virtualCharm?.skill2?.let { add(it.system, it.value) }

        return points
    }

    fun resolveActivated(points: Map<String, Int>, hunterType: HunterType): List<String> {
        val activated = mutableListOf<String>()
        for ((sys, pts) in points) {
            val candidates = data.systems[sys] ?: continue
            val positive = candidates
                .filter { it.points > 0 && pts >= it.points && typeMatches(it.type, hunterType) }
                .maxByOrNull { it.points }
            if (positive != null) activated.add(positive.name)

            val negative = candidates
                .filter { it.points < 0 && pts <= it.points && typeMatches(it.type, hunterType) }
                .minByOrNull { it.points }
            if (negative != null) activated.add(negative.name)
        }
        for (comp in data.composites) {
            if (comp.requiredSkills.all { it in activated }) {
                activated.add(comp.name)
            }
        }
        return activated.distinct()
    }

    private fun typeMatches(skillType: HunterType, hunterType: HunterType) =
        skillType == HunterType.BOTH || hunterType == HunterType.BOTH || skillType == hunterType

    fun satisfies(points: Map<String, Int>, required: List<String>, hunterType: HunterType): Boolean {
        if (required.isEmpty()) return true
        val activated = resolveActivated(points, hunterType).toSet()
        return required.all { it in activated }
    }

    fun buildRequirementMap(requiredSkills: List<String>): Map<String, Int> {
        val req = mutableMapOf<String, Int>()
        for (name in requiredSkills) {
            val sk = data.skillByName[name] ?: continue
            val current = req[sk.system] ?: 0
            if (sk.points > 0) {
                req[sk.system] = maxOf(current, sk.points)
            } else {
                req[sk.system] = if (current == 0) sk.points else minOf(current, sk.points)
            }
        }
        return req
    }

    /**
     * 追加スキル検索: 現在のポイントから、あと何ポイントで発動できるスキルを列挙
     */
    fun findAdditionalSkills(
        points: Map<String, Int>,
        hunterType: HunterType,
        maxDeficit: Int = 10
    ): List<AdditionalSkill> {
        val activated = resolveActivated(points, hunterType).toSet()
        val result = mutableListOf<AdditionalSkill>()

        for ((sys, candidates) in data.systems) {
            val current = points[sys] ?: 0
            for (sk in candidates) {
                if (sk.points <= 0) continue
                if (sk.name in activated) continue
                if (!typeMatches(sk.type, hunterType)) continue
                val deficit = sk.points - current
                if (deficit in 1..maxDeficit) {
                    result.add(AdditionalSkill(sk.name, sys, sk.points, current, deficit))
                }
            }
        }
        return result.sortedWith(compareBy({ it.deficit }, { -it.pointsNeeded }))
    }

    /**
     * 不足ポイントから仮想護石候補を生成
     * 最低限必要なポイントの組み合わせを返す
     */
    fun generateVirtualCharms(
        deficit: Map<String, Int>,
        maxSlots: Int = 3
    ): List<VirtualCharm> {
        if (deficit.isEmpty()) return emptyList()
        val result = mutableListOf<VirtualCharm>()
        val systems = deficit.entries.sortedByDescending { it.value }

        // 単一スキル護石
        for ((sys, need) in systems) {
            val pts = need.coerceIn(1, 10)
            result.add(VirtualCharm(
                skill1 = SkillPoint(sys, pts),
                skill2 = null,
                slots = 0,
                minPointsNeeded = mapOf(sys to pts)
            ))
            // スロット付き
            if (maxSlots >= 1) {
                result.add(VirtualCharm(
                    skill1 = SkillPoint(sys, pts),
                    skill2 = null,
                    slots = 1,
                    minPointsNeeded = mapOf(sys to pts)
                ))
            }
        }

        // 2スキル護石
        if (systems.size >= 2) {
            val (s1, n1) = systems[0]
            val (s2, n2) = systems[1]
            result.add(VirtualCharm(
                skill1 = SkillPoint(s1, n1.coerceIn(1, 6)),
                skill2 = SkillPoint(s2, n2.coerceIn(1, 6)),
                slots = 0,
                minPointsNeeded = mapOf(s1 to n1.coerceIn(1, 6), s2 to n2.coerceIn(1, 6))
            ))
        }

        return result.distinctBy { it.displayName() }
    }
}
