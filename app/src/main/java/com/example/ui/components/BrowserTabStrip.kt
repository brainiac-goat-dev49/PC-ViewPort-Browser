package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.browser.BrowserTabState
import com.example.browser.UrlResolver

@Composable
fun BrowserTabStrip(
    tabs: List<BrowserTabState>,
    activeTabId: String,
    activeGroupFilter: String?,
    showTooltips: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenTabOptions: (String) -> Unit,
    onSelectGroupFilter: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val groups = tabs.mapNotNull { tab ->
        tab.groupName?.let { it to tab.groupColorHex }
    }.distinctBy { it.first }

    val visibleTabs = if (activeGroupFilter.isNullOrBlank()) {
        tabs
    } else {
        tabs.filter { it.groupName == activeGroupFilter }
    }

    LaunchedEffect(activeTabId, visibleTabs.size) {
        val activeIndex = visibleTabs.indexOfFirst { it.id == activeTabId }
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            if (groups.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        GroupFilterChip(
                            label = "All (${tabs.size})",
                            color = MaterialTheme.colorScheme.primary,
                            selected = activeGroupFilter == null,
                            onClick = { onSelectGroupFilter(null) }
                        )
                    }
                    items(groups, key = { it.first }) { (groupName, colorHex) ->
                        val count = tabs.count { it.groupName == groupName }
                        GroupFilterChip(
                            label = "$groupName ($count)",
                            color = Color(colorHex),
                            selected = activeGroupFilter == groupName,
                            onClick = {
                                onSelectGroupFilter(if (activeGroupFilter == groupName) null else groupName)
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp)
            ) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("tab_strip_list")
                ) {
                    items(items = visibleTabs, key = { it.id }) { tab ->
                        BrowserTabItem(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onSelect = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) },
                            onLongClick = { onOpenTabOptions(tab.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Text(
                        text = tabs.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("tab_count_badge")
                    )
                }

                DesktopTooltipWrap(
                    tooltipText = "Group, lock, or duplicate active tab",
                    enabled = showTooltips
                ) {
                    IconButton(
                        onClick = { onOpenTabOptions(activeTabId) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tab_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderSpecial,
                            contentDescription = stringResource(R.string.cd_tab_groups),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                DesktopTooltipWrap(
                    tooltipText = "Open isolated Incognito tab",
                    enabled = showTooltips
                ) {
                    IconButton(
                        onClick = onNewIncognitoTab,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("new_incognito_tab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = stringResource(R.string.cd_incognito_tab),
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                DesktopTooltipWrap(
                    tooltipText = "Open new desktop tab",
                    enabled = showTooltips
                ) {
                    IconButton(
                        onClick = onNewTab,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("new_tab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_new_tab),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
private fun GroupFilterChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserTabItem(
    tab: BrowserTabState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onLongClick: () -> Unit
) {
    val targetBg = when {
        tab.isIncognito && isActive -> Color(0xFF23173A)
        tab.isIncognito -> Color(0xFF1A132B)
        isActive -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val containerColor by animateColorAsState(targetValue = targetBg, label = "tabContainerColor")
    val contentColor = when {
        tab.isIncognito -> Color(0xFFE9D5FF)
        isActive -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val accentColor = when {
        tab.groupName != null -> Color(tab.groupColorHex)
        tab.isIncognito -> Color(0xFFC084FC)
        else -> MaterialTheme.colorScheme.primary
    }

    val tabShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    val displayTitle = tab.title.ifBlank {
        UrlResolver.extractDisplayHost(tab.url).ifBlank { stringResource(R.string.new_tab_title) }
    }

    Surface(
        shape = tabShape,
        color = containerColor,
        border = if (isActive) {
            BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        },
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 126.dp, max = 196.dp)
            .clip(tabShape)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongClick
            )
            .testTag("tab_item_${tab.id}")
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            if (isActive || tab.groupName != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isActive) 3.dp else 2.dp)
                        .background(accentColor)
                        .align(Alignment.TopCenter)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 10.dp, end = 2.dp, top = 2.dp)
            ) {
                when {
                    tab.isLoading -> {
                        CircularProgressIndicator(
                            strokeWidth = 1.8.dp,
                            color = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    tab.isIncognito -> {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = stringResource(R.string.incognito_badge),
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    tab.favicon != null -> {
                        Image(
                            bitmap = tab.favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = if (tab.isDesktopMode) {
                                Icons.Rounded.DesktopWindows
                            } else {
                                Icons.Rounded.Smartphone
                            },
                            contentDescription = null,
                            tint = if (isActive) accentColor else contentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (tab.isLocked) {
                    IconButton(
                        onClick = onLongClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("locked_tab_${tab.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked tab",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("close_tab_${tab.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close_tab),
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
