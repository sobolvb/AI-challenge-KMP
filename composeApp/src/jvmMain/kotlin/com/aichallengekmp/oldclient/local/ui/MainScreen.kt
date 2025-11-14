package local.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import local.viewmodel.ReasoningViewModel
import local.viewmodel.UiState
import local.data.TokenComparisonResponse
import local.data.TaskResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ReasoningViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val comparisonData by viewModel.comparisonData.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "📊 Анализ токенов AI Агента",
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
            MainContent(
                uiState = uiState,
                inputText = inputText,
                comparisonData = comparisonData,
                onInputTextChanged = viewModel::onInputTextChanged,
                onSolveTask = viewModel::solveTask,
                onCompareTokens = viewModel::compareTokens,
                onClearAll = viewModel::clearAll,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun MainContent(
    uiState: UiState,
    inputText: String,
    comparisonData: TokenComparisonResponse?,
    onInputTextChanged: (String) -> Unit,
    onSolveTask: () -> Unit,
    onCompareTokens: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Input section с ограничением высоты
        TaskInputField(
            value = inputText,
            onValueChange = onInputTextChanged,
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
            Button(
                onClick = onSolveTask,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🎯 Выполнить задачу")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCompareTokens,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔬 Сравнить токены")
                }

                OutlinedButton(
                    onClick = onClearAll,
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
                WelcomeMessage()
            }
            is UiState.Loading -> {
                LoadingIndicator()
            }
            is UiState.TaskSuccess -> {
                TaskResultSection(data = uiState.data)
            }
            is UiState.ComparisonSuccess -> {
                ComparisonResultsSection(data = uiState.data)
            }
            is UiState.Error -> {
                ErrorMessage(
                    message = uiState.message,
                    onDismiss = onClearAll
                )
            }

            is UiState.DialogActive -> {}
        }
    }
}

@Composable
fun WelcomeMessage(
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
                text = "📊",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Анализ токенов AI Агента",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Введите задачу или сравните разные типы запросов:\n\n📊 Короткий запрос\n📊 Длинный запрос\n📊 Запрос, превышающий лимит\n\n🔍 + Анализ использования токенов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun TaskResultSection(
    data: TaskResponse,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🎯 Задача",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = data.task)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Использование токенов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Входных: ${data.tokenUsage.inputTokens}")
                    Text(text = "Выходных: ${data.tokenUsage.outputTokens}")
                    Text(
                        text = "Всего: ${data.tokenUsage.totalTokens}",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✅ Ответ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = data.answer)
                }
            }
        }
    }
}

@Composable
fun ComparisonResultsSection(
    data: TokenComparisonResponse,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "🔬 Сравнение токенов",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            RequestCard(
                title = "📊 Короткий запрос",
                data = data.shortRequest,
                color = MaterialTheme.colorScheme.primaryContainer
            )
        }

        item {
            RequestCard(
                title = "📊 Длинный запрос",
                data = data.longRequest,
                color = MaterialTheme.colorScheme.secondaryContainer
            )
        }

        item {
            RequestCard(
                title = "📊 Запрос, превышающий лимит",
                data = data.exceedingRequest,
                color = MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔍 Анализ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = data.analysis)
                }
            }
        }
    }
}

@Composable
fun RequestCard(
    title: String,
    data: TaskResponse,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Задача:",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = data.task.take(100) + if (data.task.length > 100) "..." else "",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "📊 Токены:",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = "Вход: ${data.tokenUsage.inputTokens} | Выход: ${data.tokenUsage.outputTokens} | Всего: ${data.tokenUsage.totalTokens}")
        }
    }
}
