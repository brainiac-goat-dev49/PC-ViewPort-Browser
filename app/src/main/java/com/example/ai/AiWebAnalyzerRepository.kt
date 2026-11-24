package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class AiAnalysisMode(val label: String, val instruction: String) {
    SUMMARY(
        label = "Summary",
        instruction = "Provide a concise, well-structured executive summary of this web page with key topics and purpose."
    ),
    KEY_POINTS(
        label = "Key Takeaways",
        instruction = "Extract the most important facts, claims, stats, and actionable takeaways from this web page as clear bullet points."
    ),
    EXPLAIN_SIMPLE(
        label = "Explain Simply",
        instruction = "Explain what this web page is about in plain, clear language as if briefing a busy developer or researcher."
    ),
    PRIVACY_AUDIT(
        label = "Trust & Audit",
        instruction = "Analyze the page structure, links, claims, and potential privacy/tracker or credibility signals based strictly on the provided page content."
    )
}

data class PageSnapshot(
    val title: String,
    val url: String,
    val description: String,
    val selectedText: String,
    val headings: List<String>,
    val bodyText: String,
    val linkCount: Int,
    val mediaCount: Int
)

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data object Extracting : AiAnalysisUiState
    data object Loading : AiAnalysisUiState
    data class Streaming(val text: String) : AiAnalysisUiState
    data class Success(val text: String) : AiAnalysisUiState
    data class Error(val message: String) : AiAnalysisUiState
}

interface WebAnalyzer {
    fun analyzePageStream(
        snapshot: PageSnapshot,
        mode: AiAnalysisMode,
        customQuestion: String = ""
    ): Flow<AiAnalysisUiState>
}

class AiWebAnalyzerRepository : WebAnalyzer {

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    override fun analyzePageStream(
        snapshot: PageSnapshot,
        mode: AiAnalysisMode,
        customQuestion: String
    ): Flow<AiAnalysisUiState> = flow {
        emit(AiAnalysisUiState.Loading)
        val prompt = buildPrompt(snapshot, mode, customQuestion)
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiAnalysisUiState.Streaming(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                emit(AiAnalysisUiState.Success(accumulated.toString()))
            } else {
                emit(AiAnalysisUiState.Error("Gemini returned an empty analysis for this page."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI web analysis timed out", e)
            emit(AiAnalysisUiState.Error("Analysis timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI web analysis failed", e)
            emit(
                AiAnalysisUiState.Error(
                    e.localizedMessage ?: "Failed to analyze web page with Gemini."
                )
            )
        }
    }

    private fun buildPrompt(
        snapshot: PageSnapshot,
        mode: AiAnalysisMode,
        customQuestion: String
    ): String {
        val taskInstruction = if (customQuestion.isNotBlank()) {
            "Answer the user's specific question about this web page accurately using the extracted page content: $customQuestion"
        } else {
            mode.instruction
        }
        val headingsText = if (snapshot.headings.isNotEmpty()) {
            snapshot.headings.joinToString("\n- ", prefix = "- ")
        } else {
            "(none extracted)"
        }
        val selectionSection = if (snapshot.selectedText.isNotBlank()) {
            "\nHighlighted User Selection:\n${snapshot.selectedText.take(2000)}\n"
        } else {
            ""
        }
        return """
            You are an intelligent web analyst inside a desktop web browser.
            Task: $taskInstruction

            Page Title: ${snapshot.title}
            Page URL: ${snapshot.url}
            Meta Description: ${snapshot.description}
            Links Detected: ${snapshot.linkCount} | Media Elements: ${snapshot.mediaCount}

            Key Headings:
            $headingsText
            $selectionSection
            Extracted Page Content:
            ${snapshot.bodyText.take(12_000)}
        """.trimIndent()
    }

    companion object {
        private const val TAG = "AiWebAnalyzer"
    }
}
