package com.rork.whispercell.services

import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.models.SpeechProviderMode

object ProviderCatalog {
    val providers: List<SpeechProviderInfo> = listOf(
        SpeechProviderInfo(
            id = "native_recorder",
            displayName = "Native Recorder",
            mode = SpeechProviderMode.Live,
            supportsBackground = true,
            supportsPartialResults = true,
            supportsTimestamps = true,
            status = "Continuous recording engine"
        ),
        SpeechProviderInfo(
            id = "openai_chunk",
            displayName = "OpenAI Transcription",
            mode = SpeechProviderMode.Chunk,
            supportsBackground = true,
            supportsPartialResults = false,
            supportsTimestamps = true,
            status = "Transcribes recorded audio chunks"
        ),
        SpeechProviderInfo(
            id = "elevenlabs_stt",
            displayName = "ElevenLabs Speech to Text",
            mode = SpeechProviderMode.Chunk,
            supportsBackground = true,
            supportsPartialResults = false,
            supportsTimestamps = true,
            status = "Transcribes recorded audio chunks"
        )
    )
}
