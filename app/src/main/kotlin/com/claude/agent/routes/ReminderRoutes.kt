package com.claude.agent.routes

import com.claude.agent.models.ErrorResponse
import com.claude.agent.models.Reminder
import com.claude.agent.service.ReminderService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Роуты для работы с напоминаниями.
 * 
 * Предоставляет API для получения списка активных напоминаний.
 */
fun Route.reminderRoutes(reminderService: ReminderService) {
    val logger = LoggerFactory.getLogger("ReminderRoutes")

    route("/api/reminders") {

        /**
         * GET /api/reminders - получить список всех активных напоминаний.
         */
        get {
            try {
                val reminders = reminderService.listReminders()
                    .filter { !it.done } // Возвращаем только активные напоминания

                logger.info("Возвращено ${reminders.size} активных напоминаний")
                call.respond(HttpStatusCode.OK, RemindersResponse(reminders))

            } catch (e: Exception) {
                logger.error("Ошибка получения списка напоминаний: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }

        /**
         * DELETE /api/reminders/{id} - удалить/отклонить напоминание.
         */
        delete("/{id}") {
            try {
                val reminderId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("ID напоминания не указан"))
                    return@delete
                }

                reminderService.deleteReminder(reminderId)
                logger.info("Напоминание удалено: $reminderId")
                call.respond(HttpStatusCode.OK, DeleteReminderResponse("Напоминание удалено"))

            } catch (e: Exception) {
                logger.error("Ошибка удаления напоминания: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
            }
        }
    }
}

@Serializable
data class RemindersResponse(
    val reminders: List<Reminder>
)

@Serializable
data class DeleteReminderResponse(
    val message: String
)

