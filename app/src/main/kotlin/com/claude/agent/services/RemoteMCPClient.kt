package com.claude.agent.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с удаленными MCP серверами.
 *
 * Поддерживает подключение к публичным MCP серверам через HTTP/SSE.
 */
class RemoteMCPClient() {
    private val logger = LoggerFactory.getLogger(RemoteMCPClient::class.java)

    // Список доступных remote MCP серверов
    private val availableServers = mapOf(
        "kiwi-com-flight-search" to RemoteMCPServerConfig(
            name = "kiwi-com-flight-search",
            url = "https://mcp.kiwi.com",
            description = "Поиск авиабилетов через Kiwi.com",
            enabled = false // По умолчанию выключен
        )
    )

    // Состояние включенных серверов
    private val enabledServers = mutableSetOf<String>()

    /**
     * Возвращает список всех доступных remote MCP серверов
     */
    fun getAllServers(): List<RemoteMCPServerConfig> {
        return availableServers.values.toList()
    }

    /**
     * Включает или выключает remote MCP сервер
     */
    fun setServerEnabled(serverName: String, enabled: Boolean) {
        if (enabled) {
            enabledServers.add(serverName)
            logger.info("Remote MCP сервер '$serverName' включен")
        } else {
            enabledServers.remove(serverName)
            logger.info("Remote MCP сервер '$serverName' выключен")
        }
    }

    /**
     * Проверяет, включен ли сервер
     */
    fun isServerEnabled(serverName: String): Boolean {
        return enabledServers.contains(serverName)
    }

    /**
     * Возвращает параметры MCP серверов для Claude API
     * Только для включенных серверов
     */
    fun getMcpParams(): JsonArray {
        val servers = enabledServers.mapNotNull { serverName ->
            availableServers[serverName]?.let { config ->
                JsonObject(mapOf(
                    "type" to JsonPrimitive("url"),
                    "url" to JsonPrimitive(config.url),
                    "name" to JsonPrimitive(config.name)
                ))
            }
        }

        if (servers.isNotEmpty()) {
            logger.info("Активные remote MCP серверы: ${servers.size} шт.")
        }

        return JsonArray(servers)
    }
}

/**
 * Конфигурация remote MCP сервера
 */
@Serializable
data class RemoteMCPServerConfig(
    val name: String,
    val url: String,
    val description: String,
    val enabled: Boolean = false
)