package com.tbread.entity

import kotlinx.serialization.Serializable

@Serializable
data class Mob(val code: Int, val name: String, val boss: Boolean, val isDummy: Boolean = false)