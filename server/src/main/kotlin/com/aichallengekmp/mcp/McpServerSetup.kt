package com.aichallengekmp.mcp

import com.aichallengekmp.service.ReminderService
import com.aichallengekmp.tools.TrackerToolsService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Настройка MCP сервера для Яндекс.Трекер + инструмента напоминаний.
 * Добавляет MCP эндпоинт /mcp для подключения MCP‑клиентов (WebSocket/SSE —
 * это уже деталь реализации библиотеки io.modelcontextprotocol.kotlin.sdk.server.mcp).
 */
fun Application.configureMcpServer(
    trackerTools: TrackerToolsService,
    reminderService: ReminderService
): Server {
    val logger = LoggerFactory.getLogger("McpServer")
    
    val mcpServer = Server(
        serverInfo = Implementation("yandex-tracker-mcp", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null),
                prompts = null,
                resources = null
            )
        )
    )
    
    // Регистрируем инструменты Яндекс.Трекера
    try {
        logger.info("🔧 Регистрация MCP инструментов...")
        
        // Инструмент 1: Количество задач
        mcpServer.addTool(
            name = "get_issues_count",
            description = "Получить общее количество задач в Яндекс.Трекере",
            inputSchema = Tool.Input(
                properties = buildJsonObject {}
            ),
            handler = { _ ->
                val result = trackerTools.executeTool("get_issues_count", emptyMap())
                CallToolResult(content = listOf(TextContent(result)))
            }
        )
        
        // Инструмент 2: Список всех задач
        mcpServer.addTool(
            name = "get_all_issue_names",
            description = "Получить имена всех задач в Яндекс.Трекере",
            inputSchema = Tool.Input(
                properties = buildJsonObject {}
            ),
            handler = { _ ->
                val result = trackerTools.executeTool("get_all_issue_names", emptyMap())
                CallToolResult(content = listOf(TextContent(result)))
            }
        )
        
        // Инструмент 3: Информация о задаче
        mcpServer.addTool(
            name = "get_issue_info",
            description = "Получить информацию о конкретной задаче по ключу",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("issue_key", buildJsonObject {
                        put("type", "string")
                        put("description", "Ключ задачи (например TEST-123)")
                    })
                },
                required = listOf("issue_key")
            ),
            handler = { arguments ->
                val issueKey = arguments.arguments["issue_key"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'issue_key'"))
                    )

                val result = trackerTools.executeTool(
                    toolName = "get_issue_info",
                    arguments = mapOf("issue_key" to issueKey)
                )
                CallToolResult(content = listOf(TextContent(result)))
            }
        )
        
        // Инструмент 4.1: Создать напоминание
        mcpServer.addTool(
            name = "create_reminder",
            description = "Создать напоминание пользователю о чём-либо в указанное время",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("message", buildJsonObject {
                        put("type", "string")
                        put("description", "Текст напоминания")
                    })
                    put("remind_at_iso", buildJsonObject {
                        put("type", "string")
                        put("description", "Время напоминания в формате ISO 8601, например 2025-11-20T10:00:00+03:00")
                    })
                },
                required = listOf("message", "remind_at_iso")
            ),
            handler = { arguments ->
                val message = arguments.arguments["message"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'message'"))
                    )
                val remindAtIso = arguments.arguments["remind_at_iso"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'remind_at_iso'"))
                    )

                val remindAtMillis = try {
                    val odt = OffsetDateTime.parse(remindAtIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    odt.toInstant().toEpochMilli()
                } catch (e: Exception) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не удалось разобрать дату/время remind_at_iso: $remindAtIso"))
                    )
                }

                val reminder = reminderService.createReminder(message, remindAtMillis)
                CallToolResult(
                    content = listOf(
                        TextContent("Напоминание #${reminder.id} создано на время ${reminder.remindAt}")
                    )
                )
            }
        )

        // Инструмент 4.2: Список напоминаний
        mcpServer.addTool(
            name = "list_reminders",
            description = "Показать все активные напоминания пользователя",
            inputSchema = Tool.Input(
                properties = buildJsonObject {},
            ),
            handler = { _ ->
                val reminders = reminderService.getAllReminders()
                val text = if (reminders.isEmpty()) {
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
                CallToolResult(content = listOf(TextContent(text)))
            }
        )

        // Инструмент 4.3: Удалить напоминание
        mcpServer.addTool(
            name = "delete_reminder",
            description = "Удалить напоминание по его идентификатору",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "number")
                        put("description", "Идентификатор напоминания")
                    })
                },
                required = listOf("id")
            ),
            handler = { arguments ->
                val id = arguments.arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'id'"))
                    )

                reminderService.deleteReminder(id)
                CallToolResult(content = listOf(TextContent("Напоминание #$id удалено")))
            }
        )

        logger.info("✅ MCP инструменты зарегистрированы")
        
    } catch (e: Exception) {
        logger.error("❌ Ошибка регистрации MCP инструментов: ${e.message}", e)
    }
    
    // Добавляем MCP SSE эндпоинт
    routing {
        mcp("/mcp") { mcpServer }
    }
    
    logger.info("🚀 MCP сервер запущен на /mcp")

    return mcpServer
}

