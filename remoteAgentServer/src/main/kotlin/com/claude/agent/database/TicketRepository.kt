package com.claude.agent.database

import com.claude.agent.database.models.Tickets
import com.claude.agent.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repository для работы с тикетами поддержки в базе данных
 */
class TicketRepository {
    private val logger = LoggerFactory.getLogger(TicketRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    // Счетчик для генерации ID тикетов
    private val ticketCounter = AtomicInteger(0)
    
    init {
        // Инициализируем счетчик максимальным ID из БД
        transaction(DatabaseFactory.getMainDatabase()) {
            val maxId = Tickets.selectAll()
                .mapNotNull { row ->
                    val id = row[Tickets.id]
                    id.removePrefix("T-").toIntOrNull()
                }
                .maxOrNull() ?: 0
            ticketCounter.set(maxId)
            logger.info("Ticket counter initialized to $maxId")
        }
    }
    
    /**
     * Генерирует уникальный ID для нового тикета
     */
    private fun generateTicketId(): String {
        val nextId = ticketCounter.incrementAndGet()
        return "T-%03d".format(nextId)
    }
    
    /**
     * Создает новый тикет
     */
    fun createTicket(request: CreateTicketRequest, autoCreated: Boolean = false): SupportTicket {
        return transaction(DatabaseFactory.getMainDatabase()) {
            val ticketId = generateTicketId()
            val now = Instant.now().toString()

            Tickets.insert {
                it[id] = ticketId
                it[sessionId] = request.sessionId
                it[title] = request.title
                it[description] = request.description
                it[status] = "OPEN"
                it[priority] = request.priority.name
                it[category] = request.category
                it[createdAt] = now
                it[updatedAt] = now
                it[resolvedAt] = null
                it[closedAt] = null
                it[assignedTo] = null
                it[tags] = json.encodeToString(request.tags)
                it[Tickets.autoCreated] = autoCreated
                it[updateHistory] = "[]"
            }

            logger.info("Created ticket: $ticketId for session: ${request.sessionId} (autoCreated=$autoCreated)")

            SupportTicket(
                id = ticketId,
                sessionId = request.sessionId,
                title = request.title,
                description = request.description,
                status = TicketStatus.OPEN,
                priority = request.priority,
                category = request.category,
                createdAt = now,
                updatedAt = now,
                resolvedAt = null,
                closedAt = null,
                assignedTo = null,
                tags = request.tags,
                autoCreated = autoCreated,
                updateHistory = emptyList()
            )
        }
    }
    
    /**
     * Получает тикет по ID
     */
    fun getTicket(ticketId: String): SupportTicket? {
        return transaction(DatabaseFactory.getMainDatabase()) {
            Tickets.selectAll().where { Tickets.id eq ticketId }
                .map { rowToTicket(it) }
                .singleOrNull()
        }
    }

    /**
     * Получает тикеты, связанные с сессией чата
     */
    fun getSessionTickets(sessionId: String): List<SupportTicket> {
        return transaction(DatabaseFactory.getMainDatabase()) {
            Tickets.selectAll().where { Tickets.sessionId eq sessionId }
                .orderBy(Tickets.createdAt to SortOrder.DESC)
                .map { rowToTicket(it) }
        }
    }
    
    /**
     * Получает все тикеты с пагинацией
     */
    fun getAllTickets(page: Int = 0, pageSize: Int = 20): TicketListResponse {
        return transaction(DatabaseFactory.getMainDatabase()) {
            val total = Tickets.selectAll().count().toInt()
            val tickets = Tickets.selectAll()
                .orderBy(Tickets.createdAt to SortOrder.DESC)
                .limit(pageSize, offset = (page * pageSize).toLong())
                .map { rowToTicket(it) }
            
            TicketListResponse(
                tickets = tickets,
                total = total,
                page = page,
                pageSize = pageSize
            )
        }
    }
    
    /**
     * Обновляет статус тикета
     */
    fun updateTicketStatus(ticketId: String, newStatus: TicketStatus): SupportTicket? {
        return transaction(DatabaseFactory.getMainDatabase()) {
            val now = Instant.now().toString()
            
            Tickets.update({ Tickets.id eq ticketId }) {
                it[status] = newStatus.name
                it[updatedAt] = now
                
                // Устанавливаем resolvedAt при переходе в RESOLVED
                if (newStatus == TicketStatus.RESOLVED) {
                    it[resolvedAt] = now
                }
                
                // Устанавливаем closedAt при переходе в CLOSED
                if (newStatus == TicketStatus.CLOSED) {
                    it[closedAt] = now
                }
            }

            getTicket(ticketId)
        }
    }

    /**
     * Добавляет дополнительную информацию к существующему тикету
     */
    fun addInfoToTicket(ticketId: String, additionalInfo: String): SupportTicket? {
        return transaction(DatabaseFactory.getMainDatabase()) {
            val ticket = getTicket(ticketId) ?: return@transaction null
            val now = Instant.now().toString()

            // Создаем запись в истории обновлений
            val update = TicketUpdate(
                timestamp = now,
                updateType = "info_added",
                description = additionalInfo
            )

            val updatedHistory = ticket.updateHistory + update
            val updatedHistoryJson = json.encodeToString(updatedHistory)

            Tickets.update({ Tickets.id eq ticketId }) {
                it[updatedAt] = now
                it[updateHistory] = updatedHistoryJson
            }

            logger.info("Added info to ticket $ticketId: $additionalInfo")
            getTicket(ticketId)
        }
    }

    /**
     * Обновляет тикет
     */
    fun updateTicket(ticketId: String, request: UpdateTicketRequest): TicketUpdateResult? {
        return transaction(DatabaseFactory.getMainDatabase()) {
            // Получаем старый тикет для сравнения
            val oldTicket = getTicket(ticketId) ?: return@transaction null
            val oldStatus = oldTicket.status

            val now = Instant.now().toString()

            Tickets.update({ Tickets.id eq ticketId }) { stmt ->
                stmt[updatedAt] = now

                request.status?.let { newStatus ->
                    stmt[status] = newStatus.name

                    if (newStatus == TicketStatus.RESOLVED) {
                        stmt[resolvedAt] = now
                    }

                    if (newStatus == TicketStatus.CLOSED) {
                        stmt[closedAt] = now
                    }
                }

                request.priority?.let { newPriority ->
                    stmt[priority] = newPriority.name
                }

                request.assignedTo?.let { assignee ->
                    stmt[assignedTo] = assignee
                }

                request.category?.let { cat ->
                    stmt[category] = cat
                }

                request.tags?.let { tagList ->
                    stmt[tags] = json.encodeToString(tagList)
                }
            }

            val updatedTicket = getTicket(ticketId)
            val newStatus = request.status ?: oldStatus

            // Автоматически удаляем закрытые тикеты
            if (request.status == TicketStatus.CLOSED) {
                logger.info("Auto-deleting closed ticket: $ticketId")
                deleteTicket(ticketId)
                return@transaction TicketUpdateResult(
                    ticket = null,
                    oldStatus = oldStatus,
                    newStatus = newStatus,
                    wasDeleted = true
                )
            }

            TicketUpdateResult(
                ticket = updatedTicket,
                oldStatus = oldStatus,
                newStatus = newStatus,
                wasDeleted = false
            )
        }
    }

    /**
     * Удаляет тикет
     */
    fun deleteTicket(ticketId: String): Boolean {
        return try {
            transaction(DatabaseFactory.getMainDatabase()) {
                Tickets.deleteWhere { Tickets.id eq ticketId }
                true
            }
        } catch (e: Exception) {
            logger.error("Error deleting ticket: ${e.message}", e)
            false
        }
    }

    /**
     * Конвертирует строку БД в объект SupportTicket
     */
    private fun rowToTicket(row: ResultRow): SupportTicket {
        val tagsJson = row[Tickets.tags]
        val tagsList = try {
            json.decodeFromString<List<String>>(tagsJson)
        } catch (e: Exception) {
            emptyList()
        }

        val updateHistoryJson = row[Tickets.updateHistory]
        val updateHistoryList = try {
            json.decodeFromString<List<TicketUpdate>>(updateHistoryJson)
        } catch (e: Exception) {
            emptyList()
        }

        return SupportTicket(
            id = row[Tickets.id],
            sessionId = row[Tickets.sessionId],
            title = row[Tickets.title],
            description = row[Tickets.description],
            status = TicketStatus.valueOf(row[Tickets.status]),
            priority = TicketPriority.valueOf(row[Tickets.priority]),
            category = row[Tickets.category],
            createdAt = row[Tickets.createdAt],
            updatedAt = row[Tickets.updatedAt],
            resolvedAt = row[Tickets.resolvedAt],
            closedAt = row[Tickets.closedAt],
            assignedTo = row[Tickets.assignedTo],
            tags = tagsList,
            autoCreated = row[Tickets.autoCreated],
            updateHistory = updateHistoryList
        )
    }
}
