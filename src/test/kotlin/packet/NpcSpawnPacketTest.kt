package com.tbread.packet

import com.tbread.data.DataManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NpcSpawnPacketTest {
    @Test
    fun mapsDummyFromNpcCreateOpcode() {
        DataManager.load()
        DataManager.hardReset()
        val instanceId = 44001
        val dummyCode = 2300229
        StreamProcessor().onPacketReceived(npcCreatePacket(instanceId, dummyCode, opcode = 0x40), 0L)
        assertEquals(dummyCode, DataManager.mobId(instanceId))
        assertEquals("훈련용 허수아비", DataManager.mob(dummyCode)?.name)
        assertEquals(true, DataManager.mob(dummyCode)?.isDummy)
    }

    @Test
    fun mapsDummyFromUnknownNpcAppearanceOpcode() {
        DataManager.load()
        DataManager.hardReset()
        val instanceId = 44002
        val dummyCode = 2300229
        StreamProcessor().onPacketReceived(npcCreatePacket(instanceId, dummyCode, opcode = 0x42), 0L)
        assertEquals(dummyCode, DataManager.mobId(instanceId))
        assertEquals("훈련용 허수아비", DataManager.mob(dummyCode)?.name)
    }

    @Test
    fun harvestsCatalogCodeForUnmappedCombatTarget() {
        DataManager.load()
        DataManager.hardReset()
        val instanceId = 44003
        val dummyCode = 2300229
        DataManager.saveMobMaxHp(instanceId, 129475)
        DataManager.touchDummyOrUnmappedBattle(instanceId, DataManager.currentEpoch())
        assertNull(DataManager.mobId(instanceId))

        StreamProcessor().onPacketReceived(catalogHintPacket(instanceId, dummyCode), 0L)
        assertEquals(dummyCode, DataManager.mobId(instanceId))
        assertEquals(129475, DataManager.mobMaxHp(instanceId))
        assertEquals("훈련용 허수아비", DataManager.mob(dummyCode)?.name)
    }

    @Test
    fun harvestsDummyUtf8NameForUnmappedCombatTarget() {
        DataManager.load()
        DataManager.hardReset()
        val instanceId = 44004
        DataManager.saveMobMaxHp(instanceId, 129475)
        DataManager.touchDummyOrUnmappedBattle(instanceId, DataManager.currentEpoch())
        assertNull(DataManager.mobId(instanceId))

        StreamProcessor().onPacketReceived(utf8NamePacket(instanceId, "근접 훈련용 허수아비"), 0L)
        assertEquals(2300229, DataManager.mobId(instanceId))
        assertEquals("훈련용 허수아비", DataManager.mob(2300229)?.name)
    }

    @Test
    fun ignoresCatalogHintWhenNotTheCurrentTarget() {
        DataManager.load()
        DataManager.hardReset()
        val instanceId = 44005
        StreamProcessor().onPacketReceived(catalogHintPacket(instanceId, 2300229), 0L)
        assertNull(DataManager.mobId(instanceId))
    }

    private fun npcCreatePacket(instanceId: Int, mobCode: Int, opcode: Int): ByteArray {
        val body = ArrayList<Byte>()
        body += 0x20
        body += opcode.toByte()
        body += 0x36
        body += varInt(instanceId)
        body += 0x00
        body += 0x10
        body += 0x00
        body += (mobCode and 0xFF).toByte()
        body += ((mobCode ushr 8) and 0xFF).toByte()
        body += ((mobCode ushr 16) and 0xFF).toByte()
        body += ((mobCode ushr 24) and 0xFF).toByte()
        return body.toByteArray()
    }

    private fun catalogHintPacket(instanceId: Int, mobCode: Int): ByteArray {
        val body = ArrayList<Byte>()
        body += 0x30
        body += 0x01
        body += 0x02
        body += varInt(instanceId)
        body += 0x00
        body += 0x10
        body += 0x00
        body += (mobCode and 0xFF).toByte()
        body += ((mobCode ushr 8) and 0xFF).toByte()
        body += ((mobCode ushr 16) and 0xFF).toByte()
        body += ((mobCode ushr 24) and 0xFF).toByte()
        return body.toByteArray()
    }

    private fun utf8NamePacket(instanceId: Int, name: String): ByteArray {
        val utf8 = name.toByteArray(Charsets.UTF_8)
        val body = ArrayList<Byte>()
        body += 0x40
        body += 0x01
        body += 0x02
        body += varInt(instanceId)
        body += 0x07
        body += varInt(utf8.size)
        body += utf8.toList()
        return body.toByteArray()
    }

    private fun varInt(value: Int): List<Byte> {
        val out = ArrayList<Byte>()
        var v = value
        while (v > 0x7F) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out
    }
}
