package com.example.data

import kotlinx.coroutines.flow.Flow

interface DownloadStore {
    val downloads: Flow<List<DownloadEntity>>
    suspend fun recordDownload(
        fileName: String,
        url: String,
        mimeType: String,
        systemDownloadId: Long
    )
    suspend fun deleteDownload(id: Long)
    suspend fun clearAll()
}

class DownloadRepository(private val downloadDao: DownloadDao) : DownloadStore {
    override val downloads: Flow<List<DownloadEntity>> = downloadDao.observeAll()

    override suspend fun recordDownload(
        fileName: String,
        url: String,
        mimeType: String,
        systemDownloadId: Long
    ) {
        downloadDao.insert(
            DownloadEntity(
                fileName = fileName,
                url = url,
                mimeType = mimeType,
                systemDownloadId = systemDownloadId
            )
        )
    }

    override suspend fun deleteDownload(id: Long) {
        downloadDao.deleteById(id)
    }

    override suspend fun clearAll() {
        downloadDao.clearAll()
    }
}
