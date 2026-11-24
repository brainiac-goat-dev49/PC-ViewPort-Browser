package com.example

import com.example.browser.AdBlocker
import com.example.browser.BrowserScripts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerAndScriptsTest {

    @Test
    fun `adBlocker blocks common ad and tracker domains on subresource requests`() {
        val blockedUrls = listOf(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://stats.g.doubleclick.net/r/collect",
            "https://www.google-analytics.com/analytics.js",
            "https://ib.adnxs.com/ut/v3",
            "https://cdn.taboola.com/libtrc/loader.js",
            "https://widgets.outbrain.com/outbrain.js",
            "https://facebook.com/tr/?id=123"
        )

        blockedUrls.forEach { url ->
            assertTrue(
                "Expected $url to be blocked",
                AdBlocker.shouldBlock(url, isForMainFrame = false, enabled = true)
            )
        }
    }

    @Test
    fun `adBlocker allows main frame navigation and non-tracker urls or when disabled`() {
        assertFalse(
            AdBlocker.shouldBlock(
                "https://facebook.com",
                isForMainFrame = true,
                enabled = true
            )
        )
        assertFalse(
            AdBlocker.shouldBlock(
                "https://developer.android.com/jetpack",
                isForMainFrame = false,
                enabled = true
            )
        )
        assertFalse(
            AdBlocker.shouldBlock(
                "https://doubleclick.net/ad.js",
                isForMainFrame = false,
                enabled = false
            )
        )
    }

    @Test
    fun `mock geolocation shim embeds custom coordinates and overrides navigator geolocation`() {
        val shim = BrowserScripts.buildGeolocationShim(latitude = 6.5244, longitude = 3.3792)
        assertTrue(shim.contains("latitude: 6.5244"))
        assertTrue(shim.contains("longitude: 3.3792"))
        assertTrue(shim.contains("getCurrentPosition"))
        assertTrue(shim.contains("watchPosition"))
        assertTrue(shim.contains("Object.defineProperty(navigator, 'geolocation'"))
    }

    @Test
    fun `es module injector wraps user script inside script type module`() {
        val code = "import confetti from 'https://esm.sh/canvas-confetti'; await Promise.resolve(1);"
        val script = BrowserScripts.buildEsModuleInjectionScript(code)
        assertTrue(script.contains("script.type = 'module'"))
        assertTrue(script.contains("canvas-confetti"))
        assertTrue(script.contains("appendChild(script)"))
    }

    @Test
    fun `desktop viewport script clamps dimensions and overrides screen resolution`() {
        val script = BrowserScripts.buildDesktopViewportScript(
            desktopWidthPx = 1440,
            fitScale = 0.285f,
            desktopHeightPx = 2304
        )
        assertTrue(script.contains("const targetW = 1440;"))
        assertTrue(script.contains("const targetH = 2304;"))
        assertTrue(script.contains("const targetScale = 0.285;"))
        assertTrue(script.contains("Object.defineProperty(window.screen, 'width'"))
        assertTrue(script.contains("Object.defineProperty(window, 'outerWidth'"))
        assertTrue(script.contains("Object.defineProperty(window, 'outerHeight'"))
        assertTrue(script.contains("pc-viewport-force-style"))
        assertTrue(script.contains("MutationObserver"))

        val clampedLow = BrowserScripts.buildDesktopViewportScript(
            desktopWidthPx = 300,
            fitScale = 0.01f,
            desktopHeightPx = 200
        )
        assertTrue(clampedLow.contains("const targetW = 640;"))
        assertTrue(clampedLow.contains("const targetH = 480;"))
        assertTrue(clampedLow.contains("const targetScale = 0.150;"))
    }

    @Test
    fun `mobile viewport restore script disables desktop override and removes style lock`() {
        val script = BrowserScripts.buildMobileViewportRestoreScript()
        assertTrue(script.contains("window.__pcDesktopActive = false;"))
        assertTrue(script.contains("pc-viewport-force-style"))
        assertTrue(script.contains("width=device-width, initial-scale=1.0"))
    }

    @Test
    fun `autofill password script escapes credentials safely and dispatches events`() {
        val script = BrowserScripts.buildAutofillPasswordScript(
            username = "dev@example.com",
            password = "p@ss\"word<1>"
        )
        assertTrue(script.contains("\"dev@example.com\""))
        assertTrue(script.contains("p@ss\\\"word\\u003C1\\u003E"))
        assertTrue(script.contains("input[type=\"password\"]"))
        assertTrue(script.contains("dispatchEvent(new Event('input'"))
        assertTrue(script.contains("FILLED_"))
    }

    @Test
    fun `extraction scripts target page content, media, and pwa manifest`() {
        assertTrue(BrowserScripts.EXTRACT_PAGE_CONTENT_SCRIPT.contains("bodyText"))
        assertTrue(BrowserScripts.EXTRACT_PAGE_CONTENT_SCRIPT.contains("selectedText"))
        assertTrue(BrowserScripts.EXTRACT_MEDIA_SCRIPT.contains("video, video source"))
        assertTrue(BrowserScripts.EXTRACT_MEDIA_SCRIPT.contains("audio, audio source"))
        assertTrue(BrowserScripts.EXTRACT_MEDIA_SCRIPT.contains("img"))
        assertTrue(BrowserScripts.EXTRACT_PWA_MANIFEST_SCRIPT.contains("link[rel=\"manifest\"]"))
    }
}
