package com.aichallengekmp.ai

import com.aichallengekmp.tools.ToolDefinition
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Провайдер для YandexGPT
 */
class YandexGPTProvider(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val folderId: String,
    private val trackerTools: com.aichallengekmp.tools.TrackerToolsService? = null
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
        // Если есть tools - используем function calling
        if (!request.tools.isNullOrEmpty()) {
            return completeWithFunctionCalling(request)
        }

        // Обычный запрос без инструментов
        return completeSimple(request)
    }

    /**
     * Обычный запрос без function calling
     */
    private suspend fun completeSimple(request: CompletionRequest): CompletionResult {
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
            messages = messages,
            tools = request.tools?.map { convertToYandexTool(it) }
        )

        logger.debug("📤 Отправка запроса в Yandex API")

        val raw = try {
            httpClient.post(API_URL) {
                headers {
                    append(HttpHeaders.Authorization, "Api-Key $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()
        } catch (e: Exception) {
            logger.error("❌ Ошибка при вызове Yandex API: ${e.message}", e)
            throw AIProviderException("Ошибка при обращении к YandexGPT: ${e.message}", e)
        }
        logger.debug("RAW YA RESPONSE = $raw")

        val response = try {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<YandexCompletionResponse>(raw)
        } catch (e: Exception) {
            logger.error("❌ Ошибка парсинга ответа Yandex API: ${e.message}", e)
            throw AIProviderException("Ошибка при разборе ответа YandexGPT: ${e.message}", e)
        }

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

        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            messages += YandexMessage(role = "system", text = it)
        }

        request.messages.forEach { msg ->

            // 🟢 Нормализация: если есть вызовы инструмента — text = null
            val safeText =
                if (msg.toolCallList != null) null
                else msg.content.takeIf { !it.isNullOrBlank() }

            // 🛑 Игнорируем полностью пустые assistant (text==null && no tools && no results)
            val completelyEmptyAssistant =
                msg.role == "assistant" &&
                        safeText == null &&
                        msg.toolCallList == null &&
                        msg.toolResultList == null

            if (completelyEmptyAssistant) return@forEach

            messages += YandexMessage(
                role = msg.role,
                text = safeText,
                toolCallList = msg.toolCallList as ToolCallList?,
                toolResultList = msg.toolResultList as ToolResultList?
            )
        }

        return messages
    }


// ============= Yandex API Models =============

    @Serializable
    private data class YandexCompletionRequest(
        val modelUri: String,
        val completionOptions: YandexCompletionOptions,
        val messages: List<YandexMessage>,
        val tools: List<YandexTool>? = null
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
        val text: String? = null,
        val toolCallList: ToolCallList? = null,
        val toolResultList: ToolResultList? = null
    )

    @Serializable
    private data class ToolResultList(
        val toolResults: List<ToolResult>
    )

    @Serializable
    private data class ToolResult(
        val functionResult: FunctionResult
    )

    @Serializable
    private data class FunctionResult(
        val name: String,
        val content: String
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
        val status: String,
        val toolCallList: ToolCallList? = null
    )

    @Serializable
    private data class ToolCallList(
        val toolCalls: List<ToolCall>
    )

    @Serializable
    private data class ToolCall(
        val functionCall: FunctionCall
    )

    @Serializable
    private data class FunctionCall(
        val name: String,
        val arguments: JsonElement
    )

    @Serializable
    private data class YandexUsage(
        @SerialName("inputTextTokens") val inputTextTokens: String,
        @SerialName("completionTokens") val completionTokens: String,
        @SerialName("totalTokens") val totalTokens: String
    )

    @Serializable
    private data class YandexTool(
        val function: YandexFunction
    )

    @Serializable
    private data class YandexFunction(
        val name: String,
        val description: String,
        val parameters: JsonElement? = null
    )

    /**
     * Запрос с function calling - обрабатывает вызовы функций
     */
    private suspend fun completeWithFunctionCalling(request: CompletionRequest): CompletionResult {
        logger.info("🔧 Запуск запроса с function calling")

        var currentMessages = request.messages.toMutableList()
        var totalInputTokens = 0
        var totalOutputTokens = 0
        val maxIterations = 5
        var iteration = 0

        while (iteration < maxIterations) {
            logger.debug("🔄 Function calling итерация ${iteration + 1}")

            // Отправляем запрос с tools
            val tempRequest = request.copy(messages = currentMessages)
            val modelUri = folderId
            val messages = buildMessages(tempRequest)//.filter { it.role.contains("function").not() }

            val requestBody = YandexCompletionRequest(
                modelUri = modelUri,
                completionOptions = YandexCompletionOptions(
                    temperature = request.temperature,
                    maxTokens = request.maxTokens.toLong(),
                    stream = false
                ),
                messages = messages,
                tools = request.tools?.map { convertToYandexTool(it) }
            )
            logger.info("📦 REQUEST = $request")
            logger.info("📦 requestBody = $requestBody")
            logger.info(
                "Финальный запрос: {}",
                Json.encodeToString(YandexCompletionRequest.serializer(), requestBody)
            )


            // Сначала получаем RAW ответ
            val rawResponse = try {
                httpClient.post(API_URL) {
                    headers {
                        append(HttpHeaders.Authorization, "Api-Key $apiKey")
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.bodyAsText()
            } catch (e: Exception) {
                logger.error("❌ Ошибка при вызове Yandex API: ${e.message}", e)
                throw AIProviderException("Ошибка при обращении к YandexGPT: ${e.message}", e)
            }

            logger.info("📦 RAW RESPONSE (iteration $iteration): $rawResponse")

            val response = try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<YandexCompletionResponse>(rawResponse)
            } catch (e: Exception) {
                logger.error("❌ Ошибка парсинга ответа: ${e.message}", e)
                throw AIProviderException("Ошибка парсинга: ${e.message}", e)
            }

            val usage = response.result.usage
            totalInputTokens += usage.inputTextTokens.toIntOrNull() ?: 0
            totalOutputTokens += usage.completionTokens.toIntOrNull() ?: 0

            val alternative = response.result.alternatives.firstOrNull()
                ?: throw AIProviderException("Пустой ответ от YandexGPT")

            // Может прийти toolCallList либо на уровне message, либо на уровне alternative
            val toolCallList = alternative.message.toolCallList ?: alternative.toolCallList

            // Проверяем есть ли вызов функции
            val hasToolCalls = toolCallList?.toolCalls?.isNotEmpty() == true
            logger.info("🔍 Проверка toolCallList: ${toolCallList}, hasToolCalls=$hasToolCalls")

            if (hasToolCalls) {
                logger.info("🔧 YandexGPT запросил вызов ${toolCallList!!.toolCalls.size} функций")

                // Выполняем все запрошенные функции
                val toolResults = toolCallList.toolCalls.map { toolCall ->
                    val functionName = toolCall.functionCall.name
                    val arguments = toolCall.functionCall.arguments

                    logger.info("➡️ Вызов функции: $functionName")

                    // Вызываем функцию через TrackerToolsService
                    val result = executeFunction(functionName, arguments)

                    ToolResult(
                        functionResult = FunctionResult(
                            name = functionName,
                            content = result
                        )
                    )
                }

                // Добавляем запрос на вызов функции в историю
                currentMessages.add(
                    AIMessage(
                        role = "assistant",
                        content = null,  // Пустой текст, т.к. есть toolCallList
                        toolCallList = toolCallList
                    )
                )

                // Добавляем результаты вызова функций
                currentMessages.add(
                    AIMessage(
                        role = "assistant",
                        content = null,
                        toolResultList = ToolResultList(toolResults)
                    )
                )

                // Продолжаем цикл - отправляем запрос с результатами
                logger.info("🔄 Продолжаем цикл, iteration=$iteration")
                iteration++
                logger.info("🔄 После increment, iteration=$iteration")
                continue
            }

            logger.info("ℹ️ НЕТ вызова функций, проверяем text")

            // Если нет вызова функции - возвращаем результат
            val text =
                alternative.message.text ?: throw AIProviderException("Пустой ответ от YandexGPT")

            logger.info("✅ Получен финальный ответ от YandexGPT")

            return CompletionResult(
                text = text,
                modelId = request.modelId,
                tokenUsage = TokenUsage(
                    inputTokens = totalInputTokens,
                    outputTokens = totalOutputTokens,
                    totalTokens = totalInputTokens + totalOutputTokens
                )
            )

            iteration++
        }

        throw AIProviderException("Превышено максимальное количество итераций function calling")
    }

    /**
     * Выполнить функцию (инструмент)
     */
    private suspend fun executeFunction(name: String, arguments: JsonElement): String {
        if (trackerTools == null) {
            return "Ошибка: TrackerToolsService не подключен"
        }

        // Парсим аргументы
        val args = if (arguments is JsonObject) {
            arguments.entries.associate { it.key to it.value.toString().trim('"') }
        } else {
            emptyMap()
        }

        return trackerTools.executeTool(name, args)
    }

    private fun convertToYandexTool(tool: ToolDefinition): YandexTool {
        return YandexTool(
            function = YandexFunction(
                name = tool.name,
                description = tool.description,
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        tool.parameters.forEach { (key, value) ->
                            put(key, buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive(value.toString()))
                            })
                        }
                    })
                    if (tool.parameters.isNotEmpty()) {
                        putJsonArray("required") {
                            tool.parameters.keys.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
            )
        )
    }
}

/**
 * Исключение при работе с AI провайдером
 */
class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
