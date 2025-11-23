package com.aichallengekmp.tools

import com.aichallengekmp.mcp.McpClientRegistry
import org.slf4j.LoggerFactory

/**
 * Оркестратор исполнения инструментов между локальной реализацией
 * (TrackerToolsService) и MCP-клиентами (через McpClientRegistry).
 *
 * Важно: этот класс ничего не знает о function calling и LLM — он просто
 * даёт единый вход для выполнения инструмента по имени.
 */
class McpAwareToolExecutor(
    private val mcpRegistry: McpClientRegistry
) : ToolExecutor {

    private val logger = LoggerFactory.getLogger(McpAwareToolExecutor::class.java)

    override suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
        logger.info("McpAwareToolExecutor: выполнение инструмента {} через MCP", toolName)
        return mcpRegistry.executeTool(toolName, arguments)
    }
}
