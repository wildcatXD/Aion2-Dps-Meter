package com.tbread.packet

import com.tbread.addon.PacketAddonManager
import com.tbread.data.DataManager
import com.tbread.entity.JoinRequestUser
import com.tbread.entity.ParsedDamagePacket
import com.tbread.entity.UseBuff
import com.tbread.entity.User
import com.tbread.entity.enums.JobClass
import com.tbread.entity.enums.SpecialDamage
import net.jpountz.lz4.LZ4Factory
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

class StreamProcessor() {
    private val logger = LoggerFactory.getLogger(StreamProcessor::class.java)

    data class VarIntOutput(val value: Int, val length: Int)

    private val mask = 0x0f

    private val decompressFactory = LZ4Factory.fastestInstance()
    private val decompressor = decompressFactory.fastDecompressor()

    private sealed class Opcode(b1: Int, b2: Int) {
        val key: Int = b1 or (b2 shl 8)

        object OwnNickname   : Opcode(0x33, 0x36)
        object OtherNickname : Opcode(0x44, 0x36)
        object OtherNickname2 : Opcode(0x45, 0x36)
        object Summon        : Opcode(0x41, 0x36)
        object NpcSpawn      : Opcode(0x40, 0x36)
        object Damage        : Opcode(0x04, 0x38)
        object DoT           : Opcode(0x05, 0x38)
        object BuffApply     : Opcode(0x2A, 0x38)
        object BuffApply2    : Opcode(0x2B, 0x38)
        object BattleToggle  : Opcode(0x21, 0x8D)
        object RemainHp      : Opcode(0x00, 0x8D)
        object OdeEnergy     : Opcode(0x0C, 0x61)
        object StatsSnapshot : Opcode(0x0B, 0x61)
        object JoinRequest   : Opcode(0x07, 0x97)
        object CancelJoin    : Opcode(0x25, 0x97)
        object AdmitJoin     : Opcode(0x0B, 0x97)
        object RefuseJoin    : Opcode(0x09, 0x97)
        object InstanceStart : Opcode(0x18, 0x97)
        object ExitParty     : Opcode(0x1D, 0x97)
    }

    private val seenUnknownOpcodes = ConcurrentHashMap.newKeySet<Int>()

    private val handlers: Map<Int, (ByteArray, VarIntOutput, Boolean, Long, Long) -> Unit> = mapOf(
        Opcode.OwnNickname.key   to { packet, lengthInfo, extraFlag, _, arrivedAt    -> searchOwnNickname(packet, lengthInfo, extraFlag, arrivedAt) },
        Opcode.OtherNickname.key to { packet, lengthInfo, extraFlag, _, arrivedAt    -> searchOtherNickname(packet, lengthInfo, extraFlag, arrivedAt) },
        Opcode.OtherNickname2.key to { packet, lengthInfo, extraFlag, _, arrivedAt    -> searchOtherNickname(packet, lengthInfo, extraFlag, arrivedAt) },
        Opcode.Summon.key        to { packet, _, extraFlag, _, _                     -> parseSummonPacket(packet, extraFlag) },
        Opcode.NpcSpawn.key      to { packet, _, extraFlag, _, _                     -> parseSummonPacket(packet, extraFlag) },
        Opcode.Damage.key        to { packet, _, extraFlag, epoch, arrivedAt         -> parsingDamage(packet, extraFlag, epoch, arrivedAt) },
        Opcode.DoT.key           to { packet, _, extraFlag, epoch, arrivedAt         -> parseDoTPacket(packet, extraFlag, epoch, arrivedAt) },
        Opcode.BuffApply.key     to { packet, lengthInfo, extraFlag, _, arrivedAt    -> parseBuffPacket(packet, lengthInfo, extraFlag, arrivedAt) },
        Opcode.BuffApply2.key    to { packet, lengthInfo, extraFlag, _, arrivedAt    -> parseBuffPacket(packet, lengthInfo, extraFlag, arrivedAt) },
        Opcode.BattleToggle.key  to { packet, lengthInfo, extraFlag, _, _            -> parseBattlePacket(packet, lengthInfo, extraFlag) },
        Opcode.RemainHp.key      to { packet, lengthInfo, extraFlag, _, _            -> parseRemainHp(packet, lengthInfo, extraFlag) },
        Opcode.OdeEnergy.key     to { packet, lengthInfo, extraFlag, _, _            -> parseOdeEnergy(packet, lengthInfo, extraFlag, snapshot = false) },
        Opcode.StatsSnapshot.key to { packet, lengthInfo, extraFlag, _, _            -> parseOdeEnergy(packet, lengthInfo, extraFlag, snapshot = true) },
        Opcode.JoinRequest.key   to { packet, lengthInfo, extraFlag, _, arrivedAt    -> parseJoinRequestPacket(packet, lengthInfo, extraFlag, arrivedAt) },
        Opcode.CancelJoin.key    to { packet, lengthInfo, extraFlag, _, _            -> parseCancelJoinRequest(packet, lengthInfo, extraFlag) },
        Opcode.AdmitJoin.key     to { packet, lengthInfo, extraFlag, _, _            -> parseAdmitJoinRequest(packet, lengthInfo, extraFlag) },
        Opcode.RefuseJoin.key    to { packet, lengthInfo, extraFlag, _, _            -> parseRefuseJoinRequest(packet, lengthInfo, extraFlag) },
        Opcode.InstanceStart.key to { packet, lengthInfo, extraFlag, _, _            -> parseInstanceStartPacket(packet, lengthInfo, extraFlag) },
        Opcode.ExitParty.key     to { packet, lengthInfo, extraFlag, _, _            -> parseExitParty(packet, lengthInfo, extraFlag) },
    )

