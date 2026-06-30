package com.example.export

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.storage.BrushType
import com.example.storage.PageEntity
import com.example.storage.Stroke
import com.example.storage.StrokesConverter
import java.io.ByteArrayOutputStream

object ExportManager {

    fun exportNotebookToPdf(
        pages: List<PageEntity>,
        converter: StrokesConverter
    ): ByteArray {
        val pdfDocument = PdfDocument()
        val width = 1200
        val height = 1600

        pages.forEachIndexed { index, pageEntity ->
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Draw background
            val bgPaint = Paint().apply {
                color = pageEntity.backgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Draw patterns
            drawBackgroundPattern(canvas, pageEntity.backgroundType, pageEntity.backgroundColor, width, height)

            // Draw strokes
            val strokes = pageEntity.getStrokes(converter)
            strokes.forEach { stroke ->
                drawStrokeOnCanvas(canvas, stroke)
            }

            pdfDocument.finishPage(page)
        }

        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        return outputStream.toByteArray()
    }

    fun exportPageToPng(
        pageEntity: PageEntity,
        converter: StrokesConverter
    ): ByteArray {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        val bgPaint = Paint().apply {
            color = pageEntity.backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw patterns
        drawBackgroundPattern(canvas, pageEntity.backgroundType, pageEntity.backgroundColor, width, height)

        // Draw strokes
        val strokes = pageEntity.getStrokes(converter)
        strokes.forEach { stroke ->
            drawStrokeOnCanvas(canvas, stroke)
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()
        return outputStream.toByteArray()
    }

    private fun drawBackgroundPattern(canvas: Canvas, type: String, bgColor: Int, width: Int, height: Int) {
        val patternColor = if (bgColor == 0xFF000000.toInt() || bgColor == 0xFF121212.toInt()) {
            Color.parseColor("#22FFFFFF") // Subtle light lines on dark background
        } else {
            Color.parseColor("#22000000") // Subtle dark lines on light background
        }

        val patternPaint = Paint().apply {
            color = patternColor
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        when (type) {
            "GRID" -> {
                val gridSize = 60f
                var x = 0f
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), patternPaint)
                    x += gridSize
                }
                var y = 0f
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, patternPaint)
                    y += gridSize
                }
            }
            "RULED" -> {
                val rowHeight = 70f
                var y = 140f
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, patternPaint)
                    y += rowHeight
                }
            }
            "DOTTED" -> {
                patternPaint.style = Paint.Style.FILL
                val dotSize = 3f
                val spacing = 60f
                var x = spacing
                while (x < width) {
                    var y = spacing
                    while (y < height) {
                        canvas.drawCircle(x, y, dotSize, patternPaint)
                        y += spacing
                    }
                    x += spacing
                }
            }
            "GRAPH" -> {
                val gridSize = 30f
                var x = 0f
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), patternPaint)
                    x += gridSize
                }
                var y = 0f
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, patternPaint)
                    y += gridSize
                }
            }
            "MUSIC_SHEET" -> {
                val margin = 120f
                val staffSpacing = 14f
                val systemSpacing = 160f
                var startY = margin
                while (startY + 4 * staffSpacing < height) {
                    for (i in 0..4) {
                        val y = startY + i * staffSpacing
                        canvas.drawLine(40f, y, width.toFloat() - 40f, y, patternPaint)
                    }
                    startY += 4 * staffSpacing + systemSpacing
                }
            }
        }
    }

    private fun drawStrokeOnCanvas(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.isEmpty()) return

        val paint = Paint().apply {
            color = stroke.color
            strokeWidth = stroke.width
            alpha = (stroke.alpha * 255).toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        if (stroke.brushType == BrushType.HIGHLIGHTER) {
            paint.strokeCap = Paint.Cap.SQUARE
        }

        val path = Path()
        val points = stroke.points
        if (points.size < 3) {
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
        } else {
            path.moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 2) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                val midY = (p0.y + p1.y) / 2f
                path.quadTo(p0.x, p0.y, midX, midY)
            }
            val penultimate = points[points.size - 2]
            val last = points.last()
            path.quadTo(penultimate.x, penultimate.y, last.x, last.y)
        }

        canvas.drawPath(path, paint)
    }
}
