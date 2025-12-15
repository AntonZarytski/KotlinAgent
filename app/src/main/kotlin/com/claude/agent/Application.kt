package com.claude.agent

import com.claude.agent.config.AppConfig
import com.claude.agent.database.ConversationRepository
import com.claude.agent.database.DatabaseFactory
import com.claude.agent.routes.chatRoutes
import com.claude.agent.routes.healthRoutes
import com.claude.agent.routes.sessionRoutes
import com.claude.agent.services.ClaudeClient
import com.claude.agent.services.GeolocationService
import com.claude.agent.services.HistoryCompressor
import com.claude.agent.services.MCPTools
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File

/**
 * Главный файл приложения Ktor.
 *
 * Аналог App.py из Python-версии.
 * Настраивает сервер, роутинг, middleware и запускает приложение.
 */

fun main() {
    // Инициализация базы данных
    DatabaseFactory.init()

    // Запуск Ktor сервера
    embeddedServer(
        Netty,
        port = AppConfig.port,
        host = AppConfig.host,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")

    // === HTTP клиент для внешних запросов ===
    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        // Настройка таймаутов для Claude API
        // Claude API может отвечать долго (30-60+ секунд для длинных ответов)
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000  // 120 секунд на весь запрос
            connectTimeoutMillis = 30_000   // 30 секунд на подключение
            socketTimeoutMillis = 120_000   // 120 секунд на чтение данных из сокета
        }
    }

    // === Инициализация сервисов ===
    val repository = ConversationRepository()
    val geolocationService = GeolocationService(httpClient)
    val mcpTools = MCPTools(httpClient, geolocationService)
    val claudeClient = ClaudeClient(httpClient, mcpTools)
    val historyCompressor = HistoryCompressor(claudeClient)

    logger.info("=== Сервисы инициализированы ===")
    logger.info("Порт: ${AppConfig.port}")
    logger.info("Хост: ${AppConfig.host}")
    logger.info("================================")

    // === Конфигурация Ktor ===
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            encodeDefaults = true
        })
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.local.uri.startsWith("/api") }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Необработанное исключение: ${cause.message}", cause)
            call.respondText(
                text = """{"error": "Внутренняя ошибка сервера: ${cause.message}"}""",
                status = HttpStatusCode.InternalServerError,
                contentType = ContentType.Application.Json
            )
        }
    }

    // === Роутинг ===
    routing {
        // API routes должны быть определены ПЕРВЫМИ, чтобы не перекрывались статическими файлами

        // Health check и tools
        healthRoutes(claudeClient)

        // Chat endpoints
        chatRoutes(claudeClient, historyCompressor, repository)

        // Session management
        sessionRoutes(repository)

        // Статические файлы (UI) - в конце, чтобы не перекрывать API роуты
        val staticFolder = AppConfig.staticFolder
        val staticPath = File(staticFolder)

        if (staticPath.exists() && staticPath.isDirectory) {
            logger.info("Статические файлы доступны из: $staticFolder")

            // Serve index.html for root path
            get("/") {
                val indexFile = File(staticPath, "index.html")
                call.respondFile(indexFile)
            }
//            val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
//
//            Window(
//                onCloseRequest = ::exitApplication,
//                title = "Claude AI Chat",
//                state = windowState
//            ) {
//                ClaudeChatApp()
//            }

            // Serve all other static files
            static("/") {
                files(staticPath)
            }
        } else {
            logger.warn("Папка статических файлов не найдена: $staticFolder")

            // Fallback если папка ui не найдена
            get("/") {
                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><title>KotlinAgent</title></head>
                    <body>
                        <h1>KotlinAgent API</h1>
                        <p>Сервер работает!</p>
                        <p style="color: red;">⚠️ Папка UI не найдена: $staticFolder</p>
                        <ul>
                            <li><a href="/health">GET /health</a> - Health check</li>
                            <li><a href="/api/tools">GET /api/tools</a> - MCP инструменты</li>
                            <li>POST /api/chat - Отправить сообщение Claude</li>
                            <li>GET /api/sessions - Список сессий</li>
                        </ul>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html
                )
            }
        }
    }

    logger.info("=== Сервер запущен ===")
    logger.info("URL: http://${AppConfig.host}:${AppConfig.port}")
    logger.info("======================")
}
