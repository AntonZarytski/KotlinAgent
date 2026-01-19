package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.GooglePlayService
import com.claude.agent.service.SupportTicketService
import com.claude.agent.service.LocalAgentManager
import com.claude.agent.service.ProjectPathService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * MCP tool для публикации Android приложений в Google Play Console
 */
class GooglePlayPublisherMcp(
    private val googlePlayService: GooglePlayService?,
    private val ticketService: SupportTicketService
) : Mcp.Local {
    var claudeClient: ClaudeClient? = null
    private val logger = LoggerFactory.getLogger(GooglePlayPublisherMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "google_play_publisher",
        second = LocalToolDefinition(
            name = "google_play_publisher",
            ui_description = "Публикация Android приложений в Google Play Console",
            description = "Публикация Android приложений (AAB файлов) в Google Play Console с автоматическим переводом release notes на несколько языков",
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "action" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(listOf(JsonPrimitive("publish_aab"))),
                                    "description" to JsonPrimitive("Действие: publish_aab")
                                )
                            ),
                            "package_name" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Package name приложения (опционально, будет извлечен из AndroidManifest.xml)")
                                )
                            ),
                            "aab_file_path" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Путь к AAB файлу (опционально, будет найден автоматически или собран)")
                                )
                            ),
                            "version_code" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("integer"),
                                    "description" to JsonPrimitive("Версия кода (опционально, будет извлечена из build.gradle)")
                                )
                            ),
                            "version_name" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Версия приложения (опционально, будет извлечена из build.gradle)")
                                )
                            ),
                            "release_notes" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Release notes на английском языке (будут автоматически переведены)")
                                )
                            ),
                            "track" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Канал распространения: internal, alpha, beta, production"),
                                    "default" to JsonPrimitive("internal"),
                                    "enum" to JsonArray(
                                        listOf(
                                            JsonPrimitive("internal"),
                                            JsonPrimitive("alpha"),
                                            JsonPrimitive("beta"),
                                            JsonPrimitive("production")
                                        )
                                    )
                                )
                            ),
                            "session_id" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("ID сессии для создания тикета при ошибке")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(
                        listOf(
                            JsonPrimitive("action"),
                            JsonPrimitive("release_notes")
                        )
                    )
                )
            )
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        return try {
            val action = arguments["action"]?.jsonPrimitive?.content
                ?: return errorJson("Missing 'action' parameter")

            when (action) {
                "publish_aab" -> publishAAB(arguments, sessionId)
                else -> errorJson("Unknown action: $action")
            }
        } catch (e: Exception) {
            logger.error("Error in GooglePlayPublisherMcp: ${e.message}", e)
            errorJson("Error: ${e.message}")
        }
    }

    private suspend fun publishAAB(arguments: JsonObject, serverSessionId: String?): String {
        if (googlePlayService == null) {
            return errorJson("Google Play Publisher не настроен. Установите GOOGLE_PLAY_SERVICE_ACCOUNT_PATH в .env")
        }

        try {
            val sessionId = arguments["session_id"]?.jsonPrimitive?.content ?: serverSessionId
            val releaseNotesEnglish = arguments["release_notes"]?.jsonPrimitive?.content
                ?: return errorJson("Missing release_notes")
            val track = arguments["track"]?.jsonPrimitive?.content ?: "internal"

            // Получаем путь к проекту
            val projectPath = ProjectPathService.getProjectPath(sessionId)
            if (projectPath == null) {
                logger.error("❌ Project path not set")
                return errorJson("Project path not set. Use android_studio set_project_path first")
            }

            logger.info("📂 Project path: $projectPath")

            // Извлекаем метаданные из проекта
            val metadata = extractProjectMetadata(projectPath, sessionId)
            if (metadata == null) {
                return errorJson("Failed to extract project metadata")
            }

            // Используем переданные параметры или извлеченные из проекта
            val packageName = arguments["package_name"]?.jsonPrimitive?.content ?: metadata.packageName
            val versionCode = arguments["version_code"]?.jsonPrimitive?.longOrNull ?: metadata.versionCode
            val versionName = arguments["version_name"]?.jsonPrimitive?.content ?: metadata.versionName
            var aabFilePath = arguments["aab_file_path"]?.jsonPrimitive?.content

            // Если AAB файл не указан, ищем его или собираем
            if (aabFilePath == null) {
                logger.info("🔍 AAB файл не указан, ищем автоматически...")
                aabFilePath = findOrBuildAAB(projectPath, sessionId)
                if (aabFilePath == null) {
                    return errorJson("Failed to find or build AAB file")
                }
            }

            logger.info("🚀 Публикация AAB в Google Play:")
            logger.info("  Package: $packageName")
            logger.info("  Version: $versionName ($versionCode)")
            logger.info("  Track: $track")
            logger.info("  AAB: $aabFilePath")

            // 1. Валидация AAB файла
            logger.info("🔍 Проверка существования AAB файла...")
            val aabFile = File(aabFilePath)

            if (!aabFile.exists()) {
                val errorMsg = "AAB файл не найден: $aabFilePath"
                logger.error("❌ $errorMsg")

                // Создаем тикет при ошибке если указан session_id
                if (sessionId != null) {
                    try {
                        ticketService.createTicket(
                            sessionId = sessionId,
                            title = "AAB файл не найден",
                            description = """
                                Не удалось найти AAB файл для публикации в Google Play:

                                Package: $packageName
                                Version: $versionName ($versionCode)
                                Track: $track
                                AAB File: $aabFilePath

                                Возможные причины:
                                1. Неправильный путь к проекту (используйте set_project_path)
                                2. Сборка не была выполнена (используйте gradle_build с task="bundleRelease")
                                3. Неправильный путь к AAB файлу

                                Рекомендуемые действия:
                                1. Проверьте путь к проекту через get_project_path
                                2. Выполните сборку: gradle_build с task="bundleRelease"
                                3. Проверьте путь к AAB файлу (обычно: <project>/app/release/app-release.aab)
                            """.trimIndent()
                        )
                        logger.info("🎫 Создан support ticket для ошибки валидации AAB файла")
                    } catch (e: Exception) {
                        logger.error("Failed to create support ticket: ${e.message}")
                    }
                }

                return buildJsonObject {
                    put("success", false)
                    put("error", "AAB file not found")
                    put("message", errorMsg)
                    put("aab_file_path", aabFilePath)
                    put("file_exists", false)
                }.toString()
            }

            if (!aabFile.isFile) {
                val errorMsg = "Путь не является файлом: $aabFilePath"
                logger.error("❌ $errorMsg")
                return buildJsonObject {
                    put("success", false)
                    put("error", "Invalid AAB file path")
                    put("message", errorMsg)
                    put("aab_file_path", aabFilePath)
                }.toString()
            }

            val fileSizeMB = aabFile.length() / 1024.0 / 1024.0
            logger.info("✅ AAB файл найден: ${String.format("%.2f", fileSizeMB)} MB")

            // 2. Переводим release notes на несколько языков
            logger.info("🌍 Перевод release notes...")
            val releaseNotesMap = translateReleaseNotes(releaseNotesEnglish)

            // 3. Публикуем в Google Play
            logger.info("📦 Публикуем в Google Play...")
            val result = googlePlayService.publishToGooglePlay(
                packageName = packageName,
                aabFilePath = aabFilePath,
                versionCode = versionCode,
                versionName = versionName,
                releaseNotesMap = releaseNotesMap,
                track = track,
                rolloutPercent = null  // Всегда 100% согласно требованиям
            )

            return if (result.success) {
                logger.info("✅ Публикация успешна!")
                buildJsonObject {
                    put("success", true)
                    put("message", result.message)
                    put("edit_id", result.editId)
                    put("version_code", result.versionCode)
                    put("track", result.track)
                    put("package_name", packageName)
                }.toString()
            } else {
                logger.error("❌ Ошибка публикации: ${result.error}")

                // Создаем тикет при ошибке если указан session_id
                if (sessionId != null) {
                    try {
                        ticketService.createTicket(
                            sessionId = sessionId,
                            title = "Google Play публикация не удалась",
                            description = """
                                Ошибка при публикации в Google Play:

                                Package: $packageName
                                Version: $versionName ($versionCode)
                                Track: $track
                                AAB File: $aabFilePath

                                Error: ${result.error}

                                ${result.message}
                            """.trimIndent()
                        )
                        logger.info("🎫 Создан support ticket для ошибки публикации")
                    } catch (e: Exception) {
                        logger.error("Failed to create support ticket: ${e.message}")
                    }
                }

                buildJsonObject {
                    put("success", false)
                    put("error", result.error ?: "Unknown error")
                    put("message", result.message)
                    put("track", result.track)
                }.toString()
            }

        } catch (e: Exception) {
            logger.error("❌ Unexpected error during AAB publication: ${e.message}", e)

            val sessionId = arguments["session_id"]?.jsonPrimitive?.content
            if (sessionId != null) {
                try {
                    ticketService.createTicket(
                        sessionId = sessionId,
                        title = "Google Play публикация - критическая ошибка",
                        description = """
                            Критическая ошибка при публикации в Google Play:

                            Error: ${e.message}
                            Stack trace: ${e.stackTraceToString()}
                        """.trimIndent()
                    )
                } catch (ticketError: Exception) {
                    logger.error("Failed to create support ticket: ${ticketError.message}")
                }
            }

            return errorJson("Unexpected error: ${e.message}")
        }
    }

    /**
     * Переводит release notes на несколько языков используя Claude API
     */
    private suspend fun translateReleaseNotes(englishText: String): Map<String, String> {
        return try {
            logger.info("🌐 Переводим release notes на языки: en-US, ru-RU, de-DE, fr-FR, es-ES")

            val result = mutableMapOf<String, String>()
            result["en-US"] = englishText

            // Переводим на русский
            val russianText = translateText(englishText, "Russian")
            result["ru-RU"] = russianText

            // Переводим на немецкий
            val germanText = translateText(englishText, "German")
            result["de-DE"] = germanText

            // Переводим на французский
            val frenchText = translateText(englishText, "French")
            result["fr-FR"] = frenchText

            // Переводим на испанский
            val spanishText = translateText(englishText, "Spanish")
            result["es-ES"] = spanishText

            logger.info("✅ Release notes переведены на ${result.size} языков")
            result

        } catch (e: Exception) {
            logger.warn("⚠️ Ошибка перевода release notes, используем только английский: ${e.message}")
            mapOf("en-US" to englishText)
        }
    }

    /**
     * Переводит текст на указанный язык
     */
    private suspend fun translateText(text: String, targetLanguage: String): String {
        return try {
            val prompt = """
                Translate the following app release notes to $targetLanguage.
                Keep the same tone and style. Keep it concise and professional.
                Only return the translation, nothing else.

                Text to translate:
                $text
            """.trimIndent()

            val response = claudeClient?.sendMessage(
                userMessage = prompt,
                maxTokens = 500
            )

            response?.reply?.trim() ?: text

        } catch (e: Exception) {
            logger.warn("Failed to translate to $targetLanguage: ${e.message}")
            text  // Fallback to original text
        }
    }

    /**
     * Data class для метаданных проекта
     */
    private data class ProjectMetadata(
        val packageName: String,
        val versionCode: Long,
        val versionName: String
    )

    /**
     * Извлекает метаданные проекта из AndroidManifest.xml и build.gradle
     */
    private suspend fun extractProjectMetadata(projectPath: String, sessionId: String?): ProjectMetadata? {
        return try {
            logger.info("📋 Извлечение метаданных проекта...")

            // 1. Читаем AndroidManifest.xml для получения package name
            // Используем абсолютный путь, чтобы избежать проблем с путями проекта
            val manifestPath = "$projectPath/app/src/main/AndroidManifest.xml"
            val manifestFile = File(manifestPath)

            if (!manifestFile.exists()) {
                logger.error("❌ AndroidManifest.xml not found at: $manifestPath")
                return null
            }

            val manifestContent = manifestFile.readText()

            // 2. Читаем build.gradle.kts для получения packageName, versionCode и versionName
            val buildGradlePath = "$projectPath/app/build.gradle.kts"
            val buildGradleFile = File(buildGradlePath)

            if (!buildGradleFile.exists()) {
                logger.error("❌ build.gradle.kts not found at: $buildGradlePath")
                return null
            }

            val buildGradleContent = buildGradleFile.readText()

            // Извлекаем packageName (applicationId или namespace), versionCode и versionName
            val packageName = extractPackageNameFromGradle(buildGradleContent)
            val versionCode = extractVersionCode(buildGradleContent)
            val versionName = extractVersionName(buildGradleContent)

            if (packageName == null) {
                logger.error("❌ Failed to extract package name from build.gradle.kts")
                return null
            }

            if (versionCode == null || versionName == null) {
                logger.error("❌ Failed to extract version info from build.gradle.kts")
                return null
            }

            logger.info("✅ Package name: $packageName")
            logger.info("✅ Version: $versionName ($versionCode)")

            ProjectMetadata(
                packageName = packageName,
                versionCode = versionCode,
                versionName = versionName
            )

        } catch (e: Exception) {
            logger.error("❌ Error extracting project metadata: ${e.message}", e)
            null
        }
    }

    /**
     * Извлекает package name из build.gradle.kts
     * Ищет applicationId (приоритет) или namespace
     */
    private fun extractPackageNameFromGradle(buildGradleContent: String): String? {
        // Сначала ищем applicationId (это фактический package name для публикации)
        val applicationIdRegex = """applicationId\s*=\s*"([^"]+)"""".toRegex()
        val applicationId = applicationIdRegex.find(buildGradleContent)?.groupValues?.get(1)
        if (applicationId != null) {
            return applicationId
        }

        // Если applicationId не найден, ищем namespace
        val namespaceRegex = """namespace\s*=\s*"([^"]+)"""".toRegex()
        return namespaceRegex.find(buildGradleContent)?.groupValues?.get(1)
    }

    /**
     * Извлекает versionCode из build.gradle.kts
     */
    private fun extractVersionCode(buildGradleContent: String): Long? {
        // Ищем versionCode = ... или versionCode(...) в build.gradle
        val versionCodeRegex = """versionCode\s*[=(]\s*(\d+)""".toRegex()
        return versionCodeRegex.find(buildGradleContent)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Извлекает versionName из build.gradle.kts
     */
    private fun extractVersionName(buildGradleContent: String): String? {
        // Ищем versionName = "..." или versionName("...") в build.gradle
        val versionNameRegex = """versionName\s*[=(]\s*"([^"]+)"""".toRegex()
        return versionNameRegex.find(buildGradleContent)?.groupValues?.get(1)
    }

    /**
     * Ищет AAB файл или собирает его
     */
    private suspend fun findOrBuildAAB(projectPath: String, sessionId: String?): String? {
        return try {
            // Стандартный путь к AAB файлу
            val standardAabPath = "$projectPath/app/release/app-release.aab"
            val aabFile = File(standardAabPath)

            if (aabFile.exists()) {
                logger.info("✅ AAB файл найден: $standardAabPath")
                return standardAabPath
            }

            logger.info("⚠️ AAB файл не найден, запускаем сборку...")

            // Запускаем сборку bundleRelease
            val buildResult = LocalAgentManager.executeOnLocalAgent(
                toolName = "android_studio",
                arguments = buildJsonObject {
                    put("action", "gradle_build")
                    put("task", "bundleRelease")
                    sessionId?.let { put("session_id", it) }
                },
                timeoutMs = 300_000L // 5 минут на сборку
            )

            val buildJson = Json.parseToJsonElement(buildResult).jsonObject
            val success = buildJson["success"]?.jsonPrimitive?.booleanOrNull ?: false

            if (!success) {
                logger.error("❌ Сборка AAB не удалась")
                return null
            }

            logger.info("✅ Сборка AAB завершена успешно")

            // Проверяем, что файл появился
            if (aabFile.exists()) {
                logger.info("✅ AAB файл создан: $standardAabPath")
                return standardAabPath
            } else {
                logger.error("❌ AAB файл не найден после сборки")
                return null
            }

        } catch (e: Exception) {
            logger.error("❌ Error finding or building AAB: ${e.message}", e)
            null
        }
    }

    override fun errorJson(message: String): String {
        return buildJsonObject {
            put("error", message)
            put("success", false)
        }.toString()
    }
}
