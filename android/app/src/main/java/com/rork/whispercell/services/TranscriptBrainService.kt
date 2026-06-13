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
 * A lightweight intelligence layer that interprets the transcript after capture.
 * Extraction says what was heard. The brain decides what probably matters.
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
        val routineGuess = guessRoutine(activeProfile, activeChannels, extracted.bestMatches)
        val selectedPayload = selectedMatch?.payload.orEmpty()
        val confabPayload = buildConfabPayload(extracted.bestMatches)
        val strongestItem = correctedItems.maxWithOrNull(compareBy<DetectedItem> { it.confidence }.thenBy { positionScore(it, lowerTranscript) })

        if (items.size > correctedItems.size) warnings += "One or more values sounded rejected or corrected."
        if (hasCorrectionLanguage(lowerTranscript)) warnings += "Correction language detected. Prefer the final confirmed value."
        if (correctedItems.map { it.category }.distinct().size > 3) warnings += "Multiple categories detected. Confirm payload before publishing."
        if (selectedMatch == null) warnings += "No active output matched yet."

        val primary = when {
            activeProfile.name.contains("confab", ignoreCase = true) && confabPayload.isNotBlank() -> confabPayload
            selectedPayload.isNotBlank() && !payloadLooksRejected(selectedPayload, rejected) -> selectedPayload
            strongestItem != null -> strongestItem.normalizedValue
            confabPayload.isNotBlank() -> confabPayload
            else -> ""
        }

        val backups = buildBackupPayloads(correctedItems, extracted.bestMatches, selectedPayload, confabPayload, primary)
        val confidence = scoreConfidence(primary, selectedMatch, strongestItem, warnings, correctedItems)
        val shouldPublish = primary.isNotBlank() && confidence >= 0.72f && warnings.none { it.contains("Confirm", ignoreCase = true) }
        val reasoning = buildReasoning(primary, selectedMatch, strongestItem, routineGuess, warnings)

        return TranscriptBrainResult(
            primaryPayload = primary,
            backupPayloads = backups,
            routineGuess = routineGuess,
            confidence = confidence,
            reasoning = reasoning,
            warnings = warnings.distinct(),
            rejectedValues = rejected.map { it.normalizedValue }.distinct(),
            shouldPublish = shouldPublish,
            emergencyRevealText = emergencyReveal(primary, routineGuess)
        )
    }

    private fun guessRoutine(profile: PerformanceProfile, channels: List<Channel>, best: BestMatchSummary): String {
        val profileName = profile.name.takeIf { it.isNotBlank() }
        if (profileName != null && !profileName.equals("Custom Profile", ignoreCase = true)) return profileName
        val channelNames = channels.map { it.name.lowercase() }
        return when {
            channelNames.any { it.contains("confab") } || listOf(best.place, best.name, best.date, best.objectValue, best.song).count { !it.isNullOrBlank() } >= 3 -> "Confabulation"
            best.card != null -> "Card Reveal"
            best.song != null || best.artist != null || best.lyric != null -> "Music Reveal"
            best.date != null || best.birthday != null -> "Date Reveal"
            best.serial != null -> "Serial Number Reveal"
            best.zodiac != null -> "Zodiac Reveal"
            best.name != null -> "Name Reveal"
            best.objectValue != null -> "Object Reveal"
            else -> "Word Reveal"
        }
    }

    private fun buildConfabPayload(best: BestMatchSummary): String = listOfNotNull(
        best.place ?: best.country ?: best.city,
        best.name,
        best.date ?: best.birthday,
        best.objectValue,
        best.song ?: best.artist,
        best.number ?: best.serial,
        best.color,
        best.zodiac
    ).filter { it.isNotBlank() }.distinct().joinToString(" | ")

    private fun buildBackupPayloads(
        items: List<DetectedItem>,
        best: BestMatchSummary,
        selectedPayload: String,
        confabPayload: String,
        primary: String
    ): List<String> {
        val fromBest = listOfNotNull(
            best.word,
            best.phrase,
            best.place,
            best.name,
            best.date,
            best.card,
            best.song,
            best.artist,
            best.serial,
            best.objectValue,
            best.zodiac,
            best.fullConfabulation
        )
        val fromItems = items.sortedByDescending { it.confidence }.map { it.normalizedValue }
        return (listOf(selectedPayload, confabPayload) + fromBest + fromItems)
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
        items: List<DetectedItem>
    ): Float {
        if (primary.isBlank()) return 0f
        var score = max(selectedMatch?.confidence ?: 0f, strongestItem?.confidence ?: 0f)
        if (score == 0f && items.isNotEmpty()) score = items.maxOf { it.confidence }
        if (selectedMatch != null) score += 0.08f
        if (items.size == 1) score += 0.05f
        if (warnings.any { it.contains("Correction", ignoreCase = true) }) score -= 0.08f
        if (warnings.any { it.contains("Multiple", ignoreCase = true) }) score -= 0.06f
        if (warnings.any { it.contains("No active output", ignoreCase = true) }) score -= 0.12f
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
        selectedMatch != null -> "Best match for $routineGuess came from ${selectedMatch.channel.name}: $primary."
        strongestItem != null -> "No channel match was selected, so the strongest detected value was used: ${strongestItem.category.label}."
        warnings.isNotEmpty() -> "A payload was found, but warnings require review before publishing."
        else -> "A clear payload was found for $routineGuess."
    }

    private fun emergencyReveal(primary: String, routineGuess: String): String = when {
        primary.isBlank() -> "Keep one clear image in mind."
        routineGuess.contains("date", ignoreCase = true) -> "$primary feels like a date worth remembering."
        routineGuess.contains("card", ignoreCase = true) -> "Some symbols wait until the right moment to be seen: $primary."
        routineGuess.contains("music", ignoreCase = true) -> "Some songs begin before anyone says the title: $primary."
        routineGuess.contains("confab", ignoreCase = true) -> "The thought has assembled itself: $primary."
        else -> "Today’s focus is $primary."
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
