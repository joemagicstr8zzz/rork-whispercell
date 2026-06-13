package com.rork.whispercell.models

/** A single structured value extracted from a transcript. */
data class DetectedItem(
    val id: String,
    val category: DetectedCategory,
    val value: String,
    val normalizedValue: String,
    val confidence: Float,
    val sourcePhrase: String,
    val startIndex: Int? = null,
    val endIndex: Int? = null,
    val shouldPublish: Boolean = true
)
