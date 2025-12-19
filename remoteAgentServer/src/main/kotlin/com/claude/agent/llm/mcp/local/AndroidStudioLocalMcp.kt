package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.ANDROID_STUDIO_MCP
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.LocalAgentManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
            description = """
            Этот инструмент управляет Android Studio, Android Emulator, ADB, Gradle и ЛОКАЛЬНОЙ ФАЙЛОВОЙ СИСТЕМОЙ
            на ПОДКЛЮЧЁННОМ КОМПЬЮТЕРЕ РАЗРАБОТЧИКА (не на сервере).
        
            ВАЖНАЯ МОДЕЛЬ ВЫПОЛНЕНИЯ:
        
            - Этот инструмент выполняется на ЛОКАЛЬНОМ АГЕНТЕ, запущенном на компьютере разработчика.
            - LLM работает на СЕРВЕРЕ и НЕ имеет прямого доступа к файлам, проектам или операционной системе.
            - ВЕСЬ доступ к файловой системе ДОЛЖЕН осуществляться через этот инструмент.
        
            КОРЕНЬ ПРОЕКТА И ПРАВИЛА ПУТЕЙ:
        
            - Корень Android-проекта может быть задан во время выполнения с помощью действия set_project_path.
            - Используйте get_project_path, чтобы проверить текущий путь проекта.
            - browse_files и read_file работают ТОЛЬКО внутри корня проекта.
            - Пути, передаваемые в browse_files / read_file, ДОЛЖНЫ быть ОТНОСИТЕЛЬНЫМИ к корню проекта.
            - Абсолютные пути из чата (например, "/Users/anton/StudioProjects/RoundTimer")
            необходимо сначала установить через действие set_project_path,
            а затем использовать относительные пути для операций с файлами.
        
            КАК РАБОТАТЬ С ФАЙЛАМИ (ОБЯЗАТЕЛЬНЫЙ АЛГОРИТМ):
        
            1. Если пользователь упоминает проект или папку:
            - Начните с browse_files в корне проекта (directory_path = "")
            - НЕ обходите все подкаталоги рекурсивно — это НЕЭФФЕКТИВНО
            - Ограничьтесь максимум 1–2 уровнями вложенности
            - Выведи содержимое папки, сформируй из json ответа читаемый список (не дерево фалов)
        
            2. Если пользователь просит:
            - «посмотреть проект» / "show project"
            - «найти файл» / "find file"
            - «где реализована логика» / "where is the logic"
            - «покажи код» / "show code"
            - «вывести содержимое файлов» / "display file contents"
            
            КАК РАБОТАТЬ С Android приложениями (ОБЯЗАТЕЛЬНЫЙ АЛГОРИТМ):
        
            1. Если пользователь упоминает проект или проложение и просит с ним что-то сделать:
            - Начните с browse_files в корне проекта (directory_path = "")
            - Узнайте с каким приложением вы работаете прочитав AndroidManifest.xml
            - Определите package_name приложения и передавай его в инструменты как package_name
            - Выведи package_name в ответе
        
            2. Если пользователь просит:
            - «собрать приложение» / "build app"
            - «устанавить приложение» / "install app"
            - «запустить приложение» / "run app"
            - «удалить приложение» / "uninstall app"
            - «вывести содержимое файлов» / "display file contents"
        
            ЭФФЕКТИВНЫЙ РАБОЧИЙ ПРОЦЕСС:
            a) Один раз просмотреть корневую директорию (directory_path = "")
            b) При необходимости углубиться на ОДИН уровень
            c) Если пользователь попросил прочитать определенный файл - то смотри только его
            e) Анализировать содержимое ПОСЛЕ чтения - только если пользователь попросит тебя
        
            ЗАПРЕЩЕНО:
            - Просматривать все подкаталоги (com → anton → roundtimer → ...)
            - Вызывать browse_files более 3 раз для одной задачи
            - Исследовать структуру без цели
        
            3. НИКОГДА не предполагайте содержимое файлов.
            Если нужно что-то узнать — используйте read_file.
        
            4. МНОГОШАГОВЫЕ ЗАДАЧИ:
            Если пользователь просит «сделать X и Y»:
            - Полностью выполните X
            - Затем полностью выполните Y
            - Сообщите о выполнении ОБОИХ шагов
        
            ПОДСКАЗКИ ПО СТРУКТУРЕ ANDROID-ПРОЕКТА(Только по запросу пользователя):
        
            - app/src/main/java или kotlin → логика приложения
            - app/src/main/res → UI-ресурсы
            - AndroidManifest.xml → точка входа приложения
            - build.gradle / build.gradle.kts → конфигурация сборки
        
            ДОСТУПНЫЕ ДЕЙСТВИЯ:
        
            КОНФИГУРАЦИЯ ПРОЕКТА:
            - set_project_path — установить путь к Android-проекту (требуется project_path)
            - get_project_path — получить текущий путь проекта
        
            ЭМУЛЯТОР И ADB:
            - start_emulator — запустить Android-эмулятор (требуется avd_name)
            - stop_emulator — остановить запущенный эмулятор
            - list_emulators — список доступных AVD
            - install_apk — установить APK в эмулятор (требуется apk_path)
            - run_app — запустить установленное приложение (требуется в начале определить package_name)
            - adb_shell — выполнить команду adb shell (требуется command)
            - screenshot — сделать скриншот с эмулятора
        
            СБОРКА И ДЕПЛОЙ:
            - gradle_build — собрать Android-проект (требуется build_variant)
            - gradle_install_run — собрать, установить и при необходимости запустить приложение
        
            ЛОГИРОВАНИЕ:
            - logcat — получить логи Android(сформируй из json ответа от инструмента в читаемый список)
            - logcat_clear — очистить буфер logcat
        
            ДЕЙСТВИЯ С ФАЙЛОВОЙ СИСТЕМОЙ (ТОЛЬКО ЛОКАЛЬНАЯ МАШИНА):
            - browse_files — список файлов и папок относительно корня проекта
            - read_file — чтение файла относительно корня проекта
            - save_log — сохранить переданное содержимое в файл на локальной машине
        
            КРИТИЧЕСКИЕ ПРАВИЛА:
        
            - НИКОГДА не обращаться к файлам напрямую.
            - НИКОГДА не предполагать содержимое файлов.
            - ВСЕГДА изучать структуру перед чтением, если она неизвестна.
            - Думайте как удалённый оператор, управляющий машиной разработчика.
        
            Этот инструмент — ЕДИНСТВЕННЫЙ мост между LLM и локальной средой разработки Android.
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
                            add("save_log")
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

        logger.info("Android Studio tool called: action=$action, arguments=$arguments")

        return try {
            // Determine timeout based on action
            val timeoutMs = when (action) {
                "gradle_build", "gradle_install_run" -> 300_000L // 5 minutes for Gradle builds
                "start_emulator" -> 120_000L // 2 minutes for emulator startup
                else -> 60_000L // 1 minute for other operations
            }

            // Execute the command on the connected local agent
            val result = LocalAgentManager.executeOnLocalAgent(
                toolName = "android_studio",
                arguments = arguments,
                timeoutMs = timeoutMs
            )

            logger.info("Android Studio tool result: $result")
            result
        } catch (e: Exception) {
            logger.error("Error executing Android Studio tool: ${e.message}", e)
            errorJson("Failed to execute Android Studio command: ${e.message}")
        }
    }
}