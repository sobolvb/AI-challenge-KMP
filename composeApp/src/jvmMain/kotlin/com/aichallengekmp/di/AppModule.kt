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

class Solution {
    fun exclusiveTime(n: Int, logs: List<String>): IntArray {
        var array = Array<Int>(n) { 0 }
        logs.forEach { log ->
            log.split(":").also {
                val threadId = it[0].toInt()
                val command = it[1]
                val time = it[2].toInt()
            }
        }
    }
}