package com.rork.whispercell.services

import com.rork.whispercell.models.BestMatchSummary
import com.rork.whispercell.models.ConfabulationPayload
import com.rork.whispercell.models.DetectedCategory
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.ExtractedPerformanceData
import java.util.Locale
import kotlin.math.max

/** Deterministic fallback extractor used when AI is unavailable or low confidence. */
class RuleExtractionService {
    private val colors: List<String> = listOf("Red", "Blue", "Green", "Yellow", "Purple", "Black", "White", "Orange", "Pink", "Gold", "Silver")
    private val zodiacs: List<String> = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    private val places: List<String> = listOf("Spain", "Tokyo", "New York", "Paris", "Barcelona", "London", "Italy", "France", "Germany", "Mexico")
    private val celebrities: List<String> = listOf("Tom Cruise", "Taylor Swift", "Beyonce", "Queen", "Elvis Presley", "Adele")

    fun extract(rawTranscript: String, startPhrases: List<String>, stopPhrases: List<String>): ExtractedPerformanceData {
        val cleanedTranscript: String = removePhrases(rawTranscript, startPhrases + stopPhrases)
        val detectedItems: MutableList<DetectedItem> = mutableListOf()

        detectCards(cleanedTranscript, detectedItems)
        detectDates(cleanedTranscript, detectedItems)
        detectSerials(cleanedTranscript, detectedItems)
        detectMusic(cleanedTranscript, detectedItems)
        detectKnownWords(cleanedTranscript, places, DetectedCategory.Place, detectedItems)
        detectKnownWords(cleanedTranscript, colors, DetectedCategory.Color, detectedItems)
        detectKnownWords(cleanedTranscript, zodiacs, DetectedCategory.Zodiac, detectedItems)
        detectKnownWords(cleanedTranscript, celebrities, DetectedCategory.Celebrity, detectedItems)
        detectNames(cleanedTranscript, detectedItems)
        detectNumbers(cleanedTranscript, detectedItems)
        detectObject(cleanedTranscript, detectedItems)

        val best: BestMatchSummary = buildBestSummary(detectedItems, cleanedTranscript)
        val confabulation: ConfabulationPayload? = buildConfabulation(best)
        val withConfabulation: List<DetectedItem> = if (confabulation != null) {
            detectedItems + DetectedItem(
                id = "full_confabulation",
                category = DetectedCategory.FullConfabulation,
                value = confabulation.summary,
                normalizedValue = confabulation.summary,
                confidence = 0.86f,
                sourcePhrase = cleanedTranscript,
                shouldPublish = true
            )
        } else {
            detectedItems
        }
        val finalBest: BestMatchSummary = if (confabulation != null) best.copy(fullConfabulation = confabulation.summary) else best
        val confidence: Float = withConfabulation.maxOfOrNull { it.confidence } ?: 0f
        return ExtractedPerformanceData(
            rawTranscript = rawTranscript,
            cleanedTranscript = cleanedTranscript,
            detectedItems = withConfabulation.distinctBy { it.category to it.normalizedValue },
            bestMatches = finalBest,
            confabulation = confabulation,
            confidence = confidence,
            notes = listOf("Rule fallback active. AI extraction service boundary is ready for OpenAI or ElevenLabs.")
        )
    }

