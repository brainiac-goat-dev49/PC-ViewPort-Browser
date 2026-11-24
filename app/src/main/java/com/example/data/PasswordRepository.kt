package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DecryptedPasswordEntry(
    val id: Long,
    val siteTitle: String,
    val siteUrl: String,
    val username: String,
    val password: String,
    val updatedAt: Long
)

interface VaultCrypto {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

object AndroidKeystoreVaultCrypto : VaultCrypto {
    private const val KEY_ALIAS = "pc_viewport_vault_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "v1:"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    override fun decrypt(cipherText: String): String {
        if (!cipherText.startsWith(PREFIX)) return cipherText
        return try {
            val raw = Base64.decode(cipherText.removePrefix(PREFIX), Base64.NO_WRAP)
            val ivLength = raw[0].toInt() and 0xFF
            val iv = raw.copyOfRange(1, 1 + ivLength)
            val encrypted = raw.copyOfRange(1 + ivLength, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

interface PasswordStore {
    val entries: Flow<List<DecryptedPasswordEntry>>
    suspend fun saveCredential(
        siteTitle: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        existingId: Long = 0L
    )
    suspend fun deleteCredential(id: Long)
}

class PasswordRepository(
    private val dao: PasswordDao,
    private val crypto: VaultCrypto = AndroidKeystoreVaultCrypto
) : PasswordStore {

    override val entries: Flow<List<DecryptedPasswordEntry>> = dao.observeAll().map { list ->
        list.map { entity ->
            DecryptedPasswordEntry(
                id = entity.id,
                siteTitle = entity.siteTitle,
                siteUrl = entity.siteUrl,
                username = entity.username,
                password = crypto.decrypt(entity.encryptedPassword),
                updatedAt = entity.updatedAt
            )
        }
    }

    override suspend fun saveCredential(
        siteTitle: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        existingId: Long
    ) {
        val cleanUrl = siteUrl.trim()
        val cleanUser = username.trim()
        if (cleanUrl.isEmpty() || cleanUser.isEmpty() || plainPassword.isEmpty()) return
        dao.insert(
            PasswordEntity(
                id = existingId,
                siteTitle = siteTitle.trim().ifBlank { cleanUrl },
                siteUrl = cleanUrl,
                username = cleanUser,
                encryptedPassword = crypto.encrypt(plainPassword),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteCredential(id: Long) {
        dao.deleteById(id)
    }
}
