package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pc_viewport_settings"
)

interface SettingsStore {
    val settings: Flow<BrowserSettings>
    val sessionState: Flow<PersistedSessionState?>
    suspend fun update(transform: (BrowserSettings) -> BrowserSettings)
    suspend fun saveSessionState(session: PersistedSessionState)
    suspend fun clearSessionState()
}

class BrowserSettingsRepository(private val context: Context) : SettingsStore {

    private object Keys {
        val DESKTOP_WIDTH_PX = intPreferencesKey("desktop_viewport_width_px")
        val DESKTOP_LANDSCAPE_WIDTH_PX = intPreferencesKey("desktop_landscape_viewport_width_px")
        val DESKTOP_UI_SCALE = floatPreferencesKey("desktop_ui_scale")
        val AD_BLOCKER = booleanPreferencesKey("ad_blocker_enabled")
        val MOCK_LOCATION = booleanPreferencesKey("mock_location_enabled")
        val MOCK_LAT = doublePreferencesKey("mock_latitude")
        val MOCK_LNG = doublePreferencesKey("mock_longitude")
        val DISABLE_SOFT_KEYBOARD = booleanPreferencesKey("disable_soft_keyboard")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_SCHEME = stringPreferencesKey("proxy_scheme")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = stringPreferencesKey("proxy_port")
        val FORCE_DARK = booleanPreferencesKey("force_dark_web_pages")
        val BLOCK_3P_COOKIES = booleanPreferencesKey("block_third_party_cookies")
        val JS_ENABLED = booleanPreferencesKey("javascript_enabled")
        val AI_WEB_ANALYZING = booleanPreferencesKey("ai_web_analyzing_enabled")
        val SHOW_TOOLTIPS = booleanPreferencesKey("show_tooltips")
        val HAS_COMPLETED_TOUR = booleanPreferencesKey("has_completed_tour")
        val FLOATING_BUBBLE = booleanPreferencesKey("floating_bubble_enabled")
        val START_PAGE_ENABLED = booleanPreferencesKey("start_page_enabled")
        val CUSTOM_HOME_URL = stringPreferencesKey("custom_home_url")
        val RESTORE_LAST_PAGE = booleanPreferencesKey("restore_last_page_on_startup")
        val LAST_SESSION_JSON = stringPreferencesKey("last_browser_session_json")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates_on_startup")
    }

    override val settings: Flow<BrowserSettings> = context.settingsDataStore.data.map { prefs ->
        readFromPrefs(prefs)
    }

