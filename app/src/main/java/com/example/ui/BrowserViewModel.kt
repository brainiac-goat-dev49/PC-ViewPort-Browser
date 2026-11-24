package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAnalysisMode
import com.example.ai.AiAnalysisUiState
import com.example.ai.AiWebAnalyzerRepository
import com.example.ai.PageSnapshot
import com.example.ai.WebAnalyzer
import com.example.browser.BrowserConfig
import com.example.browser.BrowserScripts
import com.example.browser.BrowserTabState
import com.example.browser.ConsoleLogEntry
import com.example.browser.ConsoleLogLevel
import com.example.browser.DesktopWebView
import com.example.browser.NetworkAndProxyHelper
import com.example.browser.UrlResolver
import com.example.browser.WebViewCallbacks
import com.example.browser.WebViewRegistry
import com.example.data.BookmarkEntity
import com.example.data.BookmarkRepository
import com.example.data.BrowserDatabase
import com.example.data.BrowserSettings
import com.example.data.BrowserSettingsRepository
import com.example.data.CityPreset
import com.example.data.DecryptedPasswordEntry
import com.example.data.DownloadEntity
import com.example.data.DownloadRepository
import com.example.data.DownloadStore
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.data.HistoryStore
import com.example.data.NoteClipEntity
import com.example.data.NoteClipRepository
import com.example.data.NoteClipStore
import com.example.data.PasswordRepository
import com.example.data.PasswordStore
import com.example.data.PersistedSessionState
import com.example.data.PersistedTabSnapshot
import com.example.data.ProxyScheme
import com.example.data.PwaStore
import com.example.data.SavedPwaEntity
import com.example.data.SavedPwaRepository
import com.example.data.SettingsStore
import com.example.BuildConfig
import com.example.news.DailyNewsRepository
import com.example.news.NewsCategory
import com.example.news.NewsFeedSource
import com.example.system.AppUpdateChecker
import com.example.system.FloatingBubbleService
import com.example.system.GitHubAppUpdaterRepository
import com.example.system.GitHubReleaseInfo
import com.example.system.PwaShortcutHelper
import com.example.system.SemVer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

private class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(BrowserSettings())
    private val session = MutableStateFlow<PersistedSessionState?>(null)
    override val settings: Flow<BrowserSettings> = state
    override val sessionState: Flow<PersistedSessionState?> = session
    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        state.update(transform)
    }

    override suspend fun saveSessionState(session: PersistedSessionState) {
        this.session.value = session
    }

    override suspend fun clearSessionState() {
        this.session.value = null
    }
}

private class InMemoryHistoryStore : HistoryStore {
    private val state = MutableStateFlow<List<HistoryEntity>>(emptyList())
    override val history: Flow<List<HistoryEntity>> = state

