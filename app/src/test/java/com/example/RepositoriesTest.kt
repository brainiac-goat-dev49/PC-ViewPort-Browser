package com.example

import com.example.data.NoteClipDao
import com.example.data.NoteClipEntity
import com.example.data.NoteClipRepository
import com.example.data.PasswordDao
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
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
}
