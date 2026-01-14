package com.claude.agent.routes

import com.claude.agent.database.ConversationRepository
import com.claude.agent.database.TicketRepository
import com.claude.agent.models.*
import com.claude.agent.service.SupportService
import com.claude.agent.service.WebSocketMessage
import com.claude.agent.service.WebSocketService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Роуты для работы с AI-ассистентом службы поддержки и управления тикетами
 */
fun Route.supportRoutes(
    supportService: SupportService,
    ticketRepository: TicketRepository,
    webSocketService: WebSocketService,
    conversationRepository: ConversationRepository
) {
    val logger = LoggerFactory.getLogger("SupportRoutes")
    
    /**
     * POST /support/ask - задать вопрос службе поддержки
     * 
     * Интегрирует:
     * - RAG для поиска в документации
     * - MCP support_crm для доступа к данным пользователей и тикетов
     * - Claude API для генерации персонализированных ответов
     */
    post("/support/ask") {
        try {
            val request = call.receive<SupportRequest>()
            
            // Валидация
            if (request.question.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Вопрос не может быть пустым")
                )
                return@post
            }
            
            logger.info("Support request: question='${request.question.take(50)}...', sessionId=${request.sessionId}, ticketId=${request.ticketId}")
            
            // Обрабатываем запрос
            val response = supportService.askSupport(request)
            
            call.respond(HttpStatusCode.OK, response)
            
        } catch (e: Exception) {
            logger.error("Ошибка в /support/ask: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }
    
    /**
     * POST /support/ticket/update - обновить статус тикета
     *
     * Позволяет обновить статус тикета через MCP support_crm
     */
    post("/support/ticket/update") {
        try {
            val request = call.receive<McpUpdateTicketRequest>()

            // Валидация
            if (request.ticketId.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID тикета не может быть пустым")
                )
                return@post
            }

            logger.info("Update ticket via MCP: ticketId=${request.ticketId}, status=${request.status}")

            // Обновляем тикет через репозиторий
            val updateRequest = UpdateTicketRequest(
                status = request.status,
                priority = request.priority,
                assignedTo = request.assignedTo,
                category = request.category,
                tags = request.tags
            )

            val updatedTicket = ticketRepository.updateTicket(request.ticketId, updateRequest)
            if (updatedTicket == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Тикет не найден: ${request.ticketId}")
                )
                return@post
            }

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "success" to true,
                    "ticket" to updatedTicket
                )
            )
            
        } catch (e: Exception) {
            logger.error("Ошибка в /support/ticket/update: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }
    
    /**
     * GET /support/user/{userId} - получить информацию о пользователе
     */
    get("/support/user/{userId}") {
        try {
            val userId = call.parameters["userId"]
            
            if (userId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID пользователя не может быть пустым")
                )
                return@get
            }
            
            logger.info("Get user info: userId=$userId")
            
            // TODO: Реализовать получение через MCP
            // Пока возвращаем заглушку
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "id" to userId,
                    "message" to "User info endpoint - to be implemented"
                )
            )
            
        } catch (e: Exception) {
            logger.error("Ошибка в /support/user/{userId}: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }
    
    /**
     * GET /support/ticket/{ticketId} - получить информацию о тикете
     */
    get("/support/ticket/{ticketId}") {
        try {
            val ticketId = call.parameters["ticketId"]
            
            if (ticketId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID тикета не может быть пустым")
                )
                return@get
            }
            
            logger.info("Get ticket info: ticketId=$ticketId")

            val ticket = ticketRepository.getTicket(ticketId)
            if (ticket == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Тикет не найден: $ticketId")
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, ticket)

        } catch (e: Exception) {
            logger.error("Ошибка в /support/ticket/{ticketId}: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }

    /**
     * POST /api/tickets - создать новый тикет
     */
    post("/api/tickets") {
        try {
            val request = call.receive<CreateTicketRequest>()

            logger.info("Creating ticket: title='${request.title}'")

            val ticket = ticketRepository.createTicket(request, autoCreated = false)

            call.respond(HttpStatusCode.Created, ticket)

        } catch (e: Exception) {
            logger.error("Ошибка в POST /api/tickets: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }

    /**
     * GET /api/tickets - получить список всех тикетов
     */
    get("/api/tickets") {
        try {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            logger.info("Getting tickets list: page=$page, pageSize=$pageSize")

            val response = ticketRepository.getAllTickets(page, pageSize)

            call.respond(HttpStatusCode.OK, response)

        } catch (e: Exception) {
            logger.error("Ошибка в GET /api/tickets: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }

    /**
     * GET /api/tickets/{ticketId} - получить тикет по ID
     */
    get("/api/tickets/{ticketId}") {
        try {
            val ticketId = call.parameters["ticketId"]

            if (ticketId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID тикета не может быть пустым")
                )
                return@get
            }

            val ticket = ticketRepository.getTicket(ticketId)
            if (ticket == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Тикет не найден: $ticketId")
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, ticket)

        } catch (e: Exception) {
            logger.error("Ошибка в GET /api/tickets/{ticketId}: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }

    /**
     * PATCH /api/tickets/{ticketId} - обновить тикет
     */
    patch("/api/tickets/{ticketId}") {
        try {
            val ticketId = call.parameters["ticketId"]

            if (ticketId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID тикета не может быть пустым")
                )
                return@patch
            }

            val request = call.receive<UpdateTicketRequest>()

            logger.info("Updating ticket: $ticketId, status=${request.status}")

            val updateResult = ticketRepository.updateTicket(ticketId, request)
            if (updateResult == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Тикет не найден: $ticketId")
                )
                return@patch
            }

            // Отправляем уведомление в чат о изменении статуса
            if (updateResult.oldStatus != updateResult.newStatus) {
                val sessionId = updateResult.ticket?.sessionId ?: updateResult.oldStatus.name

                // Формируем сообщение для чата
                val statusEmoji = when (updateResult.newStatus) {
                    TicketStatus.OPEN -> "🆕"
                    TicketStatus.IN_PROGRESS -> "🔄"
                    TicketStatus.WAITING_FOR_USER -> "⏳"
                    TicketStatus.RESOLVED -> "✅"
                    TicketStatus.CLOSED -> "🔒"
                }

                val notificationMessage = if (updateResult.wasDeleted) {
                    "🎫 Тикет $ticketId закрыт и удален: ${updateResult.oldStatus.name} → ${updateResult.newStatus.name}"
                } else {
                    "🎫 Тикет $ticketId изменен: ${updateResult.oldStatus.name} → $statusEmoji ${updateResult.newStatus.name}"
                }

                // Сохраняем системное сообщение в БД
                try {
                    if (updateResult.ticket != null) {
                        conversationRepository.saveMessage(
                            sessionId = updateResult.ticket.sessionId,
                            role = "assistant",
                            content = notificationMessage,
                            inputTokens = null,
                            outputTokens = null
                        )

                        // Отправляем через WebSocket
                        val ticketData = buildJsonObject {
                            put("ticket_id", ticketId)
                            put("old_status", updateResult.oldStatus.name)
                            put("new_status", updateResult.newStatus.name)
                            put("message", notificationMessage)
                            put("was_deleted", updateResult.wasDeleted)
                        }

                        webSocketService.broadcastToSession(
                            sessionId = updateResult.ticket.sessionId,
                            message = WebSocketMessage(
                                type = "ticket_status_changed",
                                sessionId = updateResult.ticket.sessionId,
                                data = ticketData.toString()
                            )
                        )

                        logger.info("📡 Уведомление об изменении статуса тикета отправлено: $notificationMessage")
                    }
                } catch (e: Exception) {
                    logger.error("Ошибка отправки уведомления об изменении тикета: ${e.message}", e)
                }
            }

            // Если тикет был удален (CLOSED), возвращаем информацию об удалении
            if (updateResult.wasDeleted || updateResult.ticket == null) {
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true, "ticketId" to ticketId))
            } else {
                call.respond(HttpStatusCode.OK, updateResult.ticket!!)
            }

        } catch (e: Exception) {
            logger.error("Ошибка в PATCH /api/tickets/{ticketId}: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }

    /**
     * DELETE /api/tickets/{ticketId} - удалить тикет
     */
    delete("/api/tickets/{ticketId}") {
        try {
            val ticketId = call.parameters["ticketId"]

            if (ticketId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID тикета не может быть пустым")
                )
                return@delete
            }

            logger.info("Deleting ticket: $ticketId")

            val deleted = ticketRepository.deleteTicket(ticketId)
            if (!deleted) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Тикет не найден: $ticketId")
                )
                return@delete
            }

            call.respond(HttpStatusCode.OK, mapOf("success" to true, "ticketId" to ticketId))

        } catch (e: Exception) {
            logger.error("Ошибка в DELETE /api/tickets/{ticketId}: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }

    /**
     * GET /api/sessions/{sessionId}/tickets - получить тикеты сессии
     */
    get("/api/sessions/{sessionId}/tickets") {
        try {
            val sessionId = call.parameters["sessionId"]

            if (sessionId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("ID сессии не может быть пустым")
                )
                return@get
            }

            logger.info("Getting tickets for session: $sessionId")

            val tickets = ticketRepository.getSessionTickets(sessionId)

            val response = SessionTicketsResponse(
                sessionId = sessionId,
                tickets = tickets,
                count = tickets.size
            )

            call.respond(HttpStatusCode.OK, response)

        } catch (e: Exception) {
            logger.error("Ошибка в GET /api/sessions/{sessionId}/tickets: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Ошибка сервера: ${e.message}")
            )
        }
    }
}
