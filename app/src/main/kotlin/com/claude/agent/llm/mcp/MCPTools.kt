package com.claude.agent.llm.mcp

import com.claude.agent.models.UserLocation
import com.claude.agent.llm.mcp.local.model.LocalToolDefinition
import com.claude.agent.llm.mcp.providers.LocalMcpProvider
import com.claude.agent.llm.mcp.providers.RemoteMcpProvider
import com.claude.agent.llm.mcp.remote.model.RemoteToolDefinition
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP инструменты без использования MCP SDK.
 *
 * Аналог mcp_tools.py из Python-версии.
 * Предоставляет инструменты: get_weather_forecast, get_solar_activity.
 *
 * Поддерживает автоматическое определение местоположения пользователя по IP-адресу.
 */
class MCPTools(
    private val localMcpProvider: LocalMcpProvider,
    private val remoteMcpProvider: RemoteMcpProvider
) {
    private val logger = LoggerFactory.getLogger(MCPTools::class.java)

    /**
     * Возвращает определения инструментов в формате Anthropic API.
     * Обрабатывает как локальные инструменты, так и remote MCP серверы.
     */
    fun getLocalToolsDefinitions(enabledTools: List<String>): List<LocalToolDefinition> {
        logger.info("getToolsDefinitions for: $enabledTools")
        // Возвращаем только локальные инструменты
        return localMcpProvider.getToolsDefinitions(enabledTools)
    }

    fun enableServers(enabledTools: List<String>) {
        enabledTools.forEach { toolName ->
            remoteMcpProvider.setToolEnabled(toolName = toolName, enabled = true)
            localMcpProvider.setToolEnabled(toolName = toolName, enabled = true)
        }
    }


    fun getLocalTools(): List<LocalToolDefinition> {
        val local = localMcpProvider.getAllTools()
        logger.info("getLocalTools: $local")
        return local
    }

    /**
     * Возвращает список всех доступных remote MCP серверов
     */
    fun getRemoteTools(): List<RemoteToolDefinition> {
        val remote = remoteMcpProvider.getAllServers()
        logger.info("getRemoteTools: $remote")
        return remote
    }

    /**
     * Вызывает указанный инструмент с заданными аргументами.
     *
     * @param toolName Имя инструмента
     * @param arguments Аргументы инструмента
     * @param clientIp IP-адрес клиента для автоматического определения местоположения
     * @param userLocation Координаты пользователя из браузера (если доступны)
     * @param sessionId ID сессии чата для привязки напоминаний
     */
    suspend fun callLocalTool(
        toolName: String,
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null,
        sessionId: String? = null
    ): String {
        return localMcpProvider.getTool(toolName)?.executeTool(arguments, clientIp, userLocation, sessionId)
            ?: "Tool $toolName not found"
    }

    fun getRemoteMCP(): JsonArray {
        return remoteMcpProvider.getMcpParams()
    }
}
