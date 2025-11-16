package com.aichallengekmp.di

import com.aichallengekmp.ai.ModelRegistry
import com.aichallengekmp.ai.YandexGPTProvider
import com.aichallengekmp.database.DatabaseFactory
import com.aichallengekmp.database.dao.*
import com.aichallengekmp.service.ChatService
import com.aichallengekmp.service.CompressionService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Koin DI модуль для всего приложения
 */
val appModule = module {
    val logger = LoggerFactory.getLogger("AppModule")
    
    // ============= Configuration =============
    
    single {
        println("🔧 Загрузка конфигурации")
        logger.info("🔧 Загрузка конфигурации")
        AppConfig(
            yandexApiKey = System.getenv("YANDEX_API_KEY") ?: error("WQEWQEWQE"),
            yandexFolderId = System.getenv("YANDEX_MODEL_URI") ?: error("WQEWQEWQE"),
            dbPath = System.getenv("DB_PATH") ?: "chat.db"
        )
    }
    
    // ============= HTTP Client =============
    
    single {
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
    
    single {
        logger.info("💾 Инициализация базы данных")
        val config: AppConfig = get()
        val driver = DatabaseFactory.createDriver(config.dbPath)
        DatabaseFactory.createDatabase(driver)
    }
    
    // ============= DAOs =============
    
    single { SessionDao(get()) }
    single { MessageDao(get()) }
    single { SessionSettingsDao(get()) }
    single { CompressionDao(get()) }
    
    // ============= AI Providers =============
    
    single {
        logger.info("🤖 Инициализация ModelRegistry")
        val config: AppConfig = get()
        val httpClient: HttpClient = get()
        
        val registry = ModelRegistry()
        
        // Регистрируем YandexGPT провайдер
        if (config.yandexApiKey.isNotBlank() && config.yandexFolderId.isNotBlank()) {
            logger.info("✅ Регистрация YandexGPT провайдера")
            val yandexProvider = YandexGPTProvider(
                httpClient = httpClient,
                apiKey = config.yandexApiKey,
                folderId = config.yandexFolderId
            )
            registry.registerProvider(yandexProvider)
        } else {
            logger.warn("⚠️ YandexGPT API ключи не найдены! Используйте переменные окружения:")
            logger.warn("   YANDEX_API_KEY")
            logger.warn("   YANDEX_FOLDER_ID")
        }
        
        // TODO: Добавить другие провайдеры здесь
        // val openAiProvider = OpenAIProvider(...)
        // registry.registerProvider(openAiProvider)
        
        registry
    }
    
    // ============= Services =============
    
    single {
        CompressionService(
            messageDao = get(),
            compressionDao = get(),
            modelRegistry = get()
        )
    }
    
    single {
        ChatService(
            sessionDao = get(),
            messageDao = get(),
            settingsDao = get(),
            compressionService = get(),
            modelRegistry = get()
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
