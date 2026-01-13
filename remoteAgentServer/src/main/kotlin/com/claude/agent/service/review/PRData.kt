package com.claude.agent.service.review

/**
 * Данные о Pull Request, собранные из Git репозитория
 */
data class PRData(
    val files: List<FileChange>,
    val commits: List<CommitInfo>,
    val diffs: List<FileDiff>,
    val stats: String
)

/**
 * Информация об измененном файле
 */
data class FileChange(
    val status: String,
    val path: String
)

/**
 * Информация о коммите
 */
data class CommitInfo(
    val hash: String,
    val author: String,
    val message: String,
    val date: String
)

/**
 * Diff для конкретного файла
 */
data class FileDiff(
    val file: String,
    val status: String,
    val diff: String
)

