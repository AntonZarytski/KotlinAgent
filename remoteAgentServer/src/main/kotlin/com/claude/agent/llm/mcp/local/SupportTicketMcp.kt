package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.SupportTicketService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class SupportTicketMcp(
    private val ticketService: SupportTicketService
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(SupportTicketMcp::class.java)

    companion object {
        const val TOOL_NAME = "support_ticket_mcp"
    }

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = TOOL_NAME,
        second = LocalToolDefinition(
            name = TOOL_NAME,
            ui_description = "Управление тикетами для долгих задач агента. Позволяет создавать, обновлять, завершать и просматривать тикеты.",
            description = """
Система тикетов для управления долгими задачами агента.

КОГДА ИСПОЛЬЗОВАТЬ:
- Задача займет более 30 секунд выполнения
- Задача требует множественных операций
- Нужно отслеживать прогресс долгой задачи

КАК РАБОТАТЬ С ТИКЕТАМИ:

1. **create_ticket** - создать новый тикет
   - Используй когда начинаешь долгую задачу (>30 сек)
   - Укажи четкое название и описание задачи
   - Тикет получит статус "opened"
   - Возвращает ticket_id для дальнейшей работы

2. **update_progress** - обновить прогресс
   - Вызывай после каждого важного этапа работы
   - Опиши что было сделано
   - Тикет перейдет в статус "inProgress"
   - Все обновления сохраняются в timeline

3. **finish_ticket** - завершить тикет
   - Вызывай когда задача полностью выполнена
   - Предоставь краткий отчет о результатах
   - Тикет перейдет в статус "finished"
   - Финальный отчет будет выведен в чат

4. **list_tickets** - список тикетов для сессии
   - Показывает все тикеты текущей сессии
   - Сортировка по дате создания (новые сверху)

5. **get_ticket_status** - детальный статус тикета
   - Полная информация о тикете
   - Весь timeline с действиями
   - Используй чтобы напомнить себе что делал

ПРИМЕР РАБОТЫ:
1. create_ticket(title="Рефакторинг модуля авторизации", description="Нужно переписать старый код...")
2. update_progress(ticket_id=123, progress_text="Создал новые классы AuthService и TokenManager")
3. update_progress(ticket_id=123, progress_text="Обновил все тесты")
4. finish_ticket(ticket_id=123, final_report="Рефакторинг завершен: 15 файлов обновлено, все тесты проходят")

ВАЖНО: Пользователь может в любой момент спросить про статус тикета - используй list_tickets или get_ticket_status.
""",
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "action" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(
                                        listOf(
                                            JsonPrimitive("create_ticket"),
                                            JsonPrimitive("update_progress"),
                                            JsonPrimitive("finish_ticket"),
                                            JsonPrimitive("list_tickets"),
                                            JsonPrimitive("get_ticket_status")
                                        )
                                    ),
                                    "description" to JsonPrimitive("Действие с тикетом")
                                )
                            ),
                            "title" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Название задачи (для create_ticket)")
                                )
                            ),
                            "description" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Описание задачи (для create_ticket)")
                                )
                            ),
                            "ticket_id" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("ID тикета (для update_progress, finish_ticket, get_ticket_status)")
                                )
                            ),
                            "progress_text" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Текст обновления прогресса (для update_progress)")
                                )
                            ),
                            "final_report" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Итоговый отчет о выполнении (для finish_ticket)")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(listOf(JsonPrimitive("action")))
                )
            )
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        val action = arguments["action"]?.jsonPrimitive?.content
            ?: return buildErrorJson("action required")

        if (sessionId == null) {
            return buildErrorJson("sessionId required for support tickets")
        }

        logger.info("🎫 support_ticket_mcp called: $action, sessionId: $sessionId")

        return when (action) {
            "create_ticket" -> {
                val title = arguments["title"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("title required for create_ticket")
                val description = arguments["description"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("description required for create_ticket")

                val ticket = ticketService.createTicket(sessionId, title, description)

                buildJsonObject {
                    put("status", "success")
                    put("action", "create_ticket")
                    put("ticket_id", ticket.id)
                    put("title", ticket.title)
                    put("ticket_status", ticket.status)
                    put("created_at", ticket.createdAt)
                    put("message", "Тикет #${ticket.id.take(8)} создан: ${ticket.title}")
                }.toString()
            }

            "update_progress" -> {
                val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("ticket_id required for update_progress")
                val progressText = arguments["progress_text"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("progress_text required for update_progress")

                val ticket = ticketService.updateProgress(ticketId, progressText)
                    ?: return buildErrorJson("Ticket not found: $ticketId")

                buildJsonObject {
                    put("status", "success")
                    put("action", "update_progress")
                    put("ticket_id", ticket.id)
                    put("ticket_status", ticket.status)
                    put("updated_at", ticket.updatedAt)
                    put("timeline_count", ticket.timeline.size)
                    put("message", "Прогресс обновлен для тикета #${ticket.id.take(8)}")
                }.toString()
            }

            "finish_ticket" -> {
                val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("ticket_id required for finish_ticket")
                val finalReport = arguments["final_report"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("final_report required for finish_ticket")

                val ticket = ticketService.finishTicket(ticketId, finalReport)
                    ?: return buildErrorJson("Ticket not found: $ticketId")

                buildJsonObject {
                    put("status", "success")
                    put("action", "finish_ticket")
                    put("ticket_id", ticket.id)
                    put("title", ticket.title)
                    put("ticket_status", ticket.status)
                    put("finished_at", ticket.finishedAt ?: "")
                    put("final_report", finalReport)
                    put("message", "✅ Тикет #${ticket.id.take(8)} завершен: ${ticket.title}\n\n$finalReport")
                }.toString()
            }

            "list_tickets" -> {
                val tickets = ticketService.listTickets(sessionId)

                buildJsonObject {
                    put("status", "success")
                    put("action", "list_tickets")
                    put("count", tickets.size)
                    putJsonArray("tickets") {
                        tickets.forEach { ticket ->
                            addJsonObject {
                                put("ticket_id", ticket.id)
                                put("title", ticket.title)
                                put("status", ticket.status)
                                put("created_at", ticket.createdAt)
                                put("updated_at", ticket.updatedAt)
                                put("timeline_count", ticket.timeline.size)
                            }
                        }
                    }
                    put("message", "Найдено тикетов: ${tickets.size}")
                }.toString()
            }

            "get_ticket_status" -> {
                val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
                    ?: return buildErrorJson("ticket_id required for get_ticket_status")

                val ticket = ticketService.getTicket(ticketId)
                    ?: return buildErrorJson("Ticket not found: $ticketId")

                buildJsonObject {
                    put("status", "success")
                    put("action", "get_ticket_status")
                    put("ticket_id", ticket.id)
                    put("title", ticket.title)
                    put("description", ticket.description)
                    put("ticket_status", ticket.status)
                    put("created_at", ticket.createdAt)
                    put("updated_at", ticket.updatedAt)
                    if (ticket.finishedAt != null) {
                        put("finished_at", ticket.finishedAt)
                    }
                    putJsonArray("timeline") {
                        ticket.timeline.forEach { entry ->
                            addJsonObject {
                                put("timestamp", entry.timestamp)
                                put("entry", entry.entry)
                            }
                        }
                    }
                    put("message", "Тикет #${ticket.id.take(8)}: ${ticket.title} [${ticket.status}]")
                }.toString()
            }

            else -> buildErrorJson("Unknown action: $action")
        }
    }

    private fun buildErrorJson(message: String): String {
        return buildJsonObject {
            put("status", "error")
            put("error", message)
        }.toString()
    }
}
