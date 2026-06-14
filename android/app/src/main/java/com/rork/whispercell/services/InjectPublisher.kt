package com.rork.whispercell.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class InjectPublisher(
    private val client: HttpClient = NetworkClient.http
) {
    fun sanitizeInjectCode(input: String): String = input.filter { it.isLetterOrDigit() }.take(7)

    fun endpoint(injectCode: String = ""): String {
        val cleanCode = sanitizeInjectCode(injectCode)
        return if (cleanCode.isBlank()) PLACEHOLDER_ENDPOINT else "https://11z.co/_w/$cleanCode/selection"
    }

    fun publishUrl(injectCode: String, value: String): String = endpoint(injectCode)

    suspend fun publish(injectCode: String, value: String, retryOnce: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val cleanCode = sanitizeInjectCode(injectCode)
        val cleanValue = value.trim()
        if (cleanCode.isBlank()) return@withContext Result.failure(IllegalArgumentException("Code is required"))
        if (cleanValue.isBlank()) return@withContext Result.failure(IllegalArgumentException("Value is required"))
        postWithRetry(endpoint(cleanCode), cleanValue, retryOnce)
    }

    private suspend fun postWithRetry(endpoint: String, value: String, retryOnce: Boolean): Result<String> {
        val attempts = if (retryOnce) 2 else 1
        var lastError: Throwable? = null
        repeat(attempts) {
            val result = runCatching { postValue(endpoint, value) }
            result.fold(
                onSuccess = { return Result.success(it) },
                onFailure = { lastError = it }
            )
        }
        return Result.failure(lastError ?: IllegalStateException("Publish failed"))
    }

    private suspend fun postValue(endpoint: String, value: String): String {
        val body = buildJsonObject { put("value", value) }.toString()
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) error("Endpoint returned ${response.status.value}")
        return response.body<String>().ifBlank { "Published" }
    }

    private companion object {
        const val PLACEHOLDER_ENDPOINT = "https://11z.co/_w/{INJECT_CODE}/selection"
    }
}
