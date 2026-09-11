package com.tbread.packet

/**
 * 캐릭터가 월드에 들어올 때(0x0B 0x61)와 오드가 변할 때(0x0C 0x61)의 레이아웃.
 * 공개된 캡처 검증 파서(gyu98-mun/Aion2_Aether_Monitoring)와 같은 마커를 씁니다.
 */
object OdeEnergyParser {
    const val MAX_PLAUSIBLE = 100_000

    data class Reading(
        val entityId: Int,
        val total: Int,
        val base: Int? = null,
        val dynamic: Int? = null,
        val isSnapshot: Boolean = false,
    )

    fun parseUpdate(payload: ByteArray): Reading? {
        if (payload.size < 3) return null
        val header0 = payload[0].toInt() and 0xFF
        val header1 = payload[1].toInt() and 0xFF
        val header2 = payload[2].toInt() and 0xFF
        if (header2 != 0x01 || header0 !in setOf(0x00, 0x01)) return null
        if (header1 != 0x08 && header1 != 0x0C) return null

        var pos = 3
        val id = readVarInt(payload, pos) ?: return null
        pos += id.second
        val first = readVarInt(payload, pos) ?: return null
        pos += first.second

        val base: Int?
        val dynamic: Int
        val total: Int
        if (header1 == 0x0C) {
            val second = readVarInt(payload, pos) ?: return null
            pos += second.second
            base = first.first
            dynamic = second.first
            total = base + dynamic
        } else {
            base = null
            dynamic = first.first
            total = dynamic
        }

        val remaining = payload.size - pos
        if (header0 == 0x01) {
            if (remaining != 5) return null
        } else if (remaining != 1) {
            return null
        }
        if (!plausible(total)) return null
        if (base != null && (!plausible(base) || !plausible(dynamic))) return null
        return Reading(entityId = id.first, total = total, base = base, dynamic = dynamic, isSnapshot = false)
    }

    fun parseSnapshot(payload: ByteArray, knownId: Int? = null): Reading? {
        val candidates = ArrayList<Reading>()

        findMarker(payload, byteArrayOf(0x0C, 0x01)) { pos ->
            val id = readVarInt(payload, pos) ?: return@findMarker
            var cursor = pos + id.second
            val v1 = readVarInt(payload, cursor) ?: return@findMarker
            cursor += v1.second
            val v2 = readVarInt(payload, cursor) ?: return@findMarker
            val total = v1.first + v2.first
            if (plausible(total) && plausible(v1.first) && plausible(v2.first)) {
                candidates += Reading(id.first, total, v1.first, v2.first, isSnapshot = true)
            }
        }

        findMarker(payload, byteArrayOf(0x00, 0x08, 0x01)) { pos ->
            val id = readVarInt(payload, pos) ?: return@findMarker
            val total = readVarInt(payload, pos + id.second) ?: return@findMarker
            if (plausible(total.first)) {
                candidates += Reading(id.first, total.first, base = 0, dynamic = total.first, isSnapshot = true)
            }
        }

        if (knownId != null) {
            val idBytes = encodeVarInt(knownId)
            val marker = byteArrayOf(0x01) + idBytes
            findMarker(payload, marker) { pos ->
                val discIndex = pos - marker.size - 1
                if (discIndex < 0) return@findMarker
                when (payload[discIndex].toInt() and 0xFF) {
                    0x04 -> {
                        val base = readVarInt(payload, pos) ?: return@findMarker
                        if (plausible(base.first)) {
                            candidates += Reading(knownId, base.first, base.first, 0, isSnapshot = true)
                        }
                    }
                    0x00 -> candidates += Reading(knownId, 0, 0, 0, isSnapshot = true)
                }
            }
        }

        if (candidates.isEmpty()) {
            if (knownId != null && payload.size >= 32) {
                return Reading(knownId, 0, 0, 0, isSnapshot = true)
            }
            return null
        }
        val chosen = knownId?.let { id -> candidates.firstOrNull { it.entityId == id } } ?: candidates.first()
        return chosen
    }

    fun encodeVarInt(value: Int): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (v > 0x7F) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    private fun plausible(value: Int): Boolean = value in 0..MAX_PLAUSIBLE

    private fun readVarInt(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset < 0 || offset >= bytes.size) return null
        var value = 0
        var shift = 0
        var count = 0
        while (true) {
            if (offset + count >= bytes.size || shift >= 32) return null
            val byteVal = bytes[offset + count].toInt() and 0xFF
            count++
            value = value or ((byteVal and 0x7F) shl shift)
            if (byteVal and 0x80 == 0) return value to count
            shift += 7
        }
    }

    private fun findMarker(payload: ByteArray, marker: ByteArray, onHit: (Int) -> Unit) {
        var start = 0
        while (true) {
            val idx = indexOf(payload, marker, start)
            if (idx < 0) return
            onHit(idx + marker.size)
            start = idx + 1
        }
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        if (pattern.isEmpty() || from > data.size - pattern.size) return -1
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
