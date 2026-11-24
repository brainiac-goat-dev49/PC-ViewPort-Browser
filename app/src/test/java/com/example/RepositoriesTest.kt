package com.example

import com.example.browser.BrowserConfig
import com.example.data.BrowserSettingsRepository
import com.example.data.HistoryDao
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.data.NoteClipDao
import com.example.data.NoteClipEntity
import com.example.data.NoteClipRepository
import com.example.data.PasswordDao
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import com.example.data.PersistedSessionState
import com.example.data.PersistedTabSnapshot
import com.example.data.SavedPwaDao
import com.example.data.SavedPwaEntity
import com.example.data.SavedPwaRepository
import com.example.data.VaultCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePasswordDao : PasswordDao {
    private val items = MutableStateFlow<List<PasswordEntity>>(emptyList())
    private var seq = 1L

    override fun observeAll(): Flow<List<PasswordEntity>> = items

    override suspend fun insert(entity: PasswordEntity): Long {
        val id = if (entity.id > 0L) entity.id else seq++
        val saved = entity.copy(id = id)
        items.value = listOf(saved) + items.value.filterNot { it.id == id }
        return id
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }
}

private class FakeVaultCrypto : VaultCrypto {
    override fun encrypt(plainText: String): String = "ENC(${plainText.reversed()})"
    override fun decrypt(cipherText: String): String = cipherText.removePrefix("ENC(").removeSuffix(")").reversed()
}

private class FakeNoteClipDao : NoteClipDao {
    private val items = MutableStateFlow<List<NoteClipEntity>>(emptyList())
    private var seq = 1L

    override fun observeAll(): Flow<List<NoteClipEntity>> = items

    override suspend fun insert(entity: NoteClipEntity): Long {
        val id = if (entity.id > 0L) entity.id else seq++
        val saved = entity.copy(id = id)
        items.value = listOf(saved) + items.value.filterNot { it.id == id }
        return id
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }
}

private class FakeSavedPwaDao : SavedPwaDao {
    private val items = MutableStateFlow<List<SavedPwaEntity>>(emptyList())
    private var seq = 1L

    override fun observeAll(): Flow<List<SavedPwaEntity>> = items

    override suspend fun insert(entity: SavedPwaEntity): Long {
        val id = if (entity.id > 0L) entity.id else seq++
        val saved = entity.copy(id = id)
        items.value = listOf(saved) + items.value.filterNot { it.id == id }
        return id
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }
}

private class FakeHistoryDao : HistoryDao {
    private val items = MutableStateFlow<List<HistoryEntity>>(emptyList())
    private var seq = 1L

    override fun observeAll(): Flow<List<HistoryEntity>> = items

    override suspend fun getByUrl(url: String): HistoryEntity? {
        return items.value.firstOrNull { it.url == url }
    }

    override suspend fun insertOrReplace(entity: HistoryEntity): Long {
        val id = if (entity.id > 0L) entity.id else seq++
        val saved = entity.copy(id = id)
        items.value = (listOf(saved) + items.value.filterNot { it.id == id || it.url == entity.url })
            .sortedByDescending { it.visitedAt }
        return id
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        items.value = emptyList()
    }
}

class RepositoriesTest {

    @Test
    fun `passwordRepository encrypts stored secrets and decrypts on observation`() = runTest {
        val dao = FakePasswordDao()
        val repo = PasswordRepository(dao, FakeVaultCrypto())

        repo.saveCredential(
            siteTitle = "",
            siteUrl = "https://github.com",
            username = "dev",
            plainPassword = "TopSecret#99"
        )

        val rawStored = dao.observeAll().first().single()
        assertNotEquals("TopSecret#99", rawStored.encryptedPassword)
        assertTrue(rawStored.encryptedPassword.startsWith("ENC("))

        val decrypted = repo.entries.first().single()
        assertEquals("https://github.com", decrypted.siteTitle)
        assertEquals("TopSecret#99", decrypted.password)

        repo.deleteCredential(decrypted.id)
        assertTrue(repo.entries.first().isEmpty())
    }

    @Test
    fun `noteClipRepository ignores blank content and saves trimmed notes`() = runTest {
        val repo = NoteClipRepository(FakeNoteClipDao())
        repo.saveClip(title = "Empty", content = "   ")
        assertTrue(repo.clips.first().isEmpty())

        repo.saveClip(title = "", content = "  Useful finding  ", sourceUrl = " https://example.com ")
        val clip = repo.clips.first().single()
        assertEquals("Web Clip", clip.title)
        assertEquals("Useful finding", clip.content)
        assertEquals("https://example.com", clip.sourceUrl)

        repo.deleteClip(clip.id)
        assertTrue(repo.clips.first().isEmpty())
    }

    @Test
    fun `savedPwaRepository stores and deletes pinned web apps`() = runTest {
        val repo = SavedPwaRepository(FakeSavedPwaDao())
        repo.recordPwa(name = "DevDocs", startUrl = "https://devdocs.io")
        val pwa = repo.pwas.first().single()
        assertEquals("DevDocs", pwa.name)
        assertEquals("https://devdocs.io", pwa.startUrl)

        repo.removePwa(pwa.id)
        assertTrue(repo.pwas.first().isEmpty())
    }

    @Test
    fun `historyRepository records visits, increments counts, updates titles, and ignores internal urls`() = runTest {
        val repo = HistoryRepository(FakeHistoryDao())

        repo.recordVisit(title = "Start Page", url = BrowserConfig.START_PAGE_URL)
        repo.recordVisit(title = "Blank", url = "about:blank")
        assertTrue(repo.history.first().isEmpty())

        repo.recordVisit(title = "", url = "https://github.com")
        val first = repo.history.first().single()
        assertEquals("github.com", first.title)
        assertEquals(1, first.visitCount)

        repo.updateTitleIfPresent(url = "https://github.com", title = "GitHub: Build software")
        assertEquals("GitHub: Build software", repo.history.first().single().title)

        repo.recordVisit(title = "", url = "https://github.com")
        val revisited = repo.history.first().single()
        assertEquals(2, revisited.visitCount)
        assertEquals("GitHub: Build software", revisited.title)

        repo.deleteEntry(revisited.id)
        assertTrue(repo.history.first().isEmpty())

        repo.recordVisit(title = "Kotlin", url = "https://kotlinlang.org")
        assertEquals(1, repo.history.first().size)
        repo.clearAll()
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun `session json serialization roundtrip preserves tabs and active index`() {
        val original = PersistedSessionState(
            tabs = listOf(
                PersistedTabSnapshot(
                    url = "https://github.com",
                    title = "GitHub",
                    isDesktopMode = true,
                    isLocked = true,
                    groupName = "Work",
                    groupColorHex = 0xFF34D399L
                ),
                PersistedTabSnapshot(
                    url = "https://developer.android.com",
                    title = "Android",
                    isDesktopMode = false
                )
            ),
            activeIndex = 1,
            savedAt = 123456789L
        )
        val encoded = BrowserSettingsRepository.encodeSessionJson(original)
        val decoded = BrowserSettingsRepository.decodeSessionJson(encoded)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.tabs.size)
        assertEquals(1, decoded.activeIndex)
        assertEquals("https://github.com", decoded.tabs[0].url)
        assertTrue(decoded.tabs[0].isLocked)
        assertEquals("Work", decoded.tabs[0].groupName)
        assertEquals(false, decoded.tabs[1].isDesktopMode)
    }
}
