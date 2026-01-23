package com.claude.agent

import com.claude.agent.config.AppConfig
import com.claude.agent.config.PromptCachingConfig
import com.claude.agent.config.ToolsFilteringConfig
import com.claude.agent.database.ConversationRepository
import com.claude.agent.database.DatabaseFactory
import com.claude.agent.routes.chatRoutes
import com.claude.agent.routes.fileTreeRoutes
import com.claude.agent.routes.healthRoutes
import com.claude.agent.routes.metricsRoutes
import com.claude.agent.routes.ragRoutes
import com.claude.agent.routes.reminderRoutes
import com.claude.agent.routes.sessionRoutes
import com.claude.agent.routes.ticketRoutes
import com.claude.agent.routes.webSocketRoutes
import com.claude.agent.service.ReminderService
import com.claude.agent.service.SupportTicketService
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
import com.claude.agent.llm.mcp.local.GitRepositoryMcp
import com.claude.agent.llm.mcp.local.HelpMcp
import com.claude.agent.llm.mcp.local.SupportTicketMcp
import com.claude.agent.llm.mcp.local.GooglePlayPublisherMcp
import com.claude.agent.llm.mcp.remote.AirTicketsMcp
import com.claude.agent.routes.prReviewRoutes
import com.claude.agent.service.GitHubService
import com.claude.agent.service.GooglePlayService
import com.claude.agent.service.LocalAgentManager
import com.claude.agent.service.OllamaEmbeddingClient
import com.claude.agent.service.RagService
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
 * Настраивает сервер, роутинг, middleware и запускает приложение.
 */

