package com.claude.agent.service

import com.claude.agent.database.DatabaseFactory
import com.claude.agent.database.models.SupportTickets
import com.claude.agent.database.models.TicketTimeline
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Сервис для работы с тикетами поддержки (долгих задач агента).
 */
class SupportTicketService {
    private val logger = LoggerFactory.getLogger(SupportTicketService::class.java)

    data class Ticket(
        val id: String,
        val sessionId: String,
        val title: String,
        val description: String,
        val status: String,
        val createdAt: String,
        val updatedAt: String,
        val finishedAt: String?,
        val timeline: List<TimelineEntry> = emptyList()
    )

    data class TimelineEntry(
        val timestamp: String,
        val entry: String
    )

    /**
     * Создать новый тикет
     */
    fun createTicket(
        sessionId: String,
        title: String,
        description: String
    ): Ticket {
        val ticketId = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        transaction(DatabaseFactory.getMainDatabase()) {
            SupportTickets.insert {
                it[id] = ticketId
                it[SupportTickets.sessionId] = sessionId
                it[SupportTickets.title] = title
                it[SupportTickets.description] = description
                it[status] = "opened"
                it[createdAt] = now
                it[updatedAt] = now
                it[finishedAt] = null
            }

            // Добавляем первую запись в timeline
            TicketTimeline.insert {
                it[TicketTimeline.ticketId] = ticketId
                it[TicketTimeline.timestamp] = now
                it[TicketTimeline.entry] = "Тикет создан: $title"
            }
        }

        logger.info("🎫 Created ticket: $ticketId - $title")

        return Ticket(
            id = ticketId,
            sessionId = sessionId,
            title = title,
            description = description,
            status = "opened",
            createdAt = now,
            updatedAt = now,
            finishedAt = null,
            timeline = listOf(TimelineEntry(now, "Тикет создан: $title"))
        )
    }

    /**
     * Обновить прогресс тикета (добавить запись в timeline)
     */
    fun updateProgress(ticketId: String, progressText: String): Ticket? {
        val now = Instant.now().toString()

        transaction(DatabaseFactory.getMainDatabase()) {
            // Проверяем существование тикета
            val exists = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.count() > 0
            if (!exists) {
                return@transaction
            }

            // Обновляем статус на inProgress, если был opened
            SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[status] = "inProgress"
                it[updatedAt] = now
            }

            // Добавляем запись в timeline
            TicketTimeline.insert {
                it[TicketTimeline.ticketId] = ticketId
                it[TicketTimeline.timestamp] = now
                it[TicketTimeline.entry] = progressText
            }
        }

        logger.info("🎫 Updated ticket: $ticketId - $progressText")

        return getTicket(ticketId)
    }

    /**
     * Завершить тикет
     */
    fun finishTicket(ticketId: String, finalReport: String): Ticket? {
        val now = Instant.now().toString()

        transaction(DatabaseFactory.getMainDatabase()) {
            // Проверяем существование тикета
            val exists = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.count() > 0
            if (!exists) {
                return@transaction
            }

            // Обновляем статус
            SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[status] = "finished"
                it[updatedAt] = now
                it[finishedAt] = now
            }

            // Добавляем финальный отчет в timeline
            TicketTimeline.insert {
                it[TicketTimeline.ticketId] = ticketId
                it[TicketTimeline.timestamp] = now
                it[TicketTimeline.entry] = "✅ ЗАВЕРШЕНО: $finalReport"
            }
        }

        logger.info("🎫 Finished ticket: $ticketId")

        return getTicket(ticketId)
    }

    /**
     * Получить тикет по ID
     */
    fun getTicket(ticketId: String): Ticket? {
        return transaction(DatabaseFactory.getMainDatabase()) {
            val ticketRow = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }
                .singleOrNull() ?: return@transaction null

            val timeline = TicketTimeline.selectAll().where { TicketTimeline.ticketId eq ticketId }
                .orderBy(TicketTimeline.id to SortOrder.ASC)
                .map { row ->
                    TimelineEntry(
                        timestamp = row[TicketTimeline.timestamp],
                        entry = row[TicketTimeline.entry]
                    )
                }

            Ticket(
                id = ticketRow[SupportTickets.id],
                sessionId = ticketRow[SupportTickets.sessionId],
                title = ticketRow[SupportTickets.title],
                description = ticketRow[SupportTickets.description],
                status = ticketRow[SupportTickets.status],
                createdAt = ticketRow[SupportTickets.createdAt],
                updatedAt = ticketRow[SupportTickets.updatedAt],
                finishedAt = ticketRow[SupportTickets.finishedAt],
                timeline = timeline
            )
        }
    }

    /**
     * Получить все тикеты для сессии
     */
    fun listTickets(sessionId: String): List<Ticket> {
        return transaction(DatabaseFactory.getMainDatabase()) {
            SupportTickets.selectAll().where { SupportTickets.sessionId eq sessionId }
                .orderBy(SupportTickets.createdAt to SortOrder.DESC)
                .map { ticketRow ->
                    val ticketId = ticketRow[SupportTickets.id]

                    val timeline = TicketTimeline.selectAll().where { TicketTimeline.ticketId eq ticketId }
                        .orderBy(TicketTimeline.id to SortOrder.ASC)
                        .map { row ->
                            TimelineEntry(
                                timestamp = row[TicketTimeline.timestamp],
                                entry = row[TicketTimeline.entry]
                            )
                        }

                    Ticket(
                        id = ticketId,
                        sessionId = ticketRow[SupportTickets.sessionId],
                        title = ticketRow[SupportTickets.title],
                        description = ticketRow[SupportTickets.description],
                        status = ticketRow[SupportTickets.status],
                        createdAt = ticketRow[SupportTickets.createdAt],
                        updatedAt = ticketRow[SupportTickets.updatedAt],
                        finishedAt = ticketRow[SupportTickets.finishedAt],
                        timeline = timeline
                    )
                }
        }
    }
}
