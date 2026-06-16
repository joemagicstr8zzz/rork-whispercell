package com.joemagicstr8zzz.screenvault.data

import com.joemagicstr8zzz.screenvault.model.Confidence
import com.joemagicstr8zzz.screenvault.model.ExtractedAmount
import com.joemagicstr8zzz.screenvault.model.ExtractedCode
import com.joemagicstr8zzz.screenvault.model.ExtractedContact
import com.joemagicstr8zzz.screenvault.model.ExtractedDate
import com.joemagicstr8zzz.screenvault.model.ExtractedLink
import com.joemagicstr8zzz.screenvault.model.ExtractedOrderInfo
import com.joemagicstr8zzz.screenvault.model.Priority
import com.joemagicstr8zzz.screenvault.model.ScreenshotCategory
import com.joemagicstr8zzz.screenvault.model.ScreenshotItem
import com.joemagicstr8zzz.screenvault.model.ScreenshotStatus
import com.joemagicstr8zzz.screenvault.model.TaxCategory
import java.util.Locale
import kotlin.math.min

object ScreenshotAnalyzer {
    private val vendorHints = listOf(
        "amazon", "walmart", "target", "ebay", "etsy", "rakuten", "paypal", "venmo",
        "starbucks", "home depot", "lowes", "costco", "apple", "google", "samsung"
    )

    private val categoryKeywords = mapOf(
        ScreenshotCategory.Receipt to listOf("receipt", "subtotal", "transaction", "paid", "purchase", "invoice", "total"),
        ScreenshotCategory.Order to listOf("order", "order number", "confirmed", "confirmation", "shipped", "delivered", "package"),
        ScreenshotCategory.Return to listOf("return", "refund", "exchange", "replacement", "return window", "return by", "eligible for return"),
        ScreenshotCategory.Tracking to listOf("tracking", "tracking number", "estimated delivery", "out for delivery", "carrier"),
        ScreenshotCategory.Subscription to listOf("subscription", "renews", "renewal", "trial", "free trial", "cancel", "monthly", "annual"),
        ScreenshotCategory.Appointment to listOf("appointment", "reservation", "booking", "scheduled", "meeting", "doctor", "dentist", "interview"),
        ScreenshotCategory.QrBarcode to listOf("qr", "barcode", "scan code", "check-in code"),
        ScreenshotCategory.Link to listOf("http://", "https://", "www.", ".com", ".jp", ".org"),
        ScreenshotCategory.MemoIdea to listOf("idea", "note to self", "remember", "concept", "brainstorm", "draft", "prompt", "plan"),
        ScreenshotCategory.MessageFollowUp to listOf("reply", "respond", "get back", "let me know", "please confirm", "follow up", "can you send"),
        ScreenshotCategory.DocumentForm to listOf("form", "application", "paperwork", "document", "upload", "submit", "signature", "registration"),
        ScreenshotCategory.Travel to listOf("flight", "hotel", "passport", "visa", "itinerary", "boarding", "check-in", "airport"),
        ScreenshotCategory.FinanceTax to listOf("tax", "deductible", "expense", "reimbursement", "bill", "payment", "balance", "statement"),
        ScreenshotCategory.FunnyShare to listOf("meme", "joke", "funny", "share this", "lol", "haha"),
        ScreenshotCategory.Personal to listOf("personal", "family", "home", "health", "medical", "school")
    )

