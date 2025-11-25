package com.aichallengekmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichallengekmp.chat.ChatRepository
import com.aichallengekmp.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * ViewModel для управления состоянием чата
 * Следует принципу Single Source of Truth - все состояние в одном месте
 */
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    
    private val logger = LoggerFactory.getLogger(ChatViewModel::class.java)
    
    // Единый источник правды
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        logger.info("🎬 Инициализация ChatViewModel")
        loadInitialData()
        startReminderListener()
    }
    
    // ============= Public Actions =============

    fun toggleRagCompare(enabled: Boolean) {
        _uiState.update { it.copy(ragCompareEnabled = enabled) }
    }

    fun clearRagResult() {
        _uiState.update { it.copy(lastRagResult = null) }
    }
    
    /**
     * Загрузить начальные данные (сессии + модели)
     */
    fun loadInitialData() {
        viewModelScope.launch {
            logger.info("📥 Загрузка начальных данных")
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Загружаем сессии
                val sessionsResult = repository.getSessions()
                val sessions = sessionsResult.getOrElse { 
                    logger.error("❌ Ошибка загрузки сессий: ${it.message}")
                    emptyList()
                }
                
                // Загружаем доступные модели
                val modelsResult = repository.getAvailableModels()
                val models = modelsResult.getOrElse { 
                    logger.error("❌ Ошибка загрузки моделей: ${it.message}")
                    emptyList()
                }
                
                _uiState.update { 
                    it.copy(
                        sessions = sessions,
                        availableModels = models,
                        isLoading = false,
                        sessionCounter = sessions.size + 1
                    )
                }
                
                logger.info("✅ Данные загружены: ${sessions.size} сессий, ${models.size} моделей")
            } catch (e: Exception) {
                logger.error("❌ Критическая ошибка при загрузке данных: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = ErrorState(
                            message = "Ошибка подключения к серверу",
                            details = e.message
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Выбрать сессию
     */
    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            logger.info("📂 Выбор сессии: $sessionId")
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            repository.getSessionDetail(sessionId)
                .onSuccess { session ->
                    logger.info("✅ Сессия загружена: ${session.messages.size} сообщений")
                    _uiState.update { 
                        it.copy(
                            selectedSession = session,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    logger.error("❌ Ошибка загрузки сессии: ${error.message}", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = "Не удалось загрузить сессию",
                                details = error.message
                            )
                        )
                    }
                }
        }
    }
    
    /**
     * Обновить текст сообщения
     */
    fun updatePendingMessage(text: String) {
        _uiState.update { it.copy(pendingMessage = text) }
    }
    
    /**
     * Отправить сообщение
     */
    fun sendMessage() {
        val currentState = _uiState.value
        val message = currentState.pendingMessage.trim()
        
        if (message.isEmpty()) {
            logger.warn("⚠️ Попытка отправить пустое сообщение")
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null, lastRagResult = null) }
            
            try {
                if (currentState.ragCompareEnabled) {
                    // 🔎 Режим сравнения RAG / без RAG
                    sendWithRagComparison(message)
                } else {
                    // Если сессия не выбрана - создаем новую
                    if (currentState.selectedSession == null) {
                        createNewSession(message)
                    } else {
                        sendToExistingSession(currentState.selectedSession.id, message)
                    }
                }
            } catch (e: Exception) {
                logger.error("❌ Ошибка при отправке сообщения: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        error = ErrorState(
                            message = "Ошибка при отправке сообщения",
                            details = e.message
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Прервать генерацию ответа
     * TODO: Реализовать отмену запроса
     */
    fun cancelGeneration() {
        logger.info("⏹️ Отмена генерации (пока не реализовано)")
        _uiState.update { it.copy(isSending = false) }
    }
    
    /**
     * Обновить настройки по умолчанию
     */
    fun updateDefaultSettings(settings: SessionSettingsDto) {
        logger.debug("⚙️ Обновление настроек по умолчанию")
        _uiState.update { it.copy(defaultSettings = settings) }
    }
    
    /**
     * Обновить настройки текущей сессии
     */
    fun updateSessionSettings(sessionId: String, settings: SessionSettingsDto) {
        viewModelScope.launch {
            logger.info("⚙️ Обновление настроек сессии: $sessionId")
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            repository.updateSettings(sessionId, settings)
                .onSuccess { updatedSettings ->
                    logger.info("✅ Настройки обновлены")
                    // Перезагружаем сессию чтобы получить обновленные данные
                    selectSession(sessionId)
                }
                .onFailure { error ->
                    logger.error("❌ Ошибка обновления настроек: ${error.message}", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = "Не удалось обновить настройки",
                                details = error.message
                            )
                        )
                    }
                }
        }
    }
    
    /**
     * Удалить сессию
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            logger.warn("🗑️ Удаление сессии: $sessionId")
            _uiState.update { it.copy(isLoading = true, error = null, showDeleteConfirmation = null) }
            
            repository.deleteSession(sessionId)
                .onSuccess {
                    logger.info("✅ Сессия удалена")
                    
                    // Обновляем список сессий
                    val updatedSessions = _uiState.value.sessions.filter { it.id != sessionId }
                    
                    // Если удалили выбранную сессию - снимаем выбор
                    val updatedSelectedSession = if (_uiState.value.selectedSession?.id == sessionId) {
                        null
                    } else {
                        _uiState.value.selectedSession
                    }
                    
                    _uiState.update { 
                        it.copy(
                            sessions = updatedSessions,
                            selectedSession = updatedSelectedSession,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    logger.error("❌ Ошибка удаления сессии: ${error.message}", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = "Не удалось удалить сессию",
                                details = error.message
                            )
                        )
                    }
                }
        }
    }
    
    /**
     * Показать/скрыть диалог настроек сессии
     */
    fun toggleSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }
    
    /**
     * Показать/скрыть панель настроек по умолчанию
     */
    fun toggleDefaultSettingsPanel(show: Boolean) {
        _uiState.update { it.copy(showDefaultSettingsPanel = show) }
    }
    
    /**
     * Показать диалог подтверждения удаления
     */
    fun showDeleteConfirmation(sessionId: String) {
        _uiState.update { it.copy(showDeleteConfirmation = sessionId) }
    }
    
    /**
     * Скрыть диалог подтверждения удаления
     */
    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }
    
    /**
     * Очистить ошибку
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    // ============= Private Helper Methods =============
    
    /**
     * Запускает фоновое прослушивание SSE-стрима напоминаний
     * и добавляет их как системные сообщения в текущую сессию.
     */
    private fun startReminderListener() {
        viewModelScope.launch {
            logger.info("⏰ Запуск прослушивания напоминаний через SSE")
            while (isActive) {
                try {
                    repository.listenReminders { summary ->
                        logger.info("⏰ Получено напоминание через SSE:\n$summary")
                        _uiState.update { state ->
                            val currentSession = state.selectedSession
                            if (currentSession == null) {
                                // Если сессия не выбрана, просто игнорируем (можно развить до отдельного списка уведомлений)
                                return@update state
                            }

                            val reminderMessage = MessageDto(
                                id = "reminder-${System.currentTimeMillis()}",
                                role = "assistant",
                                content = "Сводка напоминаний:\n$summary",
                                modelId = currentSession.settings.modelId,
                                modelName = "Напоминания",
                                tokenUsage = null,
                                timestamp = System.currentTimeMillis()
                            )

                            val updatedSession = currentSession.copy(
                                messages = currentSession.messages + reminderMessage
                            )

                        state.copy(selectedSession = updatedSession)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("❌ Ошибка при прослушивании напоминаний: ${e.message}", e)
                    // Подождём немного и попробуем переподключиться к SSE
                    delay(5_000)
                }
            }
        }
    }

    /**
     * Создать новую сессию с первым сообщением
     */
    private suspend fun createNewSession(message: String) {
        val currentState = _uiState.value
        val sessionName = "Чат ${currentState.sessionCounter}"
        
        logger.info("🆕 Создание новой сессии: $sessionName")
        
        val request = CreateSessionRequest(
            name = sessionName,
            initialMessage = message,
            settings = currentState.defaultSettings
        )
        
        repository.createSession(request)
            .onSuccess { session ->
                logger.info("✅ Сессия создана: ${session.id}")
                
                // Обновляем список сессий
                val updatedSessions = repository.getSessions().getOrElse { emptyList() }
                
                _uiState.update { 
                    it.copy(
                        sessions = updatedSessions,
                        selectedSession = session,
                        pendingMessage = "",
                        isSending = false,
                        sessionCounter = it.sessionCounter + 1
                    )
                }
            }
            .onFailure { error ->
                logger.error("❌ Ошибка создания сессии: ${error.message}", error)
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        error = ErrorState(
                            message = "Не удалось создать сессию",
                            details = error.message
                        )
                    )
                }
            }
    }
    
    private suspend fun sendWithRagComparison(message: String) {
        val state = _uiState.value
        val settings = state.selectedSession?.settings ?: state.defaultSettings

        val request = RagAskRequest(
            question = message,
            topK = 5,
            modelId = settings.modelId,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            systemPrompt = settings.systemPrompt
        )

        repository.askRag(request)
            .onSuccess { response ->
                logger.info("✅ Получен результат RAG-сравнения")
                _uiState.update {
                    it.copy(
                        pendingMessage = "",
                        isSending = false,
                        lastRagResult = response
                    )
                }
            }
            .onFailure { error ->
                logger.error("❌ Ошибка RAG-запроса: ${error.message}", error)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = ErrorState(
                            message = "Не удалось выполнить RAG-запрос",
                            details = error.message
                        )
                    )
                }
            }
    }
    
    /**
     * Отправить сообщение в существующую сессию
     */
    private suspend fun sendToExistingSession(sessionId: String, message: String) {
        logger.info("💬 Отправка сообщения в сессию: $sessionId")
        
        repository.sendMessage(sessionId, message)
            .onSuccess { updatedSession ->
                logger.info("✅ Сообщение отправлено, получен ответ")
                
                // Обновляем список сессий (для lastMessage)
                val updatedSessions = repository.getSessions().getOrElse { _uiState.value.sessions }
                
                _uiState.update { 
                    it.copy(
                        sessions = updatedSessions,
                        selectedSession = updatedSession,
                        pendingMessage = "",
                        isSending = false
                    )
                }
            }
            .onFailure { error ->
                logger.error("❌ Ошибка отправки сообщения: ${error.message}", error)
                _uiState.update { 
                    it.copy(
                        isSending = false,
                        error = ErrorState(
                            message = "Не удалось отправить сообщение",
                            details = error.message
                        )
                    )
                }
            }
    }
}
