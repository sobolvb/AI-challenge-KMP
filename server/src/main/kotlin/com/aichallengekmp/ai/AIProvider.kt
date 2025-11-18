package com.aichallengekmp.ai

import com.aichallengekmp.tools.ToolDefinition

/**
 * Абстрактный интерфейс для AI провайдеров
 * Позволяет легко добавлять новые модели от разных провайдеров
 */
interface AIProvider {
    /**
     * ID провайдера (yandex, openai, anthropic и т.д.)
     */
    val providerId: String
    
    /**
     * Список поддерживаемых моделей
     */
    suspend fun getSupportedModels(): List<AIModel>
    
    /**
     * Отправить запрос на генерацию
     */
    suspend fun complete(request: CompletionRequest): CompletionResult
}

/**
 * Информация о модели
 */
data class AIModel(
    val id: String,
    val name: String,
    val displayName: String,
    val providerId: String,
    val maxTokens: Int = 8000,
    val supportsSystemPrompt: Boolean = true
)

/**
 * Запрос на генерацию
 */
data class CompletionRequest(
    val modelId: String,
    val messages: List<AIMessage>,
    val temperature: Double = 0.6,
    val maxTokens: Int = 2000,
    val systemPrompt: String? = null,
    val tools: List<ToolDefinition>? = null
)

/**
 * Сообщение для AI
 */
data class AIMessage(
    val role: String, // "user", "assistant", "system", "function"
    val content: String? = null,
    val toolCallList: Any? = null,  // Для YandexGPT function calling
    val toolResultList: Any? = null  // Для YandexGPT function calling
)

/**
 * Результат генерации
 */
data class CompletionResult(
    val text: String,
    val modelId: String,
    val tokenUsage: TokenUsage
)

/**
 * Информация об использовании токенов
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)
