package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ActiveTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
    HAND,
    SHAPE
}

enum class EraserType {
    PIXEL,
    STROKE
}

data class EraserState(
    val type: EraserType = EraserType.STROKE,
    val size: Float = 30f
)

data class PenState(
    val color: Int = 0xFFFFFFFF.toInt(), // White by default for black canvas!
    val size: Float = 6f,
    val alpha: Float = 1.0f,
    val brush: BrushType = BrushType.PEN,
    val shapeType: ShapeType = ShapeType.NONE
)

class NoteXViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NoteXDatabase.getDatabase(application)
    private val repository = NoteXRepository(db.dao())
    private val strokesConverter = StrokesConverter()

    // Exposed lists of notebooks
    val notebooks: StateFlow<List<NotebookEntity>> = repository.allNotebooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Workspace State
    val activeNotebook = MutableStateFlow<NotebookEntity?>(null)
    val activePages = MutableStateFlow<List<PageEntity>>(emptyList())
    val currentPageIndex = MutableStateFlow(0)

    // Current page drawing states
    val activePageStrokes = MutableStateFlow<List<Stroke>>(emptyList())
    
    // Undo/Redo Stacks
    private val undoStack = mutableListOf<List<Stroke>>()
    private val redoStack = mutableListOf<List<Stroke>>()

    // Toolbar Configurations
    val activeTool = MutableStateFlow(ActiveTool.PEN)
    val penState = MutableStateFlow(PenState())
    val eraserState = MutableStateFlow(EraserState())

    // Workspace Toast or Information Message
    val infoMessage = MutableSharedFlow<String>(replay = 0)

    // Autosave job
    private var autoSaveJob: Job? = null

    init {
        // Start the 10-second lightweight, lifecycle-aware autosave thread
        startAutosaveTimer()
    }

    private fun startAutosaveTimer() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10000)
                saveCurrentPageStrokes()
            }
        }
    }

    // Load notebook into the workspace
    fun selectNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            // Flush any current changes before switching!
            saveCurrentPageStrokes()

            activeNotebook.value = notebook
            
            // Get pages reactively
            repository.getPagesForNotebook(notebook.id).collectLatest { pagesList ->
                activePages.value = pagesList
                if (pagesList.isNotEmpty()) {
                    // Reset if index is out of bounds
                    if (currentPageIndex.value >= pagesList.size) {
                        currentPageIndex.value = pagesList.size - 1
                    }
                    val currentPage = pagesList[currentPageIndex.value]
                    activePageStrokes.value = currentPage.getStrokes(strokesConverter)
                    
                    // Clear undo/redo stacks on page transition
                    undoStack.clear()
                    redoStack.clear()
                } else {
                    activePageStrokes.value = emptyList()
                    undoStack.clear()
                    redoStack.clear()
                }
            }
        }
    }

    // Close notebook and return to dashboard
    fun closeNotebook() {
        viewModelScope.launch {
            saveCurrentPageStrokes()
            activeNotebook.value = null
            activePages.value = emptyList()
            currentPageIndex.value = 0
            activePageStrokes.value = emptyList()
            undoStack.clear()
            redoStack.clear()
        }
    }

    // Save current strokes to local SQLite Database
    suspend fun saveCurrentPageStrokes() {
        val notebook = activeNotebook.value ?: return
        val pages = activePages.value
        val index = currentPageIndex.value
        if (index in pages.indices) {
            val currentPage = pages[index]
            val strokesList = activePageStrokes.value
            val json = strokesConverter.toJson(strokesList)
            
            if (currentPage.strokesJson != json) {
                val updatedPage = currentPage.copy(strokesJson = json)
                repository.updatePage(updatedPage)
            }
        }
    }

    // Create a new notebook with the max 5 restriction
    fun createNotebook(title: String) {
        viewModelScope.launch {
            try {
                val notebookId = repository.createNotebook(title)
                infoMessage.emit("Notebook created successfully!")
                
                // Auto-select the newly created notebook
                val notebooksList = repository.allNotebooks.first()
                val created = notebooksList.find { it.id == notebookId.toInt() }
                if (created != null) {
                    selectNotebook(created)
                }
            } catch (e: Exception) {
                infoMessage.emit(e.message ?: "Failed to create notebook")
            }
        }
    }

    // Delete a notebook
    fun deleteNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            if (activeNotebook.value?.id == notebook.id) {
                closeNotebook()
            }
            repository.deleteNotebook(notebook)
            infoMessage.emit("Notebook deleted.")
        }
    }

    // Add a new page to current active notebook
    fun addNewPage() {
        val notebook = activeNotebook.value ?: return
        viewModelScope.launch {
            saveCurrentPageStrokes()
            val newIndex = activePages.value.size
            repository.addPage(notebook.id, newIndex, "BLACK_CANVAS")
            currentPageIndex.value = newIndex
            infoMessage.emit("Page ${newIndex + 1} added.")
        }
    }

    // Duplicate current page
    fun duplicateCurrentPage() {
        val notebook = activeNotebook.value ?: return
        val index = currentPageIndex.value
        val pages = activePages.value
        if (index in pages.indices) {
            viewModelScope.launch {
                saveCurrentPageStrokes()
                val currentPage = pages[index]
                val newIndex = pages.size
                
                val duplicatedPage = PageEntity(
                    notebookId = notebook.id,
                    pageIndex = newIndex,
                    backgroundType = currentPage.backgroundType,
                    backgroundColor = currentPage.backgroundColor,
                    backgroundOpacity = currentPage.backgroundOpacity,
                    strokesJson = currentPage.strokesJson
                )
                db.dao().insertPage(duplicatedPage)
                currentPageIndex.value = newIndex
                infoMessage.emit("Page duplicated.")
            }
        }
    }

    // Delete current page
    fun deleteCurrentPage() {
        val index = currentPageIndex.value
        val pages = activePages.value
        if (pages.size <= 1) {
            viewModelScope.launch {
                infoMessage.emit("A notebook must have at least one page.")
            }
            return
        }
        if (index in pages.indices) {
            viewModelScope.launch {
                val pageToDelete = pages[index]
                repository.deletePage(pageToDelete)
                
                // Adjust index safely
                if (index >= pages.size - 1) {
                    currentPageIndex.value = pages.size - 2
                } else {
                    currentPageIndex.value = index
                }
                infoMessage.emit("Page deleted.")
            }
        }
    }

    // Navigate to a specific page
    fun navigateToPage(index: Int) {
        val pages = activePages.value
        if (index in pages.indices && index != currentPageIndex.value) {
            viewModelScope.launch {
                saveCurrentPageStrokes()
                currentPageIndex.value = index
                val page = pages[index]
                activePageStrokes.value = page.getStrokes(strokesConverter)
                undoStack.clear()
                redoStack.clear()
            }
        }
    }

    // Update background style
    fun setPageBackground(backgroundType: String, backgroundColor: Int, opacity: Float) {
        val pages = activePages.value
        val index = currentPageIndex.value
        if (index in pages.indices) {
            viewModelScope.launch {
                val currentPage = pages[index]
                val updatedPage = currentPage.copy(
                    backgroundType = backgroundType,
                    backgroundColor = backgroundColor,
                    backgroundOpacity = opacity
                )
                repository.updatePage(updatedPage)
                
                // Update local list manually to prevent screen flicker during flow sync
                activePages.value = activePages.value.toMutableList().apply {
                    set(index, updatedPage)
                }
            }
        }
    }

    // Drawing manipulation
    fun addStroke(stroke: Stroke) {
        // Record undo state
        saveToUndoStack()
        
        val newList = activePageStrokes.value.toMutableList()
        newList.add(stroke)
        activePageStrokes.value = newList
        
        // Memory optimization: Trim list if active strokes exceed safety limit
        if (newList.size > 12000) {
            newList.removeAt(0)
            activePageStrokes.value = newList
        }
    }

    // Unlimited Undo
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = activePageStrokes.value
            redoStack.add(currentState)
            
            val previousState = undoStack.removeAt(undoStack.size - 1)
            activePageStrokes.value = previousState
        }
    }

    // Unlimited Redo
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = activePageStrokes.value
            undoStack.add(currentState)
            
            val nextState = redoStack.removeAt(redoStack.size - 1)
            activePageStrokes.value = nextState
        }
    }

    private fun saveToUndoStack() {
        undoStack.add(activePageStrokes.value)
        redoStack.clear() // Drawing new lines invalidates the redo stack
        if (undoStack.size > 200) { // Safety ceiling to prevent out-of-memory
            undoStack.removeAt(0)
        }
    }

    // Clear active page drawing
    fun clearCurrentPage() {
        if (activePageStrokes.value.isNotEmpty()) {
            saveToUndoStack()
            activePageStrokes.value = emptyList()
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        // Final flush save
        runBlocking {
            saveCurrentPageStrokes()
        }
        super.onCleared()
    }
}
