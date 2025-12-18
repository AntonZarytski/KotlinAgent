package com.claude.agent.llm.mcp

import com.claude.agent.llm.mcp.local.model.LocalToolDefinition
import com.claude.agent.llm.mcp.remote.model.RemoteToolDefinition
import com.claude.agent.models.UserLocation
import kotlinx.serialization.json.JsonObject

sealed interface Mcp {
    interface Local : Mcp {
        val tool: Pair<String, LocalToolDefinition>

        suspend fun executeTool(
            arguments: JsonObject,
            clientIp: String? = null,
            userLocation: UserLocation? = null,
            sessionId: String? = null
        ): String

        fun errorJson(msg: String) =
            """{"error":"$msg"}"""
    }
    interface Remote : Mcp {
        val tool: Pair<String, RemoteToolDefinition>
        val params: JsonObject
    }
}