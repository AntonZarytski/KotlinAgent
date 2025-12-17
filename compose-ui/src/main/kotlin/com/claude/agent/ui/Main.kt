package com.claude.agent.ui

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Data Models
@Serializable
data class Message(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: TokenUsage? = null
)

@Serializable
data class TokenUsage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val last_updated: Long,
    val messages: List<Message> = emptyList()
)

@Serializable
data class ChatRequest(
    val message: String,
    val session_id: String,
    val output_format: String = "default",
    val max_tokens: Int = 1024,
    val spec_mode: Boolean = false,
    val temperature: Float = 1.0f,
    val conversation_history: List<Message> = emptyList(),
    val enabled_tools: List<String> = emptyList()
)

@Serializable
data class ChatResponse(
    val reply: String? = null,
    val error: String? = null,
    val usage: TokenUsage? = null,
    val compression_applied: Boolean = false,
    val compressed_history: List<Message>? = null
)

@Serializable
data class TokenCountRequest(
    val message: String,
    val output_format: String = "default",
    val spec_mode: Boolean = false,
    val conversation_history: List<Message> = emptyList()
)

@Serializable
data class TokenCountResponse(
    val input_tokens: Int = 0,
    val error: String? = null
)

@Serializable
data class SessionsResponse(
    val sessions: List<ChatSession> = emptyList()
)

@Serializable
data class SessionResponse(
    val history: List<Message> = emptyList()
)

@Serializable
data class ToolsResponse(
    val tools: List<Tool> = emptyList()
)

@Serializable
data class Tool(
    val name: String,
    val description: String
)

data class Settings(
    val outputFormat: String = "default",
    val maxTokens: Int = 1024,
    val temperature: Float = 1.0f,
    val specMode: Boolean = false,
    val sendHistory: Boolean = true,
    val showTokenCount: Boolean = true,
    val enabledTools: Set<String> = emptySet()
)

// API Client
class ClaudeApiClient(private val baseUrl: String = "https://localhost:8001") {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun sendMessage(request: ChatRequest): ChatResponse {
        return client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun countTokens(request: TokenCountRequest): TokenCountResponse {
        return client.post("$baseUrl/api/count_tokens") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getSessions(): SessionsResponse {
        return client.get("$baseUrl/api/sessions").body()
    }

    suspend fun getSession(sessionId: String): SessionResponse {
        return client.get("$baseUrl/api/sessions/$sessionId").body()
    }

    suspend fun createSession(sessionId: String, title: String) {
        client.post("$baseUrl/api/sessions") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("session_id" to sessionId, "title" to title))
        }
    }

    suspend fun deleteSession(sessionId: String) {
        client.delete("$baseUrl/api/sessions/$sessionId")
    }

    suspend fun getTools(): ToolsResponse {
        return client.get("$baseUrl/api/tools").body()
    }
}