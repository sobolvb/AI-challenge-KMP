@file:OptIn(ExperimentalMaterial3Api::class)

package com.aichallengekmp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichallengekmp.models.ModelInfoDto
import com.aichallengekmp.models.SessionSettingsDto

@OptIn(ExperimentalMaterial3Api::class)

/**
 * Панель настроек по умолчанию (для новых сессий)
 */
@Composable
fun DefaultSettingsPanel(
    settings: SessionSettingsDto,
    availableModels: List<ModelInfoDto>,
    onSettingsChange: (SessionSettingsDto) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Выбор модели
        ModelSelector(
            selectedModelId = settings.modelId,
            availableModels = availableModels,
            onModelSelected = { modelId ->
                onSettingsChange(settings.copy(modelId = modelId))
            }
        )
        
        // Температура
        TemperatureSlider(
            temperature = settings.temperature,
            onTemperatureChange = { temp ->
                onSettingsChange(settings.copy(temperature = temp))
            }
        )
        
        // Максимум токенов
        MaxTokensInput(
            maxTokens = settings.maxTokens,
            onMaxTokensChange = { tokens ->
                onSettingsChange(settings.copy(maxTokens = tokens))
            }
        )
        
        // Порог сжатия
        CompressionThresholdSlider(
            threshold = settings.compressionThreshold,
            onThresholdChange = { threshold ->
                onSettingsChange(settings.copy(compressionThreshold = threshold))
            }
        )
        
        // Системный промпт
        SystemPromptInput(
            systemPrompt = settings.systemPrompt,
            onSystemPromptChange = { prompt ->
                onSettingsChange(settings.copy(systemPrompt = prompt))
            }
        )
    }
}

/**
 * Выбор модели
 */
@Composable
private fun ModelSelector(
    selectedModelId: String,
    availableModels: List<ModelInfoDto>,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = availableModels.find { it.id == selectedModelId }
    
    Column {
        Text(
            text = "Модель",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedModel?.displayName ?: selectedModelId,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            onModelSelected(model.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Слайдер температуры
 */
@Composable
private fun TemperatureSlider(
    temperature: Double,
    onTemperatureChange: (Double) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Температура",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.2f", temperature),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = temperature.toFloat(),
            onValueChange = { onTemperatureChange(it.toDouble()) },
            valueRange = 0f..1.2f,
            steps = 11
        )
        Text(
            text = "Выше — креативнее, ниже — точнее",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Ввод максимума токенов
 */
@Composable
private fun MaxTokensInput(
    maxTokens: Int,
    onMaxTokensChange: (Int) -> Unit
) {
    var textValue by remember(maxTokens) { mutableStateOf(maxTokens.toString()) }
    
    Column {
        Text(
            text = "Максимум токенов в ответе",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { value ->
                val digits = value.filter { it.isDigit() }
                textValue = digits
                digits.toIntOrNull()?.let { onMaxTokensChange(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("2000") }
        )
    }
}

/**
 * Слайдер порога сжатия
 */
@Composable
private fun CompressionThresholdSlider(
    threshold: Int,
    onThresholdChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Порог сжатия диалога",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$threshold сообщений",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.toInt()) },
            valueRange = 5f..50f,
            steps = 8
        )
        Text(
            text = "История сжимается автоматически, но остается видимой",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Ввод системного промпта
 */
@Composable
private fun SystemPromptInput(
    systemPrompt: String?,
    onSystemPromptChange: (String?) -> Unit
) {
    Column {
        Text(
            text = "Системный промпт (опционально)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        OutlinedTextField(
            value = systemPrompt ?: "",
            onValueChange = { value ->
                onSystemPromptChange(value.takeIf { it.isNotBlank() })
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            placeholder = { Text("Например: Ты опытный программист на Kotlin...") },
            maxLines = 4
        )
    }
}
