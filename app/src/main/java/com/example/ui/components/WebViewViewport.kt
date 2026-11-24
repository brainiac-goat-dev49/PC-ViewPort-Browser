package com.example.ui.components

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.browser.BrowserTabState
import com.example.browser.UrlResolver
import com.example.data.BookmarkEntity
import com.example.data.HistoryEntity
import com.example.data.SavedPwaEntity

@Composable
fun WebViewViewport(
    activeTab: BrowserTabState?,
    tabs: List<BrowserTabState>,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    savedPwas: List<SavedPwaEntity>,
    aiEnabled: Boolean,
    getOrCreateWebView: (Context, BrowserTabState) -> WebView,
    onNavigate: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAiAnalyst: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenSettings: () -> Unit,
    onReleaseContext: (String) -> Unit = {},
    onViewportSizeMeasured: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val latestTabId by rememberUpdatedState(activeTab?.id)
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            latestTabId?.let(onReleaseContext)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val nativeDensity = context.resources.displayMetrics.density.coerceAtLeast(1f)
                onViewportSizeMeasured(
                    size.width / nativeDensity,
                    size.height / nativeDensity
                )
            }
    ) {
        if (activeTab != null) {
            if (UrlResolver.isStartPage(activeTab.url)) {
                StartPage(
                    isIncognito = activeTab.isIncognito,
                    bookmarks = bookmarks,
                    history = history,
                    savedPwas = savedPwas,
                    tabs = tabs,
                    aiEnabled = aiEnabled,
                    onNavigate = onNavigate,
                    onSelectTab = onSelectTab,
                    onOpenHistory = onOpenHistory,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenDownloads = onOpenDownloads,
                    onOpenAiAnalyst = onOpenAiAnalyst,
                    onOpenNews = onOpenNews,
                    onOpenQuickActions = onOpenQuickActions,
                    onOpenSettings = onOpenSettings
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { container ->
                        val targetWebView = getOrCreateWebView(container.context, activeTab)
                        val currentChild = if (container.childCount > 0) container.getChildAt(0) else null
                        if (currentChild !== targetWebView) {
                            container.removeAllViews()
                            (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                            container.addView(
                                targetWebView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    onRelease = { container ->
                        container.removeAllViews()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("webview_viewport")
                )

                AnimatedVisibility(
                    visible = activeTab.isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    val progressFraction = (activeTab.progress.coerceIn(5, 100)) / 100f
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .testTag("webview_progress_bar")
                    )
                }
            }
        }
    }
}
