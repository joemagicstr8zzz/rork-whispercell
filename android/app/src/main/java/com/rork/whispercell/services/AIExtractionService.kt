package com.rork.whispercell.services

import com.rork.whispercell.models.BestMatchSummary
import com.rork.whispercell.models.ConfabulationPayload
import com.rork.whispercell.models.DetectedCategory
import com.rork.whispercell.models.DetectedItem
import com.rork.whispercell.models.ExtractedPerformanceData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** AI-first extraction boundary. Uses OpenAI when configured, otherwise deterministic fallback. */
class AIExtractionService(
    private val fallback: RuleExtractionService = RuleExtractionService()
) {
    val systemPrompt: String = """
        You are an extraction engine for a live performance utility. Your job is to read a transcript and extract only information that was actually spoken. Do not invent missing values. Return structured JSON only. Identify useful performance targets such as names, dates, birthdays, places, countries, cities, playing cards, numbers, serial numbers, songs, artists, lyrics, colors, objects, zodiac signs, emotions, celebrities, movies, animals, times, and full confabulation summaries. Normalize obvious values, but preserve the original spoken meaning. If unsure, lower the confidence score. Do not include start phrases or stop phrases in the final payload.
    """.trimIndent()

    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 20_000
        }
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun extract(
        transcript: String,
        startPhrases: List<String>,
        stopPhrases: List<String>,
        aiEnabled: Boolean,
        openAiApiKey: String,
        openAiModel: String
    ): ExtractedPerformanceData {
        val fallbackResult: ExtractedPerformanceData = fallback.extract(transcript, startPhrases, stopPhrases)
        if (!aiEnabled) return fallbackResult.copy(extractionSource = "Rule fallback")
        if (openAiApiKey.isBlank()) {
            return fallbackResult.copy(
                notes = listOf("GPT extraction is enabled, but no OpenAI API key is entered. Rule fallback produced this result."),
                extractionSource = "Rule fallback — GPT key missing"
            )
        }

        return runCatching {
            val content: String = requestOpenAiExtraction(
                transcript = transcript,
                startPhrases = startPhrases,
                stopPhrases = stopPhrases,
                apiKey = openAiApiKey,
                model = openAiModel.ifBlank { "gpt-4o-mini" }
            )
            parseOpenAiExtraction(content, fallbackResult)
        }.getOrElse { throwable ->
            fallbackResult.copy(
                notes = listOf("GPT extraction failed: ${throwable.message ?: "Unknown error"}. Rule fallback produced this result."),
                extractionSource = "Rule fallback — GPT failed"
            )
        }
    }

    private suspend fun requestOpenAiExtraction(
        transcript: String,
        startPhrases: List<String>,
        stopPhrases: List<String>,
        apiKey: String,
        model: String
    ): String {
        val responseText: String = client.post("https://api.openai.com/v1/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("model", model)
                    put("temperature", 0)
                    put("response_format", buildJsonObject { put("type", "json_object") })
                    put(
                        "messages",
                        buildJsonArray {
                            add(buildJsonObject {
                                put("role", "system")
                                put("content", systemPrompt)
                            })
                            add(buildJsonObject {
                                put("role", "user")
                                put(
                                    "content",
                                    """
                                    Return JSON with this shape:
                                    {
                                      "cleanedTranscript": "string",
                                      "detectedItems": [{"category":"place|celebrity|date|playing_card|song|artist|serial_number|zodiac|name|object|color|number|phrase|full_confabulation", "value":"string", "normalizedValue":"string", "confidence":0.0, "sourcePhrase":"string", "shouldPublish":true}],
                                      "bestMatches": {"place":"string", "name":"string", "date":"string", "card":"string", "song":"string", "artist":"string", "serial":"string", "zodiac":"string", "object":"string", "fullConfabulation":"string"},
                                      "confabulation": {"place":"string", "person":"string", "date":"string", "summary":"string"},
                                      "confidence":0.0,
                                      "notes":["string"]
                                    }
                                    Start phrases to remove: ${startPhrases.joinToString(" | ")}
                                    Stop phrases to remove: ${stopPhrases.joinToString(" | ")}
                                    Transcript: $transcript
                                    """.trimIndent()
                                )
                            })
                        }
                    )
                }.toString()
            )
        }.body()
        val responseObject: JsonObject = json.parseToJsonElement(responseText).jsonObject
        return responseObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?: error("OpenAI returned no extraction content")
    }

    private fun parseOpenAiExtraction(content: String, fallbackResult: ExtractedPerformanceData): ExtractedPerformanceData {
        val root: JsonObject = json.parseToJsonElement(content.trim()).jsonObject
        val cleanedTranscript: String = root["cleanedTranscript"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackResult.cleanedTranscript
        val detectedItems: List<DetectedItem> = parseDetectedItems(root["detectedItems"] as? JsonArray).ifEmpty { fallbackResult.detectedItems }
        val bestMatches: BestMatchSummary = parseBestMatches(root["bestMatches"] as? JsonObject, detectedItems, fallbackResult.bestMatches)
        val confabulation: ConfabulationPayload? = parseConfabulation(root["confabulation"] as? JsonObject, bestMatches)
        val confidence: Float = root["confidence"]?.jsonPrimitive?.floatOrNull ?: detectedItems.maxOfOrNull { it.confidence } ?: fallbackResult.confidence
        val notes: List<String> = (root["notes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        return ExtractedPerformanceData(
            rawTranscript = fallbackResult.rawTranscript,
            cleanedTranscript = cleanedTranscript,
            detectedItems = detectedItems.distinctBy { it.category to it.normalizedValue },
            bestMatches = if (confabulation != null && bestMatches.fullConfabulation.isNullOrBlank()) bestMatches.copy(fullConfabulation = confabulation.summary) else bestMatches,
            confabulation = confabulation,
            confidence = confidence.coerceIn(0f, 1f),
            notes = listOf("GPT extraction completed with model response JSON.") + notes,
            extractionSource = "OpenAI GPT extraction"
        )
    }

    private fun parseDetectedItems(array: JsonArray?): List<DetectedItem> = array?.mapIndexedNotNull { index, element ->
        val item: JsonObject = element as? JsonObject ?: return@mapIndexedNotNull null
        val category: DetectedCategory = parseCategory(item["category"]?.jsonPrimitive?.contentOrNull) ?: return@mapIndexedNotNull null
        val value: String = item["value"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val normalized: String = item["normalizedValue"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: value
        if (value.isBlank() && normalized.isBlank()) return@mapIndexedNotNull null
        DetectedItem(
            id = "gpt_${category.name}_$index",
            category = category,
            value = value.ifBlank { normalized },
            normalizedValue = normalized,
            confidence = (item["confidence"]?.jsonPrimitive?.floatOrNull ?: 0.75f).coerceIn(0f, 1f),
            sourcePhrase = item["sourcePhrase"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: value.ifBlank { normalized },
            shouldPublish = item["shouldPublish"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        )
    }.orEmpty()

    private fun parseBestMatches(root: JsonObject?, items: List<DetectedItem>, fallbackBest: BestMatchSummary): BestMatchSummary {
        fun field(name: String): String? = root?.get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        fun best(category: DetectedCategory): String? = items.filter { it.category == category }.maxByOrNull { it.confidence }?.normalizedValue
        return BestMatchSummary(
            word = field("word") ?: best(DetectedCategory.Word) ?: fallbackBest.word,
            phrase = field("phrase") ?: best(DetectedCategory.Phrase) ?: fallbackBest.phrase,
            name = field("name") ?: field("person") ?: best(DetectedCategory.Name) ?: best(DetectedCategory.Celebrity) ?: fallbackBest.name,
            date = field("date") ?: best(DetectedCategory.Date) ?: fallbackBest.date,
            birthday = field("birthday") ?: best(DetectedCategory.Birthday) ?: fallbackBest.birthday,
            place = field("place") ?: best(DetectedCategory.Place) ?: best(DetectedCategory.Country) ?: best(DetectedCategory.City) ?: fallbackBest.place,
            country = field("country") ?: best(DetectedCategory.Country) ?: fallbackBest.country,
            city = field("city") ?: best(DetectedCategory.City) ?: fallbackBest.city,
            card = field("card") ?: field("playingCard") ?: best(DetectedCategory.PlayingCard) ?: fallbackBest.card,
            number = field("number") ?: best(DetectedCategory.Number) ?: fallbackBest.number,
            serial = field("serial") ?: field("serialNumber") ?: best(DetectedCategory.SerialNumber) ?: fallbackBest.serial,
            song = field("song") ?: best(DetectedCategory.Song) ?: fallbackBest.song,
            artist = field("artist") ?: best(DetectedCategory.Artist) ?: fallbackBest.artist,
            lyric = field("lyric") ?: best(DetectedCategory.Lyric) ?: fallbackBest.lyric,
            color = field("color") ?: best(DetectedCategory.Color) ?: fallbackBest.color,
            objectValue = field("object") ?: field("objectValue") ?: best(DetectedCategory.Object) ?: fallbackBest.objectValue,
            zodiac = field("zodiac") ?: best(DetectedCategory.Zodiac) ?: fallbackBest.zodiac,
            time = field("time") ?: best(DetectedCategory.Time) ?: fallbackBest.time,
            fullConfabulation = field("fullConfabulation") ?: field("full_confabulation") ?: best(DetectedCategory.FullConfabulation) ?: fallbackBest.fullConfabulation
        )
    }

    private fun parseConfabulation(root: JsonObject?, best: BestMatchSummary): ConfabulationPayload? {
        fun field(name: String): String? = root?.get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val summary: String = field("summary") ?: best.fullConfabulation ?: listOfNotNull(best.place, best.name, best.date ?: best.birthday, best.objectValue, best.song).joinToString(" | ")
        if (summary.isBlank()) return null
        return ConfabulationPayload(
            place = field("place") ?: best.place,
            person = field("person") ?: best.name,
            date = field("date") ?: best.date ?: best.birthday,
            objectValue = field("object") ?: best.objectValue,
            song = field("song") ?: best.song,
            artist = field("artist") ?: best.artist,
            number = field("number") ?: best.number,
            color = field("color") ?: best.color,
            summary = summary
        )
    }

    private fun parseCategory(value: String?): DetectedCategory? = when (value?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_")) {
        "word" -> DetectedCategory.Word
        "phrase" -> DetectedCategory.Phrase
        "name" -> DetectedCategory.Name
        "date" -> DetectedCategory.Date
        "birthday" -> DetectedCategory.Birthday
        "place" -> DetectedCategory.Place
        "country" -> DetectedCategory.Country
        "city" -> DetectedCategory.City
        "number" -> DetectedCategory.Number
        "playing_card", "card" -> DetectedCategory.PlayingCard
        "serial_number", "serial" -> DetectedCategory.SerialNumber
        "song" -> DetectedCategory.Song
        "artist" -> DetectedCategory.Artist
        "lyric" -> DetectedCategory.Lyric
        "color" -> DetectedCategory.Color
        "object" -> DetectedCategory.Object
        "zodiac", "zodiac_sign" -> DetectedCategory.Zodiac
        "emotion" -> DetectedCategory.Emotion
        "movie" -> DetectedCategory.Movie
        "celebrity", "person" -> DetectedCategory.Celebrity
        "animal" -> DetectedCategory.Animal
        "time" -> DetectedCategory.Time
        "full_confabulation", "confabulation" -> DetectedCategory.FullConfabulation
        else -> null
    }
}
