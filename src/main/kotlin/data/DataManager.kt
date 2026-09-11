package com.tbread.data

import com.tbread.data.repository.*
import com.tbread.entity.*
import com.tbread.packet.OdeEnergyParser
import com.tbread.util.SkillCodes
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object DataManager {
    private val logger = LoggerFactory.getLogger(DataManager::class.java)

    private val resetEpoch = AtomicLong(0)

    fun currentEpoch(): Long = resetEpoch.get()

    /*
    rawPacket 버퍼 영역
     */
    private val rawPacketBuffer = ConcurrentLinkedDeque<RawPacket>()

    fun saveRawPacket(data: ByteArray, timestamp: Long) {
        rawPacketBuffer.add(RawPacket(data, timestamp))
    }

    fun rawPacketsInRange(from: Long, to: Long): List<RawPacket> {
        return rawPacketBuffer.filter { it.timestamp in from..to }
    }

    private val mobIdRepository = MobIdRepository()
    private val mobRepository = MobRepository()
    private val userRepository = UserRepository()
    private val packetRepository = PacketRepository()
    private val summonRepository = SummonRepository()
    private val battleLogRepository = BattleLogRepository()
    private val skillRepository = SkillRepository()
    private val mobHpRepository = MobHpRepository()
    private val useBuffRepository = UseBuffRepository()
    private val buffRepository = BuffRepository()

    private val buffBlacklist = mutableSetOf<Int>()

    fun isBuffBlacklisted(code: Int): Boolean = code in buffBlacklist

    fun load() {
        loadMobJson()
        loadSkillJson()
        loadBuffJson()
        loadCustomBuffJson()
        loadBuffBlacklistJson()
    }

    @Volatile
    private var dummyNameMatcherList: List<Pair<ByteArray, Int>> = emptyList()

    private fun loadMobJson() {
        val mobJson = object {}.javaClass.getResourceAsStream("/json/mobs.json")
            ?.bufferedReader()
            ?.readText()!!
        Json.decodeFromString<List<Mob>>(mobJson).forEach { saveMob(it) }
        rebuildDummyNameMatchers()
    }

    private fun rebuildDummyNameMatchers() {
        val names = LinkedHashMap<String, Int>()
        names["근접 훈련용 허수아비"] = 2300229
        names["훈련용 허수아비"] = 2300229
        names["훈련용 허수아비 (표본)"] = 2090773
        for (mob in mobRepository.dummies()) {
            names.putIfAbsent(mob.name, mob.code)
        }
        dummyNameMatcherList = names.entries
            .sortedByDescending { it.key.toByteArray(Charsets.UTF_8).size }
            .map { it.key.toByteArray(Charsets.UTF_8) to it.value }
    }

    fun dummyNameMatchers(): List<Pair<ByteArray, Int>> = dummyNameMatcherList

    private fun loadSkillJson() {
        val skillJson = object {}.javaClass.getResourceAsStream("/json/skills.json")
            ?.bufferedReader()
            ?.readText()!!
        Json.decodeFromString<List<Skill>>(skillJson).forEach {
            saveSkill(it)
        }
    }

    private fun loadBuffJson() {
        try {
            val buffJson = object {}.javaClass.getResourceAsStream("/json/buff.json")
                ?.bufferedReader()
                ?.readText()!!

            val json = Json { ignoreUnknownKeys = true }

            json.decodeFromString<JsonObject>(buffJson).forEach { (code, element) ->
                val obj = element.jsonObject
                val buff = obj["effect"]?.jsonPrimitive?.contentOrNull?.let {
                    obj["summary"]?.jsonPrimitive?.contentOrNull?.let { it1 ->
                        Buff(
                            code = code.toInt(),
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            summary = it1,
                            effect = it
                        )
                    }
                }
                buff?.let { saveBuff(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadBuffBlacklistJson() {
        try {
            val json = object {}.javaClass.getResourceAsStream("/json/buff_blacklist.json")
                ?.bufferedReader()?.readText() ?: return
            Json.decodeFromString<JsonObject>(json)["blacklist"]
                ?.jsonArray
                ?.forEach { buffBlacklist.add(it.jsonPrimitive.int) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCustomBuffJson() {
        try {
            val buffJson = object {}.javaClass.getResourceAsStream("/json/buff_custom.json")
                ?.bufferedReader()
                ?.readText()!!

            val json = Json { ignoreUnknownKeys = true }

            json.decodeFromString<JsonObject>(buffJson).forEach { (code, element) ->
                val obj = element.jsonObject
                val buff = obj["effect"]?.jsonPrimitive?.contentOrNull?.let {
                    obj["summary"]?.jsonPrimitive?.contentOrNull?.let { it1 ->
                        Buff(
                            code = code.toInt(),
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            summary = it1,
                            effect = it
                        )
                    }
                }
                buff?.let { saveBuff(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun hardReset() {
        resetEpoch.incrementAndGet()
        battleLogRepository.flush()
        mobHpRepository.flush()
        mobIdRepository.flush()
        packetRepository.flush()
        summonRepository.flush()
        userRepository.flush()
        rawPacketBuffer.clear()
        lastDummyHitTime = 0
    }

    /*
    mobHp 영역
     */

    fun mobHp(mobId: Int): Int? {
        return mobHpRepository.get(mobId)
    }

    fun mobHp(mobId: Int, mobHp: Int) {
        mobHpRepository.set(mobId, mobHp)
    }

    /*
    skill 영역
     */
    private fun saveSkill(skill: Skill) {
        return skillRepository.save(skill.code, skill)
    }

    fun skill(skillId: Long): Skill? {
        return skillRepository.get(skillId)
    }


    /*
    packet 영역
     */
    fun battleData(targetId: Int): CopyOnWriteArrayList<ParsedDamagePacket>? {
        if (packetRepository.currentTarget() == 0) {
            return CopyOnWriteArrayList(packetRepository.getAll().values.flatten().filter {
                !existMobId(it.getTargetId())
            })
        }
        return packetRepository.get(targetId)
    }

    fun currentTarget(): Int {
        return packetRepository.currentTarget()
    }

    fun isCurrentTargetDummy(): Boolean {
        val current = currentTarget()
        if (current <= 0) return false
        return mobId(current)?.let { mob(it) }?.isDummy == true
    }

    fun executorId(): Int = userRepository.executor()

    @Volatile
    private var lastDummyHitTime: Long = 0
    private val DUMMY_TIMEOUT_MS = 5000L

    fun touchDummyBattle(mobId: Int, epoch: Long) {
        touchDummyOrUnmappedBattle(mobId, epoch)
    }

    // 카탈로그에 isDummy로 등록된 허수아비뿐 아니라, 스폰 패킷을 못 받은 고정 NPC
    // (레기온기지 허수아비 등)도 전투로 잡아야 미터기가 숫자만 보여주고 멈추지 않습니다.
    fun touchDummyOrUnmappedBattle(targetId: Int, epoch: Long) {
        if (resetEpoch.get() != epoch) return
        val mobCode = mobId(targetId)
        val mappedDummy = mobCode != null && mob(mobCode)?.isDummy == true
        val unmapped = mobCode == null
        if (!mappedDummy && !unmapped) return

        val now = System.currentTimeMillis()
        if (currentTarget() <= 0) {
            lastDummyHitTime = now
            saveCurrentBattleStart()
            saveCurrentTarget(targetId)
            return
        }
        if (currentTarget() == targetId) {
            lastDummyHitTime = now
        }
    }

    fun checkDummyTimeout() {
        val current = currentTarget()
        if (current <= 0) return
        val mappedDummy = isCurrentTargetDummy()
        val unmapped = mobId(current) == null
        if (!mappedDummy && !unmapped) return
        if (System.currentTimeMillis() - lastDummyHitTime > DUMMY_TIMEOUT_MS) {
            saveCurrentBattleEnd(lastDummyHitTime)
            saveCurrentTarget(-1)
            lastDummyHitTime = 0
        }
    }

    @Synchronized
    fun startBattle(mobId: Int) {
        if (currentTarget() == mobId) {
            val now = System.currentTimeMillis()
            val preemptivePackets = packetRepository.get(mobId)
                ?.filter { it.getTimeStamp() >= now - 1000L }
                ?.toList()
            flushPacket()
            preemptivePackets?.forEach { packetRepository.save(it) }
        }
        saveCurrentBattleStart()
        saveCurrentTarget(mobId)
    }

    fun endBattle(mobId: Int) {
        if (currentTarget() != mobId) return
        saveCurrentBattleEnd()
        saveCurrentTarget(-1)
    }

    fun currentBattleStart(): Long {
        return packetRepository.currentBattleStart()
    }

    fun currentBattleEnd(): Long {
        return packetRepository.currentBattleEnd()
    }

    private fun saveCurrentBattleStart() {
        packetRepository.saveCurrentBattleStart()
    }

    private fun saveCurrentBattleEnd(time: Long = System.currentTimeMillis()) {
        packetRepository.saveCurrentBattleEnd(time)
    }

    private fun saveCurrentTarget(targetId: Int) {
        packetRepository.currentTarget(targetId)
    }

    @Synchronized
    fun flushPacket() {
        packetRepository.flush()
        packetRepository.currentTarget(-1)
        packetRepository.flushBattleTime()
        lastDummyHitTime = 0
    }

    @Synchronized
    fun saveDamage(pdp: ParsedDamagePacket, epoch: Long) {
        if (resetEpoch.get() != epoch) return
        packetRepository.save(pdp)
    }


    /*
    battleLog 영역
     */
    fun saveBattleLog(data: DpsReport, encounter: EncounterSnapshot? = null) {
        val snapshot = data.copy(
            contributors = data.contributors.mapTo(mutableSetOf()) { it.copy() }
        )
        val packets = rawPacketsInRange(data.battleStart - 5000L, data.battleEnd)
        battleLogRepository.save(DpsLog(snapshot, summonRepository.getAll(), packets, encounter))
        rawPacketBuffer.removeIf { it.timestamp <= data.battleEnd }
    }

    fun recentBattleList(): List<Pair<Int, DpsReport>> {
        val battleList: MutableList<Pair<Int, DpsReport>> = mutableListOf()
        val battleLogs = battleLogRepository.getAll()
        battleLogs.forEachIndexed { idx, it ->
            battleList.add(Pair(idx, it.report))
        }
        return battleList
    }

    fun battleLog(idx: Int): DpsLog? {
        return battleLogRepository.get(idx)
    }


    /*
    summon 영역
     */
    fun summonerId(summonId: Int): Int? {
        return summonRepository.get(summonId)
    }

    fun summonMap(): Map<Int, Int> = summonRepository.getAll()

    fun saveSummon(summonId: Int, summonerId: Int) {
        summonRepository.save(summonId, summonerId)
    }


    /*
    mobId 영역
     */
    fun mobId(mobId: Int): Int? {
        return mobIdRepository.get(mobId)?.code?.takeIf { it > 0 }
    }

    fun mobMaxHp(mobId: Int): Int? {
        return mobIdRepository.get(mobId)?.maxHp?.takeIf { it > 0 }
    }

    fun saveMobId(mid: Int, code: Int) {
        mobIdRepository.save(mid, code)
    }

    fun unmappedCombatEntityIds(): List<Int> {
        val current = currentTarget()
        if (current > 0 && mobId(current) == null && user(current) == null) {
            return listOf(current)
        }
        return emptyList()
    }

    fun saveMobMaxHp(mid: Int, maxHp: Int) {
        mobIdRepository.saveMaxHp(mid, maxHp)
    }

    private fun existMobId(mobId: Int): Boolean {
        return mobIdRepository.exist(mobId)
    }


    /*
    mob 영역
     */
    fun mob(mobCode: Int): Mob? {
        return mobRepository.get(mobCode)
    }

    private fun saveMob(mob: Mob) {
        mobRepository.save(mob.code, mob)
    }


    /*
    user 영역
     */
    fun user(uid: Int): User? {
        return userRepository.get(uid)
    }

    fun usersMissingNickname(): List<User> {
        return userRepository.all().filter { it.nickname.isNullOrBlank() }
    }

    fun saveUser(uid: Int, user: User) {
        userRepository.save(uid, user)
    }

    fun saveUser(user: User) {
        userRepository.savePending(user)
    }

    fun findUserByNicknameAndServer(nickname: String, server: Int): User? {
        return userRepository.findByNicknameAndServer(nickname, server)
    }

    fun saveNickname(uid: Int, nickname: String, isExecutor: Boolean = false,server:Int) {
        val user = userRepository.get(uid) ?: User(uid, nickname, server, null, isExecutor).also {
            userRepository.save(uid, it)
        }
        user.nickname = nickname
        if (isExecutor) {
            saveExecutorId(uid)
        }
    }

    private fun saveExecutorId(uid: Int) {
        val executor = userRepository.executor()
        if (executor != uid) {
            if (executor != 0) {
                userRepository.get(executor)!!.isExecutor = false
            }
            userRepository.executor(uid)
            userRepository.get(uid)!!.isExecutor = true
            resetOdeEnergy()
        }
    }

    /*
    buff 영역
     */
    fun saveUseBuff(uid: Int, useBuff: UseBuff) {
        useBuffRepository.save(uid, useBuff)
    }

    fun battleBuff(uid:Int,start:Long,end:Long): List<UseBuff> {
        return useBuffRepository.findOverlapping(uid,start,end)
    }

    @Volatile
    var odeEnergy: Int? = null
        private set

    @Volatile
    var shugoKeys: Int? = null
        private set

    @Volatile
    private var odeKnownIdValue: Int? = null

    @Volatile
    private var odeBase: Int = 0

    fun odeKnownId(): Int? = odeKnownIdValue

    fun applyOdeEnergy(reading: OdeEnergyParser.Reading) {
        val known = odeKnownIdValue
        if (known != null && reading.entityId != known && !reading.isSnapshot) return
        odeKnownIdValue = reading.entityId
        if (reading.base != null && reading.dynamic != null) {
            odeBase = reading.base
            odeEnergy = reading.total
            return
        }
        if (reading.isSnapshot) {
            odeBase = reading.base ?: 0
            odeEnergy = reading.total
            return
        }
        odeEnergy = odeBase + reading.total
    }

    internal fun resetOdeEnergy() {
        odeEnergy = null
        shugoKeys = null
        odeKnownIdValue = null
        odeBase = 0
    }

    fun trackerStatus(): TrackerStatus {
        val uid = executorId()
        val now = System.currentTimeMillis()
        val latest = if (uid == 0) emptyMap() else useBuffRepository.latestBySkillCode(uid)
        val grouped = LinkedHashMap<Int, TrackedBuff>()
        for (buff in latest.values) {
            val base = SkillCodes.base(buff.skillCode)
            val remaining = (buff.buffEnd - now).coerceAtLeast(0L)
            val existing = grouped[base]
            if (existing == null || remaining >= existing.remainingMs) {
                grouped[base] = TrackedBuff(
                    skillCode = base,
                    name = resolveBuffName(buff.skillCode, base),
                    remainingMs = remaining,
                    durationMs = buff.duration.coerceAtLeast(1L),
                )
            }
        }
        return TrackerStatus(
            odeEnergy = odeEnergy,
            shugoKeys = shugoKeys,
            buffs = grouped.values.toList(),
        )
    }

    private fun resolveBuffName(skillCode: Int, base: Int): String? {
        return buff(skillCode)?.name
            ?: skill(skillCode.toLong())?.name
            ?: skill(base.toLong())?.name
    }

    fun buff(buffCode:Int):Buff?{
        return buffRepository.get(buffCode)
    }

    private fun saveBuff(buff: Buff){
        buffRepository.save(buff)
    }
}