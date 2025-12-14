package com.aichallengekmp.service

import com.aichallengekmp.database.dao.AnalyticsEventDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для сбора аналитики и логирования событий
 */
class AnalyticsService(
    private val analyticsEventDao: AnalyticsEventDao
) {
    private val logger = LoggerFactory.getLogger(AnalyticsService::class.java)
    private val json = Json { prettyPrint = false }

    // ============= Методы логирования =============

    /**
     * Логирование HTTP запроса
     */
    suspend fun logApiRequest(
        method: String,
        path: String,
        statusCode: Int,
        responseTimeMs: Long,
        sessionId: String? = null,
        metadata: Map<String, Any?>? = null
    ) {
        try {
            analyticsEventDao.logApiRequest(
                httpMethod = method,
                httpPath = path,
                httpStatus = statusCode.toLong(),
                responseTimeMs = responseTimeMs,
                sessionId = sessionId,
                metadata = metadata?.let { json.encodeToString(it) }
            )
        } catch (e: Exception) {
            logger.error("Ошибка при логировании API запроса: ${e.message}", e)
        }
    }

    /**
     * Логирование вызова LLM
     */
    suspend fun logLlmCall(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        temperature: Double,
        responseTimeMs: Long,
        sessionId: String? = null,
        topP: Double? = null,
        topK: Int? = null,
        numCtx: Int? = null,
        repeatPenalty: Double? = null,
        seed: Int? = null
    ) {
        try {
            val metadata = mutableMapOf<String, Any?>()
            topP?.let { metadata["top_p"] = it }
            topK?.let { metadata["top_k"] = it }
            numCtx?.let { metadata["num_ctx"] = it }
            repeatPenalty?.let { metadata["repeat_penalty"] = it }
            seed?.let { metadata["seed"] = it }

            analyticsEventDao.logLlmCall(
                modelId = modelId,
                inputTokens = inputTokens.toLong(),
                outputTokens = outputTokens.toLong(),
                llmTemperature = temperature,
                llmResponseTimeMs = responseTimeMs,
                sessionId = sessionId,
                metadata = if (metadata.isNotEmpty()) json.encodeToString(metadata) else null
            )
        } catch (e: Exception) {
            logger.error("Ошибка при логировании LLM вызова: ${e.message}", e)
        }
    }

    /**
     * Логирование ошибки
     */
    suspend fun logError(
        errorType: String,
        errorMessage: String,
        sessionId: String? = null,
        context: Map<String, String>? = null
    ) {
        try {
            analyticsEventDao.logError(
                errorType = errorType,
                errorMessage = errorMessage,
                sessionId = sessionId,
                metadata = context?.let { json.encodeToString(it) }
            )
        } catch (e: Exception) {
            logger.error("Ошибка при логировании ошибки: ${e.message}", e)
        }
    }

    /**
     * Логирование пользовательского действия
     */
    suspend fun logUserAction(
        action: String,
        sessionId: String,
        details: Map<String, Any?>? = null
    ) {
        try {
            val metadata = mutableMapOf<String, Any?>("action" to action)
            details?.let { metadata.putAll(it) }

            analyticsEventDao.logUserAction(
                sessionId = sessionId,
                metadata = json.encodeToString(metadata)
            )
        } catch (e: Exception) {
            logger.error("Ошибка при логировании действия пользователя: ${e.message}", e)
        }
    }

    // ============= Методы получения статистики (для DAO) =============

    /**
     * Получить статистику событий по типам
     */
    suspend fun getEventCountsByType() = analyticsEventDao.getEventCountsByType()

    /**
     * Получить статистику событий за период
     */
    suspend fun getEventCountsByTypeInTimeRange(startTimestamp: Long, endTimestamp: Long) =
        analyticsEventDao.getEventCountsByTypeInTimeRange(startTimestamp, endTimestamp)

    /**
     * Получить общую статистику использования токенов
     */
    suspend fun getTotalTokenUsage() = analyticsEventDao.getTotalTokenUsage()

    /**
     * Получить статистику использования токенов за период
     */
    suspend fun getTotalTokenUsageInTimeRange(startTimestamp: Long, endTimestamp: Long) =
        analyticsEventDao.getTotalTokenUsageInTimeRange(startTimestamp, endTimestamp)

    /**
     * Получить статистику по моделям
     */
    suspend fun getTokenUsageByModel() = analyticsEventDao.getTokenUsageByModel()

    /**
     * Получить самые медленные endpoint-ы
     */
    suspend fun getSlowestEndpoints(limit: Int = 10) =
        analyticsEventDao.getSlowestEndpoints(limit.toLong())

    /**
     * Получить статистику ошибок
     */
    suspend fun getErrorStats() = analyticsEventDao.getErrorStats()

    /**
     * Получить статистику ошибок за период
     */
    suspend fun getErrorStatsInTimeRange(startTimestamp: Long, endTimestamp: Long) =
        analyticsEventDao.getErrorStatsInTimeRange(startTimestamp, endTimestamp)

    /**
     * Получить статистику HTTP статус кодов
     */
    suspend fun getHttpStatusStats() = analyticsEventDao.getHttpStatusStats()

    /**
     * Получить активность по часам
     */
    suspend fun getActivityByHour(startTimestamp: Long, endTimestamp: Long) =
        analyticsEventDao.getActivityByHour(startTimestamp, endTimestamp)

    /**
     * Получить последние события
     */
    suspend fun getRecentEvents(limit: Int = 100) =
        analyticsEventDao.getRecentEvents(limit.toLong())

    /**
     * Получить последние события по типу
     */
    suspend fun getRecentEventsByType(eventType: String, limit: Int = 100) =
        analyticsEventDao.getRecentEventsByType(eventType, limit.toLong())

    /**
     * Удалить события старше указанного timestamp
     */
    suspend fun deleteOlderThan(timestamp: Long) = analyticsEventDao.deleteOlderThan(timestamp)
}
