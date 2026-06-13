package com.rork.whispercell.models

/** Complete structured output from the extraction pipeline. */
data class ExtractedPerformanceData(
    val rawTranscript: String,
    val cleanedTranscript: String,
    val detectedItems: List<DetectedItem>,
    val bestMatches: BestMatchSummary,
    val confabulation: ConfabulationPayload? = null,
    val confidence: Float,
    val notes: List<String> = emptyList()
)
