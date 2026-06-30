package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.canvas.CanvasScreen
import com.example.ui.DashboardScreen
import com.example.ui.NoteXViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private val viewModel: NoteXViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val activeNotebook by viewModel.activeNotebook.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()

                // Temporary holders for bytes during SAF system picker transitions
                var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
                var isPdfExport by remember { mutableStateOf(true) }

                // 1. SAF PDF Document Creator Launcher
                val createPdfLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    val bytes = pendingBytes
                    if (uri != null && bytes != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                                outputStream?.use { stream ->
                                    stream.write(bytes)
                                    stream.flush()
                                }
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "PDF Notebook exported successfully!", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Failed to write PDF file: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                pendingBytes = null
                            }
                        }
                    } else {
                        pendingBytes = null
                    }
                }

                // 2. SAF PNG Document Creator Launcher
                val createPngLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("image/png")
                ) { uri ->
                    val bytes = pendingBytes
                    if (uri != null && bytes != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                                outputStream?.use { stream ->
                                    stream.write(bytes)
                                    stream.flush()
                                }
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Whiteboard page exported as PNG successfully!", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Failed to write PNG file: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                pendingBytes = null
                            }
                        }
                    } else {
                        pendingBytes = null
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (activeNotebook == null) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNotebookSelected = { book ->
                                    viewModel.selectNotebook(book)
                                }
                            )
                        } else {
                            CanvasScreen(
                                viewModel = viewModel,
                                onBackToDashboard = {
                                    viewModel.closeNotebook()
                                },
                                onSaveDocument = { suggestedName, bytes, isPdf ->
                                    pendingBytes = bytes
                                    isPdfExport = isPdf
                                    if (isPdf) {
                                        createPdfLauncher.launch(suggestedName)
                                    } else {
                                        createPngLauncher.launch(suggestedName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Simple Box container import for convenience
@Composable
fun Box(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = modifier, content = content)
}

// BoxScope interface exposure
typealias BoxScope = androidx.compose.foundation.layout.BoxScope
