package com.claude.agent.service.review

import com.claude.agent.common.models.PRInfo
import com.claude.agent.llm.mcp.MCPTools
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сборщик данных о Pull Request через GitRepositoryMcp
 * 
 * Отвечает за получение информации о файлах, коммитах и diff'ах из Git репозитория
 */
class PRDataCollector(
    private val mcpTools: MCPTools
) {
    private val logger = LoggerFactory.getLogger(PRDataCollector::class.java)
    
    companion object {
        private const val MAX_FILES_FOR_DIFF = 20
        private const val MAX_COMMITS_TO_FETCH = 50
        private const val MAX_COMMITS_IN_PROMPT = 10
    }

    /**
     * Собирает данные о PR через GitRepositoryMcp
     */
    suspend fun collect(prInfo: PRInfo, sessionId: String?): PRData {
        logger.info("📊 Collecting PR data...")

        val files = collectChangedFiles(prInfo, sessionId)
        val commits = collectCommits(prInfo, sessionId)
        val diffs = collectDiffs(files, sessionId)
        val stats = collectStats(prInfo, sessionId)

        logger.info("✅ PR data collected: ${files.size} files, ${commits.size} commits, ${diffs.size} diffs")
        
        return PRData(
            files = files,
            commits = commits,
            diffs = diffs,
            stats = stats
        )
    }

    /**
     * Получает список измененных файлов
     */
    private suspend fun collectChangedFiles(prInfo: PRInfo, sessionId: String?): List<FileChange> {
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
        return files
    }

    /**
     * Получает коммиты в ветке
     */
    private suspend fun collectCommits(prInfo: PRInfo, sessionId: String?): List<CommitInfo> {
        val commitsResult = mcpTools.callLocalTool(
            toolName = "git_repository",
            arguments = buildJsonObject {
                put("action", "get_branch_commits")
                put("branch", prInfo.branch)
                put("limit", MAX_COMMITS_TO_FETCH)
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
        return commits
    }

    /**
     * Получает diff для измененных файлов (ограничено MAX_FILES_FOR_DIFF)
     */
    private suspend fun collectDiffs(files: List<FileChange>, sessionId: String?): List<FileDiff> {
        val diffs = files.take(MAX_FILES_FOR_DIFF).mapNotNull { file ->
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
        return diffs
    }

    /**
     * Получает статистику изменений
     */
    private suspend fun collectStats(prInfo: PRInfo, sessionId: String?): String {
        return try {
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
            compareData["stats"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            logger.warn("Failed to get stats: ${e.message}")
            ""
        }
    }
}

