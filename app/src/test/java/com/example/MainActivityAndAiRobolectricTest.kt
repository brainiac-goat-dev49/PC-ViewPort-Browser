package com.example

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiAnalysisMode
import com.example.ai.AiAnalysisUiState
import com.example.ai.AiWebAnalyzerRepository
import com.example.ai.PageSnapshot
import com.example.ai.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityAndAiRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testConfigureAppCheckAndAiRepositoryStream() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        configureAppCheck(context, Intent())
        val model = Firebase.ai.generativeModel("gemini-3.8-flash")
        assertTrue(model != null)

        val repo = AiWebAnalyzerRepository()
        val snapshot = PageSnapshot(
            title = "Example",
            url = "https://example.com",
            description = "Test",
            selectedText = "",
            headings = listOf("Heading"),
            bodyText = "Hello world",
            linkCount = 1,
            mediaCount = 0
        )
        val states = repo.analyzePageStream(snapshot, AiAnalysisMode.SUMMARY)
            .take(2)
            .toList()
        println("AI_STATES: $states")
        assertTrue(states.first() is AiAnalysisUiState.Loading)
    }

    @Test
    fun testStartPageAndLiveSearchSuggestionsAndHistoryFlow() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_page").assertIsDisplayed()

        // Type into Start Page search bar and verify live suggestions appear
        composeTestRule.onNodeWithTag("start_page_search_input").performTextInput("github.com")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_page_suggestions_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_page_suggestion_0").assertIsDisplayed()

        // Also verify omnibox typing opens live suggestion popup
        composeTestRule.onNodeWithTag("omnibox_input").performClick()
        composeTestRule.onNodeWithTag("omnibox_input").performTextInput("kotlin")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("omnibox_suggestions_popup").assertIsDisplayed()

        // Open History Page from Start Page quick tool chip and verify empty state
        composeTestRule.onNodeWithTag("start_page_tool_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_empty_state").assertIsDisplayed()

        // Return to Start Page
        composeTestRule.onNodeWithTag("history_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_page").assertIsDisplayed()
    }

    @Test
    fun testLaunchMainActivityAndClickAiAnalystButton() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_analyst_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_analyst_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_run_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_output_card").assertIsDisplayed()
    }
}
