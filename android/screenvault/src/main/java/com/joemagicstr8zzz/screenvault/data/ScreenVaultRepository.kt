package com.joemagicstr8zzz.screenvault.data

import android.content.Context
import com.joemagicstr8zzz.screenvault.model.ScreenVaultSettings
import com.joemagicstr8zzz.screenvault.model.ScreenshotItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScreenVaultRepository(context: Context) {
    private val prefs = context.getSharedPreferences("screenvault_store", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun loadItems(): List<ScreenshotItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ScreenshotItem>>(raw) }.getOrDefault(emptyList())
    }

    fun saveItems(items: List<ScreenshotItem>) {
        prefs.edit().putString(KEY_ITEMS, json.encodeToString(items)).apply()
    }

    fun loadSettings(): ScreenVaultSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return ScreenVaultSettings()
        return runCatching { json.decodeFromString<ScreenVaultSettings>(raw) }.getOrDefault(ScreenVaultSettings())
    }

    fun saveSettings(settings: ScreenVaultSettings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ITEMS = "items_v1"
        private const val KEY_SETTINGS = "settings_v1"
    }
}
