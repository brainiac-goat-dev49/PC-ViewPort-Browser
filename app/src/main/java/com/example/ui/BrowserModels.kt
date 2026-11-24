package com.example.ui

import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import com.example.ai.AiAnalysisMode
import com.example.ai.AiAnalysisUiState
import com.example.browser.BrowserConfig
import com.example.browser.BrowserTabState
import com.example.browser.ConsoleLogEntry
import com.example.browser.UrlResolver
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.DecryptedPasswordEntry
import com.example.data.DownloadEntity
import com.example.data.HistoryEntity
import com.example.data.NoteClipEntity
import com.example.data.SavedPwaEntity
import com.example.news.NewsArticle
import com.example.news.NewsCategory
import com.example.system.GitHubReleaseInfo

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class UpToDate(val localVersion: String) : AppUpdateUiState
    data class Available(
        val release: GitHubReleaseInfo,
        val localVersion: String
    ) : AppUpdateUiState
    data class Downloading(
        val release: GitHubReleaseInfo,
        val localVersion: String,
        val progressPercent: Int
    ) : AppUpdateUiState
    data class ReadyToInstall(
        val release: GitHubReleaseInfo,
        val localVersion: String,
        val apkFilePath: String,
        val requiresUnknownSourcesPermission: Boolean = false
    ) : AppUpdateUiState
    data class Error(
        val message: String,
        val release: GitHubReleaseInfo? = null,
        val localVersion: String = ""
    ) : AppUpdateUiState
}

enum class ActiveToolPage {
    NONE,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    SETTINGS,
    QUICK_ACTIONS,
    AI_ANALYST,
    DAILY_NEWS,
    PASSWORDS,
    NOTES_CLIPS,
    MEDIA_DOWNLOADER,
    SCREENSHOT_EDITOR,
    IMAGE_VIEWER
}

enum class SuggestionSource {
    OPEN_TAB,
    BOOKMARK,
    SAVED_PWA,
    HISTORY,
    URL_DIRECT,
    WEB_SEARCH
}

data class OmniboxSuggestion(
    val title: String,
    val url: String,
    val source: SuggestionSource,
    val subtitle: String = url,
    val tabId: String? = null
)

object OmniboxSuggestionEngine {

    private fun normalizeUrlKey(url: String): String {
        return url.trim().lowercase().removeSuffix("/")
    }

