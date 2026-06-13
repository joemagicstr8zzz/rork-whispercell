package com.rork.whispercell.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rork.whispercell.models.AppSettings
import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.ChannelMatch
import com.rork.whispercell.models.DetectedCategory
import com.rork.whispercell.models.ExtractedPerformanceData
import com.rork.whispercell.models.InjectStatus
import com.rork.whispercell.models.LogEntry
import com.rork.whispercell.models.LogLevel
import com.rork.whispercell.models.PerformanceProfile
import com.rork.whispercell.models.PerformanceUiState
import com.rork.whispercell.models.RoutingBehavior
import com.rork.whispercell.models.SessionState
import com.rork.whispercell.models.SilenceBehavior
import com.rork.whispercell.models.SpeechSessionConfig
import com.rork.whispercell.models.StartMode
import com.rork.whispercell.models.StopMode
import com.rork.whispercell.services.AIExtractionService
import com.rork.whispercell.services.BackgroundSessionService
import com.rork.whispercell.services.ChannelMatcher
import com.rork.whispercell.services.InjectPublisher
import com.rork.whispercell.services.MockSpeechProvider
import com.rork.whispercell.services.PrivacyManager
import com.rork.whispercell.services.ProviderCatalog
import com.rork.whispercell.services.SessionLogger
import com.rork.whispercell.services.StartPhraseDetector
import com.rork.whispercell.services.StopPhraseDetector
import com.rork.whispercell.services.TranscriptBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Coordinates WhisperCell's preview-safe performance state machine and service boundaries. */
class WhisperCellViewModel : ViewModel() {
    private val logger: SessionLogger = SessionLogger()
    private val backgroundSessionService: BackgroundSessionService = BackgroundSessionService()
    private val privacyManager: PrivacyManager = PrivacyManager()
    private val transcriptBuffer: TranscriptBuffer = TranscriptBuffer()
    private val startPhraseDetector: StartPhraseDetector = StartPhraseDetector()
    private val stopPhraseDetector: StopPhraseDetector = StopPhraseDetector()
    private val extractionService: AIExtractionService = AIExtractionService()
    private val channelMatcher: ChannelMatcher = ChannelMatcher()
    private val injectPublisher: InjectPublisher = InjectPublisher()
    private val mockSpeechProvider: MockSpeechProvider = MockSpeechProvider()

    private val defaultChannels: List<Channel> = buildDefaultChannels()
    private val defaultProfiles: List<PerformanceProfile> = buildDefaultProfiles(defaultChannels)

    private val _uiState: MutableStateFlow<PerformanceUiState> = MutableStateFlow(
        PerformanceUiState(
            activeProfile = defaultProfiles.first { it.name == "Confabulation" },
            profiles = defaultProfiles,
            channels = defaultChannels,
            speechProviders = ProviderCatalog.providers,
            activeChannels = defaultChannels.filter { it.id in defaultProfiles.first { profile -> profile.name == "Confabulation" }.activeChannelIds },
            logs = listOf(logger.entry("WhisperCell armed in preview-safe Mock Transcript Mode."))
        )
    )
    val uiState: StateFlow<PerformanceUiState> = _uiState.asStateFlow()

    init {
        mockSpeechProvider.onPartialTranscript { text -> handlePartialTranscript(text) }
        mockSpeechProvider.onFinalTranscript { text -> handleFinalTranscript(text) }
        mockSpeechProvider.onError { error -> setState(SessionState.Error, error.message, LogLevel.Error) }
    }

