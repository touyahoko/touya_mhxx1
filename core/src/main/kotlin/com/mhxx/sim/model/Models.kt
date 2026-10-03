package com.mhxx.sim.model

/** ハンタータイプ */
enum class HunterType(val code: Int) {
    BOTH(0), SWORD(1), GUNNER(2);
    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code } ?: BOTH
    }
}

/** 性別 */
enum class Gender(val code: Int) {
    BOTH(0), MALE(1), FEMALE(2);
    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code } ?: BOTH
    }
}

/** 防具部位 */
enum class EquipPart(val label: String) {
    HEAD("頭"), BODY("胴"), ARM("腕"), WAIST("腰"), LEG("脚")
}

/** スキル発動条件 */
data class SkillActivation(
    val name: String,
    val system: String,
    val points: Int,
    val type: HunterType
)

/** スキル系統ポイント */
data class SkillPoint(
    val system: String,
    val value: Int
)

/** 防具データ */
data class Equipment(
    val name: String,
    val part: EquipPart,
    val gender: Gender,
    val type: HunterType,
    val rarity: Int,
    val slots: Int,
    val hr: Int,
    val village: Int,
    val hrVillageMode: Int,
    val defInit: Int,
    val defFinal: Int,
    val resFire: Int,
    val resWater: Int,
    val resThunder: Int,
    val resIce: Int,
    val resDragon: Int,
    val skills: List<SkillPoint>,
    val materials: List<Pair<String, Int>>
) {
    fun matchesType(t: HunterType) = type == HunterType.BOTH || type == t
    fun matchesGender(g: Gender) = gender == Gender.BOTH || gender == g
    fun availableAt(hrLimit: Int, villageLimit: Int): Boolean {
        val hrOk = hr <= hrLimit
        val vilOk = village <= villageLimit
        return if (hrVillageMode == 1) hrOk && vilOk else hrOk || vilOk
    }
    val hasTorsoUp: Boolean
        get() = skills.any { it.system == "胴系統倍加" && it.value > 0 }
}

/** 装飾品 */
data class Decoration(
    val name: String,
    val rarity: Int,
    val slots: Int,
    val hr: Int,
    val village: Int,
    val hrVillageMode: Int,
    val skills: List<SkillPoint>,
    val materials: List<Pair<String, Int>>
) {
    fun availableAt(hrLimit: Int, villageLimit: Int): Boolean {
        val hrOk = hr <= hrLimit
        val vilOk = village <= villageLimit
        return if (hrVillageMode == 1) hrOk && vilOk else hrOk || vilOk
    }
}

/** 護石テンプレート */
data class CharmTemplate(
    val name: String,
    val rarity: Int,
    val period: Int
)

/** 登録済み護石 */
data class OwnedCharm(
    val id: String = java.util.UUID.randomUUID().toString(),
    val template: String,
    val skill1: SkillPoint?,
    val skill2: SkillPoint?,
    val slots: Int = 0
) {
    fun displayName(): String {
        val s1 = skill1?.let { "${it.system}${if (it.value > 0) "+" else ""}${it.value}" } ?: ""
        val s2 = skill2?.let { "${it.system}${if (it.value > 0) "+" else ""}${it.value}" } ?: ""
        val sl = if (slots > 0) " スロ$slots" else ""
        return "$template [$s1${if (s2.isNotEmpty()) "/$s2" else ""}$sl]"
    }
}

/** 仮想護石候補 (持っていれば発動) */
data class VirtualCharm(
    val skill1: SkillPoint,
    val skill2: SkillPoint?,
    val slots: Int,
    val minPointsNeeded: Map<String, Int>
) {
    fun displayName(): String {
        val s1 = "${skill1.system}${if (skill1.value > 0) "+" else ""}${skill1.value}"
        val s2 = skill2?.let { " / ${it.system}${if (it.value > 0) "+" else ""}${it.value}" } ?: ""
        val sl = if (slots > 0) " スロ$slots" else ""
        return "仮想護石 [$s1$s2$sl]"
    }
}

