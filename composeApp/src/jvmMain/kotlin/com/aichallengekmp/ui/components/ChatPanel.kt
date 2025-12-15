package com.aichallengekmp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichallengekmp.models.*
import com.aichallengekmp.ui.ErrorState
import kotlinx.coroutines.launch

/**
 * Правая панель с чатом
 */
@Composable
fun ChatPanel(
    session: SessionDetailResponse?,
    pendingMessage: String,
    defaultSettings: SessionSettingsDto,
    availableModels: List<ModelInfoDto>,
    availableProfiles: List<com.aichallengekmp.model.UserProfile>,
    currentProfile: com.aichallengekmp.model.UserProfile?,
    showDefaultSettings: Boolean,
    isSending: Boolean,
    error: ErrorState?,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleDefaultSettings: (Boolean) -> Unit,
    onDefaultSettingsChange: (SessionSettingsDto) -> Unit,
    onClearError: () -> Unit,
    onSettingsClick: () -> Unit,
    ragCompareEnabled: Boolean,
    ragResult: RagAskResponse?,
    ragSimilarityThreshold: Double,
    onToggleRagCompare: (Boolean) -> Unit,
    onChangeRagThreshold: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (session != null) {
            // Есть выбранная сессия - показываем чат
            ChatContent(
                session = session,
                pendingMessage = pendingMessage,
                isSending = isSending,
                error = error,
                onMessageChange = onMessageChange,
                onSendClick = onSendClick,
                onCancelClick = onCancelClick,
                onClearError = onClearError,
                onSettingsClick = onSettingsClick,
                ragCompareEnabled = ragCompareEnabled,
                ragResult = ragResult,
                ragSimilarityThreshold = ragSimilarityThreshold,
                onToggleRagCompare = onToggleRagCompare,
                onChangeRagThreshold = onChangeRagThreshold
            )
        } else {
            // Нет выбранной сессии - показываем placeholder
            EmptyState(
                pendingMessage = pendingMessage,
                defaultSettings = defaultSettings,
                availableModels = availableModels,
                availableProfiles = availableProfiles,
                currentProfile = currentProfile,
                showSettings = showDefaultSettings,
                isSending = isSending,
                error = error,
                onMessageChange = onMessageChange,
                onSendClick = onSendClick,
                onCancelClick = onCancelClick,
                onToggleSettings = onToggleDefaultSettings,
                onSettingsChange = onDefaultSettingsChange,
                onClearError = onClearError,
                ragCompareEnabled = ragCompareEnabled,
                ragResult = ragResult,
                ragSimilarityThreshold = ragSimilarityThreshold,
                onToggleRagCompare = onToggleRagCompare,
                onChangeRagThreshold = onChangeRagThreshold
            )
        }
    }
}

/**
 * Контент чата (когда сессия выбрана)
 */
