package com.claude.agent.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Shared models
@Serializable
sealed class AgentMessage {
    @Serializable
    @SerialName("register")
    data class Register(
        val agentId: String,
        val tool: LocalToolDefinition,
        val capabilities: List<String> = emptyList()
    ) : AgentMessage()
    
    @Serializable
    @SerialName("execute_request")
    data class ExecuteRequest(
        val requestId: String,
        val toolName: String,
        val arguments: JsonObject
    ) : AgentMessage()
    
    @Serializable
    @SerialName("execute_response")
    data class ExecuteResponse(
        val requestId: String,
        val result: String,
        val success: Boolean = true
    ) : AgentMessage()
    
    @Serializable
    @SerialName("ping")
    data class Ping(val timestamp: Long = System.currentTimeMillis()) : AgentMessage()
    
    @Serializable
    @SerialName("pong")
    data class Pong(val timestamp: Long = System.currentTimeMillis()) : AgentMessage()
}

@Serializable
data class LocalToolDefinition(
    val name: String,
    val ui_description: String,
    val description: String,
    val input_schema: JsonObject,
    var enabled: Boolean
)