package com.rork.whispercell.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout

object NetworkClient {
    val http: HttpClient by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 35_000
                connectTimeoutMillis = 12_000
                socketTimeoutMillis = 35_000
            }
        }
    }

    fun close() {
        http.close()
    }
}
