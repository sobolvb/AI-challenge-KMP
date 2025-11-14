package local.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import local.viewmodel.ReasoningViewModel
import local.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatedMainScreen(
    viewModel: ReasoningViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val comparisonData by viewModel.comparisonData.collectAsState()
    val dialogMessages by viewModel.dialogMessages.collectAsState()
    val sessionInfo by viewModel.sessionInfo.collectAsState()
    val compressionStats by viewModel.compressionStats.collectAsState()
    val compressionAnalysis by viewModel.compressionAnalysis.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "💬 AI Агент с сжатием диалогов",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Input section
                TaskInputField(
                    value = inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = viewModel::solveTask,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🎯 Задача")
                        }

                        Button(
                            onClick = viewModel::sendDialogMessage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("💬 Диалог")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::compareTokens,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔬 Сравнение")
                        }

                        OutlinedButton(
                            onClick = viewModel::startNewDialog,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🆕 Новый")
                        }

                        OutlinedButton(
                            onClick = viewModel::clearAll,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🗑️ Очистить")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Content section
                when (uiState) {
                    is UiState.Initial -> {
                        WelcomeMessageDialog()
                    }
                    is UiState.Loading -> {
                        LoadingIndicator()
                    }
                    is UiState.TaskSuccess -> {
                        TaskResultSection(data = (uiState as UiState.TaskSuccess).data)
                    }
                    is UiState.ComparisonSuccess -> {
                        ComparisonResultsSection(data = (uiState as UiState.ComparisonSuccess).data)
                    }
                    is UiState.DialogActive -> {
                        DialogSection(
                            messages = dialogMessages,
                            sessionInfo = sessionInfo,
                            compressionStats = compressionStats,
                            compressionAnalysis = compressionAnalysis,
                            onLoadAnalysis = viewModel::loadCompressionAnalysis
                        )
                    }
                    is UiState.Error -> {
                        ErrorMessage(
                            message = (uiState as UiState.Error).message,
                            onDismiss = viewModel::clearAll
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeMessageDialog(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💬",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AI Агент с сжатием диалогов",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = """
                    Выберите режим:
                    
                    🎯 Задача - Решение одной задачи с подсчетом токенов
                    💬 Диалог - Разговор с автоматическим сжатием истории
                    🔬 Сравнение - Анализ разных типов запросов
                    
                    📊 Сжатие каждые 10 сообщений
                    💾 Экономия до 85% токенов
                    🔍 Подробная статистика и анализ
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
