package com.tbread.config

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * 미터기 프로세스를 하나만 남깁니다. 창이 두 개면 설치 폴더의 exe/jar 가 잠겨
 * 무인 MSI 업그레이드가 1603 으로 실패하고, 예전 버전이 다시 켜집니다.
 */
class InstanceLock(private val lockFile: File) : AutoCloseable {
    private var stream: FileOutputStream? = null
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    fun tryAcquire(): Boolean {
        return try {
            lockFile.parentFile?.mkdirs()
            val out = FileOutputStream(lockFile)
            val ch = out.channel
            val acquired = ch.tryLock()
            if (acquired == null) {
                ch.close()
                out.close()
                false
            } else {
                stream = out
                channel = ch
                lock = acquired
                true
            }
        } catch (_: OverlappingFileLockException) {
            close()
            false
        } catch (e: Exception) {
            close()
            // 잠금 파일을 못 쓰면 실행을 막지 않습니다. 중복 방지보다 미터기가 안 뜨는 쪽이 더 나쁩니다.
            LoggerFactory.getLogger(InstanceLock::class.java)
                .warn("인스턴스 잠금을 얻지 못해 중복 실행 검사를 건너뜁니다: {}", e.message)
            true
        }
    }

    override fun close() {
        try {
            lock?.release()
        } catch (_: Exception) {
        }
        try {
            channel?.close()
        } catch (_: Exception) {
        }
        try {
            stream?.close()
        } catch (_: Exception) {
        }
        lock = null
        channel = null
        stream = null
    }
}

object SingleInstance {
    private var held: InstanceLock? = null

    fun acquire(): Boolean {
        val lock = InstanceLock(defaultLockFile())
        if (!lock.tryAcquire()) {
            lock.close()
            return false
        }
        held = lock
        return true
    }

    internal fun defaultLockFile(): File {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        return File(File(appData, "Aion2DpsMeter"), "instance.lock")
    }
}
