package com.aichallengekmp

import com.aichallengekmp.mcp.McpClientRegistry
import com.aichallengekmp.mcp.McpServerClient
import com.aichallengekmp.mcp.McpServerConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тестирует именно оркестрацию инструментов между несколькими MCP-серверами.
 *
 * Здесь мы не ходим в реальный Ktor-сервер и не вызываем YandexGPT.
 * Вместо этого подменяем реальные MCP-клиенты на фейковые реализации,
 * чтобы проверить, что McpClientRegistry правильно маршрутизирует
 * вызовы инструментов на разные MCP-серверы по имени инструмента.
 */
class McpOrchestrationTest {

    /**
     * Фейковый MCP-клиент: не выполняет сетевых запросов,
     * просто возвращает строку "<serverId>:<toolName>".
     */
    private class FakeMcpClient(
        serverId: String
    ) : McpServerClient(
        config = McpServerConfig(
            id = serverId,
            baseUrl = "ws://fake-$serverId", // не используется в тесте
            toolNames = emptySet()
        ),
        httpClient = HttpClient(CIO) // не используется, но нужен для конструктора
    ) {

        override suspend fun callTool(toolName: String, arguments: Map<String, Any?>): String {
            // Никаких сетевых вызовов — только маркер, куда попал вызов
            return "${'$'}{config.id}:${'$'}toolName"
        }
    }

    @Test
    fun `tools are routed to correct MCP servers`() = runTest {
        val trackerClient = FakeMcpClient("tracker")
        val remindersClient = FakeMcpClient("reminders")

        val registry = McpClientRegistry(
            clientsById = mapOf(
                "tracker" to trackerClient,
                "reminders" to remindersClient
            ),
            toolToServerId = mapOf(
                // Трекинг-задачи
                "get_issues_count" to "tracker",
                "get_all_issue_names" to "tracker",
                "get_issue_info" to "tracker",
                // Напоминания
                "create_reminder" to "reminders",
                "list_reminders" to "reminders",
                "delete_reminder" to "reminders"
            )
        )

        // Инструменты трекера должны уходить на MCP-сервер "tracker"
        assertEquals("tracker:get_issues_count", registry.executeTool("get_issues_count", emptyMap()))
        assertEquals("tracker:get_all_issue_names", registry.executeTool("get_all_issue_names", emptyMap()))
        assertEquals("tracker:get_issue_info", registry.executeTool("get_issue_info", mapOf("issue_key" to "TEST-1")))

        // Инструменты напоминаний должны уходить на MCP-сервер "reminders"
        assertEquals("reminders:create_reminder", registry.executeTool("create_reminder", mapOf("message" to "test", "remind_at_iso" to "2025-01-01T10:00:00+03:00")))
        assertEquals("reminders:list_reminders", registry.executeTool("list_reminders", emptyMap()))
        assertEquals("reminders:delete_reminder", registry.executeTool("delete_reminder", mapOf("id" to 1)))
    }
}

