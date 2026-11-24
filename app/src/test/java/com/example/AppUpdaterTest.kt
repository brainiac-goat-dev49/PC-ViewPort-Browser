package com.example

import android.content.Context
import android.content.Intent
import com.example.data.BookmarkRepository
import com.example.data.BrowserSettings
import com.example.data.PersistedSessionState
import com.example.data.SettingsStore
import com.example.system.AppUpdateChecker
import com.example.system.GitHubAppUpdaterRepository
import com.example.system.GitHubReleaseAsset
import com.example.system.GitHubReleaseInfo
import com.example.system.SemVer
import com.example.ui.AppUpdateUiState
import com.example.ui.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

private class FakeAppUpdateChecker(
    var releaseResult: Result<GitHubReleaseInfo> = Result.failure(IOException("Offline"))
) : AppUpdateChecker {
    var fetchCount: Int = 0
        private set

    override suspend fun fetchLatestRelease(apiUrl: String): Result<GitHubReleaseInfo> {
        fetchCount++
        return releaseResult
    }

    override suspend fun downloadApkToCache(
        context: Context,
        asset: GitHubReleaseAsset,
        onProgress: (Int) -> Unit
    ): Result<File> {
        return Result.failure(UnsupportedOperationException("Use Robolectric test for file cache"))
    }

    override fun buildInstallIntent(context: Context, apkFile: File): Intent = Intent(Intent.ACTION_VIEW)

    override fun canInstallPackages(context: Context): Boolean = true
}

