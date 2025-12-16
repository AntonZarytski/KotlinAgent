package com.claude.agent.services

import com.claude.agent.models.UserLocation
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
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
    private val localMCP: LocalMCPClient,
    private val remoteMCP: RemoteMCPClient
) {
    private val logger = LoggerFactory.getLogger(MCPTools::class.java)

    /**
     * Возвращает определения инструментов в формате Anthropic API.
     * Обрабатывает как локальные инструменты, так и remote MCP серверы.
     */
    fun getToolsDefinitions(enabledTools: List<String>): List<ToolDefinition> {
        logger.info("getToolsDefinitions for: $enabledTools")

        // Разделяем локальные инструменты и remote MCP серверы
        val localToolNames = mutableListOf<String>()
        val remoteServerNames = mutableListOf<String>()

        val allRemoteServers = remoteMCP.getAllServers().map { it.name }

        enabledTools.forEach { toolName ->
            if (toolName in allRemoteServers) {
                remoteServerNames.add(toolName)
            } else {
                localToolNames.add(toolName)
            }
        }

        // Включаем remote MCP серверы
        remoteServerNames.forEach { serverName ->
            remoteMCP.setServerEnabled(serverName, true)
        }

        // Возвращаем только локальные инструменты
        return localMCP.getToolsDefinitions(localToolNames)
    }

    fun getAllTools(): List<ToolDefinition> {
        val local = localMCP.getAllTools()
        logger.info("getAllTools: local: $local")
        return local
    }

    /**
     * Возвращает список всех доступных remote MCP серверов
     */
    fun getAllRemoteServers(): List<RemoteMCPServerConfig> {
        return remoteMCP.getAllServers()
    }

    /**
     * Вызывает указанный инструмент с заданными аргументами.
     *
     * @param toolName Имя инструмента
     * @param arguments Аргументы инструмента
     * @param clientIp IP-адрес клиента для автоматического определения местоположения
     * @param userLocation Координаты пользователя из браузера (если доступны)
     */
    suspend fun callTool(
        toolName: String,
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null
    ): String {
        return when (toolName) {
            "get_weather_forecast" -> {
                localMCP.getWeatherForecast(
                    arguments = arguments,
                    clientIp = clientIp,
                    userLocation = userLocation
                )
            }

            "get_solar_activity" -> {
                localMCP.getSolarActivity(
                    arguments = arguments,
                    clientIp = clientIp,
                    userLocation = userLocation
                )
            }

            else -> {
                "call wrong tool"
            }
        }
    }

    fun getRemoteMCP(): JsonArray {
        return remoteMCP.getMcpParams()
    }
}

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val input_schema: JsonObject
)
