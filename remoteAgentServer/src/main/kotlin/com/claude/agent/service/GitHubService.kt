package com.claude.agent.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с GitHub API
 */
class GitHubService(
    private val httpClient: HttpClient,
    private val githubToken: String?
) {
    private val logger = LoggerFactory.getLogger(GitHubService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
    }
    
    @Serializable
    data class CommentRequest(
        val body: String
    )
    
    @Serializable
    data class CommentResponse(
        val id: Long,
        val html_url: String,
        val created_at: String
    )

    @Serializable
    data class ReviewComment(
        val path: String,
        val line: Int,
        val body: String
    )

    @Serializable
    data class ReviewRequest(
        val body: String,
        val event: String = "COMMENT", // APPROVE, REQUEST_CHANGES, COMMENT
        val comments: List<ReviewComment> = emptyList()
    )

    @Serializable
    data class ReviewResponse(
        val id: Long,
        val html_url: String,
        val state: String
    )
    
    /**
     * Проверяет доступность GitHub API и валидность токена
     */
    suspend fun checkHealth(): Boolean {
        if (githubToken.isNullOrBlank()) {
            logger.warn("GitHub token is not configured")
            return false
        }
        
        return try {
            val response = httpClient.get("$GITHUB_API_BASE/user") {
                header("Authorization", "Bearer $githubToken")
                header("Accept", "application/vnd.github.v3+json")
            }
            
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            logger.error("GitHub API health check failed: ${e.message}")
            false
        }
    }
    
    /**
     * Публикует комментарий в Pull Request
     * 
     * @param owner Владелец репозитория (например, "octocat")
     * @param repo Название репозитория (например, "Hello-World")
     * @param prNumber Номер Pull Request
     * @param markdown Текст комментария в формате Markdown
     * @return URL созданного комментария или null в случае ошибки
     */
    suspend fun postPRComment(
        owner: String,
        repo: String,
        prNumber: Int,
        markdown: String
    ): String? {
        if (githubToken.isNullOrBlank()) {
            logger.error("Cannot post comment: GitHub token is not configured")
            return null
        }
        
        val url = "$GITHUB_API_BASE/repos/$owner/$repo/issues/$prNumber/comments"
        
        return try {
            logger.info("📤 Posting comment to GitHub PR: $owner/$repo#$prNumber")
            
            val response = httpClient.post(url) {
                header("Authorization", "Bearer $githubToken")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(CommentRequest(body = markdown))
            }
            
            if (response.status == HttpStatusCode.Created) {
                val commentResponse = json.decodeFromString<CommentResponse>(response.bodyAsText())
                logger.info("✅ Comment posted successfully: ${commentResponse.html_url}")
                commentResponse.html_url
            } else {
                logger.error("❌ Failed to post comment: ${response.status}")
                logger.error("Response: ${response.bodyAsText()}")
                null
            }
            
        } catch (e: Exception) {
            logger.error("❌ Failed to post comment to GitHub: ${e.message}", e)
            null
        }
    }
    
    /**
     * Обновляет существующий комментарий
     */
    suspend fun updatePRComment(
        owner: String,
        repo: String,
        commentId: Long,
        markdown: String
    ): Boolean {
        if (githubToken.isNullOrBlank()) {
            logger.error("Cannot update comment: GitHub token is not configured")
            return false
        }

        val url = "$GITHUB_API_BASE/repos/$owner/$repo/issues/comments/$commentId"

        return try {
            val response = httpClient.patch(url) {
                header("Authorization", "Bearer $githubToken")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(CommentRequest(body = markdown))
            }

            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            logger.error("Failed to update comment: ${e.message}", e)
            false
        }
    }

    /**
     * Публикует Pull Request Review с inline-комментариями к конкретным строкам кода
     *
     * @param owner Владелец репозитория
     * @param repo Название репозитория
     * @param prNumber Номер Pull Request
     * @param summary Общий комментарий к ревью
     * @param comments Список inline-комментариев к строкам кода
     * @param event Тип ревью: COMMENT, APPROVE, REQUEST_CHANGES
     * @return URL созданного ревью или null в случае ошибки
     */
    suspend fun postPRReview(
        owner: String,
        repo: String,
        prNumber: Int,
        summary: String,
        comments: List<ReviewComment>,
        event: String = "COMMENT"
    ): String? {
        if (githubToken.isNullOrBlank()) {
            logger.error("Cannot post review: GitHub token is not configured")
            return null
        }

        val url = "$GITHUB_API_BASE/repos/$owner/$repo/pulls/$prNumber/reviews"

        return try {
            logger.info("📤 Posting PR review to GitHub: $owner/$repo#$prNumber (${comments.size} inline comments)")

            val response = httpClient.post(url) {
                header("Authorization", "Bearer $githubToken")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(ReviewRequest(
                    body = summary,
                    event = event,
                    comments = comments
                ))
            }

            if (response.status == HttpStatusCode.OK) {
                val reviewResponse = json.decodeFromString<ReviewResponse>(response.bodyAsText())
                logger.info("✅ Review posted successfully: ${reviewResponse.html_url}")
                logger.info("   - ${comments.size} inline comments")
                reviewResponse.html_url
            } else {
                logger.error("❌ Failed to post review: ${response.status}")
                val errorBody = response.bodyAsText()
                logger.error("Response: $errorBody")
                null
            }

        } catch (e: Exception) {
            logger.error("❌ Failed to post review to GitHub: ${e.message}", e)
            null
        }
    }
}

