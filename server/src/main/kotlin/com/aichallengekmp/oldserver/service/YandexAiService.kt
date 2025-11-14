package local.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import local.config.AppConfig
import local.data.CompletionOptions
import local.data.YandexMessage
import local.data.YandexRequestBody
import local.data.YandexResponse
import local.data.TokenUsage

class YandexAiService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    data class CompletionResult(
        val text: String,
        val tokenUsage: TokenUsage
    )

    suspend fun complete(task: String, maxTokens: Int = 2000): CompletionResult {
        // Проверяем наличие API ключей
        val modelUri = System.getenv(AppConfig.MODEL_URI_ENV)
        val apiKey = System.getenv(AppConfig.API_KEY_ENV)
        
        // Если API ключи не установлены, возвращаем тестовый ответ
        if (modelUri == null || apiKey == null) {
            println("⚠️ API ключи не установлены, возвращаем тестовый ответ")
            return getTestResponse(task, maxTokens)
        }

        val messages = mutableListOf<YandexMessage>()
        messages += YandexMessage("user", task)

        val body = YandexRequestBody(
            modelUri = modelUri,
            messages = messages,
            completionOptions = CompletionOptions(
                temperature = 0.3,
                maxTokens = maxTokens,
                stream = false
            )
        )

        val response: YandexResponse = client.post(AppConfig.YANDEX_URL) {
            headers {
                append(HttpHeaders.Authorization, "Api-Key $apiKey")
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(body)
        }.body()

        val text = response.result.alternatives.firstOrNull()?.message?.text
            ?: error("Empty response from Yandex")

        val usage = response.result.usage
        val tokenUsage = TokenUsage(
            inputTokens = usage.inputTextTokens.toIntOrNull() ?: 0,
            outputTokens = usage.completionTokens.toIntOrNull() ?: 0,
            totalTokens = usage.totalTokens.toIntOrNull() ?: 0
        )

        return CompletionResult(text, tokenUsage)
    }
    
    private fun getTestResponse(task: String, maxTokens: Int): CompletionResult {
        // Приблизительный подсчет токенов (1 токен ≈ 4 символа для русского)
        val inputTokens = task.length / 4
        val outputText = "Тестовый ответ на задачу: $task\n\nЭто симуляция работы агента без реального API."
        val outputTokens = outputText.length / 4

        return CompletionResult(
            text = outputText,
            tokenUsage = TokenUsage(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = inputTokens + outputTokens
            )
        )
    }
}
