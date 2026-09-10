package com.tbread.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SkillCodesTest {
    @Test
    fun skillCodeStaysOnTenThousandGrid() {
        assertEquals(18_250_000, SkillCodes.base(18_250_401))
        assertEquals(12_780_000, SkillCodes.base(12_780_011))
    }

    @Test
    fun buffCodeMapsToSkillBase() {
        assertEquals(18_250_000, SkillCodes.base(182_500_001))
        assertEquals(12_780_000, SkillCodes.base(127_800_011))
    }
}
