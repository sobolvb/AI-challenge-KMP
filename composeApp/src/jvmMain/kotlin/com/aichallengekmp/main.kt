package com.aichallengekmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aichallengekmp.chat.ChatClientProvider
import com.aichallengekmp.di.appModule
import com.aichallengekmp.offline.OfflineChatScreen
import com.aichallengekmp.theme.ChatTheme
import com.aichallengekmp.ui.ChatScreen
import com.aichallengekmp.ui.ModernChatScreen
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

fun main() {
    // Инициализация Koin DI
    startKoin {
        modules(appModule)
    }

    application {
        val scope = rememberCoroutineScope()
        var offlineMode by remember { mutableStateOf(false) }
        var isChecking by remember { mutableStateOf(true) }

        // Проверяем доступность сервера при запуске
        LaunchedEffect(Unit) {
            scope.launch {
                val serverUrl = ChatClientProvider.BASE_URL
                println("Checking server availability at: $serverUrl")

                val serverAvailable = try {
                    val client = HttpClient(CIO)
                    client.get("$serverUrl/api/models")
                    client.close()
                    println("Server is available!")
                    true
                } catch (e: Exception) {
                    println("Server not available: ${e.message}")
                    false
                }

                offlineMode = !serverAvailable
                isChecking = false
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = if (offlineMode) "AI Chat (Offline)" else "AI Challenge KMP",
        ) {
            ChatTheme {
                when {
                    isChecking -> LoadingScreen()
                    offlineMode -> OfflineChatScreen()
                    else -> ModernChatScreen()  // Новый современный UI
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Проверка подключения к серверу...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}