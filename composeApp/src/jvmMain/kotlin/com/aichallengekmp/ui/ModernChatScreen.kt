package com.aichallengekmp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichallengekmp.models.MessageDto
import com.aichallengekmp.ui.components.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Современный чат в стиле "Умный минимализм"
 */
@Composable
fun ModernChatScreen(
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            ModernHeader(
                currentProfile = uiState.currentProfile,
                currentModel = uiState.selectedSession?.settings?.let { settings ->
                    uiState.availableModels.firstOrNull { it.id == settings.modelId }
                },
                onSettingsClick = { viewModel.toggleSettingsDrawer(true) }
            )

            Divider()

            // Main content
            Row(modifier = Modifier.weight(1f)) {
                // Кнопка открытия сессий (слева)
                Surface(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            IconButton(
                                onClick = { viewModel.toggleSessionsDrawer(true) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Sessions"
                                )
                            }

                            // Badge в правом верхнем углу
                            if (uiState.sessions.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                ) {
                                    Text(
                                        text = "${uiState.sessions.size}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                // Chat area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    if (uiState.selectedSession != null) {
                        // Session title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.selectedSession!!.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                // Compression info
                                uiState.selectedSession!!.compressionInfo?.let { compression ->
                                    if (compression.hasSummary) {
                                        Text(
                                            text = "📦 ${compression.compressedMessagesCount} messages compressed, ${compression.tokensSaved} tokens saved",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { viewModel.startNewChat() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New")
                                }

                                TextButton(
                                    onClick = { viewModel.toggleSessionsDrawer(true) }
                                ) {
                                    Text("Sessions ▼")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Messages
                        MessageList(
                            messages = uiState.selectedSession!!.messages,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        // Empty state - центрируем относительно всей области
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "🤖",
                                    style = MaterialTheme.typography.displayLarge
                                )
                                Text(
                                    text = "Start a new conversation",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Type your message below",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Error display
                    uiState.error?.let { error ->
                        ErrorCard(
                            error = error,
                            onDismiss = {
                                viewModel.clearError()
                                viewModel.clearRagResult()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Feature Panel
                    FeaturePanel(
                        isRecordingVoice = uiState.isRecordingVoice,
                        ragEnabled = uiState.ragCompareEnabled,
                        ragDocCount = 5, // TODO: получать из состояния
                        ragSimilarityThreshold = uiState.ragSimilarityThreshold,
                        remindersCount = 0, // TODO: получать из состояния
                        analyticsEventCount = 0, // TODO: получать из состояния
                        gitAvailable = true,
                        mcpAvailable = true,
                        onVoiceClick = { /* TODO: открыть настройки голоса */ },
                        onRagClick = { viewModel.toggleRagCompare(!uiState.ragCompareEnabled) },
                        onRemindersClick = { /* TODO: открыть напоминания */ },
                        onAnalyticsClick = { /* TODO: открыть аналитику */ }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Message Input
                    ModernMessageInput(
                        message = uiState.pendingMessage,
                        isSending = uiState.isSending,
                        isRecordingVoice = uiState.isRecordingVoice,
                        onMessageChange = viewModel::updatePendingMessage,
                        onSendClick = viewModel::sendMessage,
                        onCancelClick = viewModel::cancelGeneration,
                        onStartRecording = viewModel::startVoiceRecording,
                        onStopRecording = viewModel::stopVoiceRecording
                    )
                }
            }
        }

        // Sessions Drawer
        SessionsDrawer(
            isOpen = uiState.showSessionsDrawer,
            sessions = uiState.sessions,
            selectedSessionId = uiState.selectedSession?.id,
            onDismiss = { viewModel.toggleSessionsDrawer(false) },
            onSessionClick = viewModel::selectSession,
            onDeleteClick = viewModel::showDeleteConfirmation,
            onNewChatClick = {
                viewModel.startNewChat()
                viewModel.toggleSessionsDrawer(false)
            }
        )

        // Settings Drawer
        SettingsDrawer(
            isOpen = uiState.showSettingsDrawer,
            onDismiss = { viewModel.toggleSettingsDrawer(false) },
            onProfileSettingsClick = { /* TODO */ },
            onModelConfigClick = { viewModel.toggleSettingsDialog(true) },
            onVoiceSettingsClick = { /* TODO */ },
            onRagConfigClick = { /* TODO */ },
            onToolsManagerClick = { /* TODO */ },
            onAnalyticsClick = { /* TODO */ },
            onExportImportClick = { /* TODO */ }
        )

        // Legacy dialogs (сохраняем для совместимости)
        if (uiState.showSettingsDialog && uiState.selectedSession != null) {
            SessionSettingsDialog(
                session = uiState.selectedSession!!,
                availableModels = uiState.availableModels,
                availableProfiles = uiState.availableProfiles,
                currentProfile = uiState.currentProfile,
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

/**
 * Список сообщений
 */
@Composable
private fun MessageList(
    messages: List<MessageDto>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Автоскролл к последнему сообщению
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageCard(message = message)
            }
        }
    }
}
