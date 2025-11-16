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
