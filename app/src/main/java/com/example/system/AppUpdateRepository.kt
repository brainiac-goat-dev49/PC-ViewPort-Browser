package com.example.system

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.browser.BrowserConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemVer> {

    val normalized: String
        get() = buildString {
            append(major).append('.').append(minor).append('.').append(patch)
            if (!preRelease.isNullOrBlank()) {
                append('-').append(preRelease)
            }
        }

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        val selfPre = preRelease?.takeIf { it.isNotBlank() }
        val otherPre = other.preRelease?.takeIf { it.isNotBlank() }
        if (selfPre == null && otherPre == null) return 0
        if (selfPre == null) return 1
        if (otherPre == null) return -1

        val aParts = selfPre.split('.')
        val bParts = otherPre.split('.')
        val count = maxOf(aParts.size, bParts.size)
        for (i in 0 until count) {
            val a = aParts.getOrNull(i) ?: return -1
            val b = bParts.getOrNull(i) ?: return 1
            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()
            val cmp = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1
                bNum != null -> 1
                else -> a.compareTo(b)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    companion object {
        private val SEMVER_EXACT_REGEX = Regex(
            "^\\s*[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?\\s*$"
        )
        private val SEMVER_EMBEDDED_REGEX = Regex(
            "[vV]?(\\d+\\.\\d+(?:\\.\\d+)?(?:-[0-9A-Za-z.-]+)?)"
        )

        fun parse(raw: String?): SemVer? {
            if (raw.isNullOrBlank()) return null
            val match = SEMVER_EXACT_REGEX.matchEntire(raw) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val pre = match.groupValues[4].takeIf { it.isNotBlank() }
            return SemVer(major = major, minor = minor, patch = patch, preRelease = pre)
        }

        fun extractFrom(raw: String?): SemVer? {
            if (raw.isNullOrBlank()) return null
            parse(raw)?.let { return it }
            val match = SEMVER_EMBEDDED_REGEX.find(raw) ?: return null
            return parse(match.groupValues[1])
        }

        fun compare(remoteVersion: String, localVersion: String): Int? {
            val remote = parse(remoteVersion) ?: return null
            val local = parse(localVersion) ?: return null
            return remote.compareTo(local)
        }

        fun isRemoteNewer(remoteVersion: String, localVersion: String): Boolean {
            return (compare(remoteVersion, localVersion) ?: 0) > 0
        }
    }
}

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkAsset: GitHubReleaseAsset?
)

interface AppUpdateChecker {
    suspend fun fetchLatestRelease(
        apiUrl: String = BrowserConfig.GITHUB_LATEST_RELEASE_API_URL
    ): Result<GitHubReleaseInfo>

    suspend fun downloadApkToCache(
        context: Context,
        asset: GitHubReleaseAsset,
        onProgress: (Int) -> Unit = {}
    ): Result<File>

    fun buildInstallIntent(context: Context, apkFile: File): Intent

    fun canInstallPackages(context: Context): Boolean
}

class GitHubAppUpdaterRepository(
    private val localVersionProvider: () -> String = { BuildConfig.VERSION_NAME }
) : AppUpdateChecker {

    override suspend fun fetchLatestRelease(apiUrl: String): Result<GitHubReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonText = httpGetText(apiUrl)
                parseReleaseJson(jsonText)
            }
        }

    override suspend fun downloadApkToCache(
        context: Context,
        asset: GitHubReleaseAsset,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            updatesDir.listFiles()?.forEach { stale ->
                runCatching { stale.delete() }
            }

            val safeName = asset.name
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
                .ifBlank { "pcviewport-update.apk" }

            val tmpFile = File(updatesDir, "$safeName.tmp")
            val targetFile = File(updatesDir, safeName)
            if (tmpFile.exists()) tmpFile.delete()
            if (targetFile.exists()) targetFile.delete()

            val connection = openConnectionFollowingRedirects(asset.downloadUrl)
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("Failed to download APK (HTTP $code)")
                }
                val expectedBytes = connection.contentLengthLong.takeIf { it > 0L }
                    ?: asset.sizeBytes.takeIf { it > 0L }
                    ?: -1L

                onProgress(0)
                connection.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (expectedBytes > 0L) {
                                val pct = ((downloaded * 100L) / expectedBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
                if (!tmpFile.exists() || tmpFile.length() == 0L) {
                    throw IOException("Downloaded APK file is empty")
                }
                if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
                onProgress(100)
                targetFile
            } finally {
                connection.disconnect()
            }
        }
    }

    override fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun httpGetText(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PCViewPort-Android/${localVersionProvider()}")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bodyText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val apiMsg = runCatching {
                    JSONObject(bodyText).optString("message")
                }.getOrNull().orEmpty()
                val detail = apiMsg.ifBlank { "HTTP $code" }
                throw IOException("GitHub release check failed: $detail")
            }
            bodyText
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnectionFollowingRedirects(
        startUrl: String,
        maxRedirects: Int = 6
    ): HttpURLConnection {
        var currentUrl = startUrl
        repeat(maxRedirects + 1) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "PCViewPort-Android/${localVersionProvider()}")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location header (HTTP $code)")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects downloading APK")
    }

    companion object {
        const val UPDATES_DIR_NAME = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun parseReleaseJson(json: String): GitHubReleaseInfo {
            val root = JSONObject(json)
            val tagName = root.optString("tag_name").trim()
            if (tagName.isEmpty()) {
                throw IllegalArgumentException("GitHub release response missing tag_name")
            }
            val releaseName = root.optString("name").trim().ifBlank { tagName }
            val body = root.optString("body").trim()
            val htmlUrl = root.optString("html_url").trim()

            val assetsJson = root.optJSONArray("assets")
            val apkCandidates = buildList {
                if (assetsJson != null) {
                    for (i in 0 until assetsJson.length()) {
                        val obj = assetsJson.optJSONObject(i) ?: continue
                        val assetName = obj.optString("name").trim()
                        val downloadUrl = obj.optString("browser_download_url").trim()
                        val contentType = obj.optString("content_type").trim()
                        val sizeBytes = obj.optLong("size", 0L)
                        if (downloadUrl.isNotEmpty() &&
                            (assetName.endsWith(".apk", ignoreCase = true) ||
                                contentType.equals(APK_MIME_TYPE, ignoreCase = true))
                        ) {
                            add(
                                GitHubReleaseAsset(
                                    name = assetName.ifBlank { "update.apk" },
                                    downloadUrl = downloadUrl,
                                    sizeBytes = sizeBytes,
                                    contentType = contentType.ifBlank { APK_MIME_TYPE }
                                )
                            )
                        }
                    }
                }
            }
            val preferredApk = apkCandidates.firstOrNull {
                it.name.contains("PCViewPort", ignoreCase = true)
            } ?: apkCandidates.firstOrNull()

            val resolvedVersion = resolveVersionName(
                tagName = tagName,
                releaseName = releaseName,
                apkAssetName = preferredApk?.name
            )

            return GitHubReleaseInfo(
                tagName = tagName,
                versionName = resolvedVersion,
                releaseName = releaseName,
                body = body,
                htmlUrl = htmlUrl,
                apkAsset = preferredApk
            )
        }

        fun resolveVersionName(
            tagName: String,
            releaseName: String,
            apkAssetName: String?
        ): String {
            SemVer.extractFrom(tagName)?.let { return it.normalized }
            SemVer.extractFrom(releaseName)?.let { return it.normalized }
            SemVer.extractFrom(apkAssetName)?.let { return it.normalized }
            return tagName.trim().removePrefix("v").removePrefix("V")
        }
    }
}
