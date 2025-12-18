package com.claude.agent.llm.mcp.local.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LocalToolDefinition(
    val name: String,
    val description: String,
    val input_schema: JsonObject
)