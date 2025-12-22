package com.claude.agent.database

import com.claude.agent.common.database.DocumentChunks
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

    // Сохраняем ссылку на основное подключение
    private lateinit var mainDatabase: Database

    /**
     * Получить подключение к основной базе данных
     * Используется для явного указания БД в transaction {}
     */
    fun getMainDatabase(): Database = mainDatabase

    fun init() {
        val databasePath = AppConfig.databasePath
        val jdbcUrl = "jdbc:sqlite:$databasePath"

        logger.info("Инициализация базы данных: $jdbcUrl")

        // Подключение к SQLite и сохранение ссылки
        mainDatabase = Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )

        // Создаём таблицы и недостающие колонки если не существуют
        transaction {
            logger.info("Проверка и создание таблиц: Sessions, Messages, Reminders")

            // Проверяем существующие таблицы
            val existingTables = mutableListOf<String>()
            listOf("sessions", "messages", "reminders").forEach { tableName ->
                try {
                    exec("SELECT 1 FROM $tableName LIMIT 1") { }
                    existingTables.add(tableName)
                    logger.info("  Таблица '$tableName' уже существует")
                } catch (e: Exception) {
                    logger.info("  Таблица '$tableName' не найдена, будет создана")
                }
            }

            // Создаём недостающие таблицы и колонки
            SchemaUtils.createMissingTablesAndColumns(Sessions, Messages, Reminders)

            // Проверяем что все таблицы созданы
            val tables = listOf("sessions", "messages", "reminders")
            tables.forEach { tableName ->
                try {
                    exec("SELECT 1 FROM $tableName LIMIT 1") { }
                    logger.info("✓ Таблица '$tableName' доступна")
                } catch (e: Exception) {
                    logger.error("✗ Таблица '$tableName' не найдена после создания: ${e.message}")
                    throw e
                }
            }

            logger.info("Таблицы базы данных инициализированы успешно")
        }
    }

    /**
     * Инициализация RAG базы данных (опционально)
     * Используется отдельное подключение для RAG индекса
     *
     * ВАЖНО: Эта функция НЕ должна вызываться в remoteAgentServer,
     * так как Exposed не поддерживает множественные подключения в одном процессе.
     * RAG база данных должна инициализироваться только в модуле :rag
     */
    fun initRagDatabase(ragDatabasePath: String = "rag_index.db") {
        try {
            val jdbcUrl = "jdbc:sqlite:$ragDatabasePath"
            logger.info("Инициализация RAG базы данных: $jdbcUrl")

            // Создаем отдельное подключение для RAG
            // ВНИМАНИЕ: Это подключение станет активным по умолчанию!
            Database.connect(
                url = jdbcUrl,
                driver = "org.sqlite.JDBC"
            )

            // Проверяем наличие таблицы DocumentChunks
            transaction {
                SchemaUtils.createMissingTablesAndColumns(DocumentChunks)
                logger.info("RAG база данных инициализирована успешно")
            }
        } catch (e: Exception) {
            logger.warn("Не удалось инициализировать RAG базу данных: ${e.message}")
        }
    }
}
