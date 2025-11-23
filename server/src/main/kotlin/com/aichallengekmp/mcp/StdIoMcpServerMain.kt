package com.aichallengekmp.mcp

import com.aichallengekmp.di.AppContainer
import com.aichallengekmp.service.ReminderService
import io.ktor.client.request.invoke
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

fun main(): kotlin.Unit = runBlocking {
    val reminderService = AppContainer.reminderService
    val trackerService = AppContainer.trackerTools//(/* если есть */)

    val server = Server(
        serverInfo = Implementation("aichallengekmp-stdio-server", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null)
            )
        )
    )

    // импортируй свои функции регистрации
//    configureRemindersMcpServer( reminderService)
//    configureTrackerMcpServer( trackerService)

    val proc = ProcessBuilder().start()
    val inputSource = proc.inputStream.asSource().buffered()
    val outputSink = proc.outputStream.asSink().buffered()
    val transport = StdioServerTransport(
        inputStream = inputSource,
        outputStream = outputSink
    )

    println("🔥 STDIO MCP server started")
    server.connect(transport)
}








/**

 * Заглушка под STDIO MCP-сервер.
 *





 * Реальная реализация main() для STDIO-сервера зависит от того, как ты
 * решишь упаковывать и запускать его (один jar с трекером и напоминаниями
 * или раздельные). Сейчас этот файл не участвует в запуске основного Ktor
 * приложения и не влияет на работу MCP-клиента.
 */























































































































































































object StdIoMcpServerMainStub
