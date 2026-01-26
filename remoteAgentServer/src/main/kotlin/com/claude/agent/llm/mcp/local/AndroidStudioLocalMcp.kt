package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.ANDROID_STUDIO_MCP
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.LocalAgentManager
import com.claude.agent.service.ProjectPathService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Local MCP tool for controlling Android Studio emulator via connected local agent.
 * This tool executes commands on a local machine that has Android SDK installed.
 */
class AndroidStudioLocalMcp : Mcp.Local {
    private val logger = LoggerFactory.getLogger(AndroidStudioLocalMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = ANDROID_STUDIO_MCP,
        second = LocalToolDefinition(
            name = ANDROID_STUDIO_MCP,
            ui_description = "Этот инструмент управляет Android Studio, Android Emulator, ADB, Gradle и ЛОКАЛЬНОЙ ФАЙЛОВОЙ СИСТЕМОЙ на ПОДКЛЮЧЁННОМ КОМПЬЮТЕРЕ РАЗРАБОТЧИКА.",
            description = """
            Управление Android разработкой на ЛОКАЛЬНОЙ машине разработчика.

            ПРАВИЛА:
            - Файлы доступны ТОЛЬКО через этот инструмент
            - Пути относительны к корню проекта
            - Используй read_file для чтения, browse_files для просмотра

            ОСНОВНЫЕ ДЕЙСТВИЯ:

            Проект: set_project_path, get_project_path
            Файлы: browse_files, read_file, find_files
            Сборка: gradle_build, gradle_install_run
            Эмулятор: list_emulators, start_emulator, stop_emulator
            Запуск: install_apk, run_app
            Логи: logcat, logcat_clear
            ADB: adb_shell, screenshot

            ТИПИЧНЫЙ WORKFLOW:
            1. browse_files ("") → просмотр корня проекта
            2. read_file ("AndroidManifest.xml") → узнать package_name
            3. gradle_build → собрать APK
            4. list_emulators → найти AVD
            5. start_emulator (avd_name) → запустить эмулятор
            6. gradle_install_run → установить и запустить

            ВАЖНО: Всегда читай файлы перед использованием, не предполагай содержимое.
            """.trimIndent(),
            enabled = true,
            input_schema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("description", "Action to perform")
                        putJsonArray("enum") {
                            add("set_project_path")
                            add("get_project_path")
                            add("get_file_tree")
                            add("start_emulator")
                            add("stop_emulator")
                            add("list_emulators")
                            add("install_apk")
                            add("run_app")
                            add("adb_shell")
                            add("screenshot")
                            add("gradle_build")
                            add("gradle_install_run")
                            add("logcat")
                            add("logcat_clear")
                            add("browse_files")
                            add("read_file")
                            add("read_file_lines")
                            add("find_files")
                            add("save_log")
                            add("read_app_log")
                        }
                    }
                    putJsonObject("project_path") {
                        put("type", "string")
                        put("description", "Absolute path to Android project directory (for set_project_path action)")
                    }
                    putJsonObject("avd_name") {
                        put("type", "string")
                        put("description", "Name of the Android Virtual Device (AVD) to start")
                    }
                    putJsonObject("apk_path") {
                        put("type", "string")
                        put("description", "Path to the APK file to install")
                    }
                    putJsonObject("package_name") {
                        put("type", "string")
                        put("description", "Android package name to launch (e.g., com.example.app)")
                    }
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "Shell command to execute via adb shell")
                    }
                    putJsonObject("build_variant") {
                        put("type", "string")
                        put("description", "Gradle build variant (e.g., debug, release)")
                        put("default", "debug")
                    }
                    putJsonObject("filter_tag") {
                        put("type", "string")
                        put("description", "Logcat filter tag (e.g., MyApp)")
                    }
                    putJsonObject("filter_package") {
                        put("type", "string")
                        put("description", "Logcat filter by package name")
                    }
                    putJsonObject("log_level") {
                        put("type", "string")
                        put("description", "Logcat level: V (Verbose), D (Debug), I (Info), W (Warning), E (Error), F (Fatal)")
                        put("default", "V")
                    }
                    putJsonObject("max_lines") {
                        put("type", "integer")
                        put("description", "Maximum number of log lines to retrieve")
                        put("default", 500)
                    }
                    putJsonObject("file_path") {
                        put("type", "string")
                        put("description", "Relative path to file in Android project (e.g., app/src/main/AndroidManifest.xml)")
                    }
                    putJsonObject("directory_path") {
                        put("type", "string")
                        put("description", "Relative path to directory in Android project (e.g., app/src/main)")
                    }
                    putJsonObject("log_content") {
                        put("type", "string")
                        put("description", "Log content to save to file")
                    }
                    putJsonObject("log_name") {
                        put("type", "string")
                        put("description", "Name for the log file (without extension)")
                        put("default", "log")
                    }
                    putJsonObject("start_line") {
                        put("type", "integer")
                        put("description", "Starting line number for read_file_lines (1-based, inclusive)")
                    }
                    putJsonObject("end_line") {
                        put("type", "integer")
                        put("description", "Ending line number for read_file_lines (1-based, inclusive)")
                    }
                    putJsonObject("search_pattern") {
                        put("type", "string")
                        put("description", "Regex pattern to search for in read_file_lines")
                    }
                    putJsonObject("pattern") {
                        put("type", "string")
                        put("description", "File name pattern for find_files (e.g., '*.kt', 'MainActivity.*')")
                    }
                    putJsonObject("max_depth") {
                        put("type", "integer")
                        put("description", "Maximum directory depth for find_files (default: unlimited)")
                    }
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "Starting line number for read_app_log (0-based, default: 0)")
                        put("default", 0)
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Number of log lines to retrieve for read_app_log (default: 10)")
                        put("default", 10)
                    }
                    putJsonObject("log_file") {
                        put("type", "string")
                        put("description", "Log file name to read (default: app.log)")
                        put("default", "app.log")
                    }
                }
                putJsonArray("required") { add("action") }
            }
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        val action = arguments["action"]?.jsonPrimitive?.content
            ?: return errorJson("Missing required parameter: action")

        logger.info("Android Studio tool called: action=$action, sessionId=$sessionId")

        return try {
            // Determine timeout based on action
            val timeoutMs = when (action) {
                "gradle_build", "gradle_install_run" -> 300_000L // 5 minutes for Gradle builds
                "start_emulator" -> 120_000L // 2 minutes for emulator startup
                else -> 60_000L // 1 minute for other operations
            }

            // Execute the command on the connected local agent
            val result = LocalAgentManager.executeOnLocalAgent(
                toolName = ANDROID_STUDIO_MCP,
                arguments = arguments,
                timeoutMs = timeoutMs
            )

            logger.info("Android Studio tool result: $result")

            // 🆕 Синхронизация пути проекта с ProjectPathService
            if (action == "set_project_path" && !result.contains("\"error\"")) {
                try {
                    val resultJson = Json.parseToJsonElement(result).jsonObject
                    val projectPath = resultJson["project_path"]?.jsonPrimitive?.content
                    if (projectPath != null) {
                        ProjectPathService.setProjectPath(sessionId, projectPath)
                        logger.info("✅ Project path synchronized with ProjectPathService: $projectPath")
                    }
                } catch (e: Exception) {
                    logger.warn("⚠️ Failed to sync project path with ProjectPathService: ${e.message}")
                }
            }

            result
        } catch (e: Exception) {
            logger.error("Error executing Android Studio tool: ${e.message}", e)
            errorJson("Failed to execute Android Studio command: ${e.message}")
        }
    }
}