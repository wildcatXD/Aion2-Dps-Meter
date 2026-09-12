package com.tbread

import com.tbread.data.DataManager
import com.tbread.entity.ParsedDamagePacket
import com.tbread.packet.StreamProcessor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DummyBattleLogTest {
    private val overlayJson = Json { encodeDefaults = true }

    @Test
    fun dummyFightShowsLiveDpsAndSavesHistory() {
        DataManager.load()
        DataManager.hardReset()
        val calc = DpsCalculator()
        val instanceId = 44099
        val actorId = 81111
        DataManager.saveMobId(instanceId, 2300229)
        DataManager.saveNickname(actorId, "Tester", true, 1001)
        val epoch = DataManager.currentEpoch()
        val hit = ParsedDamagePacket()
        hit.setTargetId(StreamProcessor.VarIntOutput(instanceId, 2))
        hit.setActorId(StreamProcessor.VarIntOutput(actorId, 3))
        hit.setDamage(StreamProcessor.VarIntOutput(50_000, 3))
        hit.setTimestamp(System.currentTimeMillis())
        DataManager.saveDamage(hit, epoch)
        DataManager.touchDummyOrUnmappedBattle(instanceId, epoch)

        val live = calc.getDps()
        assertEquals(50_000.0, live.information[actorId]?.amount)
        assertEquals("훈련용 허수아비", live.target?.mob?.name)

        DataManager.endBattle(instanceId)
        calc.getDps()
        val history = DataManager.recentBattleList()
        assertEquals(1, history.size)
        assertEquals("훈련용 허수아비", history[0].second.target?.mob?.name)
        assertEquals(50_000.0, history[0].second.information[actorId]?.amount)
    }

    @Test
    fun dummyTimeoutKeepsLastFightOnMeterWithoutDuplicatingHistory() {
        DataManager.load()
        DataManager.hardReset()
        val calc = DpsCalculator()
        val instanceId = 44101
        val actorId = 81113
        DataManager.saveMobId(instanceId, 2300229)
        DataManager.saveNickname(actorId, "Tester", true, 1001)
        val epoch = DataManager.currentEpoch()
        val hit = ParsedDamagePacket()
        hit.setTargetId(StreamProcessor.VarIntOutput(instanceId, 2))
        hit.setActorId(StreamProcessor.VarIntOutput(actorId, 3))
        hit.setDamage(StreamProcessor.VarIntOutput(50_000, 3))
        hit.setTimestamp(System.currentTimeMillis())
        DataManager.saveDamage(hit, epoch)
        DataManager.touchDummyOrUnmappedBattle(instanceId, epoch)

        calc.getDps()
        DataManager.endBattle(instanceId)
        val after = calc.getDps()
        assertEquals(50_000.0, after.information[actorId]?.amount)
        assertEquals("훈련용 허수아비", after.target?.mob?.name)

        val encoded = overlayJson.encodeToString(after)
        assertTrue(encoded.contains("Tester"))
        assertTrue(encoded.contains("훈련용 허수아비"))

        calc.getDps()
        calc.getDps()
        assertEquals(1, DataManager.recentBattleList().size)
    }

    @Test
    fun dummyDamageShowsWhenActorIsNotExecutor() {
        DataManager.load()
        DataManager.hardReset()
        val calc = DpsCalculator()
        val instanceId = 44100
        val actorId = 81112
        DataManager.saveMobId(instanceId, 2300229)
        DataManager.saveNickname(99999, "Other", true, 1001)
        DataManager.saveNickname(actorId, "DummyHitter", false, 1001)
        val epoch = DataManager.currentEpoch()
        val hit = ParsedDamagePacket()
        hit.setTargetId(StreamProcessor.VarIntOutput(instanceId, 2))
        hit.setActorId(StreamProcessor.VarIntOutput(actorId, 3))
        hit.setDamage(StreamProcessor.VarIntOutput(12_000, 3))
        hit.setTimestamp(System.currentTimeMillis())
        DataManager.saveDamage(hit, epoch)
        DataManager.touchDummyOrUnmappedBattle(instanceId, epoch)

        val live = calc.getDps()
        assertEquals(12_000.0, live.information[actorId]?.amount)
        assertTrue(live.information.isNotEmpty())
    }
}
