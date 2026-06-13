package com.rork.whispercell.models

/** Saved setup for a routine. */
data class PerformanceProfile(
    val id: String,
    val name: String,
    val startPhrase: String,
    val stopPhrase: String,
    val speechProviderId: String,
    val activeCategories: List<DetectedCategory>,
    val activeChannelIds: List<String>,
    val injectCode: String,
    val reviewModeEnabled: Boolean,
    val fullAutomationEnabled: Boolean,
    val startMode: StartMode,
    val stopMode: StopMode,
    val routingBehavior: RoutingBehavior,
    val confidenceThreshold: Float,
    val cooldownSeconds: Int,
    val loggingEnabled: Boolean
)
