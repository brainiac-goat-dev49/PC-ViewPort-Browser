package com.example.ui

import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import com.example.ai.AiAnalysisMode
import com.example.ai.AiAnalysisUiState
import com.example.browser.BrowserTabState
import com.example.browser.ConsoleLogEntry
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.DecryptedPasswordEntry
import com.example.data.DownloadEntity
import com.example.data.NoteClipEntity
import com.example.data.SavedPwaEntity
import com.example.news.NewsArticle
import com.example.news.NewsCategory

enum class ActiveToolPage {
    NONE,
    BOOKMARKS,
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
    val statusMessage: String? = null
) {
    val activeTab: BrowserTabState?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    val isCurrentPageBookmarked: Boolean
        get() {
            val currentUrl = activeTab?.url ?: return false
            return bookmarks.any { it.url == currentUrl }
        }

    val totalBlockedAds: Int
        get() = tabs.sumOf { it.blockedAdsCount }

    val isBookmarksSheetOpen: Boolean get() = activeToolPage == ActiveToolPage.BOOKMARKS
    val isDownloadsSheetOpen: Boolean get() = activeToolPage == ActiveToolPage.DOWNLOADS
    val isSettingsSheetOpen: Boolean get() = activeToolPage == ActiveToolPage.SETTINGS
}
