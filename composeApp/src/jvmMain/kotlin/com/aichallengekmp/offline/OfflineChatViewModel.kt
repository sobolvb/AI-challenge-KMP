package com.aichallengekmp.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel для офлайн чата (без сервера)
 */
class OfflineChatViewModel : ViewModel() {
    private val chatClient = OfflineChatClient()

    data class Message(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    var messages by mutableStateOf<List<Message>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var pendingMessage by mutableStateOf("")

    /**
     * Проверить доступность Ollama при запуске
     */
    suspend fun checkOllamaAvailable(): Boolean {
        return chatClient.checkAvailable()
    }

    /**
     * Отправить сообщение
     */
    fun sendMessage() {
        val text = pendingMessage.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            error = null

            // Добавляем сообщение пользователя
            messages = messages + Message(role = "user", content = text)
            pendingMessage = ""

            try {
                val history = messages.map { it.role to it.content }
                val response = chatClient.sendMessage(text, history)

                // Добавляем ответ AI
                messages = messages + Message(role = "assistant", content = response)
            } catch (e: Exception) {
                error = "Ошибка: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearHistory() {
        messages = emptyList()
        error = null
    }

    fun clearError() {
        error = null
    }
}
