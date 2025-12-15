package com.aichallengekmp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aichallengekmp.models.ModelInfoDto
import com.aichallengekmp.models.SessionDetailResponse
import com.aichallengekmp.models.SessionSettingsDto

/**
 * Диалог настроек сессии
 */
@Composable
fun SessionSettingsDialog(
    session: SessionDetailResponse,
    availableModels: List<ModelInfoDto>,
    availableProfiles: List<com.aichallengekmp.model.UserProfile>,
    currentProfile: com.aichallengekmp.model.UserProfile?,
    onDismiss: () -> Unit,
    onSave: (SessionSettingsDto) -> Unit
) {
    var settings by remember { mutableStateOf(session.settings) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Заголовок
                Text(
                    text = "Настройки чата",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Настройки
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    DefaultSettingsPanel(
                        settings = settings,
                        availableModels = availableModels,
                        availableProfiles = availableProfiles,
                        currentProfile = currentProfile,
                        onSettingsChange = { settings = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(onClick = { onSave(settings) }) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

/**
 * Диалог подтверждения удаления
 */
@Composable
fun DeleteConfirmationDialog(
    sessionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить чат?") },
        text = {
            Text("Вы уверены, что хотите удалить чат \"$sessionName\"? Это действие нельзя отменить.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
