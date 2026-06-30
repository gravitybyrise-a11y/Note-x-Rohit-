package com.example.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NoteXRepository(private val dao: NoteXDao) {

    val allNotebooks: Flow<List<NotebookEntity>> = dao.getAllNotebooks()

    suspend fun createNotebook(title: String): Long {
        val currentNotebooks = allNotebooks.first()
        if (currentNotebooks.size >= 5) {
            throw IllegalStateException("Maximum limit of 5 notebooks reached.")
        }
        val notebookId = dao.insertNotebook(NotebookEntity(title = title))
        
        // Always create a default first page when a notebook is created!
        dao.insertPage(
            PageEntity(
                notebookId = notebookId.toInt(),
                pageIndex = 0,
                backgroundType = "BLACK_CANVAS"
            )
        )
        return notebookId
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        dao.updateNotebook(notebook.copy(modified = System.currentTimeMillis()))
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) {
        dao.deleteNotebook(notebook)
    }

    fun getPagesForNotebook(notebookId: Int): Flow<List<PageEntity>> {
        return dao.getPagesForNotebook(notebookId)
    }

    suspend fun getPagesForNotebookSync(notebookId: Int): List<PageEntity> {
        return dao.getPagesForNotebookSync(notebookId)
    }

    suspend fun addPage(notebookId: Int, index: Int, backgroundType: String = "BLACK_CANVAS"): Long {
        val page = PageEntity(
            notebookId = notebookId,
            pageIndex = index,
            backgroundType = backgroundType
        )
        return dao.insertPage(page)
    }

    suspend fun updatePage(page: PageEntity) {
        dao.updatePage(page)
        // Also update notebook modification timestamp!
        val notebook = dao.getNotebookById(page.notebookId)
        if (notebook != null) {
            dao.updateNotebook(notebook.copy(modified = System.currentTimeMillis()))
        }
    }

    suspend fun deletePage(page: PageEntity) {
        dao.deletePage(page)
        
        // Re-index remaining pages
        val remaining = dao.getPagesForNotebookSync(page.notebookId)
        remaining.forEachIndexed { idx, p ->
            if (p.pageIndex != idx) {
                dao.updatePage(p.copy(pageIndex = idx))
            }
        }
    }
}
