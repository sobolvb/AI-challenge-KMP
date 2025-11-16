package com.aichallengekmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aichallengekmp.di.appModule
import com.aichallengekmp.theme.ChatTheme
import com.aichallengekmp.ui.ChatScreen
import org.koin.core.context.startKoin

fun main() {
    // Инициализация Koin DI
    startKoin {
        modules(appModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Challenge KMP",
        ) {
            ChatTheme {
                ChatScreen()
            }
        }
    }
}