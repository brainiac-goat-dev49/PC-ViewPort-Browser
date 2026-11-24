package com.example.data

import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) return false
        val existing = bookmarkDao.getByUrl(normalizedUrl)
        return if (existing != null) {
            bookmarkDao.deleteById(existing.id)
            false
        } else {
            val cleanTitle = title.trim().ifEmpty { normalizedUrl }
            bookmarkDao.insertOrReplace(
                BookmarkEntity(
                    title = cleanTitle,
                    url = normalizedUrl
                )
            )
            true
        }
    }

    suspend fun saveBookmark(title: String, url: String, existingId: Long = 0L) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) return
        val cleanTitle = title.trim().ifEmpty { normalizedUrl }
        if (existingId > 0L) {
            bookmarkDao.update(
                BookmarkEntity(
                    id = existingId,
                    title = cleanTitle,
                    url = normalizedUrl
                )
            )
        } else {
            bookmarkDao.insertOrReplace(
                BookmarkEntity(
                    title = cleanTitle,
                    url = normalizedUrl
                )
            )
        }
    }

    suspend fun deleteBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }
}
