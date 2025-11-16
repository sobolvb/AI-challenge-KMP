package com.aichallengekmp.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с сессиями
 */
class SessionDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(SessionDao::class.java)
    private val queries = database.sessionQueries
    
    /**
     * Получить все сессии как Flow
     */
    fun getAllAsFlow(): Flow<List<Session>> {
        logger.debug("📋 Запрос всех сессий (Flow)")
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }
    
    /**
     * Получить все сессии
     */
    suspend fun getAll(): List<Session> = withContext(Dispatchers.IO) {
        logger.debug("📋 Запрос всех сессий")
        queries.selectAll().executeAsList()
    }
    
    /**
     * Получить сессию по ID
     */
    suspend fun getById(id: String): Session? = withContext(Dispatchers.IO) {
        logger.debug("🔍 Запрос сессии: $id")
        queries.selectById(id).executeAsOneOrNull()
    }
    
    /**
     * Создать новую сессию
     */
    suspend fun insert(session: Session) = withContext(Dispatchers.IO) {
        logger.info("➕ Создание сессии: ${session.id} - ${session.name}")
        queries.insert(
            id = session.id,
            name = session.name,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt
        )
    }
    
    /**
     * Обновить название сессии
     */
    suspend fun updateName(id: String, name: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        logger.info("✏️ Обновление названия сессии: $id -> $name")
        queries.updateName(name, updatedAt, id)
    }
    
    /**
     * Обновить timestamp последнего обновления
     */
    suspend fun updateTimestamp(id: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        logger.debug("⏱️ Обновление timestamp сессии: $id")
        queries.updateTimestamp(updatedAt, id)
    }
    
    /**
     * Удалить сессию
     */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        logger.warn("🗑️ Удаление сессии: $id")
        queries.delete(id)
    }
    
    /**
     * Получить количество сессий
     */
    suspend fun count(): Long = withContext(Dispatchers.IO) {
        queries.count().executeAsOne()
    }
}
