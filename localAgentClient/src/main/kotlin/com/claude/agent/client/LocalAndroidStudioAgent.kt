package com.claude.agent.client// LocalAndroidStudioAgent.kt
import com.claude.agent.common.AgentMessage
import com.claude.agent.common.LocalToolDefinition
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds

class LocalAndroidStudioAgent(
    private val vpsUrl: String = "ws://127.0.0.1:8443",
    private val agentId: String = "android-studio-${System.getenv("COMPUTERNAME")}",
    initialAndroidProjectPath: String? = "/Users/anton/StudioProjects"
) {
    private val logger = LoggerFactory.getLogger(LocalAndroidStudioAgent::class.java)

    private val client = HttpClient(CIO) {
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                }
            }
        }
        install(WebSockets) {
            pingInterval = 20.seconds
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private var session: WebSocketSession? = null

    @Volatile
    private var androidProjectPath: String? = initialAndroidProjectPath

    @Volatile
    private var screenshotsDir: File? = null
    @Volatile
    private var logsDir: File? = null

    init {
        logger.info("🔧 [INIT] Initializing LocalAndroidStudioAgent")
        logger.info("   Agent ID: $agentId")
        logger.info("   VPS URL: $vpsUrl")
        logger.info("   Initial Project Path: $initialAndroidProjectPath")
        updateProjectDirectories()
    }

    private fun updateProjectPath(newPath: String?) {
        logger.info("📂 [UPDATE_PATH] Updating project path")
        logger.info("   Old path: $androidProjectPath")
        logger.info("   New path: $newPath")
        androidProjectPath = newPath
        updateProjectDirectories()
        logger.info("✅ [UPDATE_PATH] Project path updated successfully")
    }

    private fun updateProjectDirectories() {
        logger.info("📁 [UPDATE_DIRS] Updating project directories")
        logger.info("   Project path: $androidProjectPath")

        screenshotsDir = androidProjectPath?.let {
            File(it, "screenshots").also { dir ->
                logger.info("   Creating screenshots dir: ${dir.absolutePath}")
                dir.mkdirs()
            }
        }

        logsDir = androidProjectPath?.let {
            File(it, "logs").also { dir ->
                logger.info("   Creating logs dir: ${dir.absolutePath}")
                dir.mkdirs()
            }
        }

        logger.info("   Screenshots dir: ${screenshotsDir?.absolutePath ?: "not set"}")
        logger.info("   Logs dir: ${logsDir?.absolutePath ?: "not set"}")
    }

    suspend fun start() {
        logger.info("🚀 [START] Starting agent connection loop")
        var attemptCount = 0

        while (true) {
            attemptCount++
            try {
                logger.info("🔄 [CONNECT] Connection attempt #$attemptCount")
                logger.info("   Connecting to VPS at $vpsUrl...")
                connectAndServe()
            } catch (e: Exception) {
                logger.error("❌ [CONNECT] Connection error on attempt #$attemptCount", e)
                logger.error("   Error: ${e.message}")
                logger.debug("   Stack trace: ${e.stackTraceToString()}")
                logger.info("⏳ [CONNECT] Reconnecting in 5 seconds...")
                delay(5000)
            }
        }
    }

    private suspend fun connectAndServe() {
        logger.info("🔌 [WEBSOCKET] Preparing WebSocket connection")
        val wsUrl = vpsUrl.replace("https://", "wss://").replace("http://", "ws://")
        logger.info("   Transformed URL: $wsUrl")
        logger.info("   Endpoint: $wsUrl/mcp/local-agent")

        client.webSocket("$wsUrl/mcp/local-agent") {
            session = this
            logger.info("✅ [WEBSOCKET] Connected to VPS!")
            logger.info("   Session active: ${this.isActive}")

            // Регистрируемся
            logger.info("📝 [REGISTER] Creating registration message")
            val registerMessage = AgentMessage.Register(
                agentId = agentId,
                tool = createToolDefinition(),
                capabilities = listOf(
                    "android_emulator",
                    "adb",
                    "apk_install",
                    "gradle_build",
                    "logcat",
                    "file_browsing",
                    "log_saving"
                )
            )

            logger.info("   Agent ID: ${registerMessage.agentId}")
            logger.info("   Capabilities: ${registerMessage.capabilities}")

            sendMessage(registerMessage)
            logger.info("📤 [REGISTER] Sent registration")

            // Запускаем ping в фоне
            logger.info("🏓 [PING] Starting ping loop in background")
            launch { startPingLoop() }

            logger.info("👂 [LISTEN] Starting to listen for commands")
            // Слушаем команды
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        logger.debug("📥 [RECEIVE] Received frame")
                        logger.debug("   Frame type: Text")
                        logger.debug("   Content length: ${text.length} chars")
                        logger.debug("   Content preview: ${text.take(200)}...")
                        handleMessage(text)
                    }

                    is Frame.Close -> {
                        logger.info("🔌 [CLOSE] Connection closed by server")
                        logger.info("   Close reason: ${closeReason.await()}")
                        break
                    }

                    else -> {
                        logger.warn("❓ [RECEIVE] Received unknown frame type: ${frame::class.simpleName}")
                    }
                }
            }

            logger.info("🛑 [LISTEN] Stopped listening for commands")
        }

        logger.info("🔌 [WEBSOCKET] WebSocket connection closed")
    }

    private suspend fun startPingLoop() {
        logger.debug("🏓 [PING_LOOP] Ping loop started")
        var pingCount = 0

        while (session?.isActive == true) {
            try {
                pingCount++
                logger.debug("🏓 [PING] Sending ping #$pingCount")
                sendMessage(AgentMessage.Ping())
                logger.debug("✅ [PING] Ping #$pingCount sent successfully")
                delay(15_000)
            } catch (e: Exception) {
                logger.error("❌ [PING] Ping #$pingCount failed: ${e.message}")
                break
            }
        }

        logger.info("🛑 [PING_LOOP] Ping loop stopped (session active: ${session?.isActive})")
    }

    private suspend fun handleMessage(text: String) {
        logger.debug("🔍 [HANDLE_MSG] Handling message")

        try {
            logger.debug("   Deserializing message...")
            val message = json.decodeFromString<AgentMessage>(text)
            logger.debug("   Message type: ${message::class.simpleName}")

            when (message) {
                is AgentMessage.ExecuteRequest -> {
                    logger.info("📥 [EXECUTE_REQ] Received execute request")
                    logger.info("   Request ID: ${message.requestId}")
                    logger.info("   Tool name: ${message.toolName}")
                    logger.debug("   Arguments: ${message.arguments}")

                    val result = executeCommand(message.toolName, message.arguments)

                    logger.debug("   Result preview: ${result.take(200)}...")

                    val response = AgentMessage.ExecuteResponse(
                        requestId = message.requestId,
                        result = result,
                        success = !result.contains("\"error\"")
                    )

                    logger.info("📤 [EXECUTE_RESP] Sending response")
                    logger.info("   Request ID: ${response.requestId}")
                    logger.info("   Success: ${response.success}")

                    sendMessage(response)
                    logger.info("✅ [EXECUTE_RESP] Sent response for ${message.requestId}")
                }

                is AgentMessage.Ping -> {
                    logger.debug("🏓 [PONG] Received ping, sending pong")
                    sendMessage(AgentMessage.Pong())
                    logger.debug("✅ [PONG] Pong sent")
                }

                else -> {
                    logger.warn("⚠️ [HANDLE_MSG] Unknown message type: ${message::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            logger.error("❌ [HANDLE_MSG] Error handling message", e)
            logger.error("   Error: ${e.message}")
            logger.debug("   Stack trace: ${e.stackTraceToString()}")
        }
    }

    private suspend fun sendMessage(message: AgentMessage) {
        logger.debug("📨 [SEND] Sending message")
        logger.debug("   Message type: ${message::class.simpleName}")

        try {
            val jsonString = json.encodeToString(AgentMessage.serializer(), message)
            logger.debug("   JSON length: ${jsonString.length} chars")
            logger.debug("   JSON preview: ${jsonString.take(200)}...")

            session?.send(Frame.Text(jsonString))
            logger.debug("✅ [SEND] Message sent successfully")
        } catch (e: Exception) {
            logger.error("❌ [SEND] Failed to send message", e)
            logger.error("   Error: ${e.message}")
            throw e
        }
    }

    private fun createToolDefinition(): LocalToolDefinition {
        logger.debug("🛠️ [TOOL_DEF] Creating tool definition")

        val toolDef = LocalToolDefinition(
            name = "android_studio",
            description = """
                Control Android Studio emulator, build projects, and execute ADB commands.
                Project path: ${androidProjectPath ?: "Not configured"}
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
                        put("description", "AVD name for emulator")
                    }
                    putJsonObject("apk_path") {
                        put("type", "string")
                        put("description", "Path to APK file")
                    }
                    putJsonObject("package_name") {
                        put("type", "string")
                        put("description", "Android package name")
                    }
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "Shell command to execute")
                    }
                    putJsonObject("build_variant") {
                        put("type", "string")
                        put("description", "Gradle build variant (e.g., debug, release)")
                    }
                    putJsonObject("filter_tag") {
                        put("type", "string")
                        put("description", "Logcat filter tag")
                    }
                    putJsonObject("filter_package") {
                        put("type", "string")
                        put("description", "Logcat filter by package name")
                    }
                    putJsonObject("log_level") {
                        put("type", "string")
                        put("description", "Logcat level: V, D, I, W, E, F")
                    }
                    putJsonObject("max_lines") {
                        put("type", "integer")
                        put("description", "Maximum number of log lines to retrieve")
                    }
                    putJsonObject("file_path") {
                        put("type", "string")
                        put("description", "Relative path to file in Android project")
                    }
                    putJsonObject("directory_path") {
                        put("type", "string")
                        put("description", "Relative path to directory in Android project")
                    }
                    putJsonObject("log_content") {
                        put("type", "string")
                        put("description", "Log content to save")
                    }
                    putJsonObject("log_name") {
                        put("type", "string")
                        put("description", "Name for the log file")
                    }
                }
                putJsonArray("required") { add("action") }
            }
        )

        logger.debug("   Tool name: ${toolDef.name}")
        logger.debug("   Enabled: ${toolDef.enabled}")
        logger.debug("✅ [TOOL_DEF] Tool definition created")

        return toolDef
    }

    private suspend fun executeCommand(toolName: String, arguments: JsonObject): String {
        logger.info("⚙️ [EXEC_CMD] Executing command")
        logger.info("   Tool: $toolName")
        logger.debug("   Arguments: $arguments")

        val action = arguments["action"]?.jsonPrimitive?.content

        if (action == null) {
            logger.error("❌ [EXEC_CMD] Missing action parameter")
            return errorJson("Missing action")
        }

        logger.info("   Action: $action")

        val result = when (action) {
            "set_project_path" -> {
                logger.info("→ [EXEC_CMD] Calling setProjectPath")
                setProjectPath(arguments)
            }
            "get_project_path" -> {
                logger.info("→ [EXEC_CMD] Calling getProjectPath")
                getProjectPath()
            }
            "start_emulator" -> {
                logger.info("→ [EXEC_CMD] Calling startEmulator")
                startEmulator(arguments)
            }
            "stop_emulator" -> {
                logger.info("→ [EXEC_CMD] Calling stopEmulator")
                stopEmulator()
            }
            "list_emulators" -> {
                logger.info("→ [EXEC_CMD] Calling listEmulators")
                listEmulators()
            }
            "install_apk" -> {
                logger.info("→ [EXEC_CMD] Calling installApk")
                installApk(arguments)
            }
            "run_app" -> {
                logger.info("→ [EXEC_CMD] Calling runApp")
                runApp(arguments)
            }
            "adb_shell" -> {
                logger.info("→ [EXEC_CMD] Calling adbShell")
                adbShell(arguments)
            }
            "screenshot" -> {
                logger.info("→ [EXEC_CMD] Calling takeScreenshot")
                takeScreenshot(arguments)
            }
            "gradle_build" -> {
                logger.info("→ [EXEC_CMD] Calling gradleBuild")
                gradleBuild(arguments)
            }
            "gradle_install_run" -> {
                logger.info("→ [EXEC_CMD] Calling gradleInstallAndRun")
                gradleInstallAndRun(arguments)
            }
            "logcat" -> {
                logger.info("→ [EXEC_CMD] Calling getLogcat")
                getLogcat(arguments)
            }
            "logcat_clear" -> {
                logger.info("→ [EXEC_CMD] Calling clearLogcat")
                clearLogcat()
            }
            "browse_files" -> {
                logger.info("→ [EXEC_CMD] Calling browseFiles")
                browseFiles(arguments)
            }
            "read_file" -> {
                logger.info("→ [EXEC_CMD] Calling readFile")
                readFile(arguments)
            }
            "save_log" -> {
                logger.info("→ [EXEC_CMD] Calling saveLog")
                saveLog(arguments)
            }
            else -> {
                logger.error("❌ [EXEC_CMD] Unknown action: $action")
                errorJson("Unknown action: $action")
            }
        }

        logger.info("✅ [EXEC_CMD] Command execution completed")
        logger.debug("   Result length: ${result.length} chars")

        return result
    }

    private fun setProjectPath(arguments: JsonObject): String {
        logger.info("📂 [SET_PATH] Setting project path")
        logger.debug("   Arguments: $arguments")

        val newPath = arguments["project_path"]?.jsonPrimitive?.content

        if (newPath.isNullOrBlank()) {
            logger.error("❌ [SET_PATH] Missing or empty project_path parameter")
            return errorJson("Missing or empty project_path parameter")
        }

        logger.info("   New path: $newPath")
        val projectDir = File(newPath)

        logger.debug("   Checking if directory exists...")
        if (!projectDir.exists()) {
            logger.error("❌ [SET_PATH] Directory does not exist")
            return errorJson("Project directory does not exist: $newPath")
        }

        logger.debug("   Checking if path is directory...")
        if (!projectDir.isDirectory) {
            logger.error("❌ [SET_PATH] Path is not a directory")
            return errorJson("Path is not a directory: $newPath")
        }

        logger.debug("   Updating project path...")
        updateProjectPath(newPath)

        val result = buildJsonObject {
            put("status", "success")
            put("message", "Android project path updated successfully")
            put("project_path", newPath)
            put("screenshots_dir", screenshotsDir?.absolutePath ?: "")
            put("logs_dir", logsDir?.absolutePath ?: "")
        }.toString()

        logger.info("✅ [SET_PATH] Project path set successfully")
        return result
    }

    private fun getProjectPath(): String {
        logger.info("📂 [GET_PATH] Getting project path")
        logger.info("   Current path: $androidProjectPath")

        val result = buildJsonObject {
            put("status", "success")
            put("project_path", androidProjectPath ?: "")
            put("configured", androidProjectPath != null)
            if (androidProjectPath != null) {
                put("screenshots_dir", screenshotsDir?.absolutePath ?: "")
                put("logs_dir", logsDir?.absolutePath ?: "")
                val projectDir = File(androidProjectPath!!)
                put("exists", projectDir.exists())
                put("is_directory", projectDir.isDirectory)
            }
        }.toString()

        logger.info("✅ [GET_PATH] Project path retrieved")
        return result
    }

    private suspend fun startEmulator(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info("🚀 [START_EMU] Starting emulator")
        val avdName = arguments["avd_name"]?.jsonPrimitive?.content ?: "Pixel_5_API_33"
        logger.info("   AVD name: $avdName")

        try {
            logger.debug("   Finding ADB path...")
            val adbPath = findAdbPath()
            logger.debug("   ADB path: $adbPath")

            logger.debug("   Checking for running emulators...")
            val checkProcess = ProcessBuilder(adbPath, "devices").start()
            val devices = checkProcess.inputStream.bufferedReader().readText()
            checkProcess.waitFor()

            logger.debug("   ADB devices output:")
            devices.lines().forEach { logger.debug("     $it") }

            if (devices.contains("emulator-")) {
                logger.info("✅ [START_EMU] Emulator already running")
                return@withContext """{"status":"already_running","message":"Emulator already running"}"""
            }

            logger.debug("   Finding emulator path...")
            val emulatorPath = findEmulatorPath()
            logger.debug("   Emulator path: $emulatorPath")

            logger.info("   Starting emulator process...")
            val process = ProcessBuilder(emulatorPath, "-avd", avdName, "-no-snapshot-load")
                .redirectErrorStream(true)
                .start()

            logger.info("   Process started with PID: ${process.pid()}")

            logger.info("   Waiting for emulator to boot (max 60 seconds)...")
            repeat(60) { attempt ->
                delay(1000)

                if (attempt % 10 == 0) {
                    logger.info("   Waiting... ${attempt}s")
                }

                val check = ProcessBuilder(adbPath, "devices").start()
                val output = check.inputStream.bufferedReader().readText()
                check.waitFor()

                if (output.contains("emulator-") && output.contains("device")) {
                    logger.info("✅ [START_EMU] Emulator started successfully after ${attempt + 1}s")
                    return@withContext buildJsonObject {
                        put("status", "success")
                        put("message", "Emulator $avdName started successfully")
                        put("pid", process.pid())
                        put("boot_time_seconds", attempt + 1)
                    }.toString()
                }
            }

            logger.error("❌ [START_EMU] Emulator not responding after 60 seconds")
            errorJson("Emulator started but not responding")
        } catch (e: Exception) {
            logger.error("❌ [START_EMU] Exception occurred", e)
            logger.error("   Error: ${e.message}")
            errorJson("Failed to start emulator: ${e.message}")
        }
    }

    private suspend fun stopEmulator(): String = withContext(Dispatchers.IO) {
        logger.info(" [STOP_EMU] Stopping emulator")

        try {
            logger.debug("   Finding ADB path...")
            val adbPath = findAdbPath()
            logger.debug("   ADB path: $adbPath")

            logger.debug("   Sending kill command...")
            val process = ProcessBuilder(adbPath, "emu", "kill").start()
            val exitCode = process.waitFor()
            logger.debug("   Exit code: $exitCode")

            logger.info(" [STOP_EMU] Emulator stopped")
            """{"status":"success","message":"Emulator stopped"}"""
        } catch (e: Exception) {
            logger.error(" [STOP_EMU] Failed to stop emulator")
            logger.debug("   Error: ${e.message}")
            errorJson("Failed to stop emulator: ${e.message}")
        }
    }

    private suspend fun listEmulators(): String = withContext(Dispatchers.IO) {
        logger.info(" [LIST_EMU] Listing emulators")

        try {
            logger.debug("   Finding emulator path...")
            val emulatorPath = findEmulatorPath()
            logger.debug("   Emulator path: $emulatorPath")

            logger.debug("   Executing: $emulatorPath -list-avds")
            val process = ProcessBuilder(emulatorPath, "-list-avds")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            logger.debug("   Exit code: $exitCode")
            logger.debug("   Output:")
            output.lines().forEach { logger.debug("     $it") }

            if (exitCode != 0) {
                logger.error(" [LIST_EMU] Command failed with exit code $exitCode")
                return@withContext errorJson("emulator -list-avds failed: $output")
            }

            val avds = output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            logger.info(" [LIST_EMU] Found ${avds.size} emulators")
            avds.forEach { logger.debug("   - $it") }

            buildJsonObject {
                put("status", "success")
                putJsonArray("avds") {
                    avds.forEach { add(it) }
                }
            }.toString()
        } catch (e: Exception) {
            logger.error(" [LIST_EMU] Exception occurred")
            logger.debug("   Error: ${e.message}")
            e.printStackTrace()
            errorJson("Failed to list emulators: ${e.message}")
        }
    }

    private suspend fun installApk(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" [INSTALL_APK] Installing APK")

        val apkPath = arguments["apk_path"]?.jsonPrimitive?.content
        if (apkPath == null) {
            logger.error(" [INSTALL_APK] Missing apk_path parameter")
            return@withContext errorJson("Missing apk_path")
        }

        logger.debug("   APK path: $apkPath")

        logger.debug("   Checking if file exists...")
        if (!File(apkPath).exists()) {
            logger.error(" [INSTALL_APK] APK file not found")
            return@withContext errorJson("APK file not found: $apkPath")
        }

        try {
            logger.debug("   Finding ADB path...")
            val adbPath = findAdbPath()
            logger.debug("   ADB path: $adbPath")

            logger.debug("   Executing: $adbPath install -r $apkPath")
            val process = ProcessBuilder(adbPath, "install", "-r", apkPath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            logger.debug("   Exit code: $exitCode")
            logger.debug("   Output:")
            output.lines().forEach { logger.debug("     $it") }

            if (exitCode == 0 && output.contains("Success")) {
                logger.info(" [INSTALL_APK] APK installed successfully")
                """{"status":"success","message":"APK installed successfully"}"""
            } else {
                logger.error(" [INSTALL_APK] Installation failed")
                errorJson("Installation failed: $output")
            }
        } catch (e: Exception) {
            logger.error(" [INSTALL_APK] Exception occurred")
            logger.debug("   Error: ${e.message}")
            errorJson("Install failed: ${e.message}")
        }
    }

    private suspend fun runApp(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" [RUN_APP] Running app")

        val packageName = arguments["package_name"]?.jsonPrimitive?.content
        if (packageName == null) {
            logger.error(" [RUN_APP] Missing package_name parameter")
            return@withContext errorJson("Missing package_name")
        }

        logger.debug("   Package name: $packageName")

        try {
            logger.debug("   Finding ADB path...")
            val adbPath = findAdbPath()
            logger.debug("   ADB path: $adbPath")

            logger.debug("   Checking if package is installed...")
            val checkProcess = ProcessBuilder(
                adbPath, "shell", "pm", "list", "packages", packageName
            ).start()

            val packages = checkProcess.inputStream.bufferedReader().readText()
            checkProcess.waitFor()

            logger.debug("   Packages output:")
            packages.lines().forEach { logger.debug("     $it") }

            if (!packages.contains(packageName)) {
                logger.error(" [RUN_APP] Package not installed")
                return@withContext errorJson("Package $packageName is not installed")
            }

            logger.debug("   Resolving launch activity...")
            val resolveProcess = ProcessBuilder(
                adbPath, "shell", "cmd", "package", "resolve-activity",
                "--brief", packageName
            ).redirectErrorStream(true).start()

            val resolveOutput = resolveProcess.inputStream.bufferedReader().readText()
            resolveProcess.waitFor()

            logger.debug("   Resolve output:")
            resolveOutput.lines().forEach { logger.debug("     $it") }

            val activity = resolveOutput.lines()
                .firstOrNull { it.contains("/") }

            if (activity == null) {
                logger.error(" [RUN_APP] No launchable activity found")
                return@withContext errorJson("No launchable activity found for $packageName")
            }

            logger.debug("   Launch activity: $activity")

            logger.debug("   Starting activity...")
            val startProcess = ProcessBuilder(
                adbPath, "shell", "am", "start", "-n", activity.trim()
            ).redirectErrorStream(true).start()

            val startOutput = startProcess.inputStream.bufferedReader().readText()
            val exitCode = startProcess.waitFor()

            logger.debug("   Exit code: $exitCode")
            logger.debug("   Output:")
            startOutput.lines().forEach { logger.debug("     $it") }

            if (exitCode == 0 && startOutput.contains("Starting")) {
                logger.info(" [RUN_APP] App launched successfully")
                buildJsonObject {
                    put("status", "success")
                    put("message", "App launched successfully")
                    put("activity", activity)
                }.toString()
            } else {
                logger.error(" [RUN_APP] Failed to start app")
                errorJson("Failed to start app: $startOutput")
            }

        } catch (e: Exception) {
            logger.error(" [RUN_APP] Exception occurred")
            logger.debug("   Error: ${e.message}")
            errorJson("Failed to run app: ${e.message}")
        }
    }

    private suspend fun adbShell(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        val command = arguments["command"]?.jsonPrimitive?.content
            ?: return@withContext errorJson("Missing command")

        logger.info(" adbShell called")
        logger.debug("   command = $command")

        try {
            val adbPath = findAdbPath()
            logger.debug("   adbPath = $adbPath")

            val process = ProcessBuilder(adbPath, "shell", command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            logger.debug("   exitCode = $exitCode")
            logger.debug("   output:\n$output")

            buildJsonObject {
                put("status", "success")
                put("exit_code", exitCode)
                put("output", output)
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            errorJson("ADB command failed: ${e.message}")
        }
    }

    private suspend fun takeScreenshot(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" takeScreenshot called")

        try {
            val adbPath = findAdbPath()
            logger.debug("   adbPath = $adbPath")

            val timestamp = System.currentTimeMillis()
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
            val formattedTime = dateFormat.format(java.util.Date(timestamp))

            val remotePath = "/sdcard/screenshot_$timestamp.png"

            val localFile = if (screenshotsDir != null) {
                File(screenshotsDir, "screenshot_$formattedTime.png")
            } else {
                File("screenshot_$formattedTime.png")
            }

            logger.debug("   remotePath = $remotePath")
            logger.debug("   localPath  = ${localFile.absolutePath}")

            logger.debug("   📱 Taking screenshot on device")
            ProcessBuilder(adbPath, "shell", "screencap", "-p", remotePath)
                .start()
                .waitFor()

            logger.debug("   ⬇️ Pulling screenshot")
            ProcessBuilder(adbPath, "pull", remotePath, localFile.absolutePath)
                .start()
                .waitFor()

            logger.debug("   🧹 Removing remote screenshot")
            ProcessBuilder(adbPath, "shell", "rm", remotePath)
                .start()
                .waitFor()

            logger.debug("   ✅ Screenshot saved")

            buildJsonObject {
                put("status", "success")
                put("screenshot_path", localFile.absolutePath)
                put("relative_path", if (androidProjectPath != null) {
                    localFile.relativeTo(File(androidProjectPath)).path
                } else {
                    localFile.name
                })
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            errorJson("Screenshot failed: ${e.message}")
        }
    }

    private suspend fun gradleBuild(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" gradleBuild called with arguments: $arguments")

        if (androidProjectPath == null) {
            logger.error(" Android project path is not configured")
            return@withContext errorJson("Android project path not configured")
        }

        try {
            val buildVariant = arguments["build_variant"]?.jsonPrimitive?.content ?: "debug"
            logger.debug(" Build variant determined: $buildVariant")

            val projectDir = File(androidProjectPath)
            logger.debug(" Project directory: ${projectDir.absolutePath}")

            if (!projectDir.exists()) {
                logger.error(" Project directory does not exist: ${projectDir.absolutePath}")
                return@withContext errorJson("Project directory not found: $androidProjectPath")
            }

            // Determine gradle wrapper command
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            logger.debug(" Operating system detected: ${System.getProperty("os.name")} (isWindows=$isWindows)")

            val gradleCmd = if (isWindows) {
                File(projectDir, "gradlew.bat").absolutePath
            } else {
                File(projectDir, "gradlew").absolutePath
            }
            logger.debug(" Gradle wrapper command: $gradleCmd")

            if (!File(gradleCmd).exists()) {
                logger.error(" Gradle wrapper not found at path: $gradleCmd")
                return@withContext errorJson("Gradle wrapper not found at: $gradleCmd")
            }

            logger.info(" Starting build for variant: $buildVariant")

            val task = "assemble${buildVariant.replaceFirstChar { it.uppercase() }}"
            logger.debug(" Gradle task to execute: $task")

            val processBuilder = ProcessBuilder(gradleCmd, task)
                .directory(projectDir)
                .redirectErrorStream(true)

            logger.debug(" Starting process with ProcessBuilder...")
            val process = processBuilder.start()

            logger.debug(" Reading build output...")
            val output = process.inputStream.bufferedReader().readText()
            logger.debug("📄 Build output received (first 200 chars): ${output.take(200)}...")

            val exitCode = process.waitFor()
            logger.debug(" Gradle process exited with code: $exitCode")

            if (exitCode == 0) {
                logger.info(" Build succeeded, searching for APK...")
                val apkPath = findBuiltApk(projectDir, buildVariant)
                if (apkPath != null) {
                    logger.info(" APK found at: $apkPath")
                } else {
                    logger.warn(" APK not found after successful build")
                }

                buildJsonObject {
                    put("status", "success")
                    put("message", "Build completed successfully")
                    put("variant", buildVariant)
                    if (apkPath != null) {
                        put("apk_path", apkPath)
                    }
                    put("output", output.takeLast(500)) // Last 500 chars
                }.toString()
            } else {
                logger.error(" Build failed with exit code $exitCode")
                errorJson("Build failed with exit code $exitCode: ${output.takeLast(500)}")
            }
        } catch (e: Exception) {
            logger.error(" Exception occurred during gradleBuild")
            e.printStackTrace()
            errorJson("Gradle build failed: ${e.message}")
        }
    }

    private suspend fun gradleInstallAndRun(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" gradleInstallAndRun called with arguments: $arguments")

        if (androidProjectPath == null) {
            logger.error(" Android project path is not configured")
            return@withContext errorJson("Android project path not configured")
        }

        try {
            val buildVariant = arguments["build_variant"]?.jsonPrimitive?.content ?: "debug"
            val packageName = arguments["package_name"]?.jsonPrimitive?.content
            val projectDir = File(androidProjectPath)
            logger.debug(" Project directory: ${projectDir.absolutePath}")
            logger.debug(" Build variant: $buildVariant")
            logger.debug(" Package name: ${packageName ?: "not provided"}")

            // Determine gradle wrapper command
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val gradleCmd = if (isWindows) {
                File(projectDir, "gradlew.bat").absolutePath
            } else {
                File(projectDir, "gradlew").absolutePath
            }
            logger.debug(" Operating system: ${System.getProperty("os.name")} (isWindows=$isWindows)")
            logger.debug(" Gradle wrapper path: $gradleCmd")

            if (!File(gradleCmd).exists()) {
                logger.error(" Gradle wrapper not found at: $gradleCmd")
                return@withContext errorJson("Gradle wrapper not found at: $gradleCmd")
            }

            logger.info(" Starting build & install task for variant: $buildVariant")
            val installTask = "install${buildVariant.replaceFirstChar { it.uppercase() }}"
            logger.debug(" Gradle task: $installTask")

            val buildProcess = ProcessBuilder(gradleCmd, installTask, "-Pandroid.injected.install.options=-r")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            logger.debug(" Reading build/install output...")
            val buildOutput = buildProcess.inputStream.bufferedReader().readText()
            logger.debug("📄 Build/install output (first 200 chars): ${buildOutput.take(200)}...")
            val buildExitCode = buildProcess.waitFor()
            logger.debug(" Build/install process exited with code: $buildExitCode")
            if (buildExitCode == 0 && packageName != null) {
                logger.info("🧹 Clearing app data for: $packageName")
                val adbPath = findAdbPath()
                ProcessBuilder(adbPath, "shell", "pm", "clear", packageName)
                    .start()
                    .waitFor()
            }
            if (buildExitCode != 0) {
                logger.error(" Build/install failed")
                return@withContext errorJson("Build/Install failed: ${buildOutput.takeLast(500)}")
            }

            logger.info(" Build & install succeeded")

            // Launch app if package name provided
            if (packageName != null) {
                logger.info(" Launching app: $packageName")
                delay(1000) // Wait a bit after install
                val adbPath = findAdbPath()
                logger.debug(" Using adb at: $adbPath")

                val launchProcess = ProcessBuilder(
                    adbPath, "shell", "monkey",
                    "-p", packageName,
                    "-c", "android.intent.category.LAUNCHER", "1"
                ).start()
                val launchExitCode = launchProcess.waitFor()
                logger.debug(" App launch process exited with code: $launchExitCode")

                buildJsonObject {
                    put("status", "success")
                    put("message", "App built, installed, and launched")
                    put("package", packageName)
                }.toString()
            } else {
                logger.debug(" Package name not provided, skipping app launch")
                buildJsonObject {
                    put("status", "success")
                    put("message", "App built and installed successfully")
                }.toString()
            }
        } catch (e: Exception) {
            logger.error(" Exception during gradleInstallAndRun")
            e.printStackTrace()
            errorJson("Gradle install failed: ${e.message}")
        }
    }

    private fun findBuiltApk(projectDir: File, variant: String): String? {
        val buildDir = File(projectDir, "app/build/outputs/apk/$variant")
        if (!buildDir.exists()) return null
        logger.debug("findBuiltApk: $buildDir")
        return buildDir.listFiles()
            ?.firstOrNull { it.extension == "apk" }
            ?.absolutePath
    }

    private suspend fun getLogcat(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" getLogcat called with arguments: $arguments")
        try {
            val adbPath = findAdbPath()
            val filterTag = arguments["filter_tag"]?.jsonPrimitive?.content
            val filterPackage = arguments["filter_package"]?.jsonPrimitive?.content
            val logLevel = arguments["log_level"]?.jsonPrimitive?.content ?: "V"
            val maxLines = arguments["max_lines"]?.jsonPrimitive?.content?.toIntOrNull() ?: 500

            logger.debug(" ADB path: $adbPath")
            logger.debug(" Filter tag: ${filterTag ?: "none"}")
            logger.debug(" Filter package: ${filterPackage ?: "none"}")
            logger.debug(" Log level: $logLevel")
            logger.debug(" Max lines: $maxLines")

            val cmdArgs = mutableListOf(adbPath, "logcat", "-d", "-v", "time")
            if (filterTag != null) {
                cmdArgs.add("$filterTag:$logLevel")
                cmdArgs.add("*:S")
            } else {
                cmdArgs.add("*:$logLevel")
            }

            logger.debug(" Executing command: ${cmdArgs.joinToString(" ")}")
            val process = ProcessBuilder(cmdArgs)
                .redirectErrorStream(true)
                .start()

            var output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            logger.debug(" Logcat command exited with code: $exitCode")
            logger.debug("📄 Logcat output (first 200 chars): ${output.take(200)}...")

            if (filterPackage != null) {
                output = output.lines()
                    .filter { it.contains(filterPackage) }
                    .joinToString("\n")
                logger.debug(" Filtered log lines by package, total lines: ${output.lines().size}")
            }

            val lines = output.lines().takeLast(maxLines)
            val limitedOutput = lines.joinToString("\n")
            logger.debug(" Returning last ${lines.size} log lines")

            buildJsonObject {
                put("status", "success")
                put("logs", limitedOutput)
                put("line_count", lines.size)
                if (filterTag != null) put("filter_tag", filterTag)
                if (filterPackage != null) put("filter_package", filterPackage)
                put("log_level", logLevel)
            }.toString()
        } catch (e: Exception) {
            logger.error(" Exception during getLogcat")
            e.printStackTrace()
            errorJson("Logcat failed: ${e.message}")
        }
    }

    private suspend fun clearLogcat(): String = withContext(Dispatchers.IO) {
        logger.info(" clearLogcat called")
        try {
            val adbPath = findAdbPath()
            logger.debug(" Clearing logcat using adb: $adbPath")
            val process = ProcessBuilder(adbPath, "logcat", "-c").start()
            val exitCode = process.waitFor()
            logger.debug(" Clear logcat exited with code: $exitCode")

            buildJsonObject {
                put("status", "success")
                put("message", "Logcat cleared")
            }.toString()
        } catch (e: Exception) {
            logger.error(" Exception during clearLogcat")
            e.printStackTrace()
            errorJson("Clear logcat failed: ${e.message}")
        }
    }

    private suspend fun browseFiles(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" browseFiles called with arguments: $arguments")
        if (androidProjectPath == null) {
            logger.error(" Android project path is not configured")
            return@withContext errorJson("Android project path not configured")
        }

        try {
            val relativePath = arguments["directory_path"]?.jsonPrimitive?.content ?: ""
            val targetDir = if (relativePath.isEmpty()) File(androidProjectPath) else File(androidProjectPath, relativePath)
            logger.debug(" Target directory: ${targetDir.absolutePath}")

            if (!targetDir.exists()) {
                logger.error(" Directory not found")
                return@withContext errorJson("Directory not found: ${targetDir.absolutePath}")
            }
            if (!targetDir.isDirectory) {
                logger.error(" Path is not a directory")
                return@withContext errorJson("Path is not a directory: ${targetDir.absolutePath}")
            }

            val files = targetDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            logger.debug(" Found ${files?.size ?: 0} items in directory")

            buildJsonObject {
                put("status", "success")
                put("path", targetDir.absolutePath)
                put("relative_path", relativePath)
                putJsonArray("files") {
                    files?.forEach { file ->
                        addJsonObject {
                            put("name", file.name)
                            put("type", if (file.isDirectory) "directory" else "file")
                            put("size", file.length())
                            put("last_modified", file.lastModified())
                        }
                    }
                }
            }.toString()
        } catch (e: Exception) {
            logger.error(" Exception during browseFiles")
            e.printStackTrace()
            errorJson("Browse files failed: ${e.message}")
        }
    }

    private suspend fun readFile(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" readFile called with arguments: $arguments")
        if (androidProjectPath == null) {
            logger.error(" Android project path is not configured")
            return@withContext errorJson("Android project path not configured")
        }

        try {
            val filePath = arguments["file_path"]?.jsonPrimitive?.content
                ?: return@withContext errorJson("Missing file_path parameter")
            val targetFile = File(androidProjectPath, filePath)
            logger.debug(" Reading file: ${targetFile.absolutePath}")

            if (!targetFile.exists()) {
                logger.error(" File not found")
                return@withContext errorJson("File not found: ${targetFile.absolutePath}")
            }
            if (!targetFile.isFile) {
                logger.error(" Path is not a file")
                return@withContext errorJson("Path is not a file: ${targetFile.absolutePath}")
            }
            if (targetFile.length() > 1_000_000) {
                logger.error(" File too large")
                return@withContext errorJson("File too large (max 1MB): ${targetFile.length()} bytes")
            }

            val content = targetFile.readText()
            logger.debug(" File read successfully, size: ${targetFile.length()} bytes")

            buildJsonObject {
                put("status", "success")
                put("file_path", filePath)
                put("absolute_path", targetFile.absolutePath)
                put("content", content)
                put("size", targetFile.length())
            }.toString()
        } catch (e: Exception) {
            logger.error(" Exception during readFile")
            e.printStackTrace()
            errorJson("Read file failed: ${e.message}")
        }
    }

    private suspend fun saveLog(arguments: JsonObject): String = withContext(Dispatchers.IO) {
        logger.info(" saveLog called with arguments: $arguments")
        try {
            val logContent = arguments["log_content"]?.jsonPrimitive?.content
                ?: return@withContext errorJson("Missing log_content parameter")
            val logName = arguments["log_name"]?.jsonPrimitive?.content ?: "log"

            val timestamp = System.currentTimeMillis()
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
            val formattedTime = dateFormat.format(java.util.Date(timestamp))
            logger.debug(" Saving log with timestamp: $formattedTime")

            val logFile = if (logsDir != null) File(logsDir, "${logName}_$formattedTime.txt")
            else File("${logName}_$formattedTime.txt")

            logFile.writeText(logContent)
            logger.debug(" Log saved at: ${logFile.absolutePath}, size: ${logFile.length()} bytes")

            buildJsonObject {
                put("status", "success")
                put("log_path", logFile.absolutePath)
                put("relative_path", if (androidProjectPath != null) logFile.relativeTo(File(androidProjectPath)).path else logFile.name)
                put("size", logFile.length())
            }.toString()
        } catch (e: Exception) {
            logger.error(" Exception during saveLog")
            e.printStackTrace()
            errorJson("Save log failed: ${e.message}")
        }
    }

    internal fun findAndroidHome(): String? {
        val envVars = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_SDK")
        for (env in envVars) {
            val value = System.getenv(env)
            if (!value.isNullOrEmpty()) {
                logger.debug(" Found Android SDK from $env: $value")
                return value
            }
        }

        logger.debug(" Looking for Android SDK in common locations")
        return findAndroidHomeFromCommonLocations()?.also {
            logger.debug(" Found Android SDK at common location: $it")
        }
    }

    private fun findAndroidHomeFromCommonLocations(): String? {
        val userHome = System.getProperty("user.home")
        val commonLocations = listOf(
            "$userHome/Android/Sdk",
            "$userHome/Library/Android/sdk",
            "/usr/local/android-sdk",
            "C:\\Android\\sdk",
            "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Local\\Android\\Sdk"
        )

        return commonLocations.firstOrNull { File(it).exists() }
    }

    internal fun findAdbPath(): String {
        val androidHome = findAndroidHome()
        logger.debug(" Searching for ADB...")

        if (androidHome != null) {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val adbPath = if (isWindows) "$androidHome\\platform-tools\\adb.exe" else "$androidHome/platform-tools/adb"
            logger.debug(" Checking ADB path: $adbPath")

            if (File(adbPath).exists()) {
                logger.info(" Found ADB at: $adbPath")
                return adbPath
            } else {
                logger.warn(" ADB not found at expected location")
            }
        } else {
            logger.warn(" ANDROID_HOME not set")
        }

        logger.warn(" Trying 'adb' from PATH")
        return "adb"
    }


    internal fun findEmulatorPath(): String {
        val androidHome = findAndroidHome()
        if (androidHome != null) {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val emulatorPath = if (isWindows) "$androidHome\\emulator\\emulator.exe" else "$androidHome/emulator/emulator"
            if (File(emulatorPath).exists()) {
                logger.info(" Found emulator at: $emulatorPath")
                return emulatorPath
            }
        }
        logger.warn(" ANDROID_HOME not set, trying 'emulator' from PATH")
        return "emulator"
    }

    private fun errorJson(message: String) = buildJsonObject {
        put("error", message)
        put("status", "error")
    }.toString()
}

// Main function для запуска
fun main(args: Array<String>) = runBlocking {
    val logger = LoggerFactory.getLogger("LocalAndroidStudioAgent.Main")

    logger.info("🚀 Starting Local Android Studio Agent...")
    logger.info("📍 Agent ID: android-studio-${System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "unknown"}")

    // Parse arguments: [vpsUrl] [androidProjectPath]
    val vpsUrl = args.getOrNull(0) ?: "ws://127.0.0.1:8443"
    val androidProjectPath = args.getOrNull(1)

    logger.info("🌐 VPS URL: $vpsUrl")
    if (androidProjectPath != null) {
        logger.info("📂 Android Project: $androidProjectPath")
        val projectDir = File(androidProjectPath)
        if (!projectDir.exists()) {
            logger.warn("   ⚠️  WARNING: Project directory does not exist!")
        } else {
            logger.info("   ✅ Project directory found")
        }
    } else {
        logger.info("   ℹ️  No Android project path specified (optional)")
        logger.info("   Usage: java -jar agent.jar [vpsUrl] [androidProjectPath]")
    }
    logger.info("")

    // Проверяем Android SDK
    val agent = LocalAndroidStudioAgent(
        vpsUrl = vpsUrl,
        initialAndroidProjectPath = androidProjectPath
    )

    val androidHome = agent.findAndroidHome()
    if (androidHome != null) {
        logger.info("✅ ANDROID_HOME found: $androidHome")

        // Проверяем наличие инструментов
        val adbPath = agent.findAdbPath()
        val emulatorPath = agent.findEmulatorPath()

        logger.info("   📱 ADB: $adbPath")
        logger.info("   🖥️  Emulator: $emulatorPath")

        // Проверяем, что файлы существуют
        var hasErrors = false
        if (!java.io.File(adbPath).exists() && adbPath != "adb") {
            logger.error("   ❌ ADB not found at path!")
            hasErrors = true
        }
        if (!java.io.File(emulatorPath).exists() && emulatorPath != "emulator") {
            logger.error("   ❌ Emulator not found at path!")
            hasErrors = true
        }

        if (hasErrors) {
            logger.info("")
            logger.warn("⚠️  Some tools are missing. Please install them via Android SDK Manager.")
            logger.info("   See ANDROID_SDK_SETUP.md for instructions.")
        }
    } else {
        logger.warn("⚠️ WARNING: ANDROID_HOME not found!")
        logger.info("   Please set ANDROID_HOME environment variable")
        logger.info("   or install Android SDK to a standard location")
        logger.info("")
        logger.info("   See localAgentClient/ANDROID_SDK_SETUP.md for setup instructions.")
    }

    logger.info("")
    logger.info("🔄 Connecting to VPS...")
    agent.start()
}