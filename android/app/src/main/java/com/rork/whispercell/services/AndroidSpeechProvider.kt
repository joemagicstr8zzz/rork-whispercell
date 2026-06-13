package com.rork.whispercell.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.rork.whispercell.models.SpeechError
import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.models.SpeechProviderMode
import com.rork.whispercell.models.SpeechSessionConfig
import java.util.Locale

/** Native Android speech recognizer for on-device/live microphone testing. */
class AndroidSpeechProvider(
    private val context: Context
) : SpeechProvider {
    override val info: SpeechProviderInfo = SpeechProviderInfo(
        id = "android_builtin",
        displayName = "Android Built-In Speech Recognition",
        mode = SpeechProviderMode.Live,
        supportsBackground = false,
        supportsPartialResults = true,
        supportsTimestamps = false,
        status = "Live microphone ready"
    )

    private var recognizer: SpeechRecognizer? = null
    private var sessionConfig: SpeechSessionConfig? = null
    private var isPaused: Boolean = false
    private var isRunning: Boolean = false
    private var partialCallback: (String) -> Unit = {}
    private var finalCallback: (String) -> Unit = {}
    private var errorCallback: (SpeechError) -> Unit = {}

    override suspend fun startSession(config: SpeechSessionConfig) {
        sessionConfig = config
        isPaused = false
        isRunning = true

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            errorCallback(SpeechError("Android speech recognition is not available on this device.", recoverable = false))
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener())
            startListening(intentFor(config))
        }
    }

    override suspend fun stopSession() {
        isRunning = false
        isPaused = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    override suspend fun pauseSession() {
        isPaused = true
        recognizer?.stopListening()
    }

    override suspend fun resumeSession() {
        val config = sessionConfig ?: return
        isPaused = false
        isRunning = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener())
            startListening(intentFor(config))
        }
    }

    override fun onPartialTranscript(callback: (String) -> Unit) { partialCallback = callback }
    override fun onFinalTranscript(callback: (String) -> Unit) { finalCallback = callback }
    override fun onError(callback: (SpeechError) -> Unit) { errorCallback = callback }

    private fun listener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.bestText()
            if (text.isNotBlank()) partialCallback(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestText()
            if (text.isNotBlank()) finalCallback(text)
            restartIfNeeded()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartIfNeeded()
                else -> {
                    errorCallback(SpeechError("Android speech recognition error $error"))
                    restartIfNeeded()
                }
            }
        }
    }

    private fun restartIfNeeded() {
        val config = sessionConfig ?: return
        if (!isRunning || isPaused) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener())
            startListening(intentFor(config))
        }
    }

    private fun intentFor(config: SpeechSessionConfig): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, normalizeLanguage(config.language))
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    }

    private fun Bundle?.bestText(): String = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()

    private fun normalizeLanguage(language: String): String = when (language.lowercase(Locale.US)) {
        "en", "english" -> "en-US"
        "ja", "japanese" -> "ja-JP"
        else -> language.ifBlank { "en-US" }
    }
}
