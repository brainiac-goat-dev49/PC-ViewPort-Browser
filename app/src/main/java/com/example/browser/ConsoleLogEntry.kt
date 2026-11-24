package com.example.browser

import android.webkit.ConsoleMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ConsoleLogLevel {
    LOG,
    WARNING,
    ERROR,
    COMMAND,
    RESULT
}

data class ConsoleLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val level: ConsoleLogLevel,
    val message: String,
    val source: String = "",
    val lineNumber: Int = 0,
    val timestamp: String = currentTimestamp()
) {
    companion object {
        private fun currentTimestamp(): String {
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }

        fun fromWebConsole(consoleMessage: ConsoleMessage): ConsoleLogEntry {
            val level = when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> ConsoleLogLevel.ERROR
                ConsoleMessage.MessageLevel.WARNING -> ConsoleLogLevel.WARNING
                else -> ConsoleLogLevel.LOG
            }
            return ConsoleLogEntry(
                level = level,
                message = consoleMessage.message().orEmpty(),
                source = consoleMessage.sourceId().orEmpty(),
                lineNumber = consoleMessage.lineNumber()
            )
        }
    }
}
