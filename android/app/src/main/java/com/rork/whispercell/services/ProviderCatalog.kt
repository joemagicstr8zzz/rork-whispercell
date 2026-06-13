package com.rork.whispercell.services

import com.rork.whispercell.models.SpeechProviderInfo
import com.rork.whispercell.models.SpeechProviderMode

object ProviderCatalog {
    val providers: List<SpeechProviderInfo> = listOf(
        SpeechProviderInfo(
            id = "android_builtin",
            displayName = "Android Built-In Speech Recognition",
            mode = SpeechProviderMode.Live,
            supportsBackground = false,
            supportsPartialResults = true,
            supportsTimestamps = false,
            status = "Native implementation boundary"
        ),
        SpeechProviderInfo(
            id = "openai_chunk",
            displayName = "OpenAI Transcription — Chunk",
            mode = SpeechProviderMode.Chunk,
            supportsBackground = true,
            supportsPartialResults = false,
            supportsTimestamps = true,
            status = "Configured in settings"
        ),
        SpeechProviderInfo(
            id = "openai_realtime",
            displayName = "OpenAI Realtime Transcription",
            mode = SpeechProviderMode.Live,
            supportsBackground = true,
            supportsPartialResults = true,
            supportsTimestamps = true,
            status = "Service boundary ready"
        ),
        SpeechProviderInfo(
            id = "elevenlabs_stt",
            displayName = "ElevenLabs Speech to Text",
            mode = SpeechProviderMode.Chunk,
            supportsBackground = true,
            supportsPartialResults = false,
            supportsTimestamps = true,
            status = "Optional provider boundary"
        ),
        MockSpeechProvider().info
    )
}
