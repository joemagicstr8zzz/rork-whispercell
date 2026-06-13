package com.rork.whispercell.services

import com.rork.whispercell.models.ExtractedPerformanceData

/** AI-first extraction boundary. Preview build falls back locally unless a native AI client is added. */
class AIExtractionService(
    private val fallback: RuleExtractionService = RuleExtractionService()
) {
    val systemPrompt: String = """
        You are an extraction engine for a live performance utility. Your job is to read a transcript and extract only information that was actually spoken. Do not invent missing values. Return structured JSON only. Identify useful performance targets such as names, dates, birthdays, places, countries, cities, playing cards, numbers, serial numbers, songs, artists, lyrics, colors, objects, zodiac signs, emotions, celebrities, movies, animals, times, and full confabulation summaries. Normalize obvious values, but preserve the original spoken meaning. If unsure, lower the confidence score. Do not include start phrases or stop phrases in the final payload.
    """.trimIndent()

    suspend fun extract(
        transcript: String,
        startPhrases: List<String>,
        stopPhrases: List<String>,
        aiEnabled: Boolean
    ): ExtractedPerformanceData {
        val fallbackResult: ExtractedPerformanceData = fallback.extract(transcript, startPhrases, stopPhrases)
        return if (aiEnabled) {
            fallbackResult.copy(notes = listOf("AI extraction boundary selected. Preview uses rule fallback until provider credentials and native client are connected."))
        } else {
            fallbackResult
        }
    }
}
