package com.aichallengekmp.mcp

import com.aichallengekmp.tools.ToolExecutor
import org.slf4j.LoggerFactory

/**
 * Реестр MCP-клиентов и маппинг инструментов на конкретные MCP-серверы.
 *
 * Позволяет в рамках одного LLM-запроса вызывать в произвольном порядке
 * инструменты из разных MCP-серверов, абстрагируя это за единым интерфейсом.
 */
class McpClientRegistry(
    private val clientsById: Map<String, McpServerClient>,
    private val toolToServerId: Map<String, String>
) : ToolExecutor {

    private val logger = LoggerFactory.getLogger(McpClientRegistry::class.java)

    override suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
        val serverId = toolToServerId[toolName]
        if (serverId == null) {
            logger.warn("⚠️ Для инструмента '{}' не найден MCP-сервер в реестре", toolName)
            return "Ошибка: для инструмента '$toolName' не настроен MCP-сервер"
        }

        val client = clientsById[serverId]
        if (client == null) {
            logger.error(
                "❌ Для MCP-сервера '{}' не найден клиент при вызове инструмента '{}'",
                serverId,
                toolName
            )
            return "Ошибка: MCP-сервер '$serverId' недоступен для инструмента '$toolName'"
        }

        return client.callTool(toolName, arguments)
    }
}
