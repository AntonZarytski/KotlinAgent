package com.claude.agent.cli

import com.claude.agent.common.models.PRInfo
import org.slf4j.LoggerFactory

/**
 * Исполнитель ревью PR
 * 
 * Отвечает за выполнение ревью и форматирование результата
 */
class ReviewExecutor {
    private val logger = LoggerFactory.getLogger(ReviewExecutor::class.java)

    /**
     * Выполняет ревью PR
     * 
     * @param options Опции ревью
     * @param services Контейнер с сервисами
     * @param sessionId ID сессии
     * @return Отформатированный Markdown с результатами ревью
     */
    suspend fun execute(
        options: ReviewOptions,
        services: ServiceContainer,
        sessionId: String
    ): String {
        logger.info("🔍 Analyzing PR...")

        // Создаем информацию о PR
        val prInfo = PRInfo(
            branch = options.branch,
            targetBranch = options.baseBranch,
            title = options.prTitle,
            description = options.prDescription
        )

        // Выполняем ревью
        val review = services.reviewService.reviewPR(
            prInfo = prInfo,
            sessionId = sessionId,
            repoPath = options.repoPath
        )

        // Форматируем результат
        return services.reviewService.formatReviewAsMarkdown(review, prInfo)
    }
}

