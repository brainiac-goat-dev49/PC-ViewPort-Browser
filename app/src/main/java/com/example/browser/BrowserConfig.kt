package com.example.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserConfig {
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    const val START_PAGE_URL = "pcviewport://start"
    const val DEFAULT_HOME_URL = "https://www.google.com"
    const val GOOGLE_SEARCH_PREFIX = "https://www.google.com/search?q="

    const val GITHUB_REPO_OWNER = "brainiac-goat-dev49"
    const val GITHUB_REPO_NAME = "PC-ViewPort-Browser"
    const val GITHUB_LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
}

object UrlResolver {
    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    private val LOCALHOST_OR_IP_REGEX = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3})(:\\d+)?(/.*)?$",
        RegexOption.IGNORE_CASE
    )
    private val DOMAIN_REGEX = Regex(
        "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?([/?#].*)?$"
    )

    fun isStartPage(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        return clean.equals(BrowserConfig.START_PAGE_URL, ignoreCase = true) ||
            clean.equals("about:start", ignoreCase = true)
    }

    fun isInternalPage(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return true
        return isStartPage(clean) ||
            clean.startsWith("about:", ignoreCase = true) ||
            clean.startsWith("pcviewport://", ignoreCase = true)
    }

    fun looksLikeUrlOrDomain(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("about:") || lower.startsWith("file://") || lower.startsWith("pcviewport://")) {
            return true
        }
        if (SCHEME_REGEX.matches(trimmed)) return true
        if (!trimmed.contains(' ')) {
            if (LOCALHOST_OR_IP_REGEX.matches(trimmed)) return true
            if (DOMAIN_REGEX.matches(trimmed)) return true
        }
        return false
    }

    fun resolveInput(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        if (lower == "about:start" || lower == BrowserConfig.START_PAGE_URL) {
            return BrowserConfig.START_PAGE_URL
        }
        if (lower.startsWith("about:") || lower.startsWith("file://")) {
            return trimmed
        }

        if (SCHEME_REGEX.matches(trimmed)) {
            return trimmed
        }

        if (!trimmed.contains(' ')) {
            if (LOCALHOST_OR_IP_REGEX.matches(trimmed)) {
                return "http://$trimmed"
            }
            if (DOMAIN_REGEX.matches(trimmed)) {
                return "https://$trimmed"
            }
        }

        val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        return "${BrowserConfig.GOOGLE_SEARCH_PREFIX}$encodedQuery"
    }

    fun extractDisplayHost(url: String): String {
        if (url.isBlank()) return ""
        if (isStartPage(url)) return "Start Page"
        return try {
            val host = URI(url).host.orEmpty()
            host.removePrefix("www.").ifBlank { url }
        } catch (_: Exception) {
            url
        }
    }
}
