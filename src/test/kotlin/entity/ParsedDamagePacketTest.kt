package com.tbread.entity

import com.tbread.packet.StreamProcessor
import kotlin.test.Test
import kotlin.test.assertEquals

class ParsedDamagePacketTest {
    @Test
    fun multiHitMultipliesPerHitDamage() {
        val packet = ParsedDamagePacket()
        packet.setDamage(StreamProcessor.VarIntOutput(140191, 3))
        packet.setLoop(19)
        assertEquals(19, packet.hitCount())
        assertEquals(140191L * 19, packet.effectiveDamage())
    }

    @Test
    fun zeroLoopCountsAsOneHit() {
        val packet = ParsedDamagePacket()
        packet.setDamage(StreamProcessor.VarIntOutput(500, 2))
        packet.setLoop(0)
        assertEquals(1, packet.hitCount())
        assertEquals(500L, packet.effectiveDamage())
    }
}
