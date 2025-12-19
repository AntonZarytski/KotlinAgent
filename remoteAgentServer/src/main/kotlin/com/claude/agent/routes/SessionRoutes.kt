package com.claude.agent.routes

import com.claude.agent.database.ConversationRepository
import com.claude.agent.models.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory


fun Route.sessionRoutes(repository: ConversationRepository) {
    val logger = LoggerFactory.getLogger("SessionRoutes")

    route("/api/sessions") {

        /**
         * GET /api/sessions - получить список всех сессий.
         */
        get {
            try {
                val sessions = repository.getAllSessions()
                call.respond(HttpStatusCode.OK, SessionsResponse(sessions))
            } catch (e: Exception) {
                logger.error("Ошибка получения списка сессий: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }

        /**
         * POST /api/sessions - создать новую сессию.
         */
        post {
            try {
                val request = call.receive<CreateSessionRequest>()

                if (request.session_id.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("session_id обязателен"))
                    return@post
                }

                val success = repository.createSession(request.session_id, request.title)

                if (success) {
                    call.respond(HttpStatusCode.OK, CreateSessionResponse(request.session_id, request.title))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Сессия уже существует"))
                }

            } catch (e: Exception) {
                logger.error("Ошибка создания сессии: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }

        /**
         * GET /api/sessions/{id} - получить историю конкретной сессии.
         */
        get("/{id}") {
            try {
                val sessionId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("session_id обязателен"))
                    return@get
                }

                val history = repository.getSessionHistory(sessionId)
                val stats = repository.getSessionStats(sessionId)

                call.respond(HttpStatusCode.OK, SessionHistoryResponse(
                    session_id = sessionId,
                    history = history,
                    stats = stats
                ))

            } catch (e: Exception) {
                logger.error("Ошибка получения сессии: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }

        /**
         * DELETE /api/sessions/{id} - удалить сессию.
         */
        delete("/{id}") {
            try {
                val sessionId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("session_id обязателен"))
                    return@delete
                }

                val success = repository.deleteSession(sessionId)

                if (success) {
                    call.respond(HttpStatusCode.OK, DeleteSessionResponse("Сессия удалена"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка удаления"))
                }

            } catch (e: Exception) {
                logger.error("Ошибка удаления сессии: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }

        /**
         * GET /api/sessions/{id}/export - экспортировать сессию в JSON.
         */
        get("/{id}/export") {
            try {
                val sessionId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("session_id обязателен"))
                    return@get
                }

                val history = repository.getSessionHistory(sessionId)

                if (history.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Сессия не найдена"))
                    return@get
                }

                // Простой экспорт (можно расширить с метаданными как в Python)
                call.respond(HttpStatusCode.OK, mapOf(
                    "session_id" to sessionId,
                    "messages" to history
                ))

            } catch (e: Exception) {
                logger.error("Ошибка экспорта сессии: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }

        /**
         * GET /api/sessions/unread_counts - получить количество непрочитанных сообщений для всех сессий.
         */
        get("/unread_counts") {
            try {
                val unreadCounts = repository.getUnreadCountsForAllSessions()
                call.respond(HttpStatusCode.OK, mapOf("unread_counts" to unreadCounts))
            } catch (e: Exception) {
                logger.error("Ошибка получения непрочитанных сообщений: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }
    }
}
