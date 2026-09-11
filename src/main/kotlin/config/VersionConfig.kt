package com.tbread.config

import org.slf4j.LoggerFactory
import java.util.Properties

data class VersionConfig(val version: String) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)
        private val SEMVER = Regex("""\d+\.\d+\.\d+.*""")

        /**
         * 패키징된 `version.properties` 만 읽습니다. `%APPDATA%\Aion2DpsMeter\settings.properties`
         * 에 예전에 저장된 `version=` 은 업데이트 후에도 남을 수 있어서 UI 가 계속
         * 예전 버전을 보여 주고 업데이트 알림을 반복했습니다.
         */
        fun packagedVersion(): String {
            val fromJar = VersionConfig::class.java.getResourceAsStream("/version.properties")?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }?.trim()
            if (!fromJar.isNullOrBlank() && !fromJar.contains("\${") && SEMVER.matches(fromJar)) {
                return fromJar
            }
            return "1.0.0"
        }

        fun loadFromProperties(): VersionConfig {
            val version = packagedVersion()
            logger.info("프로퍼티스 초기화 완료")
            return VersionConfig(version)
        }
    }
}
