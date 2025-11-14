package local.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import local.config.AppConfig
import local.data.*

interface ReasoningRepository {
    suspend fun solveTask(task: String): Result<TaskResponse>
    suspend fun compareTokens(): Result<TokenComparisonResponse>
    suspend fun sendDialogMessage(sessionId: String, message: String): Result<DialogResponse>
    suspend fun getCompressionStats(sessionId: String): Result<CompressionStats>
    suspend fun getCompressionAnalysis(sessionId: String): Result<CompressionAnalysisResponse>
    suspend fun getSessionInfo(sessionId: String): Result<SessionInfo>
    suspend fun deleteSession(sessionId: String): Result<Boolean>
}

class ReasoningRepositoryImpl(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) : ReasoningRepository {
    
    override suspend fun solveTask(task: String): Result<TaskResponse> {
        return try {
            val request = ChatRequest(task = task, mode = Mode.DIRECT)
            val requestJson = json.encodeToString(ChatRequest.serializer(), request)
            
            println("🎯 Выполнение задачи: $requestJson")

            val response = client.post("${AppConfig.BASE_URL}/chat/solve") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(requestJson)
            }
            
            println("📥 Статус: ${response.status}")

            if (!response.status.isSuccess()) {
                val errorText = "HTTP ${response.status.value}: ${response.status.description}"
                return Result.failure(Exception(errorText))
            }
            
            val responseBody = response.body<String>()
            println("📥 Результат: ${responseBody.take(200)}...")

            val parsedResponse = json.decodeFromString(TaskResponse.serializer(), responseBody)
            Result.success(parsedResponse)
            
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    override suspend fun compareTokens(): Result<TokenComparisonResponse> {
        return try {
            println("🔬 Запуск сравнения токенов")

            val response = client.post("${AppConfig.BASE_URL}/chat/compare-tokens") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            }

            println("📥 Статус: ${response.status}")

            if (!response.status.isSuccess()) {
                val errorText = "HTTP ${response.status.value}: ${response.status.description}"
                return Result.failure(Exception(errorText))
            }

            val responseBody = response.body<String>()
            println("📥 Результат сравнения: ${responseBody.take(200)}...")

            val parsedResponse = json.decodeFromString(TokenComparisonResponse.serializer(), responseBody)
            Result.success(parsedResponse)

        } catch (e: Exception) {
            println("❌ Ошибка сравнения: ${e.message}")
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    override suspend fun sendDialogMessage(sessionId: String, message: String): Result<DialogResponse> {
        return try {
            val request = DialogRequest(sessionId = sessionId, message = message)
            val requestJson = json.encodeToString(DialogRequest.serializer(), request)

            println("💬 Отправка диалогового сообщения: $requestJson")

            val response = client.post("${AppConfig.BASE_URL}/chat/dialog") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(requestJson)
            }

            println("📥 Статус: ${response.status}")

            if (!response.status.isSuccess()) {
                val errorText = "HTTP ${response.status.value}: ${response.status.description}"
                return Result.failure(Exception(errorText))
            }

            val responseBody = response.body<String>()
            val parsedResponse = json.decodeFromString(DialogResponse.serializer(), responseBody)
            Result.success(parsedResponse)

        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    override suspend fun getCompressionStats(sessionId: String): Result<CompressionStats> {
        return try {
            val response = client.get("${AppConfig.BASE_URL}/chat/compression-stats/$sessionId")

            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}"))
            }

            val responseBody = response.body<String>()
            val parsedResponse = json.decodeFromString(CompressionStats.serializer(), responseBody)
            Result.success(parsedResponse)

        } catch (e: Exception) {
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    override suspend fun getCompressionAnalysis(sessionId: String): Result<CompressionAnalysisResponse> {
        return try {
            val response = client.get("${AppConfig.BASE_URL}/chat/analyze-compression/$sessionId")

            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}"))
            }

            val responseBody = response.body<String>()
            val parsedResponse = json.decodeFromString(CompressionAnalysisResponse.serializer(), responseBody)
            Result.success(parsedResponse)

        } catch (e: Exception) {
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    override suspend fun getSessionInfo(sessionId: String): Result<SessionInfo> {
        return try {
            val response = client.get("${AppConfig.BASE_URL}/chat/session-info/$sessionId")

            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}"))
            }

            val responseBody = response.body<String>()
            val parsedResponse = json.decodeFromString(SessionInfo.serializer(), responseBody)
            Result.success(parsedResponse)

        } catch (e: Exception) {
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Boolean> {
        return try {
            val response = client.delete("${AppConfig.BASE_URL}/chat/session/$sessionId")
            Result.success(response.status.isSuccess())
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }
}
