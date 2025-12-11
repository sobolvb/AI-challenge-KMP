package com.aichallengekmp.models

import kotlinx.serialization.Serializable

// ============= Requests =============

@Serializable
data class CreateSessionRequest(
    val name: String,
    val initialMessage: String,
    val settings: SessionSettingsDto
)

@Serializable
data class SendMessageRequest(
    val sessionId: String,
    val message: String
)

@Serializable
data class UpdateSettingsRequest(
    val settings: SessionSettingsDto
)

// ============= Responses =============

@Serializable
data class SessionListItem(
    val id: String,
    val name: String,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val totalMessages: Int
)

@Serializable
data class SessionDetailResponse(
    val id: String,
    val name: String,
    val messages: List<MessageDto>,
    val settings: SessionSettingsDto,
    val compressionInfo: CompressionInfoDto?
)

@Serializable
data class RagSourceDto(
    val sourceId: String,
    val chunkIndex: Int,
    val score: Double,
    val chunkText: String
)

@Serializable
data class MessageDto(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val modelId: String?,
    val modelName: String?,
    val tokenUsage: TokenUsageDto?,
    val timestamp: Long,
    val ragSources: List<RagSourceDto>? = null
)

@Serializable
data class TokenUsageDto(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

@Serializable
data class SessionSettingsDto(
    val modelId: String = "yandexgpt-lite",
    val temperature: Double = 0.6,
    val maxTokens: Int = 2000,
    val compressionThreshold: Int = 20,
    val systemPrompt: String? = "выполни задачу",
    // Advanced LLM parameters
    val topP: Double? = null,           // Nucleus sampling (0.0 - 1.0)
    val topK: Int? = null,              // Top-K sampling (1 - 100)
    val numCtx: Int? = null,            // Context window size (e.g., 2048, 4096, 32768)
    val repeatPenalty: Double? = null,  // Repeat penalty (0.0 - 2.0)
    val seed: Int? = null               // Random seed for reproducibility
) {
    companion object {
        fun default() = SessionSettingsDto()
    }
}

@Serializable
data class CompressionInfoDto(
    val hasSummary: Boolean,
    val compressedMessagesCount: Int,
    val summary: String?,
    val tokensSaved: Int
)

@Serializable
data class ModelInfoDto(
    val id: String,
    val name: String,
    val displayName: String
)

// ============= RAG Experiment =============

@Serializable
data class RagChunkDto(
    val sourceId: String,
    val chunkIndex: Int,
    val score: Double,
    val text: String
)

@Serializable
data class RagAnswerVariantDto(
    val answer: String,
    val modelId: String,
    val tokenUsage: TokenUsageDto
)

@Serializable
data class RagAskRequest(
    val question: String,
    val topK: Int = 10,
    val modelId: String = "yandexgpt-lite",
    val temperature: Double = 0.6,
    val maxTokens: Int = 1000,
    val systemPrompt: String? = null,
    /**
     * Порог отсечения нерелевантных результатов по косинусному сходству.
     * null = использовать значение по умолчанию на сервере.
     */
    val similarityThreshold: Double? = null
)

@Serializable
data class RagAskResponse(
    val question: String,
    val withRag: RagAnswerVariantDto,
    val withRagFiltered: RagAnswerVariantDto? = null,
    val withoutRag: RagAnswerVariantDto,
    val usedChunks: List<RagChunkDto>,
    val usedChunksFiltered: List<RagChunkDto>? = null,
    val similarityThreshold: Double? = null
)

// ============= Common Responses =============

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SuccessResponse(
    val success: Boolean,
    val message: String? = null
)
