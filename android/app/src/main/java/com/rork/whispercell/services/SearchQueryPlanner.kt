package com.rork.whispercell.services

import com.rork.whispercell.models.BestMatchSummary
import com.rork.whispercell.models.DetectedCategory
import com.rork.whispercell.models.DetectedItem

/** Builds the human-looking Google-style query that gets sent to Inject. */
class SearchQueryPlanner {
    fun plan(best: BestMatchSummary, items: List<DetectedItem>, transcript: String): PlannedSearchQuery {
        val context = transcript.lowercase()
        val person = bestValue(items, transcript, DetectedCategory.Celebrity, DetectedCategory.Name) ?: best.name
        val place = best.place ?: best.country ?: best.city ?: bestValue(items, transcript, DetectedCategory.Place, DetectedCategory.Country, DetectedCategory.City)
        val date = best.date ?: best.birthday ?: bestValue(items, transcript, DetectedCategory.Date, DetectedCategory.Birthday)
        val birthdayDate = best.birthday ?: bestValue(items, transcript, DetectedCategory.Birthday)
        val card = best.card ?: bestValue(items, transcript, DetectedCategory.PlayingCard)
        val serial = best.serial ?: bestValue(items, transcript, DetectedCategory.SerialNumber)
        val song = best.song ?: bestValue(items, transcript, DetectedCategory.Song)
        val artist = best.artist ?: bestValue(items, transcript, DetectedCategory.Artist)
        val zodiac = best.zodiac ?: bestValue(items, transcript, DetectedCategory.Zodiac)
        val objectValue = best.objectValue ?: bestValue(items, transcript, DetectedCategory.Object)
        val number = best.number ?: bestValue(items, transcript, DetectedCategory.Number)
        val color = best.color ?: bestValue(items, transcript, DetectedCategory.Color)
        val phrase = best.phrase?.takeIf { it.isNotBlank() }

        val normalizedDate = date?.let { simplifyDate(it) }
        val normalizedBirthday = birthdayDate?.let { simplifyDate(it) }
        val isBirthdayContext = context.contains("birthday") || context.contains("birth day") || context.contains("born")

        return when {
            person != null && place != null && normalizedDate != null -> PlannedSearchQuery(
                query = "was $person in $place on $normalizedDate",
                intent = "person-place-date search",
                confidence = 0.94f,
                reason = "A person, place, and date were mentioned, so WhisperCell built a natural Google-style question.",
                alternatives = listOf("$person $place $normalizedDate", "has $person ever visited $place")
            )
            person != null && place != null -> PlannedSearchQuery(
                query = "has $person ever visited $place",
                intent = "person-place search",
                confidence = 0.92f,
                reason = "A person and place were mentioned, so WhisperCell built a question someone could actually search.",
                alternatives = listOf("$person $place", "$person visited $place")
            )
            person != null && normalizedDate != null -> PlannedSearchQuery(
                query = if (isBirthdayContext) "$person birthday" else "$person $normalizedDate",
                intent = "person-date search",
                confidence = 0.9f,
                reason = "A person and date were mentioned, so WhisperCell built the most natural search phrase.",
                alternatives = listOfNotNull("$person $normalizedDate", normalizedBirthday?.let { "celebrities born $it" })
            )
            place != null && normalizedDate != null -> PlannedSearchQuery(
                query = "what happened in $place on $normalizedDate",
                intent = "place-date search",
                confidence = 0.88f,
                reason = "A place and date were mentioned, so WhisperCell built a natural event-style search.",
                alternatives = listOf("$place $normalizedDate", "$normalizedDate $place events")
            )
            song != null && artist != null -> exact("$song $artist", "music search", 0.88f, "A song and artist were mentioned, so the natural query is just both together.")
            song != null -> exact(song, "song search", 0.84f, "A song was mentioned, so WhisperCell used the title as the query.")
            artist != null -> exact(artist, "artist search", 0.82f, "An artist was mentioned, so WhisperCell used the artist name as the query.")
            serial != null -> exact(serial, "serial search", 0.94f, "A serial number was mentioned, so exact text is the best query.")
            card != null -> exact(card, "card search", 0.9f, "A playing card was mentioned, so the natural query is just the card name.")
            normalizedDate != null && isBirthdayContext -> exact("celebrities born $normalizedDate", "birthday search", 0.88f, "The transcript sounded birthday-related, so WhisperCell built a celebrity birthday search.")
            normalizedDate != null -> exact(normalizedDate, "date search", 0.82f, "A date was mentioned, so WhisperCell used the date naturally.")
            zodiac != null -> exact("$zodiac horoscope", "astrology search", 0.84f, "A zodiac sign was mentioned, so WhisperCell made a natural astrology query.")
            person != null -> exact(person, "name search", 0.82f, "A name was mentioned, so WhisperCell used the name naturally.")
            place != null -> exact(place, "place search", 0.8f, "A place was mentioned, so WhisperCell used the place naturally.")
            objectValue != null && place != null -> exact("$objectValue $place", "object-place search", 0.78f, "An object and place were mentioned, so WhisperCell combined them naturally.")
            objectValue != null -> exact(objectValue, "object search", 0.74f, "An object was mentioned, so WhisperCell used it naturally.")
            number != null -> exact(number, "number search", 0.72f, "A number was mentioned, so WhisperCell used it exactly.")
            color != null -> exact(color, "color search", 0.7f, "A color was mentioned, so WhisperCell used it naturally.")
            phrase != null -> exact(phrase, "phrase search", 0.68f, "A phrase was mentioned, so WhisperCell used it as the query.")
            else -> fallbackFromTranscript(transcript)
        }
    }

    private fun exact(query: String, intent: String, confidence: Float, reason: String): PlannedSearchQuery = PlannedSearchQuery(
        query = query.trim(),
        intent = intent,
        confidence = confidence,
        reason = reason,
        alternatives = emptyList()
    )

    private fun fallbackFromTranscript(transcript: String): PlannedSearchQuery {
        val clean = transcript.replace(Regex("\\s+"), " ").trim().take(120)
        return PlannedSearchQuery(
            query = clean,
            intent = "general search",
            confidence = if (clean.isBlank()) 0f else 0.55f,
            reason = "No structured target was found, so WhisperCell used the safest transcript search phrase.",
            alternatives = emptyList()
        )
    }

    private fun bestValue(items: List<DetectedItem>, transcript: String, vararg categories: DetectedCategory): String? {
        val transcriptLength = transcript.length.coerceAtLeast(1)
        val categorySet = categories.toSet()
        return items
            .filter { it.category in categorySet && it.normalizedValue.isNotBlank() && it.shouldPublish }
            .maxByOrNull { item ->
                val positionBoost = if ((item.startIndex ?: 0) >= transcriptLength * 0.75) 0.15f else 0f
                item.confidence + positionBoost
            }
            ?.normalizedValue
    }

    private fun simplifyDate(date: String): String = date
        .replace(Regex(",?\\s+\\d{4}\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

data class PlannedSearchQuery(
    val query: String,
    val intent: String,
    val confidence: Float,
    val reason: String,
    val alternatives: List<String>
)
