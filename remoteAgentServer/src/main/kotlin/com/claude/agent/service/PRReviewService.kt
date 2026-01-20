package com.claude.agent.service

import com.claude.agent.common.models.*
import com.claude.agent.llm.LlmProvider
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.models.Message
import com.claude.agent.service.review.*
import org.slf4j.LoggerFactory

/**
 * Сервис для автоматического ревью Pull Request'ов с использованием AI
 *
 * Координирует процесс ревью:
 * - Сбор данных о PR через GitRepositoryMcp
 * - Получение релевантного контекста из RAG
 * - Генерация ревью через LLM провайдер
 * - Публикация результатов в GitHub
 *
 * Делегирует специфичные задачи специализированным компонентам.
 */
class PRReviewService(
    private val llmProvider: LlmProvider,
    private val mcpTools: MCPTools,
    private val ragService: RagService? = null,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient? = null,
    private val githubService: GitHubService? = null
) {
    private val logger = LoggerFactory.getLogger(PRReviewService::class.java)

    // Специализированные компоненты
    private val dataCollector = PRDataCollector(mcpTools)
    private val ragRetriever = RAGContextRetriever(ragService, ollamaEmbeddingClient)
    private val promptBuilder = ReviewPromptBuilder()
    private val responseParser = ReviewResponseParser()
    private val markdownFormatter = ReviewMarkdownFormatter()
    private val githubPublisher = GitHubReviewPublisher(githubService, markdownFormatter)

    /**
     * Выполняет полный анализ PR и генерирует ревью
     *
     * @param prInfo Информация о Pull Request
     * @param sessionId ID сессии для отслеживания
     * @param repoPath Путь к репозиторию (не используется, оставлен для совместимости)
     * @return Структурированное ревью с комментариями
     */
    suspend fun reviewPR(
        prInfo: PRInfo,
        sessionId: String? = null,
        repoPath: String? = null
    ): PRReview {
        logger.info("🔍 Starting PR review for branch: ${prInfo.branch} -> ${prInfo.targetBranch}")

        return try {
            // 1. Собираем данные о PR
            val prData = dataCollector.collect(prInfo, sessionId)

            // 2. Получаем релевантный контекст из RAG (опционально)
            val ragContext = ragRetriever.retrieve(prData, sessionId)

            // 3. Генерируем ревью через Claude
            val review = generateReview(prInfo, prData, ragContext, sessionId)

            logger.info("✅ PR review completed: ${review.comments.size} comments generated")
            review

        } catch (e: Exception) {
            logger.error("❌ Failed to review PR: ${e.message}", e)
            throw PRReviewException("Failed to review PR: ${e.message}", e)
        }
    }



    /**
     * Генерирует ревью через LLM провайдер
     */
    private suspend fun generateReview(
        prInfo: PRInfo,
        prData: PRData,
        ragContext: String?,
        sessionId: String?
    ): PRReview {
        logger.info("🤖 Generating AI review...")

        // Формируем промпт
        val prompt = promptBuilder.buildPrompt(prInfo, prData, ragContext)

        val systemPrompt = """You are an expert code reviewer. Analyze the provided code changes and provide constructive feedback.
Focus on:
- Code quality and best practices
- Potential bugs and edge cases
- Performance considerations
- Security issues
- Maintainability and readability

Provide your review in a structured format."""

        val userMessage = Message(
            role = "user",
            content = prompt
        )

        // Отправляем запрос к LLM провайдеру
        val response = llmProvider.generate(
            systemPrompt = systemPrompt,
            messages = listOf(userMessage),
            model = "claude-sonnet-4-20250514",
            maxTokens = 8000,
            temperature = 0.3, // Более детерминированный вывод для code review
            sessionId = sessionId,
            showIntermediateMessages = false
        )

        if (response.error != null) {
            throw ClaudeAPIException("LLM API error: ${response.error}")
        }

        val reviewText = response.reply
            ?: throw ClaudeAPIException("Empty response from LLM")

        logger.info("📄 LLM response length: ${reviewText.length} chars")

        // Парсим ответ и формируем структурированное ревью
        return responseParser.parse(reviewText)
    }





    /**
     * Форматирует ревью в Markdown для публикации в PR
     *
     * @param review Структурированное ревью
     * @param prInfo Информация о PR
     * @return Отформатированный Markdown
     */
    fun formatReviewAsMarkdown(review: PRReview, prInfo: PRInfo): String {
        return markdownFormatter.format(review, prInfo)
    }

    /**
     * Публикует ревью в GitHub PR с inline-комментариями
     *
     * @param owner Владелец репозитория
     * @param repo Название репозитория
     * @param prNumber Номер Pull Request
     * @param review Объект ревью с комментариями
     * @return URL созданного ревью или null в случае ошибки
     */
    suspend fun postReviewToGitHub(
        owner: String,
        repo: String,
        prNumber: Int,
        review: PRReview
    ): String? {
        return githubPublisher.publish(owner, repo, prNumber, review)
    }
}

/**
 * Исключение при ошибке ревью PR
 */
class PRReviewException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Исключение при ошибке Claude API
 */
class ClaudeAPIException(message: String) : Exception(message)
