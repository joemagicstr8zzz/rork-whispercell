package com.rork.whispercell.models

enum class SpeechProviderMode { Live, Chunk, Mock }

enum class SilenceBehavior { Ignore, MarkPause, FinalizeAfterTimeout }

data class SpeechSessionConfig(
    val language: String,
    val startPhrases: List<String>,
    val stopPhrases: List<String>,
    val removeStartAndStopPhrases: Boolean,
    val chunkDurationMs: Int,
    val silenceBehavior: SilenceBehavior,
    val providerApiKey: String? = null,
    val model: String? = null
)

data class SpeechError(
    val message: String,
    val recoverable: Boolean = true
)
