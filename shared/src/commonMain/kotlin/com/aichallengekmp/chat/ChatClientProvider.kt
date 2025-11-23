package com.aichallengekmp.chat

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Провайдер для создания HTTP клиента и репозитория
 */
object ChatClientProvider {

    const val BASE_URL = "http://localhost:8080"

    private val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(SSE)
            // Отключаем таймауты для долгоживущих соединений (SSE)
            install(HttpTimeout) {
                // бесконечный таймаут
                requestTimeoutMillis = Int.MAX_VALUE.toLong()
            }

            install(ContentNegotiation) {
                register(ContentType.Application.Json, KotlinxSerializationConverter(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }))
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = false
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    val repository: ChatRepository by lazy {
        KtorChatRepository(httpClient, BASE_URL)
    }
}
