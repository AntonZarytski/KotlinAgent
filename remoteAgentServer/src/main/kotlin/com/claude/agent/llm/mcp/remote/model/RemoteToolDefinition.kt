package com.claude.agent.llm.mcp.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Конфигурация remote MCP сервера
 */
@Serializable
data class RemoteToolDefinition(
    val name: String,
    val url: String,
    val description: String,
    val inputSchema: JsonObject? = null,
    var enabled: Boolean = false
)