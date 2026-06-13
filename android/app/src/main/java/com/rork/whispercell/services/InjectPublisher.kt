package com.rork.whispercell.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import java.net.URLEncoder

/** Publishes selected values to Inject using the code-based selection URL. */
class InjectPublisher {
    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
    }

    fun sanitizeInjectCode(input: String): String = input.filter { it.isLetterOrDigit() }.take(7)

    fun endpoint(injectCode: String = ""): String {
        val cleanCode = sanitizeInjectCode(injectCode)
        return if (cleanCode.isBlank()) {
            LEGACY_SELECTION_ENDPOINT
        } else {
            "https://11z.co/_w/$cleanCode/selection"
        }
    }

    fun publishUrl(injectCode: String, value: String): String {
        val encodedValue = URLEncoder.encode(value.trim(), "UTF-8")
        return "${endpoint(injectCode)}?value=$encodedValue"
    }

    suspend fun publish(injectCode: String, value: String, retryOnce: Boolean = true): Result<String> {
        val cleanCode = sanitizeInjectCode(injectCode)
        val cleanValue = value.trim()
        if (cleanCode.isBlank()) return Result.failure(IllegalArgumentException("Inject code is required"))
        if (cleanValue.isBlank()) return Result.failure(IllegalArgumentException("Value is required"))
        return publishUrlWithRetry(publishUrl(cleanCode, cleanValue), retryOnce)
    }

    /** Backward-compatible preview path for existing ViewModel calls until the Inject Code UI is wired. */
    suspend fun publish(value: String, retryOnce: Boolean): Result<String> {
        val cleanValue = value.trim()
        if (cleanValue.isBlank()) return Result.failure(IllegalArgumentException("Value is required"))
        return publishUrlWithRetry(publishUrl("", cleanValue), retryOnce)
    }

    private suspend fun publishUrlWithRetry(url: String, retryOnce: Boolean): Result<String> {
        val attempts = if (retryOnce) 2 else 1
        var lastError: Throwable? = null
        repeat(attempts) {
            val result = runCatching { getValueUrl(url) }
            result.fold(
                onSuccess = { return Result.success(it) },
                onFailure = { lastError = it }
            )
        }
        return Result.failure(lastError ?: IllegalStateException("Inject publish failed"))
    }

    private suspend fun getValueUrl(url: String): String {
        val response = client.get(url) {
            accept(ContentType.Text.Plain)
        }
        if (!response.status.isSuccess()) error("Inject returned ${response.status.value}")
        return response.body<String>().ifBlank { "Published" }
    }

    private companion object {
        const val LEGACY_SELECTION_ENDPOINT = "https://11z.co/_w/selection"
    }
}