    fun buildSuggestions(
        query: String,
        history: List<HistoryEntity>,
        bookmarks: List<BookmarkEntity>,
        savedPwas: List<SavedPwaEntity>,
        tabs: List<BrowserTabState>,
        maxResults: Int = 8
    ): List<OmniboxSuggestion> {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()
        val seenUrls = mutableSetOf<String>()
        val results = mutableListOf<OmniboxSuggestion>()

        fun tryAdd(suggestion: OmniboxSuggestion) {
            if (results.size >= maxResults) return
            val key = normalizeUrlKey(suggestion.url)
            if (seenUrls.add(key)) {
                results.add(suggestion)
            }
        }

        if (trimmed.isEmpty()) {
            bookmarks.take(4).forEach { bookmark ->
                tryAdd(
                    OmniboxSuggestion(
                        title = bookmark.title.ifBlank { UrlResolver.extractDisplayHost(bookmark.url) },
                        url = bookmark.url,
                        source = SuggestionSource.BOOKMARK,
                        subtitle = bookmark.url
                    )
                )
            }
            history.take(maxResults).forEach { item ->
                tryAdd(
                    OmniboxSuggestion(
                        title = item.title.ifBlank { UrlResolver.extractDisplayHost(item.url) },
                        url = item.url,
                        source = SuggestionSource.HISTORY,
                        subtitle = item.url
                    )
                )
            }
            return results
        }

        val resolvedDirect = UrlResolver.resolveInput(trimmed)
        val isUrlLike = UrlResolver.looksLikeUrlOrDomain(trimmed)

        if (isUrlLike && resolvedDirect != null) {
            tryAdd(
                OmniboxSuggestion(
                    title = trimmed,
                    url = resolvedDirect,
                    source = SuggestionSource.URL_DIRECT,
                    subtitle = "Go to $resolvedDirect"
                )
            )
        }

        // Open tabs matching query (skip internal start page unless explicitly queried)
        tabs.filter { tab ->
            !UrlResolver.isInternalPage(tab.url) &&
                (tab.title.lowercase().contains(lower) || tab.url.lowercase().contains(lower))
        }.take(2).forEach { tab ->
            tryAdd(
                OmniboxSuggestion(
                    title = tab.title.ifBlank { UrlResolver.extractDisplayHost(tab.url) },
                    url = tab.url,
                    source = SuggestionSource.OPEN_TAB,
                    subtitle = "Switch to open tab • ${tab.url}",
                    tabId = tab.id
                )
            )
        }

        // Matching bookmarks
        bookmarks.filter {
            it.title.lowercase().contains(lower) || it.url.lowercase().contains(lower)
        }.sortedByDescending {
            val t = it.title.lowercase()
            val u = it.url.lowercase()
            when {
                t.startsWith(lower) -> 3
                u.contains("://$lower") || u.contains("www.$lower") -> 2
                else -> 1
            }
        }.take(3).forEach { bookmark ->
            tryAdd(
                OmniboxSuggestion(
                    title = bookmark.title.ifBlank { UrlResolver.extractDisplayHost(bookmark.url) },
                    url = bookmark.url,
                    source = SuggestionSource.BOOKMARK,
                    subtitle = bookmark.url
                )
            )
        }

        // Matching saved PWAs
        savedPwas.filter {
            it.name.lowercase().contains(lower) || it.startUrl.lowercase().contains(lower)
        }.take(2).forEach { pwa ->
            tryAdd(
                OmniboxSuggestion(
                    title = pwa.name,
                    url = pwa.startUrl,
                    source = SuggestionSource.SAVED_PWA,
                    subtitle = "Installed Web App • ${pwa.startUrl}"
                )
            )
        }

        // Matching history entries ranked by prefix relevance, visit count, and recency
        history.filter {
            it.title.lowercase().contains(lower) || it.url.lowercase().contains(lower)
        }.sortedWith(
            compareByDescending<HistoryEntity> { item ->
                val t = item.title.lowercase()
                val u = item.url.lowercase()
                when {
                    t.startsWith(lower) -> 3
                    u.contains("://$lower") || u.contains("www.$lower") -> 2
                    else -> 1
                }
            }.thenByDescending { it.visitCount }
                .thenByDescending { it.visitedAt }
        ).take(4).forEach { item ->
            tryAdd(
                OmniboxSuggestion(
                    title = item.title.ifBlank { UrlResolver.extractDisplayHost(item.url) },
                    url = item.url,
                    source = SuggestionSource.HISTORY,
                    subtitle = item.url
                )
            )
        }

        // Always offer Google Search for non-URL queries (or as secondary fallback)
        if (!UrlResolver.isInternalPage(trimmed)) {
            val searchUrl = if (isUrlLike) {
                "${BrowserConfig.GOOGLE_SEARCH_PREFIX}${java.net.URLEncoder.encode(trimmed, java.nio.charset.StandardCharsets.UTF_8.name())}"
            } else {
                resolvedDirect ?: "${BrowserConfig.GOOGLE_SEARCH_PREFIX}$trimmed"
            }
            if (results.size < maxResults) {
                results.add(
                    OmniboxSuggestion(
                        title = "Search Google for \"$trimmed\"",
                        url = searchUrl,
                        source = SuggestionSource.WEB_SEARCH,
                        subtitle = searchUrl
                    )
                )
            }
        }

        return results.take(maxResults)
    }
}

enum class MediaKind { VIDEO, IMAGE, AUDIO }

data class ExtractedMediaItem(
    val url: String,
    val label: String,
    val kind: MediaKind
)

data class ExtractedMediaState(
    val isScanning: Boolean = false,
    val videos: List<ExtractedMediaItem> = emptyList(),
    val images: List<ExtractedMediaItem> = emptyList(),
    val audio: List<ExtractedMediaItem> = emptyList()
)

data class NewsUiState(
    val category: NewsCategory = NewsCategory.TOP,
    val isLoading: Boolean = false,
    val articles: List<NewsArticle> = emptyList(),
    val errorMessage: String? = null
)

data class ImageViewerState(
    val title: String = "",
    val imageUrl: String? = null,
    val bitmap: Bitmap? = null
)

data class BookmarkDialogState(
    val id: Long = 0L,
    val title: String,
    val url: String
)

