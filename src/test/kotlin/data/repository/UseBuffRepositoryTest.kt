package com.tbread.data.repository

import com.tbread.entity.UseBuff
import kotlin.test.Test
import kotlin.test.assertEquals

class UseBuffRepositoryTest {
    @Test
    fun latestKeepsNewestEnd() {
        val repo = UseBuffRepository()
        repo.save(1, UseBuff(182500001, 0, 5_000, 5_000, 1))
        repo.save(1, UseBuff(182500001, 4_000, 12_000, 8_000, 1))
        val latest = repo.latestBySkillCode(1)
        assertEquals(12_000, latest[182500001]?.buffEnd)
    }
}