/**
 * MCP сервер для Яндекс.Трекера.
 * Эндпоинт: /mcp/tracker
 */
fun Application.configureTrackerMcpServer(
    trackerTools: TrackerToolsService
): Server {
    val logger = LoggerFactory.getLogger("TrackerMcpServer")

    val mcpServer = Server(
        serverInfo = Implementation("yandex-tracker-mcp", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null),
                prompts = null,
                resources = null
            )
        )
    )

    try {
        logger.info("🔧 Регистрация MCP инструментов для трекера...")

        // Инструмент 1: Количество задач
        mcpServer.addTool(
            name = "get_issues_count",
            description = "Получить общее количество задач в Яндекс.Трекере",
            inputSchema = Tool.Input(
                properties = buildJsonObject {}
            ),
            handler = { _ ->
                val result = trackerTools.executeTool("get_issues_count", emptyMap())
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент 2: Список всех задач
        mcpServer.addTool(
            name = "get_all_issue_names",
            description = "Получить имена всех задач в Яндекс.Трекере",
            inputSchema = Tool.Input(
                properties = buildJsonObject {}
            ),
            handler = { _ ->
                val result = trackerTools.executeTool("get_all_issue_names", emptyMap())
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент 3: Информация о задаче
        mcpServer.addTool(
            name = "get_issue_info",
            description = "Получить информацию о конкретной задаче по ключу",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("issue_key", buildJsonObject {
                        put("type", "string")
                        put("description", "Ключ задачи (например TEST-123)")
                    })
                },
                required = listOf("issue_key")
            ),
            handler = { arguments ->
                val issueKey = arguments.arguments["issue_key"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'issue_key'"))
                    )

                val result = trackerTools.executeTool(
                    toolName = "get_issue_info",
                    arguments = mapOf("issue_key" to issueKey)
                )
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        logger.info("✅ MCP инструменты трекера зарегистрированы")

    } catch (e: Exception) {
        logger.error("❌ Ошибка регистрации MCP инструментов трекера: ${e.message}", e)
    }

    routing {
        mcp("/mcp/tracker") { mcpServer }
    }

    logger.info("🚀 Tracker MCP сервер запущен на /mcp/tracker")

    return mcpServer
}

/**
 * MCP сервер для напоминаний.
 * Эндпоинт: /mcp/reminders
 */
fun Application.configureRemindersMcpServer(
    reminderService: ReminderService
): Server {
    val logger = LoggerFactory.getLogger("RemindersMcpServer")

    val mcpServer = Server(
        serverInfo = Implementation("reminders-mcp", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null),
                prompts = null,
                resources = null
            )
        )
    )

    try {
        logger.info("🔧 Регистрация MCP инструментов для напоминаний...")

        // Инструмент: Создать напоминание
        mcpServer.addTool(
            name = "create_reminder",
            description = "Создать напоминание пользователю о чём-либо в указанное время",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("message", buildJsonObject {
                        put("type", "string")
                        put("description", "Текст напоминания")
                    })
                    put("remind_at_iso", buildJsonObject {
                        put("type", "string")
                        put("description", "Время напоминания в формате ISO 8601, например 2025-11-20T10:00:00+03:00")
                    })
                },
                required = listOf("message", "remind_at_iso")
            ),
            handler = { arguments ->
                val message = arguments.arguments["message"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'message'"))
                    )
                val remindAtIso = arguments.arguments["remind_at_iso"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'remind_at_iso'"))
                    )

                val remindAtMillis = try {
                    val odt = OffsetDateTime.parse(remindAtIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    odt.toInstant().toEpochMilli()
                } catch (e: Exception) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не удалось разобрать дату/время remind_at_iso: $remindAtIso"))
                    )
                }

                val reminder = reminderService.createReminder(message, remindAtMillis)
                CallToolResult(
                    content = listOf(
                        TextContent("Напоминание #${reminder.id} создано на время ${reminder.remindAt}")
                    )
                )
            }
        )

        // Инструмент: Список напоминаний
        mcpServer.addTool(
            name = "list_reminders",
            description = "Показать все активные напоминания пользователя",
            inputSchema = Tool.Input(
                properties = buildJsonObject {},
            ),
            handler = { _ ->
                val reminders = reminderService.getAllReminders()
                val text = if (reminders.isEmpty()) {
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
                CallToolResult(content = listOf(TextContent(text)))
            }
        )

        // Инструмент: Удалить напоминание
        mcpServer.addTool(
            name = "delete_reminder",
            description = "Удалить напоминание по его идентификатору",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "number")
                        put("description", "Идентификатор напоминания")
                    })
                },
                required = listOf("id")
            ),
            handler = { arguments ->
                val id = arguments.arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'id'"))
                    )

                reminderService.deleteReminder(id)
                CallToolResult(content = listOf(TextContent("Напоминание #$id удалено")))
            }
        )

        logger.info("✅ MCP инструменты напоминаний зарегистрированы")

    } catch (e: Exception) {
        logger.error("❌ Ошибка регистрации MCP инструментов напоминаний: ${e.message}", e)
    }

    routing {
        mcp("/mcp/reminders") { mcpServer }
    }

    logger.info("🚀 Reminders MCP сервер запущен на /mcp/reminders")

    return mcpServer
}

