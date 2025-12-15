package com.claude.agent.config

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory

/**
 * Конфигурация приложения.
 *
 * Аналог загрузки переменных окружения из .env в Python (load_dotenv()).
 * Использует dotenv-kotlin для загрузки из .env файла.
 */
object AppConfig {
    private val logger = LoggerFactory.getLogger(AppConfig::class.java)

    // Загружаем .env файл (если существует)
    private val dotenv: Dotenv? = try {
        // Ищем .env в нескольких местах
        val possiblePaths = listOf(
            ".",                    // Текущая директория
            "..",                   // Родительская директория (для запуска из app/)
            System.getProperty("user.dir"),  // Рабочая директория JVM
            System.getenv("PWD")    // Текущая директория shell
        ).filterNotNull().distinct()

        logger.info("Поиск .env файла в: $possiblePaths")
        logger.info("Текущая рабочая директория: ${System.getProperty("user.dir")}")

        var loadedDotenv: Dotenv? = null
        for (path in possiblePaths) {
            try {
                val testDotenv = dotenv {
                    directory = path
                    ignoreIfMissing = true
                    ignoreIfMalformed = true
                }
                if (testDotenv["ANTHROPIC_API_KEY"] != null) {
                    logger.info(".env файл найден в: $path")
                    loadedDotenv = testDotenv
                    break
                }
            } catch (e: Exception) {
                // Продолжаем поиск
            }
        }
        loadedDotenv
    } catch (e: Exception) {
        logger.warn(".env файл не загружен: ${e.message}")
        null
    }

    /**
     * Получить значение переменной окружения.
     * Приоритет: системные переменные > .env файл
     */
    private fun getEnv(key: String): String? {
        return System.getenv(key) ?: dotenv?.get(key)
    }

    // === API ключи ===
    val anthropicApiKey: String by lazy {
        val key = getEnv("ANTHROPIC_API_KEY")

        // Для отладки
        if (key.isNullOrBlank()) {
            logger.error("ANTHROPIC_API_KEY не найден!")
            logger.error("Проверьте:")
            logger.error("1. Создан ли файл .env в корне проекта: ${System.getProperty("user.dir")}/.env")
            logger.error("2. Содержит ли он строку: ANTHROPIC_API_KEY=sk-ant-...")
            logger.error("3. Системная переменная: ${System.getenv("ANTHROPIC_API_KEY")}")
        }

        require(!key.isNullOrBlank()) {
            ErrorMessages.API_KEY_NOT_FOUND
        }
        logger.info("API ключ загружен: ${key.take(10)}...${key.takeLast(4)}")
        key
    }

    // === Настройки сервера ===
    val port: Int by lazy {
        getEnv("PORT")?.toIntOrNull() ?: ServerConfig.DEFAULT_PORT
    }

    val host: String by lazy {
        getEnv("HOST") ?: ServerConfig.DEFAULT_HOST
    }

    // === Пути к файлам ===
    val databasePath: String by lazy {
        getEnv("DATABASE_PATH") ?: "conversations.db"
    }

    val staticFolder: String by lazy {
        getEnv("STATIC_FOLDER") ?: "ui"
    }

    init {
        logger.info("=== Конфигурация приложения ===")
        logger.info("Порт: $port")
        logger.info("Хост: $host")
        logger.info("База данных: $databasePath")
        logger.info("Статические файлы: $staticFolder")
        logger.info("================================")
    }
}
