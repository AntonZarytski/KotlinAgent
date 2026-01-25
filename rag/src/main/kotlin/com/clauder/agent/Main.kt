package com.clauder.agent

import com.claude.agent.common.database.normalizeToRange
import kotlinx.coroutines.runBlocking
import java.io.File

val documentReader = DocumentReader()
val dataBase = DataBase()

/**
 * Main entry point for RAG index building
 *
 * Usage:
 *   ./gradlew :rag:run --args="<docs_directory> [db_path] [ollama_url]"
 *
 * Example:
 *   ./gradlew :rag:run --args="./docs"
 *   ./gradlew :rag:run --args="./docs rag_index.db http://localhost:11434"
 */
fun main(args: Array<String>) = runBlocking {
    println("🚀 RAG Index Builder")
    println("=" .repeat(50))

    // Parse arguments
    val docsDir = if (args.isNotEmpty()) {
        File(args[0])
    } else {
        println("❌ Error: Please provide documents directory path")
        println("Usage: ./gradlew :rag:run --args=\"<docs_directory> [db_path] [ollama_url]\"")
        return@runBlocking
    }

    if (!docsDir.exists() || !docsDir.isDirectory) {
        println("❌ Error: Directory not found: ${docsDir.absolutePath}")
        return@runBlocking
    }

    val dbPath = if (args.size > 1) args[1] else "rag_index.db"
    val ollamaUrl = if (args.size > 2) args[2] else "http://localhost:11434"

    println("📂 Documents directory: ${docsDir.absolutePath}")
    println("💾 Database path: $dbPath")
    println("🤖 Ollama URL: $ollamaUrl")
    println()

    // Initialize database with Exposed
    dataBase.initDatabase(dbPath)
    dataBase.initSchema()

    // Initialize Ollama client
    val ollama = OllamaClient(ollamaUrl)

    try {
        // Build index
        buildIndex(docsDir, ollama)

        // Print statistics
        val totalChunks = dataBase.getChunkCount()
        println()
        println("=" .repeat(50))
        println("✅ Index built successfully!")
        println("📊 Total chunks indexed: $totalChunks")
        println("💾 Database: $dbPath")

    } catch (e: Exception) {
        println("❌ Error building index: ${e.message}")
        e.printStackTrace()
    } finally {
        ollama.close()
    }
}

/**
 * Build index from documents directory
 */
suspend fun buildIndex(
    docsDir: File,
    ollama: OllamaClient
) {
    val docs = documentReader.loadDocuments(docsDir)

    if (docs.isEmpty()) {
        println("⚠️  No documents found in ${docsDir.absolutePath}")
        return
    }

    println("📚 Found ${docs.size} documents")
    println()

    for ((index, docPair) in docs.withIndex()) {
        val (docId, text) = docPair
        println("📄 [${index + 1}/${docs.size}] Processing: $docId")
        println("   📝 Text length: ${text.length} characters")

        // Разбиваем на чанки
        val chunks = chunkText(docId, text)
        println("   ✂️  Created ${chunks.size} chunks")

        // Обрабатываем чанки батчами для экономии памяти
        val batchSize = 10
        var processedChunks = 0

        for (batchStart in chunks.indices step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, chunks.size)
            val batch = chunks.subList(batchStart, batchEnd)

            for (chunk in batch) {
                print("   🔹 Chunk ${chunk.index + 1}/${chunks.size} - Generating embedding...")

                val embedding = ollama.embed(chunk.text)
                val normalized = normalizeToRange(embedding)

                dataBase.insertEmbedding(
                    docId = chunk.docId,
                    chunkIndex = chunk.index,
                    text = chunk.text,
                    vector = normalized
                )

                processedChunks++
                println(" ✓ (${processedChunks}/${chunks.size})")
            }

            // Подсказка GC освободить память после батча
            System.gc()
        }

        println("   ✅ Document processed: $processedChunks chunks")
        println()
    }

    println("🎉 Index building complete!")
    println("📊 Total chunks: ${dataBase.getChunkCount()}")
}