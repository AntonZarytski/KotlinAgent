package com.claude.agent

import com.claude.agent.config.AppConfig
import com.claude.agent.config.PromptCachingConfig
import com.claude.agent.config.ToolsFilteringConfig
import com.claude.agent.database.ConversationRepository
import com.claude.agent.database.DatabaseFactory
import com.claude.agent.routes.chatRoutes
import com.claude.agent.routes.healthRoutes
import com.claude.agent.routes.metricsRoutes
import com.claude.agent.routes.reminderRoutes
import com.claude.agent.routes.sessionRoutes
import com.claude.agent.routes.webSocketRoutes
import com.claude.agent.service.ReminderService
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.service.GeolocationService
import com.claude.agent.service.HistoryCompressor
import com.claude.agent.service.TokenMetricsService
import com.claude.agent.service.ToolsFilterService
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.llm.mcp.local.ActionPlannerMcp
import com.claude.agent.llm.mcp.providers.RemoteMcpProvider
import com.claude.agent.service.WebSocketService
import com.claude.agent.llm.mcp.local.ChatSummaryMcp
import com.claude.agent.llm.mcp.providers.LocalMcpProvider
import com.claude.agent.llm.mcp.local.ReminderMcp
import com.claude.agent.llm.mcp.local.SolarActivityMcp
import com.claude.agent.llm.mcp.local.WeatherMcp
import com.claude.agent.llm.mcp.local.AndroidStudioLocalMcp
import com.claude.agent.llm.mcp.remote.AirTicketsMcp
import com.claude.agent.service.LocalAgentManager
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
    // Убираем ../ui префикс если он есть, оставляем только имя папки
    val folderName = staticFolder.removePrefix("../").removePrefix("./")

    val possiblePaths = listOf(
        File(folderName),                                      // ui относительно текущей директории
        File(System.getProperty("user.dir"), folderName),     // ui относительно user.dir
        File(System.getProperty("user.dir"), "../$folderName"), // ../ui из remoteAgentServer/
        File("../$folderName")                                 // ../ui относительно remoteAgentServer/
    )

    logger.debug("Поиск статических файлов '$staticFolder' -> '$folderName' в следующих местах:")
    for (path in possiblePaths) {
        logger.debug("  - ${path.absolutePath} (exists: ${path.exists()}, isDirectory: ${path.isDirectory})")
        if (path.exists() && path.isDirectory) {
            val indexFile = File(path, "index.html")
            if (indexFile.exists()) {
                logger.info("✅ Найдена папка статических файлов: ${path.absolutePath}")
                return path
            } else {
                logger.debug("    Папка найдена, но index.html отсутствует")
            }
        }
    }

    logger.warn("❌ Папка статических файлов '$folderName' не найдена")
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
        LoggerFactory.getLogger("Application").warn("⚠️ SSL сертификат содержит только localhost/127.0.0.1 - для продакшена нужен настоящий SSL")
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

    val reminderMcp = ReminderMcp(reminderService)

    val localMcpProvider = LocalMcpProvider(
        listOf(
            ActionPlannerMcp(),
            WeatherMcp(httpClient, geolocationService),
            SolarActivityMcp(httpClient, geolocationService),
            ChatSummaryMcp(),
            reminderMcp,
            AndroidStudioLocalMcp(),
        )
    )

    // === Инициализация сервисов оптимизации ===
    val tokenMetricsService = TokenMetricsService()
    val toolsFilterService = ToolsFilterService()

    val mcpTools = MCPTools(localMcpProvider = localMcpProvider, remoteMcpProvider = remoteMcpProvider)
    val claudeClient = ClaudeClient(
        httpClient = httpClient,
        mcpTools = mcpTools,
        webSocketService = webSocketService,
        tokenMetricsService = tokenMetricsService,
        toolsFilterService = toolsFilterService
    )
    val historyCompressor = HistoryCompressor(claudeClient, tokenMetricsService)

    reminderService.claudeClient = claudeClient
    reminderService.mcpTools = mcpTools
    reminderMcp.claudeClient = claudeClient
    reminderService.startScheduler()

    logger.info("=== Сервисы инициализированы ===")
    logger.info("Порт: ${AppConfig.port}")
    logger.info("Хост: ${AppConfig.host}")
    logger.info("Token optimization: ENABLED")
    logger.info("  - Prompt Caching: ${PromptCachingConfig.ENABLED}")
    logger.info("  - Tools Filtering: ${ToolsFilteringConfig.ENABLED}")
    logger.info("  - History Compression: ENABLED")
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
        filter { call ->
            call.request.local.uri.startsWith("/api") ||
                    call.request.local.uri.startsWith("/mcp")
        }
    }

    install(WebSockets) {
        pingPeriodMillis = 30000   // 30 секунд - сервер отправляет ping
        timeoutMillis = 60000      // 60 секунд - таймаут для ответа на ping
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
        // DEBUG: Логируем ВСЕ запросы к /mcp/*
        intercept(ApplicationCallPipeline.Call) {
            if (call.request.local.uri.startsWith("/mcp/")) {
                logger.warn("🔍 [DEBUG] Request to ${call.request.local.uri}")
                logger.warn("   Method: ${call.request.local.method.value}")
                logger.warn("   Headers: ${call.request.headers.names().joinToString { "$it=${call.request.headers[it]}" }}")
                logger.warn("   Upgrade: ${call.request.headers["Upgrade"]}")
                logger.warn("   Connection: ${call.request.headers["Connection"]}")
            }
        }

        // WebSocket для локальных агентов (ДОЛЖЕН БЫТЬ ПЕРВЫМ!)
        webSocket("/mcp/local-agent") {
            logger.info("🔌 [WEBSOCKET] New WebSocket connection to /mcp/local-agent")
            logger.info("   Headers: ${call.request.headers.names().map { "$it: ${call.request.headers[it]}" }}")
            LocalAgentManager.handleConnection(this)
        }

        // Endpoint для проверки статуса агентов
        get("/mcp/agents/status") {
            val agents = LocalAgentManager.getConnectedAgents()
            call.respond(mapOf(
                "connected_agents" to agents,
                "count" to agents.size
            ))
        }

        // Health check и tools
        healthRoutes(claudeClient, mcpTools)

        // Chat endpoints
        chatRoutes(claudeClient, historyCompressor, repository)

        // Session management
        sessionRoutes(repository)

        // Reminder management
        reminderRoutes(reminderService)

        // Token metrics
        metricsRoutes(tokenMetricsService)

        // WebSocket for real-time updates
        webSocketRoutes(webSocketService)

        // === БЕЗ КОРНЕВОГО РОУТА - ОН ПЕРЕХВАТЫВАЕТ WEBSOCKET ===

        // Подготавливаем путь к UI для статических файлов
        val staticFolder = AppConfig.staticFolder
        val staticPath = resolveStaticPath(staticFolder, logger)

        // Главная страница (ПОСЛЕ WebSocket роутов, но ПЕРЕД catch-all)
        get("/") {
            if (staticPath != null && staticPath.exists() && staticPath.isDirectory) {
                val indexFile = File(staticPath, "index.html")
                if (indexFile.exists()) {
                    call.respondFile(indexFile)
                } else {
                    call.respondText("UI index.html не найден", ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            } else {
                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><title>KotlinAgent API</title></head>
                    <body>
                        <h1>KotlinAgent API</h1>
                        <p>Сервер работает!</p>
                        <ul>
                            <li><a href="/health">GET /health</a> - Health check</li>
                            <li><a href="/api/tools">GET /api/tools</a> - MCP инструменты</li>
                            <li>POST /api/chat - Отправить сообщение Claude</li>
                            <li>GET /api/sessions - Список сессий</li>
                            <li><a href="/mcp/agents/status">GET /mcp/agents/status</a> - Статус локальных агентов</li>
                            <li><a href="/ui">Web UI</a> - Веб интерфейс</li>
                            <li>WebSocket: /mcp/local-agent</li>
                        </ul>
                        <p><strong>⚠️ UI не найден:</strong> Папка '$staticFolder' не найдена</p>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html
                )
            }
        }
        // Статические файлы для UI (ПОСЛЕ всех API роутов и конкретных путей!)
        if (staticPath != null && staticPath.exists() && staticPath.isDirectory) {
            // Обслуживаем статические файлы по /ui пути - БЕЗ КОРНЕВОГО ПУТИ!
            staticFiles("/ui", staticPath)

            logger.info("✅ Статические файлы доступны из: ${staticPath.absolutePath}")
            logger.info("✅ UI доступно по адресу: /ui (НЕ на корневом пути)")
        } else {
            logger.warn("❌ Папка статических файлов не найдена: $staticFolder")
        }
    }

    logger.info("=== Сервер запущен ===")
    logger.info("HTTP URL:  http://${AppConfig.host}:${AppConfig.port}")
    logger.info("HTTPS URL: https://${AppConfig.host}:8443")
    logger.info("======================")
}
