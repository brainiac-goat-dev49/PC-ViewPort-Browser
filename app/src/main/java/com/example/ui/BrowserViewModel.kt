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
import com.example.data.NoteClipEntity
import com.example.data.NoteClipRepository
import com.example.data.NoteClipStore
import com.example.data.PasswordRepository
import com.example.data.PasswordStore
import com.example.data.ProxyScheme
import com.example.data.PwaStore
import com.example.data.SavedPwaEntity
import com.example.data.SavedPwaRepository
import com.example.data.SettingsStore
import com.example.news.DailyNewsRepository
import com.example.news.NewsCategory
import com.example.news.NewsFeedSource
import com.example.system.FloatingBubbleService
import com.example.system.PwaShortcutHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

private class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(BrowserSettings())
    override val settings: Flow<BrowserSettings> = state
    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        state.update(transform)
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
    val statusMessage: String? = null
)

private data class FeatureDataState(
    val aiAnalysisState: AiAnalysisUiState = AiAnalysisUiState.Idle,
    val aiSelectedMode: AiAnalysisMode = AiAnalysisMode.SUMMARY,
    val newsState: NewsUiState = NewsUiState(),
    val mediaState: ExtractedMediaState = ExtractedMediaState(),
    val screenshotBitmap: Bitmap? = null,
    val imageViewerState: ImageViewerState? = null
)

class BrowserViewModel(
    private val bookmarkRepository: BookmarkRepository,
    private val settingsStore: SettingsStore = InMemorySettingsStore(),
    private val downloadStore: DownloadStore = InMemoryDownloadStore(),
    private val passwordStore: PasswordStore = InMemoryPasswordStore(),
    private val noteClipStore: NoteClipStore = InMemoryNoteClipStore(),
    private val pwaStore: PwaStore = InMemoryPwaStore(),
    private val newsFeedSource: NewsFeedSource = DailyNewsRepository(),
    private val webAnalyzer: WebAnalyzer = AiWebAnalyzerRepository(),
    val webViewRegistry: WebViewRegistry = WebViewRegistry()
) : ViewModel(), WebViewCallbacks {

    private val initialTab = BrowserTabState(
        title = "Google",
        url = BrowserConfig.DEFAULT_HOME_URL,
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

    private var aiJob: Job? = null

    private val coreDataFlow = combine(
        bookmarkRepository.bookmarks,
        downloadStore.downloads,
        passwordStore.entries,
        noteClipStore.clips,
        pwaStore.pwas
    ) { bookmarks, downloads, passwords, clips, pwas ->
        CoreCollections(bookmarks, downloads, passwords, clips, pwas)
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
        webViewRegistry.updateSettingsSnapshot(settings)
        BrowserUiState(
            tabs = tabs,
            activeTabId = activeId,
            activeGroupFilter = overlays.activeGroupFilter,
            bookmarks = core.bookmarks,
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
            settingsStore.settings.collect { settings ->
                val tabModes = _tabs.value.associate { it.id to it.isDesktopMode }
                webViewRegistry.applySettingsToAll(settings, tabModes)
                if (settings.proxyEnabled && settings.proxyHost.isNotBlank()) {
                    NetworkAndProxyHelper.applyProxySettings(settings) { _, _ -> }
                }
            }
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
        url: String = BrowserConfig.DEFAULT_HOME_URL,
        select: Boolean = true,
        isIncognito: Boolean = false,
        groupName: String? = null,
        groupColorHex: Long = 0xFF38BDF8L
    ) {
        val resolved = UrlResolver.resolveInput(url) ?: BrowserConfig.DEFAULT_HOME_URL
        val hostTitle = if (isIncognito) {
            "Incognito"
        } else {
            UrlResolver.extractDisplayHost(resolved).ifBlank { "New Tab" }
        }
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
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val index = currentTabs.indexOfFirst { it.id == tabId }
        if (index == -1) return
        if (currentTabs[index].isLocked) {
            showStatusMessage("Tab is locked. Unlock it from Tab Options before closing.")
            return
        }

        webViewRegistry.destroy(tabId)

        if (currentTabs.size == 1) {
            val replacement = BrowserTabState(
                title = "Google",
                url = BrowserConfig.DEFAULT_HOME_URL,
                isDesktopMode = true
            )
            _tabs.value = listOf(replacement)
            _activeTabId.value = replacement.id
            return
        }

        val updatedTabs = currentTabs.filterNot { it.id == tabId }
        _tabs.value = updatedTabs

        if (_activeTabId.value == tabId) {
            val nextIndex = index.coerceAtMost(updatedTabs.lastIndex)
            _activeTabId.value = updatedTabs[nextIndex].id
        }
    }

    fun toggleTabLock(tabId: String) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(isLocked = !it.isLocked) else it }
        }
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
        val tabId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(url = resolvedUrl, isLoading = true, progress = 10)
                } else {
                    tab
                }
            }
        }
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE) }
        webViewRegistry.loadUrl(tabId, resolvedUrl)
    }

    fun goBack(): Boolean = webViewRegistry.goBack(_activeTabId.value)

    fun goForward(): Boolean = webViewRegistry.goForward(_activeTabId.value)

    fun refreshOrStop() {
        val active = uiState.value.activeTab ?: return
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
        navigateTo(BrowserConfig.DEFAULT_HOME_URL)
    }

    fun toggleDesktopMode(): Boolean {
        val active = uiState.value.activeTab ?: return true
        val nextMode = !active.isDesktopMode
        _tabs.update { list ->
            list.map { if (it.id == active.id) it.copy(isDesktopMode = nextMode) else it }
        }
        webViewRegistry.applyDesktopMode(active.id, nextMode, reload = true)
        return nextMode
    }

    // --- Full-Page Navigation Helpers ---

    fun closeActiveToolPage() {
        _overlays.update { it.copy(activeToolPage = ActiveToolPage.NONE) }
    }

    fun openBookmarksSheet() = openToolPage(ActiveToolPage.BOOKMARKS)
    fun closeBookmarksSheet() = closeActiveToolPage()
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

        webViewRegistry.evaluateRawJs(tab.id, BrowserScripts.EXTRACT_PAGE_CONTENT_SCRIPT) { raw ->
            val snapshot = parsePageSnapshot(raw, tab)
            aiJob?.cancel()
            aiJob = viewModelScope.launch {
                webAnalyzer.analyzePageStream(snapshot, mode, customQuestion).collect { state ->
                    _featureData.update { it.copy(aiAnalysisState = state) }
                }
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
        } catch (_: Exception) {
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

    // --- Bookmarks ---

    fun toggleBookmarkForCurrentPage(onResult: (Boolean) -> Unit = {}) {
        val active = uiState.value.activeTab ?: return
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

    fun clearAllBrowsingData(message: String) {
        webViewRegistry.clearAllBrowsingData()
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
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url.ifBlank { tab.url },
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
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) tab.copy(progress = progress, isLoading = progress in 1..99) else tab
            }
        }
    }

    override fun onReceivedTitle(tabId: String, title: String) {
        _tabs.update { list ->
            list.map { tab -> if (tab.id == tabId) tab.copy(title = title) else tab }
        }
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
                downloadStore = DownloadRepository(db.downloadDao()),
                passwordStore = PasswordRepository(db.passwordDao()),
                noteClipStore = NoteClipRepository(db.noteClipDao()),
                pwaStore = SavedPwaRepository(db.savedPwaDao())
            ) as T
        }
    }
}
