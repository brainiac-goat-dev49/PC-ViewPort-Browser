package com.example

import com.example.browser.BrowserConfig
import com.example.browser.UrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {

    @Test
    fun `blank input returns null`() {
        assertNull(UrlResolver.resolveInput(""))
        assertNull(UrlResolver.resolveInput("   "))
    }

    @Test
    fun `explicit http and https urls are preserved`() {
        assertEquals("https://example.com/docs", UrlResolver.resolveInput("https://example.com/docs"))
        assertEquals("http://example.org", UrlResolver.resolveInput("  http://example.org  "))
    }

    @Test
    fun `bare domains prepend https`() {
        assertEquals("https://github.com", UrlResolver.resolveInput("github.com"))
        assertEquals("https://en.wikipedia.org/wiki/Kotlin", UrlResolver.resolveInput("en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun `localhost and ip addresses prepend http`() {
        assertEquals("http://localhost:3000", UrlResolver.resolveInput("localhost:3000"))
        assertEquals("http://192.168.1.10/admin", UrlResolver.resolveInput("192.168.1.10/admin"))
    }

    @Test
    fun `plain search terms route to google search`() {
        assertEquals(
            "${BrowserConfig.GOOGLE_SEARCH_PREFIX}jetpack+compose+webview",
            UrlResolver.resolveInput("jetpack compose webview")
        )
        assertEquals(
            "${BrowserConfig.GOOGLE_SEARCH_PREFIX}kotlin",
            UrlResolver.resolveInput("kotlin")
        )
    }

    @Test
    fun `start page aliases and internal pages resolve accurately`() {
        assertEquals(BrowserConfig.START_PAGE_URL, UrlResolver.resolveInput("about:start"))
        assertEquals(BrowserConfig.START_PAGE_URL, UrlResolver.resolveInput(BrowserConfig.START_PAGE_URL))
        assertTrue(UrlResolver.isStartPage(BrowserConfig.START_PAGE_URL))
        assertTrue(UrlResolver.isStartPage("about:start"))
        assertTrue(UrlResolver.isInternalPage(BrowserConfig.START_PAGE_URL))
        assertFalse(UrlResolver.isInternalPage("https://github.com"))
        assertEquals("Start Page", UrlResolver.extractDisplayHost(BrowserConfig.START_PAGE_URL))
    }

    @Test
    fun `looksLikeUrlOrDomain distinguishes domains from search queries`() {
        assertTrue(UrlResolver.looksLikeUrlOrDomain("github.com"))
        assertTrue(UrlResolver.looksLikeUrlOrDomain("https://developer.android.com"))
        assertTrue(UrlResolver.looksLikeUrlOrDomain("localhost:8080"))
        assertFalse(UrlResolver.looksLikeUrlOrDomain("compose desktop browser"))
        assertFalse(UrlResolver.looksLikeUrlOrDomain("kotlin"))
    }

    @Test
    fun `extractDisplayHost strips www prefix`() {
        assertEquals("google.com", UrlResolver.extractDisplayHost("https://www.google.com/search?q=test"))
        assertEquals("github.com", UrlResolver.extractDisplayHost("https://github.com/android"))
    }
}
