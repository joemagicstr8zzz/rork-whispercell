package com.rork.whispercell.services

import com.rork.whispercell.models.BestMatchSummary
import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.ChannelMatch
import com.rork.whispercell.models.DetectedCategory
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.ExtractedPerformanceData
import com.rork.whispercell.models.PerformanceProfile
import com.rork.whispercell.models.TranscriptBrainResult
import kotlin.math.max

/**
 * Intelligent routing layer for WhisperCell.
 * Extraction says what was heard. The brain decides the natural search-style payload for Inject.
 */
class TranscriptBrainService {
    fun analyze(
        extracted: ExtractedPerformanceData,
        selectedMatch: ChannelMatch?,
        activeProfile: PerformanceProfile,
        activeChannels: List<Channel>
    ): TranscriptBrainResult {
        val transcript = extracted.cleanedTranscript.ifBlank { extracted.rawTranscript }
        val lowerTranscript = transcript.lowercase()
        val items = extracted.detectedItems
        val warnings = mutableListOf<String>()
        val rejected = findRejectedValues(items, lowerTranscript)
        val correctedItems = removeRejected(items, rejected)
        val strongestItem = correctedItems.maxWithOrNull(compareBy<DetectedItem> { it.confidence }.thenBy { positionScore(it, lowerTranscript) })
        val smartDecision = decideNaturalPayload(extracted.bestMatches, correctedItems, transcript)
        val selectedPayload = selectedMatch?.payload.orEmpty().takeIf { it.isNotBlank() && !payloadLooksRejected(it, rejected) }
        val primary = smartDecision.payload.ifBlank { selectedPayload.orEmpty() }.ifBlank { strongestItem?.normalizedValue.orEmpty() }
        val routineGuess = smartDecision.kind

        if (items.size > correctedItems.size) warnings += "One or more values sounded rejected or corrected."
        if (hasCorrectionLanguage(lowerTranscript)) warnings += "Correction language detected. Prefer the final confirmed value."
        if (primary.isBlank()) warnings += "No usable performance payload found yet."

        val backups = buildBackupPayloads(correctedItems, extracted.bestMatches, selectedPayload.orEmpty(), smartDecision.multiPayload, primary, transcript)
        val confidence = scoreConfidence(primary, selectedMatch, strongestItem, warnings, correctedItems, smartDecision.confidence)
        val shouldPublish = primary.isNotBlank() && confidence >= 0.68f
        val reasoning = smartDecision.reason.ifBlank { buildReasoning(primary, selectedMatch, strongestItem, routineGuess, warnings) }

        return TranscriptBrainResult(
            primaryPayload = primary,
            backupPayloads = backups,
            routineGuess = routineGuess,
            confidence = confidence,
            reasoning = reasoning,
            warnings = warnings.distinct(),
            rejectedValues = rejected.map { it.normalizedValue }.distinct(),
            shouldPublish = shouldPublish,
            emergencyRevealText = primary
        )
    }

    private data class SmartDecision(
        val payload: String,
        val multiPayload: String,
        val kind: String,
        val confidence: Float,
        val reason: String
    )

    private fun decideNaturalPayload(best: BestMatchSummary, items: List<DetectedItem>, transcript: String): SmartDecision {
        val multi = buildNaturalMultiPayload(best, items)
        if (multi.parts.size >= 2) {
            return SmartDecision(
                payload = multi.payload,
                multiPayload = multi.payload,
                kind = "Confabulation / multi-value search",
                confidence = 0.9f,
                reason = "Multiple meaningful values were spoken, so WhisperCell built one natural search-style Inject payload."
            )
        }

        best.serial?.let { return one(it, "Serial number reveal", 0.94f, "A serial-style value was detected and sent exactly as spoken.") }
        best.card?.let { return one(it, "Card reveal", 0.92f, "A playing card was detected. The natural query is just the card name.") }
        (best.date ?: best.birthday)?.let { date ->
            return one(datePayload(date, transcript), "Date / birthday reveal", 0.88f, "A date was detected and converted into a natural search payload.")
        }
        best.zodiac?.let { return one("$it horoscope", "Astrology reveal", 0.86f, "A zodiac sign was detected and made into a natural astrology search.") }
        best.song?.let { return one(songPayload(it, best.artist), "Music reveal", 0.86f, "A song or artist was detected and sent as a natural music search.") }
        best.artist?.let { return one(it, "Music reveal", 0.82f, "An artist was detected and sent as a natural search.") }
        best.name?.let { return one(it, "Name reveal", 0.82f, "A name was detected and sent as a natural search.") }
        (best.place ?: best.country ?: best.city)?.let { return one(it, "Place reveal", 0.8f, "A place was detected and sent as a natural search.") }
        best.objectValue?.let { return one(it, "Object reveal", 0.78f, "An object was detected and sent as a natural search.") }
        best.number?.let { return one(it, "Number reveal", 0.78f, "A number was detected and sent naturally.") }
        best.color?.let { return one(it, "Color reveal", 0.76f, "A color was detected and sent naturally.") }
        best.phrase?.takeIf { it.isNotBlank() }?.let { return one(it, "Thought reveal", 0.72f, "No specific category won, so the strongest phrase was sent naturally.") }
        return one(transcript.trim().take(120), "General thought reveal", 0.55f, "No structured value was found, so WhisperCell kept the safest transcript summary.")
    }

    private data class MultiPayload(val payload: String, val parts: List<String>)

