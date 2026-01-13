package com.claude.agent.cli

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Записыватель результатов ревью
 * 
 * Отвечает за сохранение результатов в файл и вывод в консоль
 */
class OutputWriter {
    private val logger = LoggerFactory.getLogger(OutputWriter::class.java)

    /**
     * Сохраняет результат ревью в файл и выводит в консоль
     * 
     * @param markdown Отформатированный Markdown с результатами
     * @param outputPath Путь к выходному файлу
     */
    fun write(markdown: String, outputPath: String) {
        // Сохраняем в файл
        saveToFile(markdown, outputPath)
        
        // Выводим в консоль
        printToConsole(markdown)
    }

    /**
     * Сохраняет результат в файл
     */
    private fun saveToFile(markdown: String, outputPath: String) {
        try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(markdown)
            
            logger.info("📄 Review saved to: $outputPath")
        } catch (e: Exception) {
            logger.error("❌ Failed to save review to file: ${e.message}", e)
            throw OutputWriteException("Failed to save review to $outputPath", e)
        }
    }

    /**
     * Выводит результат в консоль
     */
    private fun printToConsole(markdown: String) {
        println()
        println("=".repeat(80))
        println(markdown)
        println("=".repeat(80))
    }
}

/**
 * Исключение при ошибке записи результата
 */
class OutputWriteException(message: String, cause: Throwable) : Exception(message, cause)

