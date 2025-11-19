package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с напоминаниями
 */
class ReminderDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(ReminderDao::class.java)
    private val queries = database.reminderQueries

    suspend fun getAll(): List<Reminder> = withContext(Dispatchers.IO) {
        logger.debug("⏰ Запрос всех напоминаний")
        queries.selectAllReminders().executeAsList()
    }

    suspend fun getById(id: Long): Reminder? = withContext(Dispatchers.IO) {
        logger.debug("⏰ Запрос напоминания по id=$id")
        queries.selectReminderById(id).executeAsOneOrNull()
    }

    suspend fun insert(message: String, remindAtMillis: Long) = withContext(Dispatchers.IO) {
        logger.info("⏰ Создание напоминания на ${'$'}remindAtMillis: ${'$'}message")
        queries.insertReminder(message, remindAtMillis)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        logger.warn("⏰ Удаление напоминания id=${'$'}id")
        queries.deleteReminderById(id)
    }

    suspend fun getDueReminders(nowMillis: Long): List<Reminder> = withContext(Dispatchers.IO) {
        logger.debug("⏰ Запрос актуальных напоминаний на момент $nowMillis")
        queries.selectDueReminders(nowMillis).executeAsList()
    }
}