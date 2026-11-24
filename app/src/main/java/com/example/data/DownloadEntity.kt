package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val systemDownloadId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
