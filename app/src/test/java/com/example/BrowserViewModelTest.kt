package com.example

import com.example.ai.AiAnalysisMode
import com.example.ai.AiAnalysisUiState
import com.example.ai.PageSnapshot
import com.example.ai.WebAnalyzer
import com.example.browser.BrowserConfig
import com.example.data.BookmarkRepository
import com.example.data.CityPreset
import com.example.data.MockLocationPresets
import com.example.news.NewsArticle
import com.example.news.NewsCategory
import com.example.news.NewsFeedSource
import com.example.ui.ActiveToolPage
import com.example.ui.BrowserTourSteps
import com.example.ui.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeWebAnalyzer(
    private val chunks: List<String> = listOf("Summary part 1. ", "Summary part 2."),
    private val shouldFail: Boolean = false
) : WebAnalyzer {
    var callCount: Int = 0
        private set

    override fun analyzePageStream(
        snapshot: PageSnapshot,
        mode: AiAnalysisMode,
        customQuestion: String
    ): Flow<AiAnalysisUiState> = flow {
        callCount++
        emit(AiAnalysisUiState.Loading)
        if (shouldFail) {
            emit(AiAnalysisUiState.Error("Simulated analyzer error"))
            return@flow
        }
        val sb = StringBuilder()
        chunks.forEach { chunk ->
            sb.append(chunk)
            emit(AiAnalysisUiState.Streaming(sb.toString()))
        }
        emit(AiAnalysisUiState.Success(sb.toString()))
    }
}

