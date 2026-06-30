package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.NotebookEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: NoteXViewModel,
    onNotebookSelected: (NotebookEntity) -> Unit
) {
    val notebooks by viewModel.notebooks.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var notebookTitleInput by remember { mutableStateOf("") }
    
    var notebookToDelete by remember { mutableStateOf<NotebookEntity?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        floatingActionButton = {
            if (notebooks.size < 5) {
                FloatingActionButton(
                    onClick = {
                        notebookTitleInput = ""
                        showCreateDialog = true
                    },
                    containerColor = Color(0xFFA8C7FA),
                    contentColor = Color(0xFF00223B),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("create_notebook_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Create Notebook")
                        Spacer(Modifier.width(8.dp))
                        Text("New Notebook", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val dotColor = Color.White.copy(alpha = 0.07f)
                    val spacing = 24.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        var y = 0f
                        while (y < size.height) {
                            drawCircle(dotColor, radius = 1.dp.toPx(), center = Offset(x, y))
                            y += spacing
                        }
                        x += spacing
                    }
                }
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(30.dp))

            // Brand Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NOTE X",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFA8C7FA).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFFA8C7FA).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "PRO",
                                color = Color(0xFFA8C7FA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "High-performance vector infinite whiteboard",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Storage quota meter
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${notebooks.size}/5 notebooks",
                            color = if (notebooks.size >= 5) Color(0xFFEF5350) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Local offline storage",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            if (notebooks.isEmpty()) {
                // Empty state illustration
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 60.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(Color.White.copy(alpha = 0.04f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Book,
                                null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Your drawing canvas is blank",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create a notebook to start sketching layouts, taking notes, or designing with up to 120Hz smooth ink latency.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 320.dp),
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                notebookTitleInput = ""
                                showCreateDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create your first notebook", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // Notebook cards list
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(notebooks, key = { it.id }) { book ->
                        NotebookCard(
                            notebook = book,
                            dateFormat = dateFormat,
                            onOpen = { onNotebookSelected(book) },
                            onDelete = { notebookToDelete = book }
                        )
                    }
                }
            }
        }
    }

    // --- 1. NEW NOTEBOOK DIALOG ---
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    "New Notebook",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Give your notebook a title. This will be stored locally on your device.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = notebookTitleInput,
                        onValueChange = { notebookTitleInput = it },
                        placeholder = { Text("Title (e.g. Physics Formulas)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFA8C7FA),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF1E1E1E)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notebook_title_field")
                    )
                }
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        val title = notebookTitleInput.trim().ifEmpty { "Untitled Notebook" }
                        viewModel.createNotebook(title)
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF004A77),
                        contentColor = Color(0xFFD2E3FC)
                    )
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    // --- 2. DELETE NOTEBOOK CONFIRMATION DIALOG ---
    if (notebookToDelete != null) {
        AlertDialog(
            onDismissRequest = { notebookToDelete = null },
            title = {
                Text(
                    "Delete Notebook?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete '${notebookToDelete?.title}'? This action is irreversible and all your whiteboard drawings inside will be deleted.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            containerColor = Color(0xFF1C1C1C),
            confirmButton = {
                Button(
                    onClick = {
                        notebookToDelete?.let { book ->
                            viewModel.deleteNotebook(book)
                        }
                        notebookToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { notebookToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun NotebookCard(
    notebook: NotebookEntity,
    dateFormat: SimpleDateFormat,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1C))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Book Icon & Notebook details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.03f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Book,
                            null,
                            tint = Color(0xFFA8C7FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = notebook.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                        Text(
                            text = "Modified ${dateFormat.format(Date(notebook.modified))}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.04f), CircleShape)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        "Delete notebook",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Action section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudQueue,
                        "Local Mode",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Offline Active", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF004A77),
                        contentColor = Color(0xFFD2E3FC)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Open Canvas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