fun main() {
    val logger = LoggerFactory.getLogger("Application")

    // Запускаем Compose UI webpack dev server в фоне (если нужно)
    // По умолчанию ОТКЛЮЧЕНО, чтобы избежать бесконечной пересборки
    val autoStartComposeUI = System.getProperty("autoStartComposeUI", "false").toBoolean()
    if (autoStartComposeUI) {
        startComposeUIDevServer(logger)
    }

    logger.info("=== Starting Application ===")

    // Инициализация базы данных
    logger.info("Initializing database...")
    try {
        DatabaseFactory.init()
        logger.info("✅ Database initialized successfully")
    } catch (e: Exception) {
        logger.error("❌ Failed to initialize database: ${e.message}", e)
        throw e
    }

    // ВАЖНО: НЕ инициализируем RAG базу данных здесь!
    // Exposed не поддерживает множественные подключения в одном процессе.
    // RAG база данных инициализируется только в модуле :rag
    // DatabaseFactory.initRagDatabase("rag_index.db")

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

/**
 * Разрешает путь к Compose Web сборке.
 * Ищет скомпилированные файлы Compose Web в нескольких возможных местах:
 *
 * **Production (после deploy.sh):**
 * - `/home/agent/KotlinAgent/ui/` - deploy.sh копирует сюда файлы из compose-ui/build/dist/js/productionExecutable/
 * - Содержит: index.html, compose-web.js, styles.css
 *
 * **Development (локальная разработка):**
 * - `compose-ui/build/kotlin-webpack/js/productionExecutable/` - результат ./gradlew :compose-ui:jsBrowserProductionWebpack (приоритет)
 * - `compose-ui/build/dist/js/productionExecutable/` - результат ./gradlew :compose-ui:jsBrowserDistribution
 * - `compose-ui/build/distributions/` - альтернативная папка сборки
 *
 * @param logger Логгер для отладочных сообщений
 * @return File объект с найденной папкой или null, если не найдена
 */
private fun resolveComposeWebPath(logger: org.slf4j.Logger): File? {
    val possiblePaths = listOf(
        // ПРИОРИТЕТ 1: Development - свежая сборка из compose-ui/build
        // Processed resources (содержит index.html и styles.css)
        File("compose-ui/build/processedResources/js/main"),
        File(System.getProperty("user.dir"), "compose-ui/build/processedResources/js/main"),
        File("../compose-ui/build/processedResources/js/main"),

        // Webpack output (jsBrowserProductionWebpack)
        File("compose-ui/build/kotlin-webpack/js/productionExecutable"),
        File(System.getProperty("user.dir"), "compose-ui/build/kotlin-webpack/js/productionExecutable"),
        File("../compose-ui/build/kotlin-webpack/js/productionExecutable"),

        // Старые пути (jsBrowserDistribution)
        File("compose-ui/build/dist/js/productionExecutable"),
        File(System.getProperty("user.dir"), "compose-ui/build/dist/js/productionExecutable"),
        File("../compose-ui/build/dist/js/productionExecutable"),
        File("compose-ui/build/distributions"),

        // ПРИОРИТЕТ 2: Production - deploy.sh копирует в ui/
        // Используется только если compose-ui/build не найден
        File("ui"),
        File(System.getProperty("user.dir"), "ui")
    )

    logger.debug("Поиск Compose Web сборки в следующих местах:")
    for (path in possiblePaths) {
        logger.debug("  - ${path.absolutePath} (exists: ${path.exists()}, isDirectory: ${path.isDirectory})")
        if (path.exists() && path.isDirectory) {
            val indexFile = File(path, "index.html")
            if (indexFile.exists()) {
                logger.info("✅ Найдена Compose Web сборка: ${path.absolutePath}")
                return path
            } else {
                logger.debug("    Папка найдена, но index.html отсутствует")
            }
        }
    }

    logger.warn("❌ Compose Web сборка не найдена")
    return null
}

/**
 * Разрешает путь к папке статических файлов (legacy).
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

/**
 * Запускает Compose UI webpack dev server в отдельном процессе.
 * Это обеспечивает hot reload при изменении файлов.
 */
private fun startComposeUIDevServer(logger: org.slf4j.Logger) {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val gradlewCommand = if (isWindows) "gradlew.bat" else "./gradlew"

    val projectRoot = File(System.getProperty("user.dir"))
    val gradlewFile = File(projectRoot, gradlewCommand)

    if (!gradlewFile.exists()) {
        logger.warn("⚠️ Gradle wrapper не найден, пропускаем автозапуск Compose UI")
        return
    }

    try {
        // УБРАН --continuous флаг для предотвращения бесконечной пересборки
        // Используем production build для стабильности
        val processBuilder = ProcessBuilder()
            .command(gradlewFile.absolutePath, ":compose-ui:jsBrowserProductionWebpack")
            .directory(projectRoot)
            .redirectOutput(File(projectRoot, "app.log").apply {
                parentFile?.mkdirs()
            })
            .redirectError(ProcessBuilder.Redirect.appendTo(File(projectRoot, "app.log")))

        logger.info("🚀 Сборка Compose UI (production)...")
        logger.info("   Команда: ${processBuilder.command().joinToString(" ")}")
        logger.info("   Логи: app.log")

        val process = processBuilder.start()

        // Ждем завершения сборки
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            logger.info("✅ Compose UI успешно собран")
        } else {
            logger.error("❌ Ошибка сборки Compose UI (exit code: $exitCode)")
            logger.error("   Проверьте app.log для деталей")
        }

    } catch (e: Exception) {
        logger.error("❌ Не удалось собрать Compose UI: ${e.message}", e)
    }
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
                encodeDefaults = true  // ВАЖНО: кодировать дефолтные значения (для Ollama model field)
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

    // === Инициализация RAG сервисов (опционально) ===
    val ragService = try {
        // Путь относительно рабочей директории (корень проекта при запуске через Gradle)
        RagService(ragDatabasePath = "rag_index.db")
    } catch (e: Exception) {
        logger.warn("RAG service initialization failed: ${e.message}")
        null
    }

    val ollamaEmbeddingClient = try {
        OllamaEmbeddingClient(httpClient = httpClient)
    } catch (e: Exception) {
        logger.warn("Ollama embedding client initialization failed: ${e.message}")
        null
    }

    // === Инициализация сервисов ===
    val repository = ConversationRepository()
    val webSocketService = WebSocketService()

    // GitHub Integration
    val githubToken = AppConfig.githubToken
    val githubService = GitHubService(httpClient, githubToken)
    if (githubToken != null) {
        logger.info("✅ GitHub integration enabled (token configured)")
    } else {
        logger.warn("⚠️ GitHub integration disabled (GITHUB_TOKEN not set)")
    }

    val reminderService = ReminderService(repository, webSocketService)
    val ticketService = SupportTicketService()

    // Google Play Publisher Service
    val googlePlayService = if (AppConfig.googlePlayServiceAccountPath != null) {
        try {
            GooglePlayService(AppConfig.googlePlayServiceAccountPath!!)
                .also { logger.info("✅ Google Play Publisher service initialized") }
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize Google Play service: ${e.message}")
            null
        }
    } else {
        logger.info("⚠️ Google Play Publisher not configured (GOOGLE_PLAY_SERVICE_ACCOUNT_PATH not set)")
        null
    }

    val remoteMcpProvider = RemoteMcpProvider(listOf(AirTicketsMcp()))

    val reminderMcp = ReminderMcp(reminderService)
    val supportTicketMcp = SupportTicketMcp(ticketService)
    val googlePlayPublisherMcp = GooglePlayPublisherMcp(googlePlayService, ticketService)
    val helpMcp = HelpMcp(ragService, ollamaEmbeddingClient)

    val localMcpProvider = LocalMcpProvider(
        listOf(
            ActionPlannerMcp(),
            WeatherMcp(httpClient, geolocationService),
            SolarActivityMcp(httpClient, geolocationService),
            ChatSummaryMcp(),
            reminderMcp,
            supportTicketMcp,
            googlePlayPublisherMcp,
            AndroidStudioLocalMcp(),
            GitRepositoryMcp(),
            helpMcp
            )
    )

    // === Инициализация сервисов оптимизации ===
    val tokenMetricsService = TokenMetricsService()
    val toolsFilterService = ToolsFilterService()

    val mcpTools = MCPTools(localMcpProvider = localMcpProvider, remoteMcpProvider = remoteMcpProvider)

    // Устанавливаем зависимости для HelpMcp после создания providers
    helpMcp.localMcpProvider = localMcpProvider
    helpMcp.remoteTools = remoteMcpProvider.getAllServers()

    // === Инициализация LLM провайдеров ===

    // Claude провайдер - создаем только если есть API ключ
    val claudeClient: ClaudeClient?
    val claudeLlmProvider: com.claude.agent.llm.LlmProvider?

    if (AppConfig.anthropicApiKeyOrNull != null) {
        logger.info("✅ Initializing Claude provider...")
        claudeClient = ClaudeClient(
            httpClient = httpClient,
            mcpTools = mcpTools,
            webSocketService = webSocketService,
            tokenMetricsService = tokenMetricsService,
            toolsFilterService = toolsFilterService,
            ragService = ragService,
            ollamaEmbeddingClient = ollamaEmbeddingClient
        )
        claudeLlmProvider = com.claude.agent.llm.ClaudeLlmProvider(claudeClient)
    } else {
        logger.warn("⚠️ Claude provider disabled (ANTHROPIC_API_KEY not set)")
        claudeClient = null
        claudeLlmProvider = null
    }

    // Qwen провайдер - использует модель из конфигурации
    logger.info("✅ Initializing Qwen provider with model: ${AppConfig.ollamaModel}")
    val qwen15bProvider = com.claude.agent.llm.QwenLlmProvider(
        httpClient = httpClient,
        mcpTools = mcpTools,
        webSocketService = webSocketService,
        baseUrl = AppConfig.ollamaUrl,
        modelName = AppConfig.ollamaModel,
        ragService = ragService,
        ollamaEmbeddingClient = ollamaEmbeddingClient
    )

    // Для обратной совместимости - qwen7bProvider указывает на тот же провайдер
    val qwen7bProvider = qwen15bProvider

    // Выбираем провайдер по умолчанию из конфигурации
    val defaultLlmProvider = when (AppConfig.llmProvider.lowercase()) {
        "claude" -> {
            if (claudeLlmProvider != null) {
                logger.info("✅ Default LLM Provider: Claude (Anthropic)")
                claudeLlmProvider
            } else {
                logger.warn("⚠️ Claude provider requested but not available, falling back to Qwen 1.5B")
                qwen15bProvider
            }
        }
        "qwen-7b", "qwen7b" -> {
            logger.info("✅ Default LLM Provider: Qwen 7B (Ollama)")
            qwen7bProvider
        }
        "local", "qwen-1.5b", "qwen1.5b" -> {
            logger.info("✅ Default LLM Provider: Qwen 1.5B (Ollama)")
            qwen15bProvider
        }
        else -> {
            logger.warn("⚠️ Unknown LLM provider '${AppConfig.llmProvider}', using Qwen 1.5B")
            qwen15bProvider
        }
    }

    // Создаем фабрику провайдеров
    val llmProviderFactory = com.claude.agent.llm.LlmProviderFactory(
        claudeLlmProvider = claudeLlmProvider,
        qwen15bProvider = qwen15bProvider,
        qwen7bProvider = qwen7bProvider,
        defaultProvider = defaultLlmProvider
    )

    val historyCompressor = HistoryCompressor(defaultLlmProvider, tokenMetricsService)

    reminderService.llmProvider = defaultLlmProvider
    reminderService.mcpTools = mcpTools
    reminderMcp.llmProvider = defaultLlmProvider
    googlePlayPublisherMcp.llmProvider = defaultLlmProvider
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
        healthRoutes(claudeClient = claudeClient, mcpTools = mcpTools)

        // Chat endpoints
        chatRoutes(
            llmProviderFactory = llmProviderFactory,
            mcpTools = mcpTools,
            historyCompressor = historyCompressor,
            repository = repository
        )

        // Session management
        sessionRoutes(repository)

        // File tree management
        fileTreeRoutes()

        // Reminder management
        reminderRoutes(reminderService)

        // Ticket management
        ticketRoutes(ticketService)

        // RAG endpoints
        ragRoutes(ragService = ragService, ollamaEmbeddingClient = ollamaEmbeddingClient)

        // Token metrics
        metricsRoutes(tokenMetricsService)

        // WebSocket for real-time updates
        webSocketRoutes(webSocketService)

        // PR Review endpoints
        val prReviewService = com.claude.agent.service.PRReviewService(
            llmProvider = defaultLlmProvider,
            mcpTools = mcpTools,
            ragService = ragService,
            ollamaEmbeddingClient = ollamaEmbeddingClient,
            githubService = githubService
        )
        prReviewRoutes(prReviewService)

        // Статические файлы (UI) - ДОЛЖНЫ БЫТЬ В КОНЦЕ!

        // Попробуем найти Compose Web сборку
        val composeWebPath = resolveComposeWebPath(logger)

        // Отключаем кеширование для статических файлов в режиме разработки
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.request.local.uri.endsWith(".js") ||
                call.request.local.uri.endsWith(".css") ||
                call.request.local.uri.endsWith(".html")) {
                call.response.headers.append(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
                call.response.headers.append(HttpHeaders.Pragma, "no-cache")
                call.response.headers.append(HttpHeaders.Expires, "0")
            }
        }

        if (composeWebPath != null && composeWebPath.exists() && composeWebPath.isDirectory) {
            // Обслуживаем Compose Web приложение
            staticFiles("/", composeWebPath)

            logger.info("✅ Compose Web приложение доступно из: ${composeWebPath.absolutePath}")
            logger.info("⚠️ Кеширование отключено для режима разработки")
        } else {
            // Fallback к старым статическим файлам
            val staticFolder = AppConfig.staticFolder
            val staticPath = resolveStaticPath(staticFolder, logger)

            if (staticPath != null && staticPath.exists() && staticPath.isDirectory) {
                staticFiles("/static", staticPath)

                get("/") {
                    val indexFile = File(staticPath, "index.html")
                    if (indexFile.exists()) {
                        call.respondFile(indexFile)
                    } else {
                        call.respondText("UI not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                    }
                }

                logger.info("Статические файлы (legacy) доступны из: ${staticPath.absolutePath}")
            } else {
                logger.warn("⚠️ Ни Compose Web, ни legacy UI не найдены")

                // Fallback для корневого URL
                get("/") {
                    call.respondText(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><title>KotlinAgent</title></head>
                        <body>
                            <h1>KotlinAgent API</h1>
                            <p>Сервер работает!</p>
                            <p><strong>⚠️ UI не найден. Соберите Compose Web:</strong></p>
                            <pre>./gradlew :compose-ui:jsBrowserDistribution</pre>
                            <ul>
                                <li><a href="/health">GET /health</a> - Health check</li>
                                <li><a href="/api/tools">GET /api/tools</a> - MCP инструменты</li>
                                <li>POST /api/chat - Отправить сообщение Claude</li>
                                <li>GET /api/sessions - Список сессий</li>
                                <li><a href="/mcp/agents/status">GET /mcp/agents/status</a> - Статус локальных агентов</li>
                            </ul>
                        </body>
                        </html>
                        """.trimIndent(),
                        ContentType.Text.Html
                    )
                }
            }
        }
    }

    logger.info("=== Сервер запущен ===")
    logger.info("HTTP URL:  http://${AppConfig.host}:${AppConfig.port}")
    logger.info("HTTPS URL: https://${AppConfig.host}:8443")
    logger.info("======================")
}
