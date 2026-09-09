package com.tbread.ndps

import com.tbread.data.DataManager
import com.tbread.entity.UseBuff

/**
 * Experimental nDPS: reverse *party* synergy on hits, keep self-buffs.
 *
 * NotMeter-identical nDPS needs the character's own PvE amp and skill levels.
 * This fork only sees buff windows, so extra amps are conservative constants
 * and personal gear amp is treated as 0 (slightly over-strips synergy).
 */
object NdpsSynergy {
    data class Spec(val kind: String, val extraAmp: Double, val label: String)

    val GALE = Spec("gale", 0.05, "질풍")
    val RAGE = Spec("rage", 0.10, "격앙")
    val COUNTER = Spec("counter", 0.09, "노련한 반격")
    val EARTH = Spec("earth", 0.06, "대지의 은총")

    fun match(code: Int, name: String?): Spec? {
        val trimmed = name?.trim().orEmpty()
        when {
            trimmed.startsWith("질풍의 권능") -> return GALE
            trimmed == "격앙" -> return RAGE
            trimmed.startsWith("노련한 반격") -> return COUNTER
            trimmed.startsWith("대지의 은총") -> return EARTH
        }
        return when (code) {
            in 182500000..182500999 -> GALE
            in 127800000..127800999 -> RAGE
            in 117800000..117800999 -> COUNTER
            in 177800000..177800999 -> EARTH
            else -> null
        }
    }

    fun extraAmp(
        ownerId: Int,
        buffs: List<UseBuff>,
        resolveName: (Int) -> String?,
    ): Double {
        var extra = 0.0
        val seen = HashSet<String>()
        for (buff in buffs) {
            if (buff.actorId == ownerId) continue
            val spec = match(buff.skillCode, resolveName(buff.skillCode)) ?: continue
            if (seen.add(spec.kind)) extra += spec.extraAmp
        }
        return extra
    }

    fun normalizeHit(damage: Double, extraAmp: Double): Double {
        return damage / (1.0 + extraAmp.coerceAtLeast(0.0))
    }

    fun partyExtraAmp(uid: Int, timestamp: Long): Double {
        return extraAmp(uid, DataManager.battleBuff(uid, timestamp, timestamp)) { code ->
            DataManager.buff(code)?.name
        }
    }
}
