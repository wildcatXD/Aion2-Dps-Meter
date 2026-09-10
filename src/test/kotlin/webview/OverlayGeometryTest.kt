package com.tbread.webview

import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayGeometryTest {
    @Test
    fun emptyFallsBackTo1080p() {
        val union = OverlayGeometry.union(emptyList())
        assertEquals(0.0, union.minX)
        assertEquals(1920.0, union.width)
        assertEquals(1080.0, union.height)
    }

    @Test
    fun leftMonitorNegativeOrigin() {
        val union = OverlayGeometry.union(
            listOf(
                ScreenRect(-1920.0, 0.0, 1920.0, 1080.0),
                ScreenRect(0.0, 0.0, 2560.0, 1440.0),
            ),
        )
        assertEquals(-1920.0, union.minX)
        assertEquals(0.0, union.minY)
        assertEquals(4480.0, union.width)
        assertEquals(1440.0, union.height)
    }
}
