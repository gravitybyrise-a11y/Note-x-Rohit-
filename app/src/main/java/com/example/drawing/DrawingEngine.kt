package com.example.drawing

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.storage.CanvasPoint

object StylusManager {
    fun isStylus(event: MotionEvent): Boolean {
        for (i in 0 until event.pointerCount) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) {
                return true
            }
        }
        return false
    }
}

object PalmReject {
    // If a stylus is actively drawing, we reject all finger touch events to prevent palm smudging
    fun shouldIgnore(event: MotionEvent, isStylusActive: Boolean): Boolean {
        val toolType = event.getToolType(0)
        
        // Palm tool type is explicitly rejected (literal 3 corresponds to MotionEvent.TOOL_TYPE_PALM)
        if (toolType == 3) {
            return true
        }
        
        // Large finger surface contacts (indicative of resting a palm) are rejected
        if (toolType == MotionEvent.TOOL_TYPE_FINGER && event.size > 0.55f) {
            return true
        }
        
        // If the stylus is near or active, reject fingers entirely
        if (isStylusActive && toolType == MotionEvent.TOOL_TYPE_FINGER) {
            return true
        }
        
        return false
    }
}

object StrokeSmoother {
    // Standard moving average smoothing over 3 consecutive points
    fun smooth(points: List<CanvasPoint>): List<CanvasPoint> {
        if (points.size < 3) return points

        val output = mutableListOf<CanvasPoint>()
        output.add(points.first())

        for (i in 1 until points.lastIndex) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]

            output.add(
                CanvasPoint(
                    x = (p0.x + p1.x + p2.x) / 3f,
                    y = (p0.y + p1.y + p2.y) / 3f,
                    pressure = p1.pressure
                )
            )
        }

        output.add(points.last())
        return output
    }
}

object PathSmoother {
    // Builds a beautifully smooth Jetpack Compose Path using quadratic Bézier curve interpolation
    fun createSmoothPath(points: List<CanvasPoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size < 3) {
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            return path
        }

        path.moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 2) {
            val p0 = points[i]
            val p1 = points[i + 1]
            
            // Draw curve towards midpoint of subsequent segments for fluid joints
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f
            path.quadraticTo(p0.x, p0.y, midX, midY)
        }
        // Last connection to complete the stroke
        val penultimate = points[points.size - 2]
        val last = points.last()
        path.quadraticTo(penultimate.x, penultimate.y, last.x, last.y)
        
        return path
    }
}

object PressureEngine {
    // Dynamically scale width based on stroke speed or stylus pressure readings
    fun calculateWidth(pressure: Float, baseWidth: Float): Float {
        // Enforce stylus bounds
        val normalized = pressure.coerceIn(0.1f, 1.5f)
        return baseWidth * (0.4f + 0.6f * normalized)
    }
}

object VelocityEngine {
    // Calculate width variations on speed to make drawings look organic even with fingers
    fun calculateVelocityWidth(current: Offset, previous: Offset, timestamp: Long, lastTimestamp: Long, baseWidth: Float): Float {
        if (timestamp == lastTimestamp) return baseWidth
        val dx = current.x - previous.x
        val dy = current.y - previous.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val timeDiff = (timestamp - lastTimestamp).toFloat() // in ms
        if (timeDiff <= 0) return baseWidth
        
        val velocity = distance / timeDiff // px/ms
        
        // Fast stroke -> thinner lines, slow stroke -> thicker lines (classical ink pen physics)
        val factor = when {
            velocity < 0.5f -> 1.2f
            velocity < 1.5f -> 1.0f
            velocity < 3.0f -> 0.8f
            velocity < 6.0f -> 0.6f
            else -> 0.4f
        }
        return baseWidth * factor
    }
}
