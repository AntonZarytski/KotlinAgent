# RAG Integration Guide

## Обзор

Система RAG (Retrieval-Augmented Generation) интегрирована с Anthropic Claude API для предоставления релевантного контекста из документации при ответах на запросы пользователей.

## Архитектура

```
User Query → Ollama Embedding → Vector Search → Top-K Chunks → Claude API (with context)
```

### Компоненты

1. **`:rag` модуль** - индексация документов
   - `OllamaClient` - генерация embeddings через Ollama
   - `DataBase` - хранение векторов в SQLite
   - `TextChunk` - разбиение текста на чанки

2. **`:common` модуль** - общие модели данных
   - `DocumentChunks` - Exposed таблица для векторов
   - Утилиты конвертации `FloatArray ↔ ByteArray`

3. **`:remoteAgentServer` модуль** - поиск и интеграция
   - `RagService` - семантический поиск по векторам
   - `OllamaEmbeddingClient` - генерация query embeddings
   - `ClaudeClient` - интеграция RAG контекста в промпты

## Использование

### 1. Индексация документов

```bash
# Убедитесь что Ollama запущен
ollama pull nomic-embed-text

# Индексируйте документы
./gradlew :rag:run --args="./docs"

# Результат: rag_index.db с векторными embeddings
```

### 2. Запуск сервера с RAG

```bash
# Убедитесь что rag_index.db существует в корне проекта
./gradlew :remoteAgentServer:run
```

### 3. API запросы с RAG

#### Обычный запрос (без RAG)

```bash
curl -X POST https://localhost:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Как работает Kotlin coroutines?",
    "use_rag": false
  }'
```

#### Запрос с RAG контекстом

```bash
curl -X POST https://localhost:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Как работает Kotlin coroutines?",
    "use_rag": true,
    "rag_top_k": 3
  }'
```

**Параметры:**
- `use_rag` (boolean) - включить RAG поиск
- `rag_top_k` (int) - количество релевантных чанков (по умолчанию: 3)

### 4. Тестирование RAG поиска

#### Поиск релевантных документов

```bash
curl -X POST https://localhost:8000/api/rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "coroutines",
    "top_k": 5,
    "min_similarity": 0.3
  }'
```

**Ответ:**
```json
{
  "results": [
    {
      "doc_id": "docs/kotlin_basics.md",
      "chunk_index": 2,
      "text": "Kotlin coroutines are...",
      "similarity": 0.87
    }
  ],
  "formatted_context": "# Relevant Documentation Context\n\n..."
}
```

#### Статистика RAG базы

```bash
curl https://localhost:8000/api/rag/stats
```

**Ответ:**
```json
{
  "total_chunks": 150,
  "unique_documents": 12,
  "database_path": "rag_index.db",
  "ollama_available": true
}
```

## Как это работает

### 1. Индексация (`:rag` модуль)

```kotlin
// 1. Загрузка документов
val docs = loadDocuments("./docs")

// 2. Разбиение на чанки (800 токенов, overlap 100)
val chunks = chunkText(docId, text)

// 3. Генерация embeddings через Ollama
val embedding = ollamaClient.embed(chunk.text)

// 4. Нормализация [0, 1]
val normalized = normalizeToRange(embedding)

// 5. Сохранение в SQLite
database.insertEmbedding(docId, chunkIndex, text, normalized)
```

### 2. Поиск (`:remoteAgentServer`)

```kotlin
// 1. Генерация embedding для запроса
val queryEmbedding = ollamaEmbeddingClient.embed(userQuery)

// 2. Косинусное сходство со всеми чанками
val results = ragService.search(
    queryEmbedding = queryEmbedding,
    topK = 3,
    minSimilarity = 0.3
)

// 3. Форматирование контекста
val context = ragService.formatContext(results)
```

### 3. Интеграция с Claude

```kotlin
// RAG контекст добавляется в начало системного промпта
val systemPrompt = """
# Relevant Documentation Context

## Document 1: kotlin_basics.md (similarity: 0.87)
Kotlin coroutines are lightweight threads...

---

$originalSystemPrompt
"""

// Отправка в Claude API
claudeClient.sendMessage(
    userMessage = query,
    systemPrompt = systemPrompt
)
```

## Конфигурация

### Параметры чанкинга (`:rag/Main.kt`)

```kotlin
const val CHUNK_SIZE = 800      // токенов
const val OVERLAP = 100         // токенов
```

### Параметры поиска (`RagService.kt`)

```kotlin
val minSimilarity = 0.3  // Минимальный порог сходства (0.0 - 1.0)
val topK = 3             // Количество результатов
```

### Модель embeddings

```kotlin
// Ollama model (в OllamaEmbeddingClient)
val model = "nomic-embed-text"  // 768 dimensions
```

## Требования

1. **Ollama** должен быть запущен на `http://localhost:11434`
2. **Модель** `nomic-embed-text` должна быть установлена
3. **База данных** `rag_index.db` должна существовать (создается через `:rag:run`)

## Troubleshooting

### Ollama не доступен

```
ERROR: Ollama server not available at http://localhost:11434
```

**Решение:**
```bash
# Запустите Ollama
ollama serve

# Установите модель
ollama pull nomic-embed-text
```

### RAG база не найдена

```
WARN: RAG service initialization failed
```

**Решение:**
```bash
# Создайте индекс
./gradlew :rag:run --args="./docs"
```

### Низкое качество результатов

- Увеличьте `rag_top_k` (больше контекста)
- Уменьшите `min_similarity` (менее строгий фильтр)
- Переиндексируйте с меньшим `CHUNK_SIZE` (более точные чанки)

## Примеры использования

### Python клиент

```python
import requests

response = requests.post(
    "https://localhost:8000/api/chat",
    json={
        "message": "Explain Kotlin sealed classes",
        "use_rag": True,
        "rag_top_k": 5
    },
    verify=False  # для self-signed сертификата
)

print(response.json()["reply"])
```

### JavaScript клиент

```javascript
const response = await fetch("https://localhost:8000/api/chat", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    message: "What are Kotlin data classes?",
    use_rag: true,
    rag_top_k: 3
  })
});

const data = await response.json();
console.log(data.reply);
```

