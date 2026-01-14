package com.claude.agent.ui

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String,
    val timestamp: String? = null,
    val usage: TokenUsage? = null,
    val read: Boolean = false,
    val is_intermediate: Boolean = false  // Флаг для промежуточных сообщений
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
    val last_updated: String,
    val unread_count: Int = 0
)

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val type: String = "local"
)

@Serializable
data class Reminder(
    val id: String,
    val text: String,
    val due_at: String,
    val recurrenceType: String? = null,
    val recurrenceInterval: Int? = null
)

data class Settings(
    val outputFormat: String = "default",
    val maxTokens: Int = 1024,
    val temperature: Float = 1.0f,
    val specMode: Boolean = false,
    val sendHistory: Boolean = true,
    val showTokenCount: Boolean = true,
    val showAllIntermediateMessages: Boolean = false,  // Изменено на false - промежуточные сообщения не сохраняются в истории
    val enabledTools: Set<String> = emptySet(),
    val useRag: Boolean = false,                // Использовать RAG для контекста
    val ragTopK: Int = 3,                       // Количество релевантных чанков
    val ragMinSimilarity: Float = 0.3f,         // Минимальный порог схожести (0.0-1.0)
    val ragFilterEnabled: Boolean = true        // Включить фильтрацию по порогу
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
    val enabled_tools: List<String> = emptyList(),
    val user_location: UserLocation? = null,
    val show_intermediate_messages: Boolean = true,
    val use_rag: Boolean = false,               // Использовать RAG для контекста
    val rag_top_k: Int = 3,                     // Количество релевантных чанков
    val rag_min_similarity: Double = 0.3,       // Минимальный порог схожести (0.0-1.0)
    val rag_filter_enabled: Boolean = true      // Включить фильтрацию по порогу
)

@Serializable
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String = "browser_geolocation"
)

@Serializable
data class ChatResponse(
    val reply: String? = null,
    val error: String? = null,
    val usage: TokenUsage? = null,
    val compression_applied: Boolean = false,
    val compressed_history: List<Message>? = null,
    val intermediate_messages: List<Message> = emptyList()
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
data class RemindersResponse(
    val reminders: List<Reminder> = emptyList()
)

@Serializable
data class UnreadCountsResponse(
    val unread_counts: Map<String, Int> = emptyMap()
)

// Ticket models
@Serializable
data class TicketUpdateEntry(
    val timestamp: String,
    val updateType: String,
    val description: String
)

@Serializable
data class SupportTicket(
    val id: String,
    val sessionId: String,  // ID сессии чата (обязательное поле)
    val title: String,
    val description: String,
    val status: String,  // OPEN, IN_PROGRESS, WAITING_FOR_USER, RESOLVED, CLOSED
    val priority: String,  // LOW, MEDIUM, HIGH, CRITICAL
    val category: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val closedAt: String? = null,
    val assignedTo: String? = null,
    val tags: List<String> = emptyList(),
    val autoCreated: Boolean = false,
    val updateHistory: List<TicketUpdateEntry> = emptyList()
)

@Serializable
data class TicketsResponse(
    val tickets: List<SupportTicket> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val pageSize: Int = 20
)

@Serializable
data class CreateTicketRequest(
    val sessionId: String,  // ID сессии чата (обязательное поле)
    val title: String,
    val description: String,
    val priority: String = "MEDIUM",
    val category: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateTicketRequest(
    val status: String? = null,
    val priority: String? = null,
    val assignedTo: String? = null,
    val category: String? = null,
    val tags: List<String>? = null
)
