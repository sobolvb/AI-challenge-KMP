package com.aichallengekmp.mcp

/**
 * Конфигурация одного MCP-сервера.
 * Пример: tracker, reminders, rag-search и т.п.
 *
 * baseUrl — URL MCP endpoint. Для WebSocket-клиента должен быть вида
 * ws://host:port/mcp/... или wss://...
 */
data class McpServerConfig(
    val id: String,             // Уникальный идентификатор сервера ("tracker", "reminders")
    val baseUrl: String,        // URL MCP endpoint (ws://... или wss://...)
    val toolNames: Set<String>  // Имена инструментов, которые реализует этот сервер
)
