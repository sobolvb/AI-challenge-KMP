package com.aichallengekmp.tools

import com.aichallengekmp.di.AppContainer
import com.aichallengekmp.mcpfromanother.YandexTrackerClient
import com.aichallengekmp.service.ReminderService
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Локальная (in-process) реализация инструментов:
 *  - задачи Яндекс.Трекера через YandexTrackerClient (HTTP к Tracker API)
 *  - напоминания через ReminderService (БД)
 *
 * Этот исполнитель ничего не знает про MCP и может безопасно
 * использоваться изнутри MCP-серверов, чтобы избегать рекурсии.
 */
class LocalToolExecutor(
    private val reminderService: ReminderService
) : ToolExecutor {

    private val logger = LoggerFactory.getLogger(LocalToolExecutor::class.java)
    private val trackerClient = YandexTrackerClient()

    override suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
        logger.info("[LOCAL] Выполнение инструмента: {}", toolName)

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
                        appendLine(" Задача: ${issue.key}")
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
                        logger.error(
                            "❌ Не удалось разобрать дату/время remind_at_iso={}: {}",
                            remindAtIso,
                            e.message,
                            e
                        )
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

                "search_docs" -> {
                    val query = arguments["query"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: return "Ошибка: не указан параметр 'query'"

                    val topK = arguments["top_k"]?.toString()?.toIntOrNull() ?: 5

                    val hits = AppContainer.ragSearchService.search(query, topK)
                    if (hits.isEmpty()) {
                        "По вашему запросу ничего не найдено в локальном индексе документов."
                    } else {
                        buildString {
                            appendLine("Найдено релевантных фрагментов: ${hits.size}")
                            hits.forEach { h ->
                                appendLine()
                                appendLine("Источник: ${h.sourceId}, чанк #${h.chunkIndex}, score=${"%.3f".format(h.score)}")
                                appendLine(h.text)
                            }
                        }
                    }
                }

                else -> "Неизвестный инструмент (локальный): $toolName"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при локальном выполнении инструмента {}: {}", toolName, e.message, e)
            "Ошибка при выполнении инструмента $toolName: ${e.message}"
        }
    }
}
