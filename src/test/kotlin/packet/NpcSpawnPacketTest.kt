package com.tbread.packet

import com.tbread.data.DataManager
import kotlin.test.Test
import kotlin.test.assertEquals

class NpcSpawnPacketTest {
    @Test
    fun mapsDummyFromNpcCreateOpcode() {
        DataManager.load()
        DataManager.hardReset()
        val instanceId = 44001
        val dummyCode = 2300229
        StreamProcessor().onPacketReceived(npcCreatePacket(instanceId, dummyCode), 0L)
        assertEquals(dummyCode, DataManager.mobId(instanceId))
        assertEquals("훈련용 허수아비", DataManager.mob(dummyCode)?.name)
        assertEquals(true, DataManager.mob(dummyCode)?.isDummy)
    }

    private fun npcCreatePacket(instanceId: Int, mobCode: Int): ByteArray {
        val body = ArrayList<Byte>()
        body += 0x20
        body += 0x40
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
