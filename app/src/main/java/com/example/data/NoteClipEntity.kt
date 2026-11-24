package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "note_clips")
data class NoteClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    val sourceUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteClipDao {
    @Query("SELECT * FROM note_clips ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteClipEntity): Long

    @Query("DELETE FROM note_clips WHERE id = :id")
    suspend fun deleteById(id: Long)
}

interface NoteClipStore {
    val clips: Flow<List<NoteClipEntity>>
    suspend fun saveClip(title: String, content: String, sourceUrl: String = "", existingId: Long = 0L)
    suspend fun deleteClip(id: Long)
}

class NoteClipRepository(private val dao: NoteClipDao) : NoteClipStore {
    override val clips: Flow<List<NoteClipEntity>> = dao.observeAll()

    override suspend fun saveClip(
        title: String,
        content: String,
        sourceUrl: String,
        existingId: Long
    ) {
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) return
        dao.insert(
            NoteClipEntity(
                id = existingId,
                title = title.trim().ifBlank { "Web Clip" },
                content = cleanContent,
                sourceUrl = sourceUrl.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteClip(id: Long) {
        dao.deleteById(id)
    }
}
