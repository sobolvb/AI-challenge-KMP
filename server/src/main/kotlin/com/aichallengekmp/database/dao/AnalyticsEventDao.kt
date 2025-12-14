package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.AnalyticsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с событиями аналитики
 */
class AnalyticsEventDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(AnalyticsEventDao::class.java)
    private val queries = database.analyticsEventQueries

    // ============= Методы записи =============

    suspend fun logApiRequest(
        timestamp: Long = System.currentTimeMillis(),
        httpMethod: String,
        httpPath: String,
        httpStatus: Long,
        responseTimeMs: Long,
        sessionId: String? = null,
        metadata: String? = null
    ) = withContext(Dispatchers.IO) {
        logger.debug("📊 Логирование API запроса: $httpMethod $httpPath -> $httpStatus")
        queries.insertApiRequest(
            timestamp = timestamp,
            httpMethod = httpMethod,
            httpPath = httpPath,
            httpStatus = httpStatus,
            responseTimeMs = responseTimeMs,
            sessionId = sessionId,
            metadata = metadata
        )
    }

    suspend fun logLlmCall(
        timestamp: Long = System.currentTimeMillis(),
        modelId: String,
        inputTokens: Long,
        outputTokens: Long,
        llmTemperature: Double,
        llmResponseTimeMs: Long,
        sessionId: String? = null,
        metadata: String? = null
    ) = withContext(Dispatchers.IO) {
        logger.debug("📊 Логирование LLM вызова: $modelId (${inputTokens + outputTokens} tokens)")
        queries.insertLlmCall(
            timestamp = timestamp,
            modelId = modelId,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            llmTemperature = llmTemperature,
            llmResponseTimeMs = llmResponseTimeMs,
            sessionId = sessionId,
            metadata = metadata
        )
    }

    suspend fun logError(
        timestamp: Long = System.currentTimeMillis(),
        errorType: String,
        errorMessage: String,
        sessionId: String? = null,
        metadata: String? = null
    ) = withContext(Dispatchers.IO) {
        logger.warn("📊 Логирование ошибки: $errorType - $errorMessage")
        queries.insertError(
            timestamp = timestamp,
            errorType = errorType,
            errorMessage = errorMessage,
            sessionId = sessionId,
            metadata = metadata
        )
    }

    suspend fun logUserAction(
        timestamp: Long = System.currentTimeMillis(),
        sessionId: String,
        metadata: String
    ) = withContext(Dispatchers.IO) {
        logger.debug("📊 Логирование пользовательского действия: $sessionId")
        queries.insertUserAction(
            timestamp = timestamp,
            sessionId = sessionId,
            metadata = metadata
        )
    }

    // ============= Методы чтения для аналитики =============

    suspend fun getEventCountsByType(): List<com.aichallengekmp.database.GetEventCountsByType> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики событий по типам")
            queries.getEventCountsByType().executeAsList()
        }

    suspend fun getEventCountsByTypeInTimeRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<com.aichallengekmp.database.GetEventCountsByTypeInTimeRange> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики событий за период $startTimestamp - $endTimestamp")
            queries.getEventCountsByTypeInTimeRange(startTimestamp, endTimestamp).executeAsList()
        }

    suspend fun getTotalTokenUsage(): com.aichallengekmp.database.GetTotalTokenUsage? =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение общей статистики использования токенов")
            queries.getTotalTokenUsage().executeAsOneOrNull()
        }

    suspend fun getTotalTokenUsageInTimeRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): com.aichallengekmp.database.GetTotalTokenUsageInTimeRange? =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики токенов за период")
            queries.getTotalTokenUsageInTimeRange(startTimestamp, endTimestamp).executeAsOneOrNull()
        }

    suspend fun getTokenUsageByModel(): List<com.aichallengekmp.database.GetTokenUsageByModel> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики использования по моделям")
            queries.getTokenUsageByModel().executeAsList()
        }

    suspend fun getSlowestEndpoints(limit: Long = 10): List<com.aichallengekmp.database.GetSlowestEndpoints> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение самых медленных endpoint-ов")
            queries.getSlowestEndpoints(limit).executeAsList()
        }

    suspend fun getErrorStats(): List<com.aichallengekmp.database.GetErrorStats> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики ошибок")
            queries.getErrorStats().executeAsList()
        }

    suspend fun getErrorStatsInTimeRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<com.aichallengekmp.database.GetErrorStatsInTimeRange> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики ошибок за период")
            queries.getErrorStatsInTimeRange(startTimestamp, endTimestamp).executeAsList()
        }

    suspend fun getHttpStatusStats(): List<com.aichallengekmp.database.GetHttpStatusStats> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение статистики HTTP статус кодов")
            queries.getHttpStatusStats().executeAsList()
        }

    suspend fun getActivityByHour(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<com.aichallengekmp.database.GetActivityByHour> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение активности по часам")
            queries.getActivityByHour(startTimestamp, endTimestamp).executeAsList()
        }

    suspend fun getRecentEvents(limit: Long = 100): List<AnalyticsEvent> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение последних $limit событий")
            queries.getRecentEvents(limit).executeAsList()
        }

    suspend fun getRecentEventsByType(eventType: String, limit: Long = 100): List<AnalyticsEvent> =
        withContext(Dispatchers.IO) {
            logger.debug("📊 Получение последних $limit событий типа $eventType")
            queries.getRecentEventsByType(eventType, limit).executeAsList()
        }

    suspend fun deleteOlderThan(timestamp: Long) = withContext(Dispatchers.IO) {
        logger.info("📊 Удаление событий старше $timestamp")
        queries.deleteOlderThan(timestamp)
    }
}
