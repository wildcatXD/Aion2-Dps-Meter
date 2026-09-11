package com.tbread.packet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OdeEnergyParserTest {
    @Test
    fun parsesTwoValueIncreaseUpdate() {
        val payload = ArrayList<Byte>()
        payload += 0x01
        payload += 0x0C
        payload += 0x01
        payload += varInt(51591)
        payload += varInt(240)
        payload += varInt(25)
        payload += 0x01
        payload += int32le(40)
        val reading = OdeEnergyParser.parseUpdate(payload.toByteArray())
        assertNotNull(reading)
        assertEquals(51591, reading.entityId)
        assertEquals(265, reading.total)
        assertEquals(240, reading.base)
        assertEquals(25, reading.dynamic)
    }

    @Test
    fun parsesSingleValueIncreaseUpdate() {
        val payload = ArrayList<Byte>()
        payload += 0x01
        payload += 0x08
        payload += 0x01
        payload += varInt(51591)
        payload += varInt(780)
        payload += 0x01
        payload += int32le(15)
        val reading = OdeEnergyParser.parseUpdate(payload.toByteArray())
        assertNotNull(reading)
        assertEquals(780, reading.total)
        assertNull(reading.base)
        assertEquals(780, reading.dynamic)
    }

    @Test
    fun parsesSnapshotBaseAndDynamic() {
        val payload = ArrayList<Byte>()
        payload += 0x00
        payload += 0x0C
        payload += 0x01
        payload += varInt(51591)
        payload += varInt(240)
        payload += varInt(375)
        val reading = OdeEnergyParser.parseSnapshot(payload.toByteArray())
        assertNotNull(reading)
        assertEquals(615, reading.total)
        assertEquals(240, reading.base)
        assertEquals(375, reading.dynamic)
        assertEquals(true, reading.isSnapshot)
    }

    @Test
    fun rejectsGarbageUpdateHeader() {
        assertNull(OdeEnergyParser.parseUpdate(byteArrayOf(0x02, 0x08, 0x01, 0x01, 0x02)))
    }

    private fun varInt(value: Int): List<Byte> = OdeEnergyParser.encodeVarInt(value).toList()

    private fun int32le(value: Int): List<Byte> = listOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )
}
