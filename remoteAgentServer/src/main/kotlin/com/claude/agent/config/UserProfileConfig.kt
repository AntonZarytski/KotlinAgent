package com.claude.agent.config

import com.claude.agent.common.models.UserProfile
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Конфигурация профиля пользователя для персонализации агента.
 * 
 * Загружает профиль из agent-profile.json при инициализации.
 * Если файл не найден, используется профиль по умолчанию.
 * 
 * Профиль применяется как к локальному, так и к облачному агенту.
 */
object UserProfileConfig {
    private val logger = LoggerFactory.getLogger(UserProfileConfig::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }
    
    /**
     * Имя файла профиля
     */
    private const val PROFILE_FILENAME = "agent-profile.json"
    
    /**
     * Загруженный профиль пользователя
     */
    val profile: UserProfile by lazy {
        loadProfile()
    }
    
    /**
     * Загружает профиль из файла или возвращает профиль по умолчанию
     */
    private fun loadProfile(): UserProfile {
        val possiblePaths = listOfNotNull(
            ".",                                    // Текущая директория
            "..",                                   // Родительская директория
            System.getProperty("user.dir"),         // Рабочая директория JVM
            System.getenv("PWD"),                   // Текущая директория shell
            System.getenv("AGENT_PROFILE_PATH")     // Кастомный путь из переменной окружения
        ).distinct()
        
        logger.info("🔍 Поиск профиля пользователя ($PROFILE_FILENAME)...")
        
        for (basePath in possiblePaths) {
            try {
                val profileFile = File(basePath, PROFILE_FILENAME)
                if (profileFile.exists() && profileFile.canRead()) {
                    val content = profileFile.readText()
                    val loadedProfile = json.decodeFromString<UserProfile>(content)
                    
                    logger.info("✅ Профиль пользователя загружен из: ${profileFile.absolutePath}")
                    logger.info("👤 Пользователь: ${loadedProfile.user.name}")
                    logger.info("🌍 Язык: ${loadedProfile.user.language}")
                    logger.info("💼 Роль: ${loadedProfile.user.role ?: "не указана"}")
                    logger.info("🎨 Стиль общения: ${loadedProfile.preferences.communicationStyle}")
                    
                    if (loadedProfile.customInstructions.isNotEmpty()) {
                        logger.info("📝 Кастомных инструкций: ${loadedProfile.customInstructions.size}")
                    }
                    
                    return loadedProfile
                }
            } catch (e: Exception) {
                logger.debug("Не удалось загрузить профиль из $basePath: ${e.message}")
            }
        }
        
        logger.warn("⚠️ Профиль пользователя не найден, используется профиль по умолчанию")
        logger.info("💡 Создайте файл $PROFILE_FILENAME в корне проекта для персонализации")
        logger.info("📄 Пример: agent-profile.example.json")
        
        return UserProfile()
    }
    
    /**
     * Сохраняет профиль в файл
     * 
     * @param profile Профиль для сохранения
     * @param path Путь к файлу (по умолчанию - текущая директория)
     */
    fun saveProfile(profile: UserProfile, path: String = "."): Boolean {
        return try {
            val profileFile = File(path, PROFILE_FILENAME)
            val content = json.encodeToString(UserProfile.serializer(), profile)
            profileFile.writeText(content)
            
            logger.info("✅ Профиль сохранен в: ${profileFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("❌ Ошибка сохранения профиля: ${e.message}", e)
            false
        }
    }
    
    /**
     * Создает пример профиля для документации
     */
    fun createExampleProfile(path: String = "."): Boolean {
        return try {
            val exampleFile = File(path, "agent-profile.example.json")
            val exampleProfile = UserProfile(
                user = com.claude.agent.common.models.UserInfo(
                    name = "Антон",
                    role = "Senior Android Developer",
                    timezone = "Europe/Warsaw",
                    language = "ru"
                ),
                preferences = com.claude.agent.common.models.UserPreferences(
                    communicationStyle = "direct",
                    codeStyle = "kotlin_official",
                    verbosity = "medium",
                    emojiUsage = true,
                    codeExampleFormat = "separate",
                    explainCode = true
                ),
                workContext = com.claude.agent.common.models.WorkContext(
                    primaryProjects = listOf("KotlinAgent", "SecretChat"),
                    techStack = listOf("Kotlin", "Compose", "Ktor", "Android"),
                    favoriteTools = listOf("Android Studio", "Git", "Gradle"),
                    currentFocus = "Разработка AI агента с персонализацией",
                    expertiseLevel = "advanced"
                ),
                habits = com.claude.agent.common.models.UserHabits(
                    workHours = "10:00-19:00",
                    breakReminders = true,
                    taskManagementStyle = "agile",
                    preferredSessionLength = 90,
                    peakProductivityTime = "morning"
                ),
                customInstructions = listOf(
                    "Всегда отвечай на русском языке",
                    "Предпочитаю краткие ответы с примерами кода",
                    "Использую Kotlin Coroutines вместо RxJava",
                    "Следуй официальному Kotlin code style",
                    "Используй Jetpack Compose для UI"
                )
            )
            
            val content = json.encodeToString(UserProfile.serializer(), exampleProfile)
            exampleFile.writeText(content)
            
            logger.info("✅ Пример профиля создан: ${exampleFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("❌ Ошибка создания примера профиля: ${e.message}", e)
            false
        }
    }
    
    init {
        logger.info("=== Инициализация профиля пользователя ===")
        // Профиль загружается лениво при первом обращении к profile
    }
}