    fun onPacketReceived(packet: ByteArray, arrivedAt: Long) {
        if (packet.size == 3) return

//        DataManager.saveRawPacket(packet, arrivedAt)

        val epoch = DataManager.currentEpoch()

        val lengthInfo = readVarInt(packet)
        val extraFlag = (packet[lengthInfo.length] >= 0xf0.toByte() && packet[lengthInfo.length] < 0xff.toByte())
        if (extraFlag) {
            if (packet[lengthInfo.length + 1] == 0xff.toByte() && packet[lengthInfo.length + 2] == 0xff.toByte()) {
                decompressPacket(packet, lengthInfo.length, true, epoch, arrivedAt)
                return
            }
        } else {
            if (packet[lengthInfo.length] == 0xff.toByte() && packet[lengthInfo.length + 1] == 0xff.toByte()) {
                decompressPacket(packet, lengthInfo.length, false, epoch, arrivedAt)
                return
            }
        }

        val opcodeOffset = lengthInfo.length + if (extraFlag) 1 else 0
        if (opcodeOffset + 1 >= packet.size) return

        val opcodeKey = (packet[opcodeOffset].toInt() and 0xFF) or ((packet[opcodeOffset + 1].toInt() and 0xFF) shl 8)
        val handler = handlers[opcodeKey]
        if (handler != null) {
            handler.invoke(packet, lengthInfo, extraFlag, epoch, arrivedAt)
        } else {
            if (seenUnknownOpcodes.add(opcodeKey)) {
                val b1 = packet[opcodeOffset].toInt() and 0xFF
                val b2 = packet[opcodeOffset + 1].toInt() and 0xFF
                logger.info(
                    "미처리 옵코드 0x{} 0x{} size={}",
                    "%02X".format(b1),
                    "%02X".format(b2),
                    packet.size,
                )
            }
            // 본인 닉네임 옵코드가 패치로 바뀌었거나, 캐릭터 선택 전에 미터기를 못 켠
            // 경우에는 0x36 계열 패킷을 같은 레이아웃으로 한 번 더 시도합니다.
            if ((packet[opcodeOffset + 1].toInt() and 0xFF) == 0x36) {
                val nickOk = parseOwnNicknameBody(
                    packet,
                    opcodeOffset + 2,
                    arrivedAt,
                    isExecutor = DataManager.executorId() == 0,
                    logFailures = false,
                )
                if (!nickOk) {
                    tryParseUnknownNpcAppearance(packet, opcodeOffset)
                }
            }
        }
        harvestNicknamesForKnownActors(packet)
        harvestMobCatalogForUnmappedTargets(packet)
    }

    private fun decompressPacket(
        packet: ByteArray,
        headerLength: Int,
        extraFlag: Boolean,
        epoch: Long,
        arrivedAt: Long
    ) {
        try {
            var offset = headerLength + 2
            if (extraFlag) {
                offset += 1
            }
            val originLength = parseUInt32le(packet, offset)
            offset += 4
            val restored = ByteArray(originLength)
            decompressor.decompress(packet, offset, restored, 0, originLength)

            var innerOffset = 0
            while (innerOffset < restored.size) {
                val pastInnerOffset = innerOffset
                val lengthInfo = readVarInt(restored, innerOffset)
                if (lengthInfo.value == 0) {
                    innerOffset += 1
                    continue
                }

                val realLength = lengthInfo.value + lengthInfo.length - 4
                if (realLength <= 0) {
                    logger.error("패킷 길이 체크에서 오류발생 {}, 오프셋 {}", toHex(packet), innerOffset)
                    break
                }

                onPacketReceived(restored.copyOfRange(pastInnerOffset, pastInnerOffset + realLength), arrivedAt)
                innerOffset += realLength
            }
        } catch (e: Exception) {
            logger.error("패킷 압축 해제중 에러", e)
        }
        logger.trace("압축 패킷 해제 종료")
    }

    private fun searchOwnNickname(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean, arrivedAt: Long) {
        var offset = lengthInfo.length
        if (extraFlag) offset++
        if (packet.size < offset + 2) return
        if (packet[offset] != 0x33.toByte()) return
        if (packet[offset + 1] != 0x36.toByte()) return
        parseOwnNicknameBody(packet, offset + 2, arrivedAt, isExecutor = true, logFailures = true)
    }

