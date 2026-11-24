package com.example

import com.example.data.BookmarkDao
import com.example.data.BookmarkEntity
import com.example.data.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBookmarkDao : BookmarkDao {
    private val items = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<BookmarkEntity>> =
        items.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun getByUrl(url: String): BookmarkEntity? =
        items.value.firstOrNull { it.url == url }

    override suspend fun insertOrReplace(bookmark: BookmarkEntity): Long {
        val withoutSameUrl = items.value.filterNot { it.url == bookmark.url }
        val id = if (bookmark.id > 0L) bookmark.id else nextId++
        val saved = bookmark.copy(id = id)
        items.value = withoutSameUrl + saved
        return id
    }

    override suspend fun update(bookmark: BookmarkEntity) {
        items.value = items.value.map { if (it.id == bookmark.id) bookmark else it }
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun deleteByUrl(url: String) {
        items.value = items.value.filterNot { it.url == url }
    }
}

class BookmarkRepositoryTest {

    @Test
    fun `toggleBookmark adds and removes bookmark by url`() = runTest {
        val repo = BookmarkRepository(FakeBookmarkDao())

        val added = repo.toggleBookmark("Kotlin", "https://kotlinlang.org")
        assertTrue(added)
        assertEquals(1, repo.bookmarks.first().size)

        val removed = repo.toggleBookmark("Kotlin", "https://kotlinlang.org")
        assertFalse(removed)
        assertTrue(repo.bookmarks.first().isEmpty())
    }

    @Test
    fun `saveBookmark updates existing bookmark and deleteBookmark removes it`() = runTest {
        val repo = BookmarkRepository(FakeBookmarkDao())
        repo.saveBookmark("GitHub", "https://github.com")
        val initial = repo.bookmarks.first().single()

        repo.saveBookmark("GitHub Docs", "https://docs.github.com", existingId = initial.id)
        val updated = repo.bookmarks.first().single()
        assertEquals(initial.id, updated.id)
        assertEquals("GitHub Docs", updated.title)
        assertEquals("https://docs.github.com", updated.url)

        repo.deleteBookmark(updated.id)
        assertTrue(repo.bookmarks.first().isEmpty())
    }
}
