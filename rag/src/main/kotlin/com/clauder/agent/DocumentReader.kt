package com.clauder.agent

import java.io.File

class DocumentReader() {
    fun loadDocuments(dir: File): List<Pair<String, String>> {
        return dir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("md", "txt", "pdf", "kt", "java") }
            .map { file ->
                file.absolutePath to file.readText()
            }
            .toList()
    }
}