    private fun parseOwnNicknameBody(
        packet: ByteArray,
        start: Int,
        arrivedAt: Long,
        isExecutor: Boolean,
        logFailures: Boolean,
    ): Boolean {
        var offset = start
        if (packet.size < offset) return false

        val userInfo = readVarInt(packet, offset, silent = !logFailures)
        if (userInfo.length < 0) return false

        offset += userInfo.length
        if (offset >= packet.size) return false

        if (packet.size < offset + 10) {
            if (logFailures) logger.warn("본인 닉네임 패킷이 너무 짧습니다 uid={} ", userInfo.value)
            return false
        }
        val spliterIdx = findArrayIndex(packet.copyOfRange(offset, offset + 10), 0x07)
        if (spliterIdx == -1) {
            if (logFailures) logger.warn("본인 닉네임 패킷에서 구분자(0x07)를 찾지 못했습니다 uid={}", userInfo.value)
            return false
        }
        offset += spliterIdx + 1

        val nameLengthInfo = readVarInt(packet, offset, silent = !logFailures)
        offset += nameLengthInfo.length
        if (nameLengthInfo.length > 71) return false
        if (offset >= packet.size) return false
        if (offset + nameLengthInfo.value > packet.size) return false

        var server = -1
        var job = -1
        val np = packet.copyOfRange(offset, offset + nameLengthInfo.value)
        val nickname = String(np, Charsets.UTF_8)
        if (!isValidNickname(nickname)) {
            if (logFailures) logger.warn("본인 닉네임 문자열이 유효하지 않습니다 uid={} raw={}", userInfo.value, nickname)
            return false
        }

        offset += nameLengthInfo.value
        if (packet.size >= offset + 2) {
            server = ByteBuffer.wrap(packet, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort()
                .toInt() and 0xffff
            offset += 2

            if (packet.size >= offset + 1) {
                job = packet[offset].toInt() and 0xff
            }
        }
        DataManager.saveNickname(userInfo.value, nickname, isExecutor, server)
        if (job >= 0) {
            DataManager.user(userInfo.value)?.let { user ->
                if (user.job == null) user.job = JobClass.convertFromCode(job)
            }
        }
        logger.info(
            "본인 닉네임 탐지 uid={} nick={} server={} executor={}",
            userInfo.value,
            nickname,
            server,
            isExecutor,
        )
        PacketAddonManager.parse(packet, arrivedAt)
        return true
    }

    /**
     * 캐릭터 선택 전에 미터기를 못 켜서 본인 닉네임 패킷을 놓친 경우,
     * 이미 딜로 등록된 uid 가 다른 패킷에 이름과 같이 다시 나타나면 그때 붙입니다.
     */
    private fun harvestNicknamesForKnownActors(packet: ByteArray) {
        val missing = DataManager.usersMissingNickname()
        if (missing.isEmpty()) return
        for (user in missing) {
            if (user.id <= 0) continue
            val encoded = encodeVarInt(user.id)
            if (encoded.size < 2) continue
            val idx = findArrayIndex(packet, encoded)
            if (idx == -1) continue
            val extracted = extractNicknameAfter(packet, idx + encoded.size) ?: continue
            val markExecutor = DataManager.executorId() == 0
            DataManager.saveNickname(user.id, extracted.first, markExecutor, extracted.second)
            logger.info(
                "닉네임 보조탐지 uid={} nick={} server={} executor={}",
                user.id,
                extracted.first,
                extracted.second,
                markExecutor,
            )
        }
    }

    private fun encodeVarInt(value: Int): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (v > 0x7F) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    private fun extractNicknameAfter(packet: ByteArray, from: Int): Pair<String, Int>? {
        if (from >= packet.size) return null
        var offset = from
        if (from + 10 <= packet.size) {
            val idx = findArrayIndex(packet.copyOfRange(from, from + 10), 0x07)
            if (idx != -1) offset = from + idx + 1
        }
        if (offset >= packet.size) return null
        val lenInfo = readVarInt(packet, offset, silent = true)
        if (lenInfo.length <= 0 || lenInfo.value < 2 || lenInfo.value > 24) return null
        offset += lenInfo.length
        if (offset + lenInfo.value > packet.size) return null
        val nickname = String(packet.copyOfRange(offset, offset + lenInfo.value), Charsets.UTF_8)
        if (!isValidNickname(nickname) || nickname.length > 16) return null
        offset += lenInfo.value
        var server = -1
        if (offset + 2 <= packet.size) {
            val candidate = ByteBuffer.wrap(packet, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short.toInt() and 0xffff
            if (candidate in 1001..1021 || candidate in 2001..2021) server = candidate
        }
        return nickname to server
    }

    /**
     * 고정 NPC 스폰(`0x40 0x36`)을 놓친 뒤에도, 전투 중인 미매핑 인스턴스 id가
     * 나중에 카탈로그 코드나 허수아비 이름과 같이 오면 그때 붙입니다.
     */
    private fun harvestMobCatalogForUnmappedTargets(packet: ByteArray) {
        val missing = DataManager.unmappedCombatEntityIds()
        if (missing.isEmpty()) return
        for (entityId in missing) {
            if (entityId <= 0) continue
            val encoded = encodeVarInt(entityId)
            if (encoded.size < 2) continue
            val idx = findArrayIndex(packet, encoded)
            if (idx == -1) continue
            if (trySaveNpcCatalogCode(packet, idx + encoded.size, entityId, requireCatalog = true)) {
                logger.info(
                    "몹 카탈로그 보조탐지 id={} code={} name={}",
                    entityId,
                    DataManager.mobId(entityId),
                    DataManager.mobId(entityId)?.let { DataManager.mob(it)?.name },
                )
                continue
            }
            if (trySaveDummyNameFromPacket(packet, entityId)) {
                logger.info(
                    "허수아비 이름 보조탐지 id={} code={} name={}",
                    entityId,
                    DataManager.mobId(entityId),
                    DataManager.mobId(entityId)?.let { DataManager.mob(it)?.name },
                )
            }
        }
    }

    /**
     * 0x40/0x41 이 아닌 0x36 계열 외형 갱신. 전투력 패킷(0x56 0x36)은 제외합니다.
     */
    private fun tryParseUnknownNpcAppearance(packet: ByteArray, opcodeOffset: Int) {
        if (opcodeOffset + 2 >= packet.size) return
        val b1 = packet[opcodeOffset].toInt() and 0xFF
        if (b1 == 0x56) return
        if (packet[opcodeOffset + 1] != 0x36.toByte()) return
        var offset = opcodeOffset + 2
        val entity = readVarInt(packet, offset, silent = true)
        if (entity.length < 0 || entity.value <= 0) return
        if (DataManager.user(entity.value) != null) return
        if (DataManager.mobId(entity.value) != null) return
        offset += entity.length
        if (trySaveNpcCatalogCode(packet, offset, entity.value, requireCatalog = true)) {
            logger.info(
                "NPC 외형 보조탐지 opcode=0x{} 0x36 id={} code={}",
                "%02X".format(b1),
                entity.value,
                DataManager.mobId(entity.value),
            )
            return
        }
        trySaveDummyNameFromPacket(packet, entity.value)
    }

    private fun trySaveDummyNameFromPacket(packet: ByteArray, entityId: Int): Boolean {
        if (DataManager.mobId(entityId) != null) return false
        for ((utf8, code) in DataManager.dummyNameMatchers()) {
            if (utf8.isEmpty()) continue
            if (findArrayIndex(packet, utf8) == -1) continue
            DataManager.saveMobId(entityId, code)
            return true
        }
        return false
    }

    private fun searchOtherNickname(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean, arrivedAt: Long) {
        var offset = lengthInfo.length
        if (extraFlag) offset++
        if (packet.size < offset + 2) return
        val opcode1 = packet[offset].toInt() and 0xff
        if (opcode1 != 0x44 && opcode1 != 0x45) return
        if (packet[offset + 1] != 0x36.toByte()) return
        offset += 2
        if (packet.size < offset) return

        val userInfo = readVarInt(packet, offset)
        offset += userInfo.length
        if (packet.size < offset) return

        val unknownInfo1 = readVarInt(packet, offset)
        offset += unknownInfo1.length
        if (packet.size < offset) return

        val unknownInfo2 = readVarInt(packet, offset)
        offset += unknownInfo2.length
        if (packet.size < offset) return

        if (packet.size - offset <= 2) return
        offset += 1
        val base = offset
        //
        var nickname: String? = null
        var nickEndOffset = -1

        for (i in 0 until 5) {
            offset = base + i
            if (packet.size < offset) continue
            val nicknameLengthInfo = readVarInt(packet, offset)
            if (nicknameLengthInfo.length <= 0) continue

            offset += nicknameLengthInfo.length
            if (nicknameLengthInfo.value < 1 || nicknameLengthInfo.value > 71) continue
            if (packet.size < offset) continue
            if (packet.size < offset + nicknameLengthInfo.value) continue
            val np = packet.copyOfRange(offset, offset + nicknameLengthInfo.value)
            val candidate = String(np, Charsets.UTF_8)

            offset += nicknameLengthInfo.value
            if (!isValidNickname(candidate)) continue
            nickname = candidate
            nickEndOffset = offset
            break
        }
        if (nickname == null || nickEndOffset == -1) return

        offset = nickEndOffset

        val job = packet[offset].toInt() and 0xff
        offset += 1
        if (packet.size < offset) return
        val serverBase = offset

        var server = -1
        var legionName: String? = null
        var i = 0
        while (true) {
            offset = serverBase + i
            i++
            if (packet.size < offset + 2) break
            val serverCandidate = ByteBuffer.wrap(packet, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort()
                .toInt() and 0xffff
            if (!(serverCandidate in 1001..1021 || serverCandidate in 2001..2021)) continue
            offset += 2
            if (packet.size < offset) continue
            val LegionNameLengthInfo = readVarInt(packet, offset)
            if (LegionNameLengthInfo.value < 2 || LegionNameLengthInfo.value > 24) continue
            offset += LegionNameLengthInfo.length
            if (packet.size < offset + LegionNameLengthInfo.value) continue
            val lnp = packet.copyOfRange(offset, offset + LegionNameLengthInfo.value)
            val legionNameCandidate = String(lnp, Charsets.UTF_8)
            if (legionNameCandidate.any { !it.isDigit() }) {
                server = serverCandidate
            }
//            if (legionNameCandidate.all { it in '\uAC00'..'\uD7A3' }){
//                legionName = legionNameCandidate
//                break
//            }
            PacketAddonManager.parse(packet, arrivedAt)
        }
        DataManager.saveNickname(userInfo.value, nickname, false, server)
        if (job >= 0) {
            DataManager.user(userInfo.value)?.let { user ->
                if (user.job == null) user.job = JobClass.convertFromCode(job)
            }
        }
        logger.info("다른 유저 닉네임 탐지 uid={} nick={} server={} extraFlag={}", userInfo.value, nickname, server, extraFlag)
    }

    private fun parseDoTPacket(packet: ByteArray, extraFlag: Boolean, epoch: Long, arrivedAt: Long): Boolean {
        var offset = 0
        val pdp = ParsedDamagePacket()
        pdp.setDot(true)
        val packetLengthInfo = readVarInt(packet)
        if (packetLengthInfo.length < 0) return false
        offset += packetLengthInfo.length

        if (extraFlag) {
            offset += 1
        }
        if (packet[offset] != 0x05.toByte()) return false
        if (packet[offset + 1] != 0x38.toByte()) return false
        offset += 2
        if (packet.size < offset) return false

        val targetInfo = readVarInt(packet, offset)
        if (targetInfo.length < 0) return false
        offset += targetInfo.length
        if (packet.size < offset) return false
        pdp.setTargetId(targetInfo)


        val unknownBitFlagByte = packet[offset]
        if (unknownBitFlagByte.toInt() and 0x02 == 0) return true
        offset++
        // 0a -> 정상범주
        // 08 -> 실패
        // 02 -> 정상범주
        // 03 -> 정상범주
        // 비트플래그? 1010 / 0010 / 0011  성공 1000 실패 -> 두번째 비트?
        // 추후 나머지자리 비트플래그 체크 필요함


        if (packet.size < offset) return false

        val actorInfo = readVarInt(packet, offset)
        if (actorInfo.length < 0) return false
        if (actorInfo.value == targetInfo.value) return false
        offset += actorInfo.length
        if (packet.size < offset) return false
        pdp.setActorId(actorInfo)

        val unknownInfo = readVarInt(packet, offset)
        if (unknownInfo.length < 0) return false
        offset += unknownInfo.length

        val skillCodeCandidate = parseUInt32le(packet, offset)
        val skillCode: Int = if (DataManager.skill((skillCodeCandidate / 10).toLong()) != null) {
            skillCodeCandidate / 10
        } else {
            skillCodeCandidate / 100
        }
        offset += 4
        if (packet.size <= offset) return false
        pdp.setSkillCode(skillCode)

        val damageInfo = readVarInt(packet, offset)
        if (damageInfo.length < 0) return false
        pdp.setDamage(damageInfo)

        logger.debug("{}", toHex(packet))
        logger.debug(
            "도트데미지 공격자 {},피격자 {},스킬 {},데미지 {}",
            pdp.getActorId(),
            pdp.getTargetId(),
            pdp.getSkillCode1(),
            pdp.getDamage()
        )
        logger.debug("----------------------------------")
        if (pdp.getActorId() != pdp.getTargetId()) {
            pdp.setTimestamp(arrivedAt)
            DataManager.saveDamage(pdp, epoch)
            DataManager.touchDummyOrUnmappedBattle(pdp.getTargetId(), epoch)
        }
        return true

    }

    private fun findArrayIndex(data: ByteArray, vararg pattern: Int): Int {
        if (pattern.isEmpty()) return 0

        val p = ByteArray(pattern.size) { pattern[it].toByte() }

        val lps = IntArray(p.size)
        var len = 0
        for (i in 1 until p.size) {
            while (len > 0 && p[i] != p[len]) len = lps[len - 1]
            if (p[i] == p[len]) len++
            lps[i] = len
        }

        var i = 0
        var j = 0
        while (i < data.size) {
            if (data[i] == p[j]) {
                i++; j++
                if (j == p.size) return i - j
            } else if (j > 0) {
                j = lps[j - 1]
            } else {
                i++
            }
        }
        return -1
    }

    private fun findArrayIndex(data: ByteArray, p: ByteArray): Int {
        val lps = IntArray(p.size)
        var len = 0
        for (i in 1 until p.size) {
            while (len > 0 && p[i] != p[len]) len = lps[len - 1]
            if (p[i] == p[len]) len++
            lps[i] = len
        }

        var i = 0
        var j = 0
        while (i < data.size) {
            if (data[i] == p[j]) {
                i++; j++
                if (j == p.size) return i - j
            } else if (j > 0) {
                j = lps[j - 1]
            } else {
                i++
            }
        }
        return -1
    }

    private fun parseSummonPacket(packet: ByteArray, extraFlag: Boolean): Boolean {
        var offset = 0
        val packetLengthInfo = readVarInt(packet)
        if (packetLengthInfo.length < 0) return false
        offset += packetLengthInfo.length

        if (extraFlag) {
            offset += 1
        }


        if (packet[offset] != 0x41.toByte() && packet[offset] != 0x40.toByte()) return false
        if (packet[offset + 1] != 0x36.toByte()) return false
        val spawnOpcode = packet[offset].toInt() and 0xFF
        offset += 2

        val summonInfo = readVarInt(packet, offset)
        if (summonInfo.length < 0) return false
        offset += summonInfo.length

        trySaveNpcCatalogCode(packet, offset, summonInfo.value)

        val codeMarkerIdx = findArrayIndex(packet, 0x00, 0x40, 0x02)
            .takeIf { it != -1 }
            ?: findArrayIndex(packet, 0x00, 0x00, 0x02)
        if (codeMarkerIdx != -1) {
            val mobCode = (packet[codeMarkerIdx - 1].toInt() and 0xFF shl 16) or
                    (packet[codeMarkerIdx - 2].toInt() and 0xFF shl 8) or
                    (packet[codeMarkerIdx - 3].toInt() and 0xFF)
            if (isNpcCatalogCode(mobCode)) {
                DataManager.saveMobId(summonInfo.value, mobCode)
            }
            if (DataManager.mob(mobCode)?.boss == true) {
                PacketAddonManager.parsingMobSpawnAddon(packet,codeMarkerIdx,summonInfo.value,mobCode)
            }
        }

        if (spawnOpcode == 0x40) return true


        val keyIdx = findArrayIndex(packet, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)
        if (keyIdx == -1) return false
        val afterPacket = packet.copyOfRange(keyIdx + 8, packet.size)

        val opcodeIdx = findArrayIndex(afterPacket, 0x07, 0x02, 0x06)
        if (opcodeIdx == -1) return false
        offset = keyIdx + opcodeIdx + 11

        if (offset + 2 > packet.size) return false
        val realActorId = parseUInt16le(packet, offset)

        logger.debug("소환몹 맵핑 성공 {},{}", realActorId, summonInfo.value)
        DataManager.saveSummon(summonInfo.value, realActorId)
        return true
    }

    private fun isNpcCatalogCode(code: Int): Boolean = code in 2_000_000..2_999_999

    private fun trySaveNpcCatalogCode(
        packet: ByteArray,
        afterEntityId: Int,
        entityId: Int,
        requireCatalog: Boolean = false,
    ): Boolean {
        if (afterEntityId + 7 > packet.size) return false
        val tag0 = packet[afterEntityId].toInt() and 0xFF
        val tag1 = packet[afterEntityId + 1].toInt() and 0xFF
        val tag2 = packet[afterEntityId + 2].toInt() and 0xFF
        val likelyCarriesCode =
            ((tag1 == 0x10 || tag1 == 0x20 || tag1 == 0x21 || tag1 == 0x22 || tag1 == 0x30 || tag1 == 0x32) && tag2 == 0x00) ||
                (tag0 == 0x1C && tag1 == 0x00 && tag2 == 0x00)
        if (likelyCarriesCode) {
            val tagged = parseUInt32le(packet, afterEntityId + 3)
            if (isNpcCatalogCode(tagged) && (!requireCatalog || DataManager.mob(tagged) != null)) {
                DataManager.saveMobId(entityId, tagged)
                return true
            }
        }
        val scanEnd = minOf(afterEntityId + 16, packet.size - 4)
        var i = afterEntityId
        while (i <= scanEnd) {
            val code = parseUInt32le(packet, i)
            if (isNpcCatalogCode(code) && DataManager.mob(code) != null) {
                DataManager.saveMobId(entityId, code)
                return true
            }
            i++
        }
        return false
    }

    private fun parseOdeEnergy(
        packet: ByteArray,
        lengthInfo: VarIntOutput,
        extraFlag: Boolean,
        snapshot: Boolean,
    ) {
        var offset = lengthInfo.length
        if (extraFlag) offset++
        if (packet.size < offset + 2) return
        val b1 = packet[offset].toInt() and 0xFF
        val b2 = packet[offset + 1].toInt() and 0xFF
        if (snapshot) {
            if (b1 != 0x0B || b2 != 0x61) return
        } else if (b1 != 0x0C || b2 != 0x61) {
            return
        }
        val payload = packet.copyOfRange(offset + 2, packet.size)
        val reading = if (snapshot) {
            OdeEnergyParser.parseSnapshot(payload, DataManager.odeKnownId())
        } else {
            OdeEnergyParser.parseUpdate(payload)
        } ?: return
        DataManager.applyOdeEnergy(reading)
    }

    private fun parseUInt16le(packet: ByteArray, offset: Int = 0): Int {
        return (packet[offset].toInt() and 0xff) or ((packet[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun parseUInt32le(packet: ByteArray, offset: Int = 0): Int {
        require(offset + 4 <= packet.size) { "패킷 길이가 필요길이보다 짧음" }
        return ((packet[offset].toInt() and 0xFF)) or
                ((packet[offset + 1].toInt() and 0xFF) shl 8) or
                ((packet[offset + 2].toInt() and 0xFF) shl 16) or
                ((packet[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt32leAsLong(packet: ByteArray, offset: Int = 0): Long {
        require(offset + 4 <= packet.size) { "패킷 길이가 필요길이보다 짧음" }
        return ((packet[offset].toLong() and 0xFF)) or
                ((packet[offset + 1].toLong() and 0xFF) shl 8) or
                ((packet[offset + 2].toLong() and 0xFF) shl 16) or
                ((packet[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun readUInt64le(packet: ByteArray, offset: Int = 0): Long {
        require(offset + 8 <= packet.size) { "패킷 길이가 필요길이보다 짧음" }
        return ((packet[offset].toLong() and 0xFF)) or
                ((packet[offset + 1].toLong() and 0xFF) shl 8) or
                ((packet[offset + 2].toLong() and 0xFF) shl 16) or
                ((packet[offset + 3].toLong() and 0xFF) shl 24) or
                ((packet[offset + 4].toLong() and 0xFF) shl 32) or
                ((packet[offset + 5].toLong() and 0xFF) shl 40) or
                ((packet[offset + 6].toLong() and 0xFF) shl 48) or
                ((packet[offset + 7].toLong() and 0xFF) shl 56)
    }

    private fun parsingDamage(packet: ByteArray, extraFlag: Boolean, epoch: Long, arrivedAt: Long): Boolean {
        if (packet[0] == 0x20.toByte()) return false
        var offset = 0
        val packetLengthInfo = readVarInt(packet)
        if (packetLengthInfo.length < 0) return false
        val pdp = ParsedDamagePacket()

        offset += packetLengthInfo.length

        if (extraFlag) {
            offset += 1
        }
        if (offset >= packet.size) return false

        if (packet[offset] != 0x04.toByte()) return false
        if (packet[offset + 1] != 0x38.toByte()) return false
        offset += 2
        if (offset >= packet.size) return false
        val targetInfo = readVarInt(packet, offset)
        if (targetInfo.length < 0) return false
        pdp.setTargetId(targetInfo)
        offset += targetInfo.length //타겟
        if (offset >= packet.size) return false

        val switchInfo = readVarInt(packet, offset)
        if (switchInfo.length < 0) return false
        pdp.setSwitchVariable(switchInfo)
        offset += switchInfo.length //점프용
        if (offset >= packet.size) return false

        val flagInfo = readVarInt(packet, offset)
        if (flagInfo.length < 0) return false
        pdp.setFlag(flagInfo)
        offset += flagInfo.length //플래그
        if (offset >= packet.size) return false

        val actorInfo = readVarInt(packet, offset)
        if (actorInfo.length < 0) return false
        pdp.setActorId(actorInfo)
        offset += actorInfo.length
        if (offset >= packet.size) return false

        if (offset + 5 >= packet.size) return false

        val temp = offset

        var skillCode = parseUInt32le(packet, offset)
        if (DataManager.skill(skillCode.toLong()) == null) {
            skillCode = (skillCode / 10) * 10
        }
        pdp.setSkillCode(skillCode)

        offset = temp + 5

        val typeInfo = readVarInt(packet, offset)
        if (typeInfo.length < 0) return false
        pdp.setType(typeInfo)
        offset += typeInfo.length
        if (offset >= packet.size) return false

        val damageType = packet[offset]

        val andResult = switchInfo.value and mask
        val start = offset
        var tempV = 0
        tempV += when (andResult) {
            4 -> 8
            5 -> 12
            6 -> 10
            7 -> 14
            else -> return false
        }
        if (start + tempV > packet.size) return false
        pdp.setSpecials(parseSpecialDamageFlags(packet.copyOfRange(start, start + tempV)))
        if (pdp.getSpecials().contains(SpecialDamage.Restoration)){
            offset += 2
        }
        offset += tempV


        if (offset >= packet.size) return false

        val unknownInfo = readVarInt(packet, offset)
        if (unknownInfo.length < 0) return false
        pdp.setUnknown(unknownInfo)
        offset += unknownInfo.length
        if (offset >= packet.size) return false

        val damageInfo = readVarInt(packet, offset)
        if (damageInfo.length < 0) return false
        pdp.setDamage(damageInfo)
        offset += damageInfo.length
        if (offset >= packet.size) return false

        val multiHitInfo = tryParseMultiHit(packet, offset)
        val multiHitCount = multiHitInfo.time
        pdp.setLoop(multiHitCount)
        offset = multiHitInfo.newOffset + 2

        if (pdp.getActorId() != pdp.getTargetId()) {
            //추후 hps 를 넣는다면 수정하기
                pdp.setTimestamp(arrivedAt)
                DataManager.saveDamage(pdp, epoch)
                DataManager.touchDummyOrUnmappedBattle(pdp.getTargetId(), epoch)
        }
        return true

    }

    private data class MultiHitOutput(val time: Int, val damage: Int, val newOffset: Int)

    private fun tryParseMultiHit(data: ByteArray, offset: Int): MultiHitOutput {
        var currentOffset = offset
        val count = data[currentOffset].toInt() and 0xFF
        currentOffset++

        if (count == 0) {
            return MultiHitOutput(0, 0, offset)
        }

        if (currentOffset >= data.size) return MultiHitOutput(0, 0, offset)
        val damageInfo = readVarInt(data, currentOffset)
        if (damageInfo.value == -1) return MultiHitOutput(0, 0, offset)
        currentOffset += damageInfo.length

        if (damageInfo.value == 0) {
            return MultiHitOutput(0, 0, offset)
        }

        // 나머지 (count-1)개가 동일한 값인지 검증
        repeat(count - 1) {
            if (currentOffset >= data.size) return MultiHitOutput(0, 0, offset)
            val nextDamage = readVarInt(data, currentOffset)
            if (nextDamage.value == -1) return MultiHitOutput(0, 0, offset)
            currentOffset += nextDamage.length
            if (nextDamage.value != damageInfo.value) {
                return MultiHitOutput(0, 0, offset)
            }
        }

        return MultiHitOutput(count, damageInfo.value, currentOffset)
    }

    fun toHex(bytes: ByteArray): String {
        //출력테스트용
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    fun readVarInt(bytes: ByteArray, offset: Int = 0, silent: Boolean = false): VarIntOutput {
        var value = 0
        var shift = 0
        var count = 0

        while (true) {
            if (offset + count >= bytes.size) {
                if (!silent) logger.error("배열범위초과, 패킷 {} 오프셋 {} count {}", toHex(bytes), offset, count)
                return VarIntOutput(-1, -1)
            }

            val byteVal = bytes[offset + count].toInt() and 0xff
            count++

            value = value or (byteVal and 0x7F shl shift)

            if ((byteVal and 0x80) == 0) {
                return VarIntOutput(value, count)
            }

            shift += 7
            if (shift >= 32) {
                logger.trace(
                    "가변정수 오버플로우, 패킷 {} 오프셋 {} shift {}",
                    toHex(bytes.copyOfRange(offset, offset + 4)),
                    offset,
                    shift
                )
                return VarIntOutput(-1, -1)
            }
        }
    }

    private fun parseSpecialDamageFlags(packet: ByteArray): List<SpecialDamage> {
        val flags = mutableListOf<SpecialDamage>()

        if (packet.size == 8) {
            return emptyList()
        }
        if (packet.size >= 10) {
            val flagByte = packet[0].toInt() and 0xFF

            if ((flagByte and 0x01) != 0) {
                flags.add(SpecialDamage.BACK)
            }
            if ((flagByte and 0x02) != 0) {
                flags.add(SpecialDamage.UNKNOWN)
            }

            if ((flagByte and 0x04) != 0) {
                flags.add(SpecialDamage.PARRY)
            }

            if ((flagByte and 0x08) != 0) {
                flags.add(SpecialDamage.PERFECT)
            }

            if ((flagByte and 0x10) != 0) {
                flags.add(SpecialDamage.DOUBLE)
            }

            if ((flagByte and 0x20) != 0) {
                flags.add(SpecialDamage.ENDURE)
            }

            if ((flagByte and 0x40) != 0) {
                flags.add(SpecialDamage.Restoration)
            }

//            if ((flagByte and 0x80) != 0) {
//                flags.add(SpecialDamage.POWER_SHARD)
//            }
        }
        return flags
    }

    private fun isValidNickname(str: String): Boolean {
        val hasKoreanOrEnglish = str.any { it in '\uAC00'..'\uD7A3' || it in 'a'..'z' || it in 'A'..'Z' }
        val allValid = str.all {
            it in '\uAC00'..'\uD7A3' ||  // 한글 완성형
                    it in 'a'..'z' ||            // 영어 소문자
                    it in 'A'..'Z' ||            // 영어 대문자
                    it.isDigit()                 // 숫자
        }
        return hasKoreanOrEnglish && allValid
    }

    private fun parseBattlePacket(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x21.toByte()) return false
        if (packet[offset + 1] != 0x8d.toByte()) return false
        offset += 2

        val battleInfo = readVarInt(packet, offset)
        if (battleInfo.length <= 0) return false
        offset += battleInfo.length

        offset += readVarInt(packet,offset).length
        val toggleInfo = readVarInt(packet,offset)


        val mobCode = DataManager.mobId(battleInfo.value) ?: return true
        val mob = DataManager.mob(mobCode) ?: return true
        if (!mob.boss || mob.isDummy) return true

        when (toggleInfo.value) {
            1 -> DataManager.startBattle(battleInfo.value)
            0 -> DataManager.endBattle(battleInfo.value)
        }
        return true
    }

    private fun parseJoinRequestPacket(
        packet: ByteArray,
        lengthInfo: VarIntOutput,
        extraFlag: Boolean,
        arrivedAt: Long
    ): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x07.toByte()) return false
        if (packet[offset + 1] != 0x97.toByte()) return false
        offset += 2

        val roomNum = parseUInt32le(packet, offset)
        offset += 4

        val requester = parseUInt32le(packet, offset)
        offset += 4
        val unknown2 = parseUInt32le(packet, offset)
        offset += 4
        val job = parseUInt32le(packet, offset)
        offset += 4
        val unknown4 = parseUInt32le(packet, offset)
        offset += 4
        val unknown5 = parseUInt32le(packet, offset) // 여기 첫 2바이트 varint uid 값 가능성있음
        offset += 4

        val nicknameLengthInfo = readVarInt(packet, offset)
        offset += nicknameLengthInfo.length
        val np = packet.copyOfRange(offset, offset + nicknameLengthInfo.value)
        offset += nicknameLengthInfo.value

        val server = ByteBuffer.wrap(packet, offset, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getShort()
            .toInt() and 0xffff
        offset += 6

        val power = parseUInt32le(packet, offset)
        val realClass = JobClass.convertFromCode(job)
        val request = PacketAddonManager.processingUser(
            JoinRequestUser(
                String(np, Charsets.UTF_8),
                power,
                realClass?.className,
                server,
                requester,
                arrivedAt
            )
        )
        println("닉네임: ${String(np, Charsets.UTF_8)} 전투력: $power 직업:${realClass?.className} 코드:$job 서버:$server")
        PacketEventBus.events.tryEmit(PacketEvent.JoinRequest(request))
        val user = DataManager.findUserByNicknameAndServer(String(np, Charsets.UTF_8), server)
        if (user == null) {
            DataManager.saveUser(User(-1, String(np, Charsets.UTF_8), server, power = power))
            return true
        }
        user.power = power
        return true
    }

    private fun parseCancelJoinRequest(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x25.toByte()) return false
        if (packet[offset + 1] != 0x97.toByte()) return false
        offset += 2

        val requester = parseUInt32le(packet, offset)
        PacketEventBus.events.tryEmit(PacketEvent.JoinRequestRemove(requester))
        return true
    }

    private fun parseAdmitJoinRequest(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x0B.toByte()) return false
        if (packet[offset + 1] != 0x97.toByte()) return false
        offset += 2

        val requester = parseUInt32le(packet, offset)
        PacketEventBus.events.tryEmit(PacketEvent.JoinRequestRemove(requester))
        return true
    }

    private fun parseRemainHp(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x00.toByte()) return false
        if (packet[offset + 1] != 0x8d.toByte()) return false
        offset += 2

        val mobIdInfo = readVarInt(packet, offset)
        if (mobIdInfo.length <= 0) return false
        offset += mobIdInfo.length

        offset += readVarInt(packet, offset).length
        offset += readVarInt(packet, offset).length
        offset += readVarInt(packet, offset).length
        if (offset + 4 > packet.size) return false

        val mobHp = parseUInt32le(packet, offset)
        DataManager.mobHp(mobIdInfo.value, mobHp)
        val maxHp = DataManager.mobMaxHp(mobIdInfo.value) ?: 0
        if (mobHp > maxHp) {
            DataManager.saveMobMaxHp(mobIdInfo.value, mobHp)
        }
        return true

    }

    private fun parseBuffPacket(
        packet: ByteArray,
        lengthInfo: VarIntOutput,
        extraFlag: Boolean,
        arrivedAt: Long
    ): Boolean {
        try {
            var offset = lengthInfo.length
            if (extraFlag) {
                offset++
            }
            if (packet[offset] != 0x2a.toByte() && packet[offset] != 0x2b.toByte()) return false
            if (packet[offset + 1] != 0x38.toByte()) return false
            offset += 2

            val targetInfo = readVarInt(packet, offset)
            offset += targetInfo.length + 2

            offset += readVarInt(packet, offset).length

            val skillCode = parseUInt32le(packet, offset)
            offset += 4

            if (skillCode < 110000000 || skillCode > 190000000) {
                if (skillCode >= 30000000 || skillCode < 20000000) {
                    return true
                }
            }
            // 임시

            val duration = readUInt32leAsLong(packet, offset)
            offset += 8

            val serverTime = readUInt64le(packet, offset)
            offset += 8

            val actorInfo = readVarInt(packet, offset)

            val buff = UseBuff(skillCode, arrivedAt, arrivedAt + duration, duration, actorInfo.value)
            if (duration == 4294967295L) {
                return true
            }
            PacketAddonManager.loggingServerTime(arrivedAt, duration, serverTime)
            DataManager.saveUseBuff(targetInfo.value, buff)
//            println("대상자: ${targetInfo.value}, 사용자: ${actorInfo.value}, 버프코드: ${skillCode},버프이름: ${DataManager.buff(skillCode)?.name} 길이: $duration")
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun parseInstanceStartPacket(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x18.toByte()) return false
        if (packet[offset + 1] != 0x97.toByte()) return false

        PacketEventBus.events.tryEmit(PacketEvent.ExitPartyUI)
        return true
    }

    private fun parseExitParty(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x1D.toByte()) return false
        if (packet[offset + 1] != 0x97.toByte()) return false

        PacketEventBus.events.tryEmit(PacketEvent.ExitPartyUI)
        return true
    }

    private fun parseRefuseJoinRequest(packet: ByteArray, lengthInfo: VarIntOutput, extraFlag: Boolean): Boolean {
        var offset = lengthInfo.length
        if (extraFlag) {
            offset++
        }
        if (packet.size < offset + 2) return false

        if (packet[offset] != 0x09.toByte()) return false
        if (packet[offset + 1] != 0x97.toByte()) return false
        PacketEventBus.events.tryEmit(PacketEvent.RefuseJoinRequest)
        return true
    }

}
