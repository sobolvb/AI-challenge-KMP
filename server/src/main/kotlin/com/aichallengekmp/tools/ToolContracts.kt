package com.aichallengekmp.tools

/**
 * Базовый контракт для каталога инструментов, который может предоставить
 * список доступных tools для LLM.
 */
interface ToolCatalog {
    fun getAvailableTools(): List<ToolDefinition>
}

/**
 * Контракт для исполнителя инструментов.
 *
 * Он абстрагирует место фактического исполнения:
 *  - локально (in-process, HTTP к сторонним API и т.п.)
 *  - через MCP-клиента (обращение к внешнему MCP-серверу)
 *  - гибридные варианты.
 */
interface ToolExecutor {
    suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String
}
