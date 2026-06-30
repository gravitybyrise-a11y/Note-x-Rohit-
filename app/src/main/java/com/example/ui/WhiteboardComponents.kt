package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.BrushType
import com.example.storage.PageEntity
import com.example.storage.NotebookEntity

@Composable
fun GlassToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(24.dp, shape = RoundedCornerShape(32.dp), clip = false)
            .background(
                color = Color(0xFF1C1C1C).copy(alpha = 0.85f),
                shape = RoundedCornerShape(32.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(32.dp, shape = RoundedCornerShape(24.dp), clip = false)
            .background(
                color = Color(0xFF1C1C1C).copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenPopup(
    visible: Boolean,
    state: PenState,
    onStateChanged: (PenState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val colors = listOf(
        0xFFFFFFFF, 0xFFF44336, 0xFF4CAF50, 0xFF2196F3, 
        0xFFFFEB3B, 0xFFFF9800, 0xFFE91E63, 0xFF9C27B0, 
        0xFF00BCD4, 0xFF8BC34A, 0xFF795548, 0xFF9E9E9E
    ).map { Color(it.toInt()) }

    GlassPanel(
        modifier = modifier.width(320.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Pen settings",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = Color.LightGray)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Size slider
        Text("Brush Size: ${state.size.toInt()}px", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Slider(
            value = state.size,
            onValueChange = { onStateChanged(state.copy(size = it)) },
            valueRange = 1f..40f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFA8C7FA),
                activeTrackColor = Color(0xFFA8C7FA),
                inactiveTrackColor = Color(0xFFA8C7FA).copy(alpha = 0.2f)
            )
        )

        // Opacity slider
        Text("Opacity: ${(state.alpha * 100).toInt()}%", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Slider(
            value = state.alpha,
            onValueChange = { onStateChanged(state.copy(alpha = it)) },
            valueRange = 0.1f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFA8C7FA),
                activeTrackColor = Color(0xFFA8C7FA),
                inactiveTrackColor = Color(0xFFA8C7FA).copy(alpha = 0.2f)
            )
        )

        Spacer(Modifier.height(8.dp))

        // Brush select
        Text("Brush Type", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(BrushType.values()) { type ->
                val selected = state.brush == type
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) Color(0xFF004A77).copy(alpha = 0.8f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onStateChanged(state.copy(brush = type)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = type.name,
                        color = if (selected) Color(0xFFD2E3FC) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Color Palette
        Text("Colors", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 6
        ) {
            colors.forEach { color ->
                val isSelected = state.color == color.hashCode()
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { onStateChanged(state.copy(color = color.hashCode())) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Live preview of stroke
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Live Brush Preview render line
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                val center = this.center
                drawCircle(
                    color = Color(state.color).copy(alpha = state.alpha),
                    radius = state.size,
                    center = center
                )
            }
            Text("Ink Preview", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
    }
}

@Composable
fun EraserPopup(
    visible: Boolean,
    state: EraserState,
    onStateChanged: (EraserState) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    GlassPanel(
        modifier = modifier.width(260.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Eraser settings",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = Color.LightGray)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Eraser Types
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                EraserType.STROKE to "Stroke Erase",
                EraserType.PIXEL to "Pixel Erase"
            ).forEach { (type, label) ->
                val selected = state.type == type
                Button(
                    onClick = { onStateChanged(state.copy(type = type)) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFF004A77) else Color.White.copy(alpha = 0.08f),
                        contentColor = if (selected) Color(0xFFD2E3FC) else Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = if (selected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Size slider
        Text("Eraser Width: ${state.size.toInt()}px", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Slider(
            value = state.size,
            onValueChange = { onStateChanged(state.copy(size = it)) },
            valueRange = 10f..120f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFA8C7FA),
                activeTrackColor = Color(0xFFA8C7FA),
                inactiveTrackColor = Color(0xFFA8C7FA).copy(alpha = 0.2f)
            )
        )

        Spacer(Modifier.height(10.dp))

        // Clear button
        Button(
            onClick = onClearAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC62828),
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear Canvas Page", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundSelector(
    visible: Boolean,
    currentType: String,
    currentBgColor: Int,
    onChanged: (type: String, color: Int, opacity: Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val bgColors = listOf(
        0xFF000000, 0xFF121212, 0xFF1E272C, 0xFF1A1C1E,
        0xFFFFFFFF, 0xFFF5F5F7, 0xFFEDF2F4, 0xFFFAF3DD
    ).map { Color(it.toInt()) }

    val patterns = listOf(
        "BLACK_CANVAS" to "Black Canvas",
        "BLANK" to "Plain Paper",
        "GRID" to "Math Grid",
        "RULED" to "Ruled / Lines",
        "DOTTED" to "Dotted Grid",
        "GRAPH" to "Engineering Graph",
        "MUSIC_SHEET" to "Music Sheet"
    )

    GlassPanel(
        modifier = modifier.width(310.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Background & Canvas",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = Color.LightGray)
            }
        }

        Spacer(Modifier.height(14.dp))

        Text("Select Pattern", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .height(150.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                patterns.forEach { (type, label) ->
                    val selected = currentType == type
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onChanged(type, currentBgColor, 1.0f) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (selected) Color.White else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = if (selected) Color.White else Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text("Canvas Theme Color", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4
        ) {
            bgColors.forEach { color ->
                val isSelected = currentBgColor == color.hashCode()
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(36.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .clickable {
                            // If user chooses White / light color, ensure we also switch from default "BLACK_CANVAS" to "BLANK" for contrast!
                            val nextType = if (color == Color.White && currentType == "BLACK_CANVAS") "BLANK" else currentType
                            onChanged(nextType, color.hashCode(), 1.0f)
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PageThumbnailsSidebar(
    visible: Boolean,
    pages: List<PageEntity>,
    currentIndex: Int,
    onPageSelected: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(250)) { it } + fadeIn(),
        exit = slideOutHorizontally(animationSpec = tween(250)) { it } + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.08f))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pages Manager",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", tint = Color.LightGray)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Actions Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onAddPage,
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, "Add Page", tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("New", color = Color.White, fontSize = 9.sp)
                        }
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CopyAll, "Duplicate", tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Clone", color = Color.White, fontSize = 9.sp)
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFC62828).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            Text("Delete", color = Color(0xFFEF5350), fontSize = 9.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Divider(color = Color.White.copy(alpha = 0.1f))

                Spacer(Modifier.height(10.dp))

                // Thumbnails scroll
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(pages) { index, page ->
                        val isSelected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(page.backgroundColor))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF2196F3) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onPageSelected(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            // Render page icon thumbnail overlay
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Description,
                                    null,
                                    tint = if (page.backgroundColor == Color.White.hashCode()) Color.DarkGray else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Page ${index + 1}",
                                    color = if (page.backgroundColor == Color.White.hashCode()) Color.DarkGray else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
