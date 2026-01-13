package com.claude.agent.service

import com.claude.agent.common.models.*
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.llm.mcp.MCPTools
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для автоматического ревью Pull Request'ов с использованием AI
 * 
 * Анализирует изменения в PR через GitRepositoryMcp, использует RAG для поиска
 * релевантной документации и генерирует структурированное ревью через Claude API.
 */
class PRReviewService(
    private val claudeClient: ClaudeClient,
    private val mcpTools: MCPTools,
    private val ragService: RagService? = null,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient? = null,
    private val githubService: GitHubService? = null
) {
    private val logger = LoggerFactory.getLogger(PRReviewService::class.java)

    /**
     * Выполняет полный анализ PR и генерирует ревью
     */
    suspend fun reviewPR(
        prInfo: PRInfo,
        sessionId: String? = null,
        repoPath: String? = null
    ): PRReview {
        logger.info("🔍 Starting PR review for branch: ${prInfo.branch} -> ${prInfo.targetBranch}")

        try {
            // 1. Получаем информацию о PR через GitRepositoryMcp
            val prData = collectPRData(prInfo, sessionId)
            
            // 2. Получаем релевантный контекст из RAG
            val ragContext = if (ragService != null && ollamaEmbeddingClient != null) {
                retrieveRelevantContext(prData, sessionId)
            } else {
                null
            }

            // 3. Генерируем ревью через Claude
            val review = generateReview(prInfo, prData, ragContext, sessionId)

            logger.info("✅ PR review completed: ${review.comments.size} comments generated")
            return review

        } catch (e: Exception) {
            logger.error("❌ Failed to review PR: ${e.message}", e)
            throw e
        }
    }

    /**
     * Собирает данные о PR через GitRepositoryMcp
     */
    private suspend fun collectPRData(prInfo: PRInfo, sessionId: String?): PRData {
        logger.info("📊 Collecting PR data...")

        // Получаем список измененных файлов
        val compareResult = mcpTools.callLocalTool(
            toolName = "git_repository",
            arguments = buildJsonObject {
                put("action", "compare_branches")
                put("branch", prInfo.branch)
                put("target_branch", prInfo.targetBranch)
            },
            clientIp = null,
            userLocation = null,
            sessionId = sessionId
        )
        
        val compareData = Json.parseToJsonElement(compareResult).jsonObject
        val files = compareData["files"]?.jsonArray?.map { 
            val fileObj = it.jsonObject
            FileChange(
                status = fileObj["status"]?.jsonPrimitive?.content ?: "",
                path = fileObj["file"]?.jsonPrimitive?.content ?: ""
            )
        } ?: emptyList()

        logger.info("Found ${files.size} changed files")

        // Получаем коммиты в ветке
        val commitsResult = mcpTools.callLocalTool(
            toolName = "git_repository",
            arguments = buildJsonObject {
                put("action", "get_branch_commits")
                put("branch", prInfo.branch)
                put("limit", 50)
            },
            clientIp = null,
            userLocation = null,
            sessionId = sessionId
        )

        val commitsData = Json.parseToJsonElement(commitsResult).jsonObject
        val commits = commitsData["commits"]?.jsonArray?.map {
            val commitObj = it.jsonObject
            CommitInfo(
                hash = commitObj["hash"]?.jsonPrimitive?.content ?: "",
                author = commitObj["author"]?.jsonPrimitive?.content ?: "",
                message = commitObj["message"]?.jsonPrimitive?.content ?: "",
                date = commitObj["date"]?.jsonPrimitive?.content ?: ""
            )
        } ?: emptyList()

        logger.info("Found ${commits.size} commits")

        // Получаем diff для каждого измененного файла (ограничиваем до 20 файлов)
        val diffs = files.take(20).mapNotNull { file ->
            try {
                val diffResult = mcpTools.callLocalTool(
                    toolName = "git_repository",
                    arguments = buildJsonObject {
                        put("action", "get_diff")
                        put("file_path", file.path)
                    },
                    clientIp = null,
                    userLocation = null,
                    sessionId = sessionId
                )
                
                val diffData = Json.parseToJsonElement(diffResult).jsonObject
                val diff = diffData["diff"]?.jsonPrimitive?.content ?: ""
                
                FileDiff(
                    file = file.path,
                    status = file.status,
                    diff = diff
                )
            } catch (e: Exception) {
                logger.warn("Failed to get diff for ${file.path}: ${e.message}")
                null
            }
        }

        logger.info("Retrieved diffs for ${diffs.size} files")

        return PRData(
            files = files,
            commits = commits,
            diffs = diffs,
            stats = compareData["stats"]?.jsonPrimitive?.content ?: ""
        )
    }

    /**
     * Получает релевантный контекст из RAG системы
     */
    private suspend fun retrieveRelevantContext(prData: PRData, sessionId: String?): String? {
        if (ragService == null || ollamaEmbeddingClient == null) {
            return null
        }

        logger.info("🔍 Retrieving relevant context from RAG...")

        try {
            // Формируем запрос на основе измененных файлов и коммитов
            val query = buildString {
                appendLine("Code review context:")
                appendLine("Changed files: ${prData.files.take(10).joinToString(", ") { it.path }}")
                appendLine("Commit messages: ${prData.commits.take(5).joinToString(" | ") { it.message }}")
            }

            // Генерируем embedding
            val queryEmbedding = ollamaEmbeddingClient.embed(query)
            val normalizedEmbedding = com.claude.agent.common.database.normalizeToRange(queryEmbedding)

            // Ищем релевантные документы
            val results = ragService.search(
                queryEmbedding = normalizedEmbedding,
                topK = 5,
                minSimilarity = 0.7
            )

            if (results.isEmpty()) {
                logger.info("No relevant documentation found")
                return null
            }

            logger.info("Found ${results.size} relevant documentation chunks")
            return ragService.formatContext(results)

        } catch (e: Exception) {
            logger.error("Failed to retrieve RAG context: ${e.message}", e)
            return null
        }
    }

    /**
     * Генерирует ревью через Claude API
     */
    private suspend fun generateReview(
        prInfo: PRInfo,
        prData: PRData,
        ragContext: String?,
        sessionId: String?
    ): PRReview {
        logger.info("🤖 Generating AI review...")

        // Формируем промпт для Claude
        val prompt = buildReviewPrompt(prInfo, prData, ragContext)

        // Отправляем запрос к Claude
        val response = claudeClient.sendMessage(
            userMessage = prompt,
            sessionId = sessionId,
            model = "claude-sonnet-4-20250514",
            maxTokens = 8000,
            temperature = 0.3, // Более детерминированный вывод для code review
            showIntermediateMessages = false,
            useRag = false // RAG уже включен в промпт
        )

        if (response.error != null) {
            throw RuntimeException("Claude API error: ${response.error}")
        }

        val reviewText = response.reply ?: throw RuntimeException("Empty response from Claude")

        logger.info("📄 Claude response length: ${reviewText.length} chars")

        // Парсим ответ Claude и формируем структурированное ревью
        return parseReviewResponse(reviewText)
    }

    /**
     * Формирует промпт для генерации ревью
     */
    private fun buildReviewPrompt(prInfo: PRInfo, prData: PRData, ragContext: String?): String {
        return buildString {
            appendLine("# Pull Request Code Review")
            appendLine()
            appendLine("You are an expert code reviewer. Analyze the following Pull Request and provide a comprehensive review.")
            appendLine()
            
            if (prInfo.title != null) {
                appendLine("## PR Title")
                appendLine(prInfo.title)
                appendLine()
            }
            
            if (prInfo.description != null) {
                appendLine("## PR Description")
                appendLine(prInfo.description)
                appendLine()
            }

            appendLine("## Branch Information")
            appendLine("- Source branch: `${prInfo.branch}`")
            appendLine("- Target branch: `${prInfo.targetBranch}`")
            appendLine()

            appendLine("## Statistics")
            appendLine(prData.stats)
            appendLine()

            appendLine("## Commits (${prData.commits.size})")
            prData.commits.take(10).forEach { commit ->
                appendLine("- **${commit.hash.take(8)}**: ${commit.message} (${commit.author}, ${commit.date})")
            }
            appendLine()

            appendLine("## Changed Files (${prData.files.size})")
            prData.files.forEach { file ->
                appendLine("- **${file.status}**: `${file.path}`")
            }
            appendLine()

            if (prData.diffs.isNotEmpty()) {
                appendLine("## File Diffs")
                prData.diffs.forEach { fileDiff ->
                    appendLine()
                    appendLine("### ${fileDiff.file} (${fileDiff.status})")
                    appendLine("```diff")
                    // Ограничиваем размер diff для каждого файла
                    val truncatedDiff = if (fileDiff.diff.length > 3000) {
                        fileDiff.diff.take(3000) + "\n... (truncated)"
                    } else {
                        fileDiff.diff
                    }
                    appendLine(truncatedDiff)
                    appendLine("```")
                }
            }

            if (ragContext != null) {
                appendLine()
                appendLine("## Relevant Documentation")
                appendLine(ragContext)
            }

            appendLine()
            appendLine("## Review Instructions")
            appendLine()
            appendLine("Provide a structured code review with the following sections:")
            appendLine()
            appendLine("### 1. SUMMARY")
            appendLine("A brief overview of the changes (2-3 sentences)")
            appendLine()
            appendLine("### 2. OVERALL ASSESSMENT")
            appendLine("Rate: APPROVE / REQUEST_CHANGES / COMMENT")
            appendLine("Explanation of the rating")
            appendLine()
            appendLine("### 3. DETAILED COMMENTS")
            appendLine("List specific issues found in the code. For each comment use the format:")
            appendLine("- **[SEVERITY]** `file.kt:line` - Description of the issue")
            appendLine("  - Suggestion: How to fix it")
            appendLine()
            appendLine("Severity levels: CRITICAL, WARNING, INFO, SUGGESTION")
            appendLine()
            appendLine("### 4. SUGGESTIONS FOR IMPROVEMENT")
            appendLine("General recommendations for enhancing code quality")
            appendLine()
            appendLine("### 5. COMPLIANCE CHECKS")
            appendLine("Verify adherence to:")
            appendLine("- Code style and formatting standards")
            appendLine("- Documentation and comments")
            appendLine("- Test coverage")
            appendLine("- Security best practices")
            appendLine("- Performance considerations")
            appendLine()
            appendLine("Format: `[✓/✗] Category: Details`")
        }
    }

    /**
     * Парсит ответ Claude и формирует структурированное ревью
     */
    private fun parseReviewResponse(reviewText: String): PRReview {
        val lines = reviewText.lines()
        
        var summary = ""
        var overallAssessment = ""
        val comments = mutableListOf<ReviewComment>()
        val suggestions = mutableListOf<String>()
        val complianceChecks = mutableListOf<ComplianceCheck>()

        var currentSection = ""
        val summaryLines = mutableListOf<String>()
        val assessmentLines = mutableListOf<String>()
        
        for (line in lines) {
            when {
                // Поддерживаем оба формата: ## и ###
                line.startsWith("## 1. SUMMARY") || line.startsWith("### 1. SUMMARY") -> currentSection = "SUMMARY"
                line.startsWith("## 2. OVERALL ASSESSMENT") || line.startsWith("### 2. OVERALL ASSESSMENT") -> currentSection = "ASSESSMENT"
                line.startsWith("## 3. DETAILED COMMENTS") || line.startsWith("### 3. DETAILED COMMENTS") -> currentSection = "COMMENTS"
                line.startsWith("## 4. SUGGESTIONS") || line.startsWith("### 4. SUGGESTIONS") -> currentSection = "SUGGESTIONS"
                line.startsWith("## 5. COMPLIANCE") || line.startsWith("### 5. COMPLIANCE") -> currentSection = "COMPLIANCE"

                line.startsWith("###") || line.startsWith("##") -> {
                    // Новая секция - сбрасываем
                }

                else -> when (currentSection) {
                    "SUMMARY" -> if (line.isNotBlank()) summaryLines.add(line)
                    "ASSESSMENT" -> if (line.isNotBlank()) assessmentLines.add(line)
                    "COMMENTS" -> parseComment(line)?.let { comments.add(it) }
                    "SUGGESTIONS" -> if (line.startsWith("-") || line.startsWith("*")) {
                        suggestions.add(line.removePrefix("-").removePrefix("*").trim())
                    }
                    "COMPLIANCE" -> parseComplianceCheck(line)?.let { complianceChecks.add(it) }
                }
            }
        }

        summary = summaryLines.joinToString(" ").trim()
        overallAssessment = assessmentLines.joinToString(" ").trim()

        return PRReview(
            summary = summary.ifBlank { "No summary provided" },
            overallAssessment = overallAssessment.ifBlank { "No assessment provided" },
            comments = comments,
            suggestions = suggestions,
            complianceChecks = complianceChecks
        )
    }

    /**
     * Парсит строку комментария
     * Формат: - **[SEVERITY]** `file.kt:line` - Description
     */
    private fun parseComment(line: String): ReviewComment? {
        if (!line.trim().startsWith("-") && !line.trim().startsWith("*")) {
            return null
        }

        val trimmed = line.trim().removePrefix("-").removePrefix("*").trim()
        
        // Парсим severity
        val severityRegex = """^\*\*\[(CRITICAL|WARNING|INFO|SUGGESTION)]\*\*""".toRegex()
        val severityMatch = severityRegex.find(trimmed) ?: return null
        val severityStr = severityMatch.groupValues[1]
        val severity = try {
            Severity.valueOf(severityStr)
        } catch (e: Exception) {
            Severity.INFO
        }

        val afterSeverity = trimmed.substring(severityMatch.range.last + 1).trim()

        // Парсим file:line
        val fileRegex = """`([^`]+?):?(\d+)?`""".toRegex()
        val fileMatch = fileRegex.find(afterSeverity)
        
        val file = fileMatch?.groupValues?.get(1) ?: "unknown"
        val line = fileMatch?.groupValues?.get(2)?.toIntOrNull()
        
        // Остальное - это message
        val message = if (fileMatch != null) {
            afterSeverity.substring(fileMatch.range.last + 1).trim().removePrefix("-").trim()
        } else {
            afterSeverity
        }

        return ReviewComment(
            file = file,
            line = line,
            severity = severity,
            message = message,
            suggestion = null
        )
    }

    /**
     * Парсит проверку соответствия стандартам
     * Формат: [✓/✗] Category: Details
     */
    private fun parseComplianceCheck(line: String): ComplianceCheck? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("-")) {
            return null
        }

        val checkRegex = """[\\-\\*]?\\s*\\[([✓✗×X])]\\s*([^:]+):\\s*(.+)""".toRegex()
        val match = checkRegex.find(trimmed) ?: return null

        val status = if (match.groupValues[1] in listOf("✓", "X")) "PASS" else "FAIL"
        val category = match.groupValues[2].trim()
        val details = match.groupValues[3].trim()

        return ComplianceCheck(
            category = category,
            status = status,
            details = details
        )
    }

    /**
     * Форматирует ревью в Markdown для публикации в PR
     */
    fun formatReviewAsMarkdown(review: PRReview, prInfo: PRInfo): String {
        return buildString {
            appendLine("# 🤖 AI Code Review")
            appendLine()
            appendLine("**Branch:** `${prInfo.branch}` → `${prInfo.targetBranch}`")
            appendLine()
            
            appendLine("## 📝 Summary")
            appendLine(review.summary)
            appendLine()

            appendLine("## 🎯 Overall Assessment")
            appendLine(review.overallAssessment)
            appendLine()

            if (review.comments.isNotEmpty()) {
                appendLine("## 💬 Detailed Comments")
                appendLine()
                
                val groupedComments = review.comments.groupBy { it.severity }
                
                listOf(Severity.CRITICAL, Severity.WARNING, Severity.INFO, Severity.SUGGESTION).forEach { severity ->
                    val commentsForSeverity = groupedComments[severity] ?: emptyList()
                    if (commentsForSeverity.isNotEmpty()) {
                        val emoji = when (severity) {
                            Severity.CRITICAL -> "🔴"
                            Severity.WARNING -> "🟡"
                            Severity.INFO -> "🔵"
                            Severity.SUGGESTION -> "💡"
                        }
                        appendLine("### $emoji ${severity.name} (${commentsForSeverity.size})")
                        appendLine()
                        commentsForSeverity.forEach { comment ->
                            val location = if (comment.line != null) {
                                "`${comment.file}:${comment.line}`"
                            } else {
                                "`${comment.file}`"
                            }
                            appendLine("- **$location**")
                            appendLine("  ${comment.message}")
                            if (comment.suggestion != null) {
                                appendLine("  > 💡 Suggestion: ${comment.suggestion}")
                            }
                            appendLine()
                        }
                    }
                }
            }

            if (review.suggestions.isNotEmpty()) {
                appendLine("## 💡 Suggestions for Improvement")
                appendLine()
                review.suggestions.forEach { suggestion ->
                    appendLine("- $suggestion")
                }
                appendLine()
            }

            if (review.complianceChecks.isNotEmpty()) {
                appendLine("## ✅ Compliance Checks")
                appendLine()
                review.complianceChecks.forEach { check ->
                    val icon = if (check.status == "PASS") "✅" else "❌"
                    appendLine("- $icon **${check.category}**: ${check.details}")
                }
                appendLine()
            }

            appendLine("---")
            appendLine("*Generated by Claude AI Code Review*")
        }
    }

    // Data classes для внутреннего использования
    private data class PRData(
        val files: List<FileChange>,
        val commits: List<CommitInfo>,
        val diffs: List<FileDiff>,
        val stats: String
    )

    private data class FileChange(
        val status: String,
        val path: String
    )

    private data class CommitInfo(
        val hash: String,
        val author: String,
        val message: String,
        val date: String
    )

    private data class FileDiff(
        val file: String,
        val status: String,
        val diff: String
    )

    /**
     * Публикует ревью как комментарий в GitHub PR
     *
     * @param owner Владелец репозитория
     * @param repo Название репозитория
     * @param prNumber Номер Pull Request
     * @param markdown Текст ревью в формате Markdown
     * @return URL созданного комментария или null в случае ошибки
     */
    suspend fun postReviewToGitHub(
        owner: String,
        repo: String,
        prNumber: Int,
        markdown: String
    ): String? {
        if (githubService == null) {
            logger.warn("GitHub service is not configured")
            return null
        }

        return githubService.postPRComment(
            owner = owner,
            repo = repo,
            prNumber = prNumber,
            markdown = markdown
        )
    }
}
