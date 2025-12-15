package com.claude.agent.database.models

import org.jetbrains.exposed.sql.Table

/**
 * Определение таблиц для Exposed ORM.
 *
 * Аналог структуры БД из database.py (Python).
 */

// Таблица сессий
object Sessions : Table("sessions") {
    val id = varchar("id", 255).uniqueIndex()          // PRIMARY KEY
    val title = varchar("title", 500)
    val createdAt = varchar("created_at", 50)
    val lastUpdated = varchar("last_updated", 50)

    override val primaryKey = PrimaryKey(id)
}

// Таблица сообщений
object Messages : Table("messages") {
    val id = integer("id").autoIncrement()              // PRIMARY KEY
    val sessionId = varchar("session_id", 255).references(Sessions.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE)
    val role = varchar("role", 20)                      // "user" | "assistant"
    val content = text("content")
    val timestamp = varchar("timestamp", 50)
    val inputTokens = integer("input_tokens").nullable()
    val outputTokens = integer("output_tokens").nullable()

    override val primaryKey = PrimaryKey(id)
}
