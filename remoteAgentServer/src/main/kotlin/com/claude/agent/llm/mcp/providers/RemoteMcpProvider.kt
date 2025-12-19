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

    fun getAllServers(): List<RemoteToolDefinition> {
        return config.values.toList()
    }

    /**
     * Включает или выключает remote MCP сервер
     */
    fun setToolEnabled(toolName: String, enabled: Boolean) {
        mcps.find { it.tool.first == toolName }?.tool?.second?.enabled = enabled
    }

    /**
     * Возвращает параметры MCP серверов для Claude API
     * Только для включенных серверов
     */
    fun getMcpParams(): JsonArray {
        val servers = mcps.filter { it.tool.second.enabled }
            .map { it.params }

        if (servers.isNotEmpty()) {
            logger.info("Активные remote MCP серверы: ${servers.size} шт.")
        }

        return JsonArray(servers)
    }
}

