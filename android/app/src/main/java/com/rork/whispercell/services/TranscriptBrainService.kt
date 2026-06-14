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
 * Extraction says what was heard. The brain decides what should be sent to Inject.
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
        val smartDecision = decideSmartPayload(extracted.bestMatches, correctedItems, transcript)
        val selectedPayload = selectedMatch?.payload.orEmpty().takeIf { it.isNotBlank() && !payloadLooksRejected(it, rejected) }
        val primary = smartDecision.payload.ifBlank { selectedPayload.orEmpty() }.ifBlank { strongestItem?.normalizedValue.orEmpty() }
        val routineGuess = smartDecision.kind

        if (items.size > correctedItems.size) warnings += "One or more values sounded rejected or corrected."
        if (hasCorrectionLanguage(lowerTranscript)) warnings += "Correction language detected. Prefer the final confirmed value."
        if (primary.isBlank()) warnings += "No usable performance payload found yet."

        val backups = buildBackupPayloads(correctedItems, extracted.bestMatches, selectedPayload.orEmpty(), smartDecision.multiPayload, primary)
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

    private fun decideSmartPayload(best: BestMatchSummary, items: List<DetectedItem>, transcript: String): SmartDecision {
        val multi = buildMultiPayload(best, items)
        if (multi.parts.size >= 2) {
            return SmartDecision(
                payload = multi.payload,
                multiPayload = multi.payload,
                kind = "Confabulation / Multi-value reveal",
                confidence = 0.9f,
                reason = "Multiple meaningful values were spoken, so WhisperCell bundled them into one Inject payload."
            )
        }

        best.serial?.let { return one("SERIAL", it, "Serial number reveal", 0.94f, "A serial-style value was detected and sent as the performance payload.") }
        best.card?.let { return one("CARD", it, "Card reveal", 0.92f, "A playing card was detected and sent as the card payload.") }
        (best.date ?: best.birthday)?.let { date ->
            return one("DATE", date, "Date / birthday reveal", 0.88f, "A date was detected. Send the date payload so the Inject reveal app can use the appropriate date/birthday routine.")
        }
        best.zodiac?.let { return one("ZODIAC", it, "Astrology reveal", 0.86f, "A zodiac sign was detected and sent as an astrology payload.") }
        best.song?.let { return one("SONG", listOfNotNull(it, best.artist?.let { artist -> "by $artist" }).joinToString(" "), "Music reveal", 0.86f, "A song or artist was detected and sent as a music payload.") }
        best.artist?.let { return one("ARTIST", it, "Music reveal", 0.82f, "An artist was detected and sent as a music payload.") }
        (best.name ?: best.fullConfabulationPerson())?.let { return one("NAME", it, "Name reveal", 0.82f, "A name was detected and sent as a name payload.") }
        (best.place ?: best.country ?: best.city)?.let { return one("PLACE", it, "Place reveal", 0.8f, "A place was detected and sent as a place payload.") }
        best.objectValue?.let { return one("OBJECT", it, "Object reveal", 0.78f, "An object was detected and sent as an object payload.") }
        best.number?.let { return one("NUMBER", it, "Number reveal", 0.78f, "A number was detected and sent as a number payload.") }
        best.color?.let { return one("COLOR", it, "Color reveal", 0.76f, "A color was detected and sent as a color payload.") }
        best.phrase?.takeIf { it.isNotBlank() }?.let { return one("PHRASE", it, "Thought reveal", 0.72f, "No specific category won, so the strongest phrase was sent.") }
        return one("TRANSCRIPT", transcript.trim().take(120), "General thought reveal", 0.55f, "No structured value was found, so WhisperCell kept the safest transcript summary.")
    }

    private data class MultiPayload(val payload: String, val parts: List<String>)

    private fun buildMultiPayload(best: BestMatchSummary, items: List<DetectedItem>): MultiPayload {
        val parts = listOfNotNull(
            best.name?.prefixed("NAME"),
            best.place?.prefixed("PLACE") ?: best.country?.prefixed("PLACE") ?: best.city?.prefixed("PLACE"),
            (best.date ?: best.birthday)?.prefixed("DATE"),
            best.card?.prefixed("CARD"),
            best.serial?.prefixed("SERIAL"),
            best.song?.let { song -> "SONG:$song${best.artist?.let { " by $it" }.orEmpty()}" },
            best.zodiac?.prefixed("ZODIAC"),
            best.objectValue?.prefixed("OBJECT"),
            best.number?.prefixed("NUMBER"),
            best.color?.prefixed("COLOR")
        ).distinct()
        if (parts.size >= 2) return MultiPayload(parts.joinToString(" | "), parts)

        val itemParts = items
            .filter { it.category != DetectedCategory.FullConfabulation }
            .sortedWith(compareByDescending<DetectedItem> { it.confidence }.thenBy { it.startIndex ?: Int.MAX_VALUE })
            .map { item -> "${labelFor(item.category)}:${item.normalizedValue}" }
            .distinct()
            .take(4)
        return if (itemParts.size >= 2) MultiPayload(itemParts.joinToString(" | "), itemParts) else MultiPayload("", itemParts)
    }

    private fun one(prefix: String, value: String, kind: String, confidence: Float, reason: String): SmartDecision = SmartDecision(
        payload = value.prefixed(prefix),
        multiPayload = "",
        kind = kind,
        confidence = confidence,
        reason = reason
    )

    private fun String.prefixed(prefix: String): String = "$prefix:${trim()}"

    private fun BestMatchSummary.fullConfabulationPerson(): String? = fullConfabulation
        ?.split("|")
        ?.map { it.trim() }
        ?.firstOrNull { it.split(" ").size in 2..3 }

    private fun labelFor(category: DetectedCategory): String = when (category) {
        DetectedCategory.PlayingCard -> "CARD"
        DetectedCategory.SerialNumber -> "SERIAL"
        DetectedCategory.Date, DetectedCategory.Birthday -> "DATE"
        DetectedCategory.Zodiac -> "ZODIAC"
        DetectedCategory.Song -> "SONG"
        DetectedCategory.Artist -> "ARTIST"
        DetectedCategory.Name, DetectedCategory.Celebrity -> "NAME"
        DetectedCategory.Place, DetectedCategory.Country, DetectedCategory.City -> "PLACE"
        DetectedCategory.Object -> "OBJECT"
        DetectedCategory.Number -> "NUMBER"
        DetectedCategory.Color -> "COLOR"
        else -> category.label.uppercase().replace(" ", "_")
    }

    private fun buildBackupPayloads(
        items: List<DetectedItem>,
        best: BestMatchSummary,
        selectedPayload: String,
        multiPayload: String,
        primary: String
    ): List<String> {
        val fromBest = listOfNotNull(
            best.fullConfabulation,
            best.card?.prefixed("CARD"),
            best.serial?.prefixed("SERIAL"),
            (best.date ?: best.birthday)?.prefixed("DATE"),
            best.zodiac?.prefixed("ZODIAC"),
            best.song?.prefixed("SONG"),
            best.name?.prefixed("NAME"),
            best.place?.prefixed("PLACE"),
            best.objectValue?.prefixed("OBJECT"),
            best.number?.prefixed("NUMBER"),
            best.phrase
        )
        val fromItems = items.sortedByDescending { it.confidence }.map { "${labelFor(it.category)}:${it.normalizedValue}" }
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
        selectedMatch != null -> "WhisperCell selected a $routineGuess payload for Inject."
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
