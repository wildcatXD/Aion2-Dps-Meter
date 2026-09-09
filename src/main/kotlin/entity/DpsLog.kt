package com.tbread.entity

import kotlinx.serialization.Serializable

@Serializable
data class RawPacket(val data: ByteArray, val timestamp: Long)

@Serializable
data class DpsLog(
    val report: DpsReport,
    val summonMap: Map<Int, Int>,
    val packets: List<RawPacket>,
    val encounter: EncounterSnapshot? = null,
) {
    fun encounterJson(): String? = encounter?.let { EncounterSnapshot.encode(it) }
}
