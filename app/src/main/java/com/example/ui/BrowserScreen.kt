package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.browser.UrlResolver
import com.example.browser.WebViewRegistry
import com.example.system.ScreenshotStorageHelper
import com.example.ui.components.AiAnalystPage
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.BookmarkEditDialog
import com.example.ui.components.BookmarksPage
import com.example.ui.components.BrowserTabStrip
import com.example.ui.components.DesktopToolbar
import com.example.ui.components.DevToolsConsoleDrawer
import com.example.ui.components.DownloadsPage
import com.example.ui.components.HistoryPage
import com.example.ui.components.ImageViewerPage
import com.example.ui.components.MediaDownloaderPage
import com.example.ui.components.NewsPage
import com.example.ui.components.NotesClipsPage
import com.example.ui.components.PasswordManagerPage
import com.example.ui.components.QuickActionsPage
import com.example.ui.components.ScreenshotEditorPage
import com.example.ui.components.SettingsPage
import com.example.ui.components.TabOptionsDialog
import com.example.ui.components.TourGuideOverlay
import com.example.ui.components.WebViewViewport

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingGeo by viewModel.pendingGeolocation.collectAsStateWithLifecycle()
    val pendingDownload by viewModel.pendingDownload.collectAsStateWithLifecycle()
    val pendingInstallPermission by viewModel.pendingInstallPermissionRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab = uiState.activeTab
    val anyFullPageToolOpen = uiState.activeToolPage != ActiveToolPage.NONE

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.resolveGeolocationPermission(granted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && Settings.canDrawOverlays(context)) {
            viewModel.setFloatingBubbleEnabled(context, true)
        } else {
            viewModel.showStatusMessage("Notification permission needed to keep floating bubble active")
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setFloatingBubbleEnabled(context, true)
            }
        } else {
            viewModel.showStatusMessage("Overlay permission denied for floating bubble")
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onInstallPermissionResult(context)
    }

    fun handleToggleFloatingBubble(enable: Boolean) {
        if (!enable) {
            viewModel.setFloatingBubbleEnabled(context, false)
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            viewModel.showStatusMessage("Allow 'Display over other apps' to enable the floating bubble")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.setFloatingBubbleEnabled(context, true)
    }

    LaunchedEffect(pendingGeo) {
        if (pendingGeo != null) {
            val fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                viewModel.resolveGeolocationPermission(true)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    LaunchedEffect(pendingDownload) {
        val event = pendingDownload ?: return@LaunchedEffect
        val fileName = viewModel.processSystemDownload(context, event)
        if (fileName != null) {
            viewModel.showStatusMessage(context.getString(R.string.download_started, fileName))
        } else {
            viewModel.showStatusMessage(context.getString(R.string.download_failed))
        }
    }

    LaunchedEffect(pendingInstallPermission) {
        if (pendingInstallPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
                installPermissionLauncher.launch(intent)
            } else {
                viewModel.onInstallPermissionResult(context)
            }
        }
    }

    LaunchedEffect(uiState.settings.disableSoftKeyboard) {
        if (uiState.settings.disableSoftKeyboard) {
            keyboardController?.hide()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            (context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
            }
        }
    }

    BackHandler(enabled = uiState.tourStepIndex != null) {
        viewModel.finishTourGuide(markCompleted = false)
    }

    BackHandler(enabled = uiState.tourStepIndex == null && anyFullPageToolOpen) {
        viewModel.closeActiveToolPage()
    }

    BackHandler(enabled = uiState.tourStepIndex == null && !anyFullPageToolOpen && uiState.isFindInPageOpen) {
        viewModel.closeFindInPage()
    }

    BackHandler(
        enabled = uiState.tourStepIndex == null &&
            !anyFullPageToolOpen &&
            !uiState.isFindInPageOpen &&
            uiState.isDevToolsOpen
    ) {
        viewModel.closeDevTools()
    }

    BackHandler(
        enabled = uiState.tourStepIndex == null &&
            !anyFullPageToolOpen &&
            !uiState.isFindInPageOpen &&
            !uiState.isDevToolsOpen &&
            activeTab?.canGoBack == true
    ) {
        viewModel.goBack()
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearStatusMessage()
        }
    }

    val baseDensity = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val activeDesktopWidthPx = WebViewRegistry.resolveDesktopWidthPx(
            isLandscape = isLandscape,
            settings = uiState.settings
        )
        val effectiveScale = if (minOf(maxWidth, maxHeight) < 600.dp) {
            uiState.settings.desktopUiScale.coerceIn(0.45f, 1.0f)
        } else {
            1.0f
        }
        val scaledDensity = remember(baseDensity, effectiveScale) {
            Density(
                density = baseDensity.density * effectiveScale,
                fontScale = baseDensity.fontScale * effectiveScale
            )
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        BrowserTabStrip(
                            tabs = uiState.tabs,
                            activeTabId = uiState.activeTabId,
                            activeGroupFilter = uiState.activeGroupFilter,
                            showTooltips = uiState.settings.showTooltips,
                            onSelectTab = viewModel::selectTab,
                            onCloseTab = viewModel::closeTab,
                            onNewTab = { viewModel.openNewTab() },
                            onNewIncognitoTab = { viewModel.openNewTab(isIncognito = true) },
                            onOpenTabOptions = viewModel::openTabOptions,
                            onSelectGroupFilter = viewModel::selectGroupFilter
                        )

                        DesktopToolbar(
                            activeTab = activeTab,
                            tabs = uiState.tabs,
                            bookmarks = uiState.bookmarks,
                            history = uiState.history,
                            savedPwas = uiState.savedPwas,
                            isBookmarked = uiState.isCurrentPageBookmarked,
                            bookmarkCount = uiState.bookmarks.size,
                            adBlockerEnabled = uiState.settings.adBlockerEnabled,
                            blockedAdsCount = uiState.totalBlockedAds,
                            desktopViewportWidthPx = activeDesktopWidthPx,
                            isDevToolsOpen = uiState.isDevToolsOpen,
                            disableSoftKeyboard = uiState.settings.disableSoftKeyboard,
                            aiWebAnalyzingEnabled = uiState.settings.aiWebAnalyzingEnabled,
                            showTooltips = uiState.settings.showTooltips,
                            isFindInPageOpen = uiState.isFindInPageOpen,
                            findInPageQuery = uiState.findInPageQuery,
                            onBack = { viewModel.goBack() },
                            onForward = { viewModel.goForward() },
                            onRefreshOrStop = { viewModel.refreshOrStop() },
                            onHome = { viewModel.goHome() },
                            onNavigate = { input -> viewModel.navigateTo(input) },
                            onSelectTab = viewModel::selectTab,
                            onToggleDesktopMode = {
                                val isDesktop = viewModel.toggleDesktopMode()
                                viewModel.showStatusMessage(
                                    context.getString(
                                        if (isDesktop) R.string.mode_desktop_switched
                                        else R.string.mode_mobile_switched
                                    )
                                )
                            },
                            onToggleBookmark = {
                                viewModel.toggleBookmarkForCurrentPage { added ->
                                    viewModel.showStatusMessage(
                                        context.getString(
                                            if (added) R.string.bookmarks_added
                                            else R.string.bookmarks_removed
                                        )
                                    )
                                }
                            },
                            onOpenBookmarks = { viewModel.openBookmarksSheet() },
                            onOpenHistory = { viewModel.openHistoryPage() },
                            onOpenDownloads = { viewModel.openDownloadsSheet() },
                            onToggleDevTools = { viewModel.toggleDevTools() },
                            onOpenAiAnalyst = { viewModel.openAiAnalystPage() },
                            onOpenQuickActions = { viewModel.openQuickActionsPage() },
                            onOpenSettings = { viewModel.openSettingsSheet(context) },
                            onFindQueryChange = viewModel::updateFindQuery,
                            onFindNext = viewModel::findNextMatch,
                            onCloseFindInPage = viewModel::closeFindInPage
                        )

                        WebViewViewport(
                            activeTab = activeTab,
                            tabs = uiState.tabs,
                            bookmarks = uiState.bookmarks,
                            history = uiState.history,
                            savedPwas = uiState.savedPwas,
                            aiEnabled = uiState.settings.aiWebAnalyzingEnabled,
                            getOrCreateWebView = viewModel::getOrCreateWebView,
                            onNavigate = { input -> viewModel.navigateTo(input) },
                            onSelectTab = viewModel::selectTab,
                            onOpenHistory = viewModel::openHistoryPage,
                            onOpenBookmarks = viewModel::openBookmarksSheet,
                            onOpenDownloads = viewModel::openDownloadsSheet,
                            onOpenAiAnalyst = { viewModel.openAiAnalystPage() },
                            onOpenNews = { viewModel.openNewsPage() },
                            onOpenQuickActions = viewModel::openQuickActionsPage,
                            onOpenSettings = { viewModel.openSettingsSheet(context) },
                            onReleaseContext = viewModel::releaseWebViewContext,
                            onViewportSizeMeasured = viewModel::updateViewportSizeDp,
                            modifier = Modifier.weight(1f)
                        )

                        AnimatedVisibility(visible = uiState.isDevToolsOpen) {
                            DevToolsConsoleDrawer(
                                logs = uiState.consoleLogs,
                                disableSoftKeyboard = uiState.settings.disableSoftKeyboard,
                                onRunScript = viewModel::runConsoleScript,
                                onClearLogs = viewModel::clearConsoleLogs,
                                onClose = viewModel::closeDevTools
                            )
                        }
                    }

                    when (uiState.activeToolPage) {
                        ActiveToolPage.NONE -> Unit

                        ActiveToolPage.BOOKMARKS -> BookmarksPage(
                            bookmarks = uiState.bookmarks,
                            canBookmarkCurrentPage = activeTab != null &&
                                activeTab.url.isNotBlank() &&
                                !UrlResolver.isInternalPage(activeTab.url),
                            onBack = viewModel::closeActiveToolPage,
                            onAddCurrentPage = viewModel::openAddBookmarkDialog,
                            onOpenBookmark = viewModel::openBookmark,
                            onEditBookmark = viewModel::openEditBookmarkDialog,
                            onDeleteBookmark = viewModel::deleteBookmark
                        )

                        ActiveToolPage.HISTORY -> HistoryPage(
                            history = uiState.history,
                            onBack = viewModel::closeActiveToolPage,
                            onOpenHistoryItem = viewModel::openHistoryItem,
                            onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
                            onClearAll = viewModel::clearHistory
                        )

                        ActiveToolPage.DOWNLOADS -> DownloadsPage(
                            downloads = uiState.downloads,
                            onBack = viewModel::closeActiveToolPage,
                            onDeleteDownload = viewModel::deleteDownload,
                            onClearAll = viewModel::clearDownloads
                        )

                        ActiveToolPage.SETTINGS -> SettingsPage(
                            settings = uiState.settings,
                            blockedAdsCount = uiState.totalBlockedAds,
                            isSystemVpnActive = uiState.isSystemVpnActive,
                            onBack = viewModel::closeActiveToolPage,
                            onSelectDesktopWidthPx = viewModel::setDesktopViewportWidthPx,
                            onSelectDesktopLandscapeWidthPx = viewModel::setDesktopLandscapeViewportWidthPx,
                            onSelectDesktopUiScale = viewModel::setDesktopUiScale,
                            onToggleAdBlocker = viewModel::setAdBlockerEnabled,
                            onToggleMockLocation = viewModel::setMockLocationEnabled,
                            onUpdateCoordinates = viewModel::updateMockCoordinates,
                            onSelectCityPreset = viewModel::selectCityPreset,
                            onToggleDisableSoftKeyboard = viewModel::setDisableSoftKeyboard,
                            onApplyProxy = { enabled, scheme, host, port ->
                                viewModel.applyProxyConfiguration(enabled, scheme, host, port) { msg ->
                                    viewModel.showStatusMessage(msg)
                                }
                            },
                            onRefreshVpnStatus = { viewModel.refreshVpnStatus(context) },
                            onToggleForceDark = viewModel::setForceDarkWebPages,
                            onToggleBlockThirdPartyCookies = viewModel::setBlockThirdPartyCookies,
                            onToggleJavaScript = viewModel::setJavaScriptEnabled,
                            onToggleAiAnalyzing = viewModel::setAiWebAnalyzingEnabled,
                            onToggleTooltips = viewModel::setShowTooltips,
                            onToggleFloatingBubble = { enabled -> handleToggleFloatingBubble(enabled) },
                            onToggleStartPage = viewModel::setStartPageEnabled,
                            onUpdateCustomHomeUrl = viewModel::setCustomHomeUrl,
                            onToggleRestoreLastPage = viewModel::setRestoreLastPageOnStartup,
                            appUpdateState = uiState.appUpdateState,
                            onToggleAutoCheckUpdates = viewModel::setAutoCheckUpdatesOnStartup,
                            onCheckForUpdates = { viewModel.checkForUpdates(userInitiated = true) },
                            onOpenUpdateDialog = viewModel::openUpdateDialog,
                            onStartTourGuide = viewModel::startTourGuide,
                            onClearBrowsingData = {
                                viewModel.clearAllBrowsingData(context.getString(R.string.settings_data_cleared))
                            },
                            onOpenDevTools = viewModel::openDevTools
                        )

                        ActiveToolPage.QUICK_ACTIONS -> QuickActionsPage(
                            activeTab = activeTab,
                            aiEnabled = uiState.settings.aiWebAnalyzingEnabled,
                            floatingBubbleEnabled = uiState.settings.floatingBubbleEnabled,
                            savedPwas = uiState.savedPwas,
                            onBack = viewModel::closeActiveToolPage,
                            onOpenHistory = viewModel::openHistoryPage,
                            onOpenAiAnalyst = { viewModel.openAiAnalystPage() },
                            onOpenNews = { viewModel.openNewsPage() },
                            onOpenMediaDownloader = viewModel::openMediaDownloaderPage,
                            onOpenScreenshotEditor = viewModel::captureAndOpenScreenshotEditor,
                            onOpenPasswords = viewModel::openPasswordsPage,
                            onOpenNotesClips = viewModel::openNotesPage,
                            onClipCurrentPage = {
                                viewModel.closeActiveToolPage()
                                viewModel.clipActivePageSelection()
                            },
                            onInstallPwa = {
                                viewModel.closeActiveToolPage()
                                viewModel.installCurrentPageAsPwa(context)
                            },
                            onLaunchPwa = { url -> viewModel.openNewTab(url = url, select = true) },
                            onRemovePwa = viewModel::removeSavedPwa,
                            onNewIncognitoTab = { viewModel.openNewTab(isIncognito = true) },
                            onOpenTabOptions = {
                                activeTab?.id?.let { id ->
                                    viewModel.closeActiveToolPage()
                                    viewModel.openTabOptions(id)
                                }
                            },
                            onFindInPage = viewModel::openFindInPage,
                            onToggleFloatingBubble = {
                                handleToggleFloatingBubble(!uiState.settings.floatingBubbleEnabled)
                            },
                            onStartTourGuide = viewModel::startTourGuide,
                            onShowMessage = viewModel::showStatusMessage
                        )

                        ActiveToolPage.AI_ANALYST -> AiAnalystPage(
                            activeTab = activeTab,
                            aiEnabled = uiState.settings.aiWebAnalyzingEnabled,
                            selectedMode = uiState.aiSelectedMode,
                            analysisState = uiState.aiAnalysisState,
                            onBack = viewModel::closeActiveToolPage,
                            onEnableAi = { viewModel.enableAiAndAnalyze(uiState.aiSelectedMode) },
                            onRunAnalysis = viewModel::analyzeActivePage,
                            onSaveToNotes = viewModel::saveAiResultToNotes,
                            onShowMessage = viewModel::showStatusMessage
                        )

                        ActiveToolPage.DAILY_NEWS -> NewsPage(
                            newsState = uiState.newsState,
                            onBack = viewModel::closeActiveToolPage,
                            onSelectCategory = viewModel::loadNewsCategory,
                            onRefresh = { viewModel.loadNewsCategory(uiState.newsState.category) },
                            onOpenArticle = { article ->
                                viewModel.openNewTab(url = article.link, select = true)
                            }
                        )

                        ActiveToolPage.PASSWORDS -> PasswordManagerPage(
                            entries = uiState.passwords,
                            activeSiteTitle = activeTab?.title.orEmpty(),
                            activeSiteUrl = activeTab?.url.orEmpty(),
                            onBack = viewModel::closeActiveToolPage,
                            onSaveEntry = { siteTitle, siteUrl, user, pass ->
                                viewModel.savePasswordCredential(siteTitle, siteUrl, user, pass)
                            },
                            onDeleteEntry = viewModel::deletePasswordCredential,
                            onAutofillEntry = viewModel::autofillPasswordIntoActiveTab,
                            onShowMessage = viewModel::showStatusMessage
                        )

                        ActiveToolPage.NOTES_CLIPS -> NotesClipsPage(
                            clips = uiState.noteClips,
                            activePageTitle = activeTab?.title.orEmpty(),
                            activePageUrl = activeTab?.url.orEmpty(),
                            onBack = viewModel::closeActiveToolPage,
                            onClipCurrentPage = viewModel::clipActivePageSelection,
                            onSaveClip = { title, content, url ->
                                viewModel.saveNoteClip(title, content, url)
                            },
                            onDeleteClip = viewModel::deleteNoteClip,
                            onOpenUrl = { url -> viewModel.openNewTab(url = url, select = true) },
                            onShowMessage = viewModel::showStatusMessage
                        )

                        ActiveToolPage.MEDIA_DOWNLOADER -> MediaDownloaderPage(
                            mediaState = uiState.mediaState,
                            onBack = viewModel::closeActiveToolPage,
                            onRescan = viewModel::scanActivePageMedia,
                            onDownloadItem = { item -> viewModel.downloadMediaItem(context, item) },
                            onPreviewImage = { item ->
                                viewModel.openImageViewer(
                                    title = item.label,
                                    imageUrl = item.url
                                )
                            }
                        )

                        ActiveToolPage.SCREENSHOT_EDITOR -> ScreenshotEditorPage(
                            bitmap = uiState.screenshotBitmap,
                            onBack = viewModel::closeActiveToolPage,
                            onSaveBitmap = { bmp ->
                                val saved = ScreenshotStorageHelper.saveBitmapToGallery(context, bmp)
                                viewModel.showStatusMessage(
                                    if (saved != null) context.getString(R.string.screenshot_saved)
                                    else "Failed to save screenshot"
                                )
                            },
                            onShareBitmap = { bmp ->
                                val shareIntent = ScreenshotStorageHelper.shareBitmap(context, bmp)
                                if (shareIntent != null) {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
                                } else {
                                    viewModel.showStatusMessage("Unable to share screenshot")
                                }
                            },
                            onInspectImage = { bmp ->
                                viewModel.openImageViewer(
                                    title = activeTab?.title?.ifBlank { "Screenshot" } ?: "Screenshot",
                                    bitmap = bmp
                                )
                            }
                        )

                        ActiveToolPage.IMAGE_VIEWER -> {
                            val viewerState = uiState.imageViewerState ?: ImageViewerState()
                            ImageViewerPage(
                                state = viewerState,
                                onBack = viewModel::closeActiveToolPage,
                                onDownloadImage = viewerState.imageUrl?.let { imgUrl ->
                                    {
                                        viewModel.downloadMediaItem(
                                            context,
                                            ExtractedMediaItem(
                                                url = imgUrl,
                                                label = viewerState.title.ifBlank { "Image" },
                                                kind = MediaKind.IMAGE
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }

                    uiState.tourStepIndex?.let { stepIdx ->
                        TourGuideOverlay(
                            stepIndex = stepIdx,
                            onNext = viewModel::nextTourStep,
                            onPrevious = viewModel::previousTourStep,
                            onSkip = viewModel::finishTourGuide
                        )
                    }
                }
            }

            uiState.bookmarkDialogState?.let { dialogState ->
                BookmarkEditDialog(
                    dialogState = dialogState,
                    onDismiss = viewModel::dismissBookmarkDialog,
                    onSave = viewModel::saveBookmarkDialog
                )
            }

            uiState.tabOptionsTargetId?.let { targetId ->
                val targetTab = uiState.tabs.firstOrNull { it.id == targetId }
                if (targetTab != null) {
                    TabOptionsDialog(
                        tab = targetTab,
                        onDismiss = viewModel::dismissTabOptions,
                        onToggleLock = { viewModel.toggleTabLock(targetTab.id) },
                        onAssignGroup = { groupName, colorHex ->
                            viewModel.assignTabGroup(targetTab.id, groupName, colorHex)
                        },
                        onDuplicateTab = {
                            viewModel.openNewTab(
                                url = targetTab.url,
                                select = true,
                                isIncognito = targetTab.isIncognito,
                                groupName = targetTab.groupName,
                                groupColorHex = targetTab.groupColorHex
                            )
                        }
                    )
                }
            }

            if (uiState.isUpdateDialogVisible) {
                AppUpdateDialog(
                    state = uiState.appUpdateState,
                    onUpdateNow = { viewModel.startAppUpdateDownload(context) },
                    onOpenReleasePage = { releaseUrl ->
                        viewModel.dismissUpdateDialog()
                        viewModel.openNewTab(url = releaseUrl, select = true)
                    },
                    onDismiss = viewModel::dismissUpdateDialog
                )
            }
        }
    }
}