    private fun buildNaturalMultiPayload(best: BestMatchSummary, items: List<DetectedItem>): MultiPayload {
        val parts = listOfNotNull(
            best.name,
            best.place ?: best.country ?: best.city,
            best.date ?: best.birthday,
            best.card,
            best.serial,
            best.song?.let { songPayload(it, best.artist) },
            best.zodiac,
            best.objectValue,
            best.number,
            best.color
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (parts.size >= 2) return MultiPayload(parts.joinToString(" "), parts)

        val itemParts = items
            .filter { it.category != DetectedCategory.FullConfabulation }
            .sortedWith(compareByDescending<DetectedItem> { it.confidence }.thenBy { it.startIndex ?: Int.MAX_VALUE })
            .map { it.normalizedValue.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
        return if (itemParts.size >= 2) MultiPayload(itemParts.joinToString(" "), itemParts) else MultiPayload("", itemParts)
    }

    private fun songPayload(song: String, artist: String?): String = listOfNotNull(song, artist).joinToString(" ").trim()

    private fun datePayload(date: String, transcript: String): String {
        val cleanDate = date.replace(Regex(",?\\s+\\d{4}\\b"), "").trim()
        val lower = transcript.lowercase()
        return if (lower.contains("birthday") || lower.contains("birth day") || lower.contains("born")) {
            "celebrities born $cleanDate"
        } else {
            cleanDate
        }
    }

    private fun one(value: String, kind: String, confidence: Float, reason: String): SmartDecision = SmartDecision(
        payload = value.trim(),
        multiPayload = "",
        kind = kind,
        confidence = confidence,
        reason = reason
    )

    private fun buildBackupPayloads(
        items: List<DetectedItem>,
        best: BestMatchSummary,
        selectedPayload: String,
        multiPayload: String,
        primary: String,
        transcript: String
    ): List<String> {
        val fromBest = listOfNotNull(
            best.fullConfabulation,
            best.card,
            best.serial,
            (best.date ?: best.birthday)?.let { datePayload(it, transcript) },
            best.zodiac?.let { "$it horoscope" },
            best.song?.let { songPayload(it, best.artist) },
            best.artist,
            best.name,
            best.place,
            best.objectValue,
            best.number,
            best.phrase
        )
        val fromItems = items.sortedByDescending { it.confidence }.map { it.normalizedValue }
        return (listOf(selectedPayload, multiPayload) + fromBest + fromItems)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != primary }
            .distinct()
            .take(6)
    }

    private fun scoreConfidence(
        primary: String,
        selectedMatch: ChannelMatch?,
        strongestItem: DetectedItem?,
        warnings: List<String>,
        items: List<DetectedItem>,
        smartConfidence: Float
    ): Float {
        if (primary.isBlank()) return 0f
        var score = max(max(selectedMatch?.confidence ?: 0f, strongestItem?.confidence ?: 0f), smartConfidence)
        if (score == 0f && items.isNotEmpty()) score = items.maxOf { it.confidence }
        if (items.size == 1) score += 0.03f
        if (warnings.any { it.contains("Correction", ignoreCase = true) }) score -= 0.06f
        return score.coerceIn(0f, 1f)
    }

    private fun buildReasoning(
        primary: String,
        selectedMatch: ChannelMatch?,
        strongestItem: DetectedItem?,
        routineGuess: String,
        warnings: List<String>
    ): String = when {
        primary.isBlank() -> "The transcript did not produce a safe payload yet."
        selectedMatch != null -> "WhisperCell selected a natural $routineGuess payload for Inject."
        strongestItem != null -> "WhisperCell selected the strongest detected value: ${strongestItem.category.label}."
        warnings.isNotEmpty() -> "A payload was found, but warnings require review before publishing."
        else -> "A clear payload was found for $routineGuess."
    }

    private fun findRejectedValues(items: List<DetectedItem>, lowerTranscript: String): List<DetectedItem> = items.filter { item ->
        val value = item.normalizedValue.lowercase()
        val index = lowerTranscript.indexOf(value).takeIf { it >= 0 } ?: lowerTranscript.indexOf(item.value.lowercase())
        if (index < 0) return@filter false
        val windowStart = (index - 48).coerceAtLeast(0)
        val windowEnd = (index + value.length + 56).coerceAtMost(lowerTranscript.length)
        val window = lowerTranscript.substring(windowStart, windowEnd)
        rejectionPhrases.any { phrase -> window.contains(phrase) }
    }

    private fun removeRejected(items: List<DetectedItem>, rejected: List<DetectedItem>): List<DetectedItem> =
        if (rejected.isEmpty()) items else items.filterNot { item -> rejected.any { it.normalizedValue.equals(item.normalizedValue, ignoreCase = true) } }

    private fun payloadLooksRejected(payload: String, rejected: List<DetectedItem>): Boolean =
        rejected.any { payload.contains(it.normalizedValue, ignoreCase = true) }

    private fun hasCorrectionLanguage(lowerTranscript: String): Boolean = correctionPhrases.any { lowerTranscript.contains(it) }

    private fun positionScore(item: DetectedItem, lowerTranscript: String): Int {
        val normalizedIndex = lowerTranscript.lastIndexOf(item.normalizedValue.lowercase())
        val valueIndex = lowerTranscript.lastIndexOf(item.value.lowercase())
        return max(normalizedIndex, valueIndex)
    }

    private companion object {
        val rejectionPhrases = listOf(
            "not ",
            "no ",
            "nope",
            "instead",
            "rather",
            "changed my mind",
            "forget",
            "scratch that",
            "not that",
            "actually"
        )
        val correctionPhrases = listOf(
            "actually",
            "instead",
            "rather",
            "changed my mind",
            "scratch that",
            "no,",
            "not ",
            "forget that"
        )
    }
}
