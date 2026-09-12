package com.tbread.webview

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Psapi
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference

/**
 * JavaFX `isAlwaysOnTop`만으로는 게임(보더리스)이 덮는 경우가 있어,
 * WS_EX_TOPMOST / HWND_NOTOPMOST 와 게임 창 Z순서를 네이티브로 맞춥니다.
 */
object OverlayTopMost {
    private const val GWL_EXSTYLE = -20
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_NOOWNERZORDER = 0x0200
    private const val MIN_GAME_AREA = 200 * 200

    private val HWND_TOPMOST = WinDef.HWND(Pointer.createConstant(-1))
    private val HWND_NOTOPMOST = WinDef.HWND(Pointer.createConstant(-2))

    fun find(title: String): WinDef.HWND? = User32.INSTANCE.FindWindow(null, title)

    fun applyStyle(hwnd: WinDef.HWND, topMost: Boolean, clickThrough: Boolean) {
        val user32 = User32.INSTANCE
        val ex = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        user32.SetWindowLong(hwnd, GWL_EXSTYLE, OverlayZOrder.extendedStyle(ex, topMost, clickThrough))
        User32.INSTANCE.SetWindowPos(
            hwnd,
            if (topMost) HWND_TOPMOST else HWND_NOTOPMOST,
            0,
            0,
            0,
            0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE or SWP_FRAMECHANGED,
        )
    }

    fun applyToolWindowAndTopMost(hwnd: WinDef.HWND) {
        applyStyle(hwnd, topMost = true, clickThrough = false)
    }

    fun raise(hwnd: WinDef.HWND) {
        applyStyle(hwnd, topMost = true, clickThrough = isClickThrough(hwnd))
    }

    fun setClickThrough(hwnd: WinDef.HWND, enable: Boolean) {
        applyStyle(hwnd, topMost = isTopMost(hwnd), clickThrough = enable)
    }

    fun isTopMost(hwnd: WinDef.HWND): Boolean {
        val ex = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE)
        return (ex and OverlayZOrder.WS_EX_TOPMOST) != 0
    }

    fun isClickThrough(hwnd: WinDef.HWND): Boolean {
        val ex = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE)
        return (ex and OverlayZOrder.WS_EX_TRANSPARENT) != 0
    }

    /**
     * [overlay]를 [target] 바로 위에 둡니다. 전역 TOPMOST는 건드리지 않습니다.
     */
    fun placeJustAbove(overlay: WinDef.HWND, target: WinDef.HWND) {
        User32.INSTANCE.SetWindowPos(
            target,
            overlay,
            0,
            0,
            0,
            0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE or SWP_NOOWNERZORDER,
        )
    }

    fun findProcessMainWindow(exeFileName: String, exclude: Collection<WinDef.HWND> = emptyList()): WinDef.HWND? {
        val excluded = exclude.map { Pointer.nativeValue(it.pointer) }.toSet()
        var best: WinDef.HWND? = null
        var bestArea = 0
        val user32 = User32.INSTANCE
        user32.EnumWindows({ hwnd, _ ->
            if (hwnd == null) return@EnumWindows true
            if (Pointer.nativeValue(hwnd.pointer) in excluded) return@EnumWindows true
            if (!user32.IsWindowVisible(hwnd)) return@EnumWindows true
            val path = processImagePath(hwnd) ?: return@EnumWindows true
            if (!OverlayZOrder.matchesExe(path, exeFileName)) return@EnumWindows true
            val rect = WinDef.RECT()
            if (!user32.GetWindowRect(hwnd, rect)) return@EnumWindows true
            val area = (rect.right - rect.left) * (rect.bottom - rect.top)
            if (area > bestArea && area >= MIN_GAME_AREA) {
                bestArea = area
                best = hwnd
            }
            true
        }, Pointer.NULL)
        return best
    }

    fun processImagePath(hwnd: WinDef.HWND): String? {
        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        if (pidRef.value <= 0) return null
        val hProcess = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
            false,
            pidRef.value,
        ) ?: return null
        return try {
            val buf = Memory(2048)
            Psapi.INSTANCE.GetModuleFileNameEx(hProcess, null, buf, 1024)
            buf.getWideString(0)
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess)
        }
    }
}
