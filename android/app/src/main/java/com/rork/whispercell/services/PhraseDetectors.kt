package com.rork.whispercell.services

class StartPhraseDetector(
    private val phoneticEngine: PhoneticEngine = PhoneticEngine()
) {
    fun containsStartPhrase(text: String, phrases: List<String>): Boolean = phoneticEngine.containsPhrase(text, phrases)
}

class StopPhraseDetector(
    private val phoneticEngine: PhoneticEngine = PhoneticEngine()
) {
    fun containsStopPhrase(text: String, phrases: List<String>): Boolean = phoneticEngine.containsPhrase(text, phrases)
}
