package com.joemagicstr8zzz.screenvault.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScreenshotCategory(val label: String) {
    Receipt("Receipt"),
    Order("Order"),
    Return("Return"),
    Tracking("Tracking"),
    Subscription("Subscription"),
    Appointment("Appointment"),
    QrBarcode("QR / Barcode"),
    Link("Link"),
    MemoIdea("Memo / Idea"),
    MessageFollowUp("Message Follow-up"),
    DocumentForm("Document / Form"),
    Travel("Travel"),
    FinanceTax("Finance / Tax"),
    FunnyShare("Funny / Share"),
    Personal("Personal"),
    Unknown("Unknown")
}

@Serializable
enum class ScreenshotStatus(val label: String) {
    Inbox("Inbox"),
    Saved("Saved"),
    Action("Action"),
    Snoozed("Snoozed"),
    Done("Done"),
    Archived("Archived"),
    Ignored("Ignored")
}

@Serializable
enum class Priority(val label: String) {
    Low("Low"),
    Medium("Medium"),
    High("High"),
    Urgent("Urgent")
}

@Serializable
enum class Confidence(val label: String) {
    Low("Low"),
    Medium("Medium"),
    High("High")
}

@Serializable
enum class TaxCategory(val label: String) {
    Work("Work"),
    Business("Business"),
    Medical("Medical"),
    Travel("Travel"),
    Education("Education"),
    Charity("Charity"),
    Personal("Personal"),
    NeedsReview("Needs Review")
}

@Serializable
data class ExtractedDate(
    val id: String,
    val rawText: String,
    val dateType: String = "unknown"
)

@Serializable
data class ExtractedAmount(
    val id: String,
    val rawText: String,
    val currency: String? = null,
    val amount: Double? = null,
    val context: String? = null
)

@Serializable
data class ExtractedLink(
    val id: String,
    val url: String,
    val domain: String? = null
)

@Serializable
data class ExtractedCode(
    val id: String,
    val type: String,
    val value: String,
    val format: String? = null
)

@Serializable
data class ExtractedContact(
    val id: String,
    val type: String,
    val value: String
)

@Serializable
data class ExtractedOrderInfo(
    val vendor: String? = null,
    val orderNumber: String? = null,
    val trackingNumber: String? = null,
    val productName: String? = null,
    val returnBy: String? = null,
    val deliveredDate: String? = null
)

@Serializable
data class ScreenshotItem(
    val id: String,
    val imageUri: String,
    val sourceType: String,
    val sourceApp: String? = null,
    val sourceSite: String? = null,
    val createdAt: Long,
    val importedAt: Long,
    val updatedAt: Long,
    val title: String,
    val summary: String,
    val category: ScreenshotCategory,
    val status: ScreenshotStatus,
    val priority: Priority,
    val extractedText: String,
    val confidence: Confidence,
    val detectedDates: List<ExtractedDate> = emptyList(),
    val detectedAmounts: List<ExtractedAmount> = emptyList(),
    val detectedLinks: List<ExtractedLink> = emptyList(),
    val detectedCodes: List<ExtractedCode> = emptyList(),
    val detectedContacts: List<ExtractedContact> = emptyList(),
    val detectedOrderInfo: ExtractedOrderInfo? = null,
    val suggestedAction: String? = null,
    val reminderAt: Long? = null,
    val dueDateText: String? = null,
    val isReceipt: Boolean = false,
    val isTaxRecord: Boolean = false,
    val taxCategory: TaxCategory? = null,
    val needsReview: Boolean = false,
    val isSensitive: Boolean = false,
    val tags: List<String> = emptyList(),
    val userNotes: String = ""
)

@Serializable
data class ScreenVaultSettings(
    val onboardingComplete: Boolean = false,
    val localOnlyProcessing: Boolean = true,
    val cloudAiProcessing: Boolean = false,
    val hideSensitiveScreenshots: Boolean = true,
    val confirmSensitiveSaves: Boolean = true,
    val scanOnOpen: Boolean = false,
    val lastScanAt: Long? = null,
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-5.5",
    val showRawExtractedText: Boolean = false
)