/** 複合スキル */
data class CompositeSkill(
    val name: String,
    val requiredSkills: List<String>
)

/** 検索条件 */
data class SearchCondition(
    val requiredSkills: List<String> = emptyList(),
    val hunterType: HunterType = HunterType.SWORD,
    val gender: Gender = Gender.BOTH,
    val hrLimit: Int = 99,
    val villageLimit: Int = 99,
    val weaponSlots: Int = 0,
    val excludeEquip: Set<String> = emptySet(),
    val fixedEquip: Map<EquipPart, String> = emptyMap(),
    val excludeDeco: Set<String> = emptySet(),
    val ownedCharms: List<OwnedCharm> = emptyList(),
    val searchVirtualCharms: Boolean = true,
    val useExcludeFixed: Boolean = true,
    val maxResults: Int = 100,
    val summarize: Boolean = false
)

/** 検索結果1セット */
data class EquipSet(
    val head: Equipment?,
    val body: Equipment?,
    val arm: Equipment?,
    val waist: Equipment?,
    val leg: Equipment?,
    val decorations: List<Decoration>,
    val charm: OwnedCharm?,
    val virtualCharm: VirtualCharm? = null,
    val weaponSlotsUsed: Int,
    val totalDefense: Int,
    val totalRes: Map<String, Int>,
    val activatedSkills: List<String>,
    val skillPoints: Map<String, Int>,
    val isTorsoUp: Boolean = false
) {
    val emptyParts: Int
        get() = listOf(head, body, arm, waist, leg).count { it == null }

    fun equipName(part: EquipPart): String = when (part) {
        EquipPart.HEAD -> head?.name
        EquipPart.BODY -> body?.name
        EquipPart.ARM -> arm?.name
        EquipPart.WAIST -> waist?.name
        EquipPart.LEG -> leg?.name
    } ?: "─"

    fun summaryKey(): String {
        return if (isTorsoUp) {
            val upParts = listOfNotNull(
                head?.takeIf { it.hasTorsoUp }?.name,
                arm?.takeIf { it.hasTorsoUp }?.name,
                waist?.takeIf { it.hasTorsoUp }?.name,
                leg?.takeIf { it.hasTorsoUp }?.name
            )
            "胴倍:${upParts.joinToString(",")}|${body?.name}"
        } else {
            listOf(head, body, arm, waist, leg).joinToString("|") { it?.name ?: "-" }
        }
    }
}

/** まとめ結果グループ */
data class SummarizedGroup(
    val key: String,
    val representative: EquipSet,
    val variants: List<EquipSet>,
    val label: String
)

/** 追加スキル候補 */
data class AdditionalSkill(
    val skillName: String,
    val system: String,
    val pointsNeeded: Int,
    val currentPoints: Int,
    val deficit: Int
)

/** マイセット */
data class MySet(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val head: String?,
    val body: String?,
    val arm: String?,
    val waist: String?,
    val leg: String?,
    val decorations: List<String>,
    val charmDisplay: String?,
    val activatedSkills: List<String>,
    val totalDefense: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/** ゲーム全体データ */
data class GameData(
    val skills: List<SkillActivation>,
    val skillByName: Map<String, SkillActivation>,
    val systems: Map<String, List<SkillActivation>>,
    val equipments: Map<EquipPart, List<Equipment>>,
    val decorations: List<Decoration>,
    val charmTemplates: List<CharmTemplate>,
    val composites: List<CompositeSkill>,
    val categories: Map<String, List<String>>
) {
    fun findEquip(part: EquipPart, name: String): Equipment? =
        equipments[part]?.find { it.name == name }

    fun allEquipNames(): List<Pair<EquipPart, String>> =
        EquipPart.entries.flatMap { part ->
            (equipments[part] ?: emptyList()).map { part to it.name }
        }
}
