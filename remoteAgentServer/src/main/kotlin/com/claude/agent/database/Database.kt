package com.claude.agent.database

import com.claude.agent.config.AppConfig
import com.claude.agent.database.models.Messages
import com.claude.agent.database.models.Reminders
import com.claude.agent.database.models.Sessions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Инициализация базы данных SQLite через Exposed.
 *
 * Аналог database.py из Python-версии.
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init() {
        val databasePath = AppConfig.databasePath
        val jdbcUrl = "jdbc:sqlite:$databasePath"

        logger.info("Инициализация базы данных: $jdbcUrl")

        // Подключение к SQLite
        Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )

        // Создаём таблицы и недостающие колонки если не существуют
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Sessions, Messages, Reminders)
            logger.info("Таблицы базы данных инициализированы успешно")
        }
    }
}
