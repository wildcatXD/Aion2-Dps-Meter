package com.tbread

import com.tbread.config.PcapCapturerConfig
import com.tbread.config.VersionConfig
import com.tbread.data.DataManager
import com.tbread.packet.*
import com.tbread.webview.BrowserApp
import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

fun main() = runBlocking {
    // 이 앱은 콘솔 없는(windowed) 실행 파일로 패키징되어 있어서 System.out/System.err로
    // 나가는 로그(slf4j-simple 기본 동작)는 아무 데도 보이지 않고 사라집니다. 그래서 실행 중
    // 조용히 실패하는 버그(트레이 아이콘 등록 실패, 업데이트/설치 실패 등)를 사용자가 직접
    // 재현해서 알려주기 전까지는 원인을 알아낼 방법이 없었습니다. 여기서 로그를 파일로도
    // 남기도록 가장 먼저(어떤 클래스의 logger도 초기화되기 전에) 설정합니다.
    // PropertyHandler가 쓰는 설정 폴더(%APPDATA%/Aion2DpsMeter)와 같은 위치를 씁니다.
    run {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val logDir = java.io.File(appData, "Aion2DpsMeter")
        logDir.mkdirs()
        System.setProperty("org.slf4j.simpleLogger.logFile", java.io.File(logDir, "app.log").absolutePath)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    }

    val mainLogger = org.slf4j.LoggerFactory.getLogger("Main")
    mainLogger.info("빛 레기온 미터기 시작")
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        mainLogger.error("처리되지 않은 예외로 쓰레드가 종료되었습니다: ${t.name}", e)
    }

    DataManager.load()

    val channel = Channel<CapturedPacket>(Channel.UNLIMITED)
    val pcapConfig = PcapCapturerConfig.loadFromProperties()
    val versionConfig = VersionConfig.loadFromProperties()
    mainLogger.info("버전 {}", versionConfig.version)

    val processor = StreamProcessor()
    val alignmenter = PacketAlignmenter()
    val assembler = StreamAssembler(processor)
    val capturer = PcapCapturer(pcapConfig, channel)
    val calculator = DpsCalculator {
        assembler.flush()
        alignmenter.reset()
    }

    supervisorScope {
        launch(Dispatchers.Default) {
            var currentIp = ""
            for ((ip, seq, data, arrivedAt) in channel) {
                if (ip != currentIp) {
                    currentIp = ip
                    alignmenter.reset()
                }
                val chunks = alignmenter.feed(seq, data, arrivedAt)
                for ((chunk, ts) in chunks) {
                    assembler.processChunk(chunk, ts)
                }
            }
        }

        launch(Dispatchers.IO) {
            try {
                capturer.start()
            } catch (e: Exception) {
                mainLogger.error("패킷 캡처 스레드가 종료되었습니다. 오버레이는 유지합니다", e)
            }
        }

        launch {
            while (true) {
                delay(1000)
                DataManager.checkDummyTimeout()
            }
        }

        Platform.startup {
            val browserApp = BrowserApp(versionConfig, calculator)
            browserApp.start(Stage())
        }
    }
}


