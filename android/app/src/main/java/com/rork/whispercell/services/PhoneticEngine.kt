package com.rork.whispercell.services

import org.apache.commons.codec.language.DoubleMetaphone
import kotlin.math.min

class PhoneticEngine {
    private val metaphone = DoubleMetaphone().apply { maxCodeLen = 8 }

    fun soundsLike(a: String, b: String): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        val distance = levenshtein(left, right)
        val tolerance = when {
            right.length <= 4 -> 1
            right.length <= 8 -> 2
            else -> 3
        }
        if (distance <= tolerance) return true
        val leftCode = metaphone.doubleMetaphone(left)
        val rightCode = metaphone.doubleMetaphone(right)
        return leftCode.isNotBlank() && leftCode == rightCode
    }

    fun containsPhrase(text: String, phrases: List<String>): Boolean {
        val normalizedText = normalize(text)
        if (normalizedText.isBlank()) return false
        return phrases.any { phrase ->
            val normalizedPhrase = normalize(phrase)
            normalizedPhrase.isNotBlank() && (
                normalizedText.contains(normalizedPhrase) ||
                    slidingWindows(normalizedText, normalizedPhrase.wordCount()).any { window -> soundsLike(window, normalizedPhrase) }
            )
        }
    }

    private fun slidingWindows(text: String, targetWordCount: Int): List<String> {
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val size = targetWordCount.coerceAtLeast(1)
        val nearbySizes = listOf(size - 1, size, size + 1).filter { it > 0 }.distinct()
        return nearbySizes.flatMap { windowSize ->
            if (words.size < windowSize) emptyList() else (0..words.size - windowSize).map { index -> words.drop(index).take(windowSize).joinToString(" ") }
        }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9\\s']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.wordCount(): Int = split(" ").count { it.isNotBlank() }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = costs[0]
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                costs[j] = min(
                    min(costs[j] + 1, costs[j - 1] + 1),
                    previous + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                previous = temp
            }
        }
        return costs[b.length]
    }
}
