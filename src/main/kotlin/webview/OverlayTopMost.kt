package com.tbread.webview

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef

/**
 * JavaFX `isAlwaysOnTop`만으로는 게임(보더리스)이 덮는 경우가 있어,
 * WS_EX_TOPMOST 와 HWND_TOPMOST 를 네이티브로 다시 올립니다.
 */
object OverlayTopMost {
    private const val GWL_EXSTYLE = -20
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000
    private const val WS_EX_TOPMOST = 0x00000008
    private const val WS_EX_LAYERED = 0x00080000
    private const val WS_EX_TRANSPARENT = 0x00000020
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    private val HWND_TOPMOST = WinDef.HWND(Pointer.createConstant(-1))

    fun find(title: String): WinDef.HWND? = User32.INSTANCE.FindWindow(null, title)

    fun applyToolWindowAndTopMost(hwnd: WinDef.HWND) {
        val user32 = User32.INSTANCE
        val ex = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        user32.SetWindowLong(
            hwnd,
            GWL_EXSTYLE,
            (ex or WS_EX_TOOLWINDOW or WS_EX_TOPMOST or WS_EX_LAYERED) and WS_EX_APPWINDOW.inv(),
        )
        raise(hwnd)
    }

    fun raise(hwnd: WinDef.HWND) {
        User32.INSTANCE.SetWindowPos(
            hwnd,
            HWND_TOPMOST,
            0,
            0,
            0,
            0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE or SWP_FRAMECHANGED,
        )
    }

    fun setClickThrough(hwnd: WinDef.HWND, enable: Boolean) {
        val user32 = User32.INSTANCE
        val ex = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        val next = if (enable) {
            ex or WS_EX_LAYERED or WS_EX_TRANSPARENT or WS_EX_TOPMOST
        } else {
            (ex or WS_EX_LAYERED or WS_EX_TOPMOST) and WS_EX_TRANSPARENT.inv()
        }
        user32.SetWindowLong(hwnd, GWL_EXSTYLE, next)
        raise(hwnd)
    }
}