    override val sessionState: Flow<PersistedSessionState?> = context.settingsDataStore.data.map { prefs ->
        decodeSessionJson(prefs[Keys.LAST_SESSION_JSON])
    }

    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = readFromPrefs(prefs)
            val next = transform(current)
            prefs[Keys.DESKTOP_WIDTH_PX] = next.desktopViewportWidthPx
            prefs[Keys.DESKTOP_LANDSCAPE_WIDTH_PX] = next.desktopLandscapeViewportWidthPx
            prefs[Keys.DESKTOP_UI_SCALE] = next.desktopUiScale
            prefs[Keys.AD_BLOCKER] = next.adBlockerEnabled
            prefs[Keys.MOCK_LOCATION] = next.mockLocationEnabled
            prefs[Keys.MOCK_LAT] = next.mockLatitude
            prefs[Keys.MOCK_LNG] = next.mockLongitude
            prefs[Keys.DISABLE_SOFT_KEYBOARD] = next.disableSoftKeyboard
            prefs[Keys.PROXY_ENABLED] = next.proxyEnabled
            prefs[Keys.PROXY_SCHEME] = next.proxyScheme.name
            prefs[Keys.PROXY_HOST] = next.proxyHost
            prefs[Keys.PROXY_PORT] = next.proxyPort
            prefs[Keys.FORCE_DARK] = next.forceDarkWebPages
            prefs[Keys.BLOCK_3P_COOKIES] = next.blockThirdPartyCookies
            prefs[Keys.JS_ENABLED] = next.javaScriptEnabled
            prefs[Keys.AI_WEB_ANALYZING] = next.aiWebAnalyzingEnabled
            prefs[Keys.SHOW_TOOLTIPS] = next.showTooltips
            prefs[Keys.HAS_COMPLETED_TOUR] = next.hasCompletedTour
            prefs[Keys.FLOATING_BUBBLE] = next.floatingBubbleEnabled
            prefs[Keys.START_PAGE_ENABLED] = next.startPageEnabled
            prefs[Keys.CUSTOM_HOME_URL] = next.customHomeUrl
            prefs[Keys.RESTORE_LAST_PAGE] = next.restoreLastPageOnStartup
            prefs[Keys.AUTO_CHECK_UPDATES] = next.autoCheckUpdatesOnStartup
        }
    }

    override suspend fun saveSessionState(session: PersistedSessionState) {
        if (session.tabs.isEmpty()) return
        val encoded = encodeSessionJson(session)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LAST_SESSION_JSON] = encoded
        }
    }

    override suspend fun clearSessionState() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_SESSION_JSON)
        }
    }

    private fun readFromPrefs(prefs: Preferences): BrowserSettings {
        val schemeName = prefs[Keys.PROXY_SCHEME] ?: ProxyScheme.HTTP.name
        val scheme = runCatching { ProxyScheme.valueOf(schemeName) }.getOrDefault(ProxyScheme.HTTP)
        return BrowserSettings(
            desktopViewportWidthPx = prefs[Keys.DESKTOP_WIDTH_PX] ?: 1280,
            desktopLandscapeViewportWidthPx = prefs[Keys.DESKTOP_LANDSCAPE_WIDTH_PX] ?: 1920,
            desktopUiScale = prefs[Keys.DESKTOP_UI_SCALE] ?: 0.56f,
            adBlockerEnabled = prefs[Keys.AD_BLOCKER] ?: true,
            mockLocationEnabled = prefs[Keys.MOCK_LOCATION] ?: false,
            mockLatitude = prefs[Keys.MOCK_LAT] ?: 40.7128,
            mockLongitude = prefs[Keys.MOCK_LNG] ?: -74.0060,
            disableSoftKeyboard = prefs[Keys.DISABLE_SOFT_KEYBOARD] ?: false,
            proxyEnabled = prefs[Keys.PROXY_ENABLED] ?: false,
            proxyScheme = scheme,
            proxyHost = prefs[Keys.PROXY_HOST] ?: "",
            proxyPort = prefs[Keys.PROXY_PORT] ?: "8080",
            forceDarkWebPages = prefs[Keys.FORCE_DARK] ?: false,
            blockThirdPartyCookies = prefs[Keys.BLOCK_3P_COOKIES] ?: false,
            javaScriptEnabled = prefs[Keys.JS_ENABLED] ?: true,
            aiWebAnalyzingEnabled = prefs[Keys.AI_WEB_ANALYZING] ?: true,
            showTooltips = prefs[Keys.SHOW_TOOLTIPS] ?: true,
            hasCompletedTour = prefs[Keys.HAS_COMPLETED_TOUR] ?: false,
            floatingBubbleEnabled = prefs[Keys.FLOATING_BUBBLE] ?: false,
            startPageEnabled = prefs[Keys.START_PAGE_ENABLED] ?: true,
            customHomeUrl = prefs[Keys.CUSTOM_HOME_URL] ?: "https://www.google.com",
            restoreLastPageOnStartup = prefs[Keys.RESTORE_LAST_PAGE] ?: true,
            autoCheckUpdatesOnStartup = prefs[Keys.AUTO_CHECK_UPDATES] ?: true
        )
    }

    companion object {
        private const val FORMAT_HEADER = "PCVP_SESSION_V1"

        private fun encodeField(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")

        private fun decodeField(value: String): String =
            java.net.URLDecoder.decode(value, "UTF-8")

        fun encodeSessionJson(session: PersistedSessionState): String {
            return buildString {
                append(FORMAT_HEADER).append('\n')
                append(session.activeIndex).append('|').append(session.savedAt).append('\n')
                session.tabs.forEach { tab ->
                    append(encodeField(tab.url)).append('|')
                    append(encodeField(tab.title)).append('|')
                    append(tab.isDesktopMode).append('|')
                    append(tab.isLocked).append('|')
                    append(encodeField(tab.groupName.orEmpty())).append('|')
                    append(tab.groupColorHex).append('\n')
                }
            }
        }

        fun decodeSessionJson(raw: String?): PersistedSessionState? {
            if (raw.isNullOrBlank()) return null
            if (raw.trimStart().startsWith("{")) {
                return decodeLegacyJson(raw)
            }
            return runCatching {
                val lines = raw.lineSequence().filter { it.isNotEmpty() }.toList()
                if (lines.size < 3 || lines[0] != FORMAT_HEADER) return@runCatching null
                val meta = lines[1].split('|')
                val activeIndex = meta.getOrNull(0)?.toIntOrNull() ?: 0
                val savedAt = meta.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis()
                val tabs = lines.drop(2).mapNotNull { line ->
                    val parts = line.split('|')
                    if (parts.size < 6) return@mapNotNull null
                    val url = decodeField(parts[0]).trim()
                    if (url.isEmpty()) return@mapNotNull null
                    val title = decodeField(parts[1]).ifBlank { url }
                    val isDesktop = parts[2].toBooleanStrictOrNull() ?: true
                    val isLocked = parts[3].toBooleanStrictOrNull() ?: false
                    val groupName = decodeField(parts[4]).takeIf { it.isNotBlank() }
                    val groupColor = parts[5].toLongOrNull() ?: 0xFF38BDF8L
                    PersistedTabSnapshot(
                        url = url,
                        title = title,
                        isDesktopMode = isDesktop,
                        isLocked = isLocked,
                        groupName = groupName,
                        groupColorHex = groupColor
                    )
                }
                if (tabs.isEmpty()) return@runCatching null
                PersistedSessionState(
                    tabs = tabs,
                    activeIndex = activeIndex.coerceIn(0, tabs.lastIndex),
                    savedAt = savedAt
                )
            }.getOrNull()
        }

        private fun decodeLegacyJson(raw: String): PersistedSessionState? {
            return runCatching {
                val root = JSONObject(raw)
                val arr = root.optJSONArray("tabs") ?: return@runCatching null
                val tabs = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val url = obj.optString("url").trim()
                        if (url.isEmpty()) continue
                        add(
                            PersistedTabSnapshot(
                                url = url,
                                title = obj.optString("title").ifBlank { url },
                                isDesktopMode = obj.optBoolean("isDesktopMode", true),
                                isLocked = obj.optBoolean("isLocked", false),
                                groupName = obj.optString("groupName").takeIf { it.isNotBlank() },
                                groupColorHex = obj.optLong("groupColorHex", 0xFF38BDF8L)
                            )
                        )
                    }
                }
                if (tabs.isEmpty()) return@runCatching null
                PersistedSessionState(
                    tabs = tabs,
                    activeIndex = root.optInt("activeIndex", 0).coerceIn(0, tabs.lastIndex),
                    savedAt = root.optLong("savedAt", System.currentTimeMillis())
                )
            }.getOrNull()
        }
    }
}
