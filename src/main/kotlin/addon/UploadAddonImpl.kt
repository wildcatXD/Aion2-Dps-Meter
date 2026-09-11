package com.tbread.addon

import com.tbread.config.PropertyHandler
import com.tbread.entity.DpsLog
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 길드 웹 `POST /api/meter/encounters` 로 스냅샷만 올립니다.
 * 패킷 덤프와 낫터기 ingest는 쓰지 않습니다.
 */
class UploadAddonImpl : BattleLogUploader {
    private val logger = LoggerFactory.getLogger(UploadAddonImpl::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    override fun upload(log: DpsLog): String? {
        val payload = log.encounterJson() ?: run {
            logger.warn("전투 스냅샷이 없어 업로드하지 않습니다.")
            return null
        }
        val base = PropertyHandler.getProperty("guildWebUrl")
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()
        val token = PropertyHandler.getProperty("guildDeviceToken")?.trim().orEmpty()
        if (base.isEmpty() || token.length != 64) {
            logger.warn("길드 웹 주소 또는 기기 토큰이 없습니다.")
            return null
        }
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$base/api/meter/encounters"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                logger.warn("업로드 실패 HTTP ${response.statusCode()} ${response.body().take(180)}")
                return null
            }
            Regex("\"id\"\\s*:\\s*(\\d+)").find(response.body())?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.warn("업로드 예외: ${e.message}")
            null
        }
    }
}
