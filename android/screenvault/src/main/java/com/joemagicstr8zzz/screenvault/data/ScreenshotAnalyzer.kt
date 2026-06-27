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
        "starbucks", "home depot", "lowes", "costco", "apple", "google", "samsung",
        "fedex", "ups", "usps", "dhl", "doordash", "uber", "lyft", "booking", "expedia"
    )

    private val categoryKeywords = mapOf(
        ScreenshotCategory.Receipt to listOf("receipt", "subtotal", "transaction", "paid", "purchase", "invoice", "total", "tax", "cashier", "change"),
        ScreenshotCategory.Order to listOf("order", "order number", "confirmed", "confirmation", "shipped", "delivered", "package", "estimated delivery"),
        ScreenshotCategory.Return to listOf("return", "refund", "exchange", "replacement", "return window", "return by", "eligible for return", "return until"),
        ScreenshotCategory.Tracking to listOf("tracking", "tracking number", "estimated delivery", "out for delivery", "carrier", "shipment"),
        ScreenshotCategory.Subscription to listOf("subscription", "renews", "renewal", "trial", "free trial", "cancel", "monthly", "annual", "billing cycle"),
        ScreenshotCategory.Appointment to listOf("appointment", "reservation", "booking", "scheduled", "meeting", "doctor", "dentist", "interview", "check-in"),
        ScreenshotCategory.QrBarcode to listOf("qr", "barcode", "scan code", "check-in code"),
        ScreenshotCategory.Link to listOf("http://", "https://", "www.", ".com", ".jp", ".org", ".net"),
        ScreenshotCategory.MemoIdea to listOf("idea", "note to self", "remember", "concept", "brainstorm", "draft", "prompt", "plan", "todo"),
        ScreenshotCategory.MessageFollowUp to listOf("reply", "respond", "get back", "let me know", "please confirm", "follow up", "can you send", "waiting on"),
        ScreenshotCategory.DocumentForm to listOf("form", "application", "paperwork", "document", "upload", "submit", "signature", "registration", "records"),
        ScreenshotCategory.Travel to listOf("flight", "hotel", "passport", "visa", "itinerary", "boarding", "check-in", "airport", "reservation"),
        ScreenshotCategory.FinanceTax to listOf("tax", "deductible", "expense", "reimbursement", "bill", "payment", "balance", "statement", "due"),
        ScreenshotCategory.FunnyShare to listOf("meme", "joke", "funny", "share this", "lol", "haha"),
        ScreenshotCategory.Personal to listOf("personal", "family", "home", "health", "medical", "school", "prescription")
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
        val text = cleanOcrText(visibleText)
        val links = extractLinks(text)
        val dates = extractDates(text)
        val amounts = extractAmounts(text)
        val codes = extractCodes(text, barcodeValues)
        val contacts = extractContacts(text)
        val source = inferSource(text, sourceHint)
        val category = inferCategory(text, codes)
        val priority = inferPriority(text, category, dates)
        val lowered = text.lowercase()
        val isReceipt = category == ScreenshotCategory.Receipt || lowered.containsAny("receipt", "invoice", "subtotal", "total", "paid")
        val isTax = lowered.containsAny("tax", "deductible", "business expense", "work expense", "reimbursement", "charity", "donation")
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
            title = inferTitle(text, category, source, amounts, dates, codes),
            summary = inferSummary(text, category, source, amounts, dates, codes),
            category = category,
            status = if (actionStatus) ScreenshotStatus.Action else ScreenshotStatus.Inbox,
            priority = priority,
            extractedText = text,
            confidence = confidenceFor(text, links, dates, amounts, codes),
            detectedDates = dates,
            detectedAmounts = amounts,
            detectedLinks = links,
            detectedCodes = codes,
            detectedContacts = contacts,
            detectedOrderInfo = orderInfo,
            suggestedAction = inferSuggestedAction(category, dates, amounts, codes),
            dueDateText = dueDateText,
            isReceipt = isReceipt,
            isTaxRecord = isTax,
            taxCategory = if (isTax) TaxCategory.NeedsReview else null,
            needsReview = sensitive || text.isBlank() || category == ScreenshotCategory.Unknown,
            isSensitive = sensitive,
            tags = buildTags(category, source, isReceipt, isTax, dates, amounts, codes)
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

    fun cleanOcrText(raw: String): String {
        val normalized = raw
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t\\r]+"), " ")
            .replace(Regex("[ ]{2,}"), " ")

        val lines = normalized.lines()
            .map { it.trim().trim('|', '•', '·', '-', '_', '=', '~') }
            .filter { it.length >= 2 }
            .filterNot { line -> line.isLikelyOcrJunk() }
            .filterNot { line -> line.lowercase() in ignoredUiFragments }

        val important = lines.filter { it.looksImportant() }
        val kept = when {
            important.size >= 4 -> important
            lines.size <= 18 -> lines
            else -> (important + lines.take(8)).distinct()
        }

        return kept.distinct().take(40).joinToString("\n")
    }

    private val ignoredUiFragments = setOf(
        "back", "next", "done", "ok", "cancel", "close", "menu", "home", "search", "settings",
        "share", "copy", "edit", "save", "delete", "open", "view", "more", "less", "skip",
        "sponsored", "advertisement", "ad", "learn more", "sign in", "log in"
    )

    private fun String.isLikelyOcrJunk(): Boolean {
        val s = trim()
        if (s.length <= 1) return true
        if (s.count { it.isLetterOrDigit() } == 0) return true
        val letters = s.count { it.isLetter() }
        val digits = s.count { it.isDigit() }
        val symbols = s.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        val total = s.length.coerceAtLeast(1)
        val symbolRatio = symbols.toDouble() / total
        val hasCjk = s.any { Character.UnicodeScript.of(it.code) in setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL) }
        if (!hasCjk && letters == 0 && digits < 3) return true
        if (!hasCjk && symbolRatio > 0.55 && !s.contains(Regex("https?://|www\\.|¥|\\$|@"))) return true
        if (s.length >= 14 && s.none { it.isWhitespace() } && letters + digits > 10 && !s.contains(Regex("https?://|www\\.|[A-Z]{2,}[-0-9]"))) return true
        return false
    }

    private fun String.looksImportant(): Boolean {
        val lower = lowercase()
        return lower.containsAny(
            "order", "return", "refund", "delivered", "tracking", "total", "subtotal", "receipt", "invoice", "tax",
            "appointment", "reservation", "booking", "due", "expires", "renew", "cancel", "payment", "bill",
            "confirmation", "flight", "hotel", "passport", "visa", "submit", "signature", "form", "reply", "confirm",
            "http://", "https://", "www.", "qr", "barcode", "idea", "remember", "note"
        ) || contains('¥') || contains('$') || contains('@') || any { it.isDigit() } || contains(Regex("https?://|www\\."))
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
                raw.contains('¥') || raw.contains("yen", true) || raw.contains("jpy", true) -> "JPY"
                raw.contains('$') || raw.contains("usd", true) -> "USD"
                else -> null
            }
            val amount = raw.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            ExtractedAmount(makeId("amt"), raw, currency, amount)
        }.take(10).toList()
    }

    private fun extractDates(text: String): List<ExtractedDate> {
        val patterns = listOf(
            Regex("(?:due|expires|return by|return until|return window closes|appointment|delivery|delivered|renews|renewal|scheduled|booking)\\s*(?:on|by|until|closes on)?\\s*([A-Z][a-z]{2,8}\\s+\\d{1,2},?\\s*\\d{0,4})", RegexOption.IGNORE_CASE),
            Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"),
            Regex("\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b"),
            Regex("\\b(?:today|tomorrow|next week|this weekend)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}(?:,?\\s*\\d{4})?\\b", RegexOption.IGNORE_CASE)
        )
        val found = patterns.flatMap { pattern -> pattern.findAll(text).map { it.groups.getOrNull(1)?.value ?: it.value } }
        return found.distinct().take(12).map { raw ->
            val index = text.lowercase().indexOf(raw.lowercase())
            val context = if (index >= 0) text.substring(maxOf(0, index - 45), min(text.length, index + raw.length + 45)).lowercase() else ""
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
        return detected.distinctBy { it.value }.take(20)
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
        val scored = categoryKeywords.mapValues { (_, words) -> words.sumOf { keyword -> if (lower.contains(keyword)) if (keyword.length > 6) 2 else 1 else 0 } }
        val best = scored.maxByOrNull { it.value }
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
        if (lower.containsAny("return window", "payment due", "bill", "appointment", "renewal", "cancel before", "submit", "due in 3 days")) return Priority.High
        if (dates.any { it.dateType in listOf("return_by", "due", "appointment") }) return Priority.High
        return if (category in setOf(ScreenshotCategory.Return, ScreenshotCategory.Subscription, ScreenshotCategory.Appointment, ScreenshotCategory.DocumentForm, ScreenshotCategory.MessageFollowUp, ScreenshotCategory.FinanceTax, ScreenshotCategory.Tracking)) Priority.Medium else Priority.Low
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

    private fun inferTitle(text: String, category: ScreenshotCategory, source: String?, amounts: List<ExtractedAmount>, dates: List<ExtractedDate>, codes: List<ExtractedCode>): String {
        val firstLine = text.lines().map { it.trim() }.firstOrNull { it.isNotBlank() && it.length > 3 }
        return when (category) {
            ScreenshotCategory.Return -> if (source != null) "$source Return" else "Return Window"
            ScreenshotCategory.Order -> if (source != null) "$source Order" else "Order Confirmation"
            ScreenshotCategory.Receipt -> if (source != null) "$source Receipt" else "Receipt${amounts.firstOrNull()?.rawText?.let { " • $it" }.orEmpty()}"
            ScreenshotCategory.Appointment -> "Appointment${dates.firstOrNull()?.rawText?.let { " • $it" }.orEmpty()}"
            ScreenshotCategory.Subscription -> "Subscription Renewal"
            ScreenshotCategory.QrBarcode -> "QR / Barcode${codes.firstOrNull()?.value?.take(18)?.let { " • $it" }.orEmpty()}"
            ScreenshotCategory.DocumentForm -> "Document or Form"
            ScreenshotCategory.MessageFollowUp -> "Message Follow-up"
            ScreenshotCategory.Travel -> "Travel Record"
            ScreenshotCategory.MemoIdea -> "Saved Idea"
            else -> firstLine?.take(52) ?: "Imported Screenshot"
        }
    }

    private fun inferSummary(text: String, category: ScreenshotCategory, source: String?, amounts: List<ExtractedAmount>, dates: List<ExtractedDate>, codes: List<ExtractedCode>): String {
        val primaryAmount = amounts.firstOrNull()?.rawText
        val primaryDate = dates.firstOrNull()?.rawText
        return when (category) {
            ScreenshotCategory.Return -> "Possible return/refund${source?.let { " from $it" }.orEmpty()}${primaryDate?.let { " by $it" }.orEmpty()}."
            ScreenshotCategory.Order -> "Order or confirmation${source?.let { " from $it" }.orEmpty()}${primaryDate?.let { " dated $it" }.orEmpty()}."
            ScreenshotCategory.Receipt -> "Receipt or purchase record${source?.let { " from $it" }.orEmpty()}${primaryAmount?.let { " for $it" }.orEmpty()}."
            ScreenshotCategory.Appointment -> "Appointment or scheduled event${primaryDate?.let { " on $it" }.orEmpty()}."
            ScreenshotCategory.QrBarcode -> "QR/barcode value detected${codes.firstOrNull()?.value?.let { ": ${it.take(60)}" }.orEmpty()}."
            ScreenshotCategory.Link -> "Link or web page saved${extractLinks(text).firstOrNull()?.domain?.let { " from $it" }.orEmpty()}."
            ScreenshotCategory.MemoIdea -> text.lines().firstOrNull { it.length > 8 }?.take(120) ?: "Possible idea, note, or memory item."
            ScreenshotCategory.MessageFollowUp -> "Message may need a reply or follow-up."
            ScreenshotCategory.Unknown -> "Image imported. Review or add context if needed."
            else -> text.lines().map { it.trim() }.firstOrNull { it.isNotBlank() }?.take(120) ?: "Screenshot saved."
        }
    }

    private fun inferSuggestedAction(category: ScreenshotCategory, dates: List<ExtractedDate>, amounts: List<ExtractedAmount>, codes: List<ExtractedCode>): String = when (category) {
        ScreenshotCategory.Return -> "Review before the return window closes."
        ScreenshotCategory.Subscription -> "Review renewal and cancel if needed."
        ScreenshotCategory.Appointment -> "Confirm details and set a reminder."
        ScreenshotCategory.MessageFollowUp -> "Send a reply or follow up."
        ScreenshotCategory.DocumentForm -> "Complete, submit, or file this document."
        ScreenshotCategory.FinanceTax -> "Review amount and mark as tax record if needed."
        ScreenshotCategory.Receipt -> "Save for records; mark as tax-related if needed."
        ScreenshotCategory.Tracking -> "Track the shipment or save the tracking number."
        ScreenshotCategory.Order -> "Save order details and watch delivery or return dates."
        ScreenshotCategory.QrBarcode -> if (codes.isNotEmpty()) "Save or open the detected code/link when needed." else "Review the detected code."
        ScreenshotCategory.Link -> "Save the link for later use."
        else -> if (dates.isNotEmpty() || amounts.isNotEmpty()) "Review the extracted details and decide if action is needed." else "Save, tag, or ignore this item."
    }

    private fun confidenceFor(text: String, links: List<ExtractedLink>, dates: List<ExtractedDate>, amounts: List<ExtractedAmount>, codes: List<ExtractedCode>): Confidence {
        val signalCount = links.size + dates.size + amounts.size + codes.size
        return when {
            signalCount >= 3 || text.length > 100 -> Confidence.High
            signalCount >= 1 || text.length > 20 -> Confidence.Medium
            else -> Confidence.Low
        }
    }

    private fun hasAction(category: ScreenshotCategory, priority: Priority, dueDateText: String?): Boolean {
        val actionCategories = setOf(ScreenshotCategory.Return, ScreenshotCategory.Tracking, ScreenshotCategory.Subscription, ScreenshotCategory.Appointment, ScreenshotCategory.MessageFollowUp, ScreenshotCategory.DocumentForm, ScreenshotCategory.Travel, ScreenshotCategory.FinanceTax, ScreenshotCategory.Order)
        return category in actionCategories || dueDateText != null || priority == Priority.High || priority == Priority.Urgent
    }

    private fun detectSensitive(text: String): Boolean {
        val lower = text.lowercase()
        return lower.containsAny("password", "bank account", "credit card", "passport number", "medical record", "diagnosis", "prescription", "dod id", "ssn", "social security")
    }

    private fun buildTags(category: ScreenshotCategory, source: String?, receipt: Boolean, tax: Boolean, dates: List<ExtractedDate>, amounts: List<ExtractedAmount>, codes: List<ExtractedCode>): List<String> {
        val tags = mutableListOf(category.label.lowercase(Locale.US))
        source?.lowercase(Locale.US)?.let { tags.add(it) }
        if (receipt) tags.add("receipt")
        if (tax) tags.add("tax")
        if (dates.isNotEmpty()) tags.add("date")
        if (amounts.isNotEmpty()) tags.add("money")
        if (codes.isNotEmpty()) tags.add("code")
        return tags.distinct()
    }

    private fun String.containsAny(vararg words: String): Boolean = words.any { contains(it) }
    private fun String.titleCase(): String = split(" ").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } }
    private fun makeId(prefix: String): String = "${prefix}_${System.currentTimeMillis().toString(36)}_${(1000..9999).random()}"
}
