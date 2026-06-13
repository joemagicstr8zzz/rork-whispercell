package com.rork.whispercell.services

import com.rork.whispercell.models.SpeechError
import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.models.SpeechProviderMode
import com.rork.whispercell.models.SpeechSessionConfig

/** Preview-safe speech engine that emits performer test transcripts without microphone access. */
class MockSpeechProvider : SpeechProvider {
    override val info: SpeechProviderInfo = SpeechProviderInfo(
        id = "mock",
        displayName = "Mock Transcript Mode",
        mode = SpeechProviderMode.Mock,
        supportsBackground = true,
        supportsPartialResults = true,
        supportsTimestamps = false,
        status = "Preview ready"
    )

    private var partialCallback: (String) -> Unit = {}
    private var finalCallback: (String) -> Unit = {}
    private var errorCallback: (SpeechError) -> Unit = {}

    override suspend fun startSession(config: SpeechSessionConfig) = Unit
    override suspend fun stopSession() = Unit
    override suspend fun pauseSession() = Unit
    override suspend fun resumeSession() = Unit
    override fun onPartialTranscript(callback: (String) -> Unit) { partialCallback = callback }
    override fun onFinalTranscript(callback: (String) -> Unit) { finalCallback = callback }
    override fun onError(callback: (SpeechError) -> Unit) { errorCallback = callback }

    fun emitPartial(text: String) { partialCallback(text) }
    fun emitFinal(text: String) { finalCallback(text) }
    fun emitError(message: String) { errorCallback(SpeechError(message)) }
}
