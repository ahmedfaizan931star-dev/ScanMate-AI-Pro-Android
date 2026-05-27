package com.synthbyte.scanmate.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DocumentWithPages(
    @Embedded val document: Document,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val pages: List<Page>
)

data class DocumentPageCount(
    val documentId: Long,
    val pageCount: Int
)

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY isPinned DESC, updatedAt DESC, timestamp DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int = 8): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentDocumentsOnce(limit: Int = 3): List<Document>

    @Query("SELECT COUNT(*) FROM pages")
    fun getPageCountFlow(): Flow<Int>

    @Query("SELECT * FROM pages ORDER BY documentId ASC, pageOrder ASC")
    fun getAllPages(): Flow<List<Page>>

    @Query("""
        SELECT p.* FROM pages p
        INNER JOIN (
            SELECT documentId, MIN(pageOrder) AS minPageOrder
            FROM pages
            GROUP BY documentId
        ) first_pages
        ON p.documentId = first_pages.documentId
        AND p.pageOrder = first_pages.minPageOrder
        ORDER BY p.documentId ASC
    """)
    fun getFirstPagesForDocuments(): Flow<List<Page>>

    @Query("SELECT documentId, COUNT(*) AS pageCount FROM pages GROUP BY documentId")
    fun getPageCountsByDocument(): Flow<List<DocumentPageCount>>

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :docId")
    suspend fun getPageCountForDocument(docId: Long): Int

    @Query("SELECT COUNT(*) FROM documents WHERE type = 'PDF'")
    fun getPdfCountFlow(): Flow<Int>

    @Query("""
        SELECT * FROM documents
        WHERE title LIKE '%' || :query || '%'
           OR IFNULL(ocrText, '') LIKE '%' || :query || '%'
           OR IFNULL(category, '') LIKE '%' || :query || '%'
           OR IFNULL(tags, '') LIKE '%' || :query || '%'
           OR IFNULL(workspace, '') LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC, timestamp DESC
    """)
    fun searchDocuments(query: String): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocument(id: Long): Flow<Document?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentWithPages(id: Long): Flow<DocumentWithPages?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameDocument(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET ocrText = :ocrText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOcrText(id: Long, ocrText: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET category = :category, tags = :tags, workspace = :workspace, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCategoryTags(id: Long, category: String, tags: String, workspace: String = "Inbox", updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE documents SET workspace = :workspace, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun moveDocumentsToWorkspace(ids: List<Long>, workspace: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setFavoriteBulk(ids: List<Long>, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setPinnedBulk(ids: List<Long>, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteDocumentsByIds(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Update
    suspend fun updatePage(page: Page)

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    fun getPage(id: Long): Flow<Page?>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPageOnce(id: Long): Page?

    @Query("UPDATE pages SET imagePath = :imagePath WHERE id = :id")
    suspend fun updatePageImage(id: Long, imagePath: String)

    @Query("UPDATE pages SET pageOrder = :pageOrder WHERE id = :id")
    suspend fun updatePageOrder(id: Long, pageOrder: Int)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: Long)

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageOrder ASC")
    fun getPagesForDocument(docId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageOrder ASC")
    suspend fun getPagesForDocumentOnce(docId: Long): List<Page>

    @Query("SELECT * FROM pages WHERE documentId IN (:documentIds) ORDER BY documentId ASC, pageOrder ASC")
    suspend fun getPagesForDocumentsOnce(documentIds: List<Long>): List<Page>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrHistory(item: QrHistory): Long

    @Query("SELECT * FROM qr_history ORDER BY timestamp DESC LIMIT :limit")
    fun getQrHistory(limit: Int = 25): Flow<List<QrHistory>>

    @Query("DELETE FROM qr_history")
    suspend fun clearQrHistory()
}
