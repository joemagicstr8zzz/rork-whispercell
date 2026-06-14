package com.rork.whispercell.services

import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.ChannelMatch
import com.rork.whispercell.models.ExtractedPerformanceData
import com.rork.whispercell.models.PerformanceProfile
import com.rork.whispercell.models.RoutingBehavior

class ChannelMatcher(
    private val formatter: PayloadFormatter = PayloadFormatter()
) {
    fun match(profile: PerformanceProfile, channels: List<Channel>, data: ExtractedPerformanceData): List<ChannelMatch> {
        val activeChannels: List<Channel> = channels.filter { channel -> channel.enabled && profile.activeChannelIds.contains(channel.id) }
        val strongestItem = data.detectedItems.filter { it.shouldPublish }.maxByOrNull { it.confidence }
        val matches: List<ChannelMatch> = activeChannels.mapNotNull { channel ->
            val item = channel.priority.flatMap { category -> data.detectedItems.filter { it.category == category } }
                .filter { it.confidence >= channel.confidenceThreshold && it.shouldPublish }
                .maxByOrNull { it.confidence }
                ?: data.detectedItems.filter { it.category in channel.inputCategories && it.confidence >= channel.confidenceThreshold && it.shouldPublish }.maxByOrNull { it.confidence }
                ?: strongestItem
            val formatted = formatter.format(channel, item, data)
            val output = formatted.ifBlank { item?.normalizedValue.orEmpty() }.ifBlank { data.bestMatches.fullConfabulation.orEmpty() }
            if (output.isBlank()) null else ChannelMatch(
                channel = channel,
                item = item,
                payload = output,
                confidence = item?.confidence ?: data.confidence,
                reason = if (item != null) "Matched ${item.category.label}" else "Template output"
            )
        }
        return when (profile.routingBehavior) {
            RoutingBehavior.FirstValidMatch -> matches.take(1)
            RoutingBehavior.BestConfidenceMatch -> matches.sortedByDescending { it.confidence }.take(1)
            RoutingBehavior.AllActiveChannelOutputs -> matches
        }
    }
}
