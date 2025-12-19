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
    val read = bool("read").default(false)              // Прочитано ли сообщение

    override val primaryKey = PrimaryKey(id)
}

object Reminders : Table("reminders") {
    val id = varchar("id", 255).uniqueIndex()
    val sessionId = varchar("session_id", 255).nullable()
    val text = varchar("text", 500)
    val due_at = varchar("due_at", 50)
    val created_at = varchar("created_at", 50)
    val updated_at = varchar("updated_at", 50)
    val done = bool("done")
    val notified = bool("notified").default(false)
    // Recurring reminder fields
    val recurrenceType = varchar("recurrence_type", 20).default("none") // none, minutely, hourly, daily, weekly, monthly
    val recurrenceInterval = integer("recurrence_interval").default(1) // every N minutes/hours/days/weeks/months
    val recurrenceEndDate = varchar("recurrence_end_date", 50).nullable() // when to stop recurring (ISO 8601)
    // AI task fields
    val taskType = varchar("task_type", 20).default("reminder") // reminder, ai_response, mcp_tool
    val taskContext = text("task_context").nullable() // JSON with task details (user request, tool name, parameters, etc.)
    override val primaryKey = PrimaryKey(id)
}
