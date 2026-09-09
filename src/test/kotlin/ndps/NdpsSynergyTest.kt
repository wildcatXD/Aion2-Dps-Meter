package com.tbread.ndps

import com.tbread.entity.UseBuff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NdpsSynergyTest {
    @Test
    fun galeMatchesChanterBuff() {
        assertEquals(NdpsSynergy.GALE, NdpsSynergy.match(182500001, "질풍의 권능"))
        assertEquals(NdpsSynergy.GALE, NdpsSynergy.match(182500401, null))
        assertNull(NdpsSynergy.match(19004471, "질풍"))
    }

    @Test
    fun ownBuffIsKept() {
        val buffs = listOf(UseBuff(182500001, 0, 10_000, 10_000, actorId = 1))
        val extra = NdpsSynergy.extraAmp(1, buffs) { _ -> "질풍의 권능" }
        assertEquals(0.0, extra, 1e-9)
    }

    @Test
    fun partyGaleAndRageStackOnceEach() {
        val buffs = listOf(
            UseBuff(182500001, 0, 10_000, 10_000, 2),
            UseBuff(182500401, 0, 10_000, 10_000, 3),
            UseBuff(127800011, 0, 10_000, 10_000, 4),
        )
        val extra = NdpsSynergy.extraAmp(1, buffs) { code ->
            when (code) {
                182500001, 182500401 -> "질풍의 권능"
                127800011 -> "격앙"
                else -> null
            }
        }
        assertEquals(0.15, extra, 1e-9)
    }

    @Test
    fun normalizeHitReversesAmp() {
        assertEquals(1000.0, NdpsSynergy.normalizeHit(1050.0, 0.05), 1e-6)
        assertEquals(500.0, NdpsSynergy.normalizeHit(500.0, 0.0), 1e-9)
    }
}
