package com.mhxx.sim.data

import android.content.Context
import com.mhxx.sim.model.GameData
import com.mhxx.sim.parser.CsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DataRepository {
    @Volatile private var cached: GameData? = null

    suspend fun get(context: Context): GameData = withContext(Dispatchers.IO) {
        cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
    }

    private fun load(context: Context): GameData {
        val am = context.assets
        return CsvParser.parse(
            skillsIn = am.open("data/MHXX_SKILL.csv"),
            headIn = am.open("data/MHXX_EQUIP_HEAD.csv"),
            bodyIn = am.open("data/MHXX_EQUIP_BODY.csv"),
            armIn = am.open("data/MHXX_EQUIP_ARM.csv"),
            waistIn = am.open("data/MHXX_EQUIP_WST.csv"),
            legIn = am.open("data/MHXX_EQUIP_LEG.csv"),
            decoIn = am.open("data/MHXX_DECO.csv"),
            charmIn = am.open("data/MHXX_CHARM.csv"),
            fukugoIn = am.open("data/conf/FUKUGO.txt"),
            categoryIn = am.open("data/conf/CATEGORY.txt")
        )
    }
}
