package com.claude.agent.cli

/**
 * Парсер аргументов командной строки для PR Review CLI
 * 
 * Отвечает за разбор и валидацию аргументов командной строки
 */
class CLIArgumentParser {
    
    /**
     * Парсит аргументы командной строки
     * 
     * @param args Аргументы командной строки (без команды review-pr)
     * @return Опции ревью
     * @throws IllegalArgumentException если аргументы некорректны
     */
    fun parse(args: List<String>): ReviewOptions {
        val options = ReviewOptions()
        var i = 0

        while (i < args.size) {
            when (args[i]) {
                "--branch" -> {
                    options.branch = getNextArgument(args, i, "--branch")
                    i += 2
                }
                "--base-branch" -> {
                    options.baseBranch = getNextArgument(args, i, "--base-branch")
                    i += 2
                }
                "--output", "-o" -> {
                    options.outputPath = getNextArgument(args, i, "--output")
                    i += 2
                }
                "--pr-title" -> {
                    options.prTitle = getNextArgument(args, i, "--pr-title")
                    i += 2
                }
                "--pr-description" -> {
                    options.prDescription = getNextArgument(args, i, "--pr-description")
                    i += 2
                }
                "--repo-path" -> {
                    options.repoPath = getNextArgument(args, i, "--repo-path")
                    i += 2
                }
                "--enable-rag" -> {
                    options.enableRag = true
                    i++
                }
                "--rag-db-path" -> {
                    options.ragDbPath = getNextArgument(args, i, "--rag-db-path")
                    i += 2
                }
                else -> {
                    throw IllegalArgumentException("Unknown option: ${args[i]}")
                }
            }
        }

        return options
    }

    /**
     * Получает следующий аргумент или выбрасывает исключение
     */
    private fun getNextArgument(args: List<String>, currentIndex: Int, optionName: String): String {
        if (currentIndex + 1 >= args.size) {
            throw IllegalArgumentException("Missing value for $optionName")
        }
        return args[currentIndex + 1]
    }
}

/**
 * Опции для выполнения ревью
 */
data class ReviewOptions(
    var branch: String = "",
    var baseBranch: String = "main",
    var outputPath: String = "pr-review-report.md",
    var prTitle: String? = null,
    var prDescription: String? = null,
    var repoPath: String = System.getProperty("user.dir"),
    var enableRag: Boolean = false,
    var ragDbPath: String = "rag_index.db"
)

