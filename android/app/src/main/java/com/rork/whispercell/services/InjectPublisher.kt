package com.rork.whispercell.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.net.URLEncoder

/** Publishes selected values to Inject using the public GET value parameter. */
class InjectPublisher {
    private val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
    }

    fun buildUrl(injectCode: String, value: String): String {
        val cleanCode: String = sanitizeInjectCode(injectCode)
        val encodedValue: String = URLEncoder.encode(value, "UTF-8")
        return "https://11z.co/_w/$cleanCode/selection?value=$encodedValue"
    }

    suspend fun publish(injectCode: String, value: String): Result<String> {
        val cleanCode: String = sanitizeInjectCode(injectCode)
        if (cleanCode.isBlank()) return Result.failure(IllegalArgumentException("Inject code is required"))
        if (value.isBlank()) return Result.failure(IllegalArgumentException("Value is required"))
        val url: String = buildUrl(cleanCode, value)
        return runCatching {
            val response = client.get(url)
            if (!response.status.isSuccess()) error("Inject returned ${response.status.value}")
            response.body<String>().ifBlank { "Published" }
        }
    }

    fun sanitizeInjectCode(input: String): String = input.filter { it.isLetterOrDigit() }.take(7)
}
