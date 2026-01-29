package com.claude.agent.routes

import com.claude.agent.models.ErrorResponse
import com.claude.agent.service.LocalAgentManager
import com.claude.agent.service.ProjectPathService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

@Serializable
data class FileTreeNode(
    val name: String,
    val path: String,
    val type: String,  // "file" or "directory"
    val children: List<FileTreeNode>? = null
)

@Serializable
data class FileTreeResponse(
    val tree: FileTreeNode? = null,
    val projectPath: String? = null,
    val error: String? = null
)

@Serializable
data class SetProjectPathRequest(
    val projectPath: String,
    val sessionId: String? = null
)

@Serializable
data class SetProjectPathResponse(
    val success: Boolean,
    val projectPath: String? = null,
    val error: String? = null
)

fun Route.fileTreeRoutes() {
    val logger = LoggerFactory.getLogger("FileTreeRoutes")

    /**
     * GET /api/file-tree - получить дерево файлов проекта через android_studio_mcp
     */
    get("/api/file-tree") {
        try {
            logger.info("📂 GET /api/file-tree called")

            // Получаем sessionId из query параметров (опционально)
            val sessionId = call.request.queryParameters["session_id"]

            // Получаем путь к проекту из ProjectPathService
            val projectPath = ProjectPathService.getProjectPath(sessionId)
            logger.info("📂 Project path: $projectPath")

            // Вызываем android_studio_mcp get_file_tree
            val arguments = buildJsonObject {
                put("action", "get_file_tree")
                put("max_depth", 20)  // Увеличена глубина для полного отображения структуры проекта
            }

            val result = LocalAgentManager.executeOnLocalAgent(
                toolName = "android_studio_mcp",
                arguments = arguments,
                timeoutMs = 30_000L  // 30 секунд для построения дерева
            )

            logger.info("📂 File tree result: ${result.take(200)}")

            // Парсим результат
            val resultJson = Json.parseToJsonElement(result).jsonObject
            val status = resultJson["status"]?.jsonPrimitive?.contentOrNull

            if (status == "success") {
                val treeJson = resultJson["tree"]?.jsonObject
                val tree = if (treeJson != null) {
                    parseFileTreeNode(treeJson)
                } else null

                val response = FileTreeResponse(
                    tree = tree,
                    projectPath = projectPath,
                    error = null
                )

                call.respond(HttpStatusCode.OK, response)
            } else {
                // Ошибка от локального агента
                val errorMsg = resultJson["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                logger.error("❌ Error from local agent: $errorMsg")

                val response = FileTreeResponse(
                    tree = null,
                    projectPath = projectPath,
                    error = errorMsg
                )

                call.respond(HttpStatusCode.OK, response)
            }

        } catch (e: Exception) {
            logger.error("❌ Error getting file tree: ${e.message}", e)

            val response = FileTreeResponse(
                tree = null,
                projectPath = null,
                error = "Server error: ${e.message}"
            )

            call.respond(HttpStatusCode.InternalServerError, response)
        }
    }

    /**
     * POST /api/set-project-path - установить путь к проекту через android_studio_mcp
     */
    post("/api/set-project-path") {
        try {
            logger.info("📂 POST /api/set-project-path called")

            val request = call.receive<SetProjectPathRequest>()
            logger.info("📂 Setting project path to: ${request.projectPath}")

            // Вызываем android_studio_mcp set_project_path
            val arguments = buildJsonObject {
                put("action", "set_project_path")
                put("project_path", request.projectPath)
            }

            val result = LocalAgentManager.executeOnLocalAgent(
                toolName = "android_studio_mcp",
                arguments = arguments,
                timeoutMs = 10_000L  // 10 секунд для установки пути
            )

            logger.info("📂 Set project path result: $result")

            // Парсим результат
            val resultJson = Json.parseToJsonElement(result).jsonObject
            val status = resultJson["status"]?.jsonPrimitive?.contentOrNull

            if (status == "success") {
                val projectPath = resultJson["project_path"]?.jsonPrimitive?.contentOrNull

                // Синхронизируем с ProjectPathService
                if (projectPath != null && request.sessionId != null) {
                    ProjectPathService.setProjectPath(request.sessionId, projectPath)
                }

                val response = SetProjectPathResponse(
                    success = true,
                    projectPath = projectPath,
                    error = null
                )

                call.respond(HttpStatusCode.OK, response)
            } else {
                // Ошибка от локального агента
                val errorMsg = resultJson["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                logger.error("❌ Error from local agent: $errorMsg")

                val response = SetProjectPathResponse(
                    success = false,
                    projectPath = null,
                    error = errorMsg
                )

                call.respond(HttpStatusCode.OK, response)
            }

        } catch (e: Exception) {
            logger.error("❌ Error setting project path: ${e.message}", e)

            val response = SetProjectPathResponse(
                success = false,
                projectPath = null,
                error = "Server error: ${e.message}"
            )

            call.respond(HttpStatusCode.InternalServerError, response)
        }
    }
}

/**
 * Рекурсивно парсит JsonObject в FileTreeNode
 */
private fun parseFileTreeNode(json: JsonObject): FileTreeNode {
    val name = json["name"]?.jsonPrimitive?.content ?: ""
    val path = json["path"]?.jsonPrimitive?.content ?: ""
    val type = json["type"]?.jsonPrimitive?.content ?: "file"
    val childrenArray = json["children"]?.jsonArray

    val children = if (childrenArray != null) {
        childrenArray.mapNotNull { element ->
            element.jsonObject?.let { parseFileTreeNode(it) }
        }
    } else null

    return FileTreeNode(
        name = name,
        path = path,
        type = type,
        children = children
    )
}
