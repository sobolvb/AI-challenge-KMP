package com.aichallengekmp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Панель активных возможностей (Feature Cards)
 */
@Composable
fun FeaturePanel(
    isRecordingVoice: Boolean,
    ragEnabled: Boolean,
    ragDocCount: Int,
    ragSimilarityThreshold: Double,
    remindersCount: Int,
    analyticsEventCount: Int,
    gitAvailable: Boolean,
    mcpAvailable: Boolean,
    onVoiceClick: () -> Unit,
    onRagClick: () -> Unit,
    onRemindersClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок с кнопкой раскрытия
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Active Features",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Компактная статус-линия когда свёрнута
                if (!isExpanded) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FeatureStatusDot(
                            icon = Icons.Default.Mic,
                            isActive = isRecordingVoice,
                            color = if (isRecordingVoice) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                        Text(text = "|", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FeatureStatusDot(
                            icon = Icons.Default.Search,
                            isActive = ragEnabled,
                            color = Color(0xFF2196F3)
                        )
                        if (ragEnabled) {
                            Text(text = "$ragDocCount docs", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(text = "|", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FeatureStatusDot(
                            icon = Icons.Default.Alarm,
                            isActive = remindersCount > 0,
                            color = Color(0xFFFF9800)
                        )
                        Text(text = "$remindersCount", style = MaterialTheme.typography.labelSmall)
                        Text(text = "|", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FeatureStatusDot(
                            icon = Icons.Default.Analytics,
                            isActive = analyticsEventCount > 0,
                            color = Color(0xFF9C27B0)
                        )
                        Text(text = "$analyticsEventCount", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    modifier = Modifier.rotate(rotation)
                )
            }

            // Раскрытая панель с карточками
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Voice Card
                        FeatureCard(
                            title = "Voice",
                            icon = Icons.Default.Mic,
                            status = if (isRecordingVoice) "RECORDING" else "READY",
                            statusColor = if (isRecordingVoice) Color(0xFFF44336) else Color(0xFF4CAF50),
                            details = "Speech recognition",
                            onClick = onVoiceClick,
                            modifier = Modifier.weight(1f)
                        )

                        // RAG Card
                        FeatureCard(
                            title = "RAG",
                            icon = Icons.Default.Search,
                            status = if (ragEnabled) "Active" else "Inactive",
                            statusColor = if (ragEnabled) Color(0xFF2196F3) else Color(0xFF9E9E9E),
                            details = if (ragEnabled) "$ragDocCount docs\nsim: ${String.format("%.2f", ragSimilarityThreshold)}" else "Disabled",
                            onClick = onRagClick,
                            modifier = Modifier.weight(1f)
                        )

                        // Tasks Card
                        FeatureCard(
                            title = "Tasks",
                            icon = Icons.Default.Alarm,
                            status = if (remindersCount > 0) "$remindersCount active" else "None",
                            statusColor = if (remindersCount > 0) Color(0xFFFF9800) else Color(0xFF9E9E9E),
                            details = if (remindersCount > 0) "Reminders set" else "No reminders",
                            onClick = onRemindersClick,
                            modifier = Modifier.weight(1f)
                        )

                        // Analytics Card
                        FeatureCard(
                            title = "Analytics",
                            icon = Icons.Default.Analytics,
                            status = if (analyticsEventCount > 0) "Tracking" else "Inactive",
                            statusColor = if (analyticsEventCount > 0) Color(0xFF9C27B0) else Color(0xFF9E9E9E),
                            details = "$analyticsEventCount events",
                            onClick = onAnalyticsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка с дополнительными инструментами
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tools:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ToolChip(
                            label = "Git",
                            isAvailable = gitAvailable
                        )

                        ToolChip(
                            label = "MCP",
                            isAvailable = mcpAvailable
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка одной фичи
 */
@Composable
private fun FeatureCard(
    title: String,
    icon: ImageVector,
    status: String,
    statusColor: Color,
    details: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = statusColor
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = statusColor
                    ) {}
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Точка статуса в компактном режиме
 */
@Composable
private fun FeatureStatusDot(
    icon: ImageVector,
    isActive: Boolean,
    color: Color
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = if (isActive) color else Color(0xFF9E9E9E)
    )
}

/**
 * Чип для инструмента
 */
@Composable
private fun ToolChip(
    label: String,
    isAvailable: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (isAvailable) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            if (isAvailable) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
            }
        }
    }
}
