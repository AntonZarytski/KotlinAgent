package com.claude.agent.database

import com.claude.agent.database.models.Messages
import com.claude.agent.database.models.Sessions
import com.claude.agent.models.Message
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
        outputTokens: Int? = null
    ): Boolean {
        return try {
            transaction {
                val now = Instant.now().toString()

                // Сохраняем сообщение
                Messages.insert {
                    it[Messages.sessionId] = sessionId
                    it[Messages.role] = role
                    it[Messages.content] = content
                    it[timestamp] = now
                    it[Messages.inputTokens] = inputTokens
                    it[Messages.outputTokens] = outputTokens
                }

                // Обновляем last_updated у сессии
                Sessions.update({ Sessions.id eq sessionId }) {
                    it[lastUpdated] = now
                }

                logger.debug("Сообщение сохранено: session=$sessionId, role=$role, tokens=$inputTokens/$outputTokens")
                true
            }
        } catch (e: Exception) {
            logger.error("Ошибка сохранения сообщения: ${e.message}")
            false
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
                    } else null
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
}
