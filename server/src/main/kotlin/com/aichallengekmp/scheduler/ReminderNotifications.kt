package com.aichallengekmp.scheduler

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Простая шина событий для напоминаний.
 * ReminderScheduler шлёт сюда сообщения, а SSE-клиенты подписываются и получают их.
 */
object ReminderNotifications {
    private val logger = LoggerFactory.getLogger(ReminderNotifications::class.java)

    private val subscribers = CopyOnWriteArrayList<suspend (String) -> Unit>()

    /**
     * Подписаться на уведомления. Возвращает функцию отписки.
     */
    fun subscribe(handler: suspend (String) -> Unit): () -> Unit {
        subscribers += handler
        return { subscribers -= handler }
    }

    /**
     * Разослать уведомление всем подписчикам.
     */
    suspend fun broadcast(message: String) {
        logger.info("⏰ Рассылка уведомления о напоминании ${subscribers.size} подписчикам")
        for (handler in subscribers) {
            try {
                handler(message)
            } catch (e: Exception) {
                logger.error("Ошибка при отправке уведомления подписчику: ${e.message}", e)
                // Удаляем отвалившегося подписчика, чтобы не спамить ошибками
                subscribers -= handler
            }
        }
    }
}
