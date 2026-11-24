package com.example.news

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class NewsCategory(val label: String, val feedUrls: List<String>) {
    TOP(
        label = "Top Stories",
        feedUrls = listOf(
            "https://feeds.bbci.co.uk/news/rss.xml",
            "https://feeds.npr.org/1001/rss.xml"
        )
    ),
    TECH(
        label = "Technology",
        feedUrls = listOf(
            "https://feeds.arstechnica.com/arstechnica/technology-lab",
            "https://hnrss.org/frontpage"
        )
    ),
    WORLD(
        label = "World",
        feedUrls = listOf(
            "https://feeds.bbci.co.uk/news/world/rss.xml",
            "https://feeds.npr.org/1004/rss.xml"
        )
    ),
    SCIENCE(
        label = "Science",
        feedUrls = listOf(
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://www.sciencedaily.com/rss/top/science.xml"
        )
    ),
    BUSINESS(
        label = "Business",
        feedUrls = listOf(
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "https://feeds.npr.org/1006/rss.xml"
        )
    )
}

data class NewsArticle(
    val id: String,
    val title: String,
    val link: String,
    val summary: String,
    val source: String,
    val publishedAt: String,
    val imageUrl: String? = null
)

interface NewsFeedSource {
    suspend fun fetchCategory(category: NewsCategory): Result<List<NewsArticle>>
}

class DailyNewsRepository : NewsFeedSource {

    override suspend fun fetchCategory(category: NewsCategory): Result<List<NewsArticle>> =
        withContext(Dispatchers.IO) {
            val collected = mutableListOf<NewsArticle>()
            var lastError: Exception? = null

            for (feedUrl in category.feedUrls) {
                try {
                    val conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 12_000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; PCViewPort/1.0)")
                    }
                    try {
                        if (conn.responseCode in 200..299) {
                            conn.inputStream.use { stream ->
                                collected += parseRssFeed(stream, URL(feedUrl).host.removePrefix("www."))
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            val deduped = collected
                .distinctBy { it.link }
                .take(40)

            if (deduped.isNotEmpty()) {
                Result.success(deduped)
            } else {
                Result.failure(lastError ?: IllegalStateException("Unable to load RSS news feeds."))
            }
        }

    internal fun parseRssFeed(inputStream: InputStream, defaultSource: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var insideItem = false
        var channelTitle = defaultSource
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""
        var imageUrl: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase().orEmpty()
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "item", "entry" -> {
                            insideItem = true
                            title = ""
                            link = ""
                            description = ""
                            pubDate = ""
                            imageUrl = null
                        }
                        "title" -> {
                            val text = safeNextText(parser)
                            if (insideItem) title = stripHtml(text) else if (text.isNotBlank()) channelTitle = stripHtml(text)
                        }
                        "link" -> {
                            if (insideItem) {
                                val rel = parser.getAttributeValue(null, "rel")
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) {
                                    if (link.isBlank() || rel == null || rel == "alternate") {
                                        link = href.trim()
                                    }
                                } else {
                                    val text = safeNextText(parser)
                                    if (text.isNotBlank()) link = text.trim()
                                }
                            }
                        }
                        "description", "summary", "content:encoded" -> {
                            if (insideItem && description.isBlank()) {
                                description = stripHtml(safeNextText(parser)).take(240)
                            }
                        }
                        "pubdate", "updated", "published" -> {
                            if (insideItem && pubDate.isBlank()) {
                                pubDate = safeNextText(parser).trim()
                            }
                        }
                        "media:thumbnail", "media:content", "enclosure" -> {
                            if (insideItem && imageUrl == null) {
                                val urlAttr = parser.getAttributeValue(null, "url")
                                val typeAttr = parser.getAttributeValue(null, "type").orEmpty()
                                if (!urlAttr.isNullOrBlank() && (tagName != "enclosure" || typeAttr.startsWith("image"))) {
                                    imageUrl = urlAttr.trim()
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ((tagName == "item" || tagName == "entry") && insideItem) {
                        if (title.isNotBlank() && link.isNotBlank()) {
                            articles.add(
                                NewsArticle(
                                    id = "${link.hashCode()}_${articles.size}",
                                    title = title,
                                    link = link,
                                    summary = description,
                                    source = channelTitle,
                                    publishedAt = pubDate,
                                    imageUrl = imageUrl
                                )
                            )
                        }
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            if (parser.next() == XmlPullParser.TEXT) {
                parser.text.orEmpty()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun stripHtml(raw: String): String {
        return raw
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
