package com.claude.agent.llm

import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.models.UserLocation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.Json

/**
 * Утилита для получения контекста выбранных файлов
 * Используется всеми LLM провайдерами для единообразной обработки файлов
 */
object FileContextRetriever {
    private val logger = LoggerFactory.getLogger(FileContextRetriever::class.java)

    /**
     * Разрешённые расширения для чтения
     */
    private val allowedExtensions = setOf(
        "kt", "java", "kts", "gradle", "sh", "md", "aidl",
        "js", "html", "json", "log", "yml", "yaml", "xml", "txt"
    )

    /**
     * Расширения бинарных файлов
     */
    private val binaryExtensions = setOf(
        "aab", "apk", "jar", "aar", "so", "a", "o",
        "zip", "tar", "gz", "7z", "rar",
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico",
        "mp3", "mp4", "avi", "mov", "wav", "flac",
        "pdf", "doc", "docx", "xls", "xlsx",
        "class", "dex", "bin", "exe", "dll"
    )

    /**
     * Максимальное количество файлов для обработки
     */
    private const val MAX_FILES = 20

    /**
     * Получает контекст выбранных файлов
     *
     * @param selectedFiles Список абсолютных путей к файлам
     * @param sessionId ID сессии
     * @param mcpTools Сервис для вызова MCP инструментов
     * @return Отформатированный контекст с содержимым файлов
     */
    suspend fun retrieveFileContext(
        selectedFiles: List<String>,
        sessionId: String?,
        mcpTools: MCPTools
    ): String? {
        if (selectedFiles.isEmpty()) {
            return null
        }

        return try {
            logger.info("📂 Processing ${selectedFiles.size} selected files...")
            logger.debug("📋 Selected files: $selectedFiles")

            val fileContents = mutableListOf<String>()
            var processedCount = 0

            // Раскрываем список файлов, обрабатывая папки
            val expandedFiles = mutableListOf<String>()
            for (filePath in selectedFiles) {
                logger.debug("🔍 Processing path: $filePath")
                val fileName = filePath.substringAfterLast('/')
                val hasExtension = fileName.contains('.')

                if (!hasExtension) {
                    // Это папка - читаем все файлы первого уровня
                    logger.info("📁 Expanding directory: $filePath")
                    try {
                        val result = mcpTools.callLocalTool(
                            toolName = "android_studio_mcp",
                            arguments = buildJsonObject {
                                put("action", "browse_files")
                                put("directory_path", filePath)
                            },
                            clientIp = null,
                            userLocation = null,
                            sessionId = sessionId
                        )

                        val resultJson = Json.parseToJsonElement(result).jsonObject
                        val files = resultJson["files"]?.jsonArray

                        files?.forEach { fileElement ->
                            val fileObj = fileElement.jsonObject
                            val fileType = fileObj["type"]?.jsonPrimitive?.content
                            val name = fileObj["name"]?.jsonPrimitive?.content

                            if (fileType == "file" && name != null) {
                                val fullPath = "$filePath/$name"
                                expandedFiles.add(fullPath)
                                logger.info("  📄 Found file: $fullPath")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("❌ Error expanding directory $filePath: ${e.message}")
                    }
                } else {
                    expandedFiles.add(filePath)
                }
            }

            logger.info("📊 Total files after expansion: ${expandedFiles.size}")

            // Обрабатываем файлы с учетом лимита
            for (filePath in expandedFiles) {
                if (processedCount >= MAX_FILES) {
                    logger.warn("⚠️ Reached maximum file limit ($MAX_FILES), skipping remaining files")
                    fileContents.add("""
                        |⚠️ Warning: Maximum file limit ($MAX_FILES) reached. ${expandedFiles.size - processedCount} files were skipped.
                    """.trimMargin())
                    break
                }

                try {
                    val fileName = filePath.substringAfterLast('/')
                    val extension = fileName.substringAfterLast('.', "").lowercase()
                    val hasExtension = fileName.contains('.')

                    // Проверяем расширение
                    if (!hasExtension) {
                        logger.info("⏭️ Skipping (no extension): $filePath")
                        continue
                    }

                    if (extension !in allowedExtensions) {
                        logger.info("⏭️ Skipping (not allowed extension): $filePath (.$extension)")
                        continue
                    }

                    val isBinary = extension in binaryExtensions
                    if (isBinary) {
                        logger.info("⏭️ Skipping (binary): $filePath (.$extension)")
                        continue
                    }

                    // Читаем содержимое файла
                    val result = mcpTools.callLocalTool(
                        toolName = "android_studio_mcp",
                        arguments = buildJsonObject {
                            put("action", "read_file")
                            put("file_path", filePath)
                        },
                        clientIp = null,
                        userLocation = null,
                        sessionId = sessionId
                    )

                    val resultJson = Json.parseToJsonElement(result).jsonObject
                    val content = resultJson["content"]?.jsonPrimitive?.content

                    if (content != null) {
                        fileContents.add("""
                            |File: $filePath
                            |```
                            |$content
                            |```
                        """.trimMargin())
                        logger.info("✅ Read text file: $filePath (${content.length} chars)")
                        processedCount++
                    } else {
                        logger.warn("⚠️ Failed to read file: $filePath")
                    }
                } catch (e: Exception) {
                    logger.error("❌ Error processing file $filePath: ${e.message}")
                }
            }

            if (fileContents.isEmpty()) {
                return null
            }

            """
            |<selected_files>
            |The user has selected the following files for context:
            |
            |${fileContents.joinToString("\n\n")}
            |</selected_files>
            """.trimMargin()

        } catch (e: Exception) {
            logger.error("Failed to retrieve file context: ${e.message}", e)
            null
        }
    }
}

