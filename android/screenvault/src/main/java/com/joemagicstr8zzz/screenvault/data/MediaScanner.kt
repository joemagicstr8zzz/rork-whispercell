package com.joemagicstr8zzz.screenvault.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.joemagicstr8zzz.screenvault.model.ScreenshotItem

object MediaScanner {
    fun scanRecentScreenshots(context: Context, knownUris: Set<String>): List<ScreenshotItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val results = mutableListOf<ScreenshotItem>()

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            var inspected = 0

            while (cursor.moveToNext() && inspected < 80 && results.size < 30) {
                inspected++
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn).orEmpty()
                val relativePath = cursor.getString(pathColumn).orEmpty()
                val createdAt = cursor.getLong(dateColumn).takeIf { it > 0L }?.times(1000L) ?: System.currentTimeMillis()
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val lower = "$displayName $relativePath".lowercase()
                val likelyScreenshot = lower.contains("screenshot") || lower.contains("screen") || lower.contains("capture")

                if (uri !in knownUris && likelyScreenshot) {
                    results += ScreenshotAnalyzer.analyze(
                        imageUri = uri,
                        sourceType = "screenshot",
                        visibleText = "",
                        createdAt = createdAt,
                        sourceHint = displayName
                    )
                }
            }
        }
        return results
    }
}
