package com.aichallengekmp.tools

/**
 * Определение инструмента для AI
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
)
