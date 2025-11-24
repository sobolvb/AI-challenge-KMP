package com.aichallengekmp.tools

import com.aichallengekmp.mcpfromanother.YandexTrackerClient
import com.aichallengekmp.service.ReminderService
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Сервис для работы с инструментами Яндекс.Трекера
 * Использует YandexTrackerClient напрямую (сам MCP сервер запускается отдельно)
 */
class TrackerToolsService(
    private val reminderService: ReminderService
) {
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
            ),
            ToolDefinition(
                name = "create_reminder",
                description = "Создать напоминание пользователю о чём-либо в указанное время",
                parameters = mapOf(
                    "message" to "Текст напоминания",
                    "remind_at_iso" to "Время напоминания в формате ISO 8601, например 2025-11-20T10:00:00+03:00"
                )
            ),
            ToolDefinition(
                name = "list_reminders",
                description = "Показать все активные напоминания пользователя",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "delete_reminder",
                description = "Удалить напоминание по его идентификатору",
                parameters = mapOf(
                    "id" to "Идентификатор напоминания (число)"
                )
            ),
            ToolDefinition(
                name = "search_docs",
                description = "Поиск по локальному индексу документов (RAG) и возврат релевантных фрагментов",
                parameters = mapOf(
                    "query" to "Текст поискового запроса",
                    "top_k" to "Опционально: количество фрагментов в ответе (по умолчанию 5)"
                )
            ),
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

                "create_reminder" -> {
                    val message = arguments["message"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: return "Ошибка: не указан текст напоминания"

                    val remindAtIso = arguments["remind_at_iso"]?.toString()
                        ?: return "Ошибка: не указано время напоминания (remind_at_iso)"

                    val remindAtMillis = try {
                        val odt = OffsetDateTime.parse(remindAtIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        odt.toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        logger.error("❌ Не удалось разобрать дату/время remind_at_iso=$remindAtIso: ${e.message}", e)
                        return "Ошибка: не удалось разобрать дату/время remind_at_iso: $remindAtIso"
                    }

                    val reminder = reminderService.createReminder(message, remindAtMillis)
                    "Напоминание #${reminder.id} создано на время ${reminder.remindAt}"
                }

                "list_reminders" -> {
                    val reminders = reminderService.getAllReminders()
                    if (reminders.isEmpty()) {
                        "Напоминаний нет"
                    } else {
                        buildString {
                            appendLine("Всего напоминаний: ${reminders.size}")
                            reminders.forEach { r ->
                                append("- #").append(r.id)
                                    .append(" [").append(r.remindAt).append("] ")
                                    .appendLine(r.message)
                            }
                        }
                    }
                }

                "delete_reminder" -> {
                    val idRaw = arguments["id"]?.toString()
                        ?: return "Ошибка: не указан идентификатор напоминания (id)"

                    val id = idRaw.toLongOrNull()
                        ?: return "Ошибка: идентификатор напоминания (id) должен быть числом"

                    reminderService.deleteReminder(id)
                    "Напоминание #$id удалено"
                }

                else -> "Неизвестный инструмент: $toolName"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при выполнении инструмента $toolName: ${e.message}", e)
            "Ошибка: ${e.message}"
        }
    }
}


