package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.database.TicketRepository
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.io.File

/**
 * MCP инструмент для работы с системой поддержки (CRM/тикеты)
 *
 * Предоставляет полный доступ к управлению тикетами:
 * - Создание новых тикетов
 * - Просмотр и поиск тикетов
 * - Обновление статуса и других полей
 * - Удаление тикетов
 */
class SupportMcp(
    private val dataPath: String = "support_data"
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(SupportMcp::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Репозиторий для работы с тикетами в БД
    // Будет установлен после инициализации для избежания циклической зависимости
    var ticketRepository: TicketRepository? = null

    // Кэш данных в памяти (для legacy support_data файлов)
    private var users: List<SupportUser> = emptyList()
    private var tickets: List<SupportTicket> = emptyList()

    init {
        loadData()
    }
    
    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "support_crm",
        second = LocalToolDefinition(
            name = "support_crm",
            ui_description = "Управление тикетами поддержки: создание, просмотр, обновление, удаление",
            description = """
                Инструмент для полного управления системой тикетов поддержки.

                ФУНКЦИИ:
                1. create_ticket - создать новый тикет (используй когда пользователь сообщает о проблеме/задаче)
                2. get_ticket - получить детали тикета по ID
                3. search_tickets - найти тикеты текущей сессии чата
                4. add_info - добавить дополнительную информацию к существующему тикету
                5. update_ticket - обновить тикет (статус, приоритет, категорию, теги)
                6. delete_ticket - удалить тикет
                7. get_session_history - получить историю тикетов сессии

                КОГДА СОЗДАВАТЬ ТИКЕТ:
                - Пользователь сообщает о проблеме, баге или ошибке
                - Пользователь просит добавить новую функцию
                - Пользователь задает вопрос, требующий дальнейшего исследования
                - Пользователь описывает задачу, которую нужно отслеживать

                КОГДА ДОБАВЛЯТЬ ИНФОРМАЦИЮ (add_info):
                - Пользователь предоставляет дополнительные детали к уже созданному тикету
                - Пользователь уточняет проблему в рамках текущей сессии
                - В сессии уже есть открытый тикет по этой теме
                - НЕ создавай новый тикет, если можно обновить существующий!

                ВАЖНО:
                - Тикеты привязаны к сессиям чата (session_id)
                - ID тикетов имеют формат "T-001", "T-002" и т.д.
                - После создания тикета используй ID из ответа для дальнейших операций
                - Всегда сообщай пользователю о создании тикета в своем ответе
                - При создании тикета укажи понятный title и подробный description
                - Перед созданием нового тикета проверь, нет ли уже открытого тикета в сессии (search_tickets)
            """.trimIndent(),
            enabled = true,
            input_schema = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "action" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "enum" to JsonArray(listOf(
                            JsonPrimitive("create_ticket"),
                            JsonPrimitive("get_ticket"),
                            JsonPrimitive("search_tickets"),
                            JsonPrimitive("add_info"),
                            JsonPrimitive("update_ticket"),
                            JsonPrimitive("delete_ticket"),
                            JsonPrimitive("get_session_history")
                        )),
                        "description" to JsonPrimitive("Действие для выполнения")
                    )),
                    "session_id" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("ID сессии чата (автоматически подставляется если не указан)")
                    )),
                    "ticket_id" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("ID тикета в формате T-001, T-002 и т.д. (для get_ticket, update_ticket, delete_ticket). Используй ID из ответа create_ticket.")
                    )),
                    "title" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("Заголовок тикета (для create_ticket)")
                    )),
                    "description" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("Описание проблемы/задачи (для create_ticket)")
                    )),
                    "priority" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "enum" to JsonArray(listOf(
                            JsonPrimitive("LOW"),
                            JsonPrimitive("MEDIUM"),
                            JsonPrimitive("HIGH"),
                            JsonPrimitive("CRITICAL")
                        )),
                        "description" to JsonPrimitive("Приоритет тикета (для create_ticket, update_ticket)")
                    )),
                    "status" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "enum" to JsonArray(listOf(
                            JsonPrimitive("OPEN"),
                            JsonPrimitive("IN_PROGRESS"),
                            JsonPrimitive("WAITING_FOR_USER"),
                            JsonPrimitive("RESOLVED"),
                            JsonPrimitive("CLOSED")
                        )),
                        "description" to JsonPrimitive("Статус тикета (для update_ticket)")
                    )),
                    "category" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("Категория тикета: bug, feature_request, question, task, other (для create_ticket, update_ticket)")
                    )),
                    "tags" to JsonObject(mapOf(
                        "type" to JsonPrimitive("array"),
                        "items" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string")
                        )),
                        "description" to JsonPrimitive("Теги для тикета (для create_ticket, update_ticket)")
                    )),
                    "additional_info" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("Дополнительная информация для добавления к тикету (для add_info)")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("action")))
            ))
        )
    )
    
    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        val action = arguments["action"]?.jsonPrimitive?.content
            ?: return errorJson("action is required")

        logger.info("Support CRM action: $action, sessionId: $sessionId")

        return try {
            when (action) {
                "create_ticket" -> {
                    val sid = arguments["session_id"]?.jsonPrimitive?.content
                        ?: sessionId
                        ?: return errorJson("session_id is required for create_ticket")
                    val title = arguments["title"]?.jsonPrimitive?.content
                        ?: return errorJson("title is required for create_ticket")
                    val description = arguments["description"]?.jsonPrimitive?.content
                        ?: return errorJson("description is required for create_ticket")
                    val priority = arguments["priority"]?.jsonPrimitive?.content?.let {
                        try {
                            TicketPriority.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            TicketPriority.MEDIUM
                        }
                    } ?: TicketPriority.MEDIUM
                    val category = arguments["category"]?.jsonPrimitive?.content
                    val tags = arguments["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                    createTicket(sid, title, description, priority, category, tags)
                }
                "get_ticket" -> {
                    val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                        ?: return errorJson("ticket_id is required")
                    getTicket(ticketId)
                }
                "search_tickets" -> {
                    val sid = arguments["session_id"]?.jsonPrimitive?.content
                        ?: sessionId
                        ?: return errorJson("session_id is required")
                    searchTickets(sid)
                }
                "add_info" -> {
                    val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                        ?: return errorJson("ticket_id is required for add_info")
                    val additionalInfo = arguments["additional_info"]?.jsonPrimitive?.content
                        ?: return errorJson("additional_info is required for add_info")
                    addInfoToTicket(ticketId, additionalInfo)
                }
                "update_ticket" -> {
                    val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                        ?: return errorJson("ticket_id is required for update_ticket")
                    val status = arguments["status"]?.jsonPrimitive?.content?.let {
                        try {
                            TicketStatus.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }
                    val priority = arguments["priority"]?.jsonPrimitive?.content?.let {
                        try {
                            TicketPriority.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }
                    val category = arguments["category"]?.jsonPrimitive?.content
                    val tags = arguments["tags"]?.jsonArray?.map { it.jsonPrimitive.content }

                    updateTicket(ticketId, status, priority, category, tags)
                }
                "delete_ticket" -> {
                    val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                        ?: return errorJson("ticket_id is required for delete_ticket")
                    deleteTicket(ticketId)
                }
                "get_session_history" -> {
                    val sid = arguments["session_id"]?.jsonPrimitive?.content
                        ?: sessionId
                        ?: return errorJson("session_id is required")
                    getSessionHistory(sid)
                }
                // Legacy support для старого названия
                "update_ticket_status" -> {
                    val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                        ?: return errorJson("ticket_id is required")
                    val status = arguments["status"]?.jsonPrimitive?.content
                        ?: return errorJson("status is required")
                    updateTicketStatus(ticketId, status)
                }
                else -> errorJson("Unknown action: $action")
            }
        } catch (e: Exception) {
            logger.error("Error executing support CRM action: ${e.message}", e)
            errorJson("Error: ${e.message}")
        }
    }

    // === Приватные методы ===

    private fun loadData() {
        try {
            val dataDir = File(dataPath)
            if (!dataDir.exists()) {
                logger.warn("Support data directory not found: $dataPath, using empty data")
                return
            }

            // Загружаем пользователей
            val usersFile = File(dataDir, "users.json")
            if (usersFile.exists()) {
                users = json.decodeFromString(usersFile.readText())
                logger.info("Loaded ${users.size} users from ${usersFile.absolutePath}")
            }

            // Загружаем тикеты
            val ticketsFile = File(dataDir, "tickets.json")
            if (ticketsFile.exists()) {
                tickets = json.decodeFromString(ticketsFile.readText())
                logger.info("Loaded ${tickets.size} tickets from ${ticketsFile.absolutePath}")
            }
        } catch (e: Exception) {
            logger.error("Error loading support data: ${e.message}", e)
        }
    }

    private fun getUser(userId: String): String {
        val user = users.find { it.id == userId }
            ?: return errorJson("User not found: $userId")

        return json.encodeToString(user)
    }

    private fun getTicket(ticketId: String): String {
        // Используем БД если доступна, иначе legacy файлы
        if (ticketRepository != null) {
            val ticket = ticketRepository!!.getTicket(ticketId)
                ?: return errorJson("Ticket not found: $ticketId")
            return json.encodeToString(ticket)
        }

        // Legacy: используем файлы
        val ticket = tickets.find { it.id == ticketId }
            ?: return errorJson("Ticket not found: $ticketId")

        return json.encodeToString(ticket)
    }

    private fun searchTickets(sessionId: String): String {
        // Используем БД если доступна, иначе legacy файлы
        if (ticketRepository != null) {
            val sessionTickets = ticketRepository!!.getSessionTickets(sessionId)

            val response = McpSessionTicketsResponse(
                sessionId = sessionId,
                tickets = sessionTickets,
                count = sessionTickets.size
            )
            return json.encodeToString(McpSessionTicketsResponse.serializer(), response)
        }

        // Legacy: используем файлы
        val sessionTickets = tickets.filter { it.sessionId == sessionId }

        val response = McpSessionTicketsResponse(
            sessionId = sessionId,
            tickets = sessionTickets,
            count = sessionTickets.size
        )
        return json.encodeToString(McpSessionTicketsResponse.serializer(), response)
    }

    private fun updateTicketStatus(ticketId: String, newStatus: String): String {
        val ticketIndex = tickets.indexOfFirst { it.id == ticketId }
        if (ticketIndex == -1) {
            return errorJson("Ticket not found: $ticketId")
        }

        val status = try {
            TicketStatus.valueOf(newStatus)
        } catch (e: IllegalArgumentException) {
            return errorJson("Invalid status: $newStatus")
        }

        val oldTicket = tickets[ticketIndex]
        val updatedTicket = oldTicket.copy(
            status = status,
            updatedAt = java.time.Instant.now().toString(),
            resolvedAt = if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
                java.time.Instant.now().toString()
            } else {
                oldTicket.resolvedAt
            }
        )

        // Обновляем в памяти
        tickets = tickets.toMutableList().apply {
            set(ticketIndex, updatedTicket)
        }

        // Сохраняем на диск
        saveTickets()

        val response = McpTicketUpdateResponse(
            success = true,
            ticket = updatedTicket,
            oldStatus = oldTicket.status.name,
            newStatus = status.name,
            message = "Тикет $ticketId успешно обновлен"
        )
        return json.encodeToString(McpTicketUpdateResponse.serializer(), response)
    }

    private fun getSessionHistory(sessionId: String): String {
        // Используем БД если доступна, иначе legacy файлы
        if (ticketRepository != null) {
            val sessionTickets = ticketRepository!!.getSessionTickets(sessionId)

            val stats = mapOf(
                "total_tickets" to sessionTickets.size,
                "open_tickets" to sessionTickets.count { it.status == TicketStatus.OPEN },
                "in_progress_tickets" to sessionTickets.count { it.status == TicketStatus.IN_PROGRESS },
                "resolved_tickets" to sessionTickets.count { it.status == TicketStatus.RESOLVED },
                "closed_tickets" to sessionTickets.count { it.status == TicketStatus.CLOSED },
                "high_priority_tickets" to sessionTickets.count { it.priority == TicketPriority.HIGH || it.priority == TicketPriority.CRITICAL }
            )

            val response = McpSessionTicketsResponse(
                sessionId = sessionId,
                tickets = sessionTickets,
                count = sessionTickets.size,
                stats = stats
            )
            return json.encodeToString(McpSessionTicketsResponse.serializer(), response)
        }

        // Legacy: используем файлы
        val sessionTickets = tickets.filter { it.sessionId == sessionId }
            .sortedByDescending { it.createdAt }

        val stats = mapOf(
            "total_tickets" to sessionTickets.size,
            "open_tickets" to sessionTickets.count { it.status == TicketStatus.OPEN },
            "in_progress_tickets" to sessionTickets.count { it.status == TicketStatus.IN_PROGRESS },
            "resolved_tickets" to sessionTickets.count { it.status == TicketStatus.RESOLVED },
            "closed_tickets" to sessionTickets.count { it.status == TicketStatus.CLOSED },
            "high_priority_tickets" to sessionTickets.count { it.priority == TicketPriority.HIGH || it.priority == TicketPriority.CRITICAL }
        )

        val response = McpSessionTicketsResponse(
            sessionId = sessionId,
            tickets = sessionTickets,
            count = sessionTickets.size,
            stats = stats
        )
        return json.encodeToString(McpSessionTicketsResponse.serializer(), response)
    }

    /**
     * Создает новый тикет
     */
    private fun createTicket(
        sessionId: String,
        title: String,
        description: String,
        priority: TicketPriority,
        category: String?,
        tags: List<String>
    ): String {
        if (ticketRepository == null) {
            return errorJson("Ticket repository not initialized")
        }

        val request = CreateTicketRequest(
            sessionId = sessionId,
            title = title,
            description = description,
            priority = priority,
            category = category,
            tags = tags
        )

        val ticket = ticketRepository!!.createTicket(request, autoCreated = false)
        logger.info("✅ Created ticket via MCP: ${ticket.id} - $title")

        val response = McpTicketCreateResponse(
            success = true,
            ticket = ticket,
            message = "Тикет ${ticket.id} успешно создан"
        )
        return json.encodeToString(McpTicketCreateResponse.serializer(), response)
    }

    /**
     * Добавляет дополнительную информацию к существующему тикету
     */
    private fun addInfoToTicket(ticketId: String, additionalInfo: String): String {
        if (ticketRepository == null) {
            return errorJson("Ticket repository not initialized")
        }

        val ticket = ticketRepository!!.addInfoToTicket(ticketId, additionalInfo)
            ?: return errorJson("Ticket not found: $ticketId")

        logger.info("✅ Added info to ticket via MCP: $ticketId")

        val response = McpTicketUpdateResponse(
            success = true,
            ticket = ticket,
            message = "Информация добавлена к тикету $ticketId"
        )
        return json.encodeToString(McpTicketUpdateResponse.serializer(), response)
    }

    /**
     * Обновляет тикет (полное обновление, не только статус)
     */
    private fun updateTicket(
        ticketId: String,
        status: TicketStatus?,
        priority: TicketPriority?,
        category: String?,
        tags: List<String>?
    ): String {
        if (ticketRepository == null) {
            return errorJson("Ticket repository not initialized")
        }

        val updateRequest = UpdateTicketRequest(
            status = status,
            priority = priority,
            category = category,
            tags = tags
        )

        val result = ticketRepository!!.updateTicket(ticketId, updateRequest)
            ?: return errorJson("Ticket not found: $ticketId")

        logger.info("✅ Updated ticket via MCP: $ticketId (status: ${result.oldStatus} -> ${result.newStatus})")

        val response = if (result.wasDeleted) {
            McpTicketUpdateResponse(
                success = true,
                ticketId = ticketId,
                oldStatus = result.oldStatus.name,
                newStatus = result.newStatus.name,
                deleted = true,
                message = "Тикет $ticketId закрыт и удален"
            )
        } else {
            McpTicketUpdateResponse(
                success = true,
                ticket = result.ticket,
                oldStatus = result.oldStatus.name,
                newStatus = result.newStatus.name,
                message = "Тикет $ticketId успешно обновлен"
            )
        }
        return json.encodeToString(McpTicketUpdateResponse.serializer(), response)
    }

    /**
     * Удаляет тикет
     */
    private fun deleteTicket(ticketId: String): String {
        if (ticketRepository == null) {
            return errorJson("Ticket repository not initialized")
        }

        val deleted = ticketRepository!!.deleteTicket(ticketId)
        if (!deleted) {
            return errorJson("Ticket not found or could not be deleted: $ticketId")
        }

        logger.info("✅ Deleted ticket via MCP: $ticketId")

        val response = McpTicketDeleteResponse(
            success = true,
            ticketId = ticketId,
            message = "Тикет $ticketId успешно удален"
        )
        return json.encodeToString(McpTicketDeleteResponse.serializer(), response)
    }

    private fun saveTickets() {
        try {
            val dataDir = File(dataPath)
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }

            val ticketsFile = File(dataDir, "tickets.json")
            ticketsFile.writeText(json.encodeToString(tickets))
            logger.info("Saved ${tickets.size} tickets to ${ticketsFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Error saving tickets: ${e.message}", e)
        }
    }

    override fun errorJson(message: String): String {
        return json.encodeToString(McpErrorResponse.serializer(), McpErrorResponse(error = message))
    }
}
