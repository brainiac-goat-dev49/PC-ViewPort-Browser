package com.example.browser

import android.graphics.Bitmap
import java.util.UUID

object TabGroupPalette {
    val presets: List<Pair<String, Long>> = listOf(
        "Research" to 0xFF38BDF8L,
        "Work" to 0xFF34D399L,
        "Media" to 0xFFF472B6L,
        "Shopping" to 0xFFFBBF24L,
        "Dev" to 0xFFA78BFAL
    )
}

data class BrowserTabState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = BrowserConfig.DEFAULT_HOME_URL,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = true,
    val blockedAdsCount: Int = 0,
    val favicon: Bitmap? = null,
    val isIncognito: Boolean = false,
    val isLocked: Boolean = false,
    val groupName: String? = null,
    val groupColorHex: Long = 0xFF38BDF8L
)
