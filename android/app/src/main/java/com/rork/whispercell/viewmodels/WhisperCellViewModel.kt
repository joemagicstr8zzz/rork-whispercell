package com.rork.whispercell.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            logs = listOf(logger.entry("WhisperCell is live in Mock Transcript Mode. Enter your Inject code before publishing."))
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
            setState(SessionState.BackgroundReady, "Background session started. Preview uses Mock Transcript Mode; native foreground service is ready for Android Studio handoff.")
            delay(250)
            val nextState: SessionState = if (state.activeProfile.startMode == StartMode.AlwaysCapturingAfterSessionStarts) SessionState.Capturing else SessionState.WaitingForStartPhrase
            val message: String = if (nextState == SessionState.Capturing) "Capturing immediately for ${state.activeProfile.name}." else "Listening for natural start phrase: ${state.activeProfile.startPhrase}"
            setState(nextState, message)
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
                injectStatus = if (state.settings.defaultInjectCode.isBlank()) InjectStatus.NotConfigured else InjectStatus.Ready,
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
                selectedMatch = null,
                logs = prependLog(state.logs, logger.entry("Profile activated: ${profile.name}")),
                errorMessage = null
            )
        }
        rematchIfPossible()
    }

    fun duplicateActiveProfile() {
        _uiState.update { state ->
            val copyNumber: Int = state.profiles.count { it.name.startsWith(state.activeProfile.name) } + 1
            val duplicated: PerformanceProfile = state.activeProfile.copy(
                id = "${state.activeProfile.id}_copy_${System.currentTimeMillis()}",
                name = "${state.activeProfile.name} Copy $copyNumber"
            )
            state.copy(
                activeProfile = duplicated,
                profiles = state.profiles + duplicated,
                logs = prependLog(state.logs, logger.entry("Duplicated profile: ${duplicated.name}", LogLevel.Success))
            )
        }
    }

    fun deleteActiveProfile() {
        _uiState.update { state ->
            if (state.profiles.size <= 1) {
                state.copy(errorMessage = "At least one profile is required.")
            } else {
                val remaining: List<PerformanceProfile> = state.profiles.filterNot { it.id == state.activeProfile.id }
                val nextProfile: PerformanceProfile = remaining.first()
                state.copy(
                    activeProfile = nextProfile,
                    profiles = remaining,
                    activeChannels = state.channels.filter { it.id in nextProfile.activeChannelIds },
                    selectedMatch = null,
                    logs = prependLog(state.logs, logger.entry("Deleted profile. Active profile is now ${nextProfile.name}.", LogLevel.Warning)),
                    errorMessage = null
                )
            }
        }
    }

    fun updateActiveProfileStartPhrase(value: String) {
        updateActiveProfile("Start phrase updated") { it.copy(startPhrase = value) }
        _uiState.update { state -> state.copy(settings = state.settings.copy(startPhrases = listOf(value).filter { it.isNotBlank() })) }
    }

    fun updateActiveProfileStopPhrase(value: String) {
        updateActiveProfile("Stop phrase updated") { it.copy(stopPhrase = value) }
        _uiState.update { state -> state.copy(settings = state.settings.copy(stopPhrases = listOf(value).filter { it.isNotBlank() })) }
    }

    fun toggleActiveProfileFullAutomation(enabled: Boolean) {
        updateActiveProfile(if (enabled) "Full Automation enabled for active profile" else "Review Mode restored for active profile") {
            it.copy(fullAutomationEnabled = enabled, reviewModeEnabled = !enabled)
        }
        _uiState.update { state -> state.copy(settings = state.settings.copy(fullAutomationEnabled = enabled, reviewModeEnabled = !enabled)) }
    }

    fun toggleActiveProfileReviewMode(enabled: Boolean) {
        updateActiveProfile(if (enabled) "Review Mode enabled for active profile" else "Review Mode disabled for active profile") {
            it.copy(reviewModeEnabled = enabled, fullAutomationEnabled = !enabled && it.fullAutomationEnabled)
        }
        _uiState.update { state -> state.copy(settings = state.settings.copy(reviewModeEnabled = enabled)) }
    }

    fun toggleProfileChannel(channelId: String, enabled: Boolean) {
        updateActiveProfile(if (enabled) "Channel added to active profile" else "Channel removed from active profile") { profile ->
            val channelIds: List<String> = if (enabled) (profile.activeChannelIds + channelId).distinct() else profile.activeChannelIds.filterNot { it == channelId }
            profile.copy(activeChannelIds = channelIds)
        }
        rematchIfPossible()
    }

    fun updateMockTranscript(text: String) {
        _uiState.update { state -> state.copy(mockTranscriptInput = text) }
    }

    fun loadPresetTranscript(text: String) {
        _uiState.update { state ->
            state.copy(
                mockTranscriptInput = text,
                logs = prependLog(state.logs, logger.entry("Preset transcript loaded for quiet-room testing.")),
                errorMessage = null
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
        val state: PerformanceUiState = _uiState.value
        val match: ChannelMatch? = state.selectedMatch
        val value: String = match?.payload ?: state.extractedData?.bestMatches?.fullConfabulation ?: state.extractedData?.detectedItems?.firstOrNull()?.normalizedValue.orEmpty()
        if (value.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Run extraction before publishing.") }
            return
        }
        val injectCode: String = injectCodeFor(match?.channel, state)
        if (state.settings.injectEnabled && injectCode.isBlank()) {
            _uiState.update { current ->
                current.copy(
                    injectStatus = InjectStatus.NotConfigured,
                    errorMessage = "Enter your Inject code first. WhisperCell no longer uses a hard-coded ID.",
                    logs = prependLog(current.logs, logger.entry("Publish blocked: Inject code is blank.", LogLevel.Warning))
                )
            }
            return
        }
        val url: String = if (injectCode.isNotBlank()) injectPublisher.buildUrl(injectCode, value) else "Local simulation only"
        _uiState.update { current ->
            current.copy(
                sessionState = SessionState.Published,
                injectStatus = if (injectCode.isNotBlank()) InjectStatus.Published else InjectStatus.Ready,
                lastPublishedValue = value,
                lastInjectUrl = url,
                notificationState = backgroundSessionService.notificationFor(SessionState.Published.label),
                logs = prependLog(current.logs, logger.entry("Simulated Inject publish: $value", LogLevel.Success)),
                errorMessage = null
            )
        }
    }

    fun updateInjectCode(input: String) {
        val cleanCode: String = injectPublisher.sanitizeInjectCode(input)
        _uiState.update { state ->
            val url: String = if (cleanCode.isBlank()) "" else injectPublisher.buildUrl(cleanCode, state.selectedMatch?.payload ?: "WhisperCell Test")
            state.copy(
                settings = state.settings.copy(defaultInjectCode = cleanCode),
                injectStatus = if (cleanCode.isBlank()) InjectStatus.NotConfigured else InjectStatus.Ready,
                lastInjectUrl = url,
                logs = prependLog(state.logs, logger.entry("Default Inject code updated (${cleanCode.length}/7 characters).")),
                errorMessage = null
            )
        }
    }

    fun toggleInjectEnabled(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(injectEnabled = enabled),
                injectStatus = if (!enabled) InjectStatus.NotConfigured else if (state.settings.defaultInjectCode.isBlank()) InjectStatus.NotConfigured else InjectStatus.Ready,
                logs = prependLog(state.logs, logger.entry(if (enabled) "Inject publishing enabled." else "Inject publishing disabled; standalone tools remain available."))
            )
        }
    }

    fun testInject() {
        val code: String = _uiState.value.settings.defaultInjectCode
        if (code.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    injectStatus = InjectStatus.NotConfigured,
                    errorMessage = "Enter your Inject code only, not the full URL.",
                    logs = prependLog(state.logs, logger.entry("Inject test blocked: no code entered.", LogLevel.Warning))
                )
            }
            return
        }
        val url: String = injectPublisher.buildUrl(code, "WhisperCell Test")
        _uiState.update { state ->
            state.copy(
                injectStatus = InjectStatus.Connected,
                lastInjectUrl = url,
                logs = prependLog(state.logs, logger.entry("Inject code validated locally: ${injectPublisher.sanitizeInjectCode(code)}", LogLevel.Success)),
                errorMessage = null
            )
        }
    }

    fun publishSelectedValue() {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            val match: ChannelMatch? = state.selectedMatch
            val value: String = match?.payload.orEmpty()
            if (value.isBlank()) {
                _uiState.update { it.copy(errorMessage = "No channel payload selected. Run extraction first.") }
                return@launch
            }
            if (!state.settings.injectEnabled) {
                _uiState.update { current ->
                    current.copy(errorMessage = "Inject is disabled. Turn it on or use Simulate Inject for local testing.")
                }
                return@launch
            }
            val injectCode: String = injectCodeFor(match?.channel, state)
            if (injectCode.isBlank()) {
                _uiState.update { current ->
                    current.copy(
                        injectStatus = InjectStatus.NotConfigured,
                        errorMessage = "Enter your Inject code before publishing.",
                        logs = prependLog(current.logs, logger.entry("Real Inject publish blocked: no code entered.", LogLevel.Warning))
                    )
                }
                return@launch
            }
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
                            errorMessage = "Inject publish failed. Check the code or connection; retry or use Simulate Inject in preview."
                        )
                    }
                }
            )
        }
    }

    fun toggleChannelEnabled(channelId: String, enabled: Boolean) {
        updateChannel(channelId, if (enabled) "Channel enabled" else "Channel disabled") { it.copy(enabled = enabled) }
        rematchIfPossible()
    }

    fun toggleChannelAutoPublish(channelId: String, enabled: Boolean) {
        updateChannel(channelId, if (enabled) "Channel auto-publish enabled" else "Channel auto-publish disabled") { it.copy(autoPublish = enabled) }
    }

    fun toggleChannelUseDefaultInjectCode(channelId: String, enabled: Boolean) {
        updateChannel(channelId, if (enabled) "Channel now uses default Inject code" else "Channel now uses custom Inject code") { it.copy(useDefaultInjectCode = enabled) }
    }

    fun updateChannelCustomInjectCode(channelId: String, input: String) {
        val cleanCode: String = injectPublisher.sanitizeInjectCode(input)
        updateChannel(channelId, "Channel Inject code updated") { it.copy(customInjectCode = cleanCode) }
    }

    fun updateChannelPayloadFormat(channelId: String, value: String) {
        updateChannel(channelId, "Channel payload format updated") { it.copy(payloadFormat = value) }
        rematchIfPossible()
    }

    fun testChannel(channelId: String) {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            val channel: Channel? = state.channels.firstOrNull { it.id == channelId }
            if (channel == null) return@launch
            val extracted: ExtractedPerformanceData = state.extractedData ?: extractionService.extract(
                transcript = state.mockTranscriptInput,
                startPhrases = state.settings.startPhrases + state.activeProfile.startPhrase,
                stopPhrases = state.settings.stopPhrases + state.activeProfile.stopPhrase,
                aiEnabled = state.settings.openAiTranscriptionEnabled || state.settings.elevenLabsEnabled
            )
            val testProfile: PerformanceProfile = state.activeProfile.copy(activeChannelIds = listOf(channelId), routingBehavior = RoutingBehavior.BestConfidenceMatch)
            val match: ChannelMatch? = channelMatcher.match(testProfile, state.channels, extracted).firstOrNull()
            _uiState.update { current ->
                current.copy(
                    extractedData = extracted,
                    selectedMatch = match,
                    logs = prependLog(current.logs, logger.entry(match?.let { "Tested ${channel.name}: ${it.payload}" } ?: "Tested ${channel.name}: no matching value.", if (match != null) LogLevel.Success else LogLevel.Warning)),
                    errorMessage = if (match == null) "No value matched ${channel.name}. Try a relevant preset transcript." else null
                )
            }
        }
    }

    fun toggleStartPhraseEnabled(enabled: Boolean) {
        updateSettings("Start Phrase ${if (enabled) "enabled" else "disabled"}") { it.copy(startPhraseEnabled = enabled) }
    }

    fun toggleStopPhraseEnabled(enabled: Boolean) {
        updateSettings("Stop Phrase ${if (enabled) "enabled" else "disabled"}") { it.copy(stopPhraseEnabled = enabled) }
    }

    fun toggleRemovePhrases(enabled: Boolean) {
        updateSettings("Remove start/stop phrases ${if (enabled) "enabled" else "disabled"}") { it.copy(removeStartAndStopPhrases = enabled) }
    }

    fun updateMaximumCaptureSeconds(input: String) {
        val seconds: Int = input.filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 600) ?: 90
        updateSettings("Maximum capture set to $seconds seconds") { it.copy(maximumCaptureSeconds = seconds) }
    }

    fun selectSpeechProvider(providerId: String) {
        updateSettings("Speech provider selected: $providerId") { it.copy(selectedSpeechProviderId = providerId) }
        updateActiveProfile("Active profile speech provider updated") { it.copy(speechProviderId = providerId) }
    }

    fun toggleOpenAiEnabled(enabled: Boolean) {
        updateSettings(if (enabled) "OpenAI transcription enabled" else "OpenAI transcription disabled") { it.copy(openAiTranscriptionEnabled = enabled) }
        if (enabled) selectSpeechProvider(if (_uiState.value.settings.openAiRealtimeEnabled) "openai_realtime" else "openai_chunk")
    }

    fun updateOpenAiApiKey(value: String) {
        updateSettings("OpenAI API key updated in memory") { it.copy(openAiApiKey = value, openAiValidationStatus = "Not validated") }
    }

    fun updateOpenAiModel(value: String) {
        updateSettings("OpenAI model set to ${value.ifBlank { "custom blank" }}") { it.copy(openAiModel = value) }
    }

    fun toggleOpenAiRealtime(enabled: Boolean) {
        updateSettings(if (enabled) "OpenAI Realtime mode enabled" else "OpenAI Realtime mode disabled") {
            it.copy(openAiRealtimeEnabled = enabled, openAiChunkEnabled = if (enabled) false else it.openAiChunkEnabled)
        }
        if (enabled) selectSpeechProvider("openai_realtime")
    }

    fun toggleOpenAiChunk(enabled: Boolean) {
        updateSettings(if (enabled) "OpenAI Chunk mode enabled" else "OpenAI Chunk mode disabled") {
            it.copy(openAiChunkEnabled = enabled, openAiRealtimeEnabled = if (enabled) false else it.openAiRealtimeEnabled)
        }
        if (enabled) selectSpeechProvider("openai_chunk")
    }

    fun validateOpenAiKey() {
        _uiState.update { state ->
            val status: String = if (state.settings.openAiApiKey.length >= 12) "Looks valid for provider handoff" else "Enter a full API key"
            state.copy(
                settings = state.settings.copy(openAiValidationStatus = status),
                logs = prependLog(state.logs, logger.entry("OpenAI key validation: $status", if (state.settings.openAiApiKey.length >= 12) LogLevel.Success else LogLevel.Warning)),
                errorMessage = if (state.settings.openAiApiKey.length >= 12) null else "OpenAI key is too short."
            )
        }
    }

    fun testOpenAiTranscription() {
        viewModelScope.launch {
            updateSettings("OpenAI transcription test routed through mock transcript in preview") { it.copy(openAiTranscriptionEnabled = true) }
            processTranscript(_uiState.value.mockTranscriptInput, publishAfterMatch = false)
        }
    }

    fun toggleElevenLabsEnabled(enabled: Boolean) {
        updateSettings(if (enabled) "ElevenLabs Speech to Text enabled" else "ElevenLabs Speech to Text disabled") { it.copy(elevenLabsEnabled = enabled) }
        if (enabled) selectSpeechProvider("elevenlabs_stt")
    }

    fun updateElevenLabsApiKey(value: String) {
        updateSettings("ElevenLabs API key updated in memory") { it.copy(elevenLabsApiKey = value, elevenLabsValidationStatus = "Not validated") }
    }

    fun updateElevenLabsModel(value: String) {
        updateSettings("ElevenLabs model set to ${value.ifBlank { "custom blank" }}") { it.copy(elevenLabsModel = value) }
    }

    fun validateElevenLabsKey() {
        _uiState.update { state ->
            val status: String = if (state.settings.elevenLabsApiKey.length >= 12) "Looks valid for provider handoff" else "Enter a full API key"
            state.copy(
                settings = state.settings.copy(elevenLabsValidationStatus = status),
                logs = prependLog(state.logs, logger.entry("ElevenLabs key validation: $status", if (state.settings.elevenLabsApiKey.length >= 12) LogLevel.Success else LogLevel.Warning)),
                errorMessage = if (state.settings.elevenLabsApiKey.length >= 12) null else "ElevenLabs key is too short."
            )
        }
    }

    fun testElevenLabsTranscription() {
        viewModelScope.launch {
            updateSettings("ElevenLabs transcription test routed through mock transcript in preview") { it.copy(elevenLabsEnabled = true) }
            processTranscript(_uiState.value.mockTranscriptInput, publishAfterMatch = false)
        }
    }

    fun toggleAudioSaving(enabled: Boolean) {
        updateSettings(if (enabled) "Audio saving explicitly enabled" else "Audio saving off") { it.copy(audioSavingEnabled = enabled) }
    }

    fun toggleContinueListening(enabled: Boolean) {
        updateSettings(if (enabled) "Continue listening after publish enabled" else "Continue listening after publish disabled") { it.copy(continueListeningAfterPublish = enabled) }
    }

    fun toggleKeepLogs24Hours(enabled: Boolean) {
        updateSettings(if (enabled) "Logs will be kept for 24 hours" else "Logs limited to current session") { it.copy(keepLogsFor24Hours = enabled, transcriptSavePolicy = if (enabled) "Keep logs for 24 hours" else "Current session only") }
    }

    private fun handlePartialTranscript(text: String) {
        _uiState.update { state ->
            val startPhrases: List<String> = if (state.settings.startPhraseEnabled) state.settings.startPhrases + state.activeProfile.startPhrase else emptyList()
            state.copy(
                lastTranscriptLine = text,
                currentTranscript = text,
                sessionState = if (state.sessionState == SessionState.WaitingForStartPhrase && startPhraseDetector.containsStartPhrase(text, startPhrases)) SessionState.Capturing else state.sessionState,
                isListeningVisible = true
            )
        }
    }

    private fun handleFinalTranscript(text: String) {
        viewModelScope.launch {
            val state: PerformanceUiState = _uiState.value
            val startPhrases: List<String> = if (state.settings.startPhraseEnabled) state.settings.startPhrases + state.activeProfile.startPhrase else emptyList()
            val stopPhrases: List<String> = if (state.settings.stopPhraseEnabled) state.settings.stopPhrases + state.activeProfile.stopPhrase else emptyList()
            val hasStart: Boolean = state.activeProfile.startMode != StartMode.StartPhraseRequired || startPhraseDetector.containsStartPhrase(text, startPhrases)
            val hasStop: Boolean = state.activeProfile.stopMode == StopMode.RequiredFieldsComplete || stopPhraseDetector.containsStopPhrase(text, stopPhrases)
            transcriptBuffer.append(text)
            when {
                state.activeProfile.startMode == StartMode.StartPhraseRequired && !hasStart && state.sessionState == SessionState.WaitingForStartPhrase -> {
                    _uiState.update { current -> current.copy(lastTranscriptLine = text, logs = prependLog(current.logs, logger.entry("Transcript heard, still waiting for start phrase."))) }
                }
                hasStop || state.activeProfile.stopMode in listOf(StopMode.StopPhraseProcessesTranscript, StopMode.StopPhrasePublishesAutomatically) -> {
                    processTranscript(transcriptBuffer.combined(), publishAfterMatch = shouldAutoPublish(state))
                }
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
        val state: PerformanceUiState = _uiState.value
        val providerName: String = state.speechProviders.firstOrNull { it.id == state.settings.selectedSpeechProviderId }?.displayName ?: "Mock Transcript Mode"
        setState(SessionState.ExtractingData, "AI-first extraction running via $providerName with rule fallback.")
        val extracted: ExtractedPerformanceData = extractionService.extract(
            transcript = transcript,
            startPhrases = if (state.settings.removeStartAndStopPhrases) state.settings.startPhrases + state.activeProfile.startPhrase else emptyList(),
            stopPhrases = if (state.settings.removeStartAndStopPhrases) state.settings.stopPhrases + state.activeProfile.stopPhrase else emptyList(),
            aiEnabled = state.settings.openAiTranscriptionEnabled || state.settings.elevenLabsEnabled
        )
        _uiState.update { current ->
            current.copy(
                extractedData = extracted,
                currentTranscript = extracted.cleanedTranscript,
                lastTranscriptLine = extracted.cleanedTranscript,
                logs = prependLog(current.logs, logger.entry("Detected ${extracted.detectedItems.size} structured value(s).")),
                errorMessage = null
            )
        }
        setState(SessionState.MatchingChannel, "Matching detected values against active channels.")
        val latest: PerformanceUiState = _uiState.value
        val matches: List<ChannelMatch> = channelMatcher.match(latest.activeProfile, latest.channels, extracted)
        val selected: ChannelMatch? = matches.firstOrNull()
        _uiState.update { current ->
            current.copy(
                selectedMatch = selected,
                activeChannels = current.channels.filter { it.id in current.activeProfile.activeChannelIds },
                logs = prependLog(current.logs, logger.entry(selected?.let { "Channel selected: ${it.channel.name} → ${it.payload}" } ?: "No channel match found.", if (selected != null) LogLevel.Success else LogLevel.Warning))
            )
        }
        if (publishAfterMatch && selected != null) {
            simulateInjectPublish()
        } else {
            setState(SessionState.MatchingChannel, "Review Mode is on. Approve before publishing.")
        }
    }

    private fun rematchIfPossible() {
        val state: PerformanceUiState = _uiState.value
        val extracted: ExtractedPerformanceData = state.extractedData ?: return
        val matches: List<ChannelMatch> = channelMatcher.match(state.activeProfile, state.channels, extracted)
        _uiState.update { current ->
            current.copy(
                selectedMatch = matches.firstOrNull(),
                activeChannels = current.channels.filter { it.id in current.activeProfile.activeChannelIds }
            )
        }
    }

    private fun shouldAutoPublish(state: PerformanceUiState): Boolean {
        if (state.activeProfile.fullAutomationEnabled || state.activeProfile.stopMode == StopMode.StopPhrasePublishesAutomatically || state.settings.fullAutomationEnabled) return true
        return state.channels.any { channel -> channel.enabled && channel.autoPublish && channel.id in state.activeProfile.activeChannelIds }
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
        startPhrases = if (state.settings.startPhraseEnabled) state.settings.startPhrases + state.activeProfile.startPhrase else emptyList(),
        stopPhrases = if (state.settings.stopPhraseEnabled) state.settings.stopPhrases + state.activeProfile.stopPhrase else emptyList(),
        removeStartAndStopPhrases = state.settings.removeStartAndStopPhrases,
        chunkDurationMs = 3_000,
        silenceBehavior = SilenceBehavior.Ignore,
        providerApiKey = when (state.settings.selectedSpeechProviderId) {
            "openai_chunk", "openai_realtime" -> state.settings.openAiApiKey.ifBlank { null }
            "elevenlabs_stt" -> state.settings.elevenLabsApiKey.ifBlank { null }
            else -> null
        },
        model = when (state.settings.selectedSpeechProviderId) {
            "elevenlabs_stt" -> state.settings.elevenLabsModel
            else -> state.settings.openAiModel
        }
    )

    private fun updateActiveProfile(logMessage: String, transform: (PerformanceProfile) -> PerformanceProfile) {
        _uiState.update { state ->
            val updatedProfile: PerformanceProfile = transform(state.activeProfile)
            state.copy(
                activeProfile = updatedProfile,
                profiles = state.profiles.map { if (it.id == updatedProfile.id) updatedProfile else it },
                activeChannels = state.channels.filter { it.id in updatedProfile.activeChannelIds },
                logs = prependLog(state.logs, logger.entry(logMessage)),
                errorMessage = null
            )
        }
    }

    private fun updateSettings(logMessage: String, transform: (com.rork.whispercell.models.AppSettings) -> com.rork.whispercell.models.AppSettings) {
        _uiState.update { state ->
            state.copy(
                settings = transform(state.settings),
                logs = prependLog(state.logs, logger.entry(logMessage)),
                errorMessage = null
            )
        }
    }

    private fun updateChannel(channelId: String, logMessage: String, transform: (Channel) -> Channel) {
        _uiState.update { state ->
            val updatedChannels: List<Channel> = state.channels.map { channel -> if (channel.id == channelId) transform(channel) else channel }
            state.copy(
                channels = updatedChannels,
                activeChannels = updatedChannels.filter { it.id in state.activeProfile.activeChannelIds },
                logs = prependLog(state.logs, logger.entry(logMessage)),
                errorMessage = null
            )
        }
    }

    private fun injectCodeFor(channel: Channel?, state: PerformanceUiState): String {
        val channelCode: String = if (channel != null && !channel.useDefaultInjectCode) channel.customInjectCode.orEmpty() else ""
        return channelCode.ifBlank { state.activeProfile.injectCode.ifBlank { state.settings.defaultInjectCode } }
    }

    private fun prependLog(logs: List<LogEntry>, entry: LogEntry): List<LogEntry> = (listOf(entry) + logs).take(100)

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
            injectCode = "",
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
