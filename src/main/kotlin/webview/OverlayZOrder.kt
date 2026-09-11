package com.tbread.webview

/**
 * 오버레이를 전역 TOPMOST로 둘지, 아이온 창 바로 위에만 둘지 결정합니다.
 * 네이티브 SetWindowPos 는 [OverlayTopMost]가 담당합니다.
 */
object OverlayZOrder {
    enum class Mode { GLOBAL, GAME_WINDOW }

    data class Plan(
        val topMost: Boolean,
        val placeAboveGame: Boolean,
    )

    fun plan(
        mode: Mode,
        overlayVisible: Boolean,
        gameFound: Boolean,
        gameIsTopMost: Boolean,
        gameFocused: Boolean,
        selfFocused: Boolean,
        autoHide: Boolean,
        autoHideShouldShow: Boolean,
    ): Plan {
        if (!overlayVisible) return Plan(topMost = false, placeAboveGame = false)
        if (autoHide && !autoHideShouldShow) return Plan(topMost = false, placeAboveGame = false)
        if (mode == Mode.GLOBAL) return Plan(topMost = true, placeAboveGame = false)
        if (!gameFound) return Plan(topMost = true, placeAboveGame = false)

        val inFront = gameFocused || selfFocused
        if (!inFront) {
            // 다른 앱이 앞이면 전역 TOPMOST만 풀어 그 앱을 덮지 않습니다.
            // 게임 뒤로 다시 끼워 넣으면 게임·오버레이가 같이 올라올 수 있어 건드리지 않습니다.
            return Plan(topMost = false, placeAboveGame = false)
        }
        return Plan(topMost = gameIsTopMost, placeAboveGame = true)
    }

    fun matchesExe(path: String, exeFileName: String): Boolean {
        val name = path.substringAfterLast('\\').substringAfterLast('/')
        return name.equals(exeFileName, ignoreCase = true)
    }

    fun extendedStyle(current: Int, topMost: Boolean, clickThrough: Boolean): Int {
        var ex = (current or WS_EX_TOOLWINDOW or WS_EX_LAYERED) and WS_EX_APPWINDOW.inv()
        ex = if (topMost) ex or WS_EX_TOPMOST else ex and WS_EX_TOPMOST.inv()
        ex = if (clickThrough) ex or WS_EX_TRANSPARENT else ex and WS_EX_TRANSPARENT.inv()
        return ex
    }

    internal const val WS_EX_TOOLWINDOW = 0x00000080
    internal const val WS_EX_APPWINDOW = 0x00040000
    internal const val WS_EX_TOPMOST = 0x00000008
    internal const val WS_EX_LAYERED = 0x00080000
    internal const val WS_EX_TRANSPARENT = 0x00000020
}
