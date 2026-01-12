package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.ProjectPathService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

class GitRepositoryMcp : Mcp.Local {
    private val logger = LoggerFactory.getLogger(GitRepositoryMcp::class.java)

    init {
        logger.info("GitRepositoryMcp initialized")
        logger.info("Will use ProjectPathService to determine repository path per session")
    }

    /**
     * Получить путь к репозиторию для текущей сессии.
     * Использует ProjectPathService для синхронизации с AndroidStudioLocalMcp.
     */
    private fun getRepoPath(sessionId: String?): String {
        return ProjectPathService.getProjectPath(sessionId)
    }
    
    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "git_repository",
        second = LocalToolDefinition(
            name = "git_repository",
            ui_description = "Работа с git-репозиторием: текущая ветка, статус, история коммитов, diff",
            description = """
                Инструмент для работы с git-репозиторием проекта.

                ВАЖНО: Этот инструмент автоматически синхронизируется с android_studio tool.
                Когда пользователь устанавливает путь к проекту через android_studio (set_project_path),
                git_repository автоматически начинает работать с Git репозиторием этого проекта.

                Поддерживает команды:
                - get_current_branch: получить текущую ветку
                - get_status: получить git status (измененные файлы)
                - get_recent_commits: получить последние N коммитов
                - get_diff: получить diff для файла или всего репозитория
                - get_branches: список всех веток
                - get_file_history: история изменений файла
                - list_files_in_branch: список файлов в указанной ветке (с фильтром по расширению)
                - show_file_from_branch: показать содержимое файла из указанной ветки
                - compare_branches: сравнить две ветки (список измененных файлов)
                - get_branch_commits: получить коммиты в ветке (не в main)
            """.trimIndent(),
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "action" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(listOf(
                                        JsonPrimitive("get_current_branch"),
                                        JsonPrimitive("get_status"),
                                        JsonPrimitive("get_recent_commits"),
                                        JsonPrimitive("get_diff"),
                                        JsonPrimitive("get_branches"),
                                        JsonPrimitive("get_file_history"),
                                        JsonPrimitive("list_files_in_branch"),
                                        JsonPrimitive("show_file_from_branch"),
                                        JsonPrimitive("compare_branches"),
                                        JsonPrimitive("get_branch_commits")
                                    )),
                                    "description" to JsonPrimitive("Действие для выполнения")
                                )
                            ),
                            "file_path" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Путь к файлу (для get_diff, get_file_history, show_file_from_branch)")
                                )
                            ),
                            "branch" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Имя ветки (для list_files_in_branch, show_file_from_branch, compare_branches, get_branch_commits)")
                                )
                            ),
                            "target_branch" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Целевая ветка для сравнения (для compare_branches)")
                                )
                            ),
                            "file_extension" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Фильтр по расширению файла, например '.kt' (для list_files_in_branch)")
                                )
                            ),
                            "limit" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("integer"),
                                    "description" to JsonPrimitive("Количество коммитов (для get_recent_commits, get_branch_commits)")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(listOf(JsonPrimitive("action")))
                )
            )
        )
    )
    
    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        val action = arguments["action"]?.jsonPrimitive?.content
            ?: return errorJson("action required")

        val repoPath = getRepoPath(sessionId)
        logger.info("git_repository tool called: action=$action, repoPath=$repoPath, sessionId=$sessionId")

        // Проверяем, что это git-репозиторий
        val gitDir = File(repoPath, ".git")
        if (!gitDir.exists()) {
            logger.warn("⚠️ Git repository not found at: $repoPath")
            return errorJson("Git repository not found at: $repoPath. Make sure .git directory exists.")
        }

        return try {
            when (action) {
                "get_current_branch" -> getCurrentBranch(repoPath)
                "get_status" -> getStatus(repoPath)
                "get_recent_commits" -> {
                    val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 10
                    getRecentCommits(repoPath, limit)
                }
                "get_diff" -> {
                    val filePath = arguments["file_path"]?.jsonPrimitive?.content
                    getDiff(repoPath, filePath)
                }
                "get_branches" -> getBranches(repoPath)
                "get_file_history" -> {
                    val filePath = arguments["file_path"]?.jsonPrimitive?.content
                        ?: return errorJson("file_path required for get_file_history")
                    getFileHistory(repoPath, filePath)
                }
                "list_files_in_branch" -> {
                    val branch = arguments["branch"]?.jsonPrimitive?.content
                        ?: return errorJson("branch required for list_files_in_branch")
                    val extension = arguments["file_extension"]?.jsonPrimitive?.content
                    listFilesInBranch(repoPath, branch, extension)
                }
                "show_file_from_branch" -> {
                    val branch = arguments["branch"]?.jsonPrimitive?.content
                        ?: return errorJson("branch required for show_file_from_branch")
                    val filePath = arguments["file_path"]?.jsonPrimitive?.content
                        ?: return errorJson("file_path required for show_file_from_branch")
                    showFileFromBranch(repoPath, branch, filePath)
                }
                "compare_branches" -> {
                    val branch = arguments["branch"]?.jsonPrimitive?.content
                        ?: return errorJson("branch required for compare_branches")
                    val targetBranch = arguments["target_branch"]?.jsonPrimitive?.content ?: "main"
                    compareBranches(repoPath, branch, targetBranch)
                }
                "get_branch_commits" -> {
                    val branch = arguments["branch"]?.jsonPrimitive?.content
                        ?: return errorJson("branch required for get_branch_commits")
                    val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 10
                    getBranchCommits(repoPath, branch, limit)
                }
                else -> errorJson("Unknown action: $action")
            }
        } catch (e: Exception) {
            logger.error("Git command failed: ${e.message}", e)
            errorJson("Git command failed: ${e.message}")
        }
    }
    
    private fun executeGitCommand(repoPath: String, vararg command: String): String {
        val process = ProcessBuilder(*command)
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            output
        } else {
            throw RuntimeException("Git command failed with exit code $exitCode: $output")
        }
    }

    private fun getCurrentBranch(repoPath: String): String {
        val branch = executeGitCommand(repoPath, "git", "rev-parse", "--abbrev-ref", "HEAD").trim()
        return """{"current_branch": "$branch"}"""
    }

    private fun getStatus(repoPath: String): String {
        val status = executeGitCommand(repoPath, "git", "status", "--short")
        val files = status.lines().filter { it.isNotBlank() }

        return buildString {
            append("""{"status": "success", "modified_files": [""")
            files.forEachIndexed { index, line ->
                val parts = line.trim().split(Regex("\\s+"), 2)
                if (parts.size == 2) {
                    if (index > 0) append(", ")
                    append("""{"status": "${parts[0]}", "file": "${parts[1]}"}""")
                }
            }
            append("]}")
        }
    }

    private fun getRecentCommits(repoPath: String, limit: Int): String {
        val log = executeGitCommand(
            repoPath,
            "git", "log",
            "--pretty=format:%H|%an|%ae|%ad|%s",
            "--date=iso",
            "-n", limit.toString()
        )

        val commits = log.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|", limit = 5)
            if (parts.size == 5) {
                """{"hash": "${parts[0]}", "author": "${parts[1]}", "email": "${parts[2]}", "date": "${parts[3]}", "message": "${
                    parts[4].replace(
                        "\"",
                        "\\\""
                    )
                }"}"""
            } else {
                null
            }
        }

        return """{"commits": [${commits.joinToString(", ")}]}"""
    }

    private fun getDiff(repoPath: String, filePath: String?): String {
        val diff = if (filePath != null) {
            executeGitCommand(repoPath, "git", "diff", "HEAD", "--", filePath)
        } else {
            executeGitCommand(repoPath, "git", "diff", "HEAD")
        }

        return """{"diff": ${JsonPrimitive(diff)}}"""
    }

    private fun getBranches(repoPath: String): String {
        val branches = executeGitCommand(repoPath, "git", "branch", "-a")
            .lines()
            .filter { it.isNotBlank() }
            .map { it.trim().removePrefix("* ").trim() }

        return """{"branches": [${branches.joinToString(", ") { "\"$it\"" }}]}"""
    }

    private fun getFileHistory(repoPath: String, filePath: String): String {
        val log = executeGitCommand(
            repoPath,
            "git", "log",
            "--pretty=format:%H|%an|%ad|%s",
            "--date=short",
            "-n", "20",
            "--", filePath
        )

        val commits = log.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|", limit = 4)
            if (parts.size == 4) {
                """{"hash": "${parts[0]}", "author": "${parts[1]}", "date": "${parts[2]}", "message": "${
                    parts[3].replace(
                        "\"",
                        "\\\""
                    )
                }"}"""
            } else {
                null
            }
        }

        return """{"file": "$filePath", "history": [${commits.joinToString(", ")}]}"""
    }

    /**
     * Список файлов в указанной ветке
     */
    private fun listFilesInBranch(repoPath: String, branch: String, extension: String?): String {
        val files = executeGitCommand(repoPath, "git", "ls-tree", "-r", "--name-only", branch)
            .lines()
            .filter { it.isNotBlank() }
            .let { list ->
                if (extension != null) {
                    list.filter { it.endsWith(extension) }
                } else {
                    list
                }
            }

        return buildString {
            append("""{"branch": "$branch", """)
            if (extension != null) {
                append(""""extension": "$extension", """)
            }
            append(""""files": [${files.joinToString(", ") { "\"$it\"" }}], """)
            append(""""count": ${files.size}}""")
        }
    }

    /**
     * Показать содержимое файла из указанной ветки
     */
    private fun showFileFromBranch(repoPath: String, branch: String, filePath: String): String {
        val content = executeGitCommand(repoPath, "git", "show", "$branch:$filePath")

        return buildString {
            append("""{"branch": "$branch", """)
            append(""""file_path": "$filePath", """)
            append(""""content": ${JsonPrimitive(content)}}""")
        }
    }

    /**
     * Сравнить две ветки (список измененных файлов)
     */
    private fun compareBranches(repoPath: String, branch: String, targetBranch: String): String {
        // Получаем список измененных файлов
        val diffFiles = executeGitCommand(repoPath, "git", "diff", "--name-status", "$targetBranch...$branch")
            .lines()
            .filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), 2)
                if (parts.size == 2) {
                    """{"status": "${parts[0]}", "file": "${parts[1]}"}"""
                } else {
                    null
                }
            }

        // Получаем статистику
        val stats = executeGitCommand(repoPath, "git", "diff", "--shortstat", "$targetBranch...$branch")
            .trim()

        return buildString {
            append("""{"branch": "$branch", """)
            append(""""target_branch": "$targetBranch", """)
            append(""""files": [${diffFiles.joinToString(", ")}], """)
            append(""""stats": "$stats"}""")
        }
    }

    /**
     * Получить коммиты в ветке (которых нет в main)
     */
    private fun getBranchCommits(repoPath: String, branch: String, limit: Int): String {
        val log = executeGitCommand(
            repoPath,
            "git", "log",
            "--pretty=format:%H|%an|%ae|%ad|%s",
            "--date=iso",
            "main..$branch",
            "-n", limit.toString()
        )

        val commits = log.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|", limit = 5)
            if (parts.size == 5) {
                """{"hash": "${parts[0]}", "author": "${parts[1]}", "email": "${parts[2]}", "date": "${parts[3]}", "message": "${
                    parts[4].replace(
                        "\"",
                        "\\\""
                    )
                }"}"""
            } else {
                null
            }
        }

        return buildString {
            append("""{"branch": "$branch", """)
            append(""""base_branch": "main", """)
            append(""""commits": [${commits.joinToString(", ")}], """)
            append(""""count": ${commits.size}}""")
        }
    }

    override fun errorJson(msg: String): String {
        return """{"status": "error", "message": "${msg.replace("\"", "\\\"")}"}"""
    }
}