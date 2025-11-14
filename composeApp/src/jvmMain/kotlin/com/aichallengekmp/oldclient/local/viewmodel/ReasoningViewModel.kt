package local.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import local.data.*
import local.repository.ReasoningRepository
import java.util.UUID

sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class TaskSuccess(val data: TaskResponse) : UiState()
    data class ComparisonSuccess(val data: TokenComparisonResponse) : UiState()
    data class DialogActive(val messages: List<DialogMessage>) : UiState()
    data class Error(val message: String) : UiState()
}

class ReasoningViewModel(
    private val repository: ReasoningRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    private val _comparisonData = MutableStateFlow<TokenComparisonResponse?>(null)
    val comparisonData: StateFlow<TokenComparisonResponse?> = _comparisonData.asStateFlow()

    // Диалоговый режим
    private val _sessionId = MutableStateFlow(generateSessionId())
    val sessionId: StateFlow<String> = _sessionId.asStateFlow()

    private val _dialogMessages = MutableStateFlow<List<DialogMessage>>(emptyList())
    val dialogMessages: StateFlow<List<DialogMessage>> = _dialogMessages.asStateFlow()

    private val _sessionInfo = MutableStateFlow<SessionInfo?>(null)
    val sessionInfo: StateFlow<SessionInfo?> = _sessionInfo.asStateFlow()

    private val _compressionStats = MutableStateFlow<CompressionStats?>(null)
    val compressionStats: StateFlow<CompressionStats?> = _compressionStats.asStateFlow()

    private val _compressionAnalysis = MutableStateFlow<String?>(null)
    val compressionAnalysis: StateFlow<String?> = _compressionAnalysis.asStateFlow()

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }
    
    fun solveTask() {
        val task = _inputText.value.trim()
        if (task.isEmpty()) {
            _uiState.value = UiState.Error("Введите задачу")
            return
        }
        
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.solveTask(task).fold(
                    onSuccess = { response ->
                        _uiState.value = UiState.TaskSuccess(response)
                    },
                    onFailure = { error ->
                        _uiState.value = UiState.Error("Ошибка: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка сети: ${e.message}")
            }
        }
    }
    
    fun compareTokens() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.compareTokens().fold(
                    onSuccess = { response ->
                        _comparisonData.value = response
                        _uiState.value = UiState.ComparisonSuccess(response)
                    },
                    onFailure = { error ->
                        _uiState.value = UiState.Error("Ошибка: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка сети: ${e.message}")
            }
        }
    }

    fun sendDialogMessage() {
        val message = _inputText.value.trim()
        if (message.isEmpty()) {
            _uiState.value = UiState.Error("Введите сообщение")
            return
        }

        // Добавляем сообщение пользователя в UI
        val userMessage = DialogMessage(role = "user", content = message)
        _dialogMessages.value = _dialogMessages.value + userMessage
        _inputText.value = ""
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                repository.sendDialogMessage(_sessionId.value, message).fold(
                    onSuccess = { response ->
                        // Добавляем ответ ассистента
                        val assistantMessage = DialogMessage(role = "assistant", content = response.answer)
                        _dialogMessages.value = _dialogMessages.value + assistantMessage

                        _uiState.value = UiState.DialogActive(_dialogMessages.value)

                        // Обновляем информацию о сессии
                        updateSessionInfo()

                        // Если было сжатие, обновляем статистику
                        if (response.compressionApplied) {
                            updateCompressionStats()
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = UiState.Error("Ошибка: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка сети: ${e.message}")
            }
        }
    }

    fun startNewDialog() {
        _sessionId.value = generateSessionId()
        _dialogMessages.value = emptyList()
        _sessionInfo.value = null
        _compressionStats.value = null
        _compressionAnalysis.value = null
        _uiState.value = UiState.DialogActive(emptyList())
        println("🆕 Новый диалог: ${_sessionId.value}")
    }

    fun updateSessionInfo() {
        viewModelScope.launch {
            repository.getSessionInfo(_sessionId.value).fold(
                onSuccess = { info ->
                    _sessionInfo.value = info
                },
                onFailure = { /* игнорируем */ }
            )
        }
    }

    fun updateCompressionStats() {
        viewModelScope.launch {
            repository.getCompressionStats(_sessionId.value).fold(
                onSuccess = { stats ->
                    _compressionStats.value = stats
                },
                onFailure = { /* игнорируем */ }
            )
        }
    }

    fun loadCompressionAnalysis() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getCompressionAnalysis(_sessionId.value).fold(
                onSuccess = { response ->
                    _compressionAnalysis.value = response.analysis
                    _uiState.value = UiState.DialogActive(_dialogMessages.value)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error("Ошибка анализа: ${error.message}")
                }
            )
        }
    }

    private fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }

    fun clearAll() {
        _uiState.value = UiState.Initial
        _inputText.value = ""
        _comparisonData.value = null
        _dialogMessages.value = emptyList()
        _sessionInfo.value = null
        _compressionStats.value = null
        _compressionAnalysis.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed
    }
}
