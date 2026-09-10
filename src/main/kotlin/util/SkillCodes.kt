package com.tbread.util

object SkillCodes {
    fun base(code: Int): Int {
        return when {
            code in 11_000_000..19_999_999 -> (code / 10_000) * 10_000
            code in 110_000_000..190_999_999 -> (code / 100_000) * 10_000
            else -> code
        }
    }
}
