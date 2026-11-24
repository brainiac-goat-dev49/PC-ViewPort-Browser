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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.browser.BrowserTabState
import com.example.data.SavedPwaEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionsPage(
    activeTab: BrowserTabState?,
    aiEnabled: Boolean,
    floatingBubbleEnabled: Boolean,
    savedPwas: List<SavedPwaEntity>,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAiAnalyst: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenMediaDownloader: () -> Unit,
    onOpenScreenshotEditor: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenNotesClips: () -> Unit,
    onClipCurrentPage: () -> Unit,
    onInstallPwa: () -> Unit,
    onLaunchPwa: (String) -> Unit,
    onRemovePwa: (Long) -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenTabOptions: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleFloatingBubble: () -> Unit,
    onStartTourGuide: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("quick_actions_page")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("quick_actions_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_nav_back)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.quick_actions_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                val columns = if (maxWidth >= 680.dp) 3 else 2

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1080.dp)
                    ) {
                        if (activeTab != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activeTab.title.ifBlank { "Current Page" },
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = activeTab.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(activeTab.url))
                                            onShowMessage("URL copied to clipboard")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy page URL"
                                        )
                                    }
                                }
                            }
                        }

                        val actions = listOf(
                            QuickActionTile(
                                icon = Icons.Rounded.History,
                                title = stringResource(R.string.history_title),
                                subtitle = "Search, reopen or clear visited sites",
                                testTag = "qa_history",
                                onClick = onOpenHistory
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.AutoAwesome,
                                title = stringResource(R.string.ai_analyst_title),
                                subtitle = if (aiEnabled) "Summarize & audit page with Gemini" else "Turned off in Settings",
                                testTag = "qa_ai_analyst",
                                onClick = onOpenAiAnalyst
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.Newspaper,
                                title = stringResource(R.string.news_title),
                                subtitle = "Live Top, Tech, World & Science feeds",
                                testTag = "qa_daily_news",
                                onClick = onOpenNews
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.PermMedia,
                                title = stringResource(R.string.media_title),
                                subtitle = "Extract Video, Images & Audio from DOM",
                                testTag = "qa_media_downloader",
                                onClick = onOpenMediaDownloader
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.Draw,
                                title = stringResource(R.string.screenshot_title),
                                subtitle = "Capture desktop page, annotate & save",
                                testTag = "qa_screenshot_editor",
                                onClick = onOpenScreenshotEditor
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.Password,
                                title = stringResource(R.string.passwords_title),
                                subtitle = "Encrypted logins & 1-tap autofill",
                                testTag = "qa_passwords",
                                onClick = onOpenPasswords
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.NoteAdd,
                                title = stringResource(R.string.notes_title),
                                subtitle = "Save notes & web clips from pages",
                                testTag = "qa_notes_clips",
                                onClick = onOpenNotesClips
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.ContentCopy,
                                title = stringResource(R.string.notes_clip_page),
                                subtitle = "Clip selected text or excerpt now",
                                testTag = "qa_clip_now",
                                onClick = onClipCurrentPage
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.AddToHomeScreen,
                                title = stringResource(R.string.quick_actions_pwa_install),
                                subtitle = "Pin site as standalone home shortcut",
                                testTag = "qa_install_pwa",
                                onClick = onInstallPwa
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.FindInPage,
                                title = stringResource(R.string.quick_actions_find_in_page),
                                subtitle = "Highlight & jump to text on page",
                                testTag = "qa_find_in_page",
                                onClick = onFindInPage
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.Security,
                                title = stringResource(R.string.cd_incognito_tab),
                                subtitle = "Isolated no-cookie, no-history tab",
                                testTag = "qa_incognito",
                                onClick = onNewIncognitoTab
                            ),
                            QuickActionTile(
                                icon = if (activeTab?.isLocked == true) Icons.Rounded.Lock else Icons.Rounded.FolderSpecial,
                                title = "Tab Group & Lock",
                                subtitle = "Organize tabs by color or prevent closing",
                                testTag = "qa_tab_options",
                                onClick = onOpenTabOptions
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.BubbleChart,
                                title = if (floatingBubbleEnabled) "Floating Bubble: ON" else "Floating Bubble: OFF",
                                subtitle = "Quick-launch overlay outside app",
                                testTag = "qa_floating_bubble",
                                onClick = onToggleFloatingBubble
                            ),
                            QuickActionTile(
                                icon = Icons.Rounded.HelpOutline,
                                title = "Interactive Tour Guide",
                                subtitle = "Step-by-step walkthrough of all tools",
                                testTag = "qa_tour_guide",
                                onClick = onStartTourGuide
                            )
                        )

                        actions.chunked(columns).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { tile ->
                                    QuickActionCard(
                                        tile = tile,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        if (savedPwas.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Widgets,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Installed Web Apps (PWAs)",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        savedPwas.forEach { pwa ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { onLaunchPwa(pwa.startUrl) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                                ) {
                                                    Text(
                                                        text = pwa.name,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    IconButton(
                                                        onClick = { onRemovePwa(pwa.id) },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.DeleteOutline,
                                                            contentDescription = "Remove PWA",
                                                            modifier = Modifier.size(16.dp)
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
        }
    }
}

private data class QuickActionTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionCard(
    tile: QuickActionTile,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = tile.onClick)
            .testTag(tile.testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
