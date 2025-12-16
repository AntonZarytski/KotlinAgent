package com.claude.agent.routes

import com.claude.agent.models.ErrorResponse
import com.claude.agent.models.HealthResponse
import com.claude.agent.models.ToolInfo
import com.claude.agent.models.ToolsResponse
import com.claude.agent.services.ClaudeClient
import com.claude.agent.services.MCPTools
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import org.slf4j.LoggerFactory

/**
 * Роуты для health check и информации об инструментах.
 *
 * Аналог эндпоинтов /health и /api/tools из App.py.
 */
fun Route.healthRoutes(claudeClient: ClaudeClient, mcpTools: MCPTools) {
    val logger = LoggerFactory.getLogger("HealthRoutes")

    /**
     * GET /health - проверка состояния приложения.
     */
    get("/health") {
        try {
            val response = HealthResponse(
                status = "ok",
                timestamp = Instant.now().toString(),
                api_key_configured = claudeClient.isApiKeyConfigured()
            )
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Ошибка в /health: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }

    /**
     * GET /api/tools - получить список доступных MCP инструментов (локальных и удаленных).
     */
    get("/api/tools") {
        try {
            // Получаем локальные инструменты
            val localTools = mcpTools.getAllTools()
            val localToolsList = localTools.map { tool ->
                ToolInfo(
                    name = tool.name,
                    description = tool.description,
                    type = "local"
                )
            }

            // Получаем remote MCP серверы
            val remoteServers = mcpTools.getAllRemoteServers()
            val remoteToolsList = remoteServers.map { server ->
                ToolInfo(
                    name = server.name,
                    description = server.description,
                    type = "remote_mcp"
                )
            }

            val allTools = localToolsList + remoteToolsList

            logger.info("Возвращено ${allTools.size} инструментов (${localToolsList.size} локальных, ${remoteToolsList.size} remote MCP)")
            call.respond(HttpStatusCode.OK, ToolsResponse(allTools))

        } catch (e: Exception) {
            logger.error("Ошибка получения списка инструментов: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }
}
