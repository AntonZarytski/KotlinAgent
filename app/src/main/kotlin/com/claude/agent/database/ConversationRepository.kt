package com.claude.agent.database

import com.claude.agent.database.models.Messages
import com.claude.agent.database.models.Reminders
import com.claude.agent.database.models.Sessions
import com.claude.agent.models.Message
import com.claude.agent.models.Reminder
import com.claude.agent.models.SessionInfo
import com.claude.agent.models.SessionStats
import com.claude.agent.models.TokenUsage
import java.time.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Repository для работы с сессиями и сообщениями.
 *
 * Аналог ConversationDatabase из database.py (Python).
 */
class ConversationRepository {
    private val logger = LoggerFactory.getLogger(ConversationRepository::class.java)

    // === Сессии ===

    fun createSession(sessionId: String, title: String = "Новый диалог"): Boolean {
        return try {
            transaction {
                val now = Instant.now().toString()
                Sessions.insert {
                    it[id] = sessionId
                    it[Sessions.title] = title
                    it[createdAt] = now
                    it[lastUpdated] = now
                }
                logger.info("Создана новая сессия: $sessionId")
                true
            }
        } catch (e: Exception) {
            logger.error("Ошибка создания сессии: ${e.message}")
            false
        }
    }

    fun getAllSessions(): List<SessionInfo> {
        return transaction {
            Sessions.selectAll()
                .orderBy(Sessions.lastUpdated, SortOrder.DESC)
                .map { row ->
                    SessionInfo(
                        id = row[Sessions.id],
                        title = row[Sessions.title],
                        created_at = row[Sessions.createdAt],
                        last_updated = row[Sessions.lastUpdated]
                    )
                }
        }
    }

    fun deleteSession(sessionId: String): Boolean {
        return try {
            transaction {
                // CASCADE удалит все сообщения автоматически
                Sessions.deleteWhere { Sessions.id eq sessionId }
                logger.info("Сессия удалена: $sessionId")
                true
            }
        } catch (e: Exception) {
            logger.error("Ошибка удаления сессии: ${e.message}")
            false
        }
    }

    // === Сообщения ===

    fun saveMessage(
        sessionId: String,
        role: String,
        content: String,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        read: Boolean = false
    ): Message? {
        return try {
            transaction {
                val now = Instant.now().toString()

                // Сохраняем сообщение
                val insertedId = Messages.insert {
                    it[Messages.sessionId] = sessionId
                    it[Messages.role] = role
                    it[Messages.content] = content
                    it[timestamp] = now
                    it[Messages.inputTokens] = inputTokens
                    it[Messages.outputTokens] = outputTokens
                    it[Messages.read] = read
                } get Messages.id

                // Обновляем last_updated у сессии
                Sessions.update({ Sessions.id eq sessionId }) {
                    it[lastUpdated] = now
                }

                logger.debug("Сообщение сохранено: session=$sessionId, role=$role, tokens=$inputTokens/$outputTokens, read=$read")

                // Возвращаем сохраненное сообщение
                Message(
                    role = role,
                    content = content,
                    usage = if (inputTokens != null || outputTokens != null) {
                        TokenUsage(inputTokens, outputTokens)
                    } else null,
                    timestamp = now,
                    read = read
                )
            }
        } catch (e: Exception) {
            logger.error("Ошибка сохранения сообщения: ${e.message}")
            null
        }
    }

    fun getSessionHistory(sessionId: String, limit: Int? = null): List<Message> {
        return transaction {
            var query = Messages.selectAll().where { Messages.sessionId eq sessionId }
                .orderBy(Messages.timestamp, SortOrder.ASC)

            if (limit != null) {
                query = query.limit(limit)
            }

            query.map { row ->
                val inputTokens = row[Messages.inputTokens]
                val outputTokens = row[Messages.outputTokens]

                Message(
                    role = row[Messages.role],
                    content = row[Messages.content],
                    usage = if (inputTokens != null || outputTokens != null) {
                        TokenUsage(inputTokens, outputTokens)
                    } else null,
                    timestamp = row[Messages.timestamp],
                    read = row[Messages.read]
                )
            }
        }
    }

    fun getSessionStats(sessionId: String): SessionStats {
        return transaction {
            val messages = Messages.selectAll().where { Messages.sessionId eq sessionId }
                .map { it[Messages.role] }

            val totalMessages = messages.size
            val userMessages = messages.count { it == "user" }
            val assistantMessages = messages.count { it == "assistant" }

            SessionStats(
                total_messages = totalMessages,
                user_messages = userMessages,
                assistant_messages = assistantMessages
            )
        }
    }

