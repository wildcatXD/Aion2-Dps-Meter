package com.tbread.data.repository

data class MobInstance(val code: Int, var maxHp: Int = 0)

class MobIdRepository {
    private val storage = HashMap<Int, MobInstance>()

    fun save(key: Int, code: Int): MobInstance? {
        val existing = storage[key]
        if (existing != null) {
            storage[key] = existing.copy(code = code)
            return existing
        }
        return storage.put(key, MobInstance(code))
    }

    fun saveMaxHp(key: Int, maxHp: Int): Boolean {
        val instance = storage.getOrPut(key) { MobInstance(code = 0) }
        if (maxHp > instance.maxHp) instance.maxHp = maxHp
        return true
    }

    fun get(id: Int): MobInstance? {
        return storage[id]
    }

    fun exist(id: Int): Boolean {
        return storage.containsKey(id)
    }

    fun unmappedIds(): List<Int> {
        return storage.filter { it.value.code <= 0 }.keys.toList()
    }

    fun delete(id: Int) {
        storage.remove(id)
    }

    fun flush() {
        storage.clear()
    }
}
