package com.tbread

import com.tbread.config.PropertyHandler
import com.tbread.data.DataManager
import com.tbread.entity.*
import com.tbread.entity.enums.JobClass
import com.tbread.entity.enums.SpecialDamage
import com.tbread.ndps.NdpsSynergy
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

class DpsCalculator(private val streamResetCallback: (() -> Unit)? = null) {
    private val logger = LoggerFactory.getLogger(DpsCalculator::class.java)

    private var currentTarget: Int = 0
    private var recentTargetWasDummy: Boolean = false

    private var recentData = DpsReport()
    private var recentDataSaved = false

    private var lastProcessedCount = 0
    private val cachedInfo = HashMap<Int, DpsInformation>()
    private val cachedNdpsAmount = HashMap<Int, Double>()
    private val cachedContributors = mutableSetOf<User>()
    private var cachedBattleEnd = 0L
    private var cachedBattleStart = 0L
    private var isCachedBattleStartFake = false

    private fun resetCache() {
        lastProcessedCount = 0
        cachedInfo.clear()
        cachedNdpsAmount.clear()
        cachedContributors.clear()
        cachedBattleEnd = 0L
        cachedBattleStart = 0L
        isCachedBattleStartFake = false
    }

    private fun battleData(): CopyOnWriteArrayList<ParsedDamagePacket>? {
        return DataManager.battleData(currentTarget)
    }

    fun getRecentData(): DpsReport {
        return recentData
    }

    fun getLiveReport(): DpsReport {
        val storageTarget = DataManager.currentTarget()
        if (storageTarget == -1) return recentData
        return DpsReport(
            battleStart = DataManager.currentBattleStart(),
            battleEnd = DataManager.currentBattleEnd(),
            packets = DataManager.battleData(storageTarget)
        )
    }

    fun getDps(): DpsReport {
        val storageTarget = DataManager.currentTarget()
        val data = DataManager.battleData(storageTarget)
        val prevTargetDummy = DataManager.isCurrentTargetDummy()
        val isNewBattleEnd = storageTarget == -1 && storageTarget != currentTarget
        if (storageTarget != currentTarget && !prevTargetDummy
            && storageTarget != -1 && currentTarget != -1
        ) {
            DataManager.saveBattleLog(recentData, buildEncounterSnapshot(recentData))
            recentDataSaved = true
        }
        if (storageTarget != currentTarget) {
            resetCache()
        }
        currentTarget = storageTarget
        recentTargetWasDummy = prevTargetDummy
        if (currentTarget == -1) {
            val battleEnd = DataManager.currentBattleEnd()
            DataManager.flushPacket()
            if (isNewBattleEnd) {
                recentData.battleEnd = battleEnd
            }
            if (isNewBattleEnd && !recentData.isEmpty() && !recentTargetWasDummy) {
                DataManager.saveBattleLog(recentData, buildEncounterSnapshot(recentData))
                recentDataSaved = true
            }
            return recentData
        }

        val currentCount = data?.size ?: 0
        if (currentCount > lastProcessedCount) {
            for (i in lastProcessedCount until currentCount) {
                val packet = data!![i]
                val actor = DataManager.summonerId(packet.getActorId()) ?: packet.getActorId()
                var user = DataManager.user(actor)
                if (user == null) {
                    user = User(actor, nickname = null)
                    DataManager.saveUser(user.id, user)
                    logger.info("닉네임 없이 전투 참가자 등록 uid={}", actor)
                }
                cachedContributors.remove(user)
                cachedContributors.add(user)
                if (user.job == null) {
                    user.job = JobClass.convertFromSkill(packet.getSkillCode1())
                }
                val damage = packet.effectiveDamage().toDouble()
                cachedInfo.getOrPut(user.id) { DpsInformation() }.addDamage(damage)
                val extra = NdpsSynergy.partyExtraAmp(user.id, packet.getTimeStamp())
                cachedNdpsAmount[user.id] = (cachedNdpsAmount[user.id] ?: 0.0) +
                    NdpsSynergy.normalizeHit(damage, extra)
                val ts = packet.getTimeStamp()
                if (cachedBattleStart == 0L) {
                    cachedBattleStart = ts; isCachedBattleStartFake = true
                }
                if (isCachedBattleStartFake && cachedBattleStart > ts) cachedBattleStart = ts
                if (ts > cachedBattleEnd) cachedBattleEnd = ts
            }
            lastProcessedCount = currentCount
        }

        val dmStart = DataManager.currentBattleStart()
        val dmEnd = DataManager.currentBattleEnd()
        val report = DpsReport(
            contributors = cachedContributors.toMutableSet(),
            battleStart = when {
                dmStart != 0L && cachedBattleStart != 0L -> minOf(dmStart, cachedBattleStart)
                dmStart != 0L -> dmStart
                else -> cachedBattleStart
            },
            battleEnd = maxOf(dmEnd, cachedBattleEnd),
            packets = data
        )

        if (currentTarget > 0) {
            // 소환/스폰 패킷을 못 받은 고정 NPC는 mob 카탈로그에 없을 수 있습니다.
            // 예전엔 !!로 강제 단정해서 NPE가 났고, 그 다음에는 타겟을 비워 "타겟 인식 실패"만
            // 보였습니다. 지금은 인스턴스 id라도 있으면 "미확인 대상"으로 표시합니다.
            val mobCode = DataManager.mobId(currentTarget)
            val mob = mobCode?.let { DataManager.mob(it) } ?: Mob(0, "미확인 대상", false)
            report.target = MobInfo(currentTarget, mob)
            report.target!!.remainHp = DataManager.mobHp(currentTarget) ?: 0
            report.target!!.maxHp = DataManager.mobMaxHp(currentTarget) ?: 0
        }

        val totalDamage = cachedInfo.values.sumOf { it.amount }
        val duration = report.battleEnd - report.battleStart
        val mobMaxHp = DataManager.mobMaxHp(currentTarget)?.toDouble() ?: 0.0
        cachedInfo.forEach { (uid, cached) ->
            val nAmount = cachedNdpsAmount[uid] ?: cached.amount
            report.information[uid] = DpsInformation(
                amount = cached.amount,
                dps = if (duration > 0) cached.amount / duration * 1000 else 0.0,
                contribution = if (totalDamage > 0) cached.amount / totalDamage * 100 else 0.0,
                entireContribution = if (mobMaxHp > 0) cached.amount / mobMaxHp * 100 else 0.0,
                nAmount = nAmount,
                nDps = if (duration > 0) nAmount / duration * 1000 else 0.0,
            )
        }

        cachedContributors.forEach { cached ->
            DataManager.user(cached.id)?.let { live ->
                cached.nickname = live.nickname
                cached.isExecutor = live.isExecutor
                cached.server = live.server
                if (cached.job == null) cached.job = live.job
            }
        }

        if (DataManager.executorId() == 0 && report.contributors.size == 1) {
            report.contributors.first().isExecutor = true
        }

        if (DataManager.isCurrentTargetDummy()) {
            val executorId = DataManager.executorId()
            if (executorId != 0 && report.contributors.none { it.isExecutor || it.id == executorId }) {
                return recentData
            }
        }

        recentData = report
        recentDataSaved = false
        return report
    }

    fun battleDetails(data: DpsReport?, uid: Int): HashMap<String, AnalyzedSkill> {
        val analyzedData: HashMap<String, AnalyzedSkill> = hashMapOf()
        if (data == null) {
            return analyzedData
        }
        data.packets?.forEach {
            val skill = DataManager.skill(it.getSkillCode1().toLong())
            val skillName = it.getSkillCode1().toString()
            val realActor = DataManager.summonerId(it.getActorId()) ?: it.getActorId()
            if (realActor == uid) {
                if (!analyzedData.containsKey(skillName)) {
                    val analyzedSkill = AnalyzedSkill(it)
                    analyzedSkill.name = skill?.name ?: it.getSkillCode1().toString()
                    analyzedData[skillName] = analyzedSkill
                }
                val analyzedSkill = analyzedData[skillName]!!
                if (it.isDoT()) {
                    analyzedSkill.dotTimes++
                    analyzedSkill.dotDamageAmount += it.effectiveDamage().toInt()
                } else {
                    analyzedSkill.times++
                    analyzedSkill.damageAmount += it.effectiveDamage().toInt()
                    if (it.isCrit()) analyzedSkill.critTimes++
                    if (it.getSpecials().contains(SpecialDamage.BACK)) analyzedSkill.backTimes++
                    if (it.getSpecials().contains(SpecialDamage.PARRY)) analyzedSkill.parryTimes++
                    if (it.getSpecials().contains(SpecialDamage.DOUBLE)) analyzedSkill.doubleTimes++
                    if (it.getSpecials().contains(SpecialDamage.PERFECT)) analyzedSkill.perfectTimes++
                    if (it.getSpecials().contains(SpecialDamage.POWER_SHARD)) analyzedSkill.shardTimes++
                    if (it.getLoop() != 0) analyzedSkill.multiHitTimes++
                }
            }
        }
        return analyzedData
    }

