package com.aichallengekmp.di

import com.aichallengekmp.chat.ChatClientProvider
import com.aichallengekmp.ui.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI модуль для клиента
 */
val appModule = module {
    
    // Repository
    single { ChatClientProvider.repository }
    
    // ViewModel
    viewModel { ChatViewModel(get()) }
}
