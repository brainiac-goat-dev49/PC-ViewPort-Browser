package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.AppUpdateUiState
import java.util.Locale

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onUpdateNow: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val release = when (state) {
        is AppUpdateUiState.Available -> state.release
        is AppUpdateUiState.Downloading -> state.release
        is AppUpdateUiState.ReadyToInstall -> state.release
        is AppUpdateUiState.Error -> state.release
        else -> null
    } ?: return

    val localVersion = when (state) {
        is AppUpdateUiState.Available -> state.localVersion
        is AppUpdateUiState.Downloading -> state.localVersion
        is AppUpdateUiState.ReadyToInstall -> state.localVersion
        is AppUpdateUiState.Error -> state.localVersion
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("app_update_dialog"),
        icon = {
            Icon(
                imageVector = if (state is AppUpdateUiState.ReadyToInstall) {
                    Icons.Rounded.InstallMobile
                } else {
                    Icons.Rounded.NewReleases
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.update_dialog_title, release.versionName),
                    style = MaterialTheme.typography.titleLarge
                )
                if (localVersion.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.update_dialog_version_compare,
                            localVersion,
                            release.versionName
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                release.apkAsset?.let { asset ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = asset.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (asset.sizeBytes > 0L) {
                                val mb = asset.sizeBytes.toDouble() / (1024.0 * 1024.0)
                                Text(
                                    text = String.format(Locale.US, "%.1f MB", mb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.update_dialog_whats_new),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 220.dp)
                ) {
                    Text(
                        text = release.body.ifBlank { release.releaseName },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                            .testTag("update_dialog_notes")
                    )
                }

                when (state) {
                    is AppUpdateUiState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.update_downloading_progress, state.progressPercent),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_download_progress")
                            )
                        }
                    }

                    is AppUpdateUiState.ReadyToInstall -> {
                        if (state.requiresUnknownSourcesPermission) {
                            Text(
                                text = stringResource(R.string.update_install_permission_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    is AppUpdateUiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> Unit
                }

                if (release.htmlUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onOpenReleasePage(release.htmlUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_dialog_open_github_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.update_dialog_open_github))
                    }
                }
            }
        },
        confirmButton = {
            val isDownloading = state is AppUpdateUiState.Downloading
            val hasApkAsset = release.apkAsset != null
            Button(
                onClick = onUpdateNow,
                enabled = !isDownloading && hasApkAsset,
                modifier = Modifier.testTag("update_dialog_confirm_button")
            ) {
                Icon(
                    imageVector = if (state is AppUpdateUiState.ReadyToInstall) {
                        Icons.Rounded.InstallMobile
                    } else {
                        Icons.Rounded.Download
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state is AppUpdateUiState.ReadyToInstall) {
                        stringResource(R.string.update_dialog_install_apk)
                    } else {
                        stringResource(R.string.update_dialog_update_now)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("update_dialog_dismiss_button")
            ) {
                Text(stringResource(R.string.update_dialog_later))
            }
        }
    )
}