    fun analyze(
        imageUri: String,
        sourceType: String,
        visibleText: String = "",
        barcodeValues: List<String> = emptyList(),
        createdAt: Long = System.currentTimeMillis(),
        sourceHint: String? = null
    ): ScreenshotItem {
        val importedAt = System.currentTimeMillis()
        val text = visibleText.trim()
        val links = extractLinks(text)
        val dates = extractDates(text)
        val amounts = extractAmounts(text)
        val codes = extractCodes(text, barcodeValues)
        val contacts = extractContacts(text)
        val source = inferSource(text, sourceHint)
        val category = inferCategory(text, codes)
        val priority = inferPriority(text, category, dates)
        val isReceipt = category == ScreenshotCategory.Receipt || text.lowercase().containsAny("receipt", "invoice", "subtotal", "total")
        val isTax = text.lowercase().containsAny("tax", "deductible", "business expense", "work expense", "reimbursement")
        val sensitive = detectSensitive(text)
        val orderInfo = inferOrderInfo(text, codes, dates, source)
        val dueDateText = dates.firstOrNull { it.dateType in listOf("return_by", "due", "appointment", "renewal") }?.rawText
        val actionStatus = hasAction(category, priority, dueDateText)

        return ScreenshotItem(
            id = makeId("item"),
            imageUri = imageUri,
            sourceType = sourceType,
            sourceSite = source,
            createdAt = createdAt,
            importedAt = importedAt,
            updatedAt = importedAt,
            title = inferTitle(text, category, source),
            summary = inferSummary(text, category, source),
            category = category,
            status = if (actionStatus) ScreenshotStatus.Action else ScreenshotStatus.Inbox,
            priority = priority,
            extractedText = text,
            confidence = when {
                text.length > 60 -> Confidence.High
                text.isNotBlank() -> Confidence.Medium
                else -> Confidence.Low
            },
            detectedDates = dates,
            detectedAmounts = amounts,
            detectedLinks = links,
            detectedCodes = codes,
            detectedContacts = contacts,
            detectedOrderInfo = orderInfo,
            suggestedAction = inferSuggestedAction(category),
            dueDateText = dueDateText,
            isReceipt = isReceipt,
            isTaxRecord = isTax,
            taxCategory = if (isTax) TaxCategory.NeedsReview else null,
            needsReview = sensitive || text.isBlank() || category == ScreenshotCategory.Unknown,
            isSensitive = sensitive,
            tags = buildTags(category, source, isReceipt, isTax)
        )
    }

    fun reanalyze(item: ScreenshotItem, visibleText: String): ScreenshotItem {
        val analyzed = analyze(
            imageUri = item.imageUri,
            sourceType = item.sourceType,
            visibleText = visibleText,
            barcodeValues = item.detectedCodes.map { it.value },
            createdAt = item.createdAt,
            sourceHint = item.sourceSite ?: item.sourceApp
        )
        return analyzed.copy(
            id = item.id,
            importedAt = item.importedAt,
            updatedAt = System.currentTimeMillis(),
            userNotes = item.userNotes
        )
    }

