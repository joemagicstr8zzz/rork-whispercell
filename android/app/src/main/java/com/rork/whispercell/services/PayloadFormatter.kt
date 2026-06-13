package com.rork.whispercell.services

import com.rork.whispercell.models.BestMatchSummary
import com.rork.whispercell.models.Channel
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.ExtractedPerformanceData

class PayloadFormatter {
    fun format(channel: Channel, item: DetectedItem?, data: ExtractedPerformanceData): String {
        val best: BestMatchSummary = data.bestMatches
        val template: String = channel.payloadFormat.ifBlank { "{value}" }
        val fallbackValue: String = item?.normalizedValue ?: channel.fallbackValue.orEmpty()
        return template
            .replace("{value}", fallbackValue)
            .replace("{card}", best.card.orEmpty())
            .replace("{date}", best.date ?: best.birthday.orEmpty())
            .replace("{birthday}", best.birthday.orEmpty())
            .replace("{song}", best.song.orEmpty())
            .replace("{artist}", best.artist.orEmpty())
            .replace("{lyric}", best.lyric.orEmpty())
            .replace("{serial}", best.serial.orEmpty())
            .replace("{zodiac}", best.zodiac.orEmpty())
            .replace("{name}", best.name.orEmpty())
            .replace("{person}", best.name.orEmpty())
            .replace("{place}", best.place.orEmpty())
            .replace("{object}", best.objectValue.orEmpty())
            .replace("{number}", best.number.orEmpty())
            .replace("{color}", best.color.orEmpty())
            .replace("{phrase}", best.phrase.orEmpty())
            .replace("{full_confabulation}", best.fullConfabulation.orEmpty())
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }
}
