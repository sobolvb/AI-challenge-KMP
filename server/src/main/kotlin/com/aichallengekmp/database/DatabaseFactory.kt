package com.aichallengekmp.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Фабрика для создания и инициализации базы данных
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    
    fun createDriver(dbPath: String = "chat.db"): SqlDriver {
        logger.info("📊 Инициализация базы данных: $dbPath")
        
        val databasePath = File(dbPath)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}")
        
        // Создаем схему если БД новая
        if (!databasePath.exists() || databasePath.length() == 0L) {
            logger.info("🆕 Создание новой базы данных")
            AppDatabase.Schema.create(driver)
        } else {
            logger.info("✅ База данных существует, выполняем миграции если необходимо")
            // TODO: Добавить миграции при изменении схемы
            val currentVersion = getCurrentVersion(driver)
            val schemaVersion = AppDatabase.Schema.version
            
            if (currentVersion < schemaVersion) {
                logger.info("🔄 Миграция с версии $currentVersion на $schemaVersion")
                // TODO: когда появятся полноценные миграции SQLDelight (.sqm), заменить на Schema.migrate
                // Временная миграция: создаём недостающие таблицы (Reminder, DocumentChunk) и обновляем user_version
                try {
                    driver.execute(
                        identifier = null,
                        sql = """
                            CREATE TABLE IF NOT EXISTS Reminder (
                                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                                message TEXT NOT NULL,
                                remindAt INTEGER NOT NULL
                            );
                        """.trimIndent(),
                        parameters = 0
                    )

                    driver.execute(
                        identifier = null,
                        sql = """
                            CREATE TABLE IF NOT EXISTS DocumentChunk (
                                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                                sourceId TEXT NOT NULL,
                                chunkIndex INTEGER NOT NULL,
                                text TEXT NOT NULL,
                                embedding TEXT NOT NULL
                            );
                        """.trimIndent(),
                        parameters = 0
                    )

                    driver.execute(
                        identifier = null,
                        sql = """
                            CREATE TABLE IF NOT EXISTS AnalyticsEvent (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                timestamp INTEGER NOT NULL,
                                eventType TEXT NOT NULL,
                                httpMethod TEXT,
                                httpPath TEXT,
                                httpStatus INTEGER,
                                responseTimeMs INTEGER,
                                modelId TEXT,
                                inputTokens INTEGER,
                                outputTokens INTEGER,
                                llmTemperature REAL,
                                llmResponseTimeMs INTEGER,
                                errorType TEXT,
                                errorMessage TEXT,
                                sessionId TEXT,
                                metadata TEXT
                            );
                        """.trimIndent(),
                        parameters = 0
                    )

                    // Индексы для AnalyticsEvent
                    driver.execute(
                        identifier = null,
                        sql = "CREATE INDEX IF NOT EXISTS analytics_event_type ON AnalyticsEvent(eventType);",
                        parameters = 0
                    )
                    driver.execute(
                        identifier = null,
                        sql = "CREATE INDEX IF NOT EXISTS analytics_timestamp ON AnalyticsEvent(timestamp);",
                        parameters = 0
                    )
                    driver.execute(
                        identifier = null,
                        sql = "CREATE INDEX IF NOT EXISTS analytics_session ON AnalyticsEvent(sessionId);",
                        parameters = 0
                    )
                    driver.execute(
                        identifier = null,
                        sql = "CREATE INDEX IF NOT EXISTS analytics_model ON AnalyticsEvent(modelId);",
                        parameters = 0
                    )

                    driver.execute(
                        identifier = null,
                        sql = "PRAGMA user_version = $schemaVersion",
                        parameters = 0
                    )
                    logger.info("✅ Временная миграция: таблицы Reminder, DocumentChunk и AnalyticsEvent созданы (если отсутствовали), user_version обновлён до $schemaVersion")
                } catch (e: Exception) {
                    logger.error("❌ Ошибка временной миграции для таблиц Reminder/DocumentChunk: ${e.message}", e)
                }
                // AppDatabase.Schema.migrate(driver, currentVersion, schemaVersion)
            }
        }
        
        return driver
    }
    
    fun createDatabase(driver: SqlDriver): AppDatabase {
        logger.info("✨ База данных инициализирована")
        return AppDatabase(driver)
    }

    private fun getCurrentVersion(driver: SqlDriver): Long {
        return try {
            val result: QueryResult<Long> = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor: SqlCursor ->
                    QueryResult.Value(
                        if (cursor.next() as Boolean) cursor.getLong(0) ?: 0L else 0L
                    )
                },
                parameters = 0
            )
            result.value // извлекаем Long из QueryResult
        } catch (e: Exception) {
            0L
        }
    }



}
