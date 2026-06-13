package com.rork.whispercell.models

/** Public capabilities for a selectable speech engine. */
data class SpeechProviderInfo(
    val id: String,
    val displayName: String,
    val mode: SpeechProviderMode,
    val supportsBackground: Boolean,
    val supportsPartialResults: Boolean,
    val supportsTimestamps: Boolean,
    val status: String
)
