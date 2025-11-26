package com.aichallengekmp.ui

import com.aichallengekmp.models.*

/**
 * Единый UI State для всего чата
 */
data class ChatUiState(
    // Список всех сессий
    val sessions: List<SessionListItem> = emptyList(),
    
    // Выбранная сессия с полными данными
    val selectedSession: SessionDetailResponse? = null,
    
    // Черновик сообщения
    val pendingMessage: String = "",
    
    // Настройки по умолчанию (для новых сессий)
    val defaultSettings: SessionSettingsDto = SessionSettingsDto.default(),
    
    // Доступные модели
    val availableModels: List<ModelInfoDto> = emptyList(),
    
    // Состояния UI
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: ErrorState? = null,
    
    // Диалоги
    val showSettingsDialog: Boolean = false,
    val showDefaultSettingsPanel: Boolean = false,
    val showDeleteConfirmation: String? = null, // sessionId для подтверждения удаления
    
    // Счетчик для генерации названий новых чатов
    val sessionCounter: Int = 1,

    // RAG: включён ли режим сравнения и последний результат
    val ragCompareEnabled: Boolean = false,
    val lastRagResult: RagAskResponse? = null,

    // Настраиваемый порог похожести для фильтрации RAG (0.0–1.0)
    val ragSimilarityThreshold: Double = 0.1
)

/**
 * Состояние ошибки
 */
data class ErrorState(
    val message: String,
    val details: String? = null,
    val canCopy: Boolean = true
)
