package com.tbread.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EncounterSkillSnapshot(
    val skillCode: Int,
    val name: String? = null,
    val damageAmount: Int = 0,
    val dotDamageAmount: Int = 0,
    val dotTimes: Int = 0,
    val hitTimes: Int = 0,
    val critTimes: Int = 0,
    val backTimes: Int = 0,
    val perfectTimes: Int = 0,
    val doubleTimes: Int = 0,
    val parryTimes: Int = 0,
    val shardTimes: Int = 0,
    val multiHitTimes: Int = 0,
)

@Serializable
data class EncounterBuffSnapshot(
    val code: Int,
    val name: String,
    val summary: String? = null,
    val effect: String? = null,
    val uptimePercent: Double = 0.0,
    val actorId: Int = 0,
)

@Serializable
data class EncounterPlayerSnapshot(
    val id: Int,
    val nickname: String? = null,
    val server: Int = -1,
    val job: String? = null,
    val isSelf: Boolean = false,
    val combatPower: Int = 0,
    val damage: Double = 0.0,
    val dps: Double = 0.0,
    val sharePercent: Double = 0.0,
    val skills: List<EncounterSkillSnapshot> = emptyList(),
    val buffs: List<EncounterBuffSnapshot> = emptyList(),
)

@Serializable
data class EncounterTargetSnapshot(
    val id: Int,
    val code: Int? = null,
    val name: String? = null,
    val boss: Boolean = false,
    val remainHp: Int = 0,
    val maxHp: Int = 0,
)

@Serializable
data class EncounterSnapshot(
    val schema: String = SCHEMA,
    val meterVersion: String,
    val battleStart: Long,
    val battleEnd: Long,
    val durationMs: Long,
    val target: EncounterTargetSnapshot? = null,
    val players: List<EncounterPlayerSnapshot> = emptyList(),
) {
    companion object {
        const val SCHEMA = "bit-legion-encounter-v1"

        private val json = Json {
            encodeDefaults = true
            prettyPrint = false
        }

        fun encode(snapshot: EncounterSnapshot): String = json.encodeToString(snapshot)
    }
}
