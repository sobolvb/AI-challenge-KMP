package com.aichallengekmp.service

import com.aichallengekmp.database.Reminder
import com.aichallengekmp.database.dao.ReminderDao
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с напоминаниями (CRUD + бизнес-логика)
 */
class ReminderService(
    private val reminderDao: ReminderDao
) {
    private val logger = LoggerFactory.getLogger(ReminderService::class.java)

    suspend fun createReminder(message: String, remindAtMillis: Long): Reminder {
        require(message.isNotBlank()) { "Сообщение напоминания не может быть пустым" }
        require(remindAtMillis > 0) { "Некорректное время напоминания" }

        logger.info("Создание напоминания: message='$message', remindAt=$remindAtMillis")
        reminderDao.insert(message, remindAtMillis)

        // Для простоты возвращаем последнее напоминание с таким временем и текстом
        return reminderDao.getAll()
            .lastOrNull { it.message == message && it.remindAt == remindAtMillis }
            ?: error("Не удалось считать созданное напоминание")
    }

    suspend fun getReminder(id: Long): Reminder? = reminderDao.getById(id)

    suspend fun getAllReminders(): List<Reminder> = reminderDao.getAll()

    suspend fun deleteReminder(id: Long) {
        reminderDao.delete(id)
    }

    suspend fun getDueReminders(nowMillis: Long): List<Reminder> = reminderDao.getDueReminders(nowMillis)
}