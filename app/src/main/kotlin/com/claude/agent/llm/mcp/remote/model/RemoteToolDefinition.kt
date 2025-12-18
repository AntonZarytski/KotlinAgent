package com.claude.agent.llm.mcp.remote.model

import kotlinx.serialization.Serializable

/**
 * Конфигурация remote MCP сервера
 */
@Serializable
data class RemoteToolDefinition(
    val name: String,
    val url: String,
    val description: String,
    val enabled: Boolean = false
)