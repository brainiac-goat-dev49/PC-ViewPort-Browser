package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.BrowserSettings
import com.example.data.CityPreset
import com.example.data.DesktopViewportPresets
import com.example.data.MockLocationPresets
import com.example.data.ProxyScheme
import com.example.ui.AppUpdateUiState
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(
    settings: BrowserSettings,
    blockedAdsCount: Int,
    isSystemVpnActive: Boolean,
    onBack: () -> Unit,
    onSelectDesktopWidthPx: (Int) -> Unit,
    onSelectDesktopLandscapeWidthPx: (Int) -> Unit,
    onSelectDesktopUiScale: (Float) -> Unit,
    onToggleAdBlocker: (Boolean) -> Unit,
    onToggleMockLocation: (Boolean) -> Unit,
    onUpdateCoordinates: (Double, Double) -> Unit,
    onSelectCityPreset: (CityPreset) -> Unit,
    onToggleDisableSoftKeyboard: (Boolean) -> Unit,
    onApplyProxy: (Boolean, ProxyScheme, String, String) -> Unit,
    onRefreshVpnStatus: () -> Unit,
    onToggleForceDark: (Boolean) -> Unit,
    onToggleBlockThirdPartyCookies: (Boolean) -> Unit,
    onToggleJavaScript: (Boolean) -> Unit,
    onToggleAiAnalyzing: (Boolean) -> Unit,
    onToggleTooltips: (Boolean) -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onToggleStartPage: (Boolean) -> Unit,
    onUpdateCustomHomeUrl: (String) -> Unit,
    onToggleRestoreLastPage: (Boolean) -> Unit,
    appUpdateState: AppUpdateUiState = AppUpdateUiState.Idle,
    onToggleAutoCheckUpdates: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    onStartTourGuide: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onOpenDevTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val isCurrentlyLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels
    val portraitNativePx = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceIn(640, 3840)
    val landscapeNativePx = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceIn(640, 3840)
    val nativeHeightPx = displayMetrics.heightPixels

    var customWidthInput by remember(settings.desktopViewportWidthPx) {
        mutableStateOf(settings.desktopViewportWidthPx.toString())
    }
    var customLandscapeWidthInput by remember(settings.desktopLandscapeViewportWidthPx) {
        mutableStateOf(settings.desktopLandscapeViewportWidthPx.toString())
    }
    var customHomeInput by remember(settings.customHomeUrl) {
        mutableStateOf(settings.customHomeUrl)
    }
    var latInput by remember(settings.mockLatitude) {
        mutableStateOf(settings.mockLatitude.toString())
    }
    var lngInput by remember(settings.mockLongitude) {
        mutableStateOf(settings.mockLongitude.toString())
    }

    var proxyEnabledDraft by remember(settings.proxyEnabled) { mutableStateOf(settings.proxyEnabled) }
    var proxySchemeDraft by remember(settings.proxyScheme) { mutableStateOf(settings.proxyScheme) }
    var proxyHostDraft by remember(settings.proxyHost) { mutableStateOf(settings.proxyHost) }
    var proxyPortDraft by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_page")
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
                                    .testTag("settings_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_nav_back)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        FilledTonalButton(
                            onClick = onOpenDevTools,
                            modifier = Modifier.testTag("settings_open_devtools_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.devtools_title))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                val useTwoColumns = maxWidth >= 680.dp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val containerModifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1120.dp)
                    val viewportCard: @Composable () -> Unit = {
                        // Section 0: Desktop Viewport & App UI Scale
                        SettingsSectionCard(title = stringResource(R.string.settings_section_viewport)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DesktopWindows,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_desktop_width),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.settings_desktop_width_tip),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = settings.desktopViewportWidthPx == portraitNativePx,
                                onClick = {
                                    customWidthInput = portraitNativePx.toString()
                                    onSelectDesktopWidthPx(portraitNativePx)
                                },
                                label = { Text("Device (${portraitNativePx}px)") },
                                modifier = Modifier.testTag("desktop_width_native")
                            )
                            DesktopViewportPresets.widthsPx.forEach { widthPx ->
                                FilterChip(
                                    selected = settings.desktopViewportWidthPx == widthPx,
                                    onClick = {
                                        customWidthInput = widthPx.toString()
                                        onSelectDesktopWidthPx(widthPx)
                                    },
                                    label = { Text("${widthPx}px") },
                                    modifier = Modifier.testTag("desktop_width_$widthPx")
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customWidthInput,
                                onValueChange = { customWidthInput = it.filter(Char::isDigit) },
                                label = { Text("Portrait Custom Width (px)") },
                                supportingText = {
                                    val activeLabel = if (isCurrentlyLandscape) {
                                        "Active: Landscape ${settings.desktopLandscapeViewportWidthPx}px"
                                    } else {
                                        "Active: Portrait ${settings.desktopViewportWidthPx}px"
                                    }
                                    Text("Screen: ${displayMetrics.widthPixels}x${nativeHeightPx}px • $activeLabel")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("desktop_width_custom_input")
                            )
                            Button(
                                onClick = {
                                    customWidthInput.toIntOrNull()?.let(onSelectDesktopWidthPx)
                                },
                                modifier = Modifier.testTag("desktop_width_custom_apply")
                            ) {
                                Text(stringResource(R.string.action_apply))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = stringResource(R.string.settings_desktop_landscape_width),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = settings.desktopLandscapeViewportWidthPx == landscapeNativePx,
                                onClick = {
                                    customLandscapeWidthInput = landscapeNativePx.toString()
                                    onSelectDesktopLandscapeWidthPx(landscapeNativePx)
                                },
                                label = { Text("Device (${landscapeNativePx}px)") },
                                modifier = Modifier.testTag("desktop_landscape_width_native")
                            )
                            DesktopViewportPresets.widthsPx.forEach { widthPx ->
                                FilterChip(
                                    selected = settings.desktopLandscapeViewportWidthPx == widthPx,
                                    onClick = {
                                        customLandscapeWidthInput = widthPx.toString()
                                        onSelectDesktopLandscapeWidthPx(widthPx)
                                    },
                                    label = { Text("${widthPx}px") },
                                    modifier = Modifier.testTag("desktop_landscape_width_$widthPx")
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customLandscapeWidthInput,
                                onValueChange = { customLandscapeWidthInput = it.filter(Char::isDigit) },
                                label = { Text("Landscape Custom Width (px)") },
                                supportingText = {
                                    Text("Forced in landscape: ${settings.desktopLandscapeViewportWidthPx}px")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("desktop_landscape_width_custom_input")
                            )
                            Button(
                                onClick = {
                                    customLandscapeWidthInput.toIntOrNull()?.let(onSelectDesktopLandscapeWidthPx)
                                },
                                modifier = Modifier.testTag("desktop_landscape_width_custom_apply")
                            ) {
                                Text(stringResource(R.string.action_apply))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_ui_scale),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.settings_ui_scale_tip),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DesktopViewportPresets.uiScales.forEach { (scale, label) ->
                                FilterChip(
                                    selected = abs(settings.desktopUiScale - scale) < 0.02f,
                                    onClick = { onSelectDesktopUiScale(scale) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("ui_scale_${scale}")
                                )
                            }
                        }
                        }
                    }

                    val privacyCard: @Composable () -> Unit = {
                        // Section 1: Ad Blocker & Privacy
                        SettingsSectionCard(title = stringResource(R.string.settings_section_privacy)) {
                        SettingSwitchRow(
                            icon = Icons.Rounded.Shield,
                            title = "${stringResource(R.string.settings_ad_blocker)} ($blockedAdsCount blocked)",
                            tip = stringResource(R.string.settings_ad_blocker_tip),
                            checked = settings.adBlockerEnabled,
                            onCheckedChange = onToggleAdBlocker,
                            testTag = "setting_ad_blocker_switch"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SettingSwitchRow(
                            icon = Icons.Rounded.Cookie,
                            title = stringResource(R.string.settings_block_3p_cookies),
                            tip = stringResource(R.string.settings_block_3p_cookies_tip),
                            checked = settings.blockThirdPartyCookies,
                            onCheckedChange = onToggleBlockThirdPartyCookies,
                            testTag = "setting_block_3p_cookies_switch"
                        )

                        OutlinedButton(
                            onClick = onClearBrowsingData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_clear_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_clear_browsing_data))
                        }
                        }
                    }

                    val locationCard: @Composable () -> Unit = {
                        // Section 2: Mock GPS Location
                        SettingsSectionCard(title = stringResource(R.string.settings_section_location)) {
                        SettingSwitchRow(
                            icon = Icons.Rounded.LocationOn,
                            title = stringResource(R.string.settings_mock_location),
                            tip = stringResource(R.string.settings_mock_location_tip),
                            checked = settings.mockLocationEnabled,
                            onCheckedChange = onToggleMockLocation,
                            testTag = "setting_mock_location_switch"
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                label = { Text(stringResource(R.string.settings_latitude)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("setting_mock_lat_input")
                            )
                            OutlinedTextField(
                                value = lngInput,
                                onValueChange = { lngInput = it },
                                label = { Text(stringResource(R.string.settings_longitude)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("setting_mock_lng_input")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MockLocationPresets.cities.forEach { preset ->
                                FilterChip(
                                    selected = settings.mockLocationEnabled &&
                                        settings.mockLatitude == preset.latitude &&
                                        settings.mockLongitude == preset.longitude,
                                    onClick = {
                                        latInput = preset.latitude.toString()
                                        lngInput = preset.longitude.toString()
                                        onSelectCityPreset(preset)
                                    },
                                    label = { Text(preset.name) },
                                    modifier = Modifier.testTag("mock_city_${preset.name}")
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val lat = latInput.toDoubleOrNull()
                                val lng = lngInput.toDoubleOrNull()
                                if (lat != null && lng != null) {
                                    onUpdateCoordinates(lat, lng)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("setting_apply_coords_button")
                        ) {
                            Text(stringResource(R.string.action_apply))
                        }
                        }
                    }

                    val networkCard: @Composable () -> Unit = {
                        // Section 3: Network & VPN
                        SettingsSectionCard(title = stringResource(R.string.settings_section_network)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSystemVpnActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VpnLock,
                                        contentDescription = null,
                                        tint = if (isSystemVpnActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(
                                            if (isSystemVpnActive) {
                                                R.string.settings_vpn_active
                                            } else {
                                                R.string.settings_vpn_inactive
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.testTag("vpn_status_text")
                                    )
                                }
                                IconButton(
                                    onClick = onRefreshVpnStatus,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.cd_nav_refresh),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        SettingSwitchRow(
                            icon = Icons.Rounded.VpnLock,
                            title = stringResource(R.string.settings_proxy_enable),
                            tip = stringResource(R.string.settings_proxy_tip),
                            checked = proxyEnabledDraft,
                            onCheckedChange = { enabled ->
                                proxyEnabledDraft = enabled
                                if (!enabled) {
                                    onApplyProxy(false, proxySchemeDraft, proxyHostDraft, proxyPortDraft)
                                }
                            },
                            testTag = "setting_proxy_switch"
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProxyScheme.entries.forEach { scheme ->
                                FilterChip(
                                    selected = proxySchemeDraft == scheme,
                                    onClick = { proxySchemeDraft = scheme },
                                    label = { Text(scheme.label) }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = proxyHostDraft,
                                onValueChange = { proxyHostDraft = it },
                                label = { Text(stringResource(R.string.settings_proxy_host)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(2f)
                                    .testTag("setting_proxy_host_input")
                            )
                            OutlinedTextField(
                                value = proxyPortDraft,
                                onValueChange = { proxyPortDraft = it },
                                label = { Text(stringResource(R.string.settings_proxy_port)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("setting_proxy_port_input")
                            )
                        }

                        Button(
                            onClick = {
                                onApplyProxy(proxyEnabledDraft, proxySchemeDraft, proxyHostDraft, proxyPortDraft)
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("setting_apply_proxy_button")
                        ) {
                            Text(stringResource(R.string.action_apply))
                        }
                        }
                    }

                    val engineCard: @Composable () -> Unit = {
                        // Section 4: Engine & Hardware Mode
                        SettingsSectionCard(title = stringResource(R.string.settings_section_engine)) {
                        SettingSwitchRow(
                            icon = Icons.Rounded.KeyboardHide,
                            title = stringResource(R.string.settings_hardware_keyboard),
                            tip = stringResource(R.string.settings_hardware_keyboard_tip),
                            checked = settings.disableSoftKeyboard,
                            onCheckedChange = onToggleDisableSoftKeyboard,
                            testTag = "setting_hardware_keyboard_switch"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SettingSwitchRow(
                            icon = Icons.Rounded.DarkMode,
                            title = stringResource(R.string.settings_force_dark),
                            tip = stringResource(R.string.settings_force_dark_tip),
                            checked = settings.forceDarkWebPages,
                            onCheckedChange = onToggleForceDark,
                            testTag = "setting_force_dark_switch"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SettingSwitchRow(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.settings_js_enabled),
                            tip = stringResource(R.string.settings_js_enabled_tip),
                            checked = settings.javaScriptEnabled,
                            onCheckedChange = onToggleJavaScript,
                            testTag = "setting_js_enabled_switch"
                        )
                        }
                    }

                    val productivityCard: @Composable () -> Unit = {
                        // Section 5: AI, Tooltips & Outside-App Tools
                        SettingsSectionCard(title = stringResource(R.string.settings_section_productivity)) {
                            SettingSwitchRow(
                                icon = Icons.Rounded.AutoAwesome,
                                title = stringResource(R.string.settings_ai_analyzing),
                                tip = stringResource(R.string.settings_ai_analyzing_tip),
                                checked = settings.aiWebAnalyzingEnabled,
                                onCheckedChange = onToggleAiAnalyzing,
                                testTag = "setting_ai_analyzing_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SettingSwitchRow(
                                icon = Icons.Rounded.Info,
                                title = stringResource(R.string.settings_tooltips),
                                tip = stringResource(R.string.settings_tooltips_tip),
                                checked = settings.showTooltips,
                                onCheckedChange = onToggleTooltips,
                                testTag = "setting_tooltips_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SettingSwitchRow(
                                icon = Icons.Rounded.BubbleChart,
                                title = stringResource(R.string.settings_floating_bubble),
                                tip = stringResource(R.string.settings_floating_bubble_tip),
                                checked = settings.floatingBubbleEnabled,
                                onCheckedChange = onToggleFloatingBubble,
                                testTag = "setting_floating_bubble_switch"
                            )

                            FilledTonalButton(
                                onClick = onStartTourGuide,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setting_start_tour_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HelpOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_start_tour))
                            }
                        }
                    }

                    val startupCard: @Composable () -> Unit = {
                        SettingsSectionCard(title = stringResource(R.string.settings_section_startup)) {
                            SettingSwitchRow(
                                icon = Icons.Rounded.Home,
                                title = stringResource(R.string.settings_start_page_enable),
                                tip = stringResource(R.string.settings_start_page_tip),
                                checked = settings.startPageEnabled,
                                onCheckedChange = onToggleStartPage,
                                testTag = "setting_start_page_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customHomeInput,
                                    onValueChange = { customHomeInput = it },
                                    label = { Text(stringResource(R.string.settings_custom_home_label)) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setting_custom_home_input")
                                )
                                Button(
                                    onClick = { onUpdateCustomHomeUrl(customHomeInput) },
                                    enabled = customHomeInput.isNotBlank(),
                                    modifier = Modifier.testTag("setting_custom_home_apply")
                                ) {
                                    Text(stringResource(R.string.action_apply))
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            SettingSwitchRow(
                                icon = Icons.Rounded.Restore,
                                title = stringResource(R.string.settings_restore_last_page),
                                tip = stringResource(R.string.settings_restore_last_page_tip),
                                checked = settings.restoreLastPageOnStartup,
                                onCheckedChange = onToggleRestoreLastPage,
                                testTag = "setting_restore_last_page_switch"
                            )
                        }
                    }

                    val updatesCard: @Composable () -> Unit = {
                        SettingsSectionCard(title = stringResource(R.string.settings_section_updates)) {
                            val statusText = when (appUpdateState) {
                                is AppUpdateUiState.Idle -> "Installed version: v${BuildConfig.VERSION_NAME}"
                                is AppUpdateUiState.Checking -> stringResource(R.string.settings_checking_updates)
                                is AppUpdateUiState.UpToDate -> stringResource(
                                    R.string.update_up_to_date,
                                    appUpdateState.localVersion
                                )
                                is AppUpdateUiState.Available -> stringResource(
                                    R.string.update_dialog_title,
                                    appUpdateState.release.versionName
                                )
                                is AppUpdateUiState.Downloading -> stringResource(
                                    R.string.update_downloading_progress,
                                    appUpdateState.progressPercent
                                )
                                is AppUpdateUiState.ReadyToInstall -> "APK ready to install (v${appUpdateState.release.versionName})"
                                is AppUpdateUiState.Error -> appUpdateState.message
                            }

                            SettingSwitchRow(
                                icon = Icons.Rounded.SystemUpdate,
                                title = stringResource(R.string.settings_auto_update_check),
                                tip = stringResource(R.string.settings_auto_update_check_tip),
                                checked = settings.autoCheckUpdatesOnStartup,
                                onCheckedChange = onToggleAutoCheckUpdates,
                                testTag = "setting_auto_update_switch"
                            )

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (appUpdateState is AppUpdateUiState.Error) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("setting_update_status_text")
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = onCheckForUpdates,
                                    enabled = appUpdateState !is AppUpdateUiState.Checking &&
                                        appUpdateState !is AppUpdateUiState.Downloading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setting_check_updates_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appUpdateState is AppUpdateUiState.Checking) {
                                            stringResource(R.string.settings_checking_updates)
                                        } else {
                                            stringResource(R.string.settings_check_updates_button)
                                        }
                                    )
                                }

                                if (appUpdateState is AppUpdateUiState.Available ||
                                    appUpdateState is AppUpdateUiState.Downloading ||
                                    appUpdateState is AppUpdateUiState.ReadyToInstall ||
                                    (appUpdateState is AppUpdateUiState.Error && appUpdateState.release != null)
                                ) {
                                    Button(
                                        onClick = onOpenUpdateDialog,
                                        modifier = Modifier.testTag("setting_open_update_dialog_button")
                                    ) {
                                        Text(stringResource(R.string.update_dialog_update_now))
                                    }
                                }
                            }
                        }
                    }

                    if (useTwoColumns) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = containerModifier
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                viewportCard()
                                startupCard()
                                productivityCard()
                                privacyCard()
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                locationCard()
                                networkCard()
                                engineCard()
                                updatesCard()
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = containerModifier
                        ) {
                            viewportCard()
                            startupCard()
                            productivityCard()
                            privacyCard()
                            locationCard()
                            networkCard()
                            engineCard()
                            updatesCard()
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    tip: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
