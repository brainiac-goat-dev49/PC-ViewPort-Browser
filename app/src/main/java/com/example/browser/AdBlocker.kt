package com.example.browser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    val BLOCKED_DOMAINS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "://facebook.com",
        "connect.facebook.net",
        "facebook.net",
        "ads-twitter.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "scorecardresearch.com",
        "quantserve.com",
        "amazon-adsystem.com",
        "moatads.com",
        "appsflyer.com",
        "mixpanel.com",
        "segment.io"
    )

    fun isBlockedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = url.lowercase()
        return BLOCKED_DOMAINS.any { pattern -> normalized.contains(pattern) }
    }

    fun shouldBlock(url: String?, isForMainFrame: Boolean, enabled: Boolean): Boolean {
        if (!enabled || isForMainFrame) return false
        return isBlockedUrl(url)
    }

    fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
