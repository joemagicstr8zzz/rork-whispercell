package com.rork.whispercell.models

/** A privacy-aware session log entry. Audio is never stored here. */
data class LogEntry(
    val id: String,
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.Info
)

enum class LogLevel { Info, Success, Warning, Error }
