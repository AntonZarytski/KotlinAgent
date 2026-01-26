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
        val possiblePaths = listOfNotNull(
            ".",                    // Текущая директория
            "..",                   // Родительская директория (для запуска из app/)
            System.getProperty("user.dir"),  // Рабочая директория JVM
            System.getenv("PWD")    // Текущая директория shell
        ).distinct()

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

    /**
     * Опциональный API ключ Anthropic Claude.
     * Возвращает null если ключ не найден.
     */
    val anthropicApiKeyOrNull: String? by lazy {
        val key = getEnv("ANTHROPIC_API_KEY")
        if (!key.isNullOrBlank()) {
            logger.info("✅ Anthropic API ключ загружен: ${key.take(10)}...${key.takeLast(4)}")
        } else {
            logger.warn("⚠️ ANTHROPIC_API_KEY не найден - Claude провайдер будет недоступен")
        }
        key
    }

    /**
     * Обязательный API ключ Anthropic Claude.
     * Выбрасывает исключение если ключ не найден.
     */
    val anthropicApiKey: String by lazy {
        val key = anthropicApiKeyOrNull

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

    // === GitHub Integration ===
    val githubToken: String? by lazy {
        getEnv("GITHUB_TOKEN")
    }

    // === Google Play Publisher Integration ===
    val googlePlayServiceAccountPath: String? by lazy {
        getEnv("GOOGLE_PLAY_SERVICE_ACCOUNT_PATH")
    }

    // === LLM Provider Configuration ===
    val llmProvider: String by lazy {
        getEnv("LLM_PROVIDER")?.lowercase() ?: "local"
    }

    val ollamaUrl: String by lazy {
        getEnv("OLLAMA_URL") ?: "http://localhost:11434"
    }

    val ollamaModel: String by lazy {
        getEnv("OLLAMA_MODEL") ?: "qwen2.5-coder:7b-instruct"
    }

    // === Logging Configuration ===
    val enableFileLogging: Boolean by lazy {
        getEnv("ENABLE_FILE_LOGGING")?.toBoolean() ?: true
    }

    // === User Profile Configuration ===
    /**
     * Путь к файлу профиля пользователя (опционально)
     * По умолчанию ищется agent-profile.json в корне проекта
     */
    val userProfilePath: String? by lazy {
        getEnv("AGENT_PROFILE_PATH")
    }

    init {
        logger.info("=== Конфигурация приложения ===")
        logger.info("Порт: $port")
        logger.info("Хост: $host")
        logger.info("База данных: $databasePath")
        logger.info("Статические файлы: $staticFolder")
        logger.info("Google Play Service Account: ${if (googlePlayServiceAccountPath != null) "настроен" else "не настроен"}")
        logger.info("LLM Provider: $llmProvider")
        if (llmProvider == "local") {
            logger.info("Ollama URL: $ollamaUrl")
            logger.info("Ollama Model: $ollamaModel")
        }
        logger.info("================================")

        // Инициализируем профиль пользователя
        try {
            UserProfileConfig.profile
        } catch (e: Exception) {
            logger.warn("Не удалось загрузить профиль пользователя: ${e.message}")
        }
    }
}