    // === Работа с непрочитанными сообщениями ===

    fun getUnreadCount(sessionId: String): Int {
        return transaction {
            Messages.selectAll()
                .where { (Messages.sessionId eq sessionId) and (Messages.read eq false) }
                .count()
                .toInt()
        }
    }

    fun markMessagesAsRead(sessionId: String): Int {
        return transaction {
            Messages.update({ (Messages.sessionId eq sessionId) and (Messages.read eq false) }) {
                it[read] = true
            }
        }
    }

    fun getUnreadCountsForAllSessions(): Map<String, Int> {
        return transaction {
            Messages.selectAll()
                .where { Messages.read eq false }
                .groupBy { it[Messages.sessionId] }
                .mapValues { (_, messages) -> messages.size }
        }
    }

    fun getReminders(): List<Reminder> {
        return transaction {
            Reminders.selectAll().map { row ->
                Reminder(
                    id = row[Reminders.id],
                    sessionId = row[Reminders.sessionId],
                    text = row[Reminders.text],
                    due_at = row[Reminders.due_at],
                    created_at = row[Reminders.created_at],
                    updated_at = row[Reminders.updated_at],
                    done = row[Reminders.done],
                    notified = row[Reminders.notified],
                    recurrenceType = row[Reminders.recurrenceType],
                    recurrenceInterval = row[Reminders.recurrenceInterval],
                    recurrenceEndDate = row[Reminders.recurrenceEndDate],
                    taskType = row[Reminders.taskType],
                    taskContext = row[Reminders.taskContext]
                )
            }
        }
    }

    fun createReminder(reminder: Reminder): Boolean {
        return try {
            transaction {
                val now = Instant.now().toString()
                Reminders.insert {
                    it[id] = reminder.id
                    it[sessionId] = reminder.sessionId
                    it[text] = reminder.text
                    it[due_at] = reminder.due_at
                    it[created_at] = now
                    it[updated_at] = now
                    it[done] = reminder.done
                    it[notified] = reminder.notified
                    it[recurrenceType] = reminder.recurrenceType
                    it[recurrenceInterval] = reminder.recurrenceInterval
                    it[recurrenceEndDate] = reminder.recurrenceEndDate
                    it[taskType] = reminder.taskType
                    it[taskContext] = reminder.taskContext
                }
                logger.info("Создан новый reminder: ${reminder.text} (type: ${reminder.taskType}, recurrence: ${reminder.recurrenceType})")
                true
            }
        } catch (e: Exception) {
            logger.error("Ошибка создания reminder: ${e.message}")
            false
        }
    }

    fun deleteReminder(reminderId: String): Boolean {
        return try {
            transaction {
                Reminders.deleteWhere { Reminders.id eq reminderId }
                logger.info("Reminder удален: $reminderId")
                true
            }
        } catch (e: Exception) {
            logger.error("Ошибка удаления reminder: ${e.message}")
            false
        }
    }

    fun checkDueReminders(): List<Reminder> {
        val now = Instant.now()
        return transaction {
            Reminders
                .selectAll().where { (Reminders.done eq false) and (Reminders.notified eq false) }
                .mapNotNull {
                    val reminder = Reminder(
                        id = it[Reminders.id],
                        sessionId = it[Reminders.sessionId],
                        text = it[Reminders.text],
                        due_at = it[Reminders.due_at],
                        created_at = it[Reminders.created_at],
                        updated_at = it[Reminders.updated_at],
                        done = it[Reminders.done],
                        notified = it[Reminders.notified],
                        recurrenceType = it[Reminders.recurrenceType],
                        recurrenceInterval = it[Reminders.recurrenceInterval],
                        recurrenceEndDate = it[Reminders.recurrenceEndDate],
                        taskType = it[Reminders.taskType],
                        taskContext = it[Reminders.taskContext]
                    )

                    // Парсим due_at и сравниваем с текущим временем
                    try {
                        val dueAt = Instant.parse(reminder.due_at)
                        if (dueAt.isBefore(now) || dueAt == now) {
                            logger.info("Напоминание просрочено: ${reminder.text}, due_at: ${reminder.due_at}, now: $now")
                            reminder
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        logger.error("Ошибка парсинга due_at для напоминания ${reminder.id}: ${e.message}")
                        null
                    }
                }
        }
    }

    fun markNotified(id: String) {
        transaction {
            logger.info("Напоминание отмечено как уведомленное: $id")
            Reminders.update({ Reminders.id eq id }) {
                it[notified] = true
                it[updated_at] = Instant.now().toString()
            }
        }
    }
}
