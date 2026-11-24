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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pc_viewport_settings"
)

interface SettingsStore {
    val settings: Flow<BrowserSettings>
    suspend fun update(transform: (BrowserSettings) -> BrowserSettings)
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
    }

    override val settings: Flow<BrowserSettings> = context.settingsDataStore.data.map { prefs ->
        readFromPrefs(prefs)
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
            floatingBubbleEnabled = prefs[Keys.FLOATING_BUBBLE] ?: false
        )
    }
}
