package com.joemagicstr8zzz.screenvault.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.joemagicstr8zzz.screenvault.model.Priority
import com.joemagicstr8zzz.screenvault.model.ScreenshotCategory
import com.joemagicstr8zzz.screenvault.model.ScreenshotItem
import com.joemagicstr8zzz.screenvault.model.ScreenshotStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AdvancedImageAnalyzer {
    suspend fun analyzeImage(
        context: Context,
        imageUri: String,
        sourceType: String,
        sourceHint: String? = null,
        openAiApiKey: String = "",
        openAiModel: String = "gpt-5.5",
        useOpenAi: Boolean = false
    ): ScreenshotItem {
        val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
        val ocrText = runMultilingualOcr(image)
        val codes = runBarcodeScan(image)
        val local = ScreenshotAnalyzer.analyze(
            imageUri = imageUri,
            sourceType = sourceType,
            visibleText = ocrText,
            barcodeValues = codes,
            sourceHint = sourceHint
        )
        return if (useOpenAi && openAiApiKey.isNotBlank()) {
            runCatching { enhanceWithOpenAi(context, imageUri, openAiApiKey, openAiModel, local) }
                .getOrElse { local.copy(userNotes = appendNote(local.userNotes, "OpenAI vision failed: ${it.message ?: "unknown error"}")) }
        } else local
    }

    private suspend fun runMultilingualOcr(image: InputImage): String {
        val recognizers = listOf(
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        )
        val texts = mutableListOf<String>()
        recognizers.forEach { recognizer ->
            runCatching { recognizer.process(image).await().text.trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { texts.add(it) }
        }
        return texts
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private suspend fun runBarcodeScan(image: InputImage): List<String> {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return runCatching { scanner.process(image).await() }
            .getOrDefault(emptyList())
            .mapNotNull { barcode -> barcode.rawValue ?: barcode.displayValue }
            .distinct()
    }

    private suspend fun enhanceWithOpenAi(
        context: Context,
        imageUri: String,
        apiKey: String,
        model: String,
        local: ScreenshotItem
    ): ScreenshotItem = withContext(Dispatchers.IO) {
        val imageBytes = context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { it.readBytes() }
            ?: return@withContext local
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val prompt = """
            Analyze this mobile screenshot for ScreenVault. Extract text, identify image type, QR/barcode meaning if visible, receipt/order/return/tracking/tax/appointment/link/memo/message/document/travel/funny/unknown category, dates, money amounts, vendor/source, urgency, and recommended user action.
            Return ONLY JSON with keys: title, summary, category, priority, suggestedAction, dueDateText, isReceipt, isTaxRecord, isSensitive, tags, extraText.
            Allowed category values: receipt, order, return, tracking, subscription, appointment, qr_barcode, link, memo_idea, message_followup, document_form, travel, finance_tax, funny_share, personal, unknown.
            Allowed priority values: low, medium, high, urgent.
        """.trimIndent()
        val body = JSONObject()
            .put("model", model)
            .put("max_output_tokens", 900)
            .put("input", org.json.JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", org.json.JSONArray()
                        .put(JSONObject().put("type", "input_text").put("text", prompt))
                        .put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,$base64"))
                    )
            ))
        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (connection.responseCode !in 200..299) error(raw.take(240))
        val outputText = extractOutputText(raw)
        val jsonText = outputText.substringAfter("{", "").substringBeforeLast("}", "")
        if (jsonText.isBlank()) return@withContext local.copy(userNotes = appendNote(local.userNotes, "OpenAI vision returned no structured JSON."))
        val ai = JSONObject("{$jsonText}")
        mergeAi(local, ai)
    }

    private fun extractOutputText(raw: String): String {
        val root = JSONObject(raw)
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = root.optJSONArray("output") ?: return raw
        val chunks = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                part.optString("text").takeIf { it.isNotBlank() }?.let { chunks.add(it) }
            }
        }
        return chunks.joinToString("\n")
    }

    private fun mergeAi(local: ScreenshotItem, ai: JSONObject): ScreenshotItem {
        val category = ai.optString("category").toCategory() ?: local.category
        val priority = ai.optString("priority").toPriority() ?: local.priority
        val aiText = ai.optString("extraText").trim()
        val mergedText = listOf(local.extractedText, aiText).filter { it.isNotBlank() }.distinct().joinToString("\n")
        val reanalyzed = ScreenshotAnalyzer.reanalyze(local.copy(extractedText = mergedText), mergedText)
        val tags = ai.optJSONArray("tags")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { tag -> tag.isNotBlank() } }
        }.orEmpty()
        return reanalyzed.copy(
            title = ai.optString("title").takeIf { it.isNotBlank() } ?: reanalyzed.title,
            summary = ai.optString("summary").takeIf { it.isNotBlank() } ?: reanalyzed.summary,
            category = category,
            priority = priority,
            status = if (priority == Priority.High || priority == Priority.Urgent || category in actionCategories) ScreenshotStatus.Action else reanalyzed.status,
            suggestedAction = ai.optString("suggestedAction").takeIf { it.isNotBlank() } ?: reanalyzed.suggestedAction,
            dueDateText = ai.optString("dueDateText").takeIf { it.isNotBlank() } ?: reanalyzed.dueDateText,
            isReceipt = ai.optBoolean("isReceipt", reanalyzed.isReceipt),
            isTaxRecord = ai.optBoolean("isTaxRecord", reanalyzed.isTaxRecord),
            isSensitive = ai.optBoolean("isSensitive", reanalyzed.isSensitive),
            needsReview = ai.optBoolean("isSensitive", reanalyzed.needsReview),
            tags = (reanalyzed.tags + tags + "openai-vision").distinct(),
            userNotes = appendNote(reanalyzed.userNotes, "OpenAI vision enrichment applied.")
        )
    }

    private val actionCategories = setOf(
        ScreenshotCategory.Return,
        ScreenshotCategory.Tracking,
        ScreenshotCategory.Subscription,
        ScreenshotCategory.Appointment,
        ScreenshotCategory.MessageFollowUp,
        ScreenshotCategory.DocumentForm,
        ScreenshotCategory.Travel,
        ScreenshotCategory.FinanceTax,
        ScreenshotCategory.Order
    )

    private fun String.toCategory(): ScreenshotCategory? = when (lowercase()) {
        "receipt" -> ScreenshotCategory.Receipt
        "order" -> ScreenshotCategory.Order
        "return" -> ScreenshotCategory.Return
        "tracking" -> ScreenshotCategory.Tracking
        "subscription" -> ScreenshotCategory.Subscription
        "appointment" -> ScreenshotCategory.Appointment
        "qr_barcode" -> ScreenshotCategory.QrBarcode
        "link" -> ScreenshotCategory.Link
        "memo_idea" -> ScreenshotCategory.MemoIdea
        "message_followup" -> ScreenshotCategory.MessageFollowUp
        "document_form" -> ScreenshotCategory.DocumentForm
        "travel" -> ScreenshotCategory.Travel
        "finance_tax" -> ScreenshotCategory.FinanceTax
        "funny_share" -> ScreenshotCategory.FunnyShare
        "personal" -> ScreenshotCategory.Personal
        "unknown" -> ScreenshotCategory.Unknown
        else -> null
    }

    private fun String.toPriority(): Priority? = when (lowercase()) {
        "low" -> Priority.Low
        "medium" -> Priority.Medium
        "high" -> Priority.High
        "urgent" -> Priority.Urgent
        else -> null
    }

    private fun appendNote(existing: String, note: String): String = listOf(existing, note).filter { it.isNotBlank() }.joinToString("\n")

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
}
