package com.mhxx.sim.data

import android.content.Context
import com.mhxx.sim.model.MySet
import com.mhxx.sim.model.OwnedCharm
import com.mhxx.sim.model.SkillPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * マイセット・護石の永続化 (SharedPreferences + JSON)
 */
class MySetStore(context: Context) {
    private val prefs = context.getSharedPreferences("mhxx_gansimu", Context.MODE_PRIVATE)

    // ---- マイセット ----
    fun loadMySets(): List<MySet> {
        val json = prefs.getString("mysets", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MySet(
                id = o.getString("id"),
                name = o.getString("name"),
                head = o.optString("head", null),
                body = o.optString("body", null),
                arm = o.optString("arm", null),
                waist = o.optString("waist", null),
                leg = o.optString("leg", null),
                decorations = o.optJSONArray("decorations")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList(),
                charmDisplay = o.optString("charmDisplay", null),
                activatedSkills = o.optJSONArray("activatedSkills")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList(),
                totalDefense = o.optInt("totalDefense", 0),
                createdAt = o.optLong("createdAt", 0)
            )
        }
    }

    fun saveMySets(sets: List<MySet>) {
        val arr = JSONArray()
        for (s in sets) {
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("head", s.head)
                put("body", s.body)
                put("arm", s.arm)
                put("waist", s.waist)
                put("leg", s.leg)
                put("decorations", JSONArray(s.decorations))
                put("charmDisplay", s.charmDisplay)
                put("activatedSkills", JSONArray(s.activatedSkills))
                put("totalDefense", s.totalDefense)
                put("createdAt", s.createdAt)
            })
        }
        prefs.edit().putString("mysets", arr.toString()).apply()
    }

    fun addMySet(set: MySet) {
        val list = loadMySets().toMutableList()
        list.add(0, set)
        saveMySets(list)
    }

    fun removeMySet(id: String) {
        saveMySets(loadMySets().filter { it.id != id })
    }

    // ---- 護石 ----
    fun loadCharms(): List<OwnedCharm> {
        val json = prefs.getString("charms", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OwnedCharm(
                id = o.getString("id"),
                template = o.getString("template"),
                skill1 = o.optJSONObject("skill1")?.let { parseSP(it) },
                skill2 = o.optJSONObject("skill2")?.let { parseSP(it) },
                slots = o.optInt("slots", 0)
            )
        }
    }

    fun saveCharms(charms: List<OwnedCharm>) {
        val arr = JSONArray()
        for (c in charms) {
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("template", c.template)
                put("skill1", c.skill1?.let { spJson(it) })
                put("skill2", c.skill2?.let { spJson(it) })
                put("slots", c.slots)
            })
        }
        prefs.edit().putString("charms", arr.toString()).apply()
    }

    fun addCharm(charm: OwnedCharm) {
        val list = loadCharms().toMutableList()
        list.add(charm)
        saveCharms(list)
    }

    fun removeCharm(id: String) {
        saveCharms(loadCharms().filter { it.id != id })
    }

    // ---- 除外・固定 ----
    fun loadExcludeEquip(): Set<String> =
        prefs.getStringSet("exclude_equip", emptySet()) ?: emptySet()

    fun saveExcludeEquip(names: Set<String>) {
        prefs.edit().putStringSet("exclude_equip", names).apply()
    }

    fun loadFixedEquip(): Map<String, String> {
        val json = prefs.getString("fixed_equip", "{}") ?: "{}"
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun saveFixedEquip(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("fixed_equip", o.toString()).apply()
    }

    fun loadExcludeDeco(): Set<String> =
        prefs.getStringSet("exclude_deco", emptySet()) ?: emptySet()

    fun saveExcludeDeco(names: Set<String>) {
        prefs.edit().putStringSet("exclude_deco", names).apply()
    }

    private fun parseSP(o: JSONObject) = SkillPoint(o.getString("system"), o.getInt("value"))
    private fun spJson(sp: SkillPoint) = JSONObject().apply {
        put("system", sp.system)
        put("value", sp.value)
    }
}
