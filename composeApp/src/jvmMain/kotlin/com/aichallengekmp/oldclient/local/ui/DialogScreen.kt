package local.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import local.data.*

@Composable
fun DialogSection(
    messages: List<DialogMessage>,
    sessionInfo: SessionInfo?,
    compressionStats: CompressionStats?,
    compressionAnalysis: String?,
    onLoadAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Информация о сессии
        item {
            SessionInfoCard(sessionInfo = sessionInfo, compressionStats = compressionStats)
        }
        
        // Сообщения диалога
        items(messages) { message ->
            DialogMessageCard(message = message)
        }
        
        // Статистика сжатия
        compressionStats?.let { stats ->
            item {
                CompressionStatsCard(
                    stats = stats,
                    analysis = compressionAnalysis,
                    onLoadAnalysis = onLoadAnalysis
                )
            }
        }
        
        // Добавляем отступ снизу
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SessionInfoCard(
    sessionInfo: SessionInfo?,
    compressionStats: CompressionStats?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Информация о сессии",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            sessionInfo?.let { info ->
                Text(text = "Сообщений: ${info.messageCount}")
                Text(text = "Есть summary: ${if (info.hasSummary) "✅ Да" else "❌ Нет"}")
                
                compressionStats?.let { stats ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💾 Токенов сэкономлено: ${stats.tokensSaved}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "📉 Степень сжатия: ${String.format("%.1f", stats.compressionRatio * 100)}%",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } ?: run {
                Text(text = "Новый диалог", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun DialogMessageCard(
    message: DialogMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
//        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "👤 Вы" else "🤖 Ассистент",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun CompressionStatsCard(
    stats: CompressionStats,
    analysis: String?,
    onLoadAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🗜️ Статистика сжатия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Статистика
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "До сжатия",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Сообщений: ${stats.messagesBeforeCompression}")
                    Text(text = "Токенов: ${stats.tokensBeforeCompression.totalTokens}")
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "После сжатия",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Сообщений: ${stats.messagesAfterCompression}")
                    Text(text = "Токенов: ${stats.tokensAfterCompression.totalTokens}")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Экономия
            Text(
                text = "💰 Экономия: ${stats.tokensSaved} токенов (${String.format("%.1f", stats.compressionRatio * 100)}%)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Summary
            if (stats.summary.isNotEmpty() && stats.summary != "Нет summary") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "📝 Summary:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stats.summary,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Кнопка анализа
            Spacer(modifier = Modifier.height(12.dp))
            if (analysis == null) {
                Button(
                    onClick = onLoadAnalysis,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 Загрузить анализ сжатия")
                }
            } else {
                Text(
                    text = "🔍 Анализ AI:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = analysis,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