@Composable
private fun ChatContent(
    session: SessionDetailResponse,
    pendingMessage: String,
    isSending: Boolean,
    error: ErrorState?,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onClearError: () -> Unit,
    onSettingsClick: () -> Unit,
    ragCompareEnabled: Boolean,
    ragResult: RagAskResponse?,
    ragSimilarityThreshold: Double,
    onToggleRagCompare: (Boolean) -> Unit,
    onChangeRagThreshold: (Double) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Заголовок с названием сессии и кнопкой настроек
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Информация о сжатии если есть
                session.compressionInfo?.let { compression ->
                    if (compression.hasSummary) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📦 ${compression.compressedMessagesCount} сообщений сжато, сэкономлено ${compression.tokensSaved} токенов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки сессии"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Список сообщений
        MessageList(
            messages = session.messages,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Ошибка если есть
        error?.let {
            ErrorCard(
                error = it,
                onDismiss = onClearError,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Поле ввода
        MessageInput(
            message = pendingMessage,
            isSending = isSending,
            ragCompareEnabled = ragCompareEnabled,
            ragResult = ragResult,
            similarityThreshold = ragSimilarityThreshold,
            onMessageChange = onMessageChange,
            onSendClick = onSendClick,
            onCancelClick = onCancelClick,
            onToggleRagCompare = onToggleRagCompare,
            onChangeThreshold = onChangeRagThreshold
        )
    }
}

/**
 * Пустое состояние (когда сессия не выбрана)
 */
@Composable
private fun EmptyState(
    pendingMessage: String,
    defaultSettings: SessionSettingsDto,
    availableModels: List<ModelInfoDto>,
    availableProfiles: List<com.aichallengekmp.model.UserProfile>,
    currentProfile: com.aichallengekmp.model.UserProfile?,
    showSettings: Boolean,
    isSending: Boolean,
    error: ErrorState?,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleSettings: (Boolean) -> Unit,
    onSettingsChange: (SessionSettingsDto) -> Unit,
    onClearError: () -> Unit,
    ragCompareEnabled: Boolean,
    ragResult: RagAskResponse?,
    ragSimilarityThreshold: Double,
    onToggleRagCompare: (Boolean) -> Unit,
    onChangeRagThreshold: (Double) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        // Приветствие
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Начните новый диалог",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Введите сообщение ниже, чтобы создать новый чат",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Ошибка если есть
        error?.let {
            ErrorCard(
                error = it,
                onDismiss = onClearError,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Раскрывающаяся панель настроек
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Заголовок с кнопкой раскрытия
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Настройки по умолчанию",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = { onToggleSettings(!showSettings) }) {
                        Icon(
                            imageVector = if (showSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showSettings) "Скрыть" else "Показать"
                        )
                    }
                }
                
                // Настройки
                AnimatedVisibility(visible = showSettings) {
                    DefaultSettingsPanel(
                        settings = defaultSettings,
                        availableModels = availableModels,
                        availableProfiles = availableProfiles,
                        currentProfile = currentProfile,
                        onSettingsChange = onSettingsChange
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Поле ввода
        MessageInput(
            message = pendingMessage,
            isSending = isSending,
            ragCompareEnabled = ragCompareEnabled,
            ragResult = ragResult,
            similarityThreshold = ragSimilarityThreshold,
            onMessageChange = onMessageChange,
            onSendClick = onSendClick,
            onCancelClick = onCancelClick,
            onToggleRagCompare = onToggleRagCompare,
            onChangeThreshold = onChangeRagThreshold
        )
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
    
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageCard(message = message)
        }
    }
}

/**
 * Поле ввода сообщения + галочка RAG и результат сравнения
 */
@Composable
private fun MessageInput(
    message: String,
    isSending: Boolean,
    ragCompareEnabled: Boolean,
    ragResult: RagAskResponse?,
    similarityThreshold: Double,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleRagCompare: (Boolean) -> Unit,
    onChangeThreshold: (Double) -> Unit
) {
    Column(
        Modifier.verticalScroll(rememberScrollState())
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 200.dp),
            placeholder = { Text("Введите сообщение...") },
            enabled = !isSending,
            maxLines = 6
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Строка: слева галочка RAG, справа кнопка отправки / индикатор
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = ragCompareEnabled,
                    onCheckedChange = { onToggleRagCompare(it) },
                    enabled = !isSending
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Сравнить с документацией (RAG)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (ragCompareEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Порог: ${String.format("%.2f", similarityThreshold)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Модель думает...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onCancelClick) {
                        Text("Прервать")
                    }
                } else {
                    Button(
                        onClick = onSendClick,
                        enabled = message.trim().isNotEmpty()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отправить")
                        }
                    }
                }
            }
        }

        // Если есть результат RAG-сравнения — показываем его под полем ввода
        ragResult?.let { result ->

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Результат сравнения для вопроса (порог фильтра: " +
                        (result.similarityThreshold?.let { String.format("%.2f", it) } ?: "—") + "):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = result.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WITHOUT RAG
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Без RAG (baseline)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.withoutRag.answer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚡ ${result.withoutRag.tokenUsage.totalTokens} tokens (in: ${result.withoutRag.tokenUsage.inputTokens}, out: ${result.withoutRag.tokenUsage.outputTokens})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // WITH RAG (без фильтра)
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "RAG без фильтра",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.withRag.answer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚡ ${result.withRag.tokenUsage.totalTokens} tokens (in: ${result.withRag.tokenUsage.inputTokens}, out: ${result.withRag.tokenUsage.outputTokens})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // WITH RAG + FILTER
                result.withRagFiltered?.let { filtered ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "RAG с фильтром",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = filtered.answer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚡ ${filtered.tokenUsage.totalTokens} tokens (in: ${filtered.tokenUsage.inputTokens}, out: ${filtered.tokenUsage.outputTokens})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