data class PendingGeolocationPrompt(
    val origin: String,
    val callback: GeolocationPermissions.Callback
)

data class PendingDownloadEvent(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String
)

data class TourStepInfo(
    val title: String,
    val description: String,
    val featureBadge: String
)

object BrowserTourSteps {
    val steps = listOf(
        TourStepInfo(
            title = "True Desktop Viewport & UI Scale",
            description = "Websites render at desktop CSS pixels (1280px default) while the browser toolbar, tabs, and tools scale cleanly to desktop proportions.",
            featureBadge = "Desktop Engine"
        ),
        TourStepInfo(
            title = "Tab Grouping, Locking & Incognito",
            description = "Long-press any tab or use Tab Options to group tabs by color, lock important tabs against accidental close, or launch isolated Incognito tabs.",
            featureBadge = "Multi-Tab Pro"
        ),
        TourStepInfo(
            title = "AI Web Analysing (Gemini 3.8 Flash)",
            description = "Tap the Sparkle AI button in the toolbar to summarize pages, extract takeaways, or ask custom questions about the active tab (toggleable in Settings).",
            featureBadge = "AI Analyst"
        ),
        TourStepInfo(
            title = "Media Extractor, Screenshot Editor & Image Viewer",
            description = "Scan any page for downloadable Videos, Images, and Audio, capture & annotate screenshots, or pinch-zoom inspect images full-screen.",
            featureBadge = "Media & Capture"
        ),
        TourStepInfo(
            title = "Password Vault, Notes & Clips, & Daily News",
            description = "Open Website Quick Actions to autofill encrypted logins, clip selected web text to Notes, install PWAs, or browse live Daily News feeds.",
            featureBadge = "Productivity"
        ),
        TourStepInfo(
            title = "Floating Bubble & Home Screen Widget",
            description = "Enable the draggable Floating Bubble in Settings or add the PC ViewPort Home Widget for instant Incognito, Search, AI, and News outside the app.",
            featureBadge = "Outside App"
        )
    )
}

data class BrowserUiState(
    val tabs: List<BrowserTabState> = emptyList(),
    val activeTabId: String = "",
    val activeGroupFilter: String? = null,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val passwords: List<DecryptedPasswordEntry> = emptyList(),
    val noteClips: List<NoteClipEntity> = emptyList(),
    val savedPwas: List<SavedPwaEntity> = emptyList(),
    val settings: BrowserSettings = BrowserSettings(),
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    val activeToolPage: ActiveToolPage = ActiveToolPage.NONE,
    val isDevToolsOpen: Boolean = false,
    val isFindInPageOpen: Boolean = false,
    val findInPageQuery: String = "",
    val isSystemVpnActive: Boolean = false,
    val aiAnalysisState: AiAnalysisUiState = AiAnalysisUiState.Idle,
    val aiSelectedMode: AiAnalysisMode = AiAnalysisMode.SUMMARY,
    val newsState: NewsUiState = NewsUiState(),
    val mediaState: ExtractedMediaState = ExtractedMediaState(),
    val screenshotBitmap: Bitmap? = null,
    val imageViewerState: ImageViewerState? = null,
    val tabOptionsTargetId: String? = null,
    val tourStepIndex: Int? = null,
    val bookmarkDialogState: BookmarkDialogState? = null,
    val appUpdateState: AppUpdateUiState = AppUpdateUiState.Idle,
    val isUpdateDialogVisible: Boolean = false,
    val statusMessage: String? = null
) {
    val activeTab: BrowserTabState?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    val isCurrentPageBookmarked: Boolean
        get() {
            val currentUrl = activeTab?.url ?: return false
            if (UrlResolver.isInternalPage(currentUrl)) return false
            return bookmarks.any { it.url == currentUrl }
        }

    val totalBlockedAds: Int
        get() = tabs.sumOf { it.blockedAdsCount }

    val isBookmarksSheetOpen: Boolean get() = activeToolPage == ActiveToolPage.BOOKMARKS
    val isHistorySheetOpen: Boolean get() = activeToolPage == ActiveToolPage.HISTORY
    val isDownloadsSheetOpen: Boolean get() = activeToolPage == ActiveToolPage.DOWNLOADS
    val isSettingsSheetOpen: Boolean get() = activeToolPage == ActiveToolPage.SETTINGS
}
