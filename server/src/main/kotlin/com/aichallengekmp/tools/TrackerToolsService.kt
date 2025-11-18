package com.aichallengekmp.tools

import com.aichallengekmp.mcpfromanother.YandexTrackerClient
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с инструментами Яндекс.Трекера
 * Использует YandexTrackerClient напрямую (сам MCP сервер запускается отдельно)
 */
class TrackerToolsService {
    private val logger = LoggerFactory.getLogger(TrackerToolsService::class.java)
    private val trackerClient = YandexTrackerClient()
    
    /**
     * Получить список всех доступных инструментов
     */
    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_issues_count",
                description = "Получить общее количество задач в Яндекс.Трекере",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_all_issue_names",
                description = "Получить список всех задач с их названиями",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_issue_info",
                description = "Получить подробную информацию о конкретной задаче по её ключу",
                parameters = mapOf(
                    "issue_key" to "Ключ задачи в формате QUEUE-NUMBER (например TEST-123)"
                )
            )
        )
    }
    
    /**
     * Выполнить инструмент
     */
    suspend fun executeTool(toolName: String, arguments: Map<String, Any>): String {
        logger.info("🔧 Выполнение инструмента: $toolName")
        
        return try {
            when (toolName) {
                "get_issues_count" -> {
                    val count = trackerClient.getIssuesCount()
                    "Общее количество задач в трекере: $count"
                }
                
                "get_all_issue_names" -> {
                    val names = trackerClient.getAllIssueNames()
                    if (names.isEmpty()) {
                        "Задачи не найдены"
                    } else {
                        "Всего задач: ${names.size}\n\n" + names.joinToString("\n")
                    }
                }
                
                "get_issue_info" -> {
                    val issueKey = arguments["issue_key"]?.toString()
                        ?: return "Ошибка: не указан ключ задачи"
                    
                    val issue = trackerClient.getIssueByKey(issueKey)
                    buildString {
                        appendLine("📋 Задача: ${issue.key}")
                        appendLine("Название: ${issue.summary}")
                        issue.description?.let { appendLine("Описание: $it") }
                        issue.status?.let { appendLine("Статус: ${it.display}") }
                        issue.assignee?.display?.let { appendLine("Исполнитель: $it") }
                        issue.createdBy?.display?.let { appendLine("Создал: $it") }
                        issue.createdAt?.let { appendLine("Создана: $it") }
                        issue.updatedAt?.let { appendLine("Обновлена: $it") }
                    }
                }
                
                else -> "Неизвестный инструмент: $toolName"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при выполнении инструмента $toolName: ${e.message}", e)
            "Ошибка: ${e.message}"
        }
    }
}


