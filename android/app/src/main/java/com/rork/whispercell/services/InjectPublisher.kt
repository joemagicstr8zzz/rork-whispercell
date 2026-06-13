package com.rork.whispercell.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Publishes selected values to Inject by posting a value field to the fixed selection endpoint. */
class InjectPublisher {
    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
    }

    private val json: Json = Json { encodeDefaults = true }

    fun endpoint(): String = INJECT_SELECTION_ENDPOINT

    suspend fun publish(value: String, retryOnce: Boolean): Result<String> {
        val cleanValue: String = value.trim()
        if (cleanValue.isBlank()) return Result.failure(IllegalArgumentException("Value is required"))

        val attemptCount: Int = if (retryOnce) 2 else 1
        var lastError: Throwable? = null
        repeat(attemptCount) {
            val result: Result<String> = runCatching { postFormValue(cleanValue) }
                .recoverCatching { formError ->
                    lastError = formError
                    postJsonValue(cleanValue)
                }
            result.fold(
                onSuccess = { responseText -> return Result.success(responseText) },
                onFailure = { throwable -> lastError = throwable }
            )
        }
        return Result.failure(lastError ?: IllegalStateException("Inject publish failed"))
    }

    fun sanitizeInjectCode(input: String): String = input.filter { it.isLetterOrDigit() }.take(7)

    private suspend fun postJsonValue(value: String): String {
        val response = client.post(INJECT_SELECTION_ENDPOINT) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(json.encodeToString(InjectValuePayload(value = value)))
        }
        if (!response.status.isSuccess()) error("Inject returned ${response.status.value}")
        return response.body<String>().ifBlank { "Published" }
    }

    private suspend fun postFormValue(value: String): String {
        val response = client.post(INJECT_SELECTION_ENDPOINT) {
            accept(ContentType.Application.Json)
            setBody(FormDataContent(Parameters.build { append("value", value) }))
        }
        if (!response.status.isSuccess()) error("Inject returned ${response.status.value}")
        return response.body<String>().ifBlank { "Published" }
    }

    private companion object {
        const val INJECT_SELECTION_ENDPOINT: String = "https://11z.co/_w/selection"
    }
}

@Serializable
private data class InjectValuePayload(
    val value: String
)
