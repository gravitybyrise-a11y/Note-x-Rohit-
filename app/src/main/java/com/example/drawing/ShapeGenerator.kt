package com.example.drawing

import com.example.storage.CanvasPoint
import kotlin.math.cos
import kotlin.math.sin

object ShapeGenerator {

    fun generateShapePoints(
        type: String,
        start: CanvasPoint,
        end: CanvasPoint
    ): List<CanvasPoint> {
        val points = mutableListOf<CanvasPoint>()
        when (type) {
            "LINE" -> {
                points.add(start)
                points.add(end)
            }
            "RECTANGLE" -> {
                points.add(start)
                points.add(CanvasPoint(end.x, start.y, 1.0f))
                points.add(end)
                points.add(CanvasPoint(start.x, end.y, 1.0f))
                points.add(start)
            }
            "CIRCLE" -> {
                val cx = (start.x + end.x) / 2f
                val cy = (start.y + end.y) / 2f
                val rx = kotlin.math.abs(end.x - start.x) / 2f
                val ry = kotlin.math.abs(end.y - start.y) / 2f
                
                val segments = 40
                for (i in 0..segments) {
                    val angle = (2 * kotlin.math.PI * i / segments).toFloat()
                    val x = cx + rx * cos(angle)
                    val y = cy + ry * sin(angle)
                    points.add(CanvasPoint(x, y, 1.0f))
                }
            }
            "TRIANGLE" -> {
                val topX = (start.x + end.x) / 2f
                val topY = start.y
                points.add(CanvasPoint(topX, topY, 1.0f))
                points.add(end) // Bottom right
                points.add(CanvasPoint(start.x, end.y, 1.0f)) // Bottom left
                points.add(CanvasPoint(topX, topY, 1.0f)) // Back to top
            }
            "ARROW" -> {
                points.add(start)
                points.add(end)
                
                // Calculate arrowhead lines
                val dx = end.x - start.x
                val dy = end.y - start.y
                val length = kotlin.math.sqrt(dx * dx + dy * dy)
                if (length > 10f) {
                    val arrowLength = (length * 0.2f).coerceIn(15f, 60f)
                    val angle = kotlin.math.atan2(dy, dx)
                    
                    // Left fin
                    val leftAngle = angle - (kotlin.math.PI / 6).toFloat() // 30 degrees
                    val lx = end.x - arrowLength * cos(leftAngle)
                    val ly = end.y - arrowLength * sin(leftAngle)
                    
                    // Right fin
                    val rightAngle = angle + (kotlin.math.PI / 6).toFloat() // 30 degrees
                    val rx = end.x - arrowLength * cos(rightAngle)
                    val ry = end.y - arrowLength * sin(rightAngle)
                    
                    points.add(CanvasPoint(lx, ly, 1.0f))
                    points.add(end)
                    points.add(CanvasPoint(rx, ry, 1.0f))
                }
            }
        }
        return points
    }
}
