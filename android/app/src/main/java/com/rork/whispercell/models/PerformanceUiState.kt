package com.rork.whispercell.models

/** Single observable UI state for the app shell. */
data class PerformanceUiState(
    val sessionState: SessionState = SessionState.Idle,
    val activeProfile: PerformanceProfile,
    val profiles: List<PerformanceProfile>,
    val channels: List<Channel>,
    val settings: AppSettings = AppSettings(),
    val speechProviders: List<SpeechProviderInfo>,
    val activeChannels: List<Channel>,
    val currentTranscript: String = "",
    val rawCapturedTranscript: String = "",
    val lastTranscriptLine: String = "No transcript yet.",
    val transcriptEvents: List<String> = emptyList(),
    val extractedData: ExtractedPerformanceData? = null,
    val transcriptBrain: TranscriptBrainResult? = null,
    val selectedMatch: ChannelMatch? = null,
    val lastPublishedValue: String = "Nothing published yet.",
    val lastInjectUrl: String = "",
    val injectStatus: InjectStatus = InjectStatus.Ready,
    val logs: List<LogEntry> = emptyList(),
    val mockTranscriptInput: String = "I want to go to Spain and meet Tom Cruise on June 2nd, 2035.",
    val isListeningVisible: Boolean = false,
    val providerActivity: String = "Mock Transcript Mode ready. Live microphone transcription requires a native provider/device handoff.",
    val aiActivity: String = "GPT not run yet. Rule fallback ready.",
    val notificationState: String = "No background session",
    val errorMessage: String? = null
)
