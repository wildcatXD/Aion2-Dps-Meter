package com.tbread.webview

import com.sun.jna.platform.win32.*
import com.tbread.DpsCalculator
import com.tbread.addon.UploadManager
import com.tbread.config.HotkeyHandler
import com.tbread.config.PropertyHandler
import com.tbread.config.VersionConfig
import com.tbread.data.DataManager
import com.tbread.entity.DpsReport
import com.tbread.entity.JoinRequestUser
import com.tbread.packet.PacketEvent
import com.tbread.packet.PacketEventBus
import javafx.application.Application
import javafx.application.HostServices
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.stage.StageStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import netscape.javascript.JSObject
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import kotlin.system.exitProcess

class BrowserApp(private val config: VersionConfig, private val dpsCalculator: DpsCalculator) : Application() {

    private val logger = LoggerFactory.getLogger(BrowserApp::class.java)

    // 기본 Json은 encodeDefaults=false라서 0.0/0 같은 기본값 필드가 통째로 빠집니다.
    // 오버레이(TS)는 그 필드를 Number(undefined)로 읽어 NaN이 되므로, 여기서는 항상 필드를
    // 내려보내도록 별도 인스턴스를 씁니다 (예: entireContribution이 0일 때 "NaN%"로 보이던 문제).
    private val overlayJson = Json { encodeDefaults = true }

    private lateinit var engine: WebEngine
    private var trayIcon: TrayIcon? = null

