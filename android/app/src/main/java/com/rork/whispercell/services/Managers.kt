package com.rork.whispercell.services

import com.rork.whispercell.models.LogEntry
import com.rork.whispercell.models.LogLevel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SessionLogger {
    fun entry(message: String, level: LogLevel = LogLevel.Info): LogEntry = LogEntry(
        id = UUID.randomUUID().toString(),
        timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
        message = message,
        level = level
    )
}

class BackgroundSessionService {
    fun notificationFor(stateLabel: String): String = when (stateLabel) {
        "Waiting for Start Phrase" -> "Waiting for start phrase"
        "Capturing" -> "Capturing performance audio"
        "Thinking Pause" -> "Thinking pause"
        "Processing" -> "Processing transcript"
        "Publishing to Inject" -> "Publishing to Inject"
        "Published" -> "Published successfully"
        "Error" -> "Error"
        else -> "WhisperCell session active"
    }
}

class PrivacyManager {
    fun clearSessionNotice(): String = "Transcript, detected values, and current-session logs cleared. Audio was not saved."
}

class SettingsStore {
    val storageNote: String = "Preview stores settings in memory. Android Studio handoff should back this with encrypted local storage for provider keys."
}
