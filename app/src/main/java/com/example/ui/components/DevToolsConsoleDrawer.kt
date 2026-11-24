package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.browser.ConsoleLogEntry
import com.example.browser.ConsoleLogLevel
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun DevToolsConsoleDrawer(
    logs: List<ConsoleLogEntry>,
    disableSoftKeyboard: Boolean,
    onRunScript: (code: String, asEsModule: Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scriptText by remember { mutableStateOf("") }
    var runAsEsModule by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        color = Color(0xFF0D1321),
        contentColor = Color(0xFFE2E8F0),
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .testTag("devtools_console_drawer")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131C2E))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.devtools_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFF1F5F9)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = runAsEsModule,
                        onClick = { runAsEsModule = !runAsEsModule },
                        label = {
                            Text(
                                text = stringResource(R.string.devtools_es_module),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("devtools_es_module_toggle")
                    )

                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("devtools_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.devtools_clear),
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("devtools_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close_tab),
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.devtools_es_module_tip),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("devtools_log_list")
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.devtools_empty),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    items(items = logs, key = { it.id }) { entry ->
                        ConsoleLogRow(entry = entry)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131C2E))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = {
                        scriptText = it
                        if (disableSoftKeyboard) {
                            keyboardController?.hide()
                        }
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.devtools_input_hint),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = Color(0xFFF8FAFC)
                    ),
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("devtools_script_input")
                )

                Button(
                    onClick = {
                        onRunScript(scriptText, runAsEsModule)
                    },
                    enabled = scriptText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("devtools_run_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.devtools_run_script),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: ConsoleLogEntry) {
    val textColor = when (entry.level) {
        ConsoleLogLevel.ERROR -> Color(0xFFFCA5A5)
        ConsoleLogLevel.WARNING -> Color(0xFFFCD34D)
        ConsoleLogLevel.COMMAND -> Color(0xFF38BDF8)
        ConsoleLogLevel.RESULT -> Color(0xFF6EE7B7)
        ConsoleLogLevel.LOG -> Color(0xFFE2E8F0)
    }

    val badgeText = when (entry.level) {
        ConsoleLogLevel.ERROR -> "ERR"
        ConsoleLogLevel.WARNING -> "WRN"
        ConsoleLogLevel.COMMAND -> "CMD"
        ConsoleLogLevel.RESULT -> "RES"
        ConsoleLogLevel.LOG -> "LOG"
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${entry.timestamp} [$badgeText]",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.78f),
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = entry.message,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}