    inner class JSBridge(private val stage: Stage, private val hostServices: HostServices) {

        fun saveProps(key: String, value: String) {
            PropertyHandler.setProperty(key, value)
        }

        fun loadProps(key: String): String? {
            return PropertyHandler.getProperty(key)
        }

        fun moveWindow(x: Double, y: Double) {
            stage.x = x
            stage.y = y
        }

        fun resetDps() {
            dpsCalculator.resetDataStorage()
            engine.executeScript("resetDpsUI()")
        }

        fun hardResetDps() {
            // dpsCalculator.hardReset()
            // engine.executeScript("strongReset()")
        }

        fun updateHotkey(modifiers: Int, vkCode: Int) {
            HotkeyHandler.updateHotkey(modifiers, vkCode)
        }

        fun getHotkey(): String {
            return HotkeyHandler.getCurrentHotkey().toString()
        }

        fun openBrowser(url: String) {
            try {
                hostServices.showDocument(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun exitApp() {
            Platform.exit()
            exitProcess(0)
        }

        fun toggleVisibility() {
            if (isVisible) hideToTray(stage) else showFromTray(stage)
        }

        fun showWindow() {
            if (!isVisible) showFromTray(stage)
        }

        fun getHideHotkey(): String {
            return HotkeyHandler.getVisibilityHotkey().toString()
        }

        fun updateHideHotkey(modifiers: Int, vkCode: Int) {
            HotkeyHandler.updateVisibilityHotkey(modifiers, vkCode)
        }

        fun isClickThrough(): Boolean = isClickThrough

        fun toggleAutoHide() {
            isAutoHide = !isAutoHide
            PropertyHandler.setProperty("isAutoHide", isAutoHide.toString())
        }

        fun isAutoHide(): Boolean = isAutoHide

        fun getClickThroughHotkey(): String {
            return HotkeyHandler.getClickThroughHotkey().toString()
        }

        fun updateClickThroughHotkey(modifiers: Int, vkCode: Int) {
            HotkeyHandler.updateClickThroughHotkey(modifiers, vkCode)
        }

        fun getDpsData(): String {
            return cachedDpsJson
        }

        fun isDebuggingMode(): Boolean {
            return debugMode
        }

        fun getBattleDetail(uid: Int): String {
            return overlayJson.encodeToString(dpsCalculator.battleDetails(dpsCalculator.getLiveReport(), uid))
        }

        fun getBattleDetailFromList(idx: Int, uid: Int): String {
            return overlayJson.encodeToString(dpsCalculator.battleDetails(DataManager.battleLog(idx)?.report, uid))
        }

        fun getBattleList(): String {
            return overlayJson.encodeToString(DataManager.recentBattleList())
        }

        fun getLiveBuffOperatingRate(uid: Int): String {
            val report = dpsCalculator.getLiveReport()
            val end = if (report.battleEnd == 0L) System.currentTimeMillis() else report.battleEnd
            return overlayJson.encodeToString(dpsCalculator.getBuffOperatingRate(uid,report.battleStart,end))
        }

        fun getBuffOperatingRate(idx: Int, uid: Int): String {
            val report = DataManager.battleLog(idx)?.report ?: return ""
            return overlayJson.encodeToString(dpsCalculator.getBuffOperatingRate(uid,report.battleStart,report.battleEnd))
        }

        fun getLiveBossBuffOperatingRate(): String {
            val report = dpsCalculator.getLiveReport()
            val end = if (report.battleEnd == 0L) System.currentTimeMillis() else report.battleEnd
            val targetId = report.target?.id ?: return ""
            return overlayJson.encodeToString(dpsCalculator.getBuffOperatingRate(targetId,report.battleStart,end))
        }

        fun getBossBuffOperatingRate(idx: Int): String {
            val report = DataManager.battleLog(idx)?.report ?: return ""
            val targetId = report.target?.id ?: return ""
            return overlayJson.encodeToString(dpsCalculator.getBuffOperatingRate(targetId,report.battleStart,report.battleEnd))
        }

        fun upload(idx: Int): Boolean {
            val log = DataManager.battleLog(idx) ?: return false
            return UploadManager.upload(log)
        }

        fun getVersion(): String {
            return version
        }

        fun startUpdate(msiUrl: String) {
            // 재실행할 때 쓸 실행 파일 경로를 미리 잡아둡니다. 설치가 끝난 뒤에는 msiexec가
            // 같은 경로에 파일을 덮어쓰므로, 지금 떠 있는 이 프로세스의 실행 파일 경로를 그대로
            // 다시 실행하면 됩니다.
            val currentExePath = ProcessHandle.current().info().command().orElse(null)

            Thread {
                try {
                    val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "bit-dps-meter").also { it.mkdirs() }
                    val msiFile = java.io.File(tempDir, "bit-dps-meter-update.msi")
                    val logFile = java.io.File(tempDir, "install.log")

                    val connection = java.net.URI(msiUrl).toURL().openConnection() as java.net.HttpURLConnection
                    connection.connect()
                    val totalBytes = connection.contentLengthLong

                    var downloadedBytes = 0L
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(msiFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val percent = (downloadedBytes * 100 / totalBytes).toInt()
                                    Platform.runLater {
                                        engine.executeScript("onDownloadProgress($percent)")
                                    }
                                }
                            }
                        }
                    }

                    Platform.runLater { engine.executeScript("onDownloadComplete()") }
                    Platform.runLater { engine.executeScript("onInstallStarting()") }

                    // 조용히(무인) 설치합니다. 미터기가 이미 관리자 권한으로 실행 중이라 msiexec도
                    // 같은 권한을 물려받아 UAC 창이 따로 뜨지 않습니다. 설치가 끝날 때까지 기다린
                    // 뒤, 성공하면 새로 설치된 실행 파일을 자동으로 다시 실행하고 지금 프로세스는
                    // 종료합니다 — 설치 마법사의 다음/설치 버튼을 누르는 등 별도 조작이 필요 없습니다.
                    val installProcess = ProcessBuilder(
                        "msiexec", "/i", msiFile.absolutePath,
                        "/quiet", "/norestart",
                        "/l*v", logFile.absolutePath,
                    ).start()
                    val exitCode = installProcess.waitFor()

                    if (exitCode == 0) {
                        if (currentExePath != null && java.io.File(currentExePath).exists()) {
                            try {
                                Runtime.getRuntime().exec(arrayOf(currentExePath))
                            } catch (e: Exception) {
                                logger.error("업데이트 후 재실행 실패, 수동으로 다시 실행해야 합니다", e)
                            }
                        } else {
                            logger.warn("실행 파일 경로를 찾지 못해 자동 재실행을 건너뜁니다: $currentExePath")
                        }
                        Thread.sleep(500)
                        Platform.exit()
                        exitProcess(0)
                    }

                    // 무인 설치가 실패하면(exitCode != 0) 설치 마법사를 직접 띄워서 사용자가
                    // 눈으로 보고 마무리할 수 있게 합니다. 로그는 install.log에 남아있습니다.
                    logger.error("무인 설치 실패 (exitCode=$exitCode), 설치 마법사를 직접 엽니다: ${logFile.absolutePath}")
                    val installerLaunched = try {
                        Runtime.getRuntime().exec(arrayOf("msiexec", "/i", msiFile.absolutePath))
                        true
                    } catch (e: Exception) {
                        logger.error("설치 프로그램 실행 실패, 폴더만 엽니다", e)
                        try {
                            Runtime.getRuntime().exec(arrayOf("explorer.exe", tempDir.absolutePath))
                        } catch (e2: Exception) {
                            logger.error("탐색기 열기 실패", e2)
                        }
                        false
                    }

                    if (installerLaunched) {
                        Thread.sleep(1500)
                        Platform.exit()
                        exitProcess(0)
                    } else {
                        Platform.runLater { engine.executeScript("onDownloadError()") }
                    }
                } catch (e: Exception) {
                    logger.error("업데이트 실패", e)
                    Platform.runLater { engine.executeScript("onDownloadError()") }
                }
            }.start()
        }

