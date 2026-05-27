package com.synthbyte.scanmate.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.DocumentPageCount
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.data.QrHistory
import com.synthbyte.scanmate.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentViewModel(private val dao: DocDao, private val context: Context) : ViewModel() {
    val allDocuments: Flow<List<Document>> = dao.getAllDocuments()
    val favoriteDocuments: Flow<List<Document>> = dao.getFavoriteDocuments()
    val pinnedDocuments: Flow<List<Document>> = dao.getPinnedDocuments()
    val recentDocuments: Flow<List<Document>> = dao.getRecentDocuments()
    val firstPages: Flow<List<Page>> = dao.getFirstPagesForDocuments()
    val pageCountsByDocument: Flow<List<DocumentPageCount>> = dao.getPageCountsByDocument()
    val pageCount: Flow<Int> = dao.getPageCountFlow()
    val pdfCount: Flow<Int> = dao.getPdfCountFlow()
    val qrHistory: Flow<List<QrHistory>> = dao.getQrHistory()

    fun createDocumentFromUris(
        uris: List<Uri>,
        defaultWorkspace: String = "Inbox",
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val copiedFiles = uris.mapNotNull { uri -> FileUtils.copyUriToImageFile(context, uri) }
            if (copiedFiles.isEmpty()) {
                withContext(Dispatchers.Main) { onError("No gallery images could be imported") }
                return@launch
            }

            val now = System.currentTimeMillis()
            val docId = dao.insertDocument(
                Document(
                    title = "Imported ${copiedFiles.size} page${if (copiedFiles.size == 1) "" else "s"}",
                    timestamp = now,
                    updatedAt = now,
                    type = "IMAGE",
                    workspace = defaultWorkspace.ifBlank { "Inbox" }
                )
            )
            copiedFiles.forEachIndexed { index, file ->
                dao.insertPage(Page(documentId = docId, imagePath = file.absolutePath, pageOrder = index))
            }
            withContext(Dispatchers.Main) { onCreated(docId) }
        }
    }

    fun createDocumentFromFiles(
        files: List<java.io.File>,
        title: String,
        type: String = "SCAN",
        defaultWorkspace: String = "Inbox",
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val validFiles = files.filter { it.exists() && it.length() > 0L }
            if (validFiles.isEmpty()) {
                withContext(Dispatchers.Main) { onError("Capture or import at least one valid page") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val docId = dao.insertDocument(
                Document(
                    title = title.trim().ifBlank { "Scanned ${validFiles.size} page${if (validFiles.size == 1) "" else "s"}" },
                    timestamp = now,
                    updatedAt = now,
                    type = type,
                    workspace = defaultWorkspace.ifBlank { "Inbox" }
                )
            )
            validFiles.forEachIndexed { index, file ->
                dao.insertPage(Page(documentId = docId, imagePath = file.absolutePath, pageOrder = index))
            }
            withContext(Dispatchers.Main) { onCreated(docId) }
        }
    }

    fun getDocumentWithPages(docId: Long): Flow<DocumentWithPages?> = dao.getDocumentWithPages(docId)

    fun getPage(pageId: Long): Flow<Page?> = dao.getPage(pageId)

    fun getPagesForDocument(docId: Long): Flow<List<Page>> = dao.getPagesForDocument(docId)

    suspend fun getPagesForDocumentsOnce(documentIds: List<Long>): List<Page> = withContext(Dispatchers.IO) {
        if (documentIds.isEmpty()) emptyList() else dao.getPagesForDocumentsOnce(documentIds)
    }

    fun toggleFavorite(documentId: Long, current: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { dao.setFavorite(documentId, !current) }
    }

    fun togglePinned(documentId: Long, current: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { dao.setPinned(documentId, !current) }
    }

    fun updateCategoryTags(documentId: Long, category: String, tags: String, workspace: String = "Inbox") {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateCategoryTags(documentId, category.ifBlank { "General" }, tags, workspace.ifBlank { "Inbox" })
        }
    }

    fun updateOcrText(documentId: Long, text: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.updateOcrText(documentId, text) }
    }

    fun insertQrHistory(value: String, type: String) {
        val safeValue = value.trim()
        if (safeValue.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { dao.insertQrHistory(QrHistory(value = safeValue, type = type)) }
    }

    fun clearQrHistory() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearQrHistory() }
    }

    fun deleteDocumentAndThen(id: Long, onDeleted: () -> Unit = {}) {
        deleteDocument(id, onDeleted)
    }

    fun updatePageOrder(pageId: Long, pageOrder: Int) {
        viewModelScope.launch(Dispatchers.IO) { dao.updatePageOrder(pageId, pageOrder) }
    }

    fun updatePageImage(pageId: Long, imagePath: String, onDone: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val safePath = imagePath.trim()
        if (safePath.isBlank()) {
            onError("Missing page image")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.updatePageImage(pageId, safePath)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun savePageOcrText(documentId: Long, pageOrder: Int, text: String, onDone: () -> Unit = {}) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateOcrText(documentId, "Page ${pageOrder + 1}:\n$cleanText")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun deletePage(documentId: Long, pageId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePageById(pageId)
            renumberPagesInternal(documentId)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun duplicatePage(documentId: Long, sourcePath: String, sourcePageId: Long, onDone: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val copied = FileUtils.duplicateImageFile(context, sourcePath)
            if (copied == null) {
                withContext(Dispatchers.Main) { onError("Could not duplicate page image") }
                return@launch
            }
            val pages = dao.getPagesForDocumentOnce(documentId).sortedBy { it.pageOrder }
            val insertIndex = pages.indexOfFirst { it.id == sourcePageId }.takeIf { it >= 0 }?.plus(1) ?: pages.size
            pages.forEachIndexed { index, existing ->
                val order = if (index >= insertIndex) index + 1 else index
                dao.updatePageOrder(existing.id, order)
            }
            dao.insertPage(Page(documentId = documentId, imagePath = copied.absolutePath, pageOrder = insertIndex))
            renumberPagesInternal(documentId)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun movePage(documentId: Long, pageId: Long, direction: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = dao.getPagesForDocumentOnce(documentId).sortedBy { it.pageOrder }.toMutableList()
            val index = pages.indexOfFirst { it.id == pageId }
            if (index >= 0) {
                val newIndex = (index + direction).coerceIn(0, pages.lastIndex)
                if (index != newIndex) {
                    val current = pages.removeAt(index)
                    pages.add(newIndex, current)
                    pages.forEachIndexed { order, existing -> dao.updatePageOrder(existing.id, order) }
                }
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun reorderPages(pages: List<Page>, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            pages.forEachIndexed { order, page -> dao.updatePageOrder(page.id, order) }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private suspend fun renumberPagesInternal(documentId: Long) {
        dao.getPagesForDocumentOnce(documentId).sortedBy { it.pageOrder }.forEachIndexed { index, page ->
            dao.updatePageOrder(page.id, index)
        }
    }

    fun renameDocument(id: Long, title: String) {
        val safeTitle = title.trim().ifBlank { "Untitled Scan" }
        viewModelScope.launch(Dispatchers.IO) { dao.renameDocument(id, safeTitle) }
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch(Dispatchers.IO) { dao.setFavorite(document.id, !document.isFavorite) }
    }

    fun togglePinned(document: Document) {
        viewModelScope.launch(Dispatchers.IO) { dao.setPinned(document.id, !document.isPinned) }
    }

    fun deleteDocument(id: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocumentById(id)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }

    fun setWorkspace(documentIds: List<Long>, workspace: String, onDone: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        val safeWorkspace = workspace.trim().ifBlank { "Inbox" }
        viewModelScope.launch(Dispatchers.IO) {
            dao.moveDocumentsToWorkspace(documentIds, safeWorkspace)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun setFavoriteBulk(documentIds: List<Long>, favorite: Boolean, onDone: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setFavoriteBulk(documentIds, favorite)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun setPinnedBulk(documentIds: List<Long>, pinned: Boolean, onDone: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setPinnedBulk(documentIds, pinned)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun deleteDocuments(documentIds: List<Long>, onDeleted: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocumentsByIds(documentIds)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }

    fun restoreDocument(document: Document, pages: List<Page>, onRestored: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertDocument(document.copy(updatedAt = System.currentTimeMillis()))
            pages.sortedBy { it.pageOrder }.forEach { page ->
                dao.insertPage(page)
            }
            withContext(Dispatchers.Main) { onRestored() }
        }
    }
}


class DocumentViewModelFactory(private val dao: DocDao, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentViewModel(dao, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
