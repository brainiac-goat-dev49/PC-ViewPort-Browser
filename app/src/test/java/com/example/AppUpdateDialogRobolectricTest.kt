package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.system.GitHubAppUpdaterRepository
import com.example.system.GitHubReleaseAsset
import com.example.system.GitHubReleaseInfo
import com.example.system.SemVer
import com.example.ui.AppUpdateUiState
import com.example.ui.components.AppUpdateDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateDialogRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun parseGitHubLatestReleaseJsonAndBuildFileProviderIntent() {
        val sampleJson = """
            {
              "tag_name": "v1.2.0",
              "name": "PC ViewPort v1.2.0",
              "html_url": "https://github.com/brainiac-goat-dev49/PC-ViewPort-Browser/releases/tag/v1.2.0",
              "body": "### What's New\n- GitHub in-app updater\n- Faster desktop toolbar",
              "assets": [
                {
                  "name": "PCViewPort_v1.2.0.apk",
                  "size": 24117248,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://github.com/brainiac-goat-dev49/PC-ViewPort-Browser/releases/download/v1.2.0/PCViewPort_v1.2.0.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = GitHubAppUpdaterRepository.parseReleaseJson(sampleJson)
        assertEquals("v1.2.0", parsed.tagName)
        assertEquals("1.2.0", parsed.versionName)
        assertTrue(parsed.body.contains("GitHub in-app updater"))
        assertNotNull(parsed.apkAsset)
        assertEquals("PCViewPort_v1.2.0.apk", parsed.apkAsset?.name)
        assertTrue(SemVer.isRemoteNewer(parsed.versionName, "1.0.0"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val updatesDir = File(context.cacheDir, GitHubAppUpdaterRepository.UPDATES_DIR_NAME).apply {
            mkdirs()
        }
        val dummyApk = File(updatesDir, "PCViewPort_v1.2.0.apk").apply {
            writeText("dummy-apk-bytes")
        }

        val repo = GitHubAppUpdaterRepository()
        val intent = repo.buildInstallIntent(context, dummyApk)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(GitHubAppUpdaterRepository.APK_MIME_TYPE, intent.type)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue(intent.data.toString().contains("${context.packageName}.fileprovider"))
    }

    @Test
    fun appUpdateDialogDisplaysChangelogAndTriggersUpdateNow() {
        val release = GitHubReleaseInfo(
            tagName = "v1.2.0",
            versionName = "1.2.0",
            releaseName = "PC ViewPort v1.2.0",
            body = "Changelog: Added self-updater & stability improvements",
            htmlUrl = "https://github.com/brainiac-goat-dev49/PC-ViewPort-Browser/releases/tag/v1.2.0",
            apkAsset = GitHubReleaseAsset(
                name = "PCViewPort_v1.2.0.apk",
                downloadUrl = "https://example.com/PCViewPort_v1.2.0.apk",
                sizeBytes = 23_000_000L,
                contentType = GitHubAppUpdaterRepository.APK_MIME_TYPE
            )
        )
        var updateClicked = false
        var dismissed = false

        composeRule.setContent {
            MaterialTheme {
                AppUpdateDialog(
                    state = AppUpdateUiState.Available(release = release, localVersion = "1.0.0"),
                    onUpdateNow = { updateClicked = true },
                    onOpenReleasePage = {},
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("app_update_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("update_dialog_notes")
            .assertIsDisplayed()
            .assertTextContains("Added self-updater", substring = true)

        composeRule.onNodeWithTag("update_dialog_confirm_button").performClick()
        assertTrue(updateClicked)

        composeRule.onNodeWithTag("update_dialog_dismiss_button").performClick()
        assertTrue(dismissed)
    }
}
