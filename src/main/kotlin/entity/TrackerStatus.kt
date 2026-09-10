package com.tbread.entity

import kotlinx.serialization.Serializable

@Serializable
data class TrackedBuff(
    val skillCode: Int,
    val name: String? = null,
    val remainingMs: Long = 0,
    val durationMs: Long = 0,
)

@Serializable
data class TrackerStatus(
    val odeEnergy: Int? = null,
    val shugoKeys: Int? = null,
    val buffs: List<TrackedBuff> = emptyList(),
)
