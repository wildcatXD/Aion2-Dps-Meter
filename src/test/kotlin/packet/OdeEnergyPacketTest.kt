package com.tbread.packet

import com.tbread.data.DataManager
import kotlin.test.Test
import kotlin.test.assertEquals

class OdeEnergyPacketTest {
    @Test
    fun snapshotFillsTrackerOdeEnergy() {
        DataManager.hardReset()
        DataManager.resetOdeEnergy()
        val payload = ArrayList<Byte>()
        payload += 0x20
        payload += 0x0B
        payload += 0x61
        payload += 0x0C
        payload += 0x01
        payload += varInt(51591)
        payload += varInt(240)
        payload += varInt(25)
        StreamProcessor().onPacketReceived(payload.toByteArray(), 0L)
        assertEquals(265, DataManager.odeEnergy)
        assertEquals(51591, DataManager.odeKnownId())
        assertEquals(265, DataManager.trackerStatus().odeEnergy)
    }

    @Test
    fun updateAddsToSnapshotBase() {
        DataManager.hardReset()
        DataManager.resetOdeEnergy()
        val snap = ArrayList<Byte>()
        snap += 0x20
        snap += 0x0B
        snap += 0x61
        snap += 0x0C
        snap += 0x01
        snap += varInt(51591)
        snap += varInt(240)
        snap += varInt(0)
        StreamProcessor().onPacketReceived(snap.toByteArray(), 0L)

        val update = ArrayList<Byte>()
        update += 0x20
        update += 0x0C
        update += 0x61
        update += 0x01
        update += 0x08
        update += 0x01
        update += varInt(51591)
        update += varInt(40)
        update += 0x01
        update += 0x28
        update += 0x00
        update += 0x00
        update += 0x00
        StreamProcessor().onPacketReceived(update.toByteArray(), 0L)
        assertEquals(280, DataManager.odeEnergy)
    }

    private fun varInt(value: Int): List<Byte> = OdeEnergyParser.encodeVarInt(value).toList()
}
