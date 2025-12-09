package com.aichallengekmp.offline

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Клиент для прямой работы с Ollama в офлайн режиме
 * БЕЗ сервера, БЕЗ сложной логики
 */
class OfflineChatClient(
    private val ollamaUrl: String = "http://localhost:11434"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 2 минуты для загрузки модели
        }
    }

    // ============= Ollama API Models =============

    @Serializable
    private data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = false
    )

    @Serializable
    private data class OllamaMessage(
        val role: String,
        val content: String? = null
    )

    @Serializable
    private data class OllamaChatResponse(
        val model: String,
        val message: OllamaMessage,
        @SerialName("created_at") val createdAt: String,
        val done: Boolean
    )

    /**
     * Проверка доступности Ollama
     */
    suspend fun checkAvailable(): Boolean {
        return try {
            client.get("$ollamaUrl/api/tags")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Отправка сообщения и получение ответа
     */
    suspend fun sendMessage(
        message: String,
        history: List<Pair<String, String>> = emptyList() // role to content
    ): String {
        val messages = history.map { (role, content) ->
            OllamaMessage(role = role, content = content)
        } + OllamaMessage(role = "user", content = message)

        val requestBody = OllamaChatRequest(
            model = "qwen2.5:14b",
            messages = messages,
            stream = false
        )

        val rawResponse = client.post("$ollamaUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.bodyAsText()

        // Парсим NDJSON ответ (как в OllamaProvider)
        // Ollama возвращает streaming response построчно, даже при stream=false
        val fullText = StringBuilder()
        rawResponse.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val chunk = json.decodeFromString<OllamaChatResponse>(line)
            chunk.message.content?.let { fullText.append(it) }
        }

        return fullText.toString().takeIf { it.isNotBlank() }
            ?: throw Exception("Пустой ответ от Ollama")
    }
}
