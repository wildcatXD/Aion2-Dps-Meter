package com.tbread.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class MobIdRepositoryTest {
    @Test
    fun saveMaxHpWithoutSpawnKeepsPlaceholderCode() {
        val repo = MobIdRepository()
        assertEquals(true, repo.saveMaxHp(7, 129475))
        assertEquals(0, repo.get(7)?.code)
        assertEquals(129475, repo.get(7)?.maxHp)
    }

    @Test
    fun laterSpawnDoesNotWipeMaxHp() {
        val repo = MobIdRepository()
        repo.saveMaxHp(7, 129475)
        repo.save(7, 2301089)
        assertEquals(2301089, repo.get(7)?.code)
        assertEquals(129475, repo.get(7)?.maxHp)
    }

    @Test
    fun unmappedIdsAreThoseWithoutCatalogCode() {
        val repo = MobIdRepository()
        repo.saveMaxHp(7, 129475)
        repo.save(8, 2300229)
        assertEquals(listOf(7), repo.unmappedIds())
    }
}
