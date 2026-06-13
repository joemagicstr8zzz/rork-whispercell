package com.rork.whispercell.models

/** High-level interpretation of a captured performance transcript. */
data class TranscriptBrainResult(
    val primaryPayload: String = "",
    val backupPayloads: List<String> = emptyList(),
    val routineGuess: String = "Unknown",
    val confidence: Float = 0f,
    val reasoning: String = "No analysis yet.",
    val warnings: List<String> = emptyList(),
    val rejectedValues: List<String> = emptyList(),
    val shouldPublish: Boolean = false,
    val emergencyRevealText: String = ""
)
