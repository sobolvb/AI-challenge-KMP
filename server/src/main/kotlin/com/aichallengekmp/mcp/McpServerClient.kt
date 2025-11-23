package com.aichallengekmp.mcp

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Обёртка над MCP-клиентом для подключения к одному MCP-серверу.
 *
 * Использует WebSocketClientTransport и baseUrl из McpServerConfig, который
 * должен указывать на MCP endpoint (ws://... или wss://...).
 *
 * Класс объявлен open, чтобы в тестах можно было делать фейковые реализации,
 * не выполняющие реальных сетевых вызовов.
 */
open class McpServerClient(
    private val config: McpServerConfig,
    private val httpClient: HttpClient
) {

    private val logger = LoggerFactory.getLogger(McpServerClient::class.java)

    private val client = Client(
        clientInfo = Implementation(
            name = "ai-challenge-kmp-internal-${config.id}",
            version = "1.0.0"
        )
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val connectMutex = Mutex()
    @Volatile
    private var connected: Boolean = false

    /**
     * Ленивая и потокобезопасная инициализация подключения.
     *
     * Подключаемся к MCP-серверу по SSE, используя HTTP(S) baseUrl.
     */
    private suspend fun ensureConnected() {
        if (connected) return
        connectMutex.withLock {
            if (connected) return

            logger.info("🔌 Подключение MCP-клиента '{}' к {} (SSE)", config.id, config.baseUrl)

            val transport = SseClientTransport(httpClient, config.baseUrl)
            client.connect(transport)

            connected = true
            logger.info("✅ MCP-клиент '{}' подключён к MCP endpoint {}", config.id, config.baseUrl)
        }
    }

    /**
     * Вызвать инструмент на этом MCP-сервере.
     * Возвращает агрегированный текст из TextContent.
     */
    open suspend fun callTool(toolName: String, arguments: Map<String, Any?>): String {
        ensureConnected()

        return try {
            val argsJson = JsonObject(
                arguments.mapValues { (_, v) -> JsonPrimitive(v?.toString() ?: "") }
            )

            logger.info(
                "➡️ [MCP:{}] Вызов инструмента '{}' с аргументами {}",
                config.id,
                toolName,
                argsJson
            )

            val result = client.callTool(
                name = toolName,
                arguments = argsJson
            ) ?: return "MCP-клиент не получил ответа от сервера при вызове '$toolName'"

            val text = result.content
                .filterIsInstance<TextContent>()
                .joinToString("\n") { it.text ?: "" }

            text.ifBlank { "MCP-сервер вернул пустой результат" }
        } catch (e: Exception) {
            logger.error(
                "❌ Ошибка при вызове MCP-инструмента '{}' на сервере {}: {}",
                toolName,
                config.id,
                e.message,
                e
            )
            "Ошибка при вызове MCP-инструмента '$toolName' на сервере '${config.id}': ${e.message}"
        }
    }
}
