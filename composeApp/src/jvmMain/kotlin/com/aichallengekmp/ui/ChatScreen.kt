package com.aichallengekmp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichallengekmp.ui.components.*
import org.koin.compose.viewmodel.koinViewModel

/**
 * Главный экран чата
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Левая панель - список сессий
            SessionListPanel(
                sessions = uiState.sessions,
                selectedSessionId = uiState.selectedSession?.id,
                isLoading = uiState.isLoading,
                onSessionClick = viewModel::selectSession,
                onDeleteClick = viewModel::showDeleteConfirmation,
                onSettingsClick = { viewModel.toggleSettingsDialog(true) },
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )
            
            // Разделитель
            VerticalDivider()
            
            // Правая панель - чат
            ChatPanel(
                session = uiState.selectedSession,
                pendingMessage = uiState.pendingMessage,
                defaultSettings = uiState.defaultSettings,
                availableModels = uiState.availableModels,
                showDefaultSettings = uiState.showDefaultSettingsPanel,
                isSending = uiState.isSending,
                error = uiState.error,
                onMessageChange = viewModel::updatePendingMessage,
                onSendClick = viewModel::sendMessage,
                onCancelClick = viewModel::cancelGeneration,
                onToggleDefaultSettings = viewModel::toggleDefaultSettingsPanel,
                onDefaultSettingsChange = viewModel::updateDefaultSettings,
                onClearError = {
                    viewModel.clearError()
                    viewModel.clearRagResult()
                },
                onSettingsClick = { viewModel.toggleSettingsDialog(true) },
                ragCompareEnabled = uiState.ragCompareEnabled,
                ragResult = uiState.lastRagResult,
                ragSimilarityThreshold = uiState.ragSimilarityThreshold,
                onToggleRagCompare = viewModel::toggleRagCompare,
                onChangeRagThreshold = viewModel::updateRagThreshold,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Диалоги
        if (uiState.showSettingsDialog && uiState.selectedSession != null) {
            SessionSettingsDialog(
                session = uiState.selectedSession!!,
                availableModels = uiState.availableModels,
                onDismiss = { viewModel.toggleSettingsDialog(false) },
                onSave = { settings ->
                    viewModel.updateSessionSettings(uiState.selectedSession!!.id, settings)
                    viewModel.toggleSettingsDialog(false)
                }
            )
        }
        
        if (uiState.showDeleteConfirmation != null) {
            DeleteConfirmationDialog(
                sessionName = uiState.sessions.find { it.id == uiState.showDeleteConfirmation }?.name ?: "",
                onConfirm = {
                    viewModel.deleteSession(uiState.showDeleteConfirmation!!)
                },
                onDismiss = viewModel::hideDeleteConfirmation
            )
        }
    }
}
