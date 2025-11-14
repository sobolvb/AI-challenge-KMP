package local.service

import local.data.DialogMessage
import local.data.DialogSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер диалоговых сессий
 * Хранит истории диалогов и управляет их сжатием
 */
class DialogSessionManager {
    private val sessions = ConcurrentHashMap<String, DialogSession>()
    
    companion object {
        const val COMPRESSION_THRESHOLD = 10 // Сжимать каждые 10 сообщений
        const val MESSAGES_TO_KEEP_AFTER_COMPRESSION = 3 // Оставлять последние 3 сообщения после сжатия
    }
    
    /**
     * Получить или создать сессию
     */
    fun getOrCreateSession(sessionId: String): DialogSession {
        return sessions.getOrPut(sessionId) {
            println("🆕 Создана новая сессия: $sessionId")
            DialogSession(sessionId = sessionId)
        }
    }
    
    /**
     * Добавить сообщение в сессию
     */
    fun addMessage(sessionId: String, message: DialogMessage) {
        val session = getOrCreateSession(sessionId)
        session.messages.add(message)
        session.updatedAt = System.currentTimeMillis()
        
        println("💬 Добавлено сообщение в сессию $sessionId. Всего сообщений: ${session.messages.size}")
    }
    
    /**
     * Проверить, нужно ли сжатие
     */
    fun needsCompression(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        val messagesSinceLastCompression = session.messages.size - session.lastCompressionAt
        
        return messagesSinceLastCompression >= COMPRESSION_THRESHOLD
    }
    
    /**
     * Выполнить сжатие истории диалога
     */
    fun compressHistory(sessionId: String, summary: String) {
        val session = sessions[sessionId] ?: return
        
        println("🗜️ Начинаем сжатие истории для сессии $sessionId")
        println("📊 Сообщений до сжатия: ${session.messages.size}")
        
        // Определяем, какие сообщения сжимать
        val messagesToCompress = session.messages.size - MESSAGES_TO_KEEP_AFTER_COMPRESSION
        
        if (messagesToCompress > 0) {
            // Сохраняем последние сообщения
            val recentMessages = session.messages.takeLast(MESSAGES_TO_KEEP_AFTER_COMPRESSION)
            
            // Обновляем сессию
            session.summary = summary
            session.messages.clear()
            session.messages.addAll(recentMessages)
            session.lastCompressionAt = MESSAGES_TO_KEEP_AFTER_COMPRESSION
            session.updatedAt = System.currentTimeMillis()
            
            println("✅ Сжатие выполнено. Сообщений после сжатия: ${session.messages.size}")
            println("📝 Summary сохранен: ${summary.take(100)}...")
        }
    }
    
    /**
     * Получить контекст для AI (summary + последние сообщения)
     */
    fun getContextForAI(sessionId: String): List<DialogMessage> {
        val session = sessions[sessionId] ?: return emptyList()
        
        val context = mutableListOf<DialogMessage>()
        
        // Если есть summary, добавляем его как системное сообщение
        session.summary?.let { summary ->
            context.add(
                DialogMessage(
                    role = "system",
                    content = "Контекст предыдущего разговора: $summary"
                )
            )
        }
        
        // Добавляем текущие сообщения
        context.addAll(session.messages)
        
        return context
    }
    
    /**
     * Получить все сообщения для сжатия
     */
    fun getMessagesForCompression(sessionId: String): List<DialogMessage> {
        val session = sessions[sessionId] ?: return emptyList()
        val messagesToCompress = session.messages.size - MESSAGES_TO_KEEP_AFTER_COMPRESSION
        
        return if (messagesToCompress > 0) {
            session.messages.take(messagesToCompress)
        } else {
            emptyList()
        }
    }
    
    /**
     * Получить информацию о сессии
     */
    fun getSessionInfo(sessionId: String): DialogSession? {
        return sessions[sessionId]
    }
    
    /**
     * Получить все активные сессии
     */
    fun getAllSessions(): List<String> {
        return sessions.keys.toList()
    }
    
    /**
     * Удалить сессию
     */
    fun deleteSession(sessionId: String) {
        sessions.remove(sessionId)
        println("🗑️ Сессия удалена: $sessionId")
    }
    
    /**
     * Очистить старые сессии (старше 24 часов)
     */
    fun cleanupOldSessions() {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 60 * 60 * 1000
        
        sessions.entries.removeIf { (sessionId, session) ->
            val isOld = session.updatedAt < oneDayAgo
            if (isOld) {
                println("🧹 Удалена старая сессия: $sessionId")
            }
            isOld
        }
    }
}
