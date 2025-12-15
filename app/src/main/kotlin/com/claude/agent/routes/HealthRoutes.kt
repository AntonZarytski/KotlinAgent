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
fun Route.healthRoutes(claudeClient: ClaudeClient) {
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
     * GET /api/tools - получить список доступных MCP инструментов.
     */
    get("/api/tools") {
        try {
            val toolDefinitions = MCPTools.getAllTools()

            val toolsList = toolDefinitions.map { tool ->
                ToolInfo(
                    name = tool.name,
                    description = tool.description
                )
            }

            logger.info("Возвращено ${toolsList.size} инструментов")
            call.respond(HttpStatusCode.OK, ToolsResponse(toolsList))

        } catch (e: Exception) {
            logger.error("Ошибка получения списка инструментов: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }
}
