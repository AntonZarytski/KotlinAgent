package com.claude.agent.models

import kotlinx.serialization.Serializable

/**
 * Модели данных для сервиса поддержки пользователей
 */

/**
 * Пользователь в системе поддержки
 */
@Serializable
data class SupportUser(
    val id: String,
    val name: String,
    val email: String,
    val status: UserStatus = UserStatus.ACTIVE,
    val registeredAt: String,
    val lastActivity: String? = null
)

@Serializable
enum class UserStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}

/**
 * Запись в истории обновлений тикета
 */
@Serializable
data class TicketUpdate(
    val timestamp: String,
    val updateType: String,  // "info_added", "status_changed", "priority_changed", etc.
    val description: String
)

/**
 * Тикет в системе поддержки
 * Привязан к сессии чата (session_id обязателен)
 */
@Serializable
data class SupportTicket(
    val id: String,
    val sessionId: String,  // ID сессии чата (обязательное поле)
    val title: String,
    val description: String,
    val status: TicketStatus = TicketStatus.OPEN,
    val priority: TicketPriority = TicketPriority.MEDIUM,
    val category: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val closedAt: String? = null,
    val assignedTo: String? = null,
    val tags: List<String> = emptyList(),
    val autoCreated: Boolean = false,  // Создан автоматически AI
    val updateHistory: List<TicketUpdate> = emptyList()  // История обновлений
)

/**
 * Результат обновления тикета с информацией о старом статусе
 */
data class TicketUpdateResult(
    val ticket: SupportTicket?,  // null если тикет был удален (CLOSED)
    val oldStatus: TicketStatus,
    val newStatus: TicketStatus,
    val wasDeleted: Boolean = false
)

@Serializable
enum class TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_USER,
    RESOLVED,
    CLOSED
}

@Serializable
enum class TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Запрос к сервису поддержки
 */
@Serializable
data class SupportRequest(
    val question: String,
    val ticketId: String? = null,
    val sessionId: String? = null,
    val useRag: Boolean = true,
    val ragTopK: Int = 5,
    val ragMinSimilarity: Double = 0.7
)

/**
 * Ответ от сервиса поддержки
 */
@Serializable
data class SupportResponse(
    val answer: String,
    val sources: List<DocumentSource> = emptyList(),
    val ticketContext: SupportTicket? = null,
    val confidence: Double? = null,
    val suggestedActions: List<String> = emptyList()
)

/**
 * Источник документации, использованный в ответе
 */
@Serializable
data class DocumentSource(
    val docId: String,
    val chunkIndex: Int,
    val text: String,
    val similarity: Double
)

/**
 * Запрос на обновление тикета через REST API
 * ticketId передается в URL, а не в теле запроса
 */
@Serializable
data class UpdateTicketRequest(
    val status: TicketStatus? = null,
    val priority: TicketPriority? = null,
    val assignedTo: String? = null,
    val category: String? = null,
    val tags: List<String>? = null
)

/**
 * Запрос на обновление тикета через MCP support_crm
 * ticketId передается в теле запроса
 */
@Serializable
data class McpUpdateTicketRequest(
    val ticketId: String,
    val status: TicketStatus? = null,
    val priority: TicketPriority? = null,
    val assignedTo: String? = null,
    val category: String? = null,
    val tags: List<String>? = null
)

/**
 * Запрос на создание тикета
 */
@Serializable
data class CreateTicketRequest(
    val sessionId: String,  // ID сессии чата (обязательное поле)
    val title: String,
    val description: String,
    val priority: TicketPriority = TicketPriority.MEDIUM,
    val category: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Анализ проблемы для автоматического создания тикета
 */
@Serializable
data class ProblemAnalysis(
    val needsTicket: Boolean,
    val title: String? = null,
    val description: String? = null,
    val priority: TicketPriority? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val reasoning: String? = null
)

/**
 * Список тикетов с метаданными
 */
@Serializable
data class TicketListResponse(
    val tickets: List<SupportTicket>,
    val total: Int,
    val page: Int = 0,
    val pageSize: Int = 20
)

/**
 * Тикеты, связанные с сессией
 */
@Serializable
data class SessionTicketsResponse(
    val sessionId: String,
    val tickets: List<SupportTicket>,
    val count: Int
)

/**
 * Статистика работы сервиса поддержки
 */
@Serializable
data class SupportStats(
    val totalQuestions: Int,
    val averageResponseTime: Double,
    val ragHitRate: Double,
    val topCategories: Map<String, Int>
)

/**
 * Ответ MCP при создании тикета
 */
@Serializable
data class McpTicketCreateResponse(
    val success: Boolean,
    val ticket: SupportTicket,
    val message: String
)

/**
 * Ответ MCP при обновлении тикета
 */
@Serializable
data class McpTicketUpdateResponse(
    val success: Boolean,
    val ticket: SupportTicket? = null,
    val ticketId: String? = null,
    val oldStatus: String? = null,
    val newStatus: String? = null,
    val deleted: Boolean = false,
    val message: String
)

/**
 * Ответ MCP с ошибкой
 */
@Serializable
data class McpErrorResponse(
    val error: String
)

/**
 * Ответ MCP при поиске тикетов по сессии
 */
@Serializable
data class McpSessionTicketsResponse(
    val sessionId: String,
    val tickets: List<SupportTicket>,
    val count: Int,
    val stats: Map<String, Int>? = null
)

/**
 * Ответ MCP при удалении тикета
 */
@Serializable
data class McpTicketDeleteResponse(
    val success: Boolean,
    val ticketId: String,
    val message: String
)
