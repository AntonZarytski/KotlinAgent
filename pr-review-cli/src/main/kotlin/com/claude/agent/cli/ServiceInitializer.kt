package com.claude.agent.cli

import com.claude.agent.config.localModel
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.llm.ClaudeLlmProvider
import com.claude.agent.llm.QwenLlmProvider
import com.claude.agent.llm.LlmProvider
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.llm.mcp.local.*
import com.claude.agent.llm.mcp.providers.LocalMcpProvider
import com.claude.agent.llm.mcp.providers.RemoteMcpProvider
import com.claude.agent.llm.mcp.remote.AirTicketsMcp
import com.claude.agent.service.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Инициализатор сервисов для CLI
 * 
 * Отвечает за создание и настройку всех необходимых сервисов
 */
class ServiceInitializer {
    private val logger = LoggerFactory.getLogger(ServiceInitializer::class.java)

    /**
     * Инициализирует все сервисы для выполнения ревью
     * 
     * @param options Опции ревью
     * @param sessionId ID сессии
     * @return Контейнер с инициализированными сервисами
     */
    suspend fun initialize(options: ReviewOptions, sessionId: String): ServiceContainer {
        logger.info("🔧 Initializing services...")

        // Устанавливаем путь к проекту для GitRepositoryMcp
        ProjectPathService.setProjectPath(sessionId, options.repoPath)

        val httpClient = createHttpClient()
        val geolocationService = GeolocationService(httpClient)
        val mcpTools = createMCPTools(httpClient, geolocationService)
        val webSocketService = WebSocketService()

        val (ragService, ollamaClient) = createRAGServices(options, httpClient)

        val claudeClient = createClaudeClient(
            httpClient, mcpTools, webSocketService, ragService, ollamaClient
        )

        // Создаем LLM провайдеры
        val llmProvider = createLlmProvider(
            options, httpClient, mcpTools, webSocketService, claudeClient
        )

        val reviewService = PRReviewService(
            llmProvider = llmProvider,
            mcpTools = mcpTools,
            ragService = ragService,
            ollamaEmbeddingClient = ollamaClient
        )

        logger.info("✅ Services initialized")

        return ServiceContainer(
            httpClient = httpClient,
            reviewService = reviewService
        )
    }

    /**
     * Создает HTTP клиент
     */
    private fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    /**
     * Создает MCP Tools
     */
    private fun createMCPTools(
        httpClient: HttpClient,
        geolocationService: GeolocationService
    ): MCPTools {
        val remoteMcpProvider = RemoteMcpProvider(listOf(AirTicketsMcp()))

        val localMcpProvider = LocalMcpProvider(
            listOf(
                ActionPlannerMcp(),
                WeatherMcp(httpClient, geolocationService),
                SolarActivityMcp(httpClient, geolocationService),
                ChatSummaryMcp(),
                AndroidStudioLocalMcp(),
                GitRepositoryMcp()
            )
        )

        return MCPTools(
            localMcpProvider = localMcpProvider,
            remoteMcpProvider = remoteMcpProvider
        )
    }

    /**
     * Создает RAG сервисы (опционально)
     */
    private suspend fun createRAGServices(
        options: ReviewOptions,
        httpClient: HttpClient
    ): Pair<RagService?, OllamaEmbeddingClient?> {
        if (!options.enableRag) {
            logger.info("ℹ️ RAG disabled")
            return Pair(null, null)
        }

        val ragService = if (File(options.ragDbPath).exists()) {
            logger.info("📚 RAG enabled: ${options.ragDbPath}")
            RagService(options.ragDbPath)
        } else {
            logger.warn("⚠️ RAG database not found: ${options.ragDbPath}")
            null
        }

        val ollamaClient = try {
            logger.info("🔌 Connecting to Ollama...")
            OllamaEmbeddingClient(httpClient)
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to connect to Ollama: ${e.message}")
            null
        }

        return Pair(ragService, ollamaClient)
    }

    /**
     * Создает Claude клиент
     */
    private fun createClaudeClient(
        httpClient: HttpClient,
        mcpTools: MCPTools,
        webSocketService: WebSocketService,
        ragService: RagService?,
        ollamaClient: OllamaEmbeddingClient?
    ): ClaudeClient {
        return ClaudeClient(
            httpClient = httpClient,
            mcpTools = mcpTools,
            webSocketService = webSocketService,
            tokenMetricsService = null,
            toolsFilterService = null,
            ragService = ragService,
            ollamaEmbeddingClient = ollamaClient
        )
    }

    /**
     * Создает LLM провайдер на основе опций
     */
    private fun createLlmProvider(
        options: ReviewOptions,
        httpClient: HttpClient,
        mcpTools: MCPTools,
        webSocketService: WebSocketService,
        claudeClient: ClaudeClient
    ): LlmProvider {
        // Создаем провайдеры
        val claudeLlmProvider = ClaudeLlmProvider(claudeClient)
        val qwenLlmProvider = QwenLlmProvider(
            httpClient = httpClient,
            mcpTools = mcpTools,
            webSocketService = webSocketService,
            baseUrl = "http://localhost:11434",
            modelName = localModel
        )

        // Выбираем провайдер (по умолчанию Claude для CLI)
        val provider = when (options.llmProvider?.lowercase()) {
            "local", "qwen" -> {
                logger.info("✅ Using Qwen (local) LLM provider")
                qwenLlmProvider
            }
            else -> {
                logger.info("✅ Using Claude LLM provider")
                claudeLlmProvider
            }
        }

        return provider
    }
}

/**
 * Контейнер с инициализированными сервисами
 */
data class ServiceContainer(
    val httpClient: HttpClient,
    val reviewService: PRReviewService
)