private class UpdateTestSettingsStore(
    initial: BrowserSettings = BrowserSettings()
) : SettingsStore {
    private val settingsFlow = MutableStateFlow(initial)
    private val sessionFlow = MutableStateFlow<PersistedSessionState?>(null)
    override val settings: Flow<BrowserSettings> = settingsFlow
    override val sessionState: Flow<PersistedSessionState?> = sessionFlow

    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        settingsFlow.update(transform)
    }

    override suspend fun saveSessionState(session: PersistedSessionState) {
        sessionFlow.value = session
    }

    override suspend fun clearSessionState() {
        sessionFlow.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdaterTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `semver strips v prefix and compares multi-digit and pre-release versions accurately`() {
        assertTrue(SemVer.isRemoteNewer("v1.2.0", "1.0.0"))
        assertTrue(SemVer.isRemoteNewer("v1.10.0", "1.2.0"))
        assertTrue(SemVer.isRemoteNewer("1.0.1", "v1.0.0"))
        assertFalse(SemVer.isRemoteNewer("v1.0.0", "1.0.0"))
        assertFalse(SemVer.isRemoteNewer("v0.9.9", "1.0.0"))
        assertTrue(SemVer.isRemoteNewer("2.0.0", "2.0.0-rc.1"))
        assertFalse(SemVer.isRemoteNewer("2.0.0-rc.1", "2.0.0"))

        assertEquals(
            "1.0.0",
            GitHubAppUpdaterRepository.resolveVersionName(
                tagName = "latest",
                releaseName = "Release PC ViewPort v1.0.0",
                apkAssetName = "PCViewPort_v1.0.0.apk"
            )
        )
    }

    @Test
    fun `startup update check opens dialog when newer release is available`() = runTest {
        val release = GitHubReleaseInfo(
            tagName = "v1.2.0",
            versionName = "1.2.0",
            releaseName = "PC ViewPort v1.2.0",
            body = "- Added GitHub self-updater\n- Improved desktop layout",
            htmlUrl = "https://github.com/brainiac-goat-dev49/PC-ViewPort-Browser/releases/tag/v1.2.0",
            apkAsset = GitHubReleaseAsset(
                name = "PCViewPort_v1.2.0.apk",
                downloadUrl = "https://github.com/brainiac-goat-dev49/PC-ViewPort-Browser/releases/download/v1.2.0/PCViewPort_v1.2.0.apk",
                sizeBytes = 24_000_000L,
                contentType = "application/vnd.android.package-archive"
            )
        )
        val fakeChecker = FakeAppUpdateChecker(Result.success(release))
        val vm = BrowserViewModel(
            bookmarkRepository = BookmarkRepository(FakeBookmarkDao()),
            settingsStore = UpdateTestSettingsStore(BrowserSettings(autoCheckUpdatesOnStartup = true)),
            appUpdateChecker = fakeChecker,
            localVersionName = "1.0.0"
        )
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(1, fakeChecker.fetchCount)
        assertTrue(state.isUpdateDialogVisible)
        assertTrue(state.appUpdateState is AppUpdateUiState.Available)
        assertEquals("1.2.0", (state.appUpdateState as AppUpdateUiState.Available).release.versionName)

        vm.dismissUpdateDialog()
        assertFalse(vm.uiState.value.isUpdateDialogVisible)
    }

    @Test
    fun `startup update check is skipped when disabled and silent on offline error`() = runTest {
        val disabledChecker = FakeAppUpdateChecker()
        val disabledVm = BrowserViewModel(
            bookmarkRepository = BookmarkRepository(FakeBookmarkDao()),
            settingsStore = UpdateTestSettingsStore(BrowserSettings(autoCheckUpdatesOnStartup = false)),
            appUpdateChecker = disabledChecker,
            localVersionName = "1.0.0"
        )
        backgroundScope.launch(testDispatcher) { disabledVm.uiState.collect {} }
        assertEquals(0, disabledChecker.fetchCount)
        assertEquals(AppUpdateUiState.Idle, disabledVm.uiState.value.appUpdateState)

        // When enabled but offline on startup, failure stays silent
        val offlineChecker = FakeAppUpdateChecker(Result.failure(IOException("No internet")))
        val offlineVm = BrowserViewModel(
            bookmarkRepository = BookmarkRepository(FakeBookmarkDao()),
            settingsStore = UpdateTestSettingsStore(BrowserSettings(autoCheckUpdatesOnStartup = true)),
            appUpdateChecker = offlineChecker,
            localVersionName = "1.0.0"
        )
        backgroundScope.launch(testDispatcher) { offlineVm.uiState.collect {} }
        assertEquals(1, offlineChecker.fetchCount)
        assertFalse(offlineVm.uiState.value.isUpdateDialogVisible)
        assertEquals(AppUpdateUiState.Idle, offlineVm.uiState.value.appUpdateState)

        // Manual check surfaces error cleanly
        offlineVm.checkForUpdates(userInitiated = true)
        assertTrue(offlineVm.uiState.value.appUpdateState is AppUpdateUiState.Error)
        assertEquals("No internet", offlineVm.uiState.value.statusMessage)
    }

    @Test
    fun `manual check reports UpToDate when remote matches local version`() = runTest {
        val release = GitHubReleaseInfo(
            tagName = "v1.0.0",
            versionName = "1.0.0",
            releaseName = "PC ViewPort v1.0.0",
            body = "Initial build",
            htmlUrl = "https://github.com/brainiac-goat-dev49/PC-ViewPort-Browser/releases/tag/v1.0.0",
            apkAsset = null
        )
        val fakeChecker = FakeAppUpdateChecker(Result.success(release))
        val vm = BrowserViewModel(
            bookmarkRepository = BookmarkRepository(FakeBookmarkDao()),
            settingsStore = UpdateTestSettingsStore(BrowserSettings(autoCheckUpdatesOnStartup = false)),
            appUpdateChecker = fakeChecker,
            localVersionName = "1.0.0"
        )
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }

        vm.checkForUpdates(userInitiated = true)
        assertFalse(vm.uiState.value.isUpdateDialogVisible)
        assertEquals(AppUpdateUiState.UpToDate("1.0.0"), vm.uiState.value.appUpdateState)
        assertTrue(vm.uiState.value.statusMessage?.contains("latest version", ignoreCase = true) == true)
    }
}
