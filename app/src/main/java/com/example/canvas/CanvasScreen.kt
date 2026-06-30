package com.example.canvas

import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.BrushType
import com.example.storage.CanvasPoint
import com.example.storage.ShapeType
import com.example.storage.Stroke
import com.example.storage.StrokesConverter
import com.example.drawing.*
import com.example.export.ExportManager
import com.example.ui.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasScreen(
    viewModel: NoteXViewModel,
    onBackToDashboard: () -> Unit,
    onSaveDocument: (title: String, bytes: ByteArray, isPdf: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeNotebook by viewModel.activeNotebook.collectAsState()
    val activePages by viewModel.activePages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val activeStrokes by viewModel.activePageStrokes.collectAsState()

    val tool by viewModel.activeTool.collectAsState()
    val pen by viewModel.penState.collectAsState()
    val eraser by viewModel.eraserState.collectAsState()

    // Infinite Canvas Pan & Zoom States
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Screen Dimensions measured inside the Canvas drawing block
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }

    // Floating UI popup visibilities
    var showPenPopup by remember { mutableStateOf(false) }
    var showEraserPopup by remember { mutableStateOf(false) }
    var showBackgroundPopup by remember { mutableStateOf(false) }
    var showPagesSidebar by remember { mutableStateOf(false) }

    // Current page reference
    val currentPage = activePages.getOrNull(currentPageIndex)

    // Touch Drawing State
    val activePoints = remember { mutableStateListOf<CanvasPoint>() }
    var drawingStrokeColor by remember { mutableStateOf(Color.White) }
    var isStylusActive by remember { mutableStateOf(false) }

    // Multi-touch Pan & Zoom helpers for pointerInteropFilter
    var initialSpacing by remember { mutableFloatStateOf(1.0f) }
    var initialMidPoint by remember { mutableStateOf(Offset.Zero) }
    var initialScale by remember { mutableFloatStateOf(1.0f) }
    var initialOffset by remember { mutableStateOf(Offset.Zero) }
    var touchPointsCount by remember { mutableStateOf(0) }

    // Shape drawing initial point tracking
    var shapeStartPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var shapeEndPoint by remember { mutableStateOf<CanvasPoint?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Listen for notification messages
    LaunchedEffect(Unit) {
        viewModel.infoMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (activeNotebook == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // --- 1. INFINITE WHITEBOARD CANVAS LAYER ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("whiteboard_canvas")
                .pointerInteropFilter { motionEvent ->
                    touchPointsCount = motionEvent.pointerCount
                    isStylusActive = StylusManager.isStylus(motionEvent)

                    // Apply stylus palm rejection if appropriate
                    if (PalmReject.shouldIgnore(motionEvent, isStylusActive)) {
                        return@pointerInteropFilter true
                    }

                    when (motionEvent.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (touchPointsCount == 1) {
                                val screenPt = Offset(motionEvent.x, motionEvent.y)
                                // Convert to Canvas Coordinate Space
                                val mappedPt = (screenPt - panOffset) / scale
                                val initialCanvasPoint = CanvasPoint(mappedPt.x, mappedPt.y, motionEvent.pressure)

                                if (tool == ActiveTool.SHAPE) {
                                    shapeStartPoint = initialCanvasPoint
                                    shapeEndPoint = initialCanvasPoint
                                } else if (tool != ActiveTool.HAND) {
                                    activePoints.clear()
                                    activePoints.add(initialCanvasPoint)
                                }
                            }
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (touchPointsCount >= 2) {
                                // Cancel any current drawing stroke to prioritize panning/zooming
                                activePoints.clear()
                                shapeStartPoint = null
                                shapeEndPoint = null

                                // Initialize gesture scale and offset trackers
                                initialSpacing = spacing(motionEvent)
                                initialMidPoint = midPoint(motionEvent)
                                initialScale = scale
                                initialOffset = panOffset
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (touchPointsCount >= 2) {
                                // Perform two-finger infinite pan & pinch zoom scaling
                                val currentSpacing = spacing(motionEvent)
                                val currentMid = midPoint(motionEvent)

                                val newScale = (initialScale * (currentSpacing / initialSpacing)).coerceIn(0.15f, 8.0f)
                                val deltaPan = currentMid - initialMidPoint
                                val newOffset = initialOffset + deltaPan

                                scale = newScale
                                panOffset = newOffset
                            } else if (touchPointsCount == 1) {
                                val screenPt = Offset(motionEvent.x, motionEvent.y)
                                val mappedPt = (screenPt - panOffset) / scale

                                when (tool) {
                                    ActiveTool.PEN, ActiveTool.HIGHLIGHTER -> {
                                        // 120Hz sub-frame coordinate history extraction
                                        val historySize = motionEvent.historySize
                                        for (h in 0 until historySize) {
                                            val histX = motionEvent.getHistoricalX(0, h)
                                            val histY = motionEvent.getHistoricalY(0, h)
                                            val histPressure = motionEvent.getHistoricalPressure(0, h)
                                            val hMapped = (Offset(histX, histY) - panOffset) / scale
                                            activePoints.add(CanvasPoint(hMapped.x, hMapped.y, histPressure))
                                        }
                                        activePoints.add(CanvasPoint(mappedPt.x, mappedPt.y, motionEvent.pressure))
                                    }

                                    ActiveTool.ERASER -> {
                                        val radius = eraser.size / scale
                                        val eraserCanvasCenter = mappedPt

                                        if (eraser.type == EraserType.PIXEL) {
                                            // Erase nearby pixels of active strokes
                                            val strokesToKeep = activeStrokes.map { s ->
                                                val remainingPoints = s.points.filter { pt ->
                                                    val dist = distance(pt, eraserCanvasCenter)
                                                    dist > radius
                                                }
                                                s.copy(points = remainingPoints)
                                            }.filter { it.points.isNotEmpty() }
                                            viewModel.activePageStrokes.value = strokesToKeep
                                        } else {
                                            // Erase whole strokes overlapping eraser brush boundary
                                            val strokesToKeep = activeStrokes.filter { s ->
                                                val overlaps = s.points.any { pt ->
                                                    distance(pt, eraserCanvasCenter) <= radius
                                                }
                                                !overlaps
                                            }
                                            viewModel.activePageStrokes.value = strokesToKeep
                                        }
                                    }

                                    ActiveTool.SHAPE -> {
                                        shapeEndPoint = CanvasPoint(mappedPt.x, mappedPt.y, motionEvent.pressure)
                                    }

                                    ActiveTool.HAND -> {
                                        // Simple dragging pan when hand tool is active
                                        if (motionEvent.historySize > 0) {
                                            val prevX = motionEvent.getHistoricalX(0, 0)
                                            val prevY = motionEvent.getHistoricalY(0, 0)
                                            val delta = screenPt - Offset(prevX, prevY)
                                            panOffset += delta
                                        }
                                    }
                                }
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (touchPointsCount <= 1) {
                                if (activePoints.isNotEmpty() && (tool == ActiveTool.PEN || tool == ActiveTool.HIGHLIGHTER)) {
                                    // Smooth out stroke with moving average filtering
                                    val smoothed = StrokeSmoother.smooth(activePoints)
                                    
                                    val brushWidth = if (isStylusActive) {
                                        PressureEngine.calculateWidth(motionEvent.pressure, pen.size)
                                    } else {
                                        pen.size
                                    }

                                    val finalStroke = Stroke(
                                        points = smoothed,
                                        color = if (tool == ActiveTool.HIGHLIGHTER) pen.color else pen.color,
                                        width = brushWidth,
                                        alpha = if (tool == ActiveTool.HIGHLIGHTER) 0.45f else pen.alpha,
                                        brushType = if (tool == ActiveTool.HIGHLIGHTER) BrushType.HIGHLIGHTER else pen.brush
                                    )
                                    viewModel.addStroke(finalStroke)
                                    activePoints.clear()
                                } else if (tool == ActiveTool.SHAPE && shapeStartPoint != null && shapeEndPoint != null) {
                                    val start = shapeStartPoint!!
                                    val end = shapeEndPoint!!
                                    val shapePoints = ShapeGenerator.generateShapePoints(pen.shapeType.name, start, end)
                                    if (shapePoints.isNotEmpty()) {
                                        val shapeStroke = Stroke(
                                            points = shapePoints,
                                            color = pen.color,
                                            width = pen.size,
                                            alpha = pen.alpha,
                                            brushType = pen.brush,
                                            shapeType = pen.shapeType
                                        )
                                        viewModel.addStroke(shapeStroke)
                                    }
                                    shapeStartPoint = null
                                    shapeEndPoint = null
                                }
                            }
                            touchPointsCount = 0
                        }
                    }
                    true
                }
        ) {
            screenWidth = size.width
            screenHeight = size.height

            // Render Whiteboard page background pattern
            currentPage?.let { page ->
                drawRect(
                    color = Color(page.backgroundColor),
                    size = size
                )

                // Render grid patterns transformed by our current infinite viewport matrix
                withTransform({
                    scale(scale, scale, pivot = Offset.Zero)
                    translate(panOffset.x / scale, panOffset.y / scale)
                }) {
                    drawWhiteboardBackgroundPattern(
                        drawScope = this,
                        type = page.backgroundType,
                        bgColor = Color(page.backgroundColor),
                        width = 10000f, // Large virtual boundary
                        height = 10000f,
                        scale = scale,
                        panOffset = panOffset,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight
                    )

                    // Draw all saved historical whiteboard strokes with smart clipping optimization
                    val viewportLeft = -panOffset.x / scale
                    val viewportTop = -panOffset.y / scale
                    val viewportRight = (screenWidth - panOffset.x) / scale
                    val viewportBottom = (screenHeight - panOffset.y) / scale

                    activeStrokes.forEach { stroke ->
                        // Optimize render bounds: only draw if stroke bounding box overlaps viewport window
                        if (isStrokeVisible(stroke, viewportLeft, viewportTop, viewportRight, viewportBottom)) {
                            drawWhiteboardStroke(this, stroke)
                        }
                    }

                    // Render active, real-time handwriting paths
                    if (activePoints.isNotEmpty()) {
                        val activeStroke = Stroke(
                            points = activePoints.toList(),
                            color = pen.color,
                            width = pen.size,
                            alpha = if (tool == ActiveTool.HIGHLIGHTER) 0.45f else pen.alpha,
                            brushType = if (tool == ActiveTool.HIGHLIGHTER) BrushType.HIGHLIGHTER else pen.brush
                        )
                        drawWhiteboardStroke(this, activeStroke)
                    }

                    // Render shape preview line during active drawing
                    if (tool == ActiveTool.SHAPE && shapeStartPoint != null && shapeEndPoint != null) {
                        val points = ShapeGenerator.generateShapePoints(
                            pen.shapeType.name,
                            shapeStartPoint!!,
                            shapeEndPoint!!
                        )
                        if (points.isNotEmpty()) {
                            val previewStroke = Stroke(
                                points = points,
                                color = pen.color,
                                width = pen.size,
                                alpha = 0.5f,
                                brushType = pen.brush,
                                shapeType = pen.shapeType
                            )
                            drawWhiteboardStroke(this, previewStroke)
                        }
                    }
                }
            }
        }

        // --- 2. FLOATING TOP PANEL (Whiteboard Status Center) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Return & Notebook info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF1C1C1C).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onBackToDashboard,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Column {
                    Text(
                        text = "${activeNotebook?.title?.uppercase() ?: "NOTE"} • LOCAL",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("PAGE ${currentPageIndex + 1} OF ${activePages.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                    }
                }
            }

            // Top-Center Zoom Indicator
            Box(
                modifier = Modifier
                    .background(Color(0xFF1C1C1C).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    .clickable {
                        // Double tap / Click zoom resets offset and zoom scale back to default
                        scale = 1.0f
                        panOffset = Offset.Zero
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ZoomIn, null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${(scale * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Right-Side Export & presentations panel
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PDF Export trigger
                Button(
                    onClick = {
                        val pdfBytes = ExportManager.exportNotebookToPdf(activePages, viewModel.getConverter())
                        onSaveDocument("${activeNotebook?.title ?: "Note"}_Notebook", pdfBytes, true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF004A77),
                        contentColor = Color(0xFFD2E3FC)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // PNG Export trigger
                Button(
                    onClick = {
                        currentPage?.let { page ->
                            val pngBytes = ExportManager.exportPageToPng(page, viewModel.getConverter())
                            onSaveDocument("${activeNotebook?.title ?: "Note"}_Page_${currentPageIndex + 1}", pngBytes, false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Battery friendly & Hardware optimization tag
                Box(
                    modifier = Modifier
                        .background(Color(0xFFA8C7FA).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color(0xFFA8C7FA).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, "Fast rendering optimized", tint = Color(0xFFA8C7FA), modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("120Hz", color = Color(0xFFA8C7FA), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 3. FLOATING POPUP OVERLAYS ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            // Pen settings popup
            PenPopup(
                visible = showPenPopup && tool == ActiveTool.PEN,
                state = pen,
                onStateChanged = { viewModel.penState.value = it },
                onDismiss = { showPenPopup = false }
            )

            // Eraser settings popup
            EraserPopup(
                visible = showEraserPopup && tool == ActiveTool.ERASER,
                state = eraser,
                onStateChanged = { viewModel.eraserState.value = it },
                onClearAll = {
                    viewModel.clearCurrentPage()
                    showEraserPopup = false
                },
                onDismiss = { showEraserPopup = false }
            )

            // Background theme settings popup
            BackgroundSelector(
                visible = showBackgroundPopup,
                currentType = currentPage?.backgroundType ?: "BLACK_CANVAS",
                currentBgColor = currentPage?.backgroundColor ?: 0xFF000000.toInt(),
                onChanged = { type, color, opacity ->
                    viewModel.setPageBackground(type, color, opacity)
                },
                onDismiss = { showBackgroundPopup = false }
            )
        }

        // --- 4. FLOATING BOTTOM GLASSMORPHIC NAVIGATION TOOLBAR ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            GlassToolbar {
                // PEN Button
                ToolbarButton(
                    icon = Icons.Default.Edit,
                    description = "Pen",
                    selected = tool == ActiveTool.PEN,
                    onClick = {
                        if (tool == ActiveTool.PEN) {
                            showPenPopup = !showPenPopup
                        } else {
                            viewModel.activeTool.value = ActiveTool.PEN
                            showEraserPopup = false
                            showBackgroundPopup = false
                        }
                    }
                )

                // HIGHLIGHTER Button
                ToolbarButton(
                    icon = Icons.Outlined.Create,
                    description = "Highlighter",
                    selected = tool == ActiveTool.HIGHLIGHTER,
                    onClick = {
                        viewModel.activeTool.value = ActiveTool.HIGHLIGHTER
                        showPenPopup = false
                        showEraserPopup = false
                        showBackgroundPopup = false
                    }
                )

                // ERASER Button
                ToolbarButton(
                    icon = Icons.Default.Layers,
                    description = "Eraser",
                    selected = tool == ActiveTool.ERASER,
                    onClick = {
                        if (tool == ActiveTool.ERASER) {
                            showEraserPopup = !showEraserPopup
                        } else {
                            viewModel.activeTool.value = ActiveTool.ERASER
                            showPenPopup = false
                            showBackgroundPopup = false
                        }
                    }
                )

                // SHAPE Button
                ToolbarButton(
                    icon = getShapeIcon(pen.shapeType),
                    description = "Shapes",
                    selected = tool == ActiveTool.SHAPE,
                    onClick = {
                        viewModel.activeTool.value = ActiveTool.SHAPE
                        showPenPopup = false
                        showEraserPopup = false
                        showBackgroundPopup = false
                        
                        // Rotates through different shapes sequentially upon multiple clicks
                        val nextShape = when (pen.shapeType) {
                            ShapeType.NONE -> ShapeType.LINE
                            ShapeType.LINE -> ShapeType.RECTANGLE
                            ShapeType.RECTANGLE -> ShapeType.CIRCLE
                            ShapeType.CIRCLE -> ShapeType.TRIANGLE
                            ShapeType.TRIANGLE -> ShapeType.ARROW
                            ShapeType.ARROW -> ShapeType.NONE
                        }
                        viewModel.penState.value = pen.copy(shapeType = nextShape)
                    }
                )

                // HAND (Pan / Zoom) Button
                ToolbarButton(
                    icon = Icons.Default.PanTool,
                    description = "Hand Tool",
                    selected = tool == ActiveTool.HAND,
                    onClick = {
                        viewModel.activeTool.value = ActiveTool.HAND
                        showPenPopup = false
                        showEraserPopup = false
                        showBackgroundPopup = false
                    }
                )

                Divider(
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .height(26.dp)
                        .width(1.dp)
                )

                // UNDO Button
                ToolbarButton(
                    icon = Icons.Default.Undo,
                    description = "Undo",
                    selected = false,
                    onClick = { viewModel.undo() }
                )

                // REDO Button
                ToolbarButton(
                    icon = Icons.Default.Redo,
                    description = "Redo",
                    selected = false,
                    onClick = { viewModel.redo() }
                )

                Divider(
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .height(26.dp)
                        .width(1.dp)
                )

                // BACKGROUND Style Button
                ToolbarButton(
                    icon = Icons.Default.GridOn,
                    description = "Canvas Background",
                    selected = showBackgroundPopup,
                    onClick = {
                        showBackgroundPopup = !showBackgroundPopup
                        showPenPopup = false
                        showEraserPopup = false
                    }
                )

                // PAGES Sidebar Toggle
                ToolbarButton(
                    icon = Icons.Default.AutoAwesomeMotion,
                    description = "Pages Drawer",
                    selected = showPagesSidebar,
                    onClick = {
                        showPagesSidebar = !showPagesSidebar
                        showPenPopup = false
                        showEraserPopup = false
                    }
                )
            }
        }

        // --- 5. FLOATING PAGES DRAWER / SIDEBAR (Right Alignment) ---
        PageThumbnailsSidebar(
            visible = showPagesSidebar,
            pages = activePages,
            currentIndex = currentPageIndex,
            onPageSelected = { index ->
                viewModel.navigateToPage(index)
            },
            onAddPage = { viewModel.addNewPage() },
            onDuplicate = { viewModel.duplicateCurrentPage() },
            onDelete = { viewModel.deleteCurrentPage() },
            onDismiss = { showPagesSidebar = false },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // --- 6. FLOATING SYSTEM SNACKBAR HOST ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) Color.White else Color.LightGray.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun spacing(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 1.0f
    val x = event.getX(0) - event.getX(1)
    val y = event.getY(0) - event.getY(1)
    return kotlin.math.sqrt(x * x + y * y)
}

private fun midPoint(event: MotionEvent): Offset {
    if (event.pointerCount < 2) return Offset.Zero
    val x = (event.getX(0) + event.getX(1)) / 2f
    val y = (event.getY(0) + event.getY(1)) / 2f
    return Offset(x, y)
}

private fun distance(p1: CanvasPoint, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun getShapeIcon(type: ShapeType): ImageVector {
    return when (type) {
        ShapeType.NONE -> Icons.Default.Category
        ShapeType.LINE -> Icons.Default.Maximize
        ShapeType.RECTANGLE -> Icons.Default.Rectangle
        ShapeType.CIRCLE -> Icons.Default.Circle
        ShapeType.TRIANGLE -> Icons.Default.ChangeHistory
        ShapeType.ARROW -> Icons.Default.TrendingFlat
    }
}

// Bounding-box visible checking algorithm for viewport clipping
private fun isStrokeVisible(
    stroke: Stroke,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): Boolean {
    val pts = stroke.points
    if (pts.isEmpty()) return false

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    for (p in pts) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
    }

    // Return true if overlapping/intersecting with viewport coordinates
    return !(maxX < left || minX > right || maxY < top || minY > bottom)
}

private fun drawWhiteboardBackgroundPattern(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    type: String,
    bgColor: Color,
    width: Float,
    height: Float,
    scale: Float,
    panOffset: Offset,
    screenWidth: Float,
    screenHeight: Float
) {
    // Generate contrast colors
    val patternColor = if (bgColor == Color.Black || bgColor == Color(0xFF121212) || bgColor == Color(0xFF1E272C) || bgColor == Color(0xFF1A1C1E)) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    val viewportLeft = -panOffset.x / scale
    val viewportTop = -panOffset.y / scale
    val viewportRight = (screenWidth - panOffset.x) / scale
    val viewportBottom = (screenHeight - panOffset.y) / scale

    when (type) {
        "GRID" -> {
            val spacing = 70f
            val startX = (viewportLeft / spacing).toInt() * spacing - spacing
            val endX = (viewportRight / spacing).toInt() * spacing + spacing
            val startY = (viewportTop / spacing).toInt() * spacing - spacing
            val endY = (viewportBottom / spacing).toInt() * spacing + spacing

            var x = startX
            while (x <= endX) {
                drawScope.drawLine(
                    color = patternColor,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 1f / scale
                )
                x += spacing
            }

            var y = startY
            while (y <= endY) {
                drawScope.drawLine(
                    color = patternColor,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 1f / scale
                )
                y += spacing
            }
        }

        "RULED" -> {
            val rowHeight = 80f
            val startY = (viewportTop / rowHeight).toInt() * rowHeight - rowHeight
            val endY = (viewportBottom / rowHeight).toInt() * rowHeight + rowHeight

            var y = startY
            while (y <= endY) {
                drawScope.drawLine(
                    color = patternColor,
                    start = Offset(viewportLeft, y),
                    end = Offset(viewportRight, y),
                    strokeWidth = 1f / scale
                )
                y += rowHeight
            }
        }

        "DOTTED" -> {
            val spacing = 70f
            val startX = (viewportLeft / spacing).toInt() * spacing - spacing
            val endX = (viewportRight / spacing).toInt() * spacing + spacing
            val startY = (viewportTop / spacing).toInt() * spacing - spacing
            val endY = (viewportBottom / spacing).toInt() * spacing + spacing

            var x = startX
            while (x <= endX) {
                var y = startY
                while (y <= endY) {
                    drawScope.drawCircle(
                        color = patternColor,
                        radius = 2.5f / scale,
                        center = Offset(x, y)
                    )
                    y += spacing
                }
                x += spacing
            }
        }

        "GRAPH" -> {
            val spacing = 35f
            val startX = (viewportLeft / spacing).toInt() * spacing - spacing
            val endX = (viewportRight / spacing).toInt() * spacing + spacing
            val startY = (viewportTop / spacing).toInt() * spacing - spacing
            val endY = (viewportBottom / spacing).toInt() * spacing + spacing

            var x = startX
            while (x <= endX) {
                drawScope.drawLine(
                    color = patternColor,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 0.5f / scale
                )
                x += spacing
            }

            var y = startY
            while (y <= endY) {
                drawScope.drawLine(
                    color = patternColor,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 0.5f / scale
                )
                y += spacing
            }
        }

        "MUSIC_SHEET" -> {
            val staffSpacing = 16f
            val systemSpacing = 180f
            val systemTotalHeight = 4 * staffSpacing
            val fullSpacing = systemTotalHeight + systemSpacing

            val startSystem = (viewportTop / fullSpacing).toInt() * fullSpacing - fullSpacing
            val endSystem = (viewportBottom / fullSpacing).toInt() * fullSpacing + fullSpacing

            var startY = startSystem
            while (startY <= endSystem) {
                for (i in 0..4) {
                    val lineY = startY + i * staffSpacing
                    drawScope.drawLine(
                        color = patternColor,
                        start = Offset(viewportLeft, lineY),
                        end = Offset(viewportRight, lineY),
                        strokeWidth = 1f / scale
                    )
                }
                startY += fullSpacing
            }
        }
    }
}

private fun drawWhiteboardStroke(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    stroke: Stroke
) {
    if (stroke.points.isEmpty()) return

    val path = PathSmoother.createSmoothPath(stroke.points)

    drawScope.drawPath(
        path = path,
        color = Color(stroke.color),
        alpha = stroke.alpha,
        style = DrawStroke(
            width = stroke.width,
            cap = if (stroke.brushType == BrushType.HIGHLIGHTER) androidx.compose.ui.graphics.StrokeCap.Square else androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

// Extension to expose converter safely
fun NoteXViewModel.getConverter(): StrokesConverter {
    return StrokesConverter()
}
