package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val siteTitle: String,
    val siteUrl: String,
    val username: String,
    val encryptedPassword: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface PasswordDao {
    @Query("SELECT * FROM saved_passwords ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PasswordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PasswordEntity): Long

    @Query("DELETE FROM saved_passwords WHERE id = :id")
    suspend fun deleteById(id: Long)
}
