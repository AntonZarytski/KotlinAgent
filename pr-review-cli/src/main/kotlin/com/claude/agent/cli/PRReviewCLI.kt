package com.claude.agent.cli

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * CLI для запуска PR ревью из командной строки или CI/CD
 *
 * Координирует процесс выполнения ревью:
 * - Парсинг и валидация аргументов
 * - Инициализация сервисов
 * - Выполнение ревью
 * - Сохранение результатов
 *
 * Использование:
 * java -jar pr-review-cli.jar review-pr --branch feature/new-feature --base-branch main --output review.md
 */
object PRReviewCLI {
    private val logger = LoggerFactory.getLogger(PRReviewCLI::class.java)

    // Компоненты CLI
    private val argumentParser = CLIArgumentParser()
    private val optionsValidator = CLIOptionsValidator()
    private val serviceInitializer = ServiceInitializer()
    private val reviewExecutor = ReviewExecutor()
    private val outputWriter = OutputWriter()
    private val usagePrinter = UsagePrinter()

    @JvmStatic
    fun main(args: Array<String>) {
        // Проверяем команду
        if (args.isEmpty() || args[0] != "review-pr") {
            usagePrinter.print()
            exitProcess(1)
        }

        try {
            // 1. Парсим аргументы
            val options = argumentParser.parse(args.drop(1))

            // 2. Валидируем опции
            when (val validationResult = optionsValidator.validate(options)) {
                is ValidationResult.Success -> {
                    // Продолжаем выполнение
                }
                is ValidationResult.Failure -> {
                    optionsValidator.logErrors(validationResult.errors)
                    exitProcess(1)
                }
            }

            logger.info("🤖 Starting AI PR Review CLI")
            logger.info("Branch: ${options.branch} → ${options.baseBranch}")

            // 3. Выполняем ревью
            val markdown = runBlocking {
                executeReview(options)
            }

            logger.info("✅ Review completed successfully")

            // 4. Сохраняем результат
            outputWriter.write(markdown, options.outputPath)

            exitProcess(0)

        } catch (e: IllegalArgumentException) {
            logger.error("❌ Invalid arguments: ${e.message}")
            println("ERROR: ${e.message}")
            println()
            usagePrinter.print()
            exitProcess(1)
        } catch (e: Exception) {
            logger.error("❌ Failed to execute PR review: ${e.message}", e)
            println("ERROR: ${e.message}")
            exitProcess(1)
        }
    }

    /**
     * Выполняет ревью PR
     */
    private suspend fun executeReview(options: ReviewOptions): String {
        val sessionId = "cli-${System.currentTimeMillis()}"

        // Инициализируем сервисы
        val services = serviceInitializer.initialize(options, sessionId)

        try {
            // Выполняем ревью
            return reviewExecutor.execute(options, services, sessionId)
        } finally {
            // Очищаем ресурсы
            services.httpClient.close()
        }
    }
}