private class FakeNewsFeedSource(
    private val failCategories: Set<NewsCategory> = emptySet()
) : NewsFeedSource {
    override suspend fun fetchCategory(category: NewsCategory): Result<List<NewsArticle>> {
        if (category in failCategories) {
            return Result.failure(IllegalStateException("Network unavailable"))
        }
        return Result.success(
            listOf(
                NewsArticle(
                    id = "art-${category.name}-1",
                    title = "Breaking ${category.label} Story",
                    link = "https://news.example.com/${category.name.lowercase()}/1",
                    summary = "Concise summary",
                    source = "Example News",
                    publishedAt = "2026-11-24"
                )
            )
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: BookmarkRepository
    private lateinit var fakeAnalyzer: FakeWebAnalyzer
    private lateinit var fakeNews: FakeNewsFeedSource
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = BookmarkRepository(FakeBookmarkDao())
        fakeAnalyzer = FakeWebAnalyzer()
        fakeNews = FakeNewsFeedSource(failCategories = setOf(NewsCategory.SCIENCE))
        viewModel = BrowserViewModel(
            bookmarkRepository = repository,
            newsFeedSource = fakeNews,
            webAnalyzer = fakeAnalyzer
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial tab uses desktop mode and Windows 11 Chrome UA constant`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(1, state.tabs.size)
        assertTrue(state.activeTab!!.isDesktopMode)
        assertFalse(state.activeTab!!.isIncognito)
        assertTrue(state.settings.aiWebAnalyzingEnabled)
        assertTrue(state.settings.showTooltips)
        assertEquals(BrowserConfig.DEFAULT_HOME_URL, state.activeTab!!.url)
        assertTrue(BrowserConfig.DESKTOP_USER_AGENT.contains("Windows NT 10.0; Win64; x64"))
        assertTrue(BrowserConfig.DESKTOP_USER_AGENT.contains("Chrome/131.0.0.0"))
    }

    @Test
    fun `multi-tab open switch and close updates active tab safely`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val firstTabId = viewModel.uiState.value.activeTabId
        viewModel.openNewTab("github.com")
        val afterOpen = viewModel.uiState.value
        assertEquals(2, afterOpen.tabs.size)
        val secondTabId = afterOpen.activeTabId
        assertNotEquals(firstTabId, secondTabId)
        assertEquals("https://github.com", afterOpen.activeTab!!.url)

        viewModel.selectTab(firstTabId)
        assertEquals(firstTabId, viewModel.uiState.value.activeTabId)

        viewModel.closeTab(firstTabId)
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertEquals(secondTabId, viewModel.uiState.value.activeTabId)

        viewModel.closeTab(secondTabId)
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertNotEquals(secondTabId, viewModel.uiState.value.activeTabId)
        assertEquals(BrowserConfig.DEFAULT_HOME_URL, viewModel.uiState.value.activeTab!!.url)
    }

    @Test
    fun `incognito tab tab locking and tab grouping work accurately`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        val baseId = viewModel.uiState.value.activeTabId
        viewModel.openNewTab("https://private.example.com", isIncognito = true)
        val incognitoTab = viewModel.uiState.value.activeTab!!
        assertTrue(incognitoTab.isIncognito)
        assertEquals("Incognito", incognitoTab.title)

        // Lock incognito tab and verify closeTab refuses to close while locked
        viewModel.toggleTabLock(incognitoTab.id)
        assertTrue(viewModel.uiState.value.activeTab!!.isLocked)
        viewModel.closeTab(incognitoTab.id)
        assertEquals(2, viewModel.uiState.value.tabs.size)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("locked", ignoreCase = true) == true)

        // Assign group & filter
        viewModel.assignTabGroup(incognitoTab.id, "Research", 0xFF34D399L)
        assertEquals("Research", viewModel.uiState.value.activeTab!!.groupName)
        viewModel.selectGroupFilter("Research")
        assertEquals("Research", viewModel.uiState.value.activeGroupFilter)

        // Unlock and close
        viewModel.toggleTabLock(incognitoTab.id)
        viewModel.closeTab(incognitoTab.id)
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertEquals(baseId, viewModel.uiState.value.activeTabId)
    }

    @Test
    fun `ai web analyzing defaults enabled, toggles off, and saves result to notes`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        // Enabled by default
        assertTrue(viewModel.uiState.value.settings.aiWebAnalyzingEnabled)
        viewModel.openAiAnalystPage(autoRun = true)
        assertEquals(ActiveToolPage.AI_ANALYST, viewModel.uiState.value.activeToolPage)
        assertEquals(1, fakeAnalyzer.callCount)
        assertTrue(viewModel.uiState.value.aiAnalysisState is AiAnalysisUiState.Success)

        // Save AI analysis into Notes & Clips
        viewModel.saveAiResultToNotes()
        assertEquals(1, viewModel.uiState.value.noteClips.size)
        assertTrue(viewModel.uiState.value.noteClips.first().title.startsWith("AI Brief:"))

        // Disable AI Web Analysing in settings -> running analysis shows clear disabled message
        viewModel.setAiWebAnalyzingEnabled(false)
        assertFalse(viewModel.uiState.value.settings.aiWebAnalyzingEnabled)
        viewModel.analyzeActivePage(AiAnalysisMode.KEY_POINTS)
        assertEquals(1, fakeAnalyzer.callCount)
        assertTrue(viewModel.uiState.value.aiAnalysisState is AiAnalysisUiState.Error)

        // Re-enable atomically via enableAiAndAnalyze
        viewModel.enableAiAndAnalyze(AiAnalysisMode.EXPLAIN_SIMPLE)
        assertTrue(viewModel.uiState.value.settings.aiWebAnalyzingEnabled)
        assertEquals(2, fakeAnalyzer.callCount)
        assertTrue(viewModel.uiState.value.aiAnalysisState is AiAnalysisUiState.Success)
    }

    @Test
    fun `daily news loads categories and surfaces errors cleanly`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.openNewsPage(NewsCategory.TECH)
        assertEquals(ActiveToolPage.DAILY_NEWS, viewModel.uiState.value.activeToolPage)
        assertEquals(NewsCategory.TECH, viewModel.uiState.value.newsState.category)
        assertEquals(1, viewModel.uiState.value.newsState.articles.size)
        assertNull(viewModel.uiState.value.newsState.errorMessage)

        viewModel.loadNewsCategory(NewsCategory.SCIENCE)
        assertEquals(NewsCategory.SCIENCE, viewModel.uiState.value.newsState.category)
        assertTrue(viewModel.uiState.value.newsState.articles.isEmpty())
        assertEquals("Network unavailable", viewModel.uiState.value.newsState.errorMessage)
    }

    @Test
    fun `passwords and notes clips crud and tour guide steps update state`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.savePasswordCredential("GitHub", "https://github.com", "alice", "Secret!123")
        assertEquals(1, viewModel.uiState.value.passwords.size)
        val passId = viewModel.uiState.value.passwords.first().id
        viewModel.deletePasswordCredential(passId)
        assertTrue(viewModel.uiState.value.passwords.isEmpty())

        viewModel.saveNoteClip("Architecture Note", "Verify Room v3 migration", "https://developer.android.com")
        assertEquals(1, viewModel.uiState.value.noteClips.size)
        val noteId = viewModel.uiState.value.noteClips.first().id
        viewModel.deleteNoteClip(noteId)
        assertTrue(viewModel.uiState.value.noteClips.isEmpty())

        // Tour guide stepping and completion
        viewModel.startTourGuide()
        assertEquals(0, viewModel.uiState.value.tourStepIndex)
        viewModel.nextTourStep()
        assertEquals(1, viewModel.uiState.value.tourStepIndex)
        viewModel.previousTourStep()
        assertEquals(0, viewModel.uiState.value.tourStepIndex)
        repeat(BrowserTourSteps.steps.size) { viewModel.nextTourStep() }
        assertNull(viewModel.uiState.value.tourStepIndex)
        assertTrue(viewModel.uiState.value.settings.hasCompletedTour)
    }

    @Test
    fun `toggleDesktopMode flips active tab user agent mode`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.activeTab!!.isDesktopMode)
        val mobileMode = viewModel.toggleDesktopMode()
        assertFalse(mobileMode)
        assertFalse(viewModel.uiState.value.activeTab!!.isDesktopMode)

        val desktopMode = viewModel.toggleDesktopMode()
        assertTrue(desktopMode)
        assertTrue(viewModel.uiState.value.activeTab!!.isDesktopMode)
    }

    @Test
    fun `navigateTo resolves domain or google search and updates history callbacks`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        val tabId = viewModel.uiState.value.activeTabId

        viewModel.navigateTo("android compose")
        assertEquals(
            "${BrowserConfig.GOOGLE_SEARCH_PREFIX}android+compose",
            viewModel.uiState.value.activeTab!!.url
        )

        viewModel.onPageStarted(tabId, "https://developer.android.com")
        assertTrue(viewModel.uiState.value.activeTab!!.isLoading)

        viewModel.onReceivedTitle(tabId, "Android Developers")
        viewModel.onPageFinished(
            tabId = tabId,
            url = "https://developer.android.com",
            canGoBack = true,
            canGoForward = false
        )
        val updated = viewModel.uiState.value.activeTab!!
        assertFalse(updated.isLoading)
        assertEquals("Android Developers", updated.title)
        assertTrue(updated.canGoBack)
        assertFalse(updated.canGoForward)
    }

    @Test
    fun `bookmarking current page reflects in uiState`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.navigateTo("https://example.com")
        viewModel.onReceivedTitle(viewModel.uiState.value.activeTabId, "Example Domain")

        var wasAdded = false
        viewModel.toggleBookmarkForCurrentPage { wasAdded = it }
        assertTrue(wasAdded)
        assertTrue(viewModel.uiState.value.isCurrentPageBookmarked)
        assertEquals(1, repository.bookmarks.first().size)
    }

    @Test
    fun `ad blocker counter and power settings update uiState`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        val tabId = viewModel.uiState.value.activeTabId

        viewModel.onAdBlocked(tabId, "https://doubleclick.net/ad.js")
        viewModel.onAdBlocked(tabId, "https://google-analytics.com/ga.js")
        assertEquals(2, viewModel.uiState.value.totalBlockedAds)

        val tokyo: CityPreset = MockLocationPresets.cities.first { it.name == "Tokyo" }
        viewModel.selectCityPreset(tokyo)
        assertTrue(viewModel.uiState.value.settings.mockLocationEnabled)
        assertEquals(35.6762, viewModel.uiState.value.settings.mockLatitude, 0.0001)
        assertEquals(139.6503, viewModel.uiState.value.settings.mockLongitude, 0.0001)

        viewModel.setDisableSoftKeyboard(true)
        assertTrue(viewModel.uiState.value.settings.disableSoftKeyboard)

        viewModel.setForceDarkWebPages(true)
        assertTrue(viewModel.uiState.value.settings.forceDarkWebPages)

        viewModel.setBlockThirdPartyCookies(true)
        assertTrue(viewModel.uiState.value.settings.blockThirdPartyCookies)

        viewModel.setJavaScriptEnabled(false)
        assertFalse(viewModel.uiState.value.settings.javaScriptEnabled)

        viewModel.setShowTooltips(false)
        assertFalse(viewModel.uiState.value.settings.showTooltips)
    }

    @Test
    fun `devtools console records commands and clears logs`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.openDevTools()
        assertTrue(viewModel.uiState.value.isDevToolsOpen)

        viewModel.runConsoleScript("console.log('hi')", asEsModule = true)
        assertTrue(viewModel.uiState.value.consoleLogs.any { it.message.contains("[ESM]") })

        viewModel.clearConsoleLogs()
        assertTrue(viewModel.uiState.value.consoleLogs.isEmpty())
    }

    @Test
    fun `desktop viewport width and ui scale update and clamp safely`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setDesktopViewportWidthPx(1440)
        assertEquals(1440, viewModel.uiState.value.settings.desktopViewportWidthPx)

        viewModel.setDesktopViewportWidthPx(9999)
        assertEquals(3840, viewModel.uiState.value.settings.desktopViewportWidthPx)

        viewModel.setDesktopViewportWidthPx(300)
        assertEquals(640, viewModel.uiState.value.settings.desktopViewportWidthPx)

        viewModel.setDesktopUiScale(0.72f)
        assertEquals(0.72f, viewModel.uiState.value.settings.desktopUiScale, 0.001f)

        viewModel.setDesktopUiScale(0.10f)
        assertEquals(0.45f, viewModel.uiState.value.settings.desktopUiScale, 0.001f)
    }

    @Test
    fun `full page tool overlays are mutually exclusive`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.openBookmarksSheet()
        assertTrue(viewModel.uiState.value.isBookmarksSheetOpen)
        assertFalse(viewModel.uiState.value.isDownloadsSheetOpen)
        assertFalse(viewModel.uiState.value.isSettingsSheetOpen)

        viewModel.openSettingsSheet()
        assertTrue(viewModel.uiState.value.isSettingsSheetOpen)
        assertFalse(viewModel.uiState.value.isBookmarksSheetOpen)
        assertFalse(viewModel.uiState.value.isDownloadsSheetOpen)

        viewModel.openDownloadsSheet()
        assertTrue(viewModel.uiState.value.isDownloadsSheetOpen)
        assertFalse(viewModel.uiState.value.isSettingsSheetOpen)
        assertFalse(viewModel.uiState.value.isBookmarksSheetOpen)

        viewModel.openQuickActionsPage()
        assertEquals(ActiveToolPage.QUICK_ACTIONS, viewModel.uiState.value.activeToolPage)
        assertFalse(viewModel.uiState.value.isDownloadsSheetOpen)
    }
}
