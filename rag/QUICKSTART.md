# RAG Module - Quick Start Guide

## Prerequisites Setup

### 1. Install Ollama

**macOS:**
```bash
brew install ollama
```

**Linux:**
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Windows:**
Download from https://ollama.com/download

### 2. Start Ollama Service

```bash
ollama serve
```

Keep this running in a separate terminal.

### 3. Pull the Embedding Model

```bash
ollama pull nomic-embed-text
```

### 4. Verify Ollama is Running

```bash
curl http://localhost:11434/api/version
```

You should see a JSON response with version information.

## Running the RAG Index Builder

### Build the Module

```bash
./gradlew :rag:build
```

### Run with Test Documents

```bash
./gradlew :rag:run --args="./test_docs"
```

### Run with Your Own Documents

```bash
./gradlew :rag:run --args="/path/to/your/documents"
```

### Custom Database Path

```bash
./gradlew :rag:run --args="./test_docs my_custom_index.db"
```

### Custom Ollama URL

```bash
./gradlew :rag:run --args="./test_docs rag_index.db http://192.168.1.100:11434"
```

## Expected Output

```
🚀 RAG Index Builder
==================================================
📂 Documents directory: /Users/anton/IdeaProjects/KotlinAgent/test_docs
💾 Database path: rag_index.db
🤖 Ollama URL: http://localhost:11434

✅ Database schema initialized
📚 Found 3 documents

📄 [1/3] Processing: /Users/anton/IdeaProjects/KotlinAgent/test_docs/README.md
   📝 Text length: 1234 characters
   ✂️  Created 1 chunks
   🔹 Chunk 0/0 - Generating embedding... ✓
   ✅ Document processed

📄 [2/3] Processing: /Users/anton/IdeaProjects/KotlinAgent/test_docs/kotlin_basics.md
   📝 Text length: 2345 characters
   ✂️  Created 1 chunks
   🔹 Chunk 0/0 - Generating embedding... ✓
   ✅ Document processed

📄 [3/3] Processing: /Users/anton/IdeaProjects/KotlinAgent/test_docs/example.kt
   📝 Text length: 1567 characters
   ✂️  Created 1 chunks
   🔹 Chunk 0/0 - Generating embedding... ✓
   ✅ Document processed

==================================================
✅ Index built successfully!
📊 Total chunks indexed: 3
💾 Database: rag_index.db
```

## Supported File Types

- `.md` - Markdown files
- `.txt` - Text files
- `.kt` - Kotlin source files
- `.java` - Java source files

## Troubleshooting

### Error: "Connection refused"

**Problem:** Ollama is not running

**Solution:**
```bash
# Start Ollama in a separate terminal
ollama serve
```

### Error: "Model not found"

**Problem:** The embedding model is not downloaded

**Solution:**
```bash
ollama pull nomic-embed-text
```

### Error: "Directory not found"

**Problem:** The specified directory doesn't exist

**Solution:**
```bash
# Check the path
ls -la /path/to/your/documents

# Use absolute path
./gradlew :rag:run --args="/absolute/path/to/documents"
```

### Build Errors

**Solution:**
```bash
# Clean and rebuild
./gradlew :rag:clean :rag:build

# Check Java version (should be 21)
java -version

# Check Kotlin version
./gradlew :rag:dependencies | grep kotlin
```

## What Happens During Indexing?

1. **Document Loading**: Scans directory recursively for supported files
2. **Text Chunking**: Splits each document into ~800 token chunks with 100 token overlap
3. **Embedding Generation**: Sends each chunk to Ollama for embedding generation
4. **Normalization**: Normalizes vectors to [0, 1] range using min-max normalization
5. **Storage**: Stores chunk text + normalized vector in SQLite database

## Database Location

By default, the database is created in the current directory as `rag_index.db`.

To inspect the database:
```bash
sqlite3 rag_index.db

# View schema
.schema

# Count chunks
SELECT COUNT(*) FROM document_chunks;

# View sample data
SELECT id, doc_id, chunk_index, substr(text, 1, 50) as text_preview 
FROM document_chunks 
LIMIT 5;

# Exit
.quit
```

## Next Steps

After building the index, you can:
- Query the database for similar chunks (requires implementing similarity search)
- Use the embeddings for RAG-based question answering
- Integrate with your application for semantic search

See `README.md` for more detailed documentation.

