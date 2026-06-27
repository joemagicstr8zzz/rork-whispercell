package com.joemagicstr8zzz.screenvault.data

import com.joemagicstr8zzz.screenvault.model.ScreenshotItem

object SampleData {
    fun create(): List<ScreenshotItem> {
        return listOf(
            ScreenshotAnalyzer.analyze(
                imageUri = "sample://amazon-order",
                sourceType = "sample",
                sourceHint = "Amazon",
                visibleText = """
                    Amazon order #503-8291472-1923410
                    Item: Anker 737 Power Bank
                    Ordered on May 1, 2025
                    Delivered on May 4, 2025
                    Price: ¥17,980
                    Return window closes on May 18, 2025
                    Need to decide if keeping it.
                """.trimIndent()
            ),
            ScreenshotAnalyzer.analyze(
                imageUri = "sample://dental-appointment",
                sourceType = "sample",
                visibleText = """
                    Dental Appointment
                    June 20, 2026 at 10:00 AM
                    Please arrive 15 minutes early.
                    Please confirm appointment before the visit.
                """.trimIndent()
            ),
            ScreenshotAnalyzer.analyze(
                imageUri = "sample://home-depot-receipt",
                sourceType = "sample",
                sourceHint = "Home Depot",
                visibleText = """
                    THE HOME DEPOT
                    Receipt
                    05/03/2025 11:42 AM
                    Lumber 2x4x8 ¥2,180
                    Screws 1lb Box ¥680
                    Paint Brush ¥420
                    Drop Cloth ¥1,540
                    Subtotal ¥4,820
                    Total ¥4,820
                    Possible work expense. Needs review for tax records.
                """.trimIndent()
            ),
            ScreenshotAnalyzer.analyze(
                imageUri = "sample://qr-code",
                sourceType = "sample",
                barcodeValues = listOf("https://example.com/checkin"),
                visibleText = "Event Check-In QR Code\nhttps://example.com/checkin"
            ),
            ScreenshotAnalyzer.analyze(
                imageUri = "sample://project-idea",
                sourceType = "sample",
                visibleText = """
                    Idea: AI receipt and screenshot organizer
                    Remember this product concept.
                    Features: tags, reminders, tax folders, search.
                """.trimIndent()
            ),
            ScreenshotAnalyzer.analyze(
                imageUri = "sample://electric-bill",
                sourceType = "sample",
                visibleText = """
                    Electric Bill
                    Payment due in 3 days
                    Amount due: ¥7,860
                    Due May 15, 2025
                    Please pay before due date to avoid late fee.
                """.trimIndent()
            )
        ).mapIndexed { index, item ->
            item.copy(id = "sample_${index + 1}", importedAt = System.currentTimeMillis() - index * 3600000L)
        }
    }
}