    private fun removePhrases(text: String, phrases: List<String>): String {
        var cleaned: String = text
        phrases.filter { it.isNotBlank() }.forEach { phrase ->
            cleaned = cleaned.replace(Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE), "")
        }
        return cleaned.replace(Regex("\\s+"), " ").trim().trim(',', '.', ';', ':')
    }

    private fun detectCards(text: String, items: MutableList<DetectedItem>) {
        val rankWords: String = "Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Jack|Queen|King|A|2|3|4|5|6|7|8|9|10|J|Q|K"
        val suitWords: String = "Spades|Hearts|Diamonds|Clubs|S|H|D|C"
        Regex("\\b($rankWords)(?:\\s+of\\s+|\\s*)($suitWords)\\b", RegexOption.IGNORE_CASE).findAll(text).forEachIndexed { index, match ->
            val normalized: String = normalizeCard(match.value)
            items.add(item("card_$index", DetectedCategory.PlayingCard, match.value, normalized, 0.92f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun detectDates(text: String, items: MutableList<DetectedItem>) {
        val month: String = "January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec"
        Regex("\\b($month)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE).findAll(text).forEachIndexed { index, match ->
            val category: DetectedCategory = if (text.lowercase().contains("birthday")) DetectedCategory.Birthday else DetectedCategory.Date
            items.add(item("date_$index", category, match.value, normalizeTitle(match.value), 0.9f, match.value, match.range.first, match.range.last + 1))
        }
        Regex("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b").findAll(text).forEachIndexed { index, match ->
            items.add(item("date_numeric_$index", DetectedCategory.Date, match.value, match.value, 0.78f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun detectSerials(text: String, items: MutableList<DetectedItem>) {
        Regex("\\b[A-Z][0-9A-Z]{6,10}[A-Z]\\b").findAll(text.uppercase()).forEachIndexed { index, match ->
            items.add(item("serial_$index", DetectedCategory.SerialNumber, match.value, match.value, 0.94f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun detectMusic(text: String, items: MutableList<DetectedItem>) {
        Regex("(?:my song is|the song is|favorite song is|song is)\\s+(.+?)(?:\\s+by\\s+([A-Za-z0-9 '&.-]+))?(?:[.!?]|$)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val song: String = match.groupValues.getOrNull(1)?.trim()?.trimEnd(',') ?: ""
            val artist: String = match.groupValues.getOrNull(2)?.trim().orEmpty()
            if (song.isNotBlank()) items.add(item("song", DetectedCategory.Song, song, normalizeTitle(song), 0.91f, match.value, match.range.first, match.range.last + 1))
            if (artist.isNotBlank()) items.add(item("artist", DetectedCategory.Artist, artist, normalizeTitle(artist), 0.89f, match.value, match.range.first, match.range.last + 1))
        }
        Regex("(?:the artist is|artist is|by)\\s+([A-Za-z0-9 '&.-]+)(?:[.!?]|$)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val artist: String = match.groupValues[1].trim()
            if (artist.isNotBlank()) items.add(item("artist_direct", DetectedCategory.Artist, artist, normalizeTitle(artist), 0.84f, match.value, match.range.first, match.range.last + 1))
        }
        Regex("(?:the lyric is|the line goes)\\s+(.+?)(?:[.!?]|$)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val lyric: String = match.groupValues[1].trim()
            items.add(item("lyric", DetectedCategory.Lyric, lyric, lyric, 0.84f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun detectKnownWords(text: String, values: List<String>, category: DetectedCategory, items: MutableList<DetectedItem>) {
        values.forEach { value ->
            val match: MatchResult? = Regex("\\b${Regex.escape(value)}\\b", RegexOption.IGNORE_CASE).find(text)
            if (match != null) {
                items.add(item("${category.name}_${value}", category, match.value, value, 0.86f, match.value, match.range.first, match.range.last + 1))
            }
        }
    }

    private fun detectNames(text: String, items: MutableList<DetectedItem>) {
        Regex("(?:meet|named|name is|thinking of)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})").find(text)?.let { match ->
            val name: String = match.groupValues[1].trim()
            val category: DetectedCategory = if (celebrities.any { it.equals(name, ignoreCase = true) }) DetectedCategory.Celebrity else DetectedCategory.Name
            items.add(item("name", category, name, normalizeTitle(name), 0.82f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun detectNumbers(text: String, items: MutableList<DetectedItem>) {
        Regex("(?:number is|thinking of number|the number)\\s+([0-9][0-9, .-]*)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val number: String = match.groupValues[1].trim()
            items.add(item("number", DetectedCategory.Number, number, number.filter { it.isDigit() }, 0.78f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun detectObject(text: String, items: MutableList<DetectedItem>) {
        Regex("(?:object is|holding|imagine a|think of an?|the object)\\s+([A-Za-z][A-Za-z ]{1,24})(?:[.!?]|$)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val objectValue: String = match.groupValues[1].trim()
            items.add(item("object", DetectedCategory.Object, objectValue, objectValue.lowercase(), 0.7f, match.value, match.range.first, match.range.last + 1))
        }
    }

    private fun buildBestSummary(items: List<DetectedItem>, transcript: String): BestMatchSummary {
        fun best(category: DetectedCategory): String? = items.filter { it.category == category }.maxByOrNull { it.confidence }?.normalizedValue
        val place: String? = best(DetectedCategory.Place) ?: best(DetectedCategory.Country) ?: best(DetectedCategory.City)
        val person: String? = best(DetectedCategory.Celebrity) ?: best(DetectedCategory.Name)
        val date: String? = best(DetectedCategory.Date) ?: best(DetectedCategory.Birthday)
        return BestMatchSummary(
            word = best(DetectedCategory.Word),
            phrase = if (items.isEmpty() && transcript.isNotBlank()) transcript else best(DetectedCategory.Phrase),
            name = best(DetectedCategory.Name),
            date = best(DetectedCategory.Date),
            birthday = best(DetectedCategory.Birthday),
            place = place,
            country = best(DetectedCategory.Country),
            city = best(DetectedCategory.City),
            card = best(DetectedCategory.PlayingCard),
            number = best(DetectedCategory.Number),
            serial = best(DetectedCategory.SerialNumber),
            song = best(DetectedCategory.Song),
            artist = best(DetectedCategory.Artist),
            lyric = best(DetectedCategory.Lyric),
            color = best(DetectedCategory.Color),
            objectValue = best(DetectedCategory.Object),
            zodiac = best(DetectedCategory.Zodiac),
            time = best(DetectedCategory.Time),
            fullConfabulation = listOfNotNull(place, person, date).takeIf { it.size >= 2 }?.joinToString(" | ")
        )
    }

    private fun buildConfabulation(best: BestMatchSummary): ConfabulationPayload? {
        val parts: List<String> = listOfNotNull(best.place, best.name, best.date ?: best.birthday, best.objectValue, best.song)
        if (parts.size < 2) return null
        return ConfabulationPayload(
            place = best.place,
            person = best.name,
            date = best.date ?: best.birthday,
            objectValue = best.objectValue,
            song = best.song,
            artist = best.artist,
            number = best.number,
            color = best.color,
            summary = parts.joinToString(" | ")
        )
    }

    private fun normalizeCard(value: String): String {
        val tokens: List<String> = value.replace("of", " ", ignoreCase = true).replace(Regex("\\s+"), " ").trim().split(" ")
        if (tokens.size < 2) return value.uppercase()
        val rank: String = when (tokens[0].lowercase()) {
            "ace", "a" -> "Ace"
            "king", "k" -> "King"
            "queen", "q" -> "Queen"
            "jack", "j" -> "Jack"
            "ten", "10" -> "10"
            "two", "2" -> "2"
            "three", "3" -> "3"
            "four", "4" -> "4"
            "five", "5" -> "5"
            "six", "6" -> "6"
            "seven", "7" -> "7"
            "eight", "8" -> "8"
            "nine", "9" -> "9"
            else -> tokens[0]
        }
        val suit: String = when (tokens[1].lowercase()) {
            "s", "spades" -> "Spades"
            "h", "hearts" -> "Hearts"
            "d", "diamonds" -> "Diamonds"
            "c", "clubs" -> "Clubs"
            else -> tokens[1]
        }
        return "$rank of $suit"
    }

    private fun normalizeTitle(value: String): String = value.split(" ").joinToString(" ") { word ->
        word.lowercase(Locale.getDefault()).replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
    }.trim().trim(',', '.', ';', ':')

    private fun item(id: String, category: DetectedCategory, value: String, normalized: String, confidence: Float, sourcePhrase: String, start: Int? = null, end: Int? = null): DetectedItem =
        DetectedItem(id = id, category = category, value = value, normalizedValue = normalized, confidence = max(0f, confidence), sourcePhrase = sourcePhrase, startIndex = start, endIndex = end, shouldPublish = true)
}
