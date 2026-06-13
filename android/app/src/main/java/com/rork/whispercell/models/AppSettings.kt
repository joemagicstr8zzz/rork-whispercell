package com.rork.whispercell.models

/** Global performer settings. Secrets are only held in memory in this preview build. */
data class AppSettings(
    val startPhraseEnabled: Boolean = true,
    val startPhrases: List<String> = listOf("Picture this clearly for me"),
    val stopPhraseEnabled: Boolean = true,
    val stopPhrases: List<String> = listOf("Perfect"),
    val removeStartAndStopPhrases: Boolean = true,
    val silenceBehavior: SilenceBehavior = SilenceBehavior.Ignore,
    val maximumCaptureSeconds: Int = 90,
    val manualBackupEnabled: Boolean = true,
    val openAiTranscriptionEnabled: Boolean = false,
    val openAiModel: String = "gpt-4o-mini-transcribe",
    val openAiRealtimeEnabled: Boolean = false,
    val openAiChunkEnabled: Boolean = true,
    val elevenLabsEnabled: Boolean = false,
    val elevenLabsModel: String = "scribe_v1",
    val language: String = "en",
    val cleanTranscriptMode: Boolean = true,
    val verbatimTranscriptMode: Boolean = false,
    val injectEnabled: Boolean = true,
    val defaultInjectCode: String = "5850",
    val injectTimeoutSeconds: Int = 8,
    val injectRetryOnce: Boolean = true,
    val reviewModeEnabled: Boolean = true,
    val fullAutomationEnabled: Boolean = false,
    val audioSavingEnabled: Boolean = false,
    val transcriptSavePolicy: String = "Current session only",
    val keepLogsFor24Hours: Boolean = false,
    val autoStartSessionWhenAppOpens: Boolean = false,
    val continueListeningAfterPublish: Boolean = true
)