    fun startBackgroundSession() {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            mockSpeechProvider.startSession(configFrom(state))
            transcriptBuffer.clear()
            setState(SessionState.BackgroundReady, "Background session started. Persistent notification boundary is active in native handoff.")
            delay(250)
            setState(SessionState.WaitingForStartPhrase, "Listening for natural start phrase: ${state.activeProfile.startPhrase}")
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            mockSpeechProvider.stopSession()
            setState(SessionState.Idle, "Session stopped manually.")
        }
    }

    fun pauseListening() {
        viewModelScope.launch {
            mockSpeechProvider.pauseSession()
            setState(SessionState.Paused, "Listening paused by performer.")
        }
    }

    fun resumeListening() {
        viewModelScope.launch {
            mockSpeechProvider.resumeSession()
            setState(SessionState.WaitingForStartPhrase, "Listening resumed. Waiting for start phrase.")
        }
    }

    fun panicStop() {
        transcriptBuffer.clear()
        _uiState.update { state ->
            state.copy(
                sessionState = SessionState.PanicStopped,
                isListeningVisible = false,
                notificationState = "Panic stopped",
                logs = prependLog(state.logs, logger.entry("Panic Stop pressed. Listening and processing halted immediately.", LogLevel.Warning)),
                errorMessage = null
            )
        }
    }

    fun clearSession() {
        transcriptBuffer.clear()
        _uiState.update { state ->
            state.copy(
                currentTranscript = "",
                lastTranscriptLine = "No transcript yet.",
                extractedData = null,
                selectedMatch = null,
                lastPublishedValue = "Nothing published yet.",
                lastInjectUrl = "",
                injectStatus = InjectStatus.Ready,
                logs = listOf(logger.entry(privacyManager.clearSessionNotice(), LogLevel.Success)),
                errorMessage = null
            )
        }
    }

    fun activateProfile(profileId: String) {
        _uiState.update { state ->
            val profile: PerformanceProfile = state.profiles.firstOrNull { it.id == profileId } ?: state.activeProfile
            state.copy(
                activeProfile = profile,
                activeChannels = state.channels.filter { it.id in profile.activeChannelIds },
                logs = prependLog(state.logs, logger.entry("Profile activated: ${profile.name}"))
            )
        }
    }

    fun updateMockTranscript(text: String) {
        _uiState.update { state -> state.copy(mockTranscriptInput = text) }
    }

    fun loadPresetTranscript(text: String) {
        _uiState.update { state ->
            state.copy(
                mockTranscriptInput = text,
                logs = prependLog(state.logs, logger.entry("Preset transcript loaded for quiet-room testing."))
            )
        }
    }

    fun fakePartialPlayback() {
        viewModelScope.launch {
            val words: List<String> = _uiState.value.mockTranscriptInput.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return@launch
            setState(SessionState.Capturing, "Fake partial transcript playback started.")
            words.indices.forEach { index ->
                val partial: String = words.take(index + 1).joinToString(" ")
                mockSpeechProvider.emitPartial(partial)
                delay(80)
            }
            mockSpeechProvider.emitFinal(_uiState.value.mockTranscriptInput)
        }
    }

    fun runExtractionOnly() {
        viewModelScope.launch {
            processTranscript(_uiState.value.mockTranscriptInput, publishAfterMatch = false)
        }
    }

    fun runSelectedProfile() {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            val phraseWrapped: String = "${state.activeProfile.startPhrase}. ${state.mockTranscriptInput} ${state.activeProfile.stopPhrase}."
            handleFinalTranscript(phraseWrapped)
        }
    }

    fun simulateInjectPublish() {
        val match: ChannelMatch? = _uiState.value.selectedMatch
        val value: String = match?.payload ?: _uiState.value.extractedData?.bestMatches?.fullConfabulation ?: _uiState.value.extractedData?.detectedItems?.firstOrNull()?.normalizedValue.orEmpty()
        if (value.isBlank()) {
            _uiState.update { state -> state.copy(errorMessage = "Run extraction before publishing.") }
            return
        }
        val injectCode: String = match?.channel?.customInjectCode ?: _uiState.value.activeProfile.injectCode.ifBlank { _uiState.value.settings.defaultInjectCode }
        val url: String = injectPublisher.buildUrl(injectCode, value)
        _uiState.update { state ->
            state.copy(
                sessionState = SessionState.Published,
                injectStatus = InjectStatus.Published,
                lastPublishedValue = value,
                lastInjectUrl = url,
                notificationState = backgroundSessionService.notificationFor(SessionState.Published.label),
                logs = prependLog(state.logs, logger.entry("Simulated Inject publish: $value", LogLevel.Success)),
                errorMessage = null
            )
        }
    }

    fun testInject() {
        val code: String = _uiState.value.settings.defaultInjectCode
        val url: String = injectPublisher.buildUrl(code, "WhisperCell Test")
        _uiState.update { state ->
            state.copy(
                injectStatus = InjectStatus.Connected,
                lastInjectUrl = url,
                logs = prependLog(state.logs, logger.entry("Inject URL generated and code validated locally: ${injectPublisher.sanitizeInjectCode(code)}", LogLevel.Success))
            )
        }
    }

    fun publishSelectedValue() {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            val match: ChannelMatch? = state.selectedMatch
            val value: String = match?.payload.orEmpty()
            if (value.isBlank()) {
                _uiState.update { it.copy(errorMessage = "No channel payload selected.") }
                return@launch
            }
            val injectCode: String = match?.channel?.customInjectCode ?: state.activeProfile.injectCode.ifBlank { state.settings.defaultInjectCode }
            setState(SessionState.PublishingToInject, "Publishing selected value to Inject.")
            _uiState.update { it.copy(injectStatus = InjectStatus.Publishing, lastInjectUrl = injectPublisher.buildUrl(injectCode, value)) }
            val result: Result<String> = injectPublisher.publish(injectCode, value)
            result.fold(
                onSuccess = {
                    _uiState.update { current ->
                        current.copy(
                            sessionState = SessionState.Published,
                            injectStatus = InjectStatus.Published,
                            lastPublishedValue = value,
                            notificationState = backgroundSessionService.notificationFor(SessionState.Published.label),
                            logs = prependLog(current.logs, logger.entry("Inject published: $value", LogLevel.Success)),
                            errorMessage = null
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            sessionState = SessionState.Error,
                            injectStatus = InjectStatus.RetryAvailable,
                            logs = prependLog(current.logs, logger.entry("Inject publish failed: ${throwable.message ?: "Unknown error"}", LogLevel.Error)),
                            errorMessage = "Inject publish failed. Simulate publish is available for preview testing."
                        )
                    }
                }
            )
        }
    }

    private fun handlePartialTranscript(text: String) {
        _uiState.update { state ->
            state.copy(
                lastTranscriptLine = text,
                currentTranscript = text,
                sessionState = if (state.sessionState == SessionState.WaitingForStartPhrase && startPhraseDetector.containsStartPhrase(text, state.settings.startPhrases)) SessionState.Capturing else state.sessionState,
                isListeningVisible = true
            )
        }
    }

    private fun handleFinalTranscript(text: String) {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            val hasStart: Boolean = startPhraseDetector.containsStartPhrase(text, state.settings.startPhrases + state.activeProfile.startPhrase)
            val hasStop: Boolean = stopPhraseDetector.containsStopPhrase(text, state.settings.stopPhrases + state.activeProfile.stopPhrase)
            transcriptBuffer.append(text)
            when {
                state.activeProfile.startMode == StartMode.StartPhraseRequired && !hasStart && state.sessionState == SessionState.WaitingForStartPhrase -> {
                    _uiState.update { current -> current.copy(lastTranscriptLine = text, logs = prependLog(current.logs, logger.entry("Transcript heard, still waiting for start phrase."))) }
                }
                hasStop || state.activeProfile.stopMode == StopMode.RequiredFieldsComplete -> processTranscript(transcriptBuffer.combined(), publishAfterMatch = state.activeProfile.fullAutomationEnabled || state.activeProfile.stopMode == StopMode.StopPhrasePublishesAutomatically)
                else -> {
                    setState(SessionState.ThinkingPause, "Thinking pause detected. Silence does not finalize capture by default.")
                    _uiState.update { it.copy(lastTranscriptLine = text, currentTranscript = transcriptBuffer.combined()) }
                }
            }
        }
    }

    private suspend fun processTranscript(transcript: String, publishAfterMatch: Boolean) {
        if (transcript.isBlank()) return
        setState(SessionState.ProcessingTranscript, "Transcript captured. Cleaning performance patter.")
        delay(150)
        setState(SessionState.ExtractingData, "AI-first extraction boundary running with rule fallback.")
        val state: PerformanceUiState = _uiState.value
        val extracted: ExtractedPerformanceData = extractionService.extract(
            transcript = transcript,
            startPhrases = state.settings.startPhrases + state.activeProfile.startPhrase,
            stopPhrases = state.settings.stopPhrases + state.activeProfile.stopPhrase,
            aiEnabled = state.settings.openAiTranscriptionEnabled || state.settings.elevenLabsEnabled
        )
        _uiState.update { current ->
            current.copy(
                extractedData = extracted,
                currentTranscript = extracted.cleanedTranscript,
                lastTranscriptLine = extracted.cleanedTranscript,
                logs = prependLog(current.logs, logger.entry("Detected ${extracted.detectedItems.size} structured value(s)."))
            )
        }
        setState(SessionState.MatchingChannel, "Matching detected values against active channels.")
        val matches: List<ChannelMatch> = channelMatcher.match(state.activeProfile, state.channels, extracted)
        val selected: ChannelMatch? = matches.firstOrNull()
        _uiState.update { current ->
            current.copy(
                selectedMatch = selected,
                logs = prependLog(current.logs, logger.entry(selected?.let { "Channel selected: ${it.channel.name} → ${it.payload}" } ?: "No channel match found.", if (selected != null) LogLevel.Success else LogLevel.Warning))
            )
        }
        if (publishAfterMatch && selected != null) {
            simulateInjectPublish()
        } else {
            setState(SessionState.MatchingChannel, "Review Mode is on. Approve before publishing.")
        }
    }

    private fun setState(state: SessionState, message: String, level: LogLevel = LogLevel.Info) {
        _uiState.update { current ->
            current.copy(
                sessionState = state,
                isListeningVisible = state in listOf(SessionState.BackgroundReady, SessionState.WaitingForStartPhrase, SessionState.Capturing, SessionState.ThinkingPause),
                notificationState = backgroundSessionService.notificationFor(state.label),
                logs = prependLog(current.logs, logger.entry(message, level)),
                errorMessage = if (level == LogLevel.Error) message else null
            )
        }
    }

    private fun configFrom(state: PerformanceUiState): SpeechSessionConfig = SpeechSessionConfig(
        language = state.settings.language,
        startPhrases = state.settings.startPhrases + state.activeProfile.startPhrase,
        stopPhrases = state.settings.stopPhrases + state.activeProfile.stopPhrase,
        removeStartAndStopPhrases = state.settings.removeStartAndStopPhrases,
        chunkDurationMs = 3_000,
        silenceBehavior = SilenceBehavior.Ignore,
        model = state.settings.openAiModel
    )

    private fun prependLog(logs: List<LogEntry>, entry: LogEntry): List<LogEntry> = (listOf(entry) + logs).take(80)

    private fun buildDefaultChannels(): List<Channel> = listOf(
        Channel("word", "Word Channel", true, listOf(DetectedCategory.Word, DetectedCategory.Phrase), listOf(DetectedCategory.Word, DetectedCategory.Phrase), "{value}", true, null, false, 0.7f, 5, false, true),
        Channel("card", "Card Channel", true, listOf(DetectedCategory.PlayingCard), listOf(DetectedCategory.PlayingCard), "{card}", true, null, false, 0.82f, 5, true, false),
        Channel("date", "Date Channel", true, listOf(DetectedCategory.Date, DetectedCategory.Birthday), listOf(DetectedCategory.Date, DetectedCategory.Birthday), "{date}", true, null, false, 0.75f, 5, false, true),
        Channel("music", "Music Channel", true, listOf(DetectedCategory.Song, DetectedCategory.Artist, DetectedCategory.Lyric), listOf(DetectedCategory.Song, DetectedCategory.Artist, DetectedCategory.Lyric), "{song}", true, null, false, 0.72f, 5, false, true),
        Channel("serial", "Serial Number Channel", true, listOf(DetectedCategory.SerialNumber, DetectedCategory.Number), listOf(DetectedCategory.SerialNumber, DetectedCategory.Number), "{serial}", true, null, false, 0.8f, 5, true, false),
        Channel("zodiac", "Zodiac Channel", true, listOf(DetectedCategory.Zodiac, DetectedCategory.Birthday), listOf(DetectedCategory.Zodiac, DetectedCategory.Birthday), "{zodiac}", true, null, false, 0.75f, 5, false, true),
        Channel("name", "Name Channel", true, listOf(DetectedCategory.Name, DetectedCategory.Celebrity), listOf(DetectedCategory.Name, DetectedCategory.Celebrity), "{name}", true, null, false, 0.7f, 5, false, true),
        Channel("object", "Object Channel", true, listOf(DetectedCategory.Object), listOf(DetectedCategory.Object), "{object}", true, null, false, 0.7f, 5, false, true),
        Channel("confabulation", "Confabulation Channel", true, listOf(DetectedCategory.FullConfabulation, DetectedCategory.Place, DetectedCategory.Name, DetectedCategory.Date, DetectedCategory.Object, DetectedCategory.Song), listOf(DetectedCategory.FullConfabulation, DetectedCategory.Place, DetectedCategory.Celebrity, DetectedCategory.Name, DetectedCategory.Date), "{place} | {person} | {date}", true, null, false, 0.7f, 8, false, true)
    )

    private fun buildDefaultProfiles(channels: List<Channel>): List<PerformanceProfile> {
        fun profile(id: String, name: String, start: String, stop: String, channelId: String, categories: List<DetectedCategory>, formatMode: RoutingBehavior = RoutingBehavior.BestConfidenceMatch): PerformanceProfile = PerformanceProfile(
            id = id,
            name = name,
            startPhrase = start,
            stopPhrase = stop,
            speechProviderId = "mock",
            activeCategories = categories,
            activeChannelIds = listOf(channelId),
            injectCode = "5850",
            reviewModeEnabled = true,
            fullAutomationEnabled = false,
            startMode = StartMode.StartPhraseRequired,
            stopMode = StopMode.StopPhraseProcessesTranscript,
            routingBehavior = formatMode,
            confidenceThreshold = 0.72f,
            cooldownSeconds = 5,
            loggingEnabled = true
        )
        return listOf(
            profile("basic_word", "Basic Word Reveal", "Hold one clear thought", "Got it", "word", listOf(DetectedCategory.Word, DetectedCategory.Phrase)),
            profile("card", "Card Reveal", "Name it out loud", "Don't change it", "card", listOf(DetectedCategory.PlayingCard)),
            profile("date", "Date or Birthday Reveal", "Take your time and make it specific", "That's clear", "date", listOf(DetectedCategory.Date, DetectedCategory.Birthday)),
            profile("music", "Music Reveal", "Make the song clear in your mind", "Got it", "music", listOf(DetectedCategory.Song, DetectedCategory.Artist, DetectedCategory.Lyric)),
            profile("serial", "Serial Number Reveal", "Say the whole thing", "Perfect", "serial", listOf(DetectedCategory.SerialNumber, DetectedCategory.Number)),
            profile("zodiac", "Zodiac Reveal", "Think of the date clearly", "Keep that in mind", "zodiac", listOf(DetectedCategory.Zodiac, DetectedCategory.Birthday)),
            profile("name", "Name Reveal", "Go with your first instinct", "Nice", "name", listOf(DetectedCategory.Name, DetectedCategory.Celebrity)),
            profile("object", "Object Reveal", "Set the scene for me", "Hold that thought", "object", listOf(DetectedCategory.Object)),
            profile("confab", "Confabulation", "Picture this clearly for me", "Perfect", "confabulation", listOf(DetectedCategory.Place, DetectedCategory.Celebrity, DetectedCategory.Name, DetectedCategory.Date, DetectedCategory.Object, DetectedCategory.Song, DetectedCategory.FullConfabulation)),
            profile("custom", "Custom Profile", "Describe it like it already happened", "That tells me enough", "word", DetectedCategory.entries)
        ).filter { profile -> channels.any { it.id in profile.activeChannelIds } }
    }
}
