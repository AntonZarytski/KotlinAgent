package com.claude.agent

import com.claude.agent.config.AppConfig
import com.claude.agent.database.ConversationRepository
import com.claude.agent.database.DatabaseFactory
import com.claude.agent.routes.chatRoutes
import com.claude.agent.routes.healthRoutes
import com.claude.agent.routes.reminderRoutes
import com.claude.agent.routes.sessionRoutes
import com.claude.agent.routes.webSocketRoutes
import com.claude.agent.service.ReminderService
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.service.GeolocationService
import com.claude.agent.service.HistoryCompressor
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.llm.mcp.providers.RemoteMcpProvider
import com.claude.agent.service.WebSocketService
import com.claude.agent.llm.mcp.local.ChatSummaryMcp
import com.claude.agent.llm.mcp.providers.LocalMcpProvider
import com.claude.agent.llm.mcp.local.ReminderMcp
import com.claude.agent.llm.mcp.local.SolarActivityMcp
import com.claude.agent.llm.mcp.local.WeatherMcp
import com.claude.agent.llm.mcp.remote.AirTicketsMcp
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.generateCertificate
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
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.security.KeyStore

/**
 * Главный файл приложения Ktor.
 ** Настраивает сервер, роутинг, middleware и запускает приложение.
 */

/**
 * Разрешает путь к папке статических файлов.
 * Ищет папку в нескольких возможных местах:
 * 1. Относительно текущей рабочей директории
 * 2. Относительно корня проекта (user.dir)
 * 3. Относительно родительской директории (для запуска из app/)
 *
 * @param staticFolder Имя папки со статическими файлами (например, "ui")
 * @param logger Логгер для отладочных сообщений
 * @return File объект с найденной папкой или null, если не найдена
 */
private fun resolveStaticPath(staticFolder: String, logger: org.slf4j.Logger): File? {
    val possiblePaths = listOf(
        File(staticFolder),                                    // Относительно текущей директории
        File(System.getProperty("user.dir"), staticFolder),    // Относительно user.dir
        File(System.getProperty("user.dir"), "../$staticFolder"), // Родительская директория
        File("app/../$staticFolder")                           // Из app/ в корень проекта
    )

    logger.debug("Поиск статических файлов '$staticFolder' в следующих местах:")
    for (path in possiblePaths) {
        logger.debug("  - ${path.absolutePath} (exists: ${path.exists()}, isDirectory: ${path.isDirectory})")
        if (path.exists() && path.isDirectory) {
            val indexFile = File(path, "index.html")
            if (indexFile.exists()) {
                logger.info("Найдена папка статических файлов: ${path.absolutePath}")
                return path
            }
        }
    }

    return null
}

fun generateCertificateIfNeeded() {
    val certFile = File("ktor.p12")
    if (!certFile.exists()) {
        LoggerFactory.getLogger("Application").info("Генерация SSL сертификата...")
        generateCertificate(
            file = certFile,
            keyAlias = "ktor",
            keyPassword = "changeit",
            jksPassword = "changeit"
        )
        LoggerFactory.getLogger("Application").info("SSL сертификат создан: ${certFile.absolutePath}")
    }
}

fun loadKeyStore(filename: String, password: String): KeyStore {
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(File(filename).inputStream(), password.toCharArray())
    return keyStore
}

fun main() {
    val logger = LoggerFactory.getLogger("Application")

    // Инициализация базы данных
    DatabaseFactory.init()

    // Генерация SSL сертификата если его нет
    generateCertificateIfNeeded()

    // Запуск Ktor сервера с SSL (Ktor 3)
    embeddedServer(
        Netty,
        applicationEnvironment {
            log = logger
        },
        configure = {
            // HTTP коннектор
            connector {
                port = AppConfig.port
                host = AppConfig.host
            }

            // HTTPS коннектор
            val keyStoreFile = File("ktor.p12")
            val keyStore = loadKeyStore("ktor.p12", "changeit")

            sslConnector(
                keyStore = keyStore,
                keyAlias = "ktor",
                keyStorePassword = { "changeit".toCharArray() },
                privateKeyPassword = { "changeit".toCharArray() }
            ) {
                port = 8443
                host = AppConfig.host
                keyStorePath = keyStoreFile
            }
        },
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
    val geolocationService = GeolocationService(httpClient)

    // === Инициализация сервисов ===
    val repository = ConversationRepository()
    val webSocketService = WebSocketService()

    val reminderService = ReminderService(repository, webSocketService)

    val remoteMcpProvider = RemoteMcpProvider(listOf(AirTicketsMcp()))

    val localMcpProvider = LocalMcpProvider(
        listOf(
            WeatherMcp(httpClient, geolocationService),
            SolarActivityMcp(httpClient, geolocationService),
            ChatSummaryMcp(),
            ReminderMcp(reminderService)
        )
    )

    val mcpTools = MCPTools(localMcpProvider = localMcpProvider, remoteMcpProvider = remoteMcpProvider)
    val claudeClient = ClaudeClient(httpClient, mcpTools)
    val historyCompressor = HistoryCompressor(claudeClient)

    reminderService.claudeClient = claudeClient
    reminderService.mcpTools = mcpTools
    reminderService.startScheduler()

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

    install(WebSockets) {
        pingPeriodMillis = 15000
        timeoutMillis = 15000
        maxFrameSize = Long.MAX_VALUE
        masking = false
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
        // Health check и tools
        healthRoutes(claudeClient, mcpTools)

        // Chat endpoints
        chatRoutes(claudeClient, historyCompressor, repository)

        // Session management
        sessionRoutes(repository)

        // Reminder management
        reminderRoutes(reminderService)

        // WebSocket for real-time updates
        webSocketRoutes(webSocketService)

        // Статические файлы (UI)
        val staticFolder = AppConfig.staticFolder
        val staticPath = resolveStaticPath(staticFolder, logger)

        if (staticPath != null && staticPath.exists() && staticPath.isDirectory) {
            staticFiles("/", staticPath) {
                default("index.html")
            }
            logger.info("Статические файлы доступны из: ${staticPath.absolutePath}")
        } else {
            logger.warn("Папка статических файлов не найдена: $staticFolder")
            logger.warn("Проверенные пути:")
            logger.warn("  - ${File(staticFolder).absolutePath}")
            logger.warn("  - ${File(System.getProperty("user.dir"), staticFolder).absolutePath}")
        }

        // Fallback для корневого URL
        get("/") {
            val indexFile = if (staticPath != null) {
                File(staticPath, "index.html")
            } else {
                File(staticFolder, "index.html")
            }

            if (indexFile.exists()) {
                call.respondFile(indexFile)
            } else {
                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><title>KotlinAgent</title></head>
                    <body>
                        <h1>KotlinAgent API</h1>
                        <p>Сервер работает!</p>
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
    logger.info("HTTP URL:  http://${AppConfig.host}:${AppConfig.port}")
    logger.info("HTTPS URL: https://${AppConfig.host}:8443")
    logger.info("======================")
}
