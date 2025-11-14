package local.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val task: String,
    val mode: Mode? = null, // Делаем опциональным для эксперимента
    val systemPrompt: String? = null
)

@Serializable
enum class Mode(val value: String) {
    @SerialName("direct")
    DIRECT("direct")
    // Оставляем только DIRECT для эксперимента
}

@Serializable
data class ChatMessage(
    val role: String,
    val text: String
)

@Serializable
data class YandexMessage(
    val role: String,
    val text: String
)

@Serializable
data class YandexRequestBody(
    val modelUri: String,
    val messages: List<YandexMessage>,
    val completionOptions: CompletionOptions,
    val jsonSchema: JsonSchema? = null
)

@Serializable
data class CompletionOptions(
    val temperature: Double = 0.2,
    val maxTokens: Int = 2000,
    val stream: Boolean = false
)

@Serializable
data class JsonSchema(
    val schema: String
)

@Serializable
data class YandexAlternative(
    val message: YandexMessage,
    val status: String
)

@Serializable
data class YandexUsage(
    val inputTextTokens: String,
    val completionTokens: String,
    val totalTokens: String
)

@Serializable
data class YandexResult(
    val alternatives: List<YandexAlternative>,
    val usage: YandexUsage,
    val modelVersion: String
)

@Serializable
data class YandexResponse(
    val result: YandexResult
)

@Serializable
data class ChatResponse(
    val mode: String,
    val answer: String
)

@Serializable
data class CompareResponse(
    val task: String,
    val modes: Map<String, String>
)

@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

@Serializable
data class TaskResponse(
    val task: String,
    val answer: String,
    val tokenUsage: TokenUsage,
    val requestType: String // "short", "long", "exceeds_limit"
)

@Serializable
data class TokenComparisonResponse(
    val shortRequest: TaskResponse,
    val longRequest: TaskResponse,
    val exceedingRequest: TaskResponse,
    val analysis: String
)

// Модели для работы с диалогами
@Serializable
data class DialogMessage(
    val role: String, // "user" или "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class DialogRequest(
    val sessionId: String,
    val message: String
)

@Serializable
data class DialogResponse(
    val sessionId: String,
    val message: String,
    val answer: String,
    val tokenUsage: TokenUsage,
    val messageCount: Int,
    val compressionApplied: Boolean,
    val summaryGenerated: Boolean = false
)

@Serializable
data class DialogSession(
    val sessionId: String,
    val messages: MutableList<DialogMessage> = mutableListOf(),
    var summary: String? = null,
    var lastCompressionAt: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class CompressionStats(
    val sessionId: String,
    val totalMessages: Int,
    val messagesBeforeCompression: Int,
    val messagesAfterCompression: Int,
    val tokensBeforeCompression: TokenUsage,
    val tokensAfterCompression: TokenUsage,
    val tokensSaved: Int,
    val compressionRatio: Double,
    val summary: String
)

@Serializable
data class SessionInfo(
    val sessionId: String,
    val messageCount: Int,
    val hasSummary: Boolean,
    val lastCompressionAt: Int,
    val createdAt: Long,
    val updatedAt: Long
)
