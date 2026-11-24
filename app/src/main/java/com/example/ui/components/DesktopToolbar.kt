package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.browser.BrowserTabState
import com.example.ui.theme.OmniboxTextStyle

@Composable
fun DesktopToolbar(
    activeTab: BrowserTabState?,
    isBookmarked: Boolean,
    bookmarkCount: Int,
    adBlockerEnabled: Boolean,
    blockedAdsCount: Int,
    desktopViewportWidthPx: Int,
    isDevToolsOpen: Boolean,
    disableSoftKeyboard: Boolean,
    aiWebAnalyzingEnabled: Boolean,
    showTooltips: Boolean,
    isFindInPageOpen: Boolean,
    findInPageQuery: String,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onHome: () -> Unit,
    onNavigate: (String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onToggleDevTools: () -> Unit,
    onOpenAiAnalyst: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenSettings: () -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onCloseFindInPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canGoBack = activeTab?.canGoBack == true
    val canGoForward = activeTab?.canGoForward == true
    val isLoading = activeTab?.isLoading == true
    val isDesktopMode = activeTab?.isDesktopMode != false
    val isIncognito = activeTab?.isIncognito == true
    val currentUrl = activeTab?.url.orEmpty()
    val tabId = activeTab?.id.orEmpty()

    Surface(
        color = if (isIncognito) Color(0xFF171026) else MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val isWideLayout = maxWidth >= 640.dp

                if (isWideLayout) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationCluster(
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            isLoading = isLoading,
                            showTooltips = showTooltips,
                            onBack = onBack,
                            onForward = onForward,
                            onRefreshOrStop = onRefreshOrStop,
                            onHome = onHome
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        OmniboxField(
                            tabId = tabId,
                            currentUrl = currentUrl,
                            isBookmarked = isBookmarked,
                            isIncognito = isIncognito,
                            disableSoftKeyboard = disableSoftKeyboard,
                            onNavigate = onNavigate,
                            onToggleBookmark = onToggleBookmark,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        UtilityCluster(
                            isDesktopMode = isDesktopMode,
                            desktopViewportWidthPx = desktopViewportWidthPx,
                            bookmarkCount = bookmarkCount,
                            adBlockerEnabled = adBlockerEnabled,
                            blockedAdsCount = blockedAdsCount,
                            isDevToolsOpen = isDevToolsOpen,
                            aiWebAnalyzingEnabled = aiWebAnalyzingEnabled,
                            showTooltips = showTooltips,
                            onToggleDesktopMode = onToggleDesktopMode,
                            onOpenAiAnalyst = onOpenAiAnalyst,
                            onOpenQuickActions = onOpenQuickActions,
                            onOpenBookmarks = onOpenBookmarks,
                            onOpenDownloads = onOpenDownloads,
                            onToggleDevTools = onToggleDevTools,
                            onOpenSettings = onOpenSettings
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OmniboxField(
                                tabId = tabId,
                                currentUrl = currentUrl,
                                isBookmarked = isBookmarked,
                                isIncognito = isIncognito,
                                disableSoftKeyboard = disableSoftKeyboard,
                                onNavigate = onNavigate,
                                onToggleBookmark = onToggleBookmark,
                                modifier = Modifier.weight(1f)
                            )

                            UserAgentToggleChip(
                                isDesktopMode = isDesktopMode,
                                desktopViewportWidthPx = desktopViewportWidthPx,
                                onToggle = onToggleDesktopMode
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            NavigationCluster(
                                canGoBack = canGoBack,
                                canGoForward = canGoForward,
                                isLoading = isLoading,
                                showTooltips = showTooltips,
                                onBack = onBack,
                                onForward = onForward,
                                onRefreshOrStop = onRefreshOrStop,
                                onHome = onHome
                            )

                            UtilityCluster(
                                isDesktopMode = isDesktopMode,
                                desktopViewportWidthPx = desktopViewportWidthPx,
                                bookmarkCount = bookmarkCount,
                                adBlockerEnabled = adBlockerEnabled,
                                blockedAdsCount = blockedAdsCount,
                                isDevToolsOpen = isDevToolsOpen,
                                aiWebAnalyzingEnabled = aiWebAnalyzingEnabled,
                                showTooltips = showTooltips,
                                showUserAgentChip = false,
                                onToggleDesktopMode = onToggleDesktopMode,
                                onOpenAiAnalyst = onOpenAiAnalyst,
                                onOpenQuickActions = onOpenQuickActions,
                                onOpenBookmarks = onOpenBookmarks,
                                onOpenDownloads = onOpenDownloads,
                                onToggleDevTools = onToggleDevTools,
                                onOpenSettings = onOpenSettings
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isFindInPageOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = findInPageQuery,
                        onValueChange = onFindQueryChange,
                        placeholder = { Text(stringResource(R.string.find_in_page_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("find_in_page_input")
                    )
                    IconButton(onClick = { onFindNext(false) }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(onClick = { onFindNext(true) }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                    }
                    IconButton(onClick = onCloseFindInPage) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close find")
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun NavigationCluster(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    showTooltips: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onHome: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DesktopTooltipWrap("Back", showTooltips) {
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_nav_back)
                )
            }
        }

        DesktopTooltipWrap("Forward", showTooltips) {
            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.cd_nav_forward)
                )
            }
        }

        DesktopTooltipWrap("Reload or stop loading", showTooltips) {
            IconButton(
                onClick = onRefreshOrStop,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_refresh_button")
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                    contentDescription = stringResource(
                        if (isLoading) R.string.cd_nav_stop else R.string.cd_nav_refresh
                    )
                )
            }
        }

        DesktopTooltipWrap("Home page", showTooltips) {
            IconButton(
                onClick = onHome,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_home_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = stringResource(R.string.cd_nav_home)
                )
            }
        }
    }
}

@Composable
private fun UtilityCluster(
    isDesktopMode: Boolean,
    desktopViewportWidthPx: Int,
    bookmarkCount: Int,
    adBlockerEnabled: Boolean,
    blockedAdsCount: Int,
    isDevToolsOpen: Boolean,
    aiWebAnalyzingEnabled: Boolean,
    showTooltips: Boolean,
    showUserAgentChip: Boolean = true,
    onToggleDesktopMode: () -> Unit,
    onOpenAiAnalyst: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onToggleDevTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (showUserAgentChip) {
            DesktopTooltipWrap("Switch Desktop / Mobile viewport", showTooltips) {
                UserAgentToggleChip(
                    isDesktopMode = isDesktopMode,
                    desktopViewportWidthPx = desktopViewportWidthPx,
                    onToggle = onToggleDesktopMode
                )
            }
        }

        if (adBlockerEnabled) {
            DesktopTooltipWrap("Ads & trackers blocked", showTooltips) {
                BlockedAdsBadge(
                    count = blockedAdsCount,
                    onClick = onOpenSettings
                )
            }
        }

        if (aiWebAnalyzingEnabled) {
            DesktopTooltipWrap("AI Web Analyst (Gemini 3.8 Flash)", showTooltips) {
                IconButton(
                    onClick = onOpenAiAnalyst,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ai_analyst_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_ai_analyst),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        DesktopTooltipWrap("Quick Actions, News, Media, Vault, Notes, PWA & Screenshot", showTooltips) {
            IconButton(
                onClick = onOpenQuickActions,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_actions_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = stringResource(R.string.cd_quick_actions),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        DesktopTooltipWrap("Bookmarks manager", showTooltips) {
            IconButton(
                onClick = onOpenBookmarks,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bookmarks_manager_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmarks,
                    contentDescription = stringResource(R.string.cd_bookmarks_manager),
                    tint = if (bookmarkCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        DesktopTooltipWrap("Downloads manager", showTooltips) {
            IconButton(
                onClick = onOpenDownloads,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("downloads_manager_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.cd_downloads_manager),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DesktopTooltipWrap("ES Module & JS DevTools Console", showTooltips) {
            IconButton(
                onClick = onToggleDevTools,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("devtools_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = stringResource(R.string.cd_devtools_console),
                    tint = if (isDevToolsOpen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        DesktopTooltipWrap("Settings, Mock GPS, Proxy & Controls", showTooltips) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OmniboxField(
    tabId: String,
    currentUrl: String,
    isBookmarked: Boolean,
    isIncognito: Boolean,
    disableSoftKeyboard: Boolean,
    onNavigate: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember(tabId) {
        mutableStateOf(TextFieldValue(text = currentUrl))
    }

    LaunchedEffect(tabId, currentUrl, isFocused) {
        if (!isFocused && textFieldValue.text != currentUrl) {
            textFieldValue = TextFieldValue(text = currentUrl)
        }
    }

    LaunchedEffect(disableSoftKeyboard, isFocused) {
        if (disableSoftKeyboard && isFocused) {
            keyboardController?.hide()
        }
    }

    val isHttps = currentUrl.startsWith("https://", ignoreCase = true)
    val borderColor = when {
        isIncognito -> Color(0xFFC084FC).copy(alpha = if (isFocused) 0.9f else 0.45f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isIncognito) Color(0xFF24193B) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp)
        ) {
            Icon(
                imageVector = when {
                    isIncognito -> Icons.Rounded.Security
                    isHttps -> Icons.Rounded.Lock
                    else -> Icons.Rounded.Public
                },
                contentDescription = stringResource(R.string.cd_site_security),
                tint = when {
                    isIncognito -> Color(0xFFC084FC)
                    isHttps -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.omnibox_placeholder),
                        style = OmniboxTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        if (disableSoftKeyboard) {
                            keyboardController?.hide()
                        }
                    },
                    singleLine = true,
                    textStyle = OmniboxTextStyle.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            onNavigate(textFieldValue.text)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            val newlyFocused = focusState.isFocused
                            if (newlyFocused && !isFocused) {
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length)
                                )
                                if (disableSoftKeyboard) {
                                    keyboardController?.hide()
                                }
                            }
                            isFocused = newlyFocused
                        }
                        .testTag("omnibox_input")
                )
            }

            if (isFocused && textFieldValue.text.isNotEmpty()) {
                IconButton(
                    onClick = { textFieldValue = TextFieldValue("") },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("omnibox_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = stringResource(R.string.cd_clear_omnibox),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("bookmark_star_button")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) {
                            Icons.Rounded.Star
                        } else {
                            Icons.Rounded.StarBorder
                        },
                        contentDescription = stringResource(R.string.cd_bookmark_toggle),
                        tint = if (isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserAgentToggleChip(
    isDesktopMode: Boolean,
    desktopViewportWidthPx: Int,
    onToggle: () -> Unit
) {
    val containerColor = if (isDesktopMode) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isDesktopMode) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(38.dp)
            .clickable(onClick = onToggle)
            .testTag("user_agent_toggle_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            Icon(
                imageVector = if (isDesktopMode) {
                    Icons.Rounded.DesktopWindows
                } else {
                    Icons.Rounded.Smartphone
                },
                contentDescription = stringResource(R.string.cd_toggle_user_agent),
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isDesktopMode) {
                    "${stringResource(R.string.mode_desktop_badge)} ${desktopViewportWidthPx}px"
                } else {
                    stringResource(R.string.mode_mobile_badge)
                },
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun BlockedAdsBadge(
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .clickable(onClick = onClick)
            .testTag("blocked_ads_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = stringResource(R.string.cd_blocked_ads),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
