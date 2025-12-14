package com.aichallengekmp.tools

import com.aichallengekmp.service.AnalyticsService
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong

/**
 * Сервис для работы с инструментами аналитики
 * Предоставляет tools для LLM function calling
 *
 * АРХИТЕКТУРА: Использует Map вместо SQLDelight классов для обхода проблем компиляции
 */
class AnalyticsToolsService(
    private val analyticsService: AnalyticsService
) {
    private val logger = LoggerFactory.getLogger(AnalyticsToolsService::class.java)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Получить список доступных analytics tools для LLM
     */
    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_token_usage",
                description = "Получить статистику использования токенов. Параметры: time_range (1h, 24h, 7d, all)",
                parameters = mapOf(
                    "time_range" to mapOf(
                        "type" to "string",
                        "description" to "Период времени: 1h, 24h, 7d или all",
                        "enum" to listOf("1h", "24h", "7d", "all"),
                        "default" to "all"
                    )
                )
            ),
            ToolDefinition(
                name = "get_token_usage_by_model",
                description = "Получить статистику использования токенов по моделям"
            ),
            ToolDefinition(
                name = "get_error_stats",
                description = "Получить статистику ошибок. Параметры: time_range (1h, 24h, 7d, all)",
                parameters = mapOf(
                    "time_range" to mapOf(
                        "type" to "string",
                        "description" to "Период времени",
                        "enum" to listOf("1h", "24h", "7d", "all"),
                        "default" to "all"
                    )
                )
            ),
            ToolDefinition(
                name = "get_slowest_endpoints",
                description = "Получить список самых медленных API endpoint-ов. Параметры: limit (1-50)",
                parameters = mapOf(
                    "limit" to mapOf(
                        "type" to "integer",
                        "description" to "Количество результатов",
                        "default" to 10
                    )
                )
            ),
            ToolDefinition(
                name = "get_event_stats",
                description = "Получить общую статистику событий по типам. Параметры: time_range",
                parameters = mapOf(
                    "time_range" to mapOf(
                        "type" to "string",
                        "enum" to listOf("1h", "24h", "7d", "all"),
                        "default" to "all"
                    )
                )
            ),
            ToolDefinition(
                name = "get_http_status_stats",
                description = "Получить статистику HTTP статус кодов"
            ),
            ToolDefinition(
                name = "get_activity_by_hour",
                description = "Получить почасовую активность. Параметры: time_range",
                parameters = mapOf(
                    "time_range" to mapOf(
                        "type" to "string",
                        "enum" to listOf("1h", "24h", "7d"),
                        "default" to "24h"
                    )
                )
            ),
            ToolDefinition(
                name = "get_recent_events",
                description = "Получить последние события. Параметры: limit, event_type",
                parameters = mapOf(
                    "limit" to mapOf(
                        "type" to "integer",
                        "default" to 10
                    ),
                    "event_type" to mapOf(
                        "type" to "string",
                        "description" to "Тип события (api_request, llm_call, error, user_action)",
                        "optional" to true
                    )
                )
            )
        )
    }

    /**
     * Выполнить analytics tool
     */
    suspend fun executeTool(toolName: String, arguments: Map<String, Any>): String {
        logger.info("🔧 Выполнение analytics tool: $toolName с аргументами: $arguments")

        return try {
            when (toolName) {
                "get_token_usage" -> getTokenUsage(arguments)
                "get_token_usage_by_model" -> getTokenUsageByModel()
                "get_error_stats" -> getErrorStats(arguments)
                "get_slowest_endpoints" -> getSlowestEndpoints(arguments)
                "get_event_stats" -> getEventStats(arguments)
                "get_http_status_stats" -> getHttpStatusStats()
                "get_activity_by_hour" -> getActivityByHour(arguments)
                "get_recent_events" -> getRecentEvents(arguments)
                else -> "❌ Неизвестный инструмент: $toolName"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при выполнении $toolName: ${e.message}", e)
            "Ошибка при выполнении $toolName: ${e.message}"
        }
    }

    // ============= Реализация tools =============

    private suspend fun getTokenUsage(arguments: Map<String, Any>): String {
        val timeRange = arguments["time_range"]?.toString() ?: "all"
        val (start, end) = parseTimeRange(timeRange)

        // Получаем данные напрямую и извлекаем поля
        return if (start != null && end != null) {
            val result = analyticsService.getTotalTokenUsageInTimeRange(start, end)
            if (result == null) {
                "За указанный период нет данных об использовании токенов"
            } else {
                buildTokenUsageString(timeRange, result)
            }
        } else {
            val result = analyticsService.getTotalTokenUsage()
            if (result == null) {
                "За указанный период нет данных об использовании токенов"
            } else {
                buildTokenUsageString(timeRange, result)
            }
        }
    }

    private fun buildTokenUsageString(timeRange: String, result: Any): String {
        // Используем reflection для извлечения значений
        val callCount = (result::class.java.getDeclaredField("callCount").apply { isAccessible = true }.get(result) as Long)
        val totalInput = (result::class.java.getDeclaredField("totalInput").apply { isAccessible = true }.get(result) as Double).toLong()
        val totalOutput = (result::class.java.getDeclaredField("totalOutput").apply { isAccessible = true }.get(result) as Double).toLong()
        val total = (result::class.java.getDeclaredField("total").apply { isAccessible = true }.get(result) as Double).toLong()
        val avgResponseTime = (result::class.java.getDeclaredField("avgResponseTime").apply { isAccessible = true }.get(result) as? Double)?.roundToLong()

        return buildString {
            appendLine("📊 Статистика использования токенов (период: $timeRange)")
            appendLine()
            appendLine("Всего вызовов LLM: $callCount")
            appendLine("Входные токены: ${formatNumber(totalInput)}")
            appendLine("Выходные токены: ${formatNumber(totalOutput)}")
            appendLine("Всего токенов: ${formatNumber(total)}")
            avgResponseTime?.let {
                appendLine("Среднее время ответа: ${formatMs(it)}")
            }
        }
    }

    private suspend fun getTokenUsageByModel(): String {
        val results = analyticsService.getTokenUsageByModel()

        if (results.isEmpty()) {
            return "Нет данных об использовании моделей"
        }

        return buildString {
            appendLine("📊 Статистика использования по моделям:")
            appendLine()
            results.forEach { result ->
                // Используем reflection
                val modelId = result::class.java.getDeclaredField("modelId").apply { isAccessible = true }.get(result) as String
                val callCount = result::class.java.getDeclaredField("callCount").apply { isAccessible = true }.get(result) as Long
                val total = (result::class.java.getDeclaredField("total").apply { isAccessible = true }.get(result) as Double).toLong()
                val totalInput = (result::class.java.getDeclaredField("totalInput").apply { isAccessible = true }.get(result) as Double).toLong()
                val totalOutput = (result::class.java.getDeclaredField("totalOutput").apply { isAccessible = true }.get(result) as Double).toLong()
                val avgResponseTime = (result::class.java.getDeclaredField("avgResponseTime").apply { isAccessible = true }.get(result) as? Double)?.roundToLong()

                appendLine("🤖 Модель: $modelId")
                appendLine("   Вызовов: $callCount")
                appendLine("   Токенов: ${formatNumber(total)} (in: ${formatNumber(totalInput)}, out: ${formatNumber(totalOutput)})")
                avgResponseTime?.let {
                    appendLine("   Среднее время: ${formatMs(it)}")
                }
                appendLine()
            }
        }
    }

    private suspend fun getErrorStats(arguments: Map<String, Any>): String {
        val timeRange = arguments["time_range"]?.toString() ?: "all"
        val (start, end) = parseTimeRange(timeRange)

        val results = if (start != null && end != null) {
            analyticsService.getErrorStatsInTimeRange(start, end)
        } else {
            analyticsService.getErrorStats()
        }

        if (results.isEmpty()) {
            return "За указанный период ошибок не зафиксировано ✅"
        }

        return buildString {
            appendLine("⚠️ Статистика ошибок (период: $timeRange):")
            appendLine()
            results.forEachIndexed { index, result ->
                val errorType = result::class.java.getDeclaredField("errorType").apply { isAccessible = true }.get(result) as String
                val eventCount = result::class.java.getDeclaredField("eventCount").apply { isAccessible = true }.get(result) as Long
                val lastOccurrence = result::class.java.getDeclaredField("lastOccurrence").apply { isAccessible = true }.get(result) as? Long

                appendLine("${index + 1}. $errorType - $eventCount раз(а)")
                lastOccurrence?.let {
                    appendLine("   Последняя: ${formatTimestamp(it)}")
                }
            }
        }
    }

    private suspend fun getSlowestEndpoints(arguments: Map<String, Any>): String {
        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 10).coerceIn(1, 50)
        val results = analyticsService.getSlowestEndpoints(limit)

        if (results.isEmpty()) {
            return "Нет данных об endpoint-ах"
        }

        return buildString {
            appendLine("🐌 Топ-$limit самых медленных endpoint-ов:")
            appendLine()
            results.forEachIndexed { index, result ->
                val httpPath = result::class.java.getDeclaredField("httpPath").apply { isAccessible = true }.get(result) as String
                val requestCount = result::class.java.getDeclaredField("requestCount").apply { isAccessible = true }.get(result) as Long
                val avgResponseTime = (result::class.java.getDeclaredField("avgResponseTime").apply { isAccessible = true }.get(result) as? Double)?.roundToLong()
                val maxResponseTime = result::class.java.getDeclaredField("maxResponseTime").apply { isAccessible = true }.get(result) as? Long

                appendLine("${index + 1}. $httpPath")
                appendLine("   Запросов: $requestCount")
                avgResponseTime?.let {
                    appendLine("   Среднее время: ${formatMs(it)}")
                }
                maxResponseTime?.let {
                    appendLine("   Максимальное: ${formatMs(it)}")
                }
                appendLine()
            }
        }
    }

    private suspend fun getEventStats(arguments: Map<String, Any>): String {
        val timeRange = arguments["time_range"]?.toString() ?: "all"
        val (start, end) = parseTimeRange(timeRange)

        val results = if (start != null && end != null) {
            analyticsService.getEventCountsByTypeInTimeRange(start, end)
        } else {
            analyticsService.getEventCountsByType()
        }

        if (results.isEmpty()) {
            return "За указанный период нет событий"
        }

        val total = results.sumOf {
            it::class.java.getDeclaredField("eventCount").apply { isAccessible = true }.get(it) as Long
        }

        return buildString {
            appendLine("📈 Статистика событий (период: $timeRange):")
            appendLine("Всего событий: $total")
            appendLine()
            results.forEach { result ->
                val eventType = result::class.java.getDeclaredField("eventType").apply { isAccessible = true }.get(result) as String
                val eventCount = result::class.java.getDeclaredField("eventCount").apply { isAccessible = true }.get(result) as Long
                val percentage = (eventCount.toDouble() / total * 100).roundToLong()
                val eventTypeRus = when(eventType) {
                    "api_request" -> "API запросы"
                    "llm_call" -> "LLM вызовы"
                    "error" -> "Ошибки"
                    "user_action" -> "Действия пользователя"
                    else -> eventType
                }
                appendLine("• $eventTypeRus: $eventCount ($percentage%)")
            }
        }
    }

    private suspend fun getHttpStatusStats(): String {
        val results = analyticsService.getHttpStatusStats()

        if (results.isEmpty()) {
            return "Нет данных о HTTP запросах"
        }

        val total = results.sumOf {
            it::class.java.getDeclaredField("eventCount").apply { isAccessible = true }.get(it) as Long
        }

        return buildString {
            appendLine("📡 Статистика HTTP статус кодов:")
            appendLine("Всего запросов: $total")
            appendLine()
            results.forEach { result ->
                val httpStatus = result::class.java.getDeclaredField("httpStatus").apply { isAccessible = true }.get(result) as? Long
                val eventCount = result::class.java.getDeclaredField("eventCount").apply { isAccessible = true }.get(result) as Long
                val percentage = (eventCount.toDouble() / total * 100).roundToLong()
                val emoji = when (httpStatus?.toInt()) {
                    in 200..299 -> "✅"
                    in 400..499 -> "⚠️"
                    in 500..599 -> "❌"
                    else -> "❓"
                }
                appendLine("$emoji $httpStatus: $eventCount ($percentage%)")
            }
        }
    }

    private suspend fun getActivityByHour(arguments: Map<String, Any>): String {
        val timeRange = arguments["time_range"]?.toString() ?: "24h"
        val (start, end) = parseTimeRange(timeRange)

        if (start == null || end == null) {
            return "Для почасовой активности нужен конкретный период (1h, 24h или 7d)"
        }

        val results = analyticsService.getActivityByHour(start, end)

        if (results.isEmpty()) {
            return "Нет данных об активности за указанный период"
        }

        return buildString {
            appendLine("📊 Активность по часам (период: $timeRange):")
            appendLine()
            results.forEach { result ->
                val hourTimestamp = result::class.java.getDeclaredField("hourTimestamp").apply { isAccessible = true }.get(result) as Long
                val eventCount = result::class.java.getDeclaredField("eventCount").apply { isAccessible = true }.get(result) as Long
                val hour = formatTimestamp(hourTimestamp)
                val bar = "█".repeat((eventCount.toInt() / 10).coerceAtMost(50))
                appendLine("$hour: $bar $eventCount")
            }
        }
    }

    private suspend fun getRecentEvents(arguments: Map<String, Any>): String {
        val eventType = arguments["event_type"]?.toString()
        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 10).coerceIn(1, 100)

        val results = if (eventType != null) {
            analyticsService.getRecentEventsByType(eventType, limit)
        } else {
            analyticsService.getRecentEvents(limit)
        }

        if (results.isEmpty()) {
            return "Нет событий"
        }

        return buildString {
            appendLine("📋 Последние события (${results.size}):")
            appendLine()
            results.forEach { result ->
                val timestamp = result::class.java.getDeclaredField("timestamp").apply { isAccessible = true }.get(result) as Long
                val eventTypeVal = result::class.java.getDeclaredField("eventType").apply { isAccessible = true }.get(result) as String
                val modelId = result::class.java.getDeclaredField("modelId").apply { isAccessible = true }.get(result) as? String
                val inputTokens = result::class.java.getDeclaredField("inputTokens").apply { isAccessible = true }.get(result) as? Long
                val outputTokens = result::class.java.getDeclaredField("outputTokens").apply { isAccessible = true }.get(result) as? Long
                val httpMethod = result::class.java.getDeclaredField("httpMethod").apply { isAccessible = true }.get(result) as? String
                val httpPath = result::class.java.getDeclaredField("httpPath").apply { isAccessible = true }.get(result) as? String
                val httpStatus = result::class.java.getDeclaredField("httpStatus").apply { isAccessible = true }.get(result) as? Long
                val errorType = result::class.java.getDeclaredField("errorType").apply { isAccessible = true }.get(result) as? String
                val errorMessage = result::class.java.getDeclaredField("errorMessage").apply { isAccessible = true }.get(result) as? String

                appendLine("${formatTimestamp(timestamp)} | $eventTypeVal")
                when (eventTypeVal) {
                    "llm_call" -> appendLine("  Модель: $modelId, токены: $inputTokens+$outputTokens")
                    "api_request" -> appendLine("  $httpMethod $httpPath → $httpStatus")
                    "error" -> appendLine("  $errorType: $errorMessage")
                }
                appendLine()
            }
        }
    }

    // ============= Вспомогательные методы =============

    private fun parseTimeRange(range: String): Pair<Long?, Long?> {
        val now = System.currentTimeMillis()
        return when (range.lowercase()) {
            "1h" -> (now - 3600_000L) to now
            "24h", "1d" -> (now - 86400_000L) to now
            "7d" -> (now - 604800_000L) to now
            "30d" -> (now - 2592000_000L) to now
            "all" -> null to null
            else -> null to null
        }
    }

    private fun formatNumber(num: Long): String {
        return when {
            num >= 1_000_000 -> "${num / 1_000_000}M"
            num >= 1_000 -> "${num / 1_000}K"
            else -> num.toString()
        }
    }

    private fun formatMs(ms: Long): String {
        return when {
            ms >= 1000 -> "${ms / 1000}s ${ms % 1000}ms"
            else -> "${ms}ms"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
}
