package com.aichallengekmp.mcp

import com.aichallengekmp.tools.TrackerToolsService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Настройка MCP сервера для Яндекс.Трекер
 * Добавляет SSE эндпоинт /mcp для подключения MCP клиентов
 */
fun Application.configureMcpServer(trackerTools: TrackerToolsService) {
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
            handler = { _ ->
                // TODO: Получить issue_key из аргументов
                // Пока используем фиксированный ключ для демонстрации
                val result = trackerTools.executeTool("get_all_issue_names", emptyMap())
                CallToolResult(content = listOf(TextContent(result)))
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
}
