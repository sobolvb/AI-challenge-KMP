package com.aichallengekmp.scheduler

import com.aichallengekmp.service.ReminderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Фоновый планировщик, который периодически проверяет базу на все напоминания
 * и отправляет summary клиентам (через MCP, SSE и т.п.).
 */
class ReminderScheduler(
    private val reminderService: ReminderService,
    private val intervalMinutes: Long = 1,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /**
     * Колбек, который будет вызван при формировании summary напоминаний.
     * Здесь можно интегрировать отправку через MCP, WebSocket, email и т.п.
     */
    private val onSummaryReady: suspend (String) -> Unit = {}
) {
    private val logger = LoggerFactory.getLogger(ReminderScheduler::class.java)

    fun start() {
        logger.info("⏰ Запуск фонового планировщика напоминаний (каждые ${intervalMinutes} минут)")
        scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                    logger.error("Ошибка в цикле ReminderScheduler: ${'$'}{e.message}", e)
                }
                delay(intervalMinutes * 20000L)
            }
        }
    }

    private suspend fun tick() {
        logger.info("⏰ Tick планировщика напоминаний")
        val allReminders = reminderService.getAllReminders()
        logger.info("⏰ Количество напоминаний в БД: ${allReminders.size}")

        if (allReminders.isEmpty()) {
            logger.debug("⏰ Напоминаний нет")
            return
        }

        val sorted = allReminders.sortedBy { it.remindAt }
        val nearest = sorted.first()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun formatTime(millis: Long): String =
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(formatter)

        val summary = buildString {
            appendLine("Приоритетное (ближайшее) напоминание: [${formatTime(nearest.remindAt)}] ${nearest.message}")
            appendLine()
            appendLine("Полный список напоминаний (${sorted.size}):")
            for (reminder in sorted) {
                append("- [").append(formatTime(reminder.remindAt)).append("] ")
                    .appendLine(reminder.message)
            }
        }

        logger.info("⏰ Готов summary всех напоминаний, передаём в onSummaryReady")
        onSummaryReady(summary)
    }
}
