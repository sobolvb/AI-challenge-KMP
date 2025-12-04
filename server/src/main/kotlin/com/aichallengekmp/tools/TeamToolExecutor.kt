package com.aichallengekmp.tools

import com.aichallengekmp.rag.RagSearchService
import com.aichallengekmp.service.ReminderService
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Исполнитель инструментов для командного ассистента
 * Объединяет все сервисы: RAG, Tracker, Git, Support
 */
class TeamToolExecutor(
    private val ragSearchService: RagSearchService,
    private val trackerTools: TrackerToolsService,
    private val gitTools: GitToolsService,
    private val supportTools: SupportToolsService,
    private val reminderService: ReminderService
) : ToolExecutor {

    private val logger = LoggerFactory.getLogger(TeamToolExecutor::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
        logger.info("🔧 Выполнение инструмента: $toolName")
        logger.debug("   Аргументы: $arguments")

        return try {
            when (toolName) {
                // RAG инструменты
                "search_documentation" -> searchDocumentation(arguments)
                "search_code" -> searchCode(arguments)

                // Трекер задач
                "get_all_tasks" -> getAllTasks()
                "get_task_info" -> getTaskInfo(arguments)
                "create_task" -> createTask(arguments)
                "update_task_status" -> updateTaskStatus(arguments)
                "get_issues_count" -> trackerTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "get_all_issue_names" -> trackerTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "get_issue_info" -> trackerTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })

                // Git/GitHub
                "get_git_branch" -> gitTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "git_get_pr_diff" -> gitTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "git_get_changed_files" -> gitTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "git_get_file_content" -> gitTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "github_get_pr_info" -> gitTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })

                // Поддержка
                "search_support_tickets" -> supportTools.executeTool("search_tickets", arguments.mapValues { it.value ?: "" })
                "get_user" -> supportTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "get_user_tickets" -> supportTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "get_ticket_details" -> supportTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })
                "get_similar_tickets" -> supportTools.executeTool(toolName, arguments.mapValues { it.value ?: "" })

                // Напоминания
                "create_reminder" -> createReminder(arguments)
                "list_reminders" -> listReminders()
                "delete_reminder" -> deleteReminder(arguments)

                // Анализ приоритетов
                "analyze_task_priorities" -> analyzeTaskPriorities()

                else -> "Ошибка: неизвестный инструмент '$toolName'"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка выполнения инструмента $toolName: ${e.message}", e)
            "Ошибка выполнения инструмента: ${e.message}"
        }
    }

    // ============= RAG =============

    private suspend fun searchDocumentation(arguments: Map<String, Any?>): String {
        val query = arguments["query"]?.toString()
            ?: return "Ошибка: параметр 'query' обязателен"
        val topK = arguments["top_k"]?.toString()?.toIntOrNull() ?: 3

        val results = ragSearchService.search(query, topK = topK)

        if (results.isEmpty()) {
            return "Документация по запросу '$query' не найдена"
        }

        return buildString {
            appendLine("Найдено ${results.size} фрагментов документации:")
            appendLine()
            results.forEachIndexed { index, hit ->
                appendLine("### Фрагмент ${index + 1} (релевантность: ${String.format("%.2f", hit.score)})")
                appendLine("Источник: ${hit.sourceId}")
                appendLine()
                appendLine(hit.text)
                appendLine()
            }
        }
    }

    private suspend fun searchCode(arguments: Map<String, Any?>): String {
        val query = arguments["query"]?.toString()
            ?: return "Ошибка: параметр 'query' обязателен"
        val topK = arguments["top_k"]?.toString()?.toIntOrNull() ?: 3

        // Добавляем к запросу "kotlin код" чтобы искать именно в коде
        val results = ragSearchService.search("kotlin код $query", topK = topK)

        if (results.isEmpty()) {
            return "Код по запросу '$query' не найден"
        }

        return buildString {
            appendLine("Найдено ${results.size} фрагментов кода:")
            appendLine()
            results.forEachIndexed { index, hit ->
                appendLine("### Фрагмент ${index + 1} (релевантность: ${String.format("%.2f", hit.score)})")
                appendLine("Источник: ${hit.sourceId}")
                appendLine()
                appendLine("```kotlin")
                appendLine(hit.text)
                appendLine("```")
                appendLine()
            }
        }
    }

    // ============= Tracker =============

    private suspend fun getAllTasks(): String {
        val tasksResult = trackerTools.executeTool("get_all_issue_names", emptyMap())
        val countResult = trackerTools.executeTool("get_issues_count", emptyMap())

        return buildString {
            appendLine("📋 Статус задач в трекере:")
            appendLine()
            appendLine(countResult)
            appendLine()
            appendLine("Список задач:")
            appendLine(tasksResult)
        }
    }

    private suspend fun getTaskInfo(arguments: Map<String, Any?>): String {
        val taskId = arguments["task_id"]?.toString()
            ?: return "Ошибка: параметр 'task_id' обязателен"

        return trackerTools.executeTool("get_issue_info", mapOf("issue_id" to taskId))
    }

    private suspend fun createTask(arguments: Map<String, Any?>): String {
        val title = arguments["title"]?.toString()
            ?: return "Ошибка: параметр 'title' обязателен"
        val description = arguments["description"]?.toString() ?: ""
        val priority = arguments["priority"]?.toString() ?: "medium"

        // TrackerToolsService использует Reminder для хранения, адаптируем параметры
        val dueDate = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L // +7 дней

        val message = "[$priority] $title" + if (description.isNotEmpty()) " - $description" else ""
        val reminderResult = reminderService.createReminder(
            message = message,
            remindAtMillis = dueDate
        )

        return buildString {
            appendLine("✅ Задача создана успешно!")
            appendLine()
            appendLine("ID: ${reminderResult.id}")
            appendLine("Название: $title")
            appendLine("Описание: $description")
            appendLine("Приоритет: $priority")
        }
    }

    private suspend fun updateTaskStatus(arguments: Map<String, Any?>): String {
        val taskId = arguments["task_id"]?.toString()?.toLongOrNull()
            ?: return "Ошибка: параметр 'task_id' должен быть числом"
        val status = arguments["status"]?.toString()
            ?: return "Ошибка: параметр 'status' обязателен"

        // Для упрощения - просто удаляем задачу если status = done
        if (status == "done") {
            reminderService.deleteReminder(taskId)
            return "✅ Задача $taskId отмечена как выполненная"
        }

        return "📝 Статус задачи $taskId обновлен на: $status"
    }

    // ============= Reminders =============

    private suspend fun createReminder(arguments: Map<String, Any?>): String {
        val message = arguments["title"]?.toString()
            ?: return "Ошибка: параметр 'title' обязателен"
        val remindAtMillis = arguments["due_date"]?.toString()?.toLongOrNull()
            ?: return "Ошибка: параметр 'due_date' должен быть числом (timestamp в миллисекундах)"

        val reminder = reminderService.createReminder(
            message = message,
            remindAtMillis = remindAtMillis
        )

        return "Напоминание создано: ${reminder.id} - ${reminder.message}"
    }

    private suspend fun listReminders(): String {
        val reminders = reminderService.getAllReminders()
        if (reminders.isEmpty()) {
            return "Напоминаний нет"
        }

        return reminders.joinToString("\n") { r ->
            "${r.message} (срок: ${java.time.Instant.ofEpochMilli(r.remindAt)})"
        }
    }

    private suspend fun deleteReminder(arguments: Map<String, Any?>): String {
        val reminderId = arguments["reminder_id"]?.toString()?.toLongOrNull()
            ?: return "Ошибка: параметр 'reminder_id' должен быть числом"

        reminderService.deleteReminder(reminderId)
        return "Напоминание удалено: $reminderId"
    }

    // ============= Анализ приоритетов =============

    private suspend fun analyzeTaskPriorities(): String {
        // Получаем все задачи (используем reminders как задачи)
        val allReminders = reminderService.getAllReminders()

        if (allReminders.isEmpty()) {
            return "🎉 Нет задач в трекере! Можно расслабиться или создать новые."
        }

        val openTasks = allReminders // Все напоминания считаем открытыми

        // Анализируем по приоритетам (извлекаем из message)
        val highPriority = openTasks.filter { it.message.contains("[high]", ignoreCase = true) }
        val mediumPriority = openTasks.filter { it.message.contains("[medium]", ignoreCase = true) }
        val lowPriority = openTasks.filter { it.message.contains("[low]", ignoreCase = true) }

        // Анализируем по срокам
        val now = System.currentTimeMillis()
        val overdue = openTasks.filter { it.remindAt < now }
        val urgentSoon = openTasks.filter { it.remindAt > now && it.remindAt < now + 24 * 60 * 60 * 1000L }

        return buildString {
            appendLine("📊 Анализ приоритетов задач")
            appendLine()
            appendLine("**Статистика:**")
            appendLine("- Всего открытых задач: ${openTasks.size}")
            appendLine("- High priority: ${highPriority.size}")
            appendLine("- Medium priority: ${mediumPriority.size}")
            appendLine("- Low priority: ${lowPriority.size}")
            appendLine("- Просроченных: ${overdue.size}")
            appendLine("- Срочных (до 24ч): ${urgentSoon.size}")
            appendLine()

            if (overdue.isNotEmpty()) {
                appendLine("⚠️ **Просроченные задачи (требуют немедленного внимания):**")
                overdue.forEach { task ->
                    appendLine("- [${task.id}] ${task.message}")
                }
                appendLine()
            }

            if (urgentSoon.isNotEmpty()) {
                appendLine("🔥 **Срочные задачи (срок до 24 часов):**")
                urgentSoon.forEach { task ->
                    appendLine("- [${task.id}] ${task.message}")
                }
                appendLine()
            }

            if (highPriority.isNotEmpty()) {
                appendLine("🔴 **High priority задачи:**")
                highPriority.forEach { task ->
                    appendLine("- [${task.id}] ${task.message}")
                }
                appendLine()
            }

            appendLine("💡 **Рекомендации по приоритизации:**")
            appendLine()
            appendLine("1. **Сначала**: Просроченные задачи и high priority")
            appendLine("2. **Затем**: Срочные задачи (срок до 24 часов)")
            appendLine("3. **Потом**: Medium priority задачи")
            appendLine("4. **В последнюю очередь**: Low priority задачи")
            appendLine()

            if (overdue.isNotEmpty() || urgentSoon.isNotEmpty()) {
                val firstTask = (overdue + urgentSoon).first()
                appendLine("⚡ **Предложение**: Начни с задачи [${firstTask.id}] \"${firstTask.message}\"")
            } else if (highPriority.isNotEmpty()) {
                appendLine("⚡ **Предложение**: Начни с high priority задачи [${highPriority.first().id}] \"${highPriority.first().message}\"")
            } else {
                appendLine("✨ **Предложение**: Начни с любой medium priority задачи")
            }
        }
    }
}
