package com.tbread.webview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayZOrderTest {
    @Test
    fun globalModeStaysTopMostWhileVisible() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GLOBAL,
            overlayVisible = true,
            gameFound = true,
            gameIsTopMost = false,
            gameFocused = false,
            selfFocused = false,
            autoHide = false,
            autoHideShouldShow = false,
        )
        assertEquals(true, plan.topMost)
        assertEquals(false, plan.placeAboveGame)
    }

    @Test
    fun hiddenOverlayDropsTopMost() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GLOBAL,
            overlayVisible = false,
            gameFound = true,
            gameIsTopMost = true,
            gameFocused = true,
            selfFocused = false,
            autoHide = true,
            autoHideShouldShow = true,
        )
        assertEquals(false, plan.topMost)
        assertEquals(false, plan.placeAboveGame)
    }

    @Test
    fun autoHideDropsTopMostWhenGameNotFocused() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GAME_WINDOW,
            overlayVisible = true,
            gameFound = true,
            gameIsTopMost = true,
            gameFocused = false,
            selfFocused = false,
            autoHide = true,
            autoHideShouldShow = false,
        )
        assertEquals(false, plan.topMost)
        assertEquals(false, plan.placeAboveGame)
    }

    @Test
    fun gameWindowModeSitsAboveWindowedGame() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GAME_WINDOW,
            overlayVisible = true,
            gameFound = true,
            gameIsTopMost = false,
            gameFocused = true,
            selfFocused = false,
            autoHide = true,
            autoHideShouldShow = true,
        )
        assertEquals(false, plan.topMost)
        assertEquals(true, plan.placeAboveGame)
    }

    @Test
    fun borderlessGameKeepsTopMostWhileFocused() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GAME_WINDOW,
            overlayVisible = true,
            gameFound = true,
            gameIsTopMost = true,
            gameFocused = true,
            selfFocused = false,
            autoHide = false,
            autoHideShouldShow = false,
        )
        assertEquals(true, plan.topMost)
        assertEquals(true, plan.placeAboveGame)
    }

    @Test
    fun otherAppInFrontDropsTopMostWithoutRestackingGame() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GAME_WINDOW,
            overlayVisible = true,
            gameFound = true,
            gameIsTopMost = true,
            gameFocused = false,
            selfFocused = false,
            autoHide = false,
            autoHideShouldShow = false,
        )
        assertEquals(false, plan.topMost)
        assertEquals(false, plan.placeAboveGame)
    }

    @Test
    fun settingsFocusStillAttachesToGame() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GAME_WINDOW,
            overlayVisible = true,
            gameFound = true,
            gameIsTopMost = false,
            gameFocused = false,
            selfFocused = true,
            autoHide = false,
            autoHideShouldShow = false,
        )
        assertEquals(false, plan.topMost)
        assertEquals(true, plan.placeAboveGame)
    }

    @Test
    fun missingGameFallsBackToTopMost() {
        val plan = OverlayZOrder.plan(
            mode = OverlayZOrder.Mode.GAME_WINDOW,
            overlayVisible = true,
            gameFound = false,
            gameIsTopMost = false,
            gameFocused = false,
            selfFocused = false,
            autoHide = false,
            autoHideShouldShow = false,
        )
        assertEquals(true, plan.topMost)
        assertEquals(false, plan.placeAboveGame)
    }

    @Test
    fun matchesAion2ExePath() {
        assertTrue(OverlayZOrder.matchesExe("""C:\NCSOFT\Aion2\Binaries\Win64\Aion2.exe""", "Aion2.exe"))
        assertTrue(OverlayZOrder.matchesExe("Aion2.exe", "Aion2.exe"))
        assertFalse(OverlayZOrder.matchesExe("""C:\Users\me\AppData\Discord.exe""", "Aion2.exe"))
        assertFalse(OverlayZOrder.matchesExe("NotAion2.exe", "Aion2.exe"))
    }

    @Test
    fun extendedStyleClearsTopMostAndSetsClickThrough() {
        val current = OverlayZOrder.WS_EX_APPWINDOW or OverlayZOrder.WS_EX_TOPMOST
        val next = OverlayZOrder.extendedStyle(current, topMost = false, clickThrough = true)
        assertEquals(0, next and OverlayZOrder.WS_EX_APPWINDOW)
        assertEquals(0, next and OverlayZOrder.WS_EX_TOPMOST)
        assertTrue(next and OverlayZOrder.WS_EX_TRANSPARENT != 0)
        assertTrue(next and OverlayZOrder.WS_EX_TOOLWINDOW != 0)
        assertTrue(next and OverlayZOrder.WS_EX_LAYERED != 0)
    }
}
