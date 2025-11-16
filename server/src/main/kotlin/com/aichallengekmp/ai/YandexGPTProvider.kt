package com.aichallengekmp.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Провайдер для YandexGPT
 */
class YandexGPTProvider(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val folderId: String
) : AIProvider {

    private val logger = LoggerFactory.getLogger(YandexGPTProvider::class.java)

    override val providerId = "yandex"

    companion object {
        private const val API_URL =
            "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

        // Доступные модели YandexGPT
        private val MODELS = listOf(
            AIModel(
                id = "yandexgpt-lite",
                name = "YandexGPT Lite",
                displayName = "YandexGPT Lite (быстрая)",
                providerId = "yandex",
                maxTokens = 8000
            ),
            // Готово к добавлению других моделей:
            // AIModel(
            //     id = "yandexgpt",
            //     name = "YandexGPT",
            //     displayName = "YandexGPT (стандартная)",
            //     providerId = "yandex"
            // )
        )
    }

    override suspend fun getSupportedModels(): List<AIModel> {
        logger.debug("📋 Запрос списка поддерживаемых моделей Yandex")
        return MODELS
    }

    override suspend fun complete(request: CompletionRequest): CompletionResult {
        logger.info("🤖 Отправка запроса в YandexGPT")
        logger.debug("   Модель: ${request.modelId}")
        logger.debug("   Температура: ${request.temperature}")
        logger.debug("   Max tokens: ${request.maxTokens}")
        logger.debug("   Сообщений: ${request.messages.size}")

        val modelUri = folderId

        // Формируем сообщения для Yandex API
        val messages = buildMessages(request)

        val requestBody = YandexCompletionRequest(
            modelUri = modelUri,
            completionOptions = YandexCompletionOptions(
                temperature = request.temperature,
                maxTokens = request.maxTokens.toLong(),
                stream = false
            ),
            messages = messages
        )

        logger.debug("📤 Отправка запроса в Yandex API")

        var raw = try {
            httpClient.post(API_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Api-Key $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                contentType(ContentType.Application.Json)
//                header("Authorization", "Api-Key $apiKey")
//                header("x-folder-id", folderId)
                setBody(requestBody)
            }.bodyAsText()
        }  catch (e: Exception) {
            logger.error("❌ Ошибка при вызове Yandex API: ${e.message}", e)
            throw AIProviderException("Ошибка при обращении к YandexGPT: ${e.message}", e)
        }
        logger.error("RAW YA RESPONSE = $raw")   // <- увидишь точный JSON
        val response = try {
            httpClient.post(API_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Api-Key $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
//                contentType(ContentType.Application.Json)
//                header("Authorization", "Api-Key $apiKey")
//                header("x-folder-id", folderId)
                setBody(requestBody)
            }.body<YandexCompletionResponse>()
        } catch (e: Exception) {
            logger.error("❌ Ошибка при вызове Yandex API: ${e.message}", e)
            throw AIProviderException("Ошибка при обращении к YandexGPT: ${e.message}", e)
        }
        logger.debug("result = {}", response, "result = $response")
response as YandexCompletionResponse
        val text = response.result.alternatives.firstOrNull()?.message?.text
            ?: throw AIProviderException("Пустой ответ от YandexGPT")

        val usage = response.result.usage
        val tokenUsage = TokenUsage(
            inputTokens = usage.inputTextTokens.toIntOrNull() ?: 0,
            outputTokens = usage.completionTokens.toIntOrNull() ?: 0,
            totalTokens = usage.totalTokens.toIntOrNull() ?: 0
        )

        logger.info("✅ Получен ответ от YandexGPT")
        logger.debug("   Токены: input=${tokenUsage.inputTokens}, output=${tokenUsage.outputTokens}, total=${tokenUsage.totalTokens}")

        return CompletionResult(
            text = text,
            modelId = request.modelId,
            tokenUsage = tokenUsage
        )
    }

    private fun buildModelUri(modelId: String): String {
        return folderId
    }

    private fun buildMessages(request: CompletionRequest): List<YandexMessage> {
        val messages = mutableListOf<YandexMessage>()

        // Добавляем system prompt если есть
        if (!request.systemPrompt.isNullOrBlank()) {
            messages.add(YandexMessage(role = "system", text = request.systemPrompt))
        }

        // Добавляем остальные сообщения
        messages.addAll(request.messages.map {
            YandexMessage(role = it.role, text = it.content)
        })

        return messages
    }

// ============= Yandex API Models =============

    @Serializable
    private data class YandexCompletionRequest(
        val modelUri: String,
        val completionOptions: YandexCompletionOptions,
        val messages: List<YandexMessage>
    )

    @Serializable
    private data class YandexCompletionOptions(
        val temperature: Double,
        val maxTokens: Long,
        val stream: Boolean = false
    )

    @Serializable
    private data class YandexMessage(
        val role: String,
        val text: String
    )

    @Serializable
    private data class YandexCompletionResponse(
        val result: YandexResult
    )

    @Serializable
    private data class YandexResult(
        val alternatives: List<YandexAlternative>,
        val usage: YandexUsage,
        val modelVersion: String
    )

    @Serializable
    private data class YandexAlternative(
        val message: YandexMessage,
        val status: String
    )

    @Serializable
    private data class YandexUsage(
        @SerialName("inputTextTokens") val inputTextTokens: String,
        @SerialName("completionTokens") val completionTokens: String,
        @SerialName("totalTokens") val totalTokens: String
    )
}

/**
 * Исключение при работе с AI провайдером
 */
class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
