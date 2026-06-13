package com.rork.whispercell.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import com.rork.whispercell.services.AndroidSpeechProvider
import com.rork.whispercell.services.BackgroundSessionService
import com.rork.whispercell.services.ChannelMatcher
import com.rork.whispercell.services.InjectPublisher
import com.rork.whispercell.services.MockSpeechProvider
import com.rork.whispercell.services.PrivacyManager
import com.rork.whispercell.services.ProviderCatalog
import com.rork.whispercell.services.SessionLogger
import com.rork.whispercell.services.SpeechProvider
import com.rork.whispercell.services.StartPhraseDetector
import com.rork.whispercell.services.StopPhraseDetector
import com.rork.whispercell.services.TranscriptBrainService
import com.rork.whispercell.services.TranscriptBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WhisperCellViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = SessionLogger()
    private val backgroundSessionService = BackgroundSessionService()
    private val privacyManager = PrivacyManager()
    private val transcriptBuffer = TranscriptBuffer()
    private val startPhraseDetector = StartPhraseDetector()
    private val stopPhraseDetector = StopPhraseDetector()
    private val extractionService = AIExtractionService()
    private val channelMatcher = ChannelMatcher()
    private val transcriptBrainService = TranscriptBrainService()
    private val androidSpeechProvider = AndroidSpeechProvider(application.applicationContext)
    private val injectPublisher = InjectPublisher()
    private val mockSpeechProvider = MockSpeechProvider()
    private var hasPublishedThisCapture: Boolean = false

    private val defaultChannels = buildDefaultChannels()
    private val defaultProfiles = buildDefaultProfiles(defaultChannels)

    private val _uiState = MutableStateFlow(
        PerformanceUiState(
            activeProfile = defaultProfiles.first { it.name == "Confabulation" },
            profiles = defaultProfiles,
            channels = defaultChannels,
            speechProviders = ProviderCatalog.providers,
            activeChannels = defaultChannels.filter { it.id in defaultProfiles.first { profile -> profile.name == "Confabulation" }.activeChannelIds },
            logs = listOf(logger.entry("WhisperCell ready. Android speech provider is the default live microphone source."))
        )
    )
    val uiState: StateFlow<PerformanceUiState> = _uiState.asStateFlow()

    init {
        registerSpeechCallbacks(mockSpeechProvider)
        registerSpeechCallbacks(androidSpeechProvider)
    }

    private fun registerSpeechCallbacks(provider: SpeechProvider) {
        provider.onPartialTranscript { text -> handlePartialTranscript(text) }
        provider.onFinalTranscript { text -> handleFinalTranscript(text) }
        provider.onError { error -> setState(SessionState.Error, error.message, LogLevel.Error) }
    }

    fun reportPermissionBlocked(missingPermissions: String) {
        _uiState.update { state ->
            state.copy(
                sessionState = SessionState.Idle,
                isListeningVisible = false,
                notificationState = "Permissions required",
                logs = prependLog(state.logs, logger.entry("Live session blocked until permissions are granted: $missingPermissions.", LogLevel.Warning)),
                errorMessage = "Grant $missingPermissions before starting a live session."
            )
        }
    }

    fun startBackgroundSession() {
        viewModelScope.launch {
            val state = _uiState.value
            val provider = speechProviderFor(state)
            hasPublishedThisCapture = false
            provider.startSession(configFrom(state))
            transcriptBuffer.clear()
            _uiState.update { current ->
                current.copy(
                    providerActivity = "${provider.info.displayName} active. Waiting for Start Phrase: ${primaryStartPhrase(current)}",
                    aiActivity = "Waiting for captured speech before extraction.",
                    rawCapturedTranscript = "",
                    transcriptBrain = null,
                    transcriptEvents = prependTranscriptEvent(current.transcriptEvents, "Live session started with ${provider.info.displayName}. Waiting for Start Phrase: ${primaryStartPhrase(current)}")
                )
            }
            setState(SessionState.BackgroundReady, "Live microphone session started using ${provider.info.displayName}.")
            delay(250)
            val next = if (state.settings.startPhraseEnabled) SessionState.WaitingForStartPhrase else SessionState.Capturing
            setState(next, if (next == SessionState.Capturing) "Capturing immediately. Start Phrase is off." else "Listening for Start Phrase: ${primaryStartPhrase(state)}")
        }
    }

    fun stopSession() { viewModelScope.launch { speechProviderFor(_uiState.value).stopSession(); setState(SessionState.Idle, "Session stopped manually.") } }
    fun pauseListening() { viewModelScope.launch { speechProviderFor(_uiState.value).pauseSession(); setState(SessionState.Paused, "Listening paused.") } }
    fun resumeListening() { viewModelScope.launch { speechProviderFor(_uiState.value).resumeSession(); setState(SessionState.WaitingForStartPhrase, "Listening resumed. Waiting for Start Phrase.") } }

    fun panicStop() {
        viewModelScope.launch { speechProviderFor(_uiState.value).stopSession() }
        transcriptBuffer.clear()
        _uiState.update { state ->
            state.copy(sessionState = SessionState.PanicStopped, isListeningVisible = false, notificationState = "Stopped", providerActivity = "Panic stop active. No capture is running.", logs = prependLog(state.logs, logger.entry("Immediate stop pressed. Listening and processing halted.", LogLevel.Warning)), errorMessage = null)
        }
    }

    fun clearSession() {
        transcriptBuffer.clear()
        hasPublishedThisCapture = false
        _uiState.update { state ->
            state.copy(
                currentTranscript = "",
                rawCapturedTranscript = "",
                lastTranscriptLine = "No transcript yet.",
                transcriptEvents = emptyList(),
                extractedData = null,
                transcriptBrain = null,
                selectedMatch = null,
                lastPublishedValue = "Nothing published yet.",
                lastInjectUrl = endpointForState(state),
                injectStatus = if (state.settings.injectEnabled) InjectStatus.Ready else InjectStatus.NotConfigured,
                providerActivity = providerReadinessMessage(state),
                aiActivity = "GPT not run yet. Rule fallback ready.",
                logs = listOf(logger.entry(privacyManager.clearSessionNotice(), LogLevel.Success)),
                errorMessage = null
            )
        }
    }

    fun activateProfile(profileId: String) {
        _uiState.update { state ->
            val profile = state.profiles.firstOrNull { it.id == profileId } ?: state.activeProfile
            state.copy(activeProfile = profile, activeChannels = state.channels.filter { it.id in profile.activeChannelIds }, selectedMatch = null, logs = prependLog(state.logs, logger.entry("Profile activated: ${profile.name}")), errorMessage = null)
        }
        rematchIfPossible()
    }

    fun duplicateActiveProfile() {
        _uiState.update { state ->
            val copyNumber = state.profiles.count { it.name.startsWith(state.activeProfile.name) } + 1
            val duplicated = state.activeProfile.copy(id = "${state.activeProfile.id}_copy_${System.currentTimeMillis()}", name = "${state.activeProfile.name} Copy $copyNumber")
            state.copy(activeProfile = duplicated, profiles = state.profiles + duplicated, logs = prependLog(state.logs, logger.entry("Duplicated profile: ${duplicated.name}", LogLevel.Success)))
        }
    }

    fun deleteActiveProfile() {
        _uiState.update { state ->
            if (state.profiles.size <= 1) state.copy(errorMessage = "At least one profile is required.") else {
                val remaining = state.profiles.filterNot { it.id == state.activeProfile.id }
                val next = remaining.first()
                state.copy(activeProfile = next, profiles = remaining, activeChannels = state.channels.filter { it.id in next.activeChannelIds }, selectedMatch = null, logs = prependLog(state.logs, logger.entry("Deleted profile. Active profile is now ${next.name}.", LogLevel.Warning)), errorMessage = null)
            }
        }
    }

    fun updateActiveProfileStartPhrase(value: String) = updateGlobalStartPhrase(value)
    fun updateActiveProfileStopPhrase(value: String) = updateGlobalStopPhrase(value)

    fun updateGlobalStartPhrase(value: String) {
        val clean = value.trimStart()
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(startPhrases = listOf(clean).filter { it.isNotBlank() }), activeProfile = state.activeProfile.copy(startPhrase = clean), profiles = state.profiles.map { it.copy(startPhrase = clean) }, logs = prependLog(state.logs, logger.entry("Global Start Phrase updated.")), errorMessage = null)
        }
    }

    fun updateGlobalStopPhrase(value: String) {
        val clean = value.trimStart()
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(stopPhrases = listOf(clean).filter { it.isNotBlank() }), activeProfile = state.activeProfile.copy(stopPhrase = clean), profiles = state.profiles.map { it.copy(stopPhrase = clean) }, logs = prependLog(state.logs, logger.entry("Global Stop Phrase updated.")), errorMessage = null)
        }
    }

    fun updateSelectionCode(value: String) {
        val clean = injectPublisher.sanitizeInjectCode(value)
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(selectionCode = clean), lastInjectUrl = if (clean.isBlank()) "" else injectPublisher.publishUrl(clean, previewValue(state)), logs = prependLog(state.logs, logger.entry("Inject Code updated (${clean.length}/7).")), errorMessage = null)
        }
    }

    fun updateMockTranscript(text: String) { _uiState.update { it.copy(mockTranscriptInput = text) } }
    fun loadPresetTranscript(text: String) { _uiState.update { state -> state.copy(mockTranscriptInput = text, logs = prependLog(state.logs, logger.entry("Preset transcript loaded.")), errorMessage = null) } }

    fun fakePartialPlayback() {
        viewModelScope.launch {
            val words = _uiState.value.mockTranscriptInput.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return@launch
            setState(SessionState.Capturing, "Fake partial transcript playback started.")
            words.indices.forEach { index ->
                mockSpeechProvider.emitPartial(words.take(index + 1).joinToString(" "))
                delay(80)
            }
            mockSpeechProvider.emitFinal(_uiState.value.mockTranscriptInput)
        }
    }

    fun runExtractionOnly() { viewModelScope.launch { processTranscript(_uiState.value.mockTranscriptInput, false) } }
    fun runSelectedProfile() { viewModelScope.launch { val s = _uiState.value; handleFinalTranscript("${primaryStartPhrase(s)}. ${s.mockTranscriptInput} ${primaryStopPhrase(s)}.") } }

    fun simulateInjectPublish() {
        val state = _uiState.value
        val value = state.transcriptBrain?.primaryPayload ?: state.selectedMatch?.payload ?: state.extractedData?.bestMatches?.fullConfabulation ?: state.extractedData?.detectedItems?.firstOrNull()?.normalizedValue.orEmpty()
        if (value.isBlank()) { _uiState.update { it.copy(errorMessage = "Run extraction before publishing.") }; return }
        val code = state.settings.selectionCode
        val url = if (code.isBlank()) "Inject Code required" else injectPublisher.publishUrl(code, value)
        _uiState.update { current ->
            current.copy(sessionState = SessionState.Published, injectStatus = if (code.isBlank()) InjectStatus.RetryAvailable else InjectStatus.Published, lastPublishedValue = value, lastInjectUrl = url, notificationState = backgroundSessionService.notificationFor(SessionState.Published.label), logs = prependLog(current.logs, logger.entry("Local publish simulation: $url", if (code.isBlank()) LogLevel.Warning else LogLevel.Success)), errorMessage = if (code.isBlank()) "Enter an Inject Code before real publishing." else null)
        }
    }

    fun toggleInjectEnabled(enabled: Boolean) { updateSettings(if (enabled) "Publishing enabled." else "Publishing disabled.") { it.copy(injectEnabled = enabled) }; _uiState.update { it.copy(injectStatus = if (enabled) InjectStatus.Ready else InjectStatus.NotConfigured) } }

    fun testInject() {
        viewModelScope.launch {
            val state = _uiState.value
            val code = state.settings.selectionCode
            val value = "WhisperCell Test"
            if (code.isBlank()) {
                _uiState.update { current -> current.copy(injectStatus = InjectStatus.RetryAvailable, errorMessage = "Enter your Inject Code first. Use only the code, not the full URL.", logs = prependLog(current.logs, logger.entry("Publish test blocked: no code entered.", LogLevel.Warning))) }
                return@launch
            }
            _uiState.update { current -> current.copy(sessionState = SessionState.PublishingToInject, injectStatus = InjectStatus.Testing, lastInjectUrl = injectPublisher.publishUrl(code, value), logs = prependLog(current.logs, logger.entry("Testing fixed endpoint with JSON value field.")), errorMessage = null) }
            injectPublisher.publish(code, value, false).fold(
                onSuccess = { _uiState.update { current -> current.copy(sessionState = SessionState.Published, injectStatus = InjectStatus.Connected, lastPublishedValue = value, logs = prependLog(current.logs, logger.entry("Test value transmitted once.", LogLevel.Success)), errorMessage = null) } },
                onFailure = { error -> _uiState.update { current -> current.copy(sessionState = SessionState.Error, injectStatus = InjectStatus.RetryAvailable, logs = prependLog(current.logs, logger.entry("Publish test failed: ${error.message ?: "Unknown error"}", LogLevel.Error)), errorMessage = "Publish test failed. Check code and network.") } }
            )
        }
    }

    fun publishSelectedValue() {
        viewModelScope.launch {
            val state = _uiState.value
            val value = state.transcriptBrain?.primaryPayload?.takeIf { it.isNotBlank() } ?: state.selectedMatch?.payload.orEmpty()
            val code = state.settings.selectionCode
            if (value.isBlank()) { _uiState.update { it.copy(errorMessage = "No payload selected. Confirm transcript proof first.") }; return@launch }
            if (!state.settings.injectEnabled) { _uiState.update { it.copy(errorMessage = "Publishing is disabled. Turn it on first.") }; return@launch }
            if (code.isBlank()) { _uiState.update { current -> current.copy(injectStatus = InjectStatus.RetryAvailable, errorMessage = "Enter your Inject Code before publishing.", logs = prependLog(current.logs, logger.entry("Publish blocked: missing code.", LogLevel.Warning))) }; return@launch }
            if (hasPublishedThisCapture) { _uiState.update { current -> current.copy(errorMessage = "This capture already published once. Start a new session or clear before publishing again.", logs = prependLog(current.logs, logger.entry("Duplicate publish blocked for this capture.", LogLevel.Warning))) }; return@launch }
            setState(SessionState.PublishingToInject, "Publishing Transcript Brain payload once.")
            _uiState.update { it.copy(injectStatus = InjectStatus.Publishing, lastInjectUrl = injectPublisher.publishUrl(code, value)) }
            injectPublisher.publish(code, value, false).fold(
                onSuccess = {
                    hasPublishedThisCapture = true
                    _uiState.update { current -> current.copy(sessionState = SessionState.Published, injectStatus = InjectStatus.Published, lastPublishedValue = value, notificationState = backgroundSessionService.notificationFor(SessionState.Published.label), providerActivity = if (current.settings.continueListeningAfterPublish) "Published once. Waiting for next Start Phrase." else "Published once. Capture stopped.", logs = prependLog(current.logs, logger.entry("Published once: $value", LogLevel.Success)), errorMessage = null) }
                },
                onFailure = { error -> _uiState.update { current -> current.copy(sessionState = SessionState.Error, injectStatus = InjectStatus.RetryAvailable, logs = prependLog(current.logs, logger.entry("Publish failed: ${error.message ?: "Unknown error"}", LogLevel.Error)), errorMessage = "Publish failed. Check network or code.") } }
            )
        }
    }

    fun toggleActiveProfileFullAutomation(enabled: Boolean) { updateActiveProfile(if (enabled) "Full Automation enabled." else "Review Mode restored.") { it.copy(fullAutomationEnabled = enabled, reviewModeEnabled = !enabled) }; updateSettings("Automation setting updated.") { it.copy(fullAutomationEnabled = enabled, reviewModeEnabled = !enabled) } }
    fun toggleActiveProfileReviewMode(enabled: Boolean) { updateActiveProfile(if (enabled) "Review Mode enabled." else "Review Mode disabled.") { it.copy(reviewModeEnabled = enabled, fullAutomationEnabled = if (enabled) false else it.fullAutomationEnabled) }; updateSettings("Review setting updated.") { it.copy(reviewModeEnabled = enabled, fullAutomationEnabled = if (enabled) false else it.fullAutomationEnabled) } }

    fun toggleProfileChannel(channelId: String, enabled: Boolean) {
        updateActiveProfile(if (enabled) "Channel added." else "Channel removed.") { profile ->
            val ids = if (enabled) (profile.activeChannelIds + channelId).distinct() else profile.activeChannelIds.filterNot { it == channelId }
            profile.copy(activeChannelIds = ids)
        }
        rematchIfPossible()
    }

    fun toggleChannelEnabled(channelId: String, enabled: Boolean) { updateChannel(channelId, if (enabled) "Channel enabled." else "Channel disabled.") { it.copy(enabled = enabled) } }
    fun toggleChannelAutoPublish(channelId: String, enabled: Boolean) { updateChannel(channelId, if (enabled) "Channel auto-publish enabled." else "Channel auto-publish disabled.") { it.copy(autoPublish = enabled) } }
    fun updateChannelPayloadFormat(channelId: String, value: String) { updateChannel(channelId, "Channel payload format updated.") { it.copy(payloadFormat = value) } }

    fun testChannel(channelId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val extracted = state.extractedData ?: extractionService.extract(
                transcript = state.mockTranscriptInput,
                startPhrases = if (state.settings.removeStartAndStopPhrases) state.settings.startPhrases else emptyList(),
                stopPhrases = if (state.settings.removeStartAndStopPhrases) state.settings.stopPhrases else emptyList(),
                aiEnabled = state.settings.openAiTranscriptionEnabled,
                openAiApiKey = state.settings.openAiApiKey,
                openAiModel = extractionModel(state)
            )
            val testProfile = state.activeProfile.copy(activeChannelIds = listOf(channelId), routingBehavior = RoutingBehavior.BestConfidenceMatch)
            val match = channelMatcher.match(testProfile, state.channels, extracted).firstOrNull()
            val brain = transcriptBrainService.analyze(extracted, match, testProfile, state.channels.filter { it.id == channelId })
            _uiState.update { current ->
                current.copy(
                    extractedData = extracted,
                    transcriptBrain = brain,
                    selectedMatch = match?.copy(payload = brain.primaryPayload.ifBlank { match.payload }, confidence = maxOf(match.confidence, brain.confidence), reason = "Brain test: ${brain.reasoning}"),
                    logs = prependLog(current.logs, logger.entry(match?.let { "Tested ${it.channel.name}: ${brain.primaryPayload.ifBlank { it.payload }}" } ?: "No value matched this output.", if (match != null) LogLevel.Success else LogLevel.Warning)),
                    errorMessage = if (match == null) "No value matched this output. Try a relevant transcript." else null
                )
            }
        }
    }

    fun toggleStartPhraseEnabled(enabled: Boolean) = updateSettings("Start Phrase ${if (enabled) "enabled" else "disabled"}.") { it.copy(startPhraseEnabled = enabled) }
    fun toggleStopPhraseEnabled(enabled: Boolean) = updateSettings("Stop Phrase ${if (enabled) "enabled" else "disabled"}.") { it.copy(stopPhraseEnabled = enabled) }
    fun toggleRemovePhrases(enabled: Boolean) = updateSettings("Phrase removal updated.") { it.copy(removeStartAndStopPhrases = enabled) }
    fun updateMaximumCaptureSeconds(input: String) = updateSettings("Maximum capture updated.") { it.copy(maximumCaptureSeconds = input.filter(Char::isDigit).toIntOrNull()?.coerceIn(5, 600) ?: it.maximumCaptureSeconds) }
    fun selectSpeechProvider(providerId: String) = updateSettings("Speech provider selected: $providerId") { it.copy(selectedSpeechProviderId = providerId) }
    fun toggleOpenAiEnabled(enabled: Boolean) = updateSettings("GPT extraction ${if (enabled) "enabled" else "disabled"}.") { it.copy(openAiTranscriptionEnabled = enabled) }
    fun updateOpenAiApiKey(value: String) = updateSettings("OpenAI key updated in memory.") { it.copy(openAiApiKey = value, openAiValidationStatus = "Not validated") }
    fun updateOpenAiModel(value: String) = updateSettings("OpenAI model updated.") { it.copy(openAiModel = value) }
    fun toggleOpenAiRealtime(enabled: Boolean) = updateSettings("Realtime toggle updated.") { it.copy(openAiRealtimeEnabled = enabled, openAiChunkEnabled = if (enabled) false else it.openAiChunkEnabled) }
    fun toggleOpenAiChunk(enabled: Boolean) = updateSettings("Chunk toggle updated.") { it.copy(openAiChunkEnabled = enabled, openAiRealtimeEnabled = if (enabled) false else it.openAiRealtimeEnabled) }
    fun validateOpenAiKey() { _uiState.update { state -> val ok = state.settings.openAiApiKey.length >= 12; state.copy(settings = state.settings.copy(openAiValidationStatus = if (ok) "Key format looks usable." else "Enter a full API key"), errorMessage = if (ok) null else "OpenAI key is too short.") } }
    fun testOpenAiTranscription() { viewModelScope.launch { processTranscript(_uiState.value.mockTranscriptInput, false) } }
    fun toggleElevenLabsEnabled(enabled: Boolean) = updateSettings("ElevenLabs ${if (enabled) "enabled" else "disabled"}.") { it.copy(elevenLabsEnabled = enabled) }
    fun updateElevenLabsApiKey(value: String) = updateSettings("ElevenLabs key updated in memory.") { it.copy(elevenLabsApiKey = value, elevenLabsValidationStatus = "Not validated") }
    fun updateElevenLabsModel(value: String) = updateSettings("ElevenLabs model updated.") { it.copy(elevenLabsModel = value) }
    fun validateElevenLabsKey() { _uiState.update { state -> val ok = state.settings.elevenLabsApiKey.length >= 12; state.copy(settings = state.settings.copy(elevenLabsValidationStatus = if (ok) "Looks usable." else "Enter a full API key"), errorMessage = if (ok) null else "ElevenLabs key is too short.") } }
    fun testElevenLabsTranscription() { viewModelScope.launch { processTranscript(_uiState.value.mockTranscriptInput, false) } }
    fun toggleAudioSaving(enabled: Boolean) = updateSettings(if (enabled) "Audio saving explicitly enabled." else "Audio saving off.") { it.copy(audioSavingEnabled = enabled) }
    fun toggleContinueListening(enabled: Boolean) = updateSettings("Continue listening updated.") { it.copy(continueListeningAfterPublish = enabled) }
    fun toggleKeepLogs24Hours(enabled: Boolean) = updateSettings("Log retention updated.") { it.copy(keepLogsFor24Hours = enabled, transcriptSavePolicy = if (enabled) "Keep logs for 24 hours" else "Current session only") }

    private fun handlePartialTranscript(text: String) {
        _uiState.update { state ->
            val didStart = state.sessionState == SessionState.WaitingForStartPhrase && startPhraseDetector.containsStartPhrase(text, if (state.settings.startPhraseEnabled) state.settings.startPhrases else emptyList())
            if (didStart) hasPublishedThisCapture = false
            state.copy(
                lastTranscriptLine = text,
                currentTranscript = text,
                rawCapturedTranscript = text,
                transcriptEvents = prependTranscriptEvent(state.transcriptEvents, if (didStart) "START PHRASE CAUGHT. Capturing now: $text" else "Partial heard: $text"),
                sessionState = if (didStart) SessionState.Capturing else state.sessionState,
                providerActivity = if (didStart) "Start Phrase caught. Capturing everything until Stop Phrase." else if (state.sessionState == SessionState.Capturing) "Capturing speech. Waiting for Stop Phrase." else state.providerActivity,
                isListeningVisible = true,
                logs = if (didStart) prependLog(state.logs, logger.entry("Start Phrase caught. Capture is now active.", LogLevel.Success)) else state.logs
            )
        }
    }

    private fun handleFinalTranscript(text: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val hasStart = !state.settings.startPhraseEnabled || startPhraseDetector.containsStartPhrase(text, state.settings.startPhrases) || state.sessionState in listOf(SessionState.Capturing, SessionState.ThinkingPause)
            val hasStop = state.settings.stopPhraseEnabled && stopPhraseDetector.containsStopPhrase(text, state.settings.stopPhrases)
            if (hasStart && state.sessionState == SessionState.WaitingForStartPhrase) hasPublishedThisCapture = false
            transcriptBuffer.append(text)
            _uiState.update { current ->
                current.copy(
                    lastTranscriptLine = text,
                    currentTranscript = transcriptBuffer.combined(),
                    rawCapturedTranscript = transcriptBuffer.combined(),
                    transcriptEvents = prependTranscriptEvent(current.transcriptEvents, when {
                        hasStop -> "STOP PHRASE CAUGHT. Capture closed: $text"
                        hasStart -> "Start Phrase confirmed in final transcript: $text"
                        else -> "Final transcript chunk: $text"
                    }),
                    providerActivity = when {
                        hasStop -> "Stop Phrase caught. Capture stopped. Processing transcript now."
                        hasStart -> "Start Phrase caught. Capturing everything until Stop Phrase."
                        else -> current.providerActivity
                    },
                    logs = prependLog(current.logs, logger.entry("Final transcript captured: ${trimForLog(text)}"))
                )
            }
            when {
                state.settings.startPhraseEnabled && !hasStart && state.sessionState == SessionState.WaitingForStartPhrase -> _uiState.update { current -> current.copy(logs = prependLog(current.logs, logger.entry("Transcript heard, still waiting for Start Phrase."))) }
                hasStop -> processTranscript(transcriptBuffer.combined(), shouldAutoPublish(state))
                else -> setState(SessionState.ThinkingPause, "Thinking pause detected. Silence does not finalize capture by default.")
            }
        }
    }

    private suspend fun processTranscript(transcript: String, publishAfterMatch: Boolean) {
        if (transcript.isBlank()) return
        setState(SessionState.ProcessingTranscript, "Transcript captured. Cleaning performance patter.")
        delay(150)
        val state = _uiState.value
        setState(SessionState.ExtractingData, if (state.settings.openAiTranscriptionEnabled && state.settings.openAiApiKey.isNotBlank()) "Running GPT extraction." else "Running rule fallback extraction.")
        val extracted = extractionService.extract(
            transcript = transcript,
            startPhrases = if (state.settings.removeStartAndStopPhrases) state.settings.startPhrases else emptyList(),
            stopPhrases = if (state.settings.removeStartAndStopPhrases) state.settings.stopPhrases else emptyList(),
            aiEnabled = state.settings.openAiTranscriptionEnabled,
            openAiApiKey = state.settings.openAiApiKey,
            openAiModel = extractionModel(state)
        )
        _uiState.update { current -> current.copy(extractedData = extracted, currentTranscript = extracted.cleanedTranscript, lastTranscriptLine = extracted.cleanedTranscript, aiActivity = "${extracted.extractionSource}: detected ${extracted.detectedItems.size} value(s).", logs = prependLog(prependLog(prependLog(current.logs, logger.entry("Transcript proof raw: ${trimForLog(extracted.rawTranscript)}")), logger.entry("Transcript proof cleaned: ${trimForLog(extracted.cleanedTranscript)}")), logger.entry("${extracted.extractionSource}: detected ${extracted.detectedItems.size} value(s).")), errorMessage = null) }
        setState(SessionState.MatchingChannel, "Matching detected values against active channels.")
        rematchIfPossible()
        val matched = _uiState.value.selectedMatch
        val brain = transcriptBrainService.analyze(extracted, matched, _uiState.value.activeProfile, _uiState.value.activeChannels)
        val brainMatch = matched?.let { match ->
            val payload = brain.primaryPayload.ifBlank { match.payload }
            match.copy(payload = payload, confidence = maxOf(match.confidence, brain.confidence), reason = "Brain: ${brain.reasoning}")
        }
        _uiState.update { current ->
            current.copy(
                transcriptBrain = brain,
                selectedMatch = brainMatch ?: matched,
                logs = prependLog(
                    prependLog(current.logs, logger.entry("Transcript Brain primary: ${brain.primaryPayload.ifBlank { "none" }}", if (brain.primaryPayload.isNotBlank()) LogLevel.Success else LogLevel.Warning)),
                    logger.entry("Transcript Brain confidence: ${(brain.confidence * 100).toInt()}% — ${brain.reasoning}")
                )
            )
        }
        val selected = _uiState.value.selectedMatch
        _uiState.update { current -> current.copy(logs = prependLog(current.logs, logger.entry(selected?.let { "Transcript proof selected payload: ${it.payload}" } ?: "Transcript proof selected payload: none", if (selected != null) LogLevel.Success else LogLevel.Warning))) }
        transcriptBuffer.clear()
        if (publishAfterMatch && selected != null && brain.shouldPublish) publishSelectedValue() else {
            if (publishAfterMatch && !brain.shouldPublish) _uiState.update { current -> current.copy(logs = prependLog(current.logs, logger.entry("Auto-publish paused by Transcript Brain. Review proof before sending.", LogLevel.Warning))) }
            setState(SessionState.MatchingChannel, "Review Mode is on. Approve before publishing.")
        }
    }

    private fun rematchIfPossible() {
        val state = _uiState.value
        val extracted = state.extractedData ?: return
        val matches = channelMatcher.match(state.activeProfile, state.channels, extracted)
        _uiState.update { current -> current.copy(selectedMatch = matches.firstOrNull(), activeChannels = current.channels.filter { it.id in current.activeProfile.activeChannelIds }) }
    }

    private fun shouldAutoPublish(state: PerformanceUiState): Boolean = state.activeProfile.fullAutomationEnabled || state.settings.fullAutomationEnabled || state.channels.any { it.enabled && it.autoPublish && it.id in state.activeProfile.activeChannelIds }

    private fun setState(state: SessionState, message: String, level: LogLevel = LogLevel.Info) {
        _uiState.update { current -> current.copy(sessionState = state, isListeningVisible = state in listOf(SessionState.BackgroundReady, SessionState.WaitingForStartPhrase, SessionState.Capturing, SessionState.ThinkingPause), notificationState = backgroundSessionService.notificationFor(state.label), logs = prependLog(current.logs, logger.entry(message, level)), errorMessage = if (level == LogLevel.Error) message else null) }
    }

    private fun configFrom(state: PerformanceUiState): SpeechSessionConfig = SpeechSessionConfig(state.settings.language, if (state.settings.startPhraseEnabled) state.settings.startPhrases else emptyList(), if (state.settings.stopPhraseEnabled) state.settings.stopPhrases else emptyList(), state.settings.removeStartAndStopPhrases, 3_000, SilenceBehavior.Ignore, model = state.settings.openAiModel)
    private fun primaryStartPhrase(state: PerformanceUiState): String = state.settings.startPhrases.firstOrNull().orEmpty().ifBlank { "Picture this clearly for me" }
    private fun primaryStopPhrase(state: PerformanceUiState): String = state.settings.stopPhrases.firstOrNull().orEmpty().ifBlank { "Perfect" }
    private fun extractionModel(state: PerformanceUiState): String = if (state.settings.openAiModel.contains("transcribe", true) || state.settings.openAiModel.isBlank()) "gpt-4o-mini" else state.settings.openAiModel
    private fun speechProviderFor(state: PerformanceUiState): SpeechProvider = when (state.settings.selectedSpeechProviderId) {
        "android_builtin" -> androidSpeechProvider
        "mock" -> mockSpeechProvider
        else -> mockSpeechProvider
    }
    private fun providerReadinessMessage(state: PerformanceUiState): String = when (state.settings.selectedSpeechProviderId) {
        "android_builtin" -> "Android Built-In selected. Press Start Background Session and speak clearly."
        "mock" -> "Mock Transcript Mode selected. Use developer/demo routines only."
        else -> "${state.speechProviders.firstOrNull { it.id == state.settings.selectedSpeechProviderId }?.displayName ?: "Provider"} is configured as a boundary, but live capture is not wired yet."
    }
    private fun previewValue(state: PerformanceUiState): String = state.transcriptBrain?.primaryPayload?.takeIf { it.isNotBlank() } ?: state.selectedMatch?.payload ?: state.lastPublishedValue.takeUnless { it == "Nothing published yet." } ?: "WhisperCell Test"
    private fun endpointForState(state: PerformanceUiState): String = state.settings.selectionCode.takeIf { it.isNotBlank() }?.let { injectPublisher.publishUrl(it, previewValue(state)) }.orEmpty()
    private fun trimForLog(value: String, max: Int = 220): String = value.replace(Regex("\\s+"), " ").trim().let { if (it.length <= max) it else it.take(max) + "…" }
    private fun prependTranscriptEvent(events: List<String>, event: String): List<String> = (listOf(event) + events).take(20)
    private fun prependLog(logs: List<LogEntry>, entry: LogEntry): List<LogEntry> = (listOf(entry) + logs).take(100)

    private fun updateActiveProfile(logMessage: String, transform: (PerformanceProfile) -> PerformanceProfile) {
        _uiState.update { state ->
            val updated = transform(state.activeProfile)
            state.copy(activeProfile = updated, profiles = state.profiles.map { if (it.id == updated.id) updated else it }, activeChannels = state.channels.filter { it.id in updated.activeChannelIds }, logs = prependLog(state.logs, logger.entry(logMessage)), errorMessage = null)
        }
    }

    private fun updateSettings(logMessage: String, transform: (AppSettings) -> AppSettings) {
        _uiState.update { state -> state.copy(settings = transform(state.settings), logs = prependLog(state.logs, logger.entry(logMessage)), errorMessage = null) }
    }

    private fun updateChannel(channelId: String, logMessage: String, transform: (Channel) -> Channel) {
        _uiState.update { state ->
            val updated = state.channels.map { if (it.id == channelId) transform(it) else it }
            state.copy(channels = updated, activeChannels = updated.filter { it.id in state.activeProfile.activeChannelIds }, logs = prependLog(state.logs, logger.entry(logMessage)), errorMessage = null)
        }
        rematchIfPossible()
    }

    private fun buildDefaultChannels(): List<Channel> = listOf(
        Channel("word", "Word Channel", true, listOf(DetectedCategory.Word, DetectedCategory.Phrase), listOf(DetectedCategory.Word, DetectedCategory.Phrase), "{value}", false, 0.7f, 5, false, true),
        Channel("card", "Card Channel", true, listOf(DetectedCategory.PlayingCard), listOf(DetectedCategory.PlayingCard), "{card}", false, 0.82f, 5, true, false),
        Channel("date", "Date Channel", true, listOf(DetectedCategory.Date, DetectedCategory.Birthday), listOf(DetectedCategory.Date, DetectedCategory.Birthday), "{date}", false, 0.75f, 5, false, true),
        Channel("music", "Music Channel", true, listOf(DetectedCategory.Song, DetectedCategory.Artist, DetectedCategory.Lyric), listOf(DetectedCategory.Song, DetectedCategory.Artist, DetectedCategory.Lyric), "{song}", false, 0.72f, 5, false, true),
        Channel("serial", "Serial Number Channel", true, listOf(DetectedCategory.SerialNumber, DetectedCategory.Number), listOf(DetectedCategory.SerialNumber, DetectedCategory.Number), "{serial}", false, 0.8f, 5, true, false),
        Channel("zodiac", "Zodiac Channel", true, listOf(DetectedCategory.Zodiac, DetectedCategory.Birthday), listOf(DetectedCategory.Zodiac, DetectedCategory.Birthday), "{zodiac}", false, 0.75f, 5, false, true),
        Channel("name", "Name Channel", true, listOf(DetectedCategory.Name, DetectedCategory.Celebrity), listOf(DetectedCategory.Name, DetectedCategory.Celebrity), "{name}", false, 0.7f, 5, false, true),
        Channel("object", "Object Channel", true, listOf(DetectedCategory.Object), listOf(DetectedCategory.Object), "{object}", false, 0.7f, 5, false, true),
        Channel("confabulation", "Confabulation Channel", true, listOf(DetectedCategory.FullConfabulation, DetectedCategory.Place, DetectedCategory.Name, DetectedCategory.Date, DetectedCategory.Object, DetectedCategory.Song), listOf(DetectedCategory.FullConfabulation, DetectedCategory.Place, DetectedCategory.Celebrity, DetectedCategory.Name, DetectedCategory.Date), "{place} | {person} | {date}", false, 0.7f, 8, false, true)
    )

    private fun buildDefaultProfiles(channels: List<Channel>): List<PerformanceProfile> {
        fun profile(id: String, name: String, channelId: String, categories: List<DetectedCategory>) = PerformanceProfile(id, name, "Picture this clearly for me", "Perfect", "mock", categories, listOf(channelId), true, false, StartMode.StartPhraseRequired, StopMode.StopPhraseProcessesTranscript, RoutingBehavior.BestConfidenceMatch, 0.72f, 5, true)
        return listOf(
            profile("basic_word", "Basic Word Reveal", "word", listOf(DetectedCategory.Word, DetectedCategory.Phrase)),
            profile("card", "Card Reveal", "card", listOf(DetectedCategory.PlayingCard)),
            profile("date", "Date or Birthday Reveal", "date", listOf(DetectedCategory.Date, DetectedCategory.Birthday)),
            profile("music", "Music Reveal", "music", listOf(DetectedCategory.Song, DetectedCategory.Artist, DetectedCategory.Lyric)),
            profile("serial", "Serial Number Reveal", "serial", listOf(DetectedCategory.SerialNumber, DetectedCategory.Number)),
            profile("zodiac", "Zodiac Reveal", "zodiac", listOf(DetectedCategory.Zodiac, DetectedCategory.Birthday)),
            profile("name", "Name Reveal", "name", listOf(DetectedCategory.Name, DetectedCategory.Celebrity)),
            profile("object", "Object Reveal", "object", listOf(DetectedCategory.Object)),
            profile("confab", "Confabulation", "confabulation", listOf(DetectedCategory.Place, DetectedCategory.Celebrity, DetectedCategory.Name, DetectedCategory.Date, DetectedCategory.Object, DetectedCategory.Song, DetectedCategory.FullConfabulation)),
            profile("custom", "Custom Profile", "word", DetectedCategory.entries)
        ).filter { profile -> channels.any { it.id in profile.activeChannelIds } }
    }
}
