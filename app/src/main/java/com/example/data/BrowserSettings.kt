package com.example.data

enum class ProxyScheme(val prefix: String, val label: String) {
    HTTP("http://", "HTTP"),
    SOCKS5("socks5://", "SOCKS5")
}

data class CityPreset(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object MockLocationPresets {
    val cities = listOf(
        CityPreset("New York", 40.7128, -74.0060),
        CityPreset("London", 51.5074, -0.1278),
        CityPreset("Tokyo", 35.6762, 139.6503),
        CityPreset("Lagos", 6.5244, 3.3792)
    )
}

object DesktopViewportPresets {
    val widthsPx = listOf(1024, 1280, 1440, 1600, 1920, 2560)
    val uiScales = listOf(
        0.52f to "0.52x Ultra",
        0.56f to "0.56x PC",
        0.72f to "0.72x Mid",
        1.0f to "1.0x Native"
    )
}

data class BrowserSettings(
    val desktopViewportWidthPx: Int = 1280,
    val desktopLandscapeViewportWidthPx: Int = 1920,
    val desktopUiScale: Float = 0.56f,
    val adBlockerEnabled: Boolean = true,
    val mockLocationEnabled: Boolean = false,
    val mockLatitude: Double = 40.7128,
    val mockLongitude: Double = -74.0060,
    val disableSoftKeyboard: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyScheme: ProxyScheme = ProxyScheme.HTTP,
    val proxyHost: String = "",
    val proxyPort: String = "8080",
    val forceDarkWebPages: Boolean = false,
    val blockThirdPartyCookies: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val aiWebAnalyzingEnabled: Boolean = true,
    val showTooltips: Boolean = true,
    val hasCompletedTour: Boolean = false,
    val floatingBubbleEnabled: Boolean = false,
    val startPageEnabled: Boolean = true,
    val customHomeUrl: String = "https://www.google.com",
    val restoreLastPageOnStartup: Boolean = true,
    val autoCheckUpdatesOnStartup: Boolean = true
)

data class PersistedTabSnapshot(
    val url: String,
    val title: String,
    val isDesktopMode: Boolean = true,
    val isLocked: Boolean = false,
    val groupName: String? = null,
    val groupColorHex: Long = 0xFF38BDF8L
)

data class PersistedSessionState(
    val tabs: List<PersistedTabSnapshot>,
    val activeIndex: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
)
