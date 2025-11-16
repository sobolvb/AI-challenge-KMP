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
data class MessageDto(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val modelId: String?,
    val modelName: String?,
    val tokenUsage: TokenUsageDto?,
    val timestamp: Long
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
    val systemPrompt: String? = "выполни задачу"
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
