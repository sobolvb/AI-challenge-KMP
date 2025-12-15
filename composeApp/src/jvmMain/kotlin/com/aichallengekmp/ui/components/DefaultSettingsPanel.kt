@file:OptIn(ExperimentalMaterial3Api::class)

package com.aichallengekmp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichallengekmp.models.LLMPresets
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
    availableProfiles: List<com.aichallengekmp.model.UserProfile>,
    currentProfile: com.aichallengekmp.model.UserProfile?,
    onSettingsChange: (SessionSettingsDto) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Выбор профиля пользователя
        // DEBUG: Показываем количество профилей
        Text(
            text = "DEBUG: Загружено профилей: ${availableProfiles.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        ProfileSelector(
            currentProfile = availableProfiles.firstOrNull { it.id == settings.selectedProfileId } ?: currentProfile,
            availableProfiles = availableProfiles,
            onProfileSelected = { profileId ->
                onSettingsChange(settings.copy(selectedProfileId = profileId))
            }
        )

        HorizontalDivider()

        // Выбор модели
        ModelSelector(
            selectedModelId = settings.modelId,
            availableModels = availableModels,
            onModelSelected = { modelId ->
                onSettingsChange(settings.copy(modelId = modelId))
            }
        )

        // Preset selector
        PresetSelector(
            onPresetSelected = { preset ->
                onSettingsChange(LLMPresets.applyToSettings(preset, settings))
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Продвинутые параметры",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Top P (nucleus sampling)
        settings.topP?.let { topP ->
            TopPSlider(
                topP = topP,
                onTopPChange = { value ->
                    onSettingsChange(settings.copy(topP = value))
                }
            )
        } ?: run {
            OutlinedButton(
                onClick = { onSettingsChange(settings.copy(topP = 0.9)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Включить Top-P sampling")
            }
        }

        // Top K sampling
        settings.topK?.let { topK ->
            TopKSlider(
                topK = topK,
                onTopKChange = { value ->
                    onSettingsChange(settings.copy(topK = value))
                }
            )
        } ?: run {
            OutlinedButton(
                onClick = { onSettingsChange(settings.copy(topK = 40)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Включить Top-K sampling")
            }
        }

        // Context window
        settings.numCtx?.let { numCtx ->
            ContextWindowInput(
                numCtx = numCtx,
                onNumCtxChange = { value ->
                    onSettingsChange(settings.copy(numCtx = value))
                }
            )
        } ?: run {
            OutlinedButton(
                onClick = { onSettingsChange(settings.copy(numCtx = 8192)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Настроить Context Window")
            }
        }

        // Repeat penalty
        settings.repeatPenalty?.let { penalty ->
            RepeatPenaltySlider(
                repeatPenalty = penalty,
                onRepeatPenaltyChange = { value ->
                    onSettingsChange(settings.copy(repeatPenalty = value))
                }
            )
        } ?: run {
            OutlinedButton(
                onClick = { onSettingsChange(settings.copy(repeatPenalty = 1.1)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Включить Repeat Penalty")
            }
        }

        // Seed
        settings.seed?.let { seed ->
            SeedInput(
                seed = seed,
                onSeedChange = { value ->
                    onSettingsChange(settings.copy(seed = value))
                }
            )
        } ?: run {
            OutlinedButton(
                onClick = { onSettingsChange(settings.copy(seed = 42)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Установить Seed (для воспроизводимости)")
            }
        }
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

/**
 * Preset selector
 */
@Composable
private fun PresetSelector(
    onPresetSelected: (com.aichallengekmp.models.LLMPreset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<com.aichallengekmp.models.LLMPreset?>(null) }

    Column {
        Text(
            text = "Быстрые настройки (Presets)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedPreset?.name ?: "Выберите preset...",
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
                LLMPresets.ALL.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.name, fontWeight = FontWeight.Bold)
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            selectedPreset = preset
                            onPresetSelected(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Top-P (nucleus sampling) slider
 */
@Composable
private fun TopPSlider(
    topP: Double,
    onTopPChange: (Double) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top-P (Nucleus Sampling)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.2f", topP),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = topP.toFloat(),
            onValueChange = { onTopPChange(it.toDouble()) },
            valueRange = 0f..1f,
            steps = 19
        )
        Text(
            text = "Ограничивает набор токенов по кумулятивной вероятности",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Top-K sampling slider
 */
@Composable
private fun TopKSlider(
    topK: Int,
    onTopKChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top-K Sampling",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$topK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = topK.toFloat(),
            onValueChange = { onTopKChange(it.toInt()) },
            valueRange = 1f..100f,
            steps = 98
        )
        Text(
            text = "Ограничивает выбор K наиболее вероятными токенами",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Context window input
 */
@Composable
private fun ContextWindowInput(
    numCtx: Int,
    onNumCtxChange: (Int) -> Unit
) {
    var textValue by remember(numCtx) { mutableStateOf(numCtx.toString()) }

    Column {
        Text(
            text = "Context Window (tokens)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { value ->
                    val digits = value.filter { it.isDigit() }
                    textValue = digits
                    digits.toIntOrNull()?.let { onNumCtxChange(it) }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("8192") }
            )

            // Quick buttons
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { textValue = "2048"; onNumCtxChange(2048) }) {
                        Text("2K", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { textValue = "4096"; onNumCtxChange(4096) }) {
                        Text("4K", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { textValue = "8192"; onNumCtxChange(8192) }) {
                        Text("8K", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { textValue = "16384"; onNumCtxChange(16384) }) {
                        Text("16K", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { textValue = "32768"; onNumCtxChange(32768) }) {
                        Text("32K", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(
            text = "Размер контекстного окна модели",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Repeat penalty slider
 */
@Composable
private fun RepeatPenaltySlider(
    repeatPenalty: Double,
    onRepeatPenaltyChange: (Double) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Repeat Penalty",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.2f", repeatPenalty),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = repeatPenalty.toFloat(),
            onValueChange = { onRepeatPenaltyChange(it.toDouble()) },
            valueRange = 0f..2f,
            steps = 19
        )
        Text(
            text = "Штраф за повторение токенов (выше = меньше повторов)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Seed input for reproducibility
 */
@Composable
private fun SeedInput(
    seed: Int,
    onSeedChange: (Int) -> Unit
) {
    var textValue by remember(seed) { mutableStateOf(seed.toString()) }

    Column {
        Text(
            text = "Random Seed",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = textValue,
            onValueChange = { value ->
                val digits = value.filter { it.isDigit() || it == '-' }
                textValue = digits
                digits.toIntOrNull()?.let { onSeedChange(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("42") }
        )
        Text(
            text = "Фиксирует генерацию для воспроизводимости результатов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
