package com.tbread.webview

data class ScreenRect(
    val minX: Double,
    val minY: Double,
    val width: Double,
    val height: Double,
) {
    val maxX: Double get() = minX + width
    val maxY: Double get() = minY + height
}

object OverlayGeometry {
    fun union(screens: List<ScreenRect>): ScreenRect {
        if (screens.isEmpty()) {
            return ScreenRect(0.0, 0.0, 1920.0, 1080.0)
        }
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (screen in screens) {
            minX = minOf(minX, screen.minX)
            minY = minOf(minY, screen.minY)
            maxX = maxOf(maxX, screen.maxX)
            maxY = maxOf(maxY, screen.maxY)
        }
        return ScreenRect(minX, minY, maxX - minX, maxY - minY)
    }
}
