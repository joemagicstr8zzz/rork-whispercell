package com.rork.whispercell.services

class StartPhraseDetector {
    fun containsStartPhrase(text: String, phrases: List<String>): Boolean = containsPhrase(text, phrases)
}

class StopPhraseDetector {
    fun containsStopPhrase(text: String, phrases: List<String>): Boolean = containsPhrase(text, phrases)
}

private fun containsPhrase(text: String, phrases: List<String>): Boolean {
    val normalizedText: String = text.lowercase().replace(Regex("\\s+"), " ").trim()
    return phrases.any { phrase ->
        val normalizedPhrase: String = phrase.lowercase().replace(Regex("\\s+"), " ").trim()
        normalizedPhrase.isNotBlank() && normalizedText.contains(normalizedPhrase)
    }
}