/**
 * MCP сервер для Git/GitHub.
 * Эндпоинт: /mcp/git
 */
fun Application.configureGitMcpServer(
    gitTools: com.aichallengekmp.tools.GitToolsService
): Server {
    val logger = LoggerFactory.getLogger("GitMcpServer")

    val mcpServer = Server(
        serverInfo = Implementation("git-mcp", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null),
                prompts = null,
                resources = null
            )
        )
    )

    try {
        logger.info("🔧 Регистрация MCP инструментов для Git/GitHub...")

        // Инструмент: Получить diff PR
        mcpServer.addTool(
            name = "git_get_pr_diff",
            description = "Получить diff изменений Pull Request по номеру PR",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("pr_number", buildJsonObject {
                        put("type", "string")
                        put("description", "Номер Pull Request")
                    })
                    put("repository", buildJsonObject {
                        put("type", "string")
                        put("description", "Репозиторий в формате owner/repo (опционально, по умолчанию из текущего репозитория)")
                    })
                },
                required = listOf("pr_number")
            ),
            handler = { arguments ->
                val prNumber = arguments.arguments["pr_number"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'pr_number'"))
                    )
                val repository = arguments.arguments["repository"]?.jsonPrimitive?.content

                val result = gitTools.executeTool(
                    toolName = "git_get_pr_diff",
                    arguments = buildMap {
                        put("pr_number", prNumber)
                        repository?.let { put("repository", it) }
                    }
                )
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Получить список измененных файлов
        mcpServer.addTool(
            name = "git_get_changed_files",
            description = "Получить список файлов, измененных в Pull Request",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("pr_number", buildJsonObject {
                        put("type", "string")
                        put("description", "Номер Pull Request")
                    })
                    put("repository", buildJsonObject {
                        put("type", "string")
                        put("description", "Репозиторий в формате owner/repo (опционально)")
                    })
                },
                required = listOf("pr_number")
            ),
            handler = { arguments ->
                val prNumber = arguments.arguments["pr_number"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'pr_number'"))
                    )
                val repository = arguments.arguments["repository"]?.jsonPrimitive?.content

                val result = gitTools.executeTool(
                    toolName = "git_get_changed_files",
                    arguments = buildMap {
                        put("pr_number", prNumber)
                        repository?.let { put("repository", it) }
                    }
                )
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Получить содержимое файла
        mcpServer.addTool(
            name = "git_get_file_content",
            description = "Получить содержимое конкретного файла из репозитория",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("file_path", buildJsonObject {
                        put("type", "string")
                        put("description", "Путь к файлу в репозитории")
                    })
                    put("ref", buildJsonObject {
                        put("type", "string")
                        put("description", "Ветка или коммит (опционально, по умолчанию HEAD)")
                    })
                },
                required = listOf("file_path")
            ),
            handler = { arguments ->
                val filePath = arguments.arguments["file_path"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'file_path'"))
                    )
                val ref = arguments.arguments["ref"]?.jsonPrimitive?.content

                val result = gitTools.executeTool(
                    toolName = "git_get_file_content",
                    arguments = buildMap {
                        put("file_path", filePath)
                        ref?.let { put("ref", it) }
                    }
                )
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Получить информацию о PR
        mcpServer.addTool(
            name = "github_get_pr_info",
            description = "Получить метаданные Pull Request (заголовок, описание, автор, статус)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("pr_number", buildJsonObject {
                        put("type", "string")
                        put("description", "Номер Pull Request")
                    })
                    put("repository", buildJsonObject {
                        put("type", "string")
                        put("description", "Репозиторий в формате owner/repo (опционально)")
                    })
                },
                required = listOf("pr_number")
            ),
            handler = { arguments ->
                val prNumber = arguments.arguments["pr_number"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'pr_number'"))
                    )
                val repository = arguments.arguments["repository"]?.jsonPrimitive?.content

                val result = gitTools.executeTool(
                    toolName = "github_get_pr_info",
                    arguments = buildMap {
                        put("pr_number", prNumber)
                        repository?.let { put("repository", it) }
                    }
                )
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        logger.info("✅ MCP инструменты Git/GitHub зарегистрированы")

    } catch (e: Exception) {
        logger.error("❌ Ошибка регистрации MCP инструментов Git/GitHub: ${e.message}", e)
    }

    routing {
        mcp("/mcp/git") { mcpServer }
    }

    logger.info("🚀 Git MCP сервер запущен на /mcp/git")

    return mcpServer
}

