package com.claude.agent.cli

/**
 * Принтер справки по использованию CLI
 * 
 * Отвечает за вывод информации о доступных командах и опциях
 */
class UsagePrinter {
    
    /**
     * Выводит справку по использованию
     */
    fun print() {
        println("""
            AI PR Review CLI

            Usage:
              java -jar pr-review-cli.jar review-pr [OPTIONS]

            Required Options:
              --branch BRANCH              Source branch to review

            Optional Options:
              --base-branch BRANCH         Target branch (default: main)
              --output PATH                Output file path (default: pr-review-report.md)
              --pr-title TITLE             Pull Request title
              --pr-description DESC        Pull Request description
              --repo-path PATH             Repository path (default: current directory)
              --enable-rag                 Enable RAG context retrieval
              --rag-db-path PATH           RAG database path (default: rag_index.db)

            Environment Variables:
              ANTHROPIC_API_KEY            Required - Your Anthropic API key

            Examples:
              # Basic usage
              java -jar pr-review-cli.jar review-pr --branch feature/new-api

              # With custom base branch and output
              java -jar pr-review-cli.jar review-pr --branch feature/auth --base-branch develop -o review.md

              # With RAG enabled
              java -jar pr-review-cli.jar review-pr --branch feature/refactor --enable-rag

              # Full example with all options
              java -jar pr-review-cli.jar review-pr \
                --branch feature/payment-integration \
                --base-branch main \
                --pr-title "Add payment gateway" \
                --pr-description "Integrates Stripe API" \
                --enable-rag \
                --output reports/pr-review.md
        """.trimIndent())
    }
}

