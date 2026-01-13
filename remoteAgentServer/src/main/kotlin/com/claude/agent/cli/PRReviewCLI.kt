package com.claude.agent.cli

import com.claude.agent.config.AppConfig
import com.claude.agent.llm.ClaudeClient
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * CLI для запуска PR ревью из командной строки или CI/CD
 * 
 * Использование:
 * java -jar app.jar review-pr --branch feature/new-feature --base-branch main --output review.md
 */
object PRReviewCLI {
    private val logger = LoggerFactory.getLogger(PRReviewCLI::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty() || args[0] != "review-pr") {
            printUsage()
            exitProcess(1)
        }

        val options = parseArguments(args.drop(1))
        
        if (!validateOptions(options)) {
            exitProcess(1)
        }

        logger.info("🤖 Starting AI PR Review CLI")
        logger.info("Branch: ${options.branch} → ${options.baseBranch}")

        try {
            val review = runBlocking {
                executeReview(options)
            }

            logger.info("✅ Review completed successfully")

            // Сохраняем результат
            val outputFile = File(options.outputPath)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(review)
            
            logger.info("📄 Review saved to: ${options.outputPath}")
            
            // Выводим в консоль
            println()
            println("=" .repeat(80))
            println(review)
            println("=" .repeat(80))

            exitProcess(0)

        } catch (e: Exception) {
            logger.error("❌ Failed to execute PR review: ${e.message}", e)
            println("ERROR: ${e.message}")
            exitProcess(1)
        }
    }

    private fun parseArguments(args: List<String>): ReviewOptions {
        val options = ReviewOptions()
        var i = 0

        while (i < args.size) {
            when (args[i]) {
                "--branch" -> {
                    if (i + 1 < args.size) {
                        options.branch = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --branch")
                    }
                }
                "--base-branch" -> {
                    if (i + 1 < args.size) {
                        options.baseBranch = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --base-branch")
                    }
                }
                "--output", "-o" -> {
                    if (i + 1 < args.size) {
                        options.outputPath = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --output")
                    }
                }
                "--pr-title" -> {
                    if (i + 1 < args.size) {
                        options.prTitle = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --pr-title")
                    }
                }
                "--pr-description" -> {
                    if (i + 1 < args.size) {
                        options.prDescription = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --pr-description")
                    }
                }
                "--repo-path" -> {
                    if (i + 1 < args.size) {
                        options.repoPath = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --repo-path")
                    }
                }
                "--enable-rag" -> {
                    options.enableRag = true
                    i++
                }
                "--rag-db-path" -> {
                    if (i + 1 < args.size) {
                        options.ragDbPath = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("Missing value for --rag-db-path")
                    }
                }
                "--help", "-h" -> {
                    printUsage()
                    exitProcess(0)
                }
                else -> {
                    throw IllegalArgumentException("Unknown option: ${args[i]}")
                }
            }
        }

        return options
    }

    private fun validateOptions(options: ReviewOptions): Boolean {
        if (options.branch.isBlank()) {
            logger.error("❌ Error: --branch is required")
            return false
        }

        if (AppConfig.anthropicApiKey.isBlank()) {
            logger.error("❌ Error: ANTHROPIC_API_KEY environment variable is not set")
            return false
        }

        // Проверяем существование репозитория
        val repoDir = File(options.repoPath)
        if (!repoDir.exists() || !File(repoDir, ".git").exists()) {
            logger.error("❌ Error: Git repository not found at ${options.repoPath}")
            return false
        }

        return true
    }

    private suspend fun executeReview(options: ReviewOptions): String {
        logger.info("🔧 Initializing services...")

        // Устанавливаем путь к проекту для GitRepositoryMcp
        val sessionId = "cli-${System.currentTimeMillis()}"
        ProjectPathService.setProjectPath(sessionId, options.repoPath)

        // Инициализация HTTP клиента
        val httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        // Инициализация geolocation service
        val geolocationService = GeolocationService(httpClient)

        // Инициализация MCP providers
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

        val mcpTools = MCPTools(
            localMcpProvider = localMcpProvider,
            remoteMcpProvider = remoteMcpProvider
        )

        val webSocketService = WebSocketService()

        // RAG сервисы (опционально)
        val ragService = if (options.enableRag && File(options.ragDbPath).exists()) {
            logger.info("📚 RAG enabled: ${options.ragDbPath}")
            RagService(options.ragDbPath)
        } else {
            logger.info("ℹ️ RAG disabled")
            null
        }

        val ollamaClient = if (options.enableRag) {
            try {
                logger.info("🔌 Connecting to Ollama...")
                OllamaEmbeddingClient(httpClient)
            } catch (e: Exception) {
                logger.warn("⚠️ Failed to connect to Ollama: ${e.message}")
                null
            }
        } else {
            null
        }

        // Claude клиент
        val claudeClient = ClaudeClient(
            httpClient = httpClient,
            mcpTools = mcpTools,
            webSocketService = webSocketService,
            tokenMetricsService = null,
            toolsFilterService = null,
            ragService = ragService,
            ollamaEmbeddingClient = ollamaClient
        )

        // Сервис ревью
        val reviewService = PRReviewService(
            claudeClient = claudeClient,
            mcpTools = mcpTools,
            ragService = ragService,
            ollamaEmbeddingClient = ollamaClient
        )

        logger.info("🔍 Analyzing PR...")

        // Выполняем ревью
        val prInfo = PRReviewService.PRInfo(
            branch = options.branch,
            targetBranch = options.baseBranch,
            title = options.prTitle,
            description = options.prDescription
        )

        val review = reviewService.reviewPR(
            prInfo = prInfo,
            sessionId = sessionId,
            repoPath = options.repoPath
        )

        // Форматируем результат
        val markdown = reviewService.formatReviewAsMarkdown(review, prInfo)

        // Очистка
        httpClient.close()

        return markdown
    }

    private fun printUsage() {
        println("""
            AI PR Review CLI
            
            Usage:
              java -jar app.jar review-pr [OPTIONS]
            
            Required Options:
              --branch BRANCH              Source branch to review
            
            Optional Options:
              --base-branch BRANCH         Target branch (default: main)
              --output PATH                Output file path (default: pr-review-report.md)
              --pr-title TITLE             Pull Request title
              --pr-description DESC        Pull Request description
              --repo-path PATH             Repository path (default: current directory)
              --enable-rag                 Enable RAG context retrieval
              --rag-db-path PATH           RAG database path (default: rag_index.db)
              --help, -h                   Show this help message
            
            Environment Variables:
              ANTHROPIC_API_KEY            Required - Your Anthropic API key
            
            Examples:
              # Basic usage
              java -jar app.jar review-pr --branch feature/new-api
              
              # With custom base branch and output
              java -jar app.jar review-pr --branch feature/auth --base-branch develop -o review.md
              
              # With RAG enabled
              java -jar app.jar review-pr --branch feature/refactor --enable-rag
              
              # Full example with all options
              java -jar app.jar review-pr \
                --branch feature/payment-integration \
                --base-branch main \
                --pr-title "Add payment gateway" \
                --pr-description "Integrates Stripe API" \
                --enable-rag \
                --output reports/pr-review.md
        """.trimIndent())
    }

    private data class ReviewOptions(
        var branch: String = "",
        var baseBranch: String = "main",
        var outputPath: String = "pr-review-report.md",
        var prTitle: String? = null,
        var prDescription: String? = null,
        var repoPath: String = System.getProperty("user.dir"),
        var enableRag: Boolean = false,
        var ragDbPath: String = "rag_index.db"
    )
}
