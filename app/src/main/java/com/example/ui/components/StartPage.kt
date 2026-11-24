package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.browser.BrowserTabState
import com.example.browser.UrlResolver
import com.example.data.BookmarkEntity
import com.example.data.HistoryEntity
import com.example.data.SavedPwaEntity
import com.example.ui.OmniboxSuggestion
import com.example.ui.OmniboxSuggestionEngine
import com.example.ui.SuggestionSource

private data class StarterLink(
    val title: String,
    val url: String
)

private val defaultStarterLinks = listOf(
    StarterLink("Google", "https://www.google.com"),
    StarterLink("GitHub", "https://github.com"),
    StarterLink("Wikipedia", "https://www.wikipedia.org"),
    StarterLink("MDN Web Docs", "https://developer.mozilla.org")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartPage(
    isIncognito: Boolean,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    savedPwas: List<SavedPwaEntity>,
    tabs: List<BrowserTabState>,
    aiEnabled: Boolean,
    onNavigate: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAiAnalyst: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val liveSuggestions = remember(searchQuery, history, bookmarks, savedPwas, tabs) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            OmniboxSuggestionEngine.buildSuggestions(
                query = searchQuery,
                history = history,
                bookmarks = bookmarks,
                savedPwas = savedPwas,
                tabs = tabs,
                maxResults = 6
            )
        }
    }

    val containerBg = if (isIncognito) {
        Color(0xFF140E22)
    } else {
        MaterialTheme.colorScheme.background
    }

    Surface(
        color = containerBg,
        modifier = modifier
            .fillMaxSize()
            .testTag("start_page")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            val isWide = maxWidth >= 640.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 920.dp)
                ) {
                    // Hero Header
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isIncognito) Color(0xFF2E1E4E) else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isIncognito) Icons.Rounded.Security else Icons.Rounded.DesktopWindows,
                                    contentDescription = null,
                                    tint = if (isIncognito) Color(0xFFC084FC) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.start_page_title),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = if (isIncognito) {
                                        stringResource(R.string.start_page_incognito_notice)
                                    } else {
                                        stringResource(R.string.start_page_subtitle)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isIncognito) {
                                        Color(0xFFD8B4FE)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    // Search Bar + Live Suggestions
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.start_page_search_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        if (searchQuery.isNotBlank()) {
                                            onNavigate(searchQuery)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_page_search_input")
                            )

                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        onNavigate(searchQuery)
                                    }
                                },
                                enabled = searchQuery.isNotBlank(),
                                modifier = Modifier
                                    .height(54.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_page_search_submit")
                            ) {
                                Text(stringResource(R.string.start_page_go))
                            }
                        }

                        if (liveSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_page_suggestions_card")
                            ) {
                                Column {
                                    liveSuggestions.forEachIndexed { idx, suggestion ->
                                        if (idx > 0) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                        StartPageSuggestionRow(
                                            suggestion = suggestion,
                                            index = idx,
                                            onClick = {
                                                if (suggestion.source == SuggestionSource.OPEN_TAB && suggestion.tabId != null) {
                                                    onSelectTab(suggestion.tabId)
                                                } else {
                                                    onNavigate(suggestion.url)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Tools Strip
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StartToolChip(
                            icon = Icons.Rounded.History,
                            label = stringResource(R.string.history_title),
                            testTag = "start_page_tool_history",
                            onClick = onOpenHistory
                        )
                        StartToolChip(
                            icon = Icons.Rounded.Bookmarks,
                            label = stringResource(R.string.bookmarks_title),
                            testTag = "start_page_tool_bookmarks",
                            onClick = onOpenBookmarks
                        )
                        if (aiEnabled) {
                            StartToolChip(
                                icon = Icons.Rounded.AutoAwesome,
                                label = stringResource(R.string.ai_analyst_title),
                                testTag = "start_page_tool_ai",
                                onClick = onOpenAiAnalyst
                            )
                        }
                        StartToolChip(
                            icon = Icons.Rounded.Newspaper,
                            label = stringResource(R.string.news_title),
                            testTag = "start_page_tool_news",
                            onClick = onOpenNews
                        )
                        StartToolChip(
                            icon = Icons.Rounded.Download,
                            label = stringResource(R.string.downloads_title),
                            testTag = "start_page_tool_downloads",
                            onClick = onOpenDownloads
                        )
                        StartToolChip(
                            icon = Icons.Rounded.Apps,
                            label = stringResource(R.string.quick_actions_title),
                            testTag = "start_page_tool_quick_actions",
                            onClick = onOpenQuickActions
                        )
                        StartToolChip(
                            icon = Icons.Rounded.Tune,
                            label = stringResource(R.string.settings_title),
                            testTag = "start_page_tool_settings",
                            onClick = onOpenSettings
                        )
                    }

                    // Quick Access Bookmarks / PWAs / Starter Links
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.start_page_quick_links),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val quickLinks = remember(bookmarks, savedPwas) {
                                val combined = mutableListOf<Triple<String, String, ImageVector>>()
                                bookmarks.take(8).forEach {
                                    combined.add(Triple(it.title.ifBlank { UrlResolver.extractDisplayHost(it.url) }, it.url, Icons.Rounded.Star))
                                }
                                savedPwas.take(4).forEach { pwa ->
                                    if (combined.none { it.second == pwa.startUrl }) {
                                        combined.add(Triple(pwa.name, pwa.startUrl, Icons.Rounded.Widgets))
                                    }
                                }
                                if (combined.isEmpty()) {
                                    defaultStarterLinks.forEach { link ->
                                        combined.add(Triple(link.title, link.url, Icons.Rounded.Public))
                                    }
                                }
                                combined
                            }

                            val cols = if (isWide) 4 else 2
                            quickLinks.chunked(cols).forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { (title, url, icon) ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onNavigate(url) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = title,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = UrlResolver.extractDisplayHost(url),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    repeat(cols - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // Recent History Section (hidden for Incognito)
                    if (!isIncognito && history.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_page_recent_history_card")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.start_page_recent_history),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = onOpenHistory,
                                        modifier = Modifier.testTag("start_page_view_all_history")
                                    ) {
                                        Text(stringResource(R.string.start_page_view_all))
                                    }
                                }

                                history.take(5).forEachIndexed { idx, item ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onNavigate(item.url) }
                                            .minimumInteractiveComponentSize()
                                            .padding(horizontal = 6.dp, vertical = 8.dp)
                                            .testTag("start_page_history_item_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartToolChip(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label)
    }
}

@Composable
private fun StartPageSuggestionRow(
    suggestion: OmniboxSuggestion,
    index: Int,
    onClick: () -> Unit
) {
    val icon = when (suggestion.source) {
        SuggestionSource.HISTORY -> Icons.Rounded.History
        SuggestionSource.BOOKMARK -> Icons.Rounded.Star
        SuggestionSource.SAVED_PWA -> Icons.Rounded.Widgets
        SuggestionSource.OPEN_TAB -> Icons.Rounded.Tab
        SuggestionSource.URL_DIRECT -> Icons.Rounded.Public
        SuggestionSource.WEB_SEARCH -> Icons.Rounded.Search
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("start_page_suggestion_$index")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = suggestion.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
