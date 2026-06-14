package com.rork.whispercell.services

import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.ChannelMatch
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.ExtractedPerformanceData
import com.rork.whispercell.models.PerformanceProfile
import com.rork.whispercell.models.TranscriptBrainResult
import kotlin.math.max

/**
 * Intelligent routing layer for WhisperCell.
 * Extraction says what was heard. SearchQueryPlanner decides what a person would actually search.
 */
class TranscriptBrainService(
    private val queryPlanner: SearchQueryPlanner = SearchQueryPlanner()
) {
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
        val plannedQuery = queryPlanner.plan(
            best = extracted.bestMatches,
            items = correctedItems,
            transcript = transcript,
            aiSuggestedQuery = extracted.suggestedSearchQuery
        )
        val selectedPayload = selectedMatch?.payload.orEmpty().takeIf { it.isNotBlank() && !payloadLooksRejected(it, rejected) }
        val primary = plannedQuery.query.ifBlank { selectedPayload.orEmpty() }.ifBlank { strongestItem?.normalizedValue.orEmpty() }

        if (items.size > correctedItems.size) warnings += "One or more values sounded rejected or corrected."
        if (hasCorrectionLanguage(lowerTranscript)) warnings += "Correction language detected. Prefer the final confirmed value."
        if (primary.isBlank()) warnings += "No usable performance search found yet."

        val backups = buildBackupPayloads(correctedItems, plannedQuery, selectedPayload.orEmpty(), primary)
        val confidence = scoreConfidence(primary, selectedMatch, strongestItem, warnings, correctedItems, plannedQuery.confidence)
        val shouldPublish = primary.isNotBlank() && confidence >= 0.68f

        return TranscriptBrainResult(
            primaryPayload = primary,
            backupPayloads = backups,
            routineGuess = plannedQuery.intent,
            confidence = confidence,
            reasoning = plannedQuery.reason,
            warnings = warnings.distinct(),
            rejectedValues = rejected.map { it.normalizedValue }.distinct(),
            shouldPublish = shouldPublish,
            emergencyRevealText = primary
        )
    }

    private fun buildBackupPayloads(
        items: List<DetectedItem>,
        plannedQuery: PlannedSearchQuery,
        selectedPayload: String,
        primary: String
    ): List<String> {
        val fromItems = items.sortedByDescending { it.confidence }.map { it.normalizedValue }
        return (listOf(selectedPayload) + plannedQuery.alternatives + fromItems)
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
        plannedConfidence: Float
    ): Float {
        if (primary.isBlank()) return 0f
        var score = max(max(selectedMatch?.confidence ?: 0f, strongestItem?.confidence ?: 0f), plannedConfidence)
        if (score == 0f && items.isNotEmpty()) score = items.maxOf { it.confidence }
        if (items.size == 1) score += 0.03f
        if (warnings.any { it.contains("Correction", ignoreCase = true) }) score -= 0.06f
        return score.coerceIn(0f, 1f)
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
