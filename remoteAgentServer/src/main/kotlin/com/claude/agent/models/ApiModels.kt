package com.claude.agent.models

import kotlinx.serialization.Serializable

/**
 * Data classes для API запросов и ответов.
 *
 * Аналог структур из Python App.py, но с типобезопасностью Kotlin.
 */

// === Сообщение в диалоге ===
@Serializable
data class Message(
    val role: String,           // "user" или "assistant"
    val content: String,
    val usage: TokenUsage? = null,
    val timestamp: String? = null,  // ISO 8601 timestamp
    val read: Boolean = false       // Прочитано ли сообщение
)

@Serializable
data class TokenUsage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null
)

// === User Location from browser geolocation ===
@Serializable
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String = "browser_geolocation"
)

// === POST /api/chat ===
@Serializable
data class ChatRequest(
    val message: String,
    val session_id: String? = null,
    val output_format: String = "default",
    val max_tokens: Int = 1024,
    val temperature: Double = 1.0,
    val spec_mode: Boolean = false,
    val conversation_history: List<Message> = emptyList(),
    val enabled_tools: List<String> = emptyList(),
    val user_location: UserLocation? = null,
    val show_intermediate_messages: Boolean = true,
    val use_rag: Boolean = false,           // Использовать RAG для контекста
    val rag_top_k: Int = 3,                 // Количество релевантных чанков
    val rag_min_similarity: Double = 0.3,   // Минимальный порог схожести (0.0-1.0)
    val rag_filter_enabled: Boolean = true  // Включить фильтрацию по порогу
)

@Serializable
data class ChatResponse(
    val reply: String,
    val usage: TokenUsage? = null,
    val compressed_history: List<Message>? = null,
    val compression_applied: Boolean = false,
    val intermediate_messages: List<Message> = emptyList()
)

@Serializable
data class ErrorResponse(
    val error: String
)

// === POST /api/count_tokens ===
@Serializable
data class CountTokensRequest(
    val message: String,
    val output_format: String = "default",
    val spec_mode: Boolean = false,
    val conversation_history: List<Message> = emptyList()
)

@Serializable
data class CountTokensResponse(
    val input_tokens: Int
)

// === GET /health ===
@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val api_key_configured: Boolean
)

// === GET /api/tools ===
@Serializable
data class ToolInfo(
    val name: String,
    val description: String,
    val type: String = "local" // "local" или "remote_mcp"
)

@Serializable
data class ToolsResponse(
    val tools: List<ToolInfo>
)

// === Sessions API ===
@Serializable
data class SessionInfo(
    val id: String,
    val title: String,
    val created_at: String,
    val last_updated: String
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionInfo>
)

@Serializable
data class SessionHistoryResponse(
    val session_id: String,
    val history: List<Message>,
    val stats: SessionStats
)

@Serializable
data class SessionStats(
    val total_messages: Int,
    val user_messages: Int,
    val assistant_messages: Int
)

@Serializable
data class CreateSessionRequest(
    val session_id: String,
    val title: String = "Новый диалог"
)

@Serializable
data class CreateSessionResponse(
    val session_id: String,
    val title: String
)

@Serializable
data class DeleteSessionResponse(
    val message: String
)

@Serializable
data class Reminder(
    val id: String,
    val sessionId: String? = null,
    val text: String,
    val due_at: String,
    val created_at: String,
    val updated_at: String,
    val done: Boolean,
    val notified: Boolean = false,
    val recurrenceType: String = "none", // none, minutely, hourly, daily, weekly, monthly
    val recurrenceInterval: Int = 1, // every N minutes/hours/days/weeks/months
    val recurrenceEndDate: String? = null, // when to stop recurring (ISO 8601)
    val taskType: String = "reminder", // reminder, ai_response, mcp_tool
    val taskContext: String? = null // JSON with task details
)
