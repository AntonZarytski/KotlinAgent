package com.claude.agent.routes

import com.claude.agent.service.SupportTicketService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class TimelineEntryDto(
    val timestamp: String,
    val entry: String
)

@Serializable
data class TicketDto(
    val id: String,
    val sessionId: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val finishedAt: String? = null,
    val timeline: List<TimelineEntryDto> = emptyList()
)

@Serializable
data class TicketsResponse(
    val tickets: List<TicketDto> = emptyList()
)

fun Route.ticketRoutes(ticketService: SupportTicketService) {
    val logger = LoggerFactory.getLogger("TicketRoutes")

    /**
     * GET /api/tickets - получить список тикетов для сессии
     */
    get("/api/tickets") {
        try {
            val sessionId = call.request.queryParameters["session_id"]

            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "session_id required"))
                return@get
            }

            logger.info("📋 GET /api/tickets for session: $sessionId")

            val tickets = ticketService.listTickets(sessionId)

            val response = TicketsResponse(
                tickets = tickets.map { ticket ->
                    TicketDto(
                        id = ticket.id,
                        sessionId = ticket.sessionId,
                        title = ticket.title,
                        description = ticket.description,
                        status = ticket.status,
                        createdAt = ticket.createdAt,
                        updatedAt = ticket.updatedAt,
                        finishedAt = ticket.finishedAt,
                        timeline = ticket.timeline.map { entry ->
                            TimelineEntryDto(
                                timestamp = entry.timestamp,
                                entry = entry.entry
                            )
                        }
                    )
                }
            )

            call.respond(HttpStatusCode.OK, response)
            logger.info("✅ Returned ${tickets.size} tickets")

        } catch (e: Exception) {
            logger.error("❌ Error getting tickets: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}
