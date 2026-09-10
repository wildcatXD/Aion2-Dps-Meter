package com.tbread.packet

import com.tbread.data.DataManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NicknamePacketTest {
    @Test
    fun ownNicknameWithoutExtraFlag() {
        val uid = 81001
        val nick = "TestUserA"
        StreamProcessor().onPacketReceived(ownNicknamePacket(uid, nick, extraFlag = false), 0L)
        val user = DataManager.user(uid)
        assertEquals(nick, user?.nickname)
        assertTrue(user?.isExecutor == true)
    }

    @Test
    fun ownNicknameWithExtraFlag() {
        val uid = 81002
        val nick = "TestUserB"
        StreamProcessor().onPacketReceived(ownNicknamePacket(uid, nick, extraFlag = true), 0L)
        val user = DataManager.user(uid)
        assertEquals(nick, user?.nickname)
        assertTrue(user?.isExecutor == true)
    }

    @Test
    fun otherNicknameWithExtraFlag() {
        val uid = 81003
        val nick = "PartyMember"
        StreamProcessor().onPacketReceived(otherNicknamePacket(uid, nick, extraFlag = true), 0L)
        val user = DataManager.user(uid)
        assertEquals(nick, user?.nickname)
        assertTrue(user?.isExecutor == false)
    }

    private fun ownNicknamePacket(uid: Int, nickname: String, extraFlag: Boolean): ByteArray {
        val name = nickname.toByteArray(Charsets.UTF_8)
        val body = ArrayList<Byte>()
        body += 0x40
        if (extraFlag) body += 0xF0.toByte()
        body += 0x33
        body += 0x36
        body += varInt(uid)
        body += 0x07
        body += varInt(name.size)
        body += name.toList()
        body += 0xE9.toByte()
        body += 0x03
        body += 0x05
        return body.toByteArray()
    }

    private fun otherNicknamePacket(uid: Int, nickname: String, extraFlag: Boolean): ByteArray {
        val name = nickname.toByteArray(Charsets.UTF_8)
        val body = ArrayList<Byte>()
        body += 0x50
        if (extraFlag) body += 0xF0.toByte()
        body += 0x44
        body += 0x36
        body += varInt(uid)
        body += varInt(0)
        body += varInt(0)
        body += 0x00
        body += varInt(name.size)
        body += name.toList()
        body += 0x05
        body += 0xE9.toByte()
        body += 0x03
        body += 0x04
        body += "길드".toByteArray(Charsets.UTF_8).toList()
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
