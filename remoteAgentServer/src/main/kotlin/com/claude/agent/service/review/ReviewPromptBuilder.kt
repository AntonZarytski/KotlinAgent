package com.claude.agent.service.review

import com.claude.agent.common.models.PRInfo

/**
 * Построитель промптов для генерации ревью через Claude API
 * 
 * Отвечает за формирование структурированных промптов с контекстом PR
 */
class ReviewPromptBuilder {
    
    /**
     * Формирует промпт для генерации ревью
     */
    fun buildPrompt(prInfo: PRInfo, prData: PRData, ragContext: String?): String {
        return buildString {
            appendHeader()
            appendPRMetadata(prInfo)
            appendStatistics(prData)
            appendCommits(prData)
            appendChangedFiles(prData)
            appendFileDiffs(prData)
            
            if (ragContext != null) {
                appendRelevantDocumentation(ragContext)
            }
            
            appendReviewInstructions()
        }
    }

    private fun StringBuilder.appendHeader() {
        appendLine("# Pull Request Code Review")
        appendLine()
        appendLine("You are an expert code reviewer. Analyze the following Pull Request and provide a comprehensive review.")
        appendLine()
    }

    private fun StringBuilder.appendPRMetadata(prInfo: PRInfo) {
        if (prInfo.title != null) {
            appendLine("## PR Title")
            appendLine(prInfo.title)
            appendLine()
        }
        
        if (prInfo.description != null) {
            appendLine("## PR Description")
            appendLine(prInfo.description)
            appendLine()
        }

        appendLine("## Branch Information")
        appendLine("- Source branch: `${prInfo.branch}`")
        appendLine("- Target branch: `${prInfo.targetBranch}`")
        appendLine()
    }

    private fun StringBuilder.appendStatistics(prData: PRData) {
        appendLine("## Statistics")
        appendLine(prData.stats)
        appendLine()
    }

    private fun StringBuilder.appendCommits(prData: PRData) {
        appendLine("## Commits (${prData.commits.size})")
        prData.commits.take(10).forEach { commit ->
            appendLine("- **${commit.hash.take(8)}**: ${commit.message} (${commit.author}, ${commit.date})")
        }
        appendLine()
    }

    private fun StringBuilder.appendChangedFiles(prData: PRData) {
        appendLine("## Changed Files (${prData.files.size})")
        prData.files.forEach { file ->
            appendLine("- **${file.status}**: `${file.path}`")
        }
        appendLine()
    }

    private fun StringBuilder.appendFileDiffs(prData: PRData) {
        if (prData.diffs.isEmpty()) return
        
        appendLine("## File Diffs")
        prData.diffs.forEach { fileDiff ->
            appendLine()
            appendLine("### ${fileDiff.file} (${fileDiff.status})")
            appendLine("```diff")
            // Ограничиваем размер diff для каждого файла
            val truncatedDiff = if (fileDiff.diff.length > 3000) {
                fileDiff.diff.take(3000) + "\n... (truncated)"
            } else {
                fileDiff.diff
            }
            appendLine(truncatedDiff)
            appendLine("```")
        }
    }

    private fun StringBuilder.appendRelevantDocumentation(ragContext: String) {
        appendLine()
        appendLine("## Relevant Documentation")
        appendLine(ragContext)
    }

    private fun StringBuilder.appendReviewInstructions() {
        appendLine()
        appendLine("## Review Instructions")
        appendLine()
        appendLine("Provide a structured code review with the following sections:")
        appendLine()
        appendLine("### 1. SUMMARY")
        appendLine("A brief overview of the changes (2-3 sentences)")
        appendLine()
        appendLine("### 2. OVERALL ASSESSMENT")
        appendLine("Rate: APPROVE / REQUEST_CHANGES / COMMENT")
        appendLine("Explanation of the rating")
        appendLine()
        appendLine("### 3. DETAILED COMMENTS")
        appendLine("List specific issues found in the code. For each comment use the format:")
        appendLine("- **[SEVERITY]** `file.kt:line` - Description of the issue")
        appendLine("  - Suggestion: How to fix it")
        appendLine()
        appendLine("Severity levels: CRITICAL, WARNING, INFO, SUGGESTION")
        appendLine()
        appendLine("### 4. SUGGESTIONS FOR IMPROVEMENT")
        appendLine("General recommendations for enhancing code quality")
        appendLine()
        appendLine("### 5. COMPLIANCE CHECKS")
        appendLine("Verify adherence to:")
        appendLine("- Code style and formatting standards")
        appendLine("- Documentation and comments")
        appendLine("- Test coverage")
        appendLine("- Security best practices")
        appendLine("- Performance considerations")
        appendLine()
        appendLine("Format: `[✓/✗] Category: Details`")
    }
}