    fun getBuffOperatingRate(uid: Int, start: Long, end: Long): List<OperatingData> {
        val totalDuration = end - start
        if (totalDuration <= 0) return emptyList()

        return DataManager.battleBuff(uid, start, end)
            .filter { !DataManager.isBuffBlacklisted(it.skillCode) }
            .groupBy { it.skillCode to it.actorId }
            .map { (key, buffs) ->
                val (skillCode, actorId) = key
                val buff = DataManager.buff(skillCode)
                val clamped = buffs
                    .map { maxOf(it.buffStart, start) to minOf(it.buffEnd, end) }
                    .sortedBy { it.first }

                val merged = mutableListOf<Pair<Long, Long>>()
                for (interval in clamped) {
                    if (merged.isEmpty() || interval.first > merged.last().second) {
                        merged.add(interval)
                    } else {
                        val last = merged.removeLast()
                        merged.add(last.first to maxOf(last.second, interval.second))
                    }
                }

                val rate = merged.sumOf { it.second - it.first }.toDouble() / totalDuration * 100.0
                OperatingData(skillCode, buff, rate, actorId)
            }
    }

    fun resetDataStorage() {
        if (!recentData.isEmpty() && !recentDataSaved && !DataManager.isCurrentTargetDummy()) {
            DataManager.saveBattleLog(recentData, buildEncounterSnapshot(recentData))
            recentDataSaved = true
        }
        DataManager.flushPacket()
        currentTarget = -1
        recentData = DpsReport()
        recentDataSaved = false
        resetCache()
        logger.info("대상 데미지 누적 데이터 초기화 완료")
    }

    fun hardReset() {
        DataManager.hardReset()
        streamResetCallback?.invoke()
        currentTarget = -1
        recentData = DpsReport()
        recentDataSaved = false
        resetCache()
        logger.info("전체 강제 초기화 완료")
    }

    private fun fillNdps(report: DpsReport) {
        val packets = report.packets ?: return
        if (packets.isEmpty()) return
        val duration = (report.battleEnd - report.battleStart).coerceAtLeast(0L)
        val sums = HashMap<Int, Double>()
        for (packet in packets) {
            val actor = DataManager.summonerId(packet.getActorId()) ?: packet.getActorId()
            val extra = NdpsSynergy.partyExtraAmp(actor, packet.getTimeStamp())
            sums[actor] = (sums[actor] ?: 0.0) +
                NdpsSynergy.normalizeHit(packet.effectiveDamage().toDouble(), extra)
        }
        for ((uid, nAmount) in sums) {
            val info = report.information[uid] ?: continue
            info.nAmount = nAmount
            info.nDps = if (duration > 0) nAmount / duration * 1000 else 0.0
        }
    }

    private fun buildEncounterSnapshot(report: DpsReport): EncounterSnapshot {
        fillNdps(report)
        val durationMs = (report.battleEnd - report.battleStart).coerceAtLeast(0L)
        val players = report.contributors.map { user ->
            val info = report.information[user.id]
            EncounterPlayerSnapshot(
                id = user.id,
                nickname = user.nickname,
                server = user.server,
                job = user.job?.className,
                isSelf = user.isExecutor,
                combatPower = user.power,
                damage = info?.amount ?: 0.0,
                dps = info?.dps ?: 0.0,
                nDamage = info?.nAmount ?: 0.0,
                nDps = info?.nDps ?: 0.0,
                sharePercent = info?.contribution ?: 0.0,
                skills = battleDetails(report, user.id).values.map { skill ->
                    EncounterSkillSnapshot(
                        skillCode = skill.skillCode,
                        name = skill.name,
                        damageAmount = skill.damageAmount,
                        dotDamageAmount = skill.dotDamageAmount,
                        dotTimes = skill.dotTimes,
                        hitTimes = skill.times,
                        critTimes = skill.critTimes,
                        backTimes = skill.backTimes,
                        perfectTimes = skill.perfectTimes,
                        doubleTimes = skill.doubleTimes,
                        parryTimes = skill.parryTimes,
                        shardTimes = skill.shardTimes,
                        multiHitTimes = skill.multiHitTimes,
                    )
                }.sortedByDescending { it.damageAmount + it.dotDamageAmount },
                buffs = getBuffOperatingRate(user.id, report.battleStart, report.battleEnd).map { buff ->
                    EncounterBuffSnapshot(
                        code = buff.code,
                        name = buff.name,
                        summary = buff.summary,
                        effect = buff.effect,
                        uptimePercent = buff.operatingRate,
                        actorId = buff.actorId,
                    )
                }.sortedByDescending { it.uptimePercent },
            )
        }.sortedByDescending { it.damage }
        val target = report.target?.let {
            EncounterTargetSnapshot(
                id = it.id,
                code = it.mob.code,
                name = it.mob.name,
                boss = it.mob.boss,
                remainHp = it.remainHp,
                maxHp = it.maxHp,
            )
        }
        return EncounterSnapshot(
            meterVersion = PropertyHandler.getProperty("version") ?: "unknown",
            battleStart = report.battleStart,
            battleEnd = report.battleEnd,
            durationMs = durationMs,
            target = target,
            players = players,
        )
    }
}