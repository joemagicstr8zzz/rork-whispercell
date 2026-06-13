package com.rork.whispercell.services

import com.rork.whispercell.models.SpeechError
import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.models.SpeechSessionConfig

/** Common boundary for live, chunk, realtime, and mock speech engines. */
interface SpeechProvider {
    val info: SpeechProviderInfo

    suspend fun startSession(config: SpeechSessionConfig)
    suspend fun stopSession()
    suspend fun pauseSession()
    suspend fun resumeSession()
    fun onPartialTranscript(callback: (String) -> Unit)
    fun onFinalTranscript(callback: (String) -> Unit)
    fun onError(callback: (SpeechError) -> Unit)
}