/**
 * Настройка Support MCP сервера для системы поддержки
 */
fun Application.configureSupportMcpServer(
    supportTools: com.aichallengekmp.tools.SupportToolsService
): Server {
    val logger = LoggerFactory.getLogger("SupportMcpServer")

    val mcpServer = Server(
        serverInfo = Implementation("support-mcp", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null),
                prompts = null,
                resources = null
            )
        )
    )

    try {
        logger.info("🔧 Регистрация MCP инструментов для системы поддержки...")

        // Инструмент: Получить информацию о пользователе
        mcpServer.addTool(
            name = "get_user",
            description = "Получить информацию о пользователе по ID",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("user_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя")
                    })
                },
                required = listOf("user_id")
            ),
            handler = { arguments ->
                val userId = arguments.arguments["user_id"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'user_id'"))
                    )

                val result = runBlocking {
                    supportTools.executeTool(
                        toolName = "get_user",
                        arguments = mapOf("user_id" to userId)
                    )
                }
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Получить тикеты пользователя
        mcpServer.addTool(
            name = "get_user_tickets",
            description = "Получить список всех тикетов пользователя",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("user_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя")
                    })
                    put("status", buildJsonObject {
                        put("type", "string")
                        put("description", "Фильтр по статусу (open, in_progress, resolved) - опционально")
                    })
                },
                required = listOf("user_id")
            ),
            handler = { arguments ->
                val userId = arguments.arguments["user_id"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'user_id'"))
                    )
                val status = arguments.arguments["status"]?.jsonPrimitive?.content

                val result = runBlocking {
                    supportTools.executeTool(
                        toolName = "get_user_tickets",
                        arguments = buildMap {
                            put("user_id", userId)
                            status?.let { put("status", it) }
                        }
                    )
                }
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Получить детали тикета
        mcpServer.addTool(
            name = "get_ticket_details",
            description = "Получить детальную информацию о тикете включая историю",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("ticket_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID тикета")
                    })
                },
                required = listOf("ticket_id")
            ),
            handler = { arguments ->
                val ticketId = arguments.arguments["ticket_id"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'ticket_id'"))
                    )

                val result = runBlocking {
                    supportTools.executeTool(
                        toolName = "get_ticket_details",
                        arguments = mapOf("ticket_id" to ticketId)
                    )
                }
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Поиск тикетов
        mcpServer.addTool(
            name = "search_tickets",
            description = "Поиск тикетов по категории или ключевым словам",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("category", buildJsonObject {
                        put("type", "string")
                        put("description", "Категория тикета (auth, ai, rag, code_review, deployment, general, ui, account, performance) - опционально")
                    })
                    put("keyword", buildJsonObject {
                        put("type", "string")
                        put("description", "Ключевое слово для поиска в теме и описании - опционально")
                    })
                },
                required = emptyList()
            ),
            handler = { arguments ->
                val category = arguments.arguments["category"]?.jsonPrimitive?.content
                val keyword = arguments.arguments["keyword"]?.jsonPrimitive?.content

                val result = runBlocking {
                    supportTools.executeTool(
                        toolName = "search_tickets",
                        arguments = buildMap {
                            category?.let { put("category", it) }
                            keyword?.let { put("keyword", it) }
                        }
                    )
                }
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        // Инструмент: Найти похожие тикеты
        mcpServer.addTool(
            name = "get_similar_tickets",
            description = "Найти похожие решенные тикеты по описанию проблемы",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Описание проблемы пользователя")
                    })
                },
                required = listOf("description")
            ),
            handler = { arguments ->
                val description = arguments.arguments["description"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("Ошибка: не указано поле 'description'"))
                    )

                val result = runBlocking {
                    supportTools.executeTool(
                        toolName = "get_similar_tickets",
                        arguments = mapOf("description" to description)
                    )
                }
                CallToolResult(content = listOf(TextContent(result)))
            }
        )

        logger.info("✅ MCP инструменты системы поддержки зарегистрированы")

    } catch (e: Exception) {
        logger.error("❌ Ошибка регистрации MCP инструментов поддержки: ${e.message}", e)
    }

    routing {
        mcp("/mcp/support") { mcpServer }
    }

    logger.info("🚀 Support MCP сервер запущен на /mcp/support")

    return mcpServer
}