        fun pushJoinRequest(data: JoinRequestUser) {
            engine.executeScript("onJoinRequest(${overlayJson.encodeToString(data)})")
        }

        fun pushJoinRequestRemove(id: Int) {
            engine.executeScript("onJoinRequestRemove($id)")
        }

        fun pushExitPartyUI(){
            engine.executeScript("onExitPartyUI()")
        }

        fun pushRefuseJoinRequest(){
            engine.executeScript("onRefuseJoinRequest()")
        }


    }

    @Volatile
    private var dpsData: DpsReport = dpsCalculator.getDps()

    @Volatile
    private var cachedDpsJson: String = overlayJson.encodeToString(dpsData)

    @Volatile
    private var isVisible = true

    @Volatile
    private var isAutoHide = PropertyHandler.getProperty("isAutoHide")?.toBooleanStrictOrNull() ?: true

    @Volatile
    private var aionEverFocused = false

    @Volatile
    private var isClickThrough = false

    private var overlayHwnd: WinDef.HWND? = null

    private val debugMode = false

    private val version = config.version


    override fun start(stage: Stage) {
        Platform.setImplicitExit(false)
        stage.setOnCloseRequest {
            HotkeyHandler.stop()
            exitProcess(0)
        }
        val webView = WebView()
        engine = webView.engine
        engine.load(javaClass.getResource("/dist/index.html")?.toExternalForm())

        val bridge = JSBridge(stage, hostServices)
        engine.loadWorker.stateProperty().addListener { _, _, newState ->
            if (newState == Worker.State.SUCCEEDED) {
                val window = engine.executeScript("window") as JSObject
                window.setMember("javaBridge", bridge)
            }
        }


        val scene = Scene(webView, 1920.0, 1080.0)
        scene.fill = Color.TRANSPARENT


        try {
            val pageField = engine.javaClass.getDeclaredField("page")
            pageField.isAccessible = true
            val page = pageField.get(engine)

            val setBgMethod = page.javaClass.getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
            setBgMethod.isAccessible = true
            setBgMethod.invoke(page, 0)
        } catch (e: Exception) {
            logger.error("리플렉션 실패", e)
        }

        stage.initStyle(StageStyle.TRANSPARENT)
        stage.scene = scene
        stage.isAlwaysOnTop = true
        stage.title = "Bit Dps Overlay"

        stage.show()
        applyOverlayWindowStyle(stage.title)

        setupTray(stage)

        HotkeyHandler.registerCallback {
            Platform.runLater {
                // bridge.hardResetDps()
            }
        }
        HotkeyHandler.registerVisibilityCallback {
            if (isVisible) hideToTray(stage) else showFromTray(stage)
        }
        HotkeyHandler.registerClickThroughCallback {
            setClickThrough(!isClickThrough)
        }
        HotkeyHandler.start()
        
        
        CoroutineScope(Dispatchers.IO).launch {
            PacketEventBus.events.collect { event ->
                Platform.runLater {
                    when (event) {
                        is PacketEvent.JoinRequest -> bridge.pushJoinRequest(event.user)
                        is PacketEvent.JoinRequestRemove -> bridge.pushJoinRequestRemove(event.id)
                        is PacketEvent.ExitPartyUI -> bridge.pushExitPartyUI()
                        is PacketEvent.RefuseJoinRequest -> bridge.pushRefuseJoinRequest()
                    }
                }
            }
        }
        
        
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                // 여기서 처리되지 않은 예외가 발생하면 이 while 루프(코루틴)가 그대로 죽어서,
                // 그 이후로는 딜/본인 탐지를 포함한 모든 갱신이 영구히 멈춰버립니다.
                // 한 번의 실패가 전체를 멈추지 않도록 방어적으로 감쌉니다.
                try {
                    val data = dpsCalculator.getDps()
                    cachedDpsJson = overlayJson.encodeToString(data)
                    dpsData = data
                } catch (e: Exception) {
                    logger.error("DPS 데이터 갱신 실패, 다음 주기에 재시도합니다", e)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                kotlinx.coroutines.delay(300)
                if (!isVisible) continue
                if (!isAutoHide) {
                    Platform.runLater { stage.opacity = 1.0 }
                    continue
                }

                val aionFocused = isAion2Focused()
                if (!aionEverFocused) {
                    if (aionFocused) aionEverFocused = true
                    else continue
                }

                val shouldShow = aionFocused || isSelfFocused()
                Platform.runLater {
                    stage.opacity = if (shouldShow) 1.0 else 0.0
                }
            }
        }
    }

    private fun isSelfFocused(): Boolean {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return false
        val pidRef = com.sun.jna.ptr.IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        return pidRef.value.toLong() == ProcessHandle.current().pid()
    }

    private fun isAion2Focused(): Boolean {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return false
        val pidRef = com.sun.jna.ptr.IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        val foregroundPid = pidRef.value.toLong()

        val hProcess = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, foregroundPid.toInt())
            ?: return false
        return try {
            val buf = com.sun.jna.Memory(2048)
            Psapi.INSTANCE.GetModuleFileNameEx(hProcess, null, buf, 1024)
            val exePath = buf.getWideString(0)
            exePath.endsWith("Aion2.exe", ignoreCase = true)
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess)
        }
    }


    private fun applyOverlayWindowStyle(title: String) {
        val GWL_EXSTYLE = -20
        val WS_EX_TOOLWINDOW = 0x00000080
        val WS_EX_APPWINDOW = 0x00040000
        val SWP_NOMOVE = 0x0002
        val SWP_NOSIZE = 0x0001
        val SWP_NOZORDER = 0x0004
        val SWP_FRAMECHANGED = 0x0020
        val user32 = User32.INSTANCE
        val hwnd = user32.FindWindow(null, title) ?: return
        overlayHwnd = hwnd
        val exStyle = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        user32.SetWindowLong(hwnd, GWL_EXSTYLE,
            (exStyle or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv()
        )
        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED)
    }

    private fun setClickThrough(enable: Boolean) {
        val hwnd = overlayHwnd ?: return
        val GWL_EXSTYLE = -20
        val WS_EX_LAYERED = 0x00080000
        val WS_EX_TRANSPARENT = 0x00000020
        val user32 = User32.INSTANCE
        val exStyle = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        val newStyle = if (enable) {
            exStyle or WS_EX_LAYERED or WS_EX_TRANSPARENT
        } else {
            (exStyle or WS_EX_LAYERED) and WS_EX_TRANSPARENT.inv()
        }
        user32.SetWindowLong(hwnd, GWL_EXSTYLE, newStyle)
        isClickThrough = enable
        Platform.runLater {
            engine.executeScript("onClickThroughChanged($enable)")
        }
    }

    private fun setupTray(stage: Stage) {
        if (!SystemTray.isSupported()) return
        EventQueue.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                val iconUrl = javaClass.getResource("/src/assets/logo.png")
                val image = if (iconUrl != null) {
                    ImageIO.read(iconUrl)
                } else {
                    java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                }

                val popup = PopupMenu()
                val showItem = MenuItem("보이기/숨기기")
                showItem.addActionListener { if (isVisible) hideToTray(stage) else showFromTray(stage) }
                val exitItem = MenuItem("종료")
                exitItem.addActionListener {
                    tray.remove(trayIcon)
                    Platform.exit()
                    exitProcess(0)
                }
                popup.add(showItem)
                popup.addSeparator()
                popup.add(exitItem)

                trayIcon = TrayIcon(image, "빛 DPS Overlay", popup).apply {
                    isImageAutoSize = true
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) {
                                if (isVisible) hideToTray(stage) else showFromTray(stage)
                            }
                        }
                    })
                }
                tray.add(trayIcon)
            } catch (e: AWTException) {
                logger.error("트레이 설정 실패", e)
            }
        }
    }

    private fun hideToTray(stage: Stage) {
        isVisible = false
        Platform.runLater { stage.opacity = 0.0 }
    }

    private fun showFromTray(stage: Stage) {
        isVisible = true
        aionEverFocused = false
        Platform.runLater {
            stage.opacity = 1.0
        }
    }

}
