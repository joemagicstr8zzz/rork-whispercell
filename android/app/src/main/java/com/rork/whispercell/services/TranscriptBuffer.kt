package com.rork.whispercell.services

class TranscriptBuffer {
    private val chunks: MutableList<String> = mutableListOf()

    fun append(text: String) {
        if (text.isNotBlank()) chunks.add(text.trim())
    }

    fun combined(): String = chunks.joinToString(separator = " ").replace(Regex("\\s+"), " ").trim()

    fun clear() { chunks.clear() }
}
