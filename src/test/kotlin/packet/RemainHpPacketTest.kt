package com.tbread.packet

import com.tbread.data.DataManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemainHpPacketTest {
    @Test
    fun storesHpForUnmappedDummy() {
        DataManager.hardReset()
        val instanceId = 91001
        val hp = 129475
        StreamProcessor().onPacketReceived(remainHpPacket(instanceId, hp), 0L)
        assertEquals(hp, DataManager.mobHp(instanceId))
        assertEquals(hp, DataManager.mobMaxHp(instanceId))
        assertNull(DataManager.mobId(instanceId))
    }

    @Test
    fun keepsMaxHpWhenLaterHitsAreLower() {
        DataManager.hardReset()
        val instanceId = 91002
        StreamProcessor().onPacketReceived(remainHpPacket(instanceId, 200000), 0L)
        StreamProcessor().onPacketReceived(remainHpPacket(instanceId, 150000), 0L)
        assertEquals(150000, DataManager.mobHp(instanceId))
        assertEquals(200000, DataManager.mobMaxHp(instanceId))
    }

    private fun remainHpPacket(instanceId: Int, hp: Int): ByteArray {
        val body = ArrayList<Byte>()
        body += 0x10
        body += 0x00
        body += 0x8D.toByte()
        body += varInt(instanceId)
        body += 0x00
        body += 0x00
        body += 0x00
        body += (hp and 0xFF).toByte()
        body += ((hp ushr 8) and 0xFF).toByte()
        body += ((hp ushr 16) and 0xFF).toByte()
        body += ((hp ushr 24) and 0xFF).toByte()
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
