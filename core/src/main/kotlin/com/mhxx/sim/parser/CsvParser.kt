package com.mhxx.sim.parser

import com.mhxx.sim.model.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {

    fun parse(skillsIn: InputStream,
              headIn: InputStream, bodyIn: InputStream, armIn: InputStream,
              waistIn: InputStream, legIn: InputStream,
              decoIn: InputStream, charmIn: InputStream,
              fukugoIn: InputStream, categoryIn: InputStream): GameData {

        val skills = parseSkills(skillsIn)
        val skillByName = skills.associateBy { it.name }
        val systems = skills.groupBy { it.system }
            .mapValues { (_, list) -> list.sortedByDescending { it.points } }

        val equipments = mapOf(
            EquipPart.HEAD to parseEquip(headIn, EquipPart.HEAD),
            EquipPart.BODY to parseEquip(bodyIn, EquipPart.BODY),
            EquipPart.ARM to parseEquip(armIn, EquipPart.ARM),
            EquipPart.WAIST to parseEquip(waistIn, EquipPart.WAIST),
            EquipPart.LEG to parseEquip(legIn, EquipPart.LEG)
        )
        val decorations = parseDeco(decoIn)
        val charms = parseCharm(charmIn)
        val composites = parseFukugo(fukugoIn)
        val categories = parseCategory(categoryIn)

        return GameData(skills, skillByName, systems, equipments, decorations, charms, composites, categories)
    }

    private fun readLines(ins: InputStream): List<String> =
        BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).use { it.readLines() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

    private fun splitCsv(line: String): List<String> {
        // 簡易CSV分割 (ダブルクォート対応)
        val result = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val end = line.indexOf('"', i + 1)
                if (end < 0) {
                    result.add(line.substring(i + 1))
                    break
                }
                result.add(line.substring(i + 1, end))
                i = end + 1
                if (i < line.length && line[i] == ',') i++
            } else {
                val end = line.indexOf(',', i)
                if (end < 0) {
                    result.add(line.substring(i).trim())
                    break
                }
                result.add(line.substring(i, end).trim())
                i = end + 1
            }
        }
        return result
    }

    private fun parseSkills(ins: InputStream): List<SkillActivation> =
        readLines(ins).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 4) return@mapNotNull null
            SkillActivation(
                name = c[0],
                system = c[1],
                points = c[2].toIntOrNull() ?: return@mapNotNull null,
                type = HunterType.from(c[3].toIntOrNull() ?: 0)
            )
        }

    private fun parseEquip(ins: InputStream, part: EquipPart): List<Equipment> =
        readLines(ins).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 25) return@mapNotNull null
            val skills = mutableListOf<SkillPoint>()
            for (i in 0 until 5) {
                val sys = c.getOrNull(15 + i * 2)?.takeIf { it.isNotBlank() } ?: continue
                val pts = c.getOrNull(16 + i * 2)?.toIntOrNull() ?: continue
                skills.add(SkillPoint(sys, pts))
            }
            val mats = mutableListOf<Pair<String, Int>>()
            for (i in 0 until 4) {
                val name = c.getOrNull(25 + i * 2)?.takeIf { it.isNotBlank() } ?: continue
                val cnt = c.getOrNull(26 + i * 2)?.toIntOrNull() ?: 1
                mats.add(name to cnt)
            }
            Equipment(
                name = c[0],
                part = part,
                gender = Gender.from(c[1].toIntOrNull() ?: 0),
                type = HunterType.from(c[2].toIntOrNull() ?: 0),
                rarity = c[3].toIntOrNull() ?: 1,
                slots = c[4].toIntOrNull() ?: 0,
                hr = c[5].toIntOrNull() ?: 99,
                village = c[6].toIntOrNull() ?: 99,
                hrVillageMode = c[7].toIntOrNull() ?: 0,
                defInit = c[8].toIntOrNull() ?: 0,
                defFinal = c[9].toIntOrNull() ?: 0,
                resFire = c[10].toIntOrNull() ?: 0,
                resWater = c[11].toIntOrNull() ?: 0,
                resThunder = c[12].toIntOrNull() ?: 0,
                resIce = c[13].toIntOrNull() ?: 0,
                resDragon = c[14].toIntOrNull() ?: 0,
                skills = skills,
                materials = mats
            )
        }

    private fun parseDeco(ins: InputStream): List<Decoration> =
        readLines(ins).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 10) return@mapNotNull null
            val skills = mutableListOf<SkillPoint>()
            for (i in 0 until 2) {
                val sys = c.getOrNull(6 + i * 2)?.takeIf { it.isNotBlank() } ?: continue
                val pts = c.getOrNull(7 + i * 2)?.toIntOrNull() ?: continue
                skills.add(SkillPoint(sys, pts))
            }
            val mats = mutableListOf<Pair<String, Int>>()
            for (i in 0 until 8) {
                val name = c.getOrNull(10 + i * 2)?.takeIf { it.isNotBlank() } ?: continue
                val cnt = c.getOrNull(11 + i * 2)?.toIntOrNull() ?: 1
                mats.add(name to cnt)
            }
            Decoration(
                name = c[0],
                rarity = c[1].toIntOrNull() ?: 1,
                slots = c[2].toIntOrNull() ?: 1,
                hr = c[3].toIntOrNull() ?: 99,
                village = c[4].toIntOrNull() ?: 99,
                hrVillageMode = c[5].toIntOrNull() ?: 0,
                skills = skills,
                materials = mats
            )
        }

    private fun parseCharm(ins: InputStream): List<CharmTemplate> =
        readLines(ins).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 3) return@mapNotNull null
            CharmTemplate(c[0], c[1].toIntOrNull() ?: 1, c[2].toIntOrNull() ?: 1)
        }

    private fun parseFukugo(ins: InputStream): List<CompositeSkill> =
        readLines(ins).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 2) return@mapNotNull null
            CompositeSkill(c[0], c.drop(1).filter { it.isNotBlank() })
        }

    private fun parseCategory(ins: InputStream): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        readLines(ins).forEach { line ->
            val c = splitCsv(line)
            if (c.isNotEmpty()) {
                map[c[0]] = c.drop(1).filter { it.isNotBlank() }
            }
        }
        return map
    }
}