    private fun extractLinks(text: String): List<ExtractedLink> {
        val regex = Regex("https?://[^\\s)]+|www\\.[^\\s)]+", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { match ->
            val raw = match.value
            val normalized = if (raw.startsWith("www.")) "https://$raw" else raw
            val domain = normalized.removePrefix("https://").removePrefix("http://").substringBefore("/").removePrefix("www.")
            ExtractedLink(makeId("link"), normalized, domain)
        }.distinctBy { it.url }.take(12).toList()
    }

    private fun extractAmounts(text: String): List<ExtractedAmount> {
        val regex = Regex("(?:¥|\\$|USD|JPY)\\s?\\d{1,3}(?:[,\\d]{0,12})(?:\\.\\d{2})?|\\d{1,3}(?:[,\\d]{0,12})(?:\\.\\d{2})?\\s?(?:yen|usd|jpy)", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { match ->
            val raw = match.value
            val currency = when {
                raw.contains("¥") || raw.contains("yen", true) || raw.contains("jpy", true) -> "JPY"
                raw.contains("$") || raw.contains("usd", true) -> "USD"
                else -> null
            }
            val amount = raw.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            ExtractedAmount(makeId("amt"), raw, currency, amount)
        }.take(10).toList()
    }

    private fun extractDates(text: String): List<ExtractedDate> {
        val patterns = listOf(
            Regex("(?:due|expires|return by|return until|appointment|delivery|delivered|renews|renewal|scheduled|booking)\\s*(?:on|by|until)?\\s*([A-Z][a-z]{2,8}\\s+\\d{1,2},?\\s*\\d{0,4})", RegexOption.IGNORE_CASE),
            Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"),
            Regex("\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b"),
            Regex("\\b(?:today|tomorrow|next week|this weekend)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}(?:,?\\s*\\d{4})?\\b", RegexOption.IGNORE_CASE)
        )
        val found = patterns.flatMap { pattern -> pattern.findAll(text).map { it.groups.getOrNull(1)?.value ?: it.value } }
        return found.distinct().take(12).map { raw ->
            val index = text.lowercase().indexOf(raw.lowercase())
            val context = if (index >= 0) text.substring(maxOf(0, index - 35), min(text.length, index + raw.length + 35)).lowercase() else ""
            val type = when {
                "return" in context -> "return_by"
                "appointment" in context || "scheduled" in context || "booking" in context -> "appointment"
                "delivery" in context || "delivered" in context -> "delivery"
                "renew" in context -> "renewal"
                "due" in context || "expires" in context -> "due"
                else -> "unknown"
            }
            ExtractedDate(makeId("date"), raw, type)
        }
    }

    private fun extractCodes(text: String, barcodeValues: List<String>): List<ExtractedCode> {
        val detected = barcodeValues.map { ExtractedCode(makeId("code"), "qr", it) }.toMutableList()
        val patterns = listOf(
            "order" to Regex("(?:order(?: number| #| no\\.)?[:\\s]+)([A-Z0-9-]{6,})", RegexOption.IGNORE_CASE),
            "tracking" to Regex("(?:tracking(?: number| #| no\\.)?[:\\s]+)([A-Z0-9-]{8,})", RegexOption.IGNORE_CASE),
            "confirmation" to Regex("(?:confirmation(?: number| #| code)?[:\\s]+)([A-Z0-9-]{5,})", RegexOption.IGNORE_CASE),
            "coupon" to Regex("(?:coupon|promo)(?: code)?[:\\s]+([A-Z0-9-]{4,})", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { (type, regex) ->
            regex.findAll(text).forEach { match -> match.groups[1]?.value?.let { detected.add(ExtractedCode(makeId("code"), type, it)) } }
        }
        return detected.take(20)
    }

    private fun extractContacts(text: String): List<ExtractedContact> {
        val results = mutableListOf<ExtractedContact>()
        Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE).findAll(text).forEach {
            results.add(ExtractedContact(makeId("contact"), "email", it.value))
        }
        vendorHints.forEach { vendor ->
            if (text.lowercase().contains(vendor)) results.add(ExtractedContact(makeId("contact"), "company", vendor.titleCase()))
        }
        return results.take(12)
    }

    private fun inferCategory(text: String, codes: List<ExtractedCode>): ScreenshotCategory {
        val lower = text.lowercase()
        if (codes.isNotEmpty() && text.isBlank()) return ScreenshotCategory.QrBarcode
        val best = categoryKeywords.mapValues { (_, words) -> words.count { lower.contains(it) } }.maxByOrNull { it.value }
        if (best == null || best.value == 0) {
            if (codes.isNotEmpty()) return ScreenshotCategory.QrBarcode
            if (extractLinks(text).isNotEmpty()) return ScreenshotCategory.Link
            return if (text.isBlank()) ScreenshotCategory.Unknown else ScreenshotCategory.MemoIdea
        }
        if ((best.key == ScreenshotCategory.Order || best.key == ScreenshotCategory.Receipt) && lower.containsAny("return", "refund", "return window")) return ScreenshotCategory.Return
        return best.key
    }

    private fun inferPriority(text: String, category: ScreenshotCategory, dates: List<ExtractedDate>): Priority {
        val lower = text.lowercase()
        if (lower.containsAny("overdue", "final notice", "expires today", "due today", "urgent", "last day")) return Priority.Urgent
        if (lower.containsAny("return window", "payment due", "bill", "appointment", "renewal", "cancel before", "submit")) return Priority.High
        if (dates.any { it.dateType in listOf("return_by", "due", "appointment") }) return Priority.High
        return if (category in setOf(ScreenshotCategory.Return, ScreenshotCategory.Subscription, ScreenshotCategory.Appointment, ScreenshotCategory.DocumentForm, ScreenshotCategory.MessageFollowUp, ScreenshotCategory.FinanceTax)) Priority.Medium else Priority.Low
    }

    private fun inferSource(text: String, sourceHint: String?): String? {
        val combined = "${sourceHint.orEmpty()} $text".lowercase()
        return vendorHints.firstOrNull { combined.contains(it) }?.titleCase()
    }

    private fun inferOrderInfo(text: String, codes: List<ExtractedCode>, dates: List<ExtractedDate>, vendor: String?): ExtractedOrderInfo {
        val productLine = text.lines().firstOrNull { it.contains("item", true) || it.contains("product", true) }
        return ExtractedOrderInfo(
            vendor = vendor,
            orderNumber = codes.firstOrNull { it.type == "order" }?.value,
            trackingNumber = codes.firstOrNull { it.type == "tracking" }?.value,
            productName = productLine?.replace("item", "", true)?.replace("product", "", true)?.replace(":", "")?.trim(),
            returnBy = dates.firstOrNull { it.dateType == "return_by" }?.rawText,
            deliveredDate = dates.firstOrNull { it.dateType == "delivery" }?.rawText
        )
    }

    private fun inferTitle(text: String, category: ScreenshotCategory, source: String?): String {
        val firstLine = text.lines().map { it.trim() }.firstOrNull { it.isNotBlank() }
        return when (category) {
            ScreenshotCategory.Return -> if (source != null) "$source Return" else "Return Window"
            ScreenshotCategory.Order -> if (source != null) "$source Order" else "Order Confirmation"
            ScreenshotCategory.Receipt -> if (source != null) "$source Receipt" else "Receipt"
            ScreenshotCategory.Appointment -> "Appointment"
            ScreenshotCategory.Subscription -> "Subscription Renewal"
            ScreenshotCategory.QrBarcode -> "QR / Barcode"
            ScreenshotCategory.DocumentForm -> "Document or Form"
            ScreenshotCategory.MessageFollowUp -> "Message Follow-up"
            ScreenshotCategory.Travel -> "Travel Record"
            ScreenshotCategory.MemoIdea -> "Saved Idea"
            else -> firstLine?.take(52) ?: "Imported Screenshot"
        }
    }

    private fun inferSummary(text: String, category: ScreenshotCategory, source: String?): String {
        return when (category) {
            ScreenshotCategory.Return -> "Possible return or refund item${source?.let { " from $it" }.orEmpty()}."
            ScreenshotCategory.Order -> "Order or confirmation screenshot${source?.let { " from $it" }.orEmpty()}."
            ScreenshotCategory.Receipt -> "Receipt or purchase record${source?.let { " from $it" }.orEmpty()}."
            ScreenshotCategory.Appointment -> "Appointment or scheduled event found."
            ScreenshotCategory.QrBarcode -> "QR code, barcode, or scannable value saved."
            ScreenshotCategory.Link -> "Link or web page screenshot saved."
            ScreenshotCategory.MemoIdea -> "Possible idea, note, or memory item."
            ScreenshotCategory.MessageFollowUp -> "Message may need a reply or follow-up."
            ScreenshotCategory.Unknown -> "Image imported. Add visible text to improve analysis."
            else -> text.lines().map { it.trim() }.firstOrNull { it.isNotBlank() }?.take(120) ?: "Screenshot saved."
        }
    }

    private fun inferSuggestedAction(category: ScreenshotCategory): String = when (category) {
        ScreenshotCategory.Return -> "Review before the return window closes."
        ScreenshotCategory.Subscription -> "Review renewal and cancel if needed."
        ScreenshotCategory.Appointment -> "Confirm details and set a reminder."
        ScreenshotCategory.MessageFollowUp -> "Send a reply or follow up."
        ScreenshotCategory.DocumentForm -> "Complete or file the document."
        ScreenshotCategory.FinanceTax -> "Review amount and mark as tax record if needed."
        ScreenshotCategory.Receipt -> "Save for records or mark as tax-related."
        ScreenshotCategory.Tracking -> "Track the shipment or save the tracking number."
        ScreenshotCategory.Order -> "Save order details and watch delivery or return dates."
        ScreenshotCategory.QrBarcode, ScreenshotCategory.Link -> "Save code or link for later use."
        else -> "Save, tag, or ignore this item."
    }

    private fun hasAction(category: ScreenshotCategory, priority: Priority, dueDateText: String?): Boolean {
        val actionCategories = setOf(ScreenshotCategory.Return, ScreenshotCategory.Tracking, ScreenshotCategory.Subscription, ScreenshotCategory.Appointment, ScreenshotCategory.MessageFollowUp, ScreenshotCategory.DocumentForm, ScreenshotCategory.Travel, ScreenshotCategory.FinanceTax, ScreenshotCategory.Order)
        return category in actionCategories || dueDateText != null || priority == Priority.High || priority == Priority.Urgent
    }

    private fun detectSensitive(text: String): Boolean {
        val lower = text.lowercase()
        return lower.containsAny("password", "bank account", "credit card", "passport number", "medical record", "diagnosis", "prescription", "dod id", "ssn")
    }

    private fun buildTags(category: ScreenshotCategory, source: String?, receipt: Boolean, tax: Boolean): List<String> {
        val tags = mutableListOf(category.label.lowercase(Locale.US))
        source?.lowercase(Locale.US)?.let { tags.add(it) }
        if (receipt) tags.add("receipt")
        if (tax) tags.add("tax")
        return tags.distinct()
    }

    private fun String.containsAny(vararg words: String): Boolean = words.any { contains(it) }
    private fun String.titleCase(): String = split(" ").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } }
    private fun makeId(prefix: String): String = "${prefix}_${System.currentTimeMillis().toString(36)}_${(1000..9999).random()}"
}
