package com.tbread.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceTest {
    @Test
    fun secondLockOnSameFileFails() {
        val dir = Files.createTempDirectory("bit-dps-lock").toFile()
        val lockFile = dir.resolve("instance.lock")
        InstanceLock(lockFile).use { first ->
            assertTrue(first.tryAcquire())
            InstanceLock(lockFile).use { second ->
                assertFalse(second.tryAcquire())
            }
        }
        dir.deleteRecursively()
    }
}
