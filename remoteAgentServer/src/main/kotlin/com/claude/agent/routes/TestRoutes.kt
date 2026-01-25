package com.claude.agent.routes

import com.claude.agent.models.ErrorResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

@Serializable
data class GenerateLogsRequest(
    val count: Int = 100,
    val includeErrors: Boolean = true,
    val includeWarnings: Boolean = true
)

@Serializable
data class GenerateLogsResponse(
    val success: Boolean,
    val message: String,
    val linesGenerated: Int,
    val filePath: String
)

/**
 * Тестовые роуты для отладки функциональности
 */
fun Route.testRoutes() {
    val logger = LoggerFactory.getLogger("TestRoutes")

    route("/api/test") {
        
        /**
         * POST /api/test/generate-logs - генерация тестовых логов
         */
        post("/generate-logs") {
            try {
                val request = try {
                    call.receive<GenerateLogsRequest>()
                } catch (e: Exception) {
                    GenerateLogsRequest() // Используем значения по умолчанию
                }

                logger.info("🧪 Generating test logs: count=${request.count}, errors=${request.includeErrors}, warnings=${request.includeWarnings}")

                val logFile = File("app.log")
                val logLines = mutableListOf<String>()

                // Генерируем тестовые логи
                val currentTime = java.time.LocalTime.now()
                val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

                val errorMessages = listOf(
                    "Failed to connect to database: Connection timeout",
                    "NullPointerException in UserService.getUserById",
                    "Failed to parse JSON response from API",
                    "OutOfMemoryError: Java heap space",
                    "Failed to authenticate user: Invalid credentials",
                    "SQLException: Duplicate entry for key 'PRIMARY'",
                    "Failed to send email notification",
                    "IllegalArgumentException: User ID cannot be null",
                    "Failed to load configuration file: File not found",
                    "RuntimeException in PaymentProcessor.processPayment"
                )

                val warningMessages = listOf(
                    "⚠️ Slow query detected: SELECT * FROM users took 2.5s",
                    "⚠️ Cache miss for key: user_profile_12345",
                    "⚠️ Deprecated API endpoint used: /api/v1/users",
                    "⚠️ High memory usage: 85% of heap used",
                    "⚠️ Rate limit approaching: 95/100 requests",
                    "⚠️ SSL certificate expires in 7 days",
                    "⚠️ Disk space low: 10% remaining",
                    "⚠️ Background job queue size: 1000 items"
                )

                val infoMessages = listOf(
                    "User logged in successfully: user_12345",
                    "Processing payment for order #67890",
                    "Email sent to user@example.com",
                    "Cache refreshed for key: product_catalog",
                    "Background job completed: data_export_task",
                    "API request processed: GET /api/products",
                    "Database connection pool initialized",
                    "Configuration loaded from application.yml"
                )

                val loggers = listOf(
                    "c.claude.agent.routes.ChatRoutes",
                    "c.claude.agent.service.UserService",
                    "c.claude.agent.database.Repository",
                    "c.claude.agent.llm.ClaudeClient",
                    "c.claude.agent.service.PaymentService",
                    "c.claude.agent.service.EmailService",
                    "c.claude.agent.service.CacheService",
                    "c.claude.agent.config.AppConfig"
                )

                for (i in 0 until request.count) {
                    val time = currentTime.plusSeconds(i.toLong()).format(formatter)
                    val logger = loggers.random()
                    
                    val message = when {
                        request.includeErrors && i % 10 == 0 -> errorMessages.random()
                        request.includeWarnings && i % 5 == 0 -> warningMessages.random()
                        else -> infoMessages.random()
                    }

                    logLines.add("$time $logger - $message")
                }

                // Добавляем к существующим логам
                val existingLogs = if (logFile.exists()) {
                    logFile.readLines()
                } else {
                    emptyList()
                }

                val allLogs = existingLogs + logLines
                logFile.writeText(allLogs.joinToString("\n"))

                logger.info("✅ Generated ${logLines.size} test log lines")

                call.respond(HttpStatusCode.OK, GenerateLogsResponse(
                    success = true,
                    message = "Test logs generated successfully",
                    linesGenerated = logLines.size,
                    filePath = logFile.absolutePath
                ))

            } catch (e: Exception) {
                logger.error("❌ Failed to generate test logs: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to generate logs: ${e.message}"))
            }
        }

        /**
         * DELETE /api/test/clear-logs - очистка логов
         */
        delete("/clear-logs") {
            try {
                val logFile = File("app.log")
                if (logFile.exists()) {
                    logFile.writeText("")
                    logger.info("✅ Logs cleared")
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Logs cleared"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Log file not found"))
                }
            } catch (e: Exception) {
                logger.error("❌ Failed to clear logs: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to clear logs: ${e.message}"))
            }
        }
    }
}

