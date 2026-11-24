package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_pwas")
data class SavedPwaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val startUrl: String,
    val installedAt: Long = System.currentTimeMillis()
)

@Dao
interface SavedPwaDao {
    @Query("SELECT * FROM saved_pwas ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<SavedPwaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedPwaEntity): Long

    @Query("DELETE FROM saved_pwas WHERE id = :id")
    suspend fun deleteById(id: Long)
}

interface PwaStore {
    val pwas: Flow<List<SavedPwaEntity>>
    suspend fun recordPwa(name: String, startUrl: String)
    suspend fun removePwa(id: Long)
}

class SavedPwaRepository(private val dao: SavedPwaDao) : PwaStore {
    override val pwas: Flow<List<SavedPwaEntity>> = dao.observeAll()

    override suspend fun recordPwa(name: String, startUrl: String) {
        val cleanUrl = startUrl.trim()
        if (cleanUrl.isEmpty()) return
        dao.insert(
            SavedPwaEntity(
                name = name.trim().ifBlank { cleanUrl },
                startUrl = cleanUrl,
                installedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removePwa(id: Long) {
        dao.deleteById(id)
    }
}
