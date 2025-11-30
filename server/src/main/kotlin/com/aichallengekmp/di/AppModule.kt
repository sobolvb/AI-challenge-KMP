package com.aichallengekmp.di

import com.aichallengekmp.ai.ModelRegistry
import com.aichallengekmp.ai.YandexGPTProvider
import com.aichallengekmp.database.DatabaseFactory
import com.aichallengekmp.database.dao.*
import com.aichallengekmp.database.dao.RagChunkDao
import com.aichallengekmp.mcp.McpClientRegistry
import com.aichallengekmp.mcp.McpServerClient
import com.aichallengekmp.mcp.McpServerConfig
import com.aichallengekmp.rag.OllamaEmbeddingsProvider
import com.aichallengekmp.rag.RagIndexService
import com.aichallengekmp.rag.RagSearchService
import com.aichallengekmp.service.ChatService
import com.aichallengekmp.service.CompressionService
import com.aichallengekmp.service.ReminderService
import com.aichallengekmp.tools.LocalToolExecutor
import com.aichallengekmp.tools.McpAwareToolExecutor
import com.aichallengekmp.tools.ToolExecutor
import com.aichallengekmp.tools.TrackerToolsService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory


/**
 * Manual DI container - заменяет Koin из-за несовместимости с Ktor 3.x
 */
object AppContainer {
    // ============= Configuration =============
    
    private val logger = LoggerFactory.getLogger("AppContainer")
    
    val config by lazy {
        logger.info("🔧 Загрузка конфигурации")
        AppConfig(
            yandexApiKey = System.getenv("YANDEX_API_KEY") ?: error("YANDEX_API_KEY not set"),
            yandexFolderId = System.getenv("YANDEX_MODEL_URI") ?: error("YANDEX_MODEL_URI not set"),
            dbPath = System.getenv("DB_PATH") ?: "chat.db",
            // MCP endpoints. Жёстко используем единый HTTP(S) эндпоинт /mcp для обоих клиентов,
            // чтобы избежать влияния переменных окружения и расхождений в конфигурации.
            // Для SseClientTransport нужны HTTP(S) URL (http:// или https://).
//            mcpTrackerUrl = "http://localhost:8080/mcp/tracker",
//            mcpRemindersUrl = "http://localhost:8080/mcp/reminders"
            mcpTrackerUrl = "http://localhost:8080/mcp",
            mcpRemindersUrl = "http://localhost:8080/mcp"
        )
    }
    
    // ============= HTTP Client =============

    val httpClient by lazy {
        logger.info("🌐 Создание HTTP клиента")
        HttpClient(CIO) {
            install(SSE)
            // нужно MCP-клиенту для WebSocketClientTransport
            install(WebSockets)

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                    explicitNulls = false
                })
            }
        }
    }

    // ============= Database =============
    
    val database by lazy {
        logger.info("💾 Инициализация базы данных")
        val driver = DatabaseFactory.createDriver(config.dbPath)
        DatabaseFactory.createDatabase(driver)
    }
    
    // ============= DAOs =============

    val sessionDao by lazy { SessionDao(database) }
    val messageDao by lazy { MessageDao(database) }
    val sessionSettingsDao by lazy { SessionSettingsDao(database) }
    val compressionDao by lazy { CompressionDao(database) }
    val reminderDao by lazy { ReminderDao(database) }
    val ragChunkDao by lazy { RagChunkDao(database) }
    val ragSourceDao by lazy { RagSourceDao(database) }
    
    // ============= RAG / Embeddings =============

    val embeddingsProvider by lazy {
        logger.info("🧠 Инициализация OllamaEmbeddingsProvider")
        OllamaEmbeddingsProvider(
            httpClient = httpClient,
            baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434",
            model = System.getenv("OLLAMA_EMBED_MODEL") ?: "nomic-embed-text"
        )
    }

    val ragIndexService by lazy {
        RagIndexService(ragChunkDao, embeddingsProvider)
    }

    val ragSearchService by lazy {
        RagSearchService(ragChunkDao, embeddingsProvider)
    }
    
    // ============= Services =============
    
    val reminderService by lazy {
        ReminderService(reminderDao)
    }

    /**
     * Локальная реализация инструментов (без MCP) — используется как
     * fallback и для MCP-серверов.
     */
    val trackerTools by lazy {
        logger.info("🔧 Инициализация TrackerToolsService (локальные инструменты)")
        TrackerToolsService(reminderService)
    }

    /**
     * MCP-клиенты к внешним MCP-серверам (tracker, reminders, ...).
     * Могут быть отключены через конфиг (feature-флаги).
     */
    private val mcpClientsById by lazy {
        logger.info("🌉 Инициализация MCP-клиентов (если включены фичи)")

        val trackerConfig = McpServerConfig(
            id = "tracker",
            baseUrl = config.mcpTrackerUrl,
            toolNames = setOf("get_issues_count", "get_all_issue_names", "get_issue_info")
        )
        val remindersConfig = McpServerConfig(
            id = "reminders",
            baseUrl = config.mcpRemindersUrl,
            toolNames = setOf("create_reminder", "list_reminders", "delete_reminder")
        )

        mapOf(
            trackerConfig.id to McpServerClient(trackerConfig, httpClient),
            remindersConfig.id to McpServerClient(remindersConfig, httpClient)
        )
    }

    /**
     * Реестр MCP-клиентов с маршрутизацией toolName -> serverId.
     */
    val mcpClientRegistry: McpClientRegistry by lazy {
        val toolToServer = mapOf(
            "get_issues_count" to "tracker",
            "get_all_issue_names" to "tracker",
            "get_issue_info" to "tracker",
            "create_reminder" to "reminders",
            "list_reminders" to "reminders",
            "delete_reminder" to "reminders"
        )
        McpClientRegistry(mcpClientsById, toolToServer)
    }

    /**
     * Оркестратор инструментов для YandexGPT — всё через MCP.
     */
    val toolExecutor: ToolExecutor by lazy {
        //McpAwareToolExecutor(mcpClientRegistry)
        LocalToolExecutor(reminderService)

    }

    // ============= AI Providers =============
    
    val modelRegistry by lazy {
        logger.info("🤖 Инициализация ModelRegistry")
        val registry = ModelRegistry()
        
        if (config.yandexApiKey.isNotBlank() && config.yandexFolderId.isNotBlank()) {
            logger.info("✅ Регистрация YandexGPT провайдера")
            val yandexProvider = YandexGPTProvider(
                httpClient = httpClient,
                apiKey = config.yandexApiKey,
                folderId = config.yandexFolderId,
                toolExecutor = toolExecutor  // Все вызовы инструментов идут через оркестратор (MCP внутри)
            )
            registry.registerProvider(yandexProvider)
        } else {
            logger.warn("⚠️ YandexGPT API ключи не найдены!")
        }
        
        registry
    }
    
    val compressionService by lazy {
        CompressionService(
            messageDao = messageDao,
            compressionDao = compressionDao,
            modelRegistry = modelRegistry
        )
    }
    
    val chatService by lazy {
        ChatService(
            sessionDao = sessionDao,
            messageDao = messageDao,
            settingsDao = sessionSettingsDao,
            compressionService = compressionService,
            modelRegistry = modelRegistry,
            trackerTools = trackerTools,
            ragSearchService = ragSearchService,
            ragSourceDao = ragSourceDao
        )
    }
}


/**
 * Конфигурация приложения
 */
data class AppConfig(
    val yandexApiKey: String,
    val yandexFolderId: String,
    val dbPath: String,
    val mcpTrackerUrl: String,
    val mcpRemindersUrl: String
)
