package com.aichallengekmp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Выдвижная панель настроек (справа)
 */
@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onModelConfigClick: () -> Unit,
    onVoiceSettingsClick: () -> Unit,
    onRagConfigClick: () -> Unit,
    onToolsManagerClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onExportImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(200)) + slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(300)
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300)
        )
    ) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            // Плавное затемнение с градиентом от панели (справа)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.5f)
                            ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY
                        )
                    )
                    .clickable(onClick = onDismiss)
            )

            // Панель с настройками
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp)
                    .align(Alignment.CenterEnd),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Заголовок
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Список секций настроек
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsSection(
                            title = "Profile Settings",
                            description = "Manage user profiles and preferences",
                            icon = Icons.Default.Person,
                            onClick = {
                                onProfileSettingsClick()
                                onDismiss()
                            }
                        )

                        SettingsSection(
                            title = "Model Configuration",
                            description = "Select and configure AI models",
                            icon = Icons.Default.Psychology,
                            onClick = {
                                onModelConfigClick()
                                onDismiss()
                            }
                        )

                        SettingsSection(
                            title = "Voice Settings",
                            description = "Speech recognition configuration",
                            icon = Icons.Default.Mic,
                            onClick = {
                                onVoiceSettingsClick()
                                onDismiss()
                            }
                        )

                        SettingsSection(
                            title = "RAG Configuration",
                            description = "Document search and similarity settings",
                            icon = Icons.Default.Search,
                            onClick = {
                                onRagConfigClick()
                                onDismiss()
                            }
                        )

                        SettingsSection(
                            title = "Tools Manager",
                            description = "Manage Git, MCP, and other tools",
                            icon = Icons.Default.Build,
                            onClick = {
                                onToolsManagerClick()
                                onDismiss()
                            }
                        )

                        SettingsSection(
                            title = "Analytics",
                            description = "View event tracking and statistics",
                            icon = Icons.Default.Analytics,
                            onClick = {
                                onAnalyticsClick()
                                onDismiss()
                            }
                        )

                        SettingsSection(
                            title = "Export / Import",
                            description = "Backup and restore your data",
                            icon = Icons.Default.ImportExport,
                            onClick = {
                                onExportImportClick()
                                onDismiss()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Версия приложения
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "God Agent v1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

/**
 * Секция настроек (кликабельная карточка)
 */
@Composable
private fun SettingsSection(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
