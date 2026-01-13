package com.claude.agent.routes

import com.claude.agent.common.models.PRInfo
import com.claude.agent.common.models.Severity
import com.claude.agent.service.PRReviewService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PRReviewRoutes")

@Serializable
data class PRReviewRequest(
    val branch: String,
    val baseBranch: String = "main",
    val prTitle: String? = null,
    val prDescription: String? = null,
    val repoPath: String? = null,
    val enableRag: Boolean = true,
    val sessionId: String? = null
)

@Serializable
data class PRReviewResponse(
    val success: Boolean,
    val markdown: String? = null,
    val error: String? = null,
    val stats: ReviewStats? = null
)

@Serializable
data class ReviewStats(
    val filesAnalyzed: Int,
    val commentsCount: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val suggestionsCount: Int
)

fun Route.prReviewRoutes(reviewService: PRReviewService) {
    
    route("/api/pr-review") {
        
        /**
         * POST /api/pr-review
         * Запуск ревью для указанной ветки
         */
        post {
            try {
                val request = call.receive<PRReviewRequest>()
                
                logger.info("🔍 PR Review requested: ${request.branch} -> ${request.baseBranch}")
                
                // Валидация
                if (request.branch.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, PRReviewResponse(
                        success = false,
                        error = "Branch name is required"
                    ))
                    return@post
                }
                
                // Выполняем ревью
                val prInfo = PRInfo(
                    branch = request.branch,
                    targetBranch = request.baseBranch,
                    title = request.prTitle,
                    description = request.prDescription
                )
                
                val review = reviewService.reviewPR(
                    prInfo = prInfo,
                    sessionId = request.sessionId ?: "api-${System.currentTimeMillis()}",
                    repoPath = request.repoPath
                )
                
                // Форматируем результат
                val markdown = reviewService.formatReviewAsMarkdown(review, prInfo)
                
                // Собираем статистику
                val stats = ReviewStats(
                    filesAnalyzed = review.comments.map { it.file }.distinct().size,
                    commentsCount = review.comments.size,
                    criticalCount = review.comments.count { it.severity == Severity.CRITICAL },
                    warningCount = review.comments.count { it.severity == Severity.WARNING },
                    suggestionsCount = review.suggestions.size
                )
                
                logger.info("✅ PR Review completed: ${stats.commentsCount} comments, ${stats.filesAnalyzed} files")
                
                call.respond(HttpStatusCode.OK, PRReviewResponse(
                    success = true,
                    markdown = markdown,
                    stats = stats
                ))
                
            } catch (e: Exception) {
                logger.error("❌ PR Review failed: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, PRReviewResponse(
                    success = false,
                    error = "Review failed: ${e.message}"
                ))
            }
        }
        
        /**
         * POST /api/pr-review/webhook
         * GitHub webhook handler для автоматического ревью при создании PR
         */
        post("/webhook") {
            try {
                val payload = call.receive<JsonObject>()
                
                logger.info("📨 GitHub webhook received")
                logger.debug("Payload: $payload")
                
                // Парсим GitHub webhook payload
                val action = payload["action"]?.jsonPrimitive?.contentOrNull
                val pullRequest = payload["pull_request"]?.jsonObject
                
                if (action == null || pullRequest == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Invalid webhook payload"
                    ))
                    return@post
                }
                
                // Обрабатываем только opened, synchronize, reopened
                if (action !in listOf("opened", "synchronize", "reopened")) {
                    logger.info("ℹ️ Ignoring action: $action")
                    call.respond(HttpStatusCode.OK, mapOf(
                        "message" to "Action ignored: $action"
                    ))
                    return@post
                }
                
                val branch = pullRequest["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull
                val baseBranch = pullRequest["base"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull
                val title = pullRequest["title"]?.jsonPrimitive?.contentOrNull
                val body = pullRequest["body"]?.jsonPrimitive?.contentOrNull
                val prNumber = pullRequest["number"]?.jsonPrimitive?.intOrNull

                logger.debug("Extracted PR data: branch=$branch, baseBranch=$baseBranch, prNumber=$prNumber, title=$title")
                logger.debug("Pull request keys: ${pullRequest.keys}")

                if (branch == null || baseBranch == null) {
                    logger.error("❌ Missing branch information: branch=$branch, baseBranch=$baseBranch")
                    logger.debug("Pull request object: $pullRequest")
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Missing branch information",
                        "branch" to branch,
                        "baseBranch" to baseBranch
                    ))
                    return@post
                }
                
                logger.info("🔍 Starting review for PR #$prNumber: $branch -> $baseBranch")
                
                // Выполняем ревью асинхронно
                val prInfo = PRInfo(
                    branch = branch,
                    targetBranch = baseBranch,
                    title = title,
                    description = body
                )
                
                // Запускаем в фоне чтобы не блокировать webhook response
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val review = reviewService.reviewPR(
                            prInfo = prInfo,
                            sessionId = "webhook-pr-$prNumber",
                            repoPath = null
                        )

                        val markdown = reviewService.formatReviewAsMarkdown(review, prInfo)

                        // Отправляем комментарий в GitHub PR
                        val repoOwner = payload["repository"]?.jsonObject
                            ?.get("owner")?.jsonObject
                            ?.get("login")?.jsonPrimitive?.contentOrNull

                        val repoName = payload["repository"]?.jsonObject
                            ?.get("name")?.jsonPrimitive?.contentOrNull

                        if (repoOwner != null && repoName != null && prNumber != null) {
                            val commentUrl = reviewService.postReviewToGitHub(
                                owner = repoOwner,
                                repo = repoName,
                                prNumber = prNumber,
                                markdown = markdown
                            )

                            if (commentUrl != null) {
                                logger.info("✅ Review posted to GitHub: $commentUrl")
                            } else {
                                logger.warn("⚠️ Failed to post review to GitHub (check GITHUB_TOKEN)")
                                logger.debug("Review markdown:\n$markdown")
                            }
                        } else {
                            logger.warn("⚠️ Missing repository info, cannot post to GitHub")
                            logger.debug("Review markdown:\n$markdown")
                        }

                        logger.info("✅ Review completed for PR #$prNumber (${markdown.length} chars)")

                    } catch (e: Exception) {
                        logger.error("❌ Review failed for PR #$prNumber: ${e.message}", e)
                    }
                }
                
                call.respond(HttpStatusCode.Accepted, mapOf(
                    "message" to "Review started for PR #$prNumber",
                    "branch" to branch,
                    "baseBranch" to baseBranch
                ))
                
            } catch (e: Exception) {
                logger.error("❌ Webhook processing failed: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Webhook processing failed: ${e.message}"
                ))
            }
        }
        
        /**
         * GET /api/pr-review/health
         * Проверка доступности сервиса ревью
         */
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf(
                "status" to "healthy",
                "service" to "pr-review"
            ))
        }
    }
}
