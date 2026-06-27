package com.rork.whispercell.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.rork.whispercell.models.SpeechError
import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.models.SpeechProviderMode
import com.rork.whispercell.models.SpeechSessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Device speech recognizer wrapper for real Android microphone transcription. */
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
        status = "Live on supported Android devices"
    )

    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var activeConfig: SpeechSessionConfig? = null
    private var isActive: Boolean = false
    private var isPaused: Boolean = false
    private var partialCallback: (String) -> Unit = {}
    private var finalCallback: (String) -> Unit = {}
    private var errorCallback: (SpeechError) -> Unit = {}

    override suspend fun startSession(config: SpeechSessionConfig) = withContext(Dispatchers.Main) {
        activeConfig = config
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            errorCallback(SpeechError("Android speech recognition is not available on this device or emulator.", recoverable = false))
            return@withContext
        }
        isActive = true
        isPaused = false
        ensureRecognizer()
        startListeningSafely()
    }

    override suspend fun stopSession() = withContext(Dispatchers.Main) {
        isActive = false
        isPaused = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override suspend fun pauseSession() {
        withContext(Dispatchers.Main) {
            isPaused = true
            mainHandler.removeCallbacksAndMessages(null)
            recognizer?.stopListening()
        }
    }

    override suspend fun resumeSession() = withContext(Dispatchers.Main) {
        if (activeConfig == null) return@withContext
        isActive = true
        isPaused = false
        ensureRecognizer()
        startListeningSafely()
    }

    override fun onPartialTranscript(callback: (String) -> Unit) {
        partialCallback = callback
    }

    override fun onFinalTranscript(callback: (String) -> Unit) {
        finalCallback = callback
    }

    override fun onError(callback: (SpeechError) -> Unit) {
        errorCallback = callback
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
    }

    private val listener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            bestResult(partialResults)?.let(partialCallback)
        }

        override fun onResults(results: Bundle?) {
            bestResult(results)?.let(finalCallback)
            scheduleRestart(delayMillis = 250L)
        }

        override fun onError(error: Int) {
            val message: String = speechErrorMessage(error)
            val recoverable: Boolean = error !in setOf(
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                SpeechRecognizer.ERROR_CLIENT
            )
            if (!recoverable) {
                errorCallback(SpeechError(message, recoverable = false))
                isActive = false
                return
            }
            if (error !in setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                errorCallback(SpeechError(message, recoverable = true))
            }
            scheduleRestart(delayMillis = 500L)
        }
    }

    private fun startListeningSafely() {
        if (!isActive || isPaused) return
        val config: SpeechSessionConfig = activeConfig ?: return
        runCatching {
            recognizer?.startListening(recognizerIntent(config))
        }.onFailure { throwable ->
            errorCallback(SpeechError("Could not start Android speech recognition: ${throwable.message ?: "unknown error"}"))
            scheduleRestart(delayMillis = 800L)
        }
    }

    private fun scheduleRestart(delayMillis: Long) {
        if (!isActive || isPaused) return
        mainHandler.postDelayed({ startListeningSafely() }, delayMillis)
    }

    private fun recognizerIntent(config: SpeechSessionConfig): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language.ifBlank { Locale.getDefault().toLanguageTag() })
    }

    private fun bestResult(bundle: Bundle?): String? = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Android speech recognizer reported an audio error."
        SpeechRecognizer.ERROR_CLIENT -> "Android speech recognizer client error. Restart the session."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for live recognition."
        SpeechRecognizer.ERROR_NETWORK -> "Android speech recognizer network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Android speech recognizer network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match yet. Continuing to listen."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Android speech recognizer is busy. Retrying."
        SpeechRecognizer.ERROR_SERVER -> "Android speech recognizer service error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard yet. Continuing to listen."
        else -> "Android speech recognizer error $error."
    }
}
