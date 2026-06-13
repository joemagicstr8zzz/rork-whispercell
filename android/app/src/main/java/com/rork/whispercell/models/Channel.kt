package com.rork.whispercell.models

/** Automation route that formats a detected value and publishes it to Inject. */
data class Channel(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val inputCategories: List<DetectedCategory>,
    val priority: List<DetectedCategory>,
    val payloadFormat: String,
    val autoPublish: Boolean,
    val confidenceThreshold: Float,
    val cooldownSeconds: Int,
    val sendOnce: Boolean,
    val allowRepeats: Boolean,
    val fallbackValue: String? = null
)

data class ChannelMatch(
    val channel: Channel,
    val item: DetectedItem?,
    val payload: String,
    val confidence: Float,
    val reason: String
)
