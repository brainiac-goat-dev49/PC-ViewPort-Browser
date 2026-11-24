package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.browser.UrlResolver
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["visitedAt"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

interface HistoryStore {
    val history: Flow<List<HistoryEntity>>
    suspend fun recordVisit(title: String, url: String)
    suspend fun updateTitleIfPresent(url: String, title: String)
    suspend fun deleteEntry(id: Long)
    suspend fun clearAll()
}

class HistoryRepository(private val dao: HistoryDao) : HistoryStore {
    override val history: Flow<List<HistoryEntity>> = dao.observeAll()

    override suspend fun recordVisit(title: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || UrlResolver.isInternalPage(cleanUrl)) return

        val now = System.currentTimeMillis()
        val existing = dao.getByUrl(cleanUrl)
        val candidateTitle = title.trim()
        val resolvedTitle = when {
            candidateTitle.isNotEmpty() && candidateTitle != cleanUrl -> candidateTitle
            existing != null && existing.title.isNotBlank() && existing.title != cleanUrl -> existing.title
            else -> UrlResolver.extractDisplayHost(cleanUrl).ifBlank { cleanUrl }
        }

        if (existing != null) {
            dao.insertOrReplace(
                existing.copy(
                    title = resolvedTitle,
                    visitedAt = now,
                    visitCount = existing.visitCount + 1
                )
            )
        } else {
            dao.insertOrReplace(
                HistoryEntity(
                    title = resolvedTitle,
                    url = cleanUrl,
                    visitedAt = now,
                    visitCount = 1
                )
            )
        }
    }

    override suspend fun updateTitleIfPresent(url: String, title: String) {
        val cleanUrl = url.trim()
        val cleanTitle = title.trim()
        if (cleanUrl.isEmpty() || cleanTitle.isEmpty() || cleanTitle == cleanUrl) return
        if (UrlResolver.isInternalPage(cleanUrl)) return

        val existing = dao.getByUrl(cleanUrl) ?: return
        if (existing.title != cleanTitle) {
            dao.insertOrReplace(existing.copy(title = cleanTitle))
        }
    }

    override suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}
