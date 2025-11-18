package com.aichallengekmp.di

import com.aichallengekmp.ai.ModelRegistry
import com.aichallengekmp.ai.YandexGPTProvider
import com.aichallengekmp.database.DatabaseFactory
import com.aichallengekmp.database.dao.*
import com.aichallengekmp.service.ChatService
import com.aichallengekmp.service.CompressionService
import com.aichallengekmp.tools.TrackerToolsService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
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
            dbPath = System.getenv("DB_PATH") ?: "chat.db"
        )
    }
    
    // ============= HTTP Client =============
    
    val httpClient by lazy {
        logger.info("🌐 Создание HTTP клиента")
        HttpClient(CIO) {
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
    
    // ============= Services =============
    
    val trackerTools by lazy {
        logger.info("🔧 Инициализация TrackerToolsService")
        TrackerToolsService()
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
                trackerTools = trackerTools  // Передаем TrackerToolsService
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
            trackerTools = trackerTools
        )
    }
}

/**
 * Конфигурация приложения
 */
data class AppConfig(
    val yandexApiKey: String,
    val yandexFolderId: String,
    val dbPath: String
)
