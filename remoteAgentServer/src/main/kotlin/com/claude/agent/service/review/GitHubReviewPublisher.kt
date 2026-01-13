package com.claude.agent.service.review

import com.claude.agent.common.models.PRReview
import com.claude.agent.service.GitHubService
import org.slf4j.LoggerFactory

/**
 * Публикатор ревью в GitHub через Pull Request Review API
 * 
 * Отвечает за преобразование и публикацию ревью с inline-комментариями в GitHub
 */
class GitHubReviewPublisher(
    private val githubService: GitHubService?,
    private val markdownFormatter: ReviewMarkdownFormatter
) {
    private val logger = LoggerFactory.getLogger(GitHubReviewPublisher::class.java)

    /**
     * Публикует ревью в GitHub PR с inline-комментариями
     *
     * @param owner Владелец репозитория
     * @param repo Название репозитория
     * @param prNumber Номер Pull Request
     * @param review Объект ревью с комментариями
     * @return URL созданного ревью или null в случае ошибки
     */
    suspend fun publish(
        owner: String,
        repo: String,
        prNumber: Int,
        review: PRReview
    ): String? {
        if (githubService == null) {
            logger.warn("GitHub service is not configured")
            return null
        }

        val githubComments = convertToGitHubComments(review)
        val summary = markdownFormatter.formatReviewSummary(review)

        logger.info("📤 Posting review with ${githubComments.size} inline comments")

        return githubService.postPRReview(
            owner = owner,
            repo = repo,
            prNumber = prNumber,
            summary = summary.ifBlank { "Code review completed" },
            comments = githubComments,
            event = "COMMENT"
        )
    }

    /**
     * Конвертирует ReviewComment в GitHubService.ReviewComment
     */
    private fun convertToGitHubComments(review: PRReview): List<GitHubService.ReviewComment> {
        return review.comments.mapNotNull { comment ->
            val line = comment.line
            if (line != null) {
                GitHubService.ReviewComment(
                    path = comment.file,
                    line = line,
                    body = markdownFormatter.formatInlineComment(comment)
                )
            } else {
                logger.warn("⚠️ Skipping comment without line number: ${comment.message}")
                null
            }
        }
    }
}