    override suspend fun recordVisit(title: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || UrlResolver.isInternalPage(cleanUrl)) return
        val now = System.currentTimeMillis()
        state.update { list ->
            val existing = list.firstOrNull { it.url == cleanUrl }
            val cleanTitle = title.trim()
            val resolvedTitle = when {
                cleanTitle.isNotEmpty() && cleanTitle != cleanUrl -> cleanTitle
                existing != null && existing.title.isNotBlank() && existing.title != cleanUrl -> existing.title
                else -> UrlResolver.extractDisplayHost(cleanUrl).ifBlank { cleanUrl }
            }
            if (existing != null) {
                val updated = existing.copy(
                    title = resolvedTitle,
                    visitedAt = now,
                    visitCount = existing.visitCount + 1
                )
                listOf(updated) + list.filterNot { it.id == existing.id }
            } else {
                val nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1L
                listOf(
                    HistoryEntity(
                        id = nextId,
                        title = resolvedTitle,
                        url = cleanUrl,
                        visitedAt = now,
                        visitCount = 1
                    )
                ) + list
            }
        }
    }

    override suspend fun updateTitleIfPresent(url: String, title: String) {
        val cleanUrl = url.trim()
        val cleanTitle = title.trim()
        if (cleanUrl.isEmpty() || cleanTitle.isEmpty() || cleanTitle == cleanUrl) return
        if (UrlResolver.isInternalPage(cleanUrl)) return
        state.update { list ->
            list.map { item ->
                if (item.url == cleanUrl) item.copy(title = cleanTitle) else item
            }
        }
    }

    override suspend fun deleteEntry(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

private class InMemoryDownloadStore : DownloadStore {
    private val state = MutableStateFlow<List<DownloadEntity>>(emptyList())
    override val downloads: Flow<List<DownloadEntity>> = state
    override suspend fun recordDownload(
        fileName: String,
        url: String,
        mimeType: String,
        systemDownloadId: Long
    ) {
        state.update {
            listOf(
                DownloadEntity(
                    id = (it.size + 1).toLong(),
                    fileName = fileName,
                    url = url,
                    mimeType = mimeType,
                    systemDownloadId = systemDownloadId
                )
            ) + it
        }
    }

    override suspend fun deleteDownload(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

private class InMemoryPasswordStore : PasswordStore {
    private val state = MutableStateFlow<List<DecryptedPasswordEntry>>(emptyList())
    override val entries: Flow<List<DecryptedPasswordEntry>> = state
    override suspend fun saveCredential(
        siteTitle: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        existingId: Long
    ) {
        state.update { list ->
            val nextId = if (existingId > 0L) existingId else (list.maxOfOrNull { it.id } ?: 0L) + 1L
            val item = DecryptedPasswordEntry(
                id = nextId,
                siteTitle = siteTitle.ifBlank { siteUrl },
                siteUrl = siteUrl,
                username = username,
                password = plainPassword,
                updatedAt = System.currentTimeMillis()
            )
            listOf(item) + list.filterNot { it.id == nextId }
        }
    }

    override suspend fun deleteCredential(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

private class InMemoryNoteClipStore : NoteClipStore {
    private val state = MutableStateFlow<List<NoteClipEntity>>(emptyList())
    override val clips: Flow<List<NoteClipEntity>> = state
    override suspend fun saveClip(
        title: String,
        content: String,
        sourceUrl: String,
        existingId: Long
    ) {
        state.update { list ->
            val nextId = if (existingId > 0L) existingId else (list.maxOfOrNull { it.id } ?: 0L) + 1L
            val item = NoteClipEntity(
                id = nextId,
                title = title.ifBlank { "Web Clip" },
                content = content,
                sourceUrl = sourceUrl
            )
            listOf(item) + list.filterNot { it.id == nextId }
        }
    }

    override suspend fun deleteClip(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

private class InMemoryPwaStore : PwaStore {
    private val state = MutableStateFlow<List<SavedPwaEntity>>(emptyList())
    override val pwas: Flow<List<SavedPwaEntity>> = state
    override suspend fun recordPwa(name: String, startUrl: String) {
        state.update { list ->
            val nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1L
            listOf(SavedPwaEntity(id = nextId, name = name, startUrl = startUrl)) + list
        }
    }

    override suspend fun removePwa(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

private data class WorkspaceOverlaysState(
    val activeToolPage: ActiveToolPage = ActiveToolPage.NONE,
    val isDevToolsOpen: Boolean = false,
    val isFindInPageOpen: Boolean = false,
    val findInPageQuery: String = "",
    val isSystemVpnActive: Boolean = false,
    val activeGroupFilter: String? = null,
    val tabOptionsTargetId: String? = null,
    val tourStepIndex: Int? = null,
    val bookmarkDialogState: BookmarkDialogState? = null,
    val isUpdateDialogVisible: Boolean = false,
    val statusMessage: String? = null
)

private data class FeatureDataState(
    val aiAnalysisState: AiAnalysisUiState = AiAnalysisUiState.Idle,
    val aiSelectedMode: AiAnalysisMode = AiAnalysisMode.SUMMARY,
    val newsState: NewsUiState = NewsUiState(),
    val mediaState: ExtractedMediaState = ExtractedMediaState(),
    val screenshotBitmap: Bitmap? = null,
    val imageViewerState: ImageViewerState? = null,
    val appUpdateState: AppUpdateUiState = AppUpdateUiState.Idle
)

class BrowserViewModel(
    private val bookmarkRepository: BookmarkRepository,
    private val settingsStore: SettingsStore = InMemorySettingsStore(),
    private val historyStore: HistoryStore = InMemoryHistoryStore(),
    private val downloadStore: DownloadStore = InMemoryDownloadStore(),
    private val passwordStore: PasswordStore = InMemoryPasswordStore(),
    private val noteClipStore: NoteClipStore = InMemoryNoteClipStore(),
    private val pwaStore: PwaStore = InMemoryPwaStore(),
    private val newsFeedSource: NewsFeedSource = DailyNewsRepository(),
    private val webAnalyzer: WebAnalyzer = AiWebAnalyzerRepository(),
    private val appUpdateChecker: AppUpdateChecker? = null,
    private val localVersionName: String = BuildConfig.VERSION_NAME,
    val webViewRegistry: WebViewRegistry = WebViewRegistry()
) : ViewModel(), WebViewCallbacks {

    private val initialTab = BrowserTabState(
        title = "Start Page",
        url = BrowserConfig.START_PAGE_URL,
        isDesktopMode = true
    )

    private val _tabs = MutableStateFlow(listOf(initialTab))
    private val _activeTabId = MutableStateFlow(initialTab.id)
    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    private val _overlays = MutableStateFlow(WorkspaceOverlaysState())
    private val _featureData = MutableStateFlow(FeatureDataState())

    private val _pendingGeolocation = MutableStateFlow<PendingGeolocationPrompt?>(null)
    val pendingGeolocation: StateFlow<PendingGeolocationPrompt?> = _pendingGeolocation

    private val _pendingDownload = MutableStateFlow<PendingDownloadEvent?>(null)
    val pendingDownload: StateFlow<PendingDownloadEvent?> = _pendingDownload

    private val _pendingInstallPermissionRequest = MutableStateFlow(false)
    val pendingInstallPermissionRequest: StateFlow<Boolean> = _pendingInstallPermissionRequest

    private var aiJob: Job? = null
    private var updateJob: Job? = null
    @Volatile
    private var latestSettings: BrowserSettings = BrowserSettings()
    @Volatile
    private var hasExternalNavigationOrUserAction: Boolean = false
    private var startupRestoreCompleted: Boolean = false

    private val coreCollectionsA = combine(
        bookmarkRepository.bookmarks,
        historyStore.history,
        downloadStore.downloads
    ) { bookmarks, history, downloads ->
        Triple(bookmarks, history, downloads)
    }

    private val coreCollectionsB = combine(
        passwordStore.entries,
        noteClipStore.clips,
        pwaStore.pwas
    ) { passwords, clips, pwas ->
        Triple(passwords, clips, pwas)
    }

    private val coreDataFlow = combine(
        coreCollectionsA,
        coreCollectionsB
    ) { (bookmarks, history, downloads), (passwords, clips, pwas) ->
        CoreCollections(bookmarks, history, downloads, passwords, clips, pwas)
    }

    val uiState: StateFlow<BrowserUiState> = combine(
        _tabs,
        _activeTabId,
        coreDataFlow,
        settingsStore.settings,
        _consoleLogs,
        _overlays,
        _featureData
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tabs = values[0] as List<BrowserTabState>
        val activeId = values[1] as String
        val core = values[2] as CoreCollections
        val settings = values[3] as BrowserSettings
        @Suppress("UNCHECKED_CAST")
        val logs = values[4] as List<ConsoleLogEntry>
        val overlays = values[5] as WorkspaceOverlaysState
        val features = values[6] as FeatureDataState
        latestSettings = settings
        webViewRegistry.updateSettingsSnapshot(settings)
        BrowserUiState(
            tabs = tabs,
            activeTabId = activeId,
            activeGroupFilter = overlays.activeGroupFilter,
            bookmarks = core.bookmarks,
            history = core.history,
            downloads = core.downloads,
            passwords = core.passwords,
            noteClips = core.clips,
            savedPwas = core.pwas,
            settings = settings,
            consoleLogs = logs,
            activeToolPage = overlays.activeToolPage,
            isDevToolsOpen = overlays.isDevToolsOpen,
            isFindInPageOpen = overlays.isFindInPageOpen,
            findInPageQuery = overlays.findInPageQuery,
            isSystemVpnActive = overlays.isSystemVpnActive,
            aiAnalysisState = features.aiAnalysisState,
            aiSelectedMode = features.aiSelectedMode,
            newsState = features.newsState,
            mediaState = features.mediaState,
            screenshotBitmap = features.screenshotBitmap,
            imageViewerState = features.imageViewerState,
            tabOptionsTargetId = overlays.tabOptionsTargetId,
            tourStepIndex = overlays.tourStepIndex,
            bookmarkDialogState = overlays.bookmarkDialogState,
            appUpdateState = features.appUpdateState,
            isUpdateDialogVisible = overlays.isUpdateDialogVisible,
            statusMessage = overlays.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserUiState(
            tabs = _tabs.value,
            activeTabId = _activeTabId.value
        )
    )

    init {
        viewModelScope.launch {
            runCatching {
                val initialSettings = settingsStore.settings.first()
                latestSettings = initialSettings
                val savedSession = settingsStore.sessionState.first()
                if (!hasExternalNavigationOrUserAction && !startupRestoreCompleted) {
                    startupRestoreCompleted = true
                    if (initialSettings.restoreLastPageOnStartup &&
                        savedSession != null &&
                        savedSession.tabs.isNotEmpty()
                    ) {
                        val restoredTabs = savedSession.tabs.map { snap ->
                            val cleanUrl = snap.url
                            BrowserTabState(
                                title = snap.title.ifBlank { UrlResolver.extractDisplayHost(cleanUrl) },
                                url = cleanUrl,
                                isDesktopMode = snap.isDesktopMode,
                                isIncognito = false,
                                isLocked = snap.isLocked,
                                groupName = snap.groupName,
                                groupColorHex = snap.groupColorHex
                            )
                        }
                        val targetTab = restoredTabs[savedSession.activeIndex.coerceIn(0, restoredTabs.lastIndex)]
                        _tabs.value = restoredTabs
                        _activeTabId.value = targetTab.id
                    } else if (!initialSettings.startPageEnabled) {
                        val homeUrl = effectiveHomeUrl(initialSettings)
                        val homeTitle = UrlResolver.extractDisplayHost(homeUrl).ifBlank { "Home" }
                        _tabs.value = listOf(
                            initialTab.copy(
                                title = homeTitle,
                                url = homeUrl
                            )
                        )
                    }
                }
                if (initialSettings.autoCheckUpdatesOnStartup && appUpdateChecker != null) {
                    checkForUpdates(userInitiated = false)
                }
            }

            settingsStore.settings.collect { settings ->
                latestSettings = settings
                val tabModes = _tabs.value.associate { it.id to it.isDesktopMode }
                webViewRegistry.applySettingsToAll(settings, tabModes)
                if (settings.proxyEnabled && settings.proxyHost.isNotBlank()) {
                    NetworkAndProxyHelper.applyProxySettings(settings) { _, _ -> }
                }
            }
        }
    }

    fun markExternalNavigation() {
        hasExternalNavigationOrUserAction = true
    }

    private fun effectiveHomeUrl(settings: BrowserSettings = latestSettings): String {
        return if (settings.startPageEnabled) {
            BrowserConfig.START_PAGE_URL
        } else {
            UrlResolver.resolveInput(settings.customHomeUrl) ?: BrowserConfig.DEFAULT_HOME_URL
        }
    }

    private fun defaultTitleForUrl(url: String, isIncognito: Boolean): String {
        return when {
            isIncognito -> "Incognito"
            UrlResolver.isStartPage(url) -> "Start Page"
            else -> UrlResolver.extractDisplayHost(url).ifBlank { "New Tab" }
        }
    }

    fun persistSessionState() {
        val currentTabs = _tabs.value
        val activeId = _activeTabId.value
        val nonIncognitoTabs = currentTabs.filter { !it.isIncognito }
        if (nonIncognitoTabs.isEmpty()) return

        val snapshots = nonIncognitoTabs.map { tab ->
            PersistedTabSnapshot(
                url = tab.url,
                title = tab.title.ifBlank { defaultTitleForUrl(tab.url, false) },
                isDesktopMode = tab.isDesktopMode,
                isLocked = tab.isLocked,
                groupName = tab.groupName,
                groupColorHex = tab.groupColorHex
            )
        }
        val activeIdx = nonIncognitoTabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        viewModelScope.launch {
            settingsStore.saveSessionState(
                PersistedSessionState(
                    tabs = snapshots,
                    activeIndex = activeIdx
                )
            )
        }
    }

    fun getOrCreateWebView(context: Context, tab: BrowserTabState): DesktopWebView {
        return webViewRegistry.getOrCreate(
            context = context,
            tabId = tab.id,
            initialUrl = tab.url,
            isDesktopMode = tab.isDesktopMode,
            isIncognito = tab.isIncognito,
            settingsSnapshot = uiState.value.settings,
            callbacks = this
        )
    }

    fun releaseWebViewContext(tabId: String) {
        webViewRegistry.detachContext(tabId)
    }

    fun openNewTab(
        url: String? = null,
        select: Boolean = true,
        isIncognito: Boolean = false,
        groupName: String? = null,
        groupColorHex: Long = 0xFF38BDF8L
    ) {
        hasExternalNavigationOrUserAction = true
        val fallbackUrl = effectiveHomeUrl()
        val resolved = if (url.isNullOrBlank()) fallbackUrl else (UrlResolver.resolveInput(url) ?: fallbackUrl)
        val hostTitle = defaultTitleForUrl(resolved, isIncognito)
        val newTab = BrowserTabState(
            title = hostTitle,
            url = resolved,
            isDesktopMode = true,
            isIncognito = isIncognito,
            groupName = groupName,
            groupColorHex = groupColorHex
        )
        _tabs.update { it + newTab }
        if (select) {
            _activeTabId.value = newTab.id
            _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE) }
        }
        if (!isIncognito) {
            persistSessionState()
        }
    }

    fun selectTab(tabId: String) {
        hasExternalNavigationOrUserAction = true
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            persistSessionState()
        }
    }

    fun closeTab(tabId: String) {
        hasExternalNavigationOrUserAction = true
        val currentTabs = _tabs.value
        val index = currentTabs.indexOfFirst { it.id == tabId }
        if (index == -1) return
        if (currentTabs[index].isLocked) {
            showStatusMessage("Tab is locked. Unlock it from Tab Options before closing.")
            return
        }

        webViewRegistry.destroy(tabId)

        if (currentTabs.size == 1) {
            val homeUrl = effectiveHomeUrl()
            val replacement = BrowserTabState(
                title = defaultTitleForUrl(homeUrl, false),
                url = homeUrl,
                isDesktopMode = true
            )
            _tabs.value = listOf(replacement)
            _activeTabId.value = replacement.id
            persistSessionState()
            return
        }

        val updatedTabs = currentTabs.filterNot { it.id == tabId }
        _tabs.value = updatedTabs

        if (_activeTabId.value == tabId) {
            val nextIndex = index.coerceAtMost(updatedTabs.lastIndex)
            _activeTabId.value = updatedTabs[nextIndex].id
        }
        persistSessionState()
    }

    fun toggleTabLock(tabId: String) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(isLocked = !it.isLocked) else it }
        }
        persistSessionState()
    }

    fun assignTabGroup(tabId: String, groupName: String?, colorHex: Long) {
        val cleanGroup = groupName?.trim()?.takeIf { it.isNotEmpty() }
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(groupName = cleanGroup, groupColorHex = colorHex)
                } else {
                    tab
                }
            }
        }
        persistSessionState()
    }

    fun selectGroupFilter(groupName: String?) {
        _overlays.update { it.copy(activeGroupFilter = groupName) }
    }

    fun openTabOptions(tabId: String) {
        _overlays.update { it.copy(tabOptionsTargetId = tabId) }
    }

    fun dismissTabOptions() {
        _overlays.update { it.copy(tabOptionsTargetId = null) }
    }

    fun navigateTo(rawInput: String) {
        val resolvedUrl = UrlResolver.resolveInput(rawInput) ?: return
        hasExternalNavigationOrUserAction = true
        val tabId = _activeTabId.value
        val isInternal = UrlResolver.isStartPage(resolvedUrl)
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    if (isInternal) {
                        tab.copy(
                            url = resolvedUrl,
                            title = if (tab.isIncognito) "Incognito" else "Start Page",
                            isLoading = false,
                            progress = 100
                        )
                    } else {
                        tab.copy(url = resolvedUrl, isLoading = true, progress = 10)
                    }
                } else {
                    tab
                }
            }
        }
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE) }
        if (!isInternal) {
            webViewRegistry.loadUrl(tabId, resolvedUrl)
        }
        persistSessionState()
    }

    fun goBack(): Boolean {
        val active = uiState.value.activeTab ?: return false
        if (UrlResolver.isStartPage(active.url)) return false
        return webViewRegistry.goBack(active.id)
    }

    fun goForward(): Boolean {
        val active = uiState.value.activeTab ?: return false
        if (UrlResolver.isStartPage(active.url)) return false
        return webViewRegistry.goForward(active.id)
    }

    fun refreshOrStop() {
        val active = uiState.value.activeTab ?: return
        if (UrlResolver.isStartPage(active.url)) return
        if (active.isLoading) {
            webViewRegistry.stopLoading(active.id)
            _tabs.update { list ->
                list.map { if (it.id == active.id) it.copy(isLoading = false, progress = 0) else it }
            }
        } else {
            webViewRegistry.reload(active.id)
        }
    }

    fun goHome() {
        navigateTo(effectiveHomeUrl())
    }

    fun toggleDesktopMode(): Boolean {
        val active = uiState.value.activeTab ?: return true
        val nextMode = !active.isDesktopMode
        _tabs.update { list ->
            list.map { if (it.id == active.id) it.copy(isDesktopMode = nextMode) else it }
        }
        if (!UrlResolver.isStartPage(active.url)) {
            webViewRegistry.applyDesktopMode(active.id, nextMode, reload = true)
        }
        persistSessionState()
        return nextMode
    }

    // --- Full-Page Navigation Helpers ---

    fun closeActiveToolPage() {
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE) }
    }

    fun openBookmarksSheet() = openToolPage(ActiveToolPage.BOOKMARKS)
    fun closeBookmarksSheet() = closeActiveToolPage()
    fun openHistoryPage() = openToolPage(ActiveToolPage.HISTORY)
    fun openDownloadsSheet() = openToolPage(ActiveToolPage.DOWNLOADS)
    fun closeDownloadsSheet() = closeActiveToolPage()
    fun openQuickActionsPage() = openToolPage(ActiveToolPage.QUICK_ACTIONS)
    fun openPasswordsPage() = openToolPage(ActiveToolPage.PASSWORDS)
    fun openNotesPage() = openToolPage(ActiveToolPage.NOTES_CLIPS)

    fun openSettingsSheet(context: Context? = null) {
        if (context != null) refreshVpnStatus(context)
        openToolPage(ActiveToolPage.SETTINGS)
    }

    fun closeSettingsSheet() = closeActiveToolPage()

    private fun openToolPage(page: ActiveToolPage) {
        _overlays.update { it.copy(activeToolPage = page, tabOptionsTargetId = null) }
    }

    // --- AI Web Analysing ---

    fun openAiAnalystPage(autoRun: Boolean = true) {
        openToolPage(ActiveToolPage.AI_ANALYST)
        if (autoRun && uiState.value.settings.aiWebAnalyzingEnabled) {
            analyzeActivePage(uiState.value.aiSelectedMode)
        }
    }

    fun setAiWebAnalyzingEnabled(enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(aiWebAnalyzingEnabled = enabled) }
    }

    fun enableAiAndAnalyze(mode: AiAnalysisMode, customQuestion: String = "") {
        viewModelScope.launch {
            settingsStore.update { it.copy(aiWebAnalyzingEnabled = true) }
            runAnalysisInternal(mode, customQuestion)
        }
    }

    fun analyzeActivePage(mode: AiAnalysisMode, customQuestion: String = "") {
        if (!uiState.value.settings.aiWebAnalyzingEnabled) {
            _featureData.update {
                it.copy(
                    aiSelectedMode = mode,
                    aiAnalysisState = AiAnalysisUiState.Error(
                        "AI Web Analysing is turned off. Enable it in Settings to analyze pages."
                    )
                )
            }
            return
        }
        runAnalysisInternal(mode, customQuestion)
    }

    private fun runAnalysisInternal(mode: AiAnalysisMode, customQuestion: String) {
        val tab = uiState.value.activeTab ?: return
        _featureData.update {
            it.copy(aiSelectedMode = mode, aiAnalysisState = AiAnalysisUiState.Extracting)
        }

        if (UrlResolver.isStartPage(tab.url) || webViewRegistry.get(tab.id) == null) {
            val snapshot = parsePageSnapshot(null, tab)
            launchAiStream(snapshot, mode, customQuestion)
            return
        }

        webViewRegistry.evaluateRawJs(tab.id, BrowserScripts.EXTRACT_PAGE_CONTENT_SCRIPT) { raw ->
            val snapshot = parsePageSnapshot(raw, tab)
            launchAiStream(snapshot, mode, customQuestion)
        }
    }

    private fun launchAiStream(
        snapshot: PageSnapshot,
        mode: AiAnalysisMode,
        customQuestion: String
    ) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            _featureData.update {
                it.copy(
                    aiAnalysisState = AiAnalysisUiState.Error(
                        throwable.localizedMessage?.takeIf { msg -> msg.isNotBlank() }
                            ?: "AI Web Analyst encountered an unexpected error."
                    )
                )
            }
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch(exceptionHandler) {
            webAnalyzer.analyzePageStream(snapshot, mode, customQuestion)
                .catch { throwable ->
                    emit(
                        AiAnalysisUiState.Error(
                            throwable.localizedMessage?.takeIf { msg -> msg.isNotBlank() }
                                ?: "AI Web Analyst encountered an unexpected error."
                        )
                    )
                }
                .collect { state ->
                    _featureData.update { it.copy(aiAnalysisState = state) }
                }
        }
    }

    fun saveAiResultToNotes() {
        val currentText = when (val state = uiState.value.aiAnalysisState) {
            is AiAnalysisUiState.Success -> state.text
            is AiAnalysisUiState.Streaming -> state.text
            else -> return
        }
        val tab = uiState.value.activeTab
        viewModelScope.launch {
            noteClipStore.saveClip(
                title = "AI Brief: ${tab?.title?.ifBlank { "Web Page" } ?: "Web Page"}",
                content = currentText,
                sourceUrl = tab?.url.orEmpty()
            )
            showStatusMessage("Saved AI brief to Notes & Clips")
        }
    }

    private fun parsePageSnapshot(rawJsonResult: String?, tab: BrowserTabState): PageSnapshot {
        val fallback = PageSnapshot(
            title = tab.title,
            url = tab.url,
            description = "",
            selectedText = "",
            headings = emptyList(),
            bodyText = "Page Title: ${tab.title}\nURL: ${tab.url}",
            linkCount = 0,
            mediaCount = 0
        )
        if (rawJsonResult.isNullOrBlank() || rawJsonResult == "null") return fallback
        return try {
            val unquoted = if (rawJsonResult.startsWith("\"") && rawJsonResult.endsWith("\"")) {
                JSONObject("{\"v\":$rawJsonResult}").getString("v")
            } else {
                rawJsonResult
            }
            val obj = JSONObject(unquoted)
            val headingsArr = obj.optJSONArray("headings")
            val headings = if (headingsArr != null) {
                (0 until headingsArr.length()).mapNotNull { headingsArr.optString(it).takeIf(String::isNotBlank) }
            } else {
                emptyList()
            }
            PageSnapshot(
                title = obj.optString("title", tab.title).ifBlank { tab.title },
                url = obj.optString("url", tab.url).ifBlank { tab.url },
                description = obj.optString("description", ""),
                selectedText = obj.optString("selectedText", ""),
                headings = headings,
                bodyText = obj.optString("bodyText", "").ifBlank { fallback.bodyText },
                linkCount = obj.optInt("linkCount", 0),
                mediaCount = obj.optInt("mediaCount", 0)
            )
        } catch (_: Throwable) {
            fallback
        }
    }

    // --- Daily News ---

    fun openNewsPage(category: NewsCategory = uiState.value.newsState.category) {
        openToolPage(ActiveToolPage.DAILY_NEWS)
        if (uiState.value.newsState.articles.isEmpty() || category != uiState.value.newsState.category) {
            loadNewsCategory(category)
        }
    }

    fun loadNewsCategory(category: NewsCategory) {
        viewModelScope.launch {
            _featureData.update {
                it.copy(newsState = it.newsState.copy(category = category, isLoading = true, errorMessage = null))
            }
            val result = newsFeedSource.fetchCategory(category)
            result.fold(
                onSuccess = { articles ->
                    _featureData.update {
                        it.copy(
                            newsState = NewsUiState(
                                category = category,
                                isLoading = false,
                                articles = articles,
                                errorMessage = null
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _featureData.update {
                        it.copy(
                            newsState = NewsUiState(
                                category = category,
                                isLoading = false,
                                articles = emptyList(),
                                errorMessage = error.localizedMessage ?: "Failed to load news feed."
                            )
                        )
                    }
                }
            )
        }
    }

    // --- Password Manager ---

    fun savePasswordCredential(
        siteTitle: String,
        siteUrl: String,
        username: String,
        plainPassword: String,
        existingId: Long = 0L
    ) {
        viewModelScope.launch {
            passwordStore.saveCredential(siteTitle, siteUrl, username, plainPassword, existingId)
            showStatusMessage("Credential saved securely")
        }
    }

    fun deletePasswordCredential(id: Long) {
        viewModelScope.launch {
            passwordStore.deleteCredential(id)
        }
    }

    fun autofillPasswordIntoActiveTab(entry: DecryptedPasswordEntry) {
        val tabId = _activeTabId.value
        val script = BrowserScripts.buildAutofillPasswordScript(entry.username, entry.password)
        webViewRegistry.evaluateRawJs(tabId, script) { result ->
            closeActiveToolPage()
            if (result?.contains("FILLED_") == true) {
                showStatusMessage("Autofilled login for ${entry.siteTitle}")
            } else {
                showStatusMessage("No login inputs detected on this page")
            }
        }
    }

    // --- Notes & Clips ---

    fun saveNoteClip(title: String, content: String, sourceUrl: String = "", existingId: Long = 0L) {
        viewModelScope.launch {
            noteClipStore.saveClip(title, content, sourceUrl, existingId)
            showStatusMessage("Note saved")
        }
    }

    fun deleteNoteClip(id: Long) {
        viewModelScope.launch {
            noteClipStore.deleteClip(id)
        }
    }

    fun clipActivePageSelection() {
        val tab = uiState.value.activeTab ?: return
        webViewRegistry.evaluateRawJs(tab.id, BrowserScripts.EXTRACT_PAGE_CONTENT_SCRIPT) { raw ->
            val snapshot = parsePageSnapshot(raw, tab)
            val excerpt = snapshot.selectedText.ifBlank {
                snapshot.description.ifBlank { snapshot.bodyText.take(600) }
            }
            if (excerpt.isNotBlank()) {
                viewModelScope.launch {
                    noteClipStore.saveClip(
                        title = snapshot.title.ifBlank { "Web Clip" },
                        content = excerpt,
                        sourceUrl = snapshot.url
                    )
                    showStatusMessage("Clipped content from ${UrlResolver.extractDisplayHost(snapshot.url)}")
                }
            } else {
                showStatusMessage("No page text available to clip")
            }
        }
    }

    // --- Media Downloader ---

    fun openMediaDownloaderPage() {
        openToolPage(ActiveToolPage.MEDIA_DOWNLOADER)
        scanActivePageMedia()
    }

    fun scanActivePageMedia() {
        val tabId = _activeTabId.value
        _featureData.update { it.copy(mediaState = ExtractedMediaState(isScanning = true)) }
        webViewRegistry.evaluateRawJs(tabId, BrowserScripts.EXTRACT_MEDIA_SCRIPT) { raw ->
            val parsed = parseMediaJson(raw)
            _featureData.update { it.copy(mediaState = parsed) }
        }
    }

    fun downloadMediaItem(context: Context, item: ExtractedMediaItem) {
        val ua = if (uiState.value.activeTab?.isDesktopMode != false) {
            BrowserConfig.DESKTOP_USER_AGENT
        } else {
            BrowserConfig.MOBILE_USER_AGENT
        }
        val event = PendingDownloadEvent(
            url = item.url,
            userAgent = ua,
            contentDisposition = "",
            mimeType = when (item.kind) {
                MediaKind.VIDEO -> "video/*"
                MediaKind.IMAGE -> "image/*"
                MediaKind.AUDIO -> "audio/*"
            }
        )
        val fileName = processSystemDownload(context, event)
        if (fileName != null) {
            showStatusMessage("Downloading $fileName")
        } else {
            showStatusMessage("Unable to download media URL")
        }
    }

    private fun parseMediaJson(raw: String?): ExtractedMediaState {
        if (raw.isNullOrBlank() || raw == "null") return ExtractedMediaState(isScanning = false)
        return try {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                JSONObject("{\"v\":$raw}").getString("v")
            } else {
                raw
            }
            val root = JSONObject(unquoted)
            fun parseList(key: String, kind: MediaKind): List<ExtractedMediaItem> {
                val arr = root.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { idx ->
                    val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                    val url = obj.optString("url")
                    if (url.isBlank()) null
                    else ExtractedMediaItem(
                        url = url,
                        label = obj.optString("label", kind.name).ifBlank { url },
                        kind = kind
                    )
                }
            }
            ExtractedMediaState(
                isScanning = false,
                videos = parseList("videos", MediaKind.VIDEO),
                images = parseList("images", MediaKind.IMAGE),
                audio = parseList("audio", MediaKind.AUDIO)
            )
        } catch (_: Exception) {
            ExtractedMediaState(isScanning = false)
        }
    }

    // --- Screenshot & Editor + Image Viewer ---

    fun captureAndOpenScreenshotEditor() {
        val bmp = webViewRegistry.captureScreenshot(_activeTabId.value)
        if (bmp != null) {
            _featureData.update { it.copy(screenshotBitmap = bmp) }
            openToolPage(ActiveToolPage.SCREENSHOT_EDITOR)
        } else {
            showStatusMessage("Unable to capture WebView screenshot right now")
        }
    }

    fun openImageViewer(title: String, imageUrl: String? = null, bitmap: Bitmap? = null) {
        _featureData.update {
            it.copy(imageViewerState = ImageViewerState(title = title, imageUrl = imageUrl, bitmap = bitmap))
        }
        openToolPage(ActiveToolPage.IMAGE_VIEWER)
    }

    // --- PWA Install ---

    fun installCurrentPageAsPwa(context: Context) {
        val tab = uiState.value.activeTab ?: return
        webViewRegistry.evaluateRawJs(tab.id, BrowserScripts.EXTRACT_PWA_MANIFEST_SCRIPT) { raw ->
            val obj = runCatching {
                val unquoted = if (raw != null && raw.startsWith("\"")) {
                    JSONObject("{\"v\":$raw}").getString("v")
                } else {
                    raw.orEmpty()
                }
                JSONObject(unquoted)
            }.getOrNull()
            val appName = obj?.optString("appName")?.ifBlank { tab.title } ?: tab.title
            val startUrl = obj?.optString("startUrl")?.ifBlank { tab.url } ?: tab.url
            val pinned = PwaShortcutHelper.requestPinPwaShortcut(context, appName, startUrl)
            viewModelScope.launch {
                pwaStore.recordPwa(appName, startUrl)
            }
            showStatusMessage(
                if (pinned) "PWA shortcut requested for $appName"
                else "Saved $appName to Installed Web Apps"
            )
        }
    }

    fun removeSavedPwa(id: Long) {
        viewModelScope.launch { pwaStore.removePwa(id) }
    }

    // --- Find in Page ---

    fun openFindInPage() {
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE, isFindInPageOpen = true) }
    }

    fun updateFindQuery(query: String) {
        _overlays.update { it.copy(findInPageQuery = query) }
        webViewRegistry.findInPage(_activeTabId.value, query)
    }

    fun findNextMatch(forward: Boolean) {
        webViewRegistry.findNext(_activeTabId.value, forward)
    }

    fun closeFindInPage() {
        webViewRegistry.clearFindInPage(_activeTabId.value)
        _overlays.update { it.copy(isFindInPageOpen = false, findInPageQuery = "") }
    }

    // --- Tooltips, Interactive Tour Guide & Floating Bubble ---

    fun setShowTooltips(enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(showTooltips = enabled) }
    }

    fun startTourGuide() {
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE, tourStepIndex = 0) }
    }

    fun nextTourStep() {
        val current = _overlays.value.tourStepIndex ?: return
        if (current < BrowserTourSteps.steps.lastIndex) {
            _overlays.update { it.copy(tourStepIndex = current + 1) }
        } else {
            finishTourGuide(markCompleted = true)
        }
    }

    fun previousTourStep() {
        val current = _overlays.value.tourStepIndex ?: return
        if (current > 0) {
            _overlays.update { it.copy(tourStepIndex = current - 1) }
        }
    }

    fun finishTourGuide(markCompleted: Boolean) {
        _overlays.update { it.copy(tourStepIndex = null) }
        if (markCompleted) {
            updateSettings(reloadActive = false) { it.copy(hasCompletedTour = true) }
        }
    }

    fun setFloatingBubbleEnabled(context: Context, enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(floatingBubbleEnabled = enabled) }
        if (enabled) {
            FloatingBubbleService.start(context)
        } else {
            FloatingBubbleService.stop(context)
        }
    }

    // --- History Management ---

    fun openHistoryItem(url: String, inNewTab: Boolean) {
        closeActiveToolPage()
        if (inNewTab) {
            openNewTab(url = url, select = true)
        } else {
            navigateTo(url)
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            historyStore.deleteEntry(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clearAll()
            showStatusMessage("Browsing history cleared")
        }
    }

    // --- Bookmarks ---

    fun toggleBookmarkForCurrentPage(onResult: (Boolean) -> Unit = {}) {
        val active = uiState.value.activeTab ?: return
        if (UrlResolver.isInternalPage(active.url)) {
            showStatusMessage("Open a web page before saving a bookmark")
            return
        }
        if (active.isIncognito) {
            showStatusMessage("Bookmarking in Incognito: saved locally only when requested")
        }
        viewModelScope.launch {
            val added = bookmarkRepository.toggleBookmark(
                title = active.title,
                url = active.url
            )
            onResult(added)
        }
    }

    fun openAddBookmarkDialog() {
        val active = uiState.value.activeTab
        _overlays.update {
            it.copy(
                bookmarkDialogState = BookmarkDialogState(
                    id = 0L,
                    title = active?.title.orEmpty(),
                    url = active?.url.orEmpty()
                )
            )
        }
    }

    fun openEditBookmarkDialog(bookmark: BookmarkEntity) {
        _overlays.update {
            it.copy(
                bookmarkDialogState = BookmarkDialogState(
                    id = bookmark.id,
                    title = bookmark.title,
                    url = bookmark.url
                )
            )
        }
    }

    fun dismissBookmarkDialog() {
        _overlays.update { it.copy(bookmarkDialogState = null) }
    }

    fun saveBookmarkDialog(title: String, url: String) {
        val editingId = _overlays.value.bookmarkDialogState?.id ?: 0L
        val resolvedUrl = UrlResolver.resolveInput(url) ?: return
        viewModelScope.launch {
            bookmarkRepository.saveBookmark(
                title = title,
                url = resolvedUrl,
                existingId = editingId
            )
            _overlays.update { it.copy(bookmarkDialogState = null) }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
        }
    }

    fun openBookmark(url: String, inNewTab: Boolean) {
        closeActiveToolPage()
        if (inNewTab) {
            openNewTab(url = url, select = true)
        } else {
            navigateTo(url)
        }
    }

    // --- Downloads ---

    fun processSystemDownload(context: Context, event: PendingDownloadEvent): String? {
        _pendingDownload.value = null
        return try {
            val fileName = URLUtil.guessFileName(
                event.url,
                event.contentDisposition,
                event.mimeType
            )
            val request = DownloadManager.Request(Uri.parse(event.url)).apply {
                setTitle(fileName)
                setDescription(event.url)
                if (event.mimeType.isNotBlank()) {
                    setMimeType(event.mimeType)
                }
                if (event.userAgent.isNotBlank()) {
                    addRequestHeader("User-Agent", event.userAgent)
                }
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)
            viewModelScope.launch {
                downloadStore.recordDownload(
                    fileName = fileName,
                    url = event.url,
                    mimeType = event.mimeType.ifBlank { "application/octet-stream" },
                    systemDownloadId = downloadId
                )
            }
            fileName
        } catch (_: Exception) {
            null
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { downloadStore.deleteDownload(id) }
    }

    fun clearDownloads() {
        viewModelScope.launch { downloadStore.clearAll() }
    }

    // --- DevTools Console ---

    fun toggleDevTools() {
        _overlays.update { it.copy(isDevToolsOpen = !it.isDevToolsOpen) }
    }

    fun openDevTools() {
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE, isDevToolsOpen = true) }
    }

    fun closeDevTools() {
        _overlays.update { it.copy(isDevToolsOpen = false) }
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    fun runConsoleScript(code: String, asEsModule: Boolean) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val tabId = _activeTabId.value
        val prefix = if (asEsModule) "[ESM] " else "> "
        appendConsoleEntry(
            ConsoleLogEntry(level = ConsoleLogLevel.COMMAND, message = "$prefix$trimmed")
        )
        webViewRegistry.evaluateScript(tabId, trimmed, asEsModule) { result ->
            appendConsoleEntry(
                ConsoleLogEntry(level = ConsoleLogLevel.RESULT, message = result)
            )
        }
    }

    private fun appendConsoleEntry(entry: ConsoleLogEntry) {
        _consoleLogs.update { logs -> (logs + entry).takeLast(250) }
    }

    // --- Settings, Mock GPS, Hardware Keyboard, Proxy/VPN ---

    fun refreshVpnStatus(context: Context) {
        _overlays.update { it.copy(isSystemVpnActive = NetworkAndProxyHelper.isSystemVpnActive(context)) }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        updateSettings(reloadActive = true) { it.copy(adBlockerEnabled = enabled) }
    }

    fun setDesktopViewportWidthPx(widthPx: Int) {
        updateSettings(reloadActive = true) {
            it.copy(desktopViewportWidthPx = widthPx.coerceIn(640, 3840))
        }
    }

    fun setDesktopLandscapeViewportWidthPx(widthPx: Int) {
        updateSettings(reloadActive = true) {
            it.copy(desktopLandscapeViewportWidthPx = widthPx.coerceIn(640, 3840))
        }
    }

    fun setDesktopUiScale(scale: Float) {
        updateSettings(reloadActive = false) {
            it.copy(desktopUiScale = scale.coerceIn(0.45f, 1.0f))
        }
    }

    fun updateViewportSizeDp(widthDp: Float, heightDp: Float) {
        webViewRegistry.updateViewportSizeDp(widthDp, heightDp)
    }

    fun setMockLocationEnabled(enabled: Boolean) {
        updateSettings(reloadActive = true) { it.copy(mockLocationEnabled = enabled) }
    }

    fun updateMockCoordinates(latitude: Double, longitude: Double) {
        updateSettings(reloadActive = false) {
            it.copy(
                mockLatitude = latitude.coerceIn(-90.0, 90.0),
                mockLongitude = longitude.coerceIn(-180.0, 180.0)
            )
        }
    }

    fun selectCityPreset(preset: CityPreset) {
        updateSettings(reloadActive = false) {
            it.copy(
                mockLocationEnabled = true,
                mockLatitude = preset.latitude,
                mockLongitude = preset.longitude
            )
        }
    }

    fun setDisableSoftKeyboard(disabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(disableSoftKeyboard = disabled) }
    }

    fun setForceDarkWebPages(enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(forceDarkWebPages = enabled) }
    }

    fun setBlockThirdPartyCookies(blocked: Boolean) {
        updateSettings(reloadActive = true) { it.copy(blockThirdPartyCookies = blocked) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        updateSettings(reloadActive = true) { it.copy(javaScriptEnabled = enabled) }
    }

    fun applyProxyConfiguration(
        enabled: Boolean,
        scheme: ProxyScheme,
        host: String,
        port: String,
        onFeedback: (String) -> Unit
    ) {
        val cleanHost = host.trim()
        val cleanPort = port.trim()
        if (enabled) {
            val portNum = cleanPort.toIntOrNull()
            if (cleanHost.isEmpty() || portNum == null || portNum !in 1..65535) {
                onFeedback("Invalid proxy host or port.")
                return
            }
        }
        viewModelScope.launch {
            settingsStore.update {
                it.copy(
                    proxyEnabled = enabled,
                    proxyScheme = scheme,
                    proxyHost = cleanHost,
                    proxyPort = cleanPort
                )
            }
            val latest = uiState.value.settings.copy(
                proxyEnabled = enabled,
                proxyScheme = scheme,
                proxyHost = cleanHost,
                proxyPort = cleanPort
            )
            NetworkAndProxyHelper.applyProxySettings(latest) { success, msg ->
                if (!success && msg != null) {
                    onFeedback(msg)
                } else if (enabled && msg != null) {
                    onFeedback(msg)
                } else if (!enabled) {
                    onFeedback("Proxy override cleared")
                }
            }
        }
    }

    fun setStartPageEnabled(enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(startPageEnabled = enabled) }
    }

    fun setCustomHomeUrl(rawUrl: String) {
        val resolved = UrlResolver.resolveInput(rawUrl) ?: BrowserConfig.DEFAULT_HOME_URL
        updateSettings(reloadActive = false) { it.copy(customHomeUrl = resolved) }
        showStatusMessage("Custom home page set to ${UrlResolver.extractDisplayHost(resolved)}")
    }

    fun setRestoreLastPageOnStartup(enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(restoreLastPageOnStartup = enabled) }
    }

    fun setAutoCheckUpdatesOnStartup(enabled: Boolean) {
        updateSettings(reloadActive = false) { it.copy(autoCheckUpdatesOnStartup = enabled) }
    }

    // --- GitHub Releases In-App Updater ---

    private fun normalizedLocalVersion(): String {
        return SemVer.extractFrom(localVersionName)?.normalized
            ?: localVersionName.trim().removePrefix("v").removePrefix("V")
    }

    fun checkForUpdates(userInitiated: Boolean = true) {
        val checker = appUpdateChecker ?: GitHubAppUpdaterRepository { localVersionName }
        val currentState = _featureData.value.appUpdateState
        if (currentState is AppUpdateUiState.Checking || currentState is AppUpdateUiState.Downloading) {
            if (userInitiated && currentState is AppUpdateUiState.Downloading) {
                _overlays.update { it.copy(isUpdateDialogVisible = true) }
            }
            return
        }

        val cleanLocal = normalizedLocalVersion()
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _featureData.update { it.copy(appUpdateState = AppUpdateUiState.Checking) }
            val result = checker.fetchLatestRelease()
            result.fold(
                onSuccess = { release ->
                    val isNewer = SemVer.isRemoteNewer(release.versionName, cleanLocal)
                    if (isNewer) {
                        _featureData.update {
                            it.copy(
                                appUpdateState = AppUpdateUiState.Available(
                                    release = release,
                                    localVersion = cleanLocal
                                )
                            )
                        }
                        _overlays.update { it.copy(isUpdateDialogVisible = true) }
                    } else {
                        _featureData.update {
                            it.copy(appUpdateState = AppUpdateUiState.UpToDate(cleanLocal))
                        }
                        if (userInitiated) {
                            showStatusMessage("You are on the latest version (v$cleanLocal)")
                        }
                    }
                },
                onFailure = { err ->
                    if (userInitiated) {
                        val msg = err.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "Unable to check GitHub Releases for updates"
                        _featureData.update {
                            it.copy(
                                appUpdateState = AppUpdateUiState.Error(
                                    message = msg,
                                    localVersion = cleanLocal
                                )
                            )
                        }
                        showStatusMessage(msg)
                    } else {
                        _featureData.update { it.copy(appUpdateState = AppUpdateUiState.Idle) }
                    }
                }
            )
        }
    }

    fun openUpdateDialog() {
        _overlays.update { it.copy(isUpdateDialogVisible = true) }
    }

    fun dismissUpdateDialog() {
        _overlays.update { it.copy(isUpdateDialogVisible = false) }
    }

    fun startAppUpdateDownload(context: Context) {
        val checker = appUpdateChecker ?: GitHubAppUpdaterRepository { localVersionName }
        val current = _featureData.value.appUpdateState
        val cleanLocal = normalizedLocalVersion()

        if (current is AppUpdateUiState.ReadyToInstall) {
            val existingFile = File(current.apkFilePath)
            if (existingFile.exists() && existingFile.length() > 0L) {
                launchApkInstaller(context, checker, existingFile, current.release, current.localVersion)
                return
            }
        }

        val release = when (current) {
            is AppUpdateUiState.Available -> current.release
            is AppUpdateUiState.Error -> current.release
            is AppUpdateUiState.ReadyToInstall -> current.release
            else -> null
        } ?: return

        val asset = release.apkAsset
        if (asset == null) {
            _featureData.update {
                it.copy(
                    appUpdateState = AppUpdateUiState.Error(
                        message = "No APK installer attached to release ${release.tagName}. Use Open Release Page instead.",
                        release = release,
                        localVersion = cleanLocal
                    )
                )
            }
            return
        }

        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _featureData.update {
                it.copy(
                    appUpdateState = AppUpdateUiState.Downloading(
                        release = release,
                        localVersion = cleanLocal,
                        progressPercent = 0
                    )
                )
            }
            val downloadResult = checker.downloadApkToCache(context.applicationContext, asset) { pct ->
                _featureData.update { state ->
                    val existing = state.appUpdateState
                    if (existing is AppUpdateUiState.Downloading) {
                        state.copy(appUpdateState = existing.copy(progressPercent = pct))
                    } else {
                        state
                    }
                }
            }
            downloadResult.fold(
                onSuccess = { apkFile ->
                    launchApkInstaller(context, checker, apkFile, release, cleanLocal)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Failed to download APK update."
                    _featureData.update {
                        it.copy(
                            appUpdateState = AppUpdateUiState.Error(
                                message = msg,
                                release = release,
                                localVersion = cleanLocal
                            )
                        )
                    }
                }
            )
        }
    }

    private fun launchApkInstaller(
        context: Context,
        checker: AppUpdateChecker,
        apkFile: File,
        release: GitHubReleaseInfo,
        localVer: String
    ) {
        if (!checker.canInstallPackages(context)) {
            _featureData.update {
                it.copy(
                    appUpdateState = AppUpdateUiState.ReadyToInstall(
                        release = release,
                        localVersion = localVer,
                        apkFilePath = apkFile.absolutePath,
                        requiresUnknownSourcesPermission = true
                    )
                )
            }
            _pendingInstallPermissionRequest.value = true
            return
        }

        _featureData.update {
            it.copy(
                appUpdateState = AppUpdateUiState.ReadyToInstall(
                    release = release,
                    localVersion = localVer,
                    apkFilePath = apkFile.absolutePath,
                    requiresUnknownSourcesPermission = false
                )
            )
        }

        runCatching {
            val intent = checker.buildInstallIntent(context, apkFile)
            context.startActivity(intent)
        }.onFailure { err ->
            _featureData.update {
                it.copy(
                    appUpdateState = AppUpdateUiState.Error(
                        message = err.localizedMessage ?: "Unable to launch package installer.",
                        release = release,
                        localVersion = localVer
                    )
                )
            }
        }
    }

    fun onInstallPermissionResult(context: Context) {
        _pendingInstallPermissionRequest.value = false
        val checker = appUpdateChecker ?: GitHubAppUpdaterRepository { localVersionName }
        val current = _featureData.value.appUpdateState
        if (current is AppUpdateUiState.ReadyToInstall) {
            if (checker.canInstallPackages(context)) {
                launchApkInstaller(
                    context = context,
                    checker = checker,
                    apkFile = File(current.apkFilePath),
                    release = current.release,
                    localVer = current.localVersion
                )
            } else {
                showStatusMessage("Allow installing unknown apps in Settings, then tap Install APK.")
            }
        }
    }

    fun clearAllBrowsingData(message: String) {
        webViewRegistry.clearAllBrowsingData()
        viewModelScope.launch {
            historyStore.clearAll()
            settingsStore.clearSessionState()
        }
        showStatusMessage(message)
    }

    private fun updateSettings(
        reloadActive: Boolean,
        transform: (BrowserSettings) -> BrowserSettings
    ) {
        viewModelScope.launch {
            var latestSettings: BrowserSettings? = null
            settingsStore.update { current ->
                val next = transform(current)
                latestSettings = next
                next
            }
            latestSettings?.let { updated ->
                val tabModes = _tabs.value.associate { it.id to it.isDesktopMode }
                webViewRegistry.applySettingsToAll(
                    settings = updated,
                    tabModes = tabModes,
                    reloadActiveTabId = if (reloadActive) _activeTabId.value else null
                )
            }
        }
    }

    fun resolveGeolocationPermission(granted: Boolean) {
        val pending = _pendingGeolocation.value ?: return
        pending.callback.invoke(pending.origin, granted, false)
        _pendingGeolocation.value = null
    }

    fun showStatusMessage(message: String) {
        _overlays.update { it.copy(statusMessage = message) }
    }

    fun clearStatusMessage() {
        _overlays.update { it.copy(statusMessage = null) }
    }

    // --- WebViewCallbacks ---

    override fun onPageStarted(tabId: String, url: String) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) tab.copy(url = url, isLoading = true, progress = 10) else tab
            }
        }
    }

    override fun onPageFinished(
        tabId: String,
        url: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        var resolvedForHistory: String? = null
        var titleForHistory = ""
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    val nextUrl = url.ifBlank { tab.url }
                    if (!tab.isIncognito && !UrlResolver.isInternalPage(nextUrl)) {
                        resolvedForHistory = nextUrl
                        titleForHistory = tab.title
                    }
                    tab.copy(
                        url = nextUrl,
                        isLoading = false,
                        progress = 100,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
        }
        val historyUrl = resolvedForHistory
        if (historyUrl != null) {
            viewModelScope.launch {
                historyStore.recordVisit(title = titleForHistory, url = historyUrl)
            }
        }
        persistSessionState()
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) tab.copy(progress = progress, isLoading = progress in 1..99) else tab
            }
        }
    }

    override fun onReceivedTitle(tabId: String, title: String) {
        var urlToUpdate: String? = null
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    if (!tab.isIncognito && !UrlResolver.isInternalPage(tab.url)) {
                        urlToUpdate = tab.url
                    }
                    tab.copy(title = title)
                } else {
                    tab
                }
            }
        }
        val historyUrl = urlToUpdate
        if (historyUrl != null) {
            viewModelScope.launch {
                historyStore.updateTitleIfPresent(url = historyUrl, title = title)
            }
        }
        persistSessionState()
    }

    override fun onReceivedIcon(tabId: String, icon: Bitmap?) {
        if (icon == null) return
        _tabs.update { list ->
            list.map { tab -> if (tab.id == tabId) tab.copy(favicon = icon) else tab }
        }
    }

    override fun onHistoryUpdated(
        tabId: String,
        url: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url.ifBlank { tab.url },
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
        }
    }

    override fun onOpenNewTabRequested(url: String) {
        val currentIncognito = uiState.value.activeTab?.isIncognito == true
        openNewTab(url = url, select = true, isIncognito = currentIncognito)
    }

    override fun onAdBlocked(tabId: String, blockedUrl: String) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) tab.copy(blockedAdsCount = tab.blockedAdsCount + 1) else tab
            }
        }
    }

    override fun onConsoleMessage(tabId: String, entry: ConsoleLogEntry) {
        if (tabId == _activeTabId.value) {
            appendConsoleEntry(entry)
        }
    }

    override fun onDownloadRequested(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        _pendingDownload.value = PendingDownloadEvent(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimeType
        )
    }

    override fun onGeolocationPermissionRequested(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        _pendingGeolocation.value = PendingGeolocationPrompt(origin, callback)
    }

    override fun onCleared() {
        super.onCleared()
        webViewRegistry.destroyAll()
    }

    private data class CoreCollections(
        val bookmarks: List<BookmarkEntity>,
        val history: List<HistoryEntity>,
        val downloads: List<DownloadEntity>,
        val passwords: List<DecryptedPasswordEntry>,
        val clips: List<NoteClipEntity>,
        val pwas: List<SavedPwaEntity>
    )

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val db = BrowserDatabase.getInstance(appCtx)
            return BrowserViewModel(
                bookmarkRepository = BookmarkRepository(db.bookmarkDao()),
                settingsStore = BrowserSettingsRepository(appCtx),
                historyStore = HistoryRepository(db.historyDao()),
                downloadStore = DownloadRepository(db.downloadDao()),
                passwordStore = PasswordRepository(db.passwordDao()),
                noteClipStore = NoteClipRepository(db.noteClipDao()),
                pwaStore = SavedPwaRepository(db.savedPwaDao()),
                appUpdateChecker = GitHubAppUpdaterRepository()
            ) as T
        }
    }
}
