package com.tbread.data.repository

import com.tbread.entity.UseBuff
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class UseBuffRepository {
    private val storage = ConcurrentHashMap<Int, CopyOnWriteArrayList<UseBuff>>()

    fun save(id: Int, useBuff: UseBuff) {
        storage.computeIfAbsent(id) { CopyOnWriteArrayList() }.add(useBuff)
    }

    fun findOverlapping(id: Int, timestamp1: Long, timestamp2: Long): List<UseBuff> {
        return storage[id]?.filter { buff ->
            buff.buffStart <= timestamp2 && buff.buffEnd >= timestamp1
        } ?: emptyList()
    }

    fun latestBySkillCode(id: Int): Map<Int, UseBuff> {
        val latest = HashMap<Int, UseBuff>()
        val list = storage[id] ?: return latest
        for (buff in list) {
            val prev = latest[buff.skillCode]
            if (prev == null || buff.buffEnd >= prev.buffEnd) {
                latest[buff.skillCode] = buff
            }
        }
        return latest
    }
}