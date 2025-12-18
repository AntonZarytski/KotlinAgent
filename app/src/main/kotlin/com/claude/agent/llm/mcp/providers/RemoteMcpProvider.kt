package com.claude.agent.llm.mcp.providers

import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.remote.model.RemoteToolDefinition
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с удаленными MCP серверами.
 *
 * Поддерживает подключение к публичным MCP серверам через HTTP/SSE.
 */
class RemoteMcpProvider(private val mcps: List<Mcp.Remote>) {
    private val logger = LoggerFactory.getLogger(RemoteMcpProvider::class.java)

    private val config: Map<String, RemoteToolDefinition> = mcps.associate { mcp ->
        mcp.tool.first to mcp.tool.second
    }

    private val enabledServers = mutableSetOf<String>()

    fun getAllServers(): List<RemoteToolDefinition> {
        return config.values.toList()
    }

    fun getMcp(serverName: String): Mcp.Remote? {
        return mcps.find { it.tool.first == serverName }
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
     * Возвращает параметры MCP серверов для Claude API
     * Только для включенных серверов
     */
    fun getMcpParams(): JsonArray {
        val servers = enabledServers.mapNotNull { serverName ->
            getMcp(serverName)?.tool?.second?.let { Json.encodeToJsonElement(it) }
        }

        if (servers.isNotEmpty()) {
            logger.info("Активные remote MCP серверы: ${servers.size} шт.")
        }

        return JsonArray(servers)
    }
}

