package com.aichallengekmp.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Провайдер для Ollama (локальные LLM модели)
 *
 * Поддержка:
 * - Qwen2.5, Llama, Mistral и другие модели через Ollama
 * - API: http://localhost:11434/api/chat
 * - НЕ поддерживает function calling (пока)
 */
class OllamaProvider(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434"
) : AIProvider {

    private val logger = LoggerFactory.getLogger(OllamaProvider::class.java)

    override val providerId = "ollama"

    companion object {
        // Доступные модели Ollama
        // Только реально установленные модели (проверяется через `ollama list`)
        private val MODELS = listOf(
            AIModel(
                id = "qwen2.5:14b",
                name = "Qwen 2.5 14B",
                displayName = "Qwen 2.5 14B (локальная)",
                providerId = "ollama",
                maxTokens = 32768,
                supportsSystemPrompt = true
            )
        )
    }

    override suspend fun getSupportedModels(): List<AIModel> {
        logger.debug("📋 Запрос списка поддерживаемых моделей Ollama")

        // Опционально: можно запросить реально установленные модели через /api/tags
        // Пока возвращаем статический список
        return MODELS
    }

    override suspend fun complete(request: CompletionRequest): CompletionResult {
        // Ollama пока не поддерживает function calling в нашей реализации
        if (!request.tools.isNullOrEmpty()) {
            logger.warn("⚠️ Ollama не поддерживает function calling, игнорируем tools")
        }

        return completeChat(request)
    }

    /**
     * Запрос через Ollama Chat API
     */
    private suspend fun completeChat(request: CompletionRequest): CompletionResult {
        logger.info("🤖 Отправка запроса в Ollama")
        logger.debug("   Модель: ${request.modelId}")
        logger.debug("   Температура: ${request.temperature}")
        logger.debug("   Сообщений: ${request.messages.size}")

        // Формируем сообщения для Ollama API (формат OpenAI-совместимый)
        val messages = buildMessages(request)

        val requestBody = OllamaChatRequest(
            model = request.modelId,
            messages = messages,
            options = OllamaOptions(
                temperature = request.temperature,
                num_predict = request.maxTokens,
                top_p = request.topP,
                top_k = request.topK,
                num_ctx = request.numCtx,
                repeat_penalty = request.repeatPenalty,
                seed = request.seed
            ),
            stream = false
        )

        logger.debug("📤 Отправка запроса в Ollama API: $baseUrl/api/chat")

        val rawResponse = try {
            httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                // Увеличенный timeout для первой загрузки модели в память (может занять 30-60 секунд)
                this.timeout {
                    requestTimeoutMillis = 120_000  // 2 минуты
                }
            }.bodyAsText()
        } catch (e: Exception) {
            logger.error("❌ Ошибка при вызове Ollama API: ${e.message}", e)
            throw AIProviderException("Ошибка при обращении к Ollama: ${e.message}. Убедитесь что Ollama запущен (ollama serve)", e)
        }

        logger.debug("RAW OLLAMA RESPONSE (первые 500 символов) = ${rawResponse.take(500)}")

        // Ollama возвращает streaming response построчно, даже при stream=false
        // Нужно парсить каждую строку и собрать полный ответ
        val json = Json { ignoreUnknownKeys = true }
        val fullText = StringBuilder()
        var finalResponse: OllamaChatResponse? = null

        try {
            rawResponse.lines().forEach { line ->
                if (line.isBlank()) return@forEach

                val chunk = json.decodeFromString<OllamaChatResponse>(line)

                // Собираем текст из каждого чанка
                chunk.message.content?.let { fullText.append(it) }

                // Последний чанк содержит статистику токенов
                if (chunk.done) {
                    finalResponse = chunk
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка парсинга streaming ответа Ollama: ${e.message}", e)
            throw AIProviderException("Ошибка при разборе ответа Ollama: ${e.message}", e)
        }

        val text = fullText.toString().takeIf { it.isNotBlank() }
            ?: throw AIProviderException("Пустой ответ от Ollama")

        // Ollama возвращает токены в финальном чанке
        val tokenUsage = TokenUsage(
            inputTokens = finalResponse?.prompt_eval_count ?: 0,
            outputTokens = finalResponse?.eval_count ?: 0,
            totalTokens = (finalResponse?.prompt_eval_count ?: 0) + (finalResponse?.eval_count ?: 0)
        )

        logger.info("✅ Получен ответ от Ollama")
        logger.debug("   Токены: input=${tokenUsage.inputTokens}, output=${tokenUsage.outputTokens}, total=${tokenUsage.totalTokens}")

        return CompletionResult(
            text = text,
            modelId = request.modelId,
            tokenUsage = tokenUsage
        )
    }

    /**
     * Формируем сообщения для Ollama (OpenAI-compatible format)
     */
    private fun buildMessages(request: CompletionRequest): List<OllamaMessage> {
        val messages = mutableListOf<OllamaMessage>()

        // System prompt идет первым сообщением
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            messages += OllamaMessage(role = "system", content = it)
        }

        // Добавляем пользовательские сообщения
        request.messages.forEach { msg ->
            // Пропускаем сообщения с tool calls/results (Ollama их не поддерживает)
            if (msg.toolCallList != null || msg.toolResultList != null) {
                logger.debug("⏭️ Пропускаем сообщение с tool calls/results")
                return@forEach
            }

            val content = msg.content?.takeIf { it.isNotBlank() } ?: return@forEach

            messages += OllamaMessage(
                role = msg.role,
                content = content
            )
        }

        return messages
    }

    // ============= Ollama API Models =============

    @Serializable
    private data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val options: OllamaOptions? = null,
        val stream: Boolean = false
    )

    @Serializable
    private data class OllamaMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class OllamaOptions(
        val temperature: Double,
        val num_predict: Int? = null,       // max tokens
        val top_p: Double? = null,          // nucleus sampling
        val top_k: Int? = null,             // top-k sampling
        val num_ctx: Int? = null,           // context window
        val repeat_penalty: Double? = null, // repeat penalty
        val seed: Int? = null               // random seed
    )

    @Serializable
    private data class OllamaChatResponse(
        val model: String,
        val message: OllamaMessage,
        @SerialName("created_at") val createdAt: String,
        val done: Boolean,

        // Token usage stats
        @SerialName("total_duration") val total_duration: Long? = null,
        @SerialName("load_duration") val load_duration: Long? = null,
        @SerialName("prompt_eval_count") val prompt_eval_count: Int? = null,
        @SerialName("eval_count") val eval_count: Int? = null,
        @SerialName("eval_duration") val eval_duration: Long? = null
    )
}
