# KotlinAgent - Comprehensive Architecture Documentation

> **Last Updated**: 2025-12-24
> **Version**: 1.0
> **Author**: Auto-generated documentation

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Architecture & Design Patterns](#4-architecture--design-patterns)
5. [Core Components Deep Dive](#5-core-components-deep-dive)
6. [Database Architecture](#6-database-architecture)
7. [RAG System Implementation](#7-rag-system-implementation)
8. [MCP Tools System](#8-mcp-tools-system)
9. [Key Services](#9-key-services)
10. [API Reference](#10-api-reference)
11. [Data Flow Diagrams](#11-data-flow-diagrams)
12. [Configuration & Deployment](#12-configuration--deployment)
13. [Development Guide](#13-development-guide)

---

## 1. Project Overview

**KotlinAgent** is a Kotlin-based AI chatbot platform that provides a sophisticated interface for interacting with Anthropic's Claude API. Originally a Python Flask application, it has been fully rewritten in Kotlin using the Ktor framework, offering enhanced performance, type safety, and modern architectural patterns.

### Key Features

- 🤖 **Claude API Integration** - Full support for Claude 3.5 Sonnet with streaming responses
- 📚 **RAG (Retrieval-Augmented Generation)** - Semantic search over indexed documentation using vector embeddings
- 🔧 **MCP (Model Context Protocol)** - Extensible tool system supporting both local and remote tools
- 💾 **Prompt Caching** - Reduces token costs by ~90% through intelligent caching
- 📊 **Token Optimization** - Automatic history compression, tool filtering, and metrics tracking
- ⏰ **Reminder System** - Scheduled tasks with AI responses and recurring reminders
- 🔄 **WebSocket Support** - Real-time updates and streaming responses
- 📱 **Multi-platform** - Desktop UI (Compose), Web UI (HTML/JS), and HTTP API

### Use Cases

- AI-powered chatbot for technical documentation
- Development assistant with IDE integration (Android Studio)
- Automated task scheduling with AI responses
- Document Q&A with semantic search
- Multi-turn conversations with tool calling

---

## 2. Technology Stack

### Backend

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Language** | Kotlin | 2.2.20 | Main programming language |
| **Framework** | Ktor | 3.x | Async HTTP server |
| **Database** | SQLite | Latest | Persistent storage |
| **ORM** | Exposed | 0.57.0 | Type-safe database queries |
| **HTTP Client** | Ktor Client (OkHttp) | 3.x | External API calls |
| **Serialization** | kotlinx.serialization | Latest | JSON handling |
| **Logging** | Logback | 1.5.12 | Structured logging |
| **Build Tool** | Gradle | 8.14 | Build automation |

### AI/ML Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **LLM** | Claude 3.5 Sonnet (Anthropic API) | Primary language model |
| **Embeddings** | Ollama (mxbai-embed-large) | Vector embeddings for RAG |
| **Vector Search** | Custom cosine similarity | Semantic search |

### External APIs

- **Anthropic Claude API** - Primary AI service
- **Ollama** - Local embedding generation (768-dim vectors)
- **Open-Meteo** - Weather data
- **NOAA** - Solar activity data

### Development Tools

- **Java** - Version 24 (required for Kotlin 2.2.20)
- **Gradle Wrapper** - 8.14
- **dotenv-kotlin** - Environment variable management

---

## 3. Project Structure

### Module Hierarchy

```
KotlinAgent/
├── remoteAgentServer/          # Main Ktor server (primary module)
│   ├── src/main/kotlin/
│   │   └── com/claude/agent/
│   │       ├── ApplicationServer.kt       # Main entry point
│   │       ├── config/                    # Configuration classes
│   │       │   ├── AppConfig.kt
│   │       │   ├── Constants.kt
│   │       │   └── PromptCachingConfig.kt
│   │       ├── database/                  # Data access layer
│   │       │   ├── Database.kt            # Database factory
│   │       │   ├── ConversationRepository.kt
│   │       │   └── models/
│   │       │       └── Tables.kt          # Sessions, Messages, Reminders
│   │       ├── llm/                       # LLM integration
│   │       │   ├── ClaudeClient.kt        # Main Claude API client
│   │       │   └── mcp/                   # Model Context Protocol
│   │       │       ├── MCPTools.kt
│   │       │       ├── providers/
│   │       │       │   ├── LocalMcpProvider.kt
│   │       │       │   └── RemoteMcpProvider.kt
│   │       │       ├── local/             # Local MCP tools
│   │       │       │   ├── WeatherMcp.kt
│   │       │       │   ├── SolarActivityMcp.kt
│   │       │       │   ├── ActionPlannerMcp.kt
│   │       │       │   ├── ChatSummaryMcp.kt
│   │       │       │   ├── ReminderMcp.kt
│   │       │       │   └── AndroidStudioLocalMcp.kt
│   │       │       └── remote/
│   │       │           └── AirTicketsMcp.kt
│   │       ├── models/                    # Data models
│   │       │   └── ApiModels.kt
│   │       ├── routes/                    # HTTP routes
│   │       │   ├── ChatRoutes.kt
│   │       │   ├── SessionRoutes.kt
│   │       │   ├── ReminderRoutes.kt
│   │       │   ├── RagRoutes.kt
│   │       │   ├── MetricsRoutes.kt
│   │       │   ├── HealthRoutes.kt
│   │       │   └── WebSocketRoutes.kt
│   │       └── service/                   # Business logic
│   │           ├── RagService.kt
│   │           ├── OllamaEmbeddingClient.kt
│   │           ├── ReminderService.kt
│   │           ├── WebSocketService.kt
│   │           ├── GeolocationService.kt
│   │           ├── HistoryCompressor.kt
│   │           ├── TokenMetricsService.kt
│   │           ├── ToolsFilterService.kt
│   │           └── LocalAgentManager.kt
│   └── build.gradle.kts
│
├── rag/                         # RAG indexing module
│   ├── src/main/kotlin/
│   │   └── com/clauder/agent/
│   │       ├── Main.kt                    # Indexing entry point
│   │       ├── DataBase.kt                # RAG database operations
│   │       ├── DocumentReader.kt          # Document loading
│   │       ├── TextChunk.kt               # Text chunking logic
│   │       └── OllamaClient.kt            # Embedding generation
│   └── build.gradle.kts
│
├── common/                      # Shared models and tables
│   └── src/main/kotlin/
│       └── com/claude/agent/common/
│           └── database/
│               └── RagTables.kt           # DocumentChunks table
│
├── localAgentClient/            # WebSocket client for remote tools
│   └── src/main/kotlin/
│       └── com/claude/agent/client/
│           └── LocalAgent.kt
│
├── utils/                       # Utility classes
│   └── src/main/kotlin/
│       └── com/claude/agent/utils/
│
├── compose-ui/                  # Desktop UI (currently disabled)
│
├── ui/                          # Web frontend
│   └── index.html                         # Single-page app (HTML/JS/CSS)
│
├── deploy/                      # Production deployment
│   ├── deploy.sh                          # Deployment script
│   ├── kotlinagent.service                # Systemd service
│   ├── start.sh
│   └── stop.sh
│
├── docs/                        # Documentation
├── test_docs/                   # Test documentation
├── .env                         # Environment variables
├── settings.gradle.kts          # Module configuration
└── build.gradle.kts             # Root build configuration
```

### Key Files

| File | Path | Purpose | Lines |
|------|------|---------|-------|
| **ApplicationServer.kt** | `remoteAgentServer/.../ApplicationServer.kt` | Main entry point, Ktor setup | 427 |
| **ClaudeClient.kt** | `remoteAgentServer/.../llm/ClaudeClient.kt` | Claude API integration | 707 |
| **ConversationRepository.kt** | `remoteAgentServer/.../database/...` | Database operations | 415 |
| **RagService.kt** | `remoteAgentServer/.../service/RagService.kt` | Semantic search | 167 |
| **MCPTools.kt** | `remoteAgentServer/.../llm/mcp/MCPTools.kt` | Tool orchestration | 96 |
| **ChatRoutes.kt** | `remoteAgentServer/.../routes/ChatRoutes.kt` | Chat API endpoint | 155 |

---

## 4. Architecture & Design Patterns

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         Client Layer                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │
│  │   Web UI     │  │ Local Agent  │  │   HTTP Clients           │ │
│  │ (HTML/JS/WS) │  │  (WebSocket) │  │   (cURL, Postman)        │ │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP/WebSocket
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│                    Remote Agent Server (Ktor)                       │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Routing Layer                             │  │
│  │  • ChatRoutes      • SessionRoutes    • RagRoutes           │  │
│  │  • ReminderRoutes  • MetricsRoutes    • HealthRoutes        │  │
│  │  • WebSocketRoutes                                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Service Layer                             │  │
│  │  • ClaudeClient (Claude API)                                 │  │
│  │  • RagService (Semantic Search)                              │  │
│  │  • ReminderService (Scheduler)                               │  │
│  │  • WebSocketService (Broadcasting)                           │  │
│  │  • TokenMetricsService (Optimization Tracking)               │  │
│  │  • HistoryCompressor (Context Management)                    │  │
│  │  • GeolocationService (IP → Location)                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                  MCP Tools Layer                             │  │
│  │  ┌────────────────────────────────────────────────────────┐  │  │
│  │  │ MCPTools (Orchestrator)                                │  │  │
│  │  │   ├── LocalMcpProvider                                 │  │  │
│  │  │   │   ├── WeatherMcp                                   │  │  │
│  │  │   │   ├── SolarActivityMcp                             │  │  │
│  │  │   │   ├── ActionPlannerMcp                             │  │  │
│  │  │   │   ├── ChatSummaryMcp                               │  │  │
│  │  │   │   ├── ReminderMcp                                  │  │  │
│  │  │   │   └── AndroidStudioLocalMcp (via WebSocket)        │  │  │
│  │  │   └── RemoteMcpProvider                                │  │  │
│  │  │       └── AirTicketsMcp                                │  │  │
│  │  └────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Data Layer                                │  │
│  │  • DatabaseFactory (Connection Management)                   │  │
│  │  • ConversationRepository (CRUD Operations)                  │  │
│  │  • Exposed ORM (Type-safe SQL)                               │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ External API Calls
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│                      External Services                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │
│  │ Claude API   │  │   Ollama     │  │   Weather APIs           │ │
│  │ (Anthropic)  │  │ (Embeddings) │  │  (Open-Meteo, NOAA)      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

### Design Patterns

#### 1. **Repository Pattern**
- **Purpose**: Abstracts database operations
- **Implementation**: `ConversationRepository`
- **Benefits**: Testable, swappable data sources

```kotlin
class ConversationRepository {
    fun saveMessage(sessionId: String, role: String, content: String): Message?
    fun getSessionHistory(sessionId: String, limit: Int): List<Message>
    fun checkDueReminders(): List<Reminder>
}
```

#### 2. **Service Layer Pattern**
- **Purpose**: Business logic separation
- **Implementation**: `ClaudeClient`, `RagService`, `ReminderService`
- **Benefits**: Single Responsibility, reusable logic

#### 3. **Provider Pattern**
- **Purpose**: Flexible tool management
- **Implementation**: `LocalMcpProvider`, `RemoteMcpProvider`
- **Benefits**: Easy to add new tools without modifying core

```kotlin
interface McpProvider {
    fun getToolsDefinitions(enabledTools: List<String>): List<ToolDefinition>
    fun getTool(name: String): McpTool?
}
```

#### 4. **Dependency Injection (Manual)**
- **Purpose**: Loose coupling, testability
- **Implementation**: Constructor injection throughout

```kotlin
class ClaudeClient(
    private val httpClient: HttpClient,
    private val mcpTools: MCPTools,
    private val webSocketService: WebSocketService,
    // ...
)
```

#### 5. **Observer Pattern** (WebSocket Broadcasting)
- **Purpose**: Real-time updates to multiple clients
- **Implementation**: `WebSocketService.broadcastToSession()`

```kotlin
sessionConnections[sessionId]?.forEach { connection ->
    connection.send(Frame.Text(messageJson))
}
```

---

## 5. Core Components Deep Dive

### 5.1 ApplicationServer.kt

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/ApplicationServer.kt`
**Lines**: 427
**Purpose**: Application bootstrap and Ktor server configuration

#### Main Function Flow

```kotlin
fun main() {
    // 1. Database Initialization
    DatabaseFactory.init()

    // 2. SSL Certificate Generation (if needed)
    generateCertificateIfNeeded()

    // 3. Ktor Server Start
    embeddedServer(Netty, ...) {
        module()
    }.start(wait = true)
}
```

#### Module Configuration (lines 180-427)

**1. Middleware Setup**:
```kotlin
install(ContentNegotiation) {
    json(Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    })
}

install(CORS) {
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Delete)
    allowHeader(HttpHeaders.ContentType)
    allowCredentials = true
    anyHost()
}

install(CallLogging) {
    level = Level.INFO
    format { call ->
        "${call.response.status()}: ${call.request.httpMethod.value} - ${call.request.uri}"
    }
}

install(WebSockets) {
    pingPeriod = Duration.ofSeconds(15)
    timeout = Duration.ofSeconds(15)
    maxFrameSize = Long.MAX_VALUE
    masking = false
}
```

**2. Service Initialization** (lines 206-257):
```kotlin
// Database
val repository = ConversationRepository()

// WebSocket
val webSocketService = WebSocketService()

// MCP Tools
val localMcpProvider = LocalMcpProvider(...)
val remoteMcpProvider = RemoteMcpProvider(...)
val mcpTools = MCPTools(localMcpProvider, remoteMcpProvider)

// RAG
val ragService = RagService("rag_index.db")
val ollamaEmbeddingClient = OllamaEmbeddingClient(httpClient)

// Token Optimization
val tokenMetricsService = TokenMetricsService()
val toolsFilterService = ToolsFilterService()

// Claude Client
val claudeClient = ClaudeClient(
    httpClient, mcpTools, webSocketService,
    tokenMetricsService, toolsFilterService,
    ragService, ollamaEmbeddingClient
)

// History Compression
val historyCompressor = HistoryCompressor(claudeClient, tokenMetricsService)

// Reminders
val reminderService = ReminderService(repository, claudeClient, mcpTools, webSocketService)
reminderService.startScheduler()
```

**3. Routing** (lines 340-405):
```kotlin
routing {
    // WebSocket for local agents
    webSocket("/mcp/local-agent") {
        localAgentManager.handleConnection(this)
    }

    // API routes
    healthRoutes(claudeClient, mcpTools)
    chatRoutes(claudeClient, historyCompressor, repository)
    sessionRoutes(repository)
    reminderRoutes(reminderService)
    ragRoutes(ragService, ollamaEmbeddingClient)
    metricsRoutes(tokenMetricsService)
    webSocketRoutes(webSocketService)

    // Static files
    staticFiles("/ui", staticPath)
    staticFiles("/", staticPath) {
        default("index.html")
    }
}
```

---

### 5.2 ClaudeClient.kt

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/llm/ClaudeClient.kt`
**Lines**: 707
**Purpose**: Complete Claude API integration with tool calling, streaming, RAG, and caching

#### Key Methods

##### `sendMessage()` (lines 75-228)

**Signature**:
```kotlin
suspend fun sendMessage(
    userMessage: String,
    outputFormat: String = "default",
    model: String = ClaudeConfig.MODEL,
    maxTokens: Int = ClaudeConfig.MAX_TOKENS,
    specMode: Boolean = false,
    conversationHistory: List<Message> = emptyList(),
    temperature: Double = 1.0,
    enabledTools: List<String> = emptyList(),
    clientIp: String? = null,
    userLocation: UserLocation? = null,
    sessionId: String? = null,
    useRag: Boolean = false,
    ragTopK: Int = 3,
    ragMinSimilarity: Double = 0.3,
    ragFilterEnabled: Boolean = true
): Triple<String?, TokenUsage?, String?>
```

**Flow**:
1. **RAG Context Retrieval** (if enabled):
```kotlin
val ragContext = if (isRagEnabled) {
    retrieveRagContext(userMessage, ragTopK, ragMinSimilarity, ragFilterEnabled)
} else null
```

2. **System Prompt Building**:
```kotlin
val systemPrompt = SystemPrompts.getSystemPrompt(
    outputFormat = outputFormat,
    specMode = specMode,
    enabledTools = enabledTools,
    isRagEnabled = isRagEnabled
)
```

3. **Messages Construction**:
```kotlin
val messages = buildMessages(
    history = conversationHistory,
    userMessage = userMessage,
    ragContext = ragContext
)
```

4. **Tool Filtering**:
```kotlin
val (filteredLocalTools, filteredRemoteTools) = getFilteredTools(
    enabledTools, remoteMcpParams, userMessage
)
```

5. **API Request**:
```kotlin
val requestBody = buildAnthropicRequest(
    model, maxTokens, systemPrompt, messages,
    temperature, filteredLocalTools, filteredRemoteTools
)

val response = httpClient.post("https://api.anthropic.com/v1/messages") {
    header("x-api-key", apiKey)
    header("anthropic-version", "2023-06-01")
    if (PromptCachingConfig.ENABLED) {
        header("anthropic-beta", "prompt-caching-2024-07-31,mcp-client-2025-11-20")
    }
    contentType(ContentType.Application.Json)
    setBody(requestBody)
}
```

6. **Response Handling with Tool Calls**:
```kotlin
return handleResponse(
    jsonResponse, messages, systemPrompt,
    filteredLocalTools, filteredRemoteTools,
    model, maxTokens, temperature, sessionId,
    clientIp, userLocation
)
```

##### `handleResponse()` (lines 233-496)

**Purpose**: Manages multi-turn tool calling loop

**Algorithm**:
```kotlin
var iteration = 0
val MAX_TOOL_ITERATIONS = 20

while (iteration < MAX_TOOL_ITERATIONS) {
    // 1. Check for tool_use blocks
    val toolUseBlocks = content.filter { it["type"] == "tool_use" }

    if (toolUseBlocks.isEmpty()) {
        // Final response - return text
        return Pair(finalText, usage)
    }

    // 2. Execute all tools in parallel
    val toolResults = toolUseBlocks.map { block ->
        val toolName = block["name"]
        val toolInput = block["input"]
        val toolUseId = block["id"]

        val result = mcpTools.callLocalTool(
            toolName, toolInput, clientIp, userLocation, sessionId
        )

        // Real-time update via WebSocket
        webSocketService.broadcastToSession(sessionId, WebSocketMessage(
            type = "tool_result",
            sessionId = sessionId,
            data = Json.encodeToString(mapOf(
                "tool_name" to toolName,
                "result" to result
            ))
        ))

        JsonObject(mapOf(
            "type" to "tool_result",
            "tool_use_id" to toolUseId,
            "content" to result
        ))
    }

    // 3. Send tool results back to Claude
    messages.add(JsonObject(mapOf(
        "role" to "user",
        "content" to JsonArray(toolResults)
    )))

    val response = httpClient.post(apiUrl) {
        setBody(buildAnthropicRequest(...))
    }

    // 4. Parse new response
    val newContent = response["content"]

    iteration++
}

// Max iterations reached
logger.warn("Reached max tool iterations")
return Pair("⚠️ Tool iteration limit reached", usage)
```

**Loop Detection** (lines 297-316):
Prevents infinite loops by tracking recent tool calls:
```kotlin
val recentToolCalls = ArrayDeque<String>(LOOP_DETECTION_THRESHOLD)

if (recentToolCalls.count { it == toolName } >= LOOP_DETECTION_THRESHOLD) {
    logger.warn("Tool loop detected: $toolName called $LOOP_DETECTION_THRESHOLD times")
    return Pair("⚠️ Tool loop detected", usage)
}

recentToolCalls.add(toolName)
if (recentToolCalls.size > LOOP_DETECTION_THRESHOLD) {
    recentToolCalls.removeFirst()
}
```

##### `buildMessages()` (lines 498-543)

**Purpose**: Constructs messages array with RAG context and caching

**With RAG**:
```kotlin
if (ragContext != null && ragContext.isNotBlank()) {
    messages.add(JsonObject(mapOf(
        "role" to "user",
        "content" to JsonArray(listOf(
            // Block 1: RAG context (cacheable)
            JsonObject(mapOf(
                "type" to "text",
                "text" to ragContext,
                "cache_control" to JsonObject(mapOf(
                    "type" to "ephemeral"
                ))
            )),
            // Block 2: User question
            JsonObject(mapOf(
                "type" to "text",
                "text" to userMessage
            ))
        ))
    )))
}
```

**Without RAG**:
```kotlin
else {
    messages.add(JsonObject(mapOf(
        "role" to "user",
        "content" to userMessage
    )))
}
```

##### `retrieveRagContext()` (lines 660-707)

**Purpose**: Semantic search using vector embeddings

**Flow**:
```kotlin
// 1. Generate query embedding
val queryEmbedding = ollamaEmbeddingClient.embed(userMessage)

// 2. Normalize (CRITICAL - must match indexing)
val normalizedQueryEmbedding = normalizeToRange(queryEmbedding)

// 3. Apply filtering
val effectiveMinSimilarity = if (filterEnabled) minSimilarity else 0.0

// 4. Search
val results = ragService.search(
    queryEmbedding = normalizedQueryEmbedding,
    topK = topK,
    minSimilarity = effectiveMinSimilarity
)

// 5. Format
return ragService.formatContext(results)
```

**Normalization** (lines 709-723):
```kotlin
private fun normalizeToRange(vector: FloatArray): FloatArray {
    var min = Float.MAX_VALUE
    var max = Float.MIN_VALUE

    for (value in vector) {
        if (value < min) min = value
        if (value > max) max = value
    }

    val range = max - min
    return if (range > 0) {
        vector.map { (it - min) / range }.toFloatArray()
    } else {
        vector
    }
}
```

---

## 6. Database Architecture

### Schema Overview

**Two Separate Databases**:

1. **Main Database** (`conversations.db`):
   - Sessions
   - Messages
   - Reminders

2. **RAG Database** (`rag_index.db`):
   - DocumentChunks

### Table Definitions

#### Sessions Table
```kotlin
object Sessions : Table("sessions") {
    val id = varchar("id", 255).uniqueIndex()
    val title = varchar("title", 500)
    val createdAt = varchar("created_at", 50)
    val lastUpdated = varchar("last_updated", 50)

    override val primaryKey = PrimaryKey(id)
}
```

**Schema**:
| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| id | VARCHAR(255) | PRIMARY KEY, UNIQUE | Session UUID |
| title | VARCHAR(500) | NOT NULL | Session title/summary |
| created_at | VARCHAR(50) | NOT NULL | ISO 8601 timestamp |
| last_updated | VARCHAR(50) | NOT NULL | ISO 8601 timestamp |

#### Messages Table
```kotlin
object Messages : Table("messages") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 255)
        .references(Sessions.id, onDelete = CASCADE)
    val role = varchar("role", 20)
    val content = text("content")
    val timestamp = varchar("timestamp", 50)
    val inputTokens = integer("input_tokens").nullable()
    val outputTokens = integer("output_tokens").nullable()
    val read = bool("read").default(false)

    override val primaryKey = PrimaryKey(id)
}
```

**Schema**:
| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Message ID |
| session_id | VARCHAR(255) | FOREIGN KEY → sessions.id, CASCADE | Links to session |
| role | VARCHAR(20) | NOT NULL | "user" or "assistant" |
| content | TEXT | NOT NULL | Message text |
| timestamp | VARCHAR(50) | NOT NULL | ISO 8601 timestamp |
| input_tokens | INTEGER | NULLABLE | Tokens in request |
| output_tokens | INTEGER | NULLABLE | Tokens in response |
| read | BOOLEAN | DEFAULT false | Read status |

#### Reminders Table
```kotlin
object Reminders : Table("reminders") {
    val id = varchar("id", 255).uniqueIndex()
    val sessionId = varchar("session_id", 255).nullable()
    val text = varchar("text", 500)
    val due_at = varchar("due_at", 50)
    val created_at = varchar("created_at", 50)
    val updated_at = varchar("updated_at", 50)
    val done = bool("done")
    val notified = bool("notified").default(false)

    // Recurring reminders
    val recurrenceType = varchar("recurrence_type", 20).default("none")
    val recurrenceInterval = integer("recurrence_interval").default(1)
    val recurrenceEndDate = varchar("recurrence_end_date", 50).nullable()

    // AI tasks
    val taskType = varchar("task_type", 20).default("reminder")
    val taskContext = text("task_context").nullable()

    override val primaryKey = PrimaryKey(id)
}
```

**Schema**:
| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| id | VARCHAR(255) | PRIMARY KEY | Reminder UUID |
| session_id | VARCHAR(255) | NULLABLE | Optional session link |
| text | VARCHAR(500) | NOT NULL | Reminder text |
| due_at | VARCHAR(50) | NOT NULL | When to trigger |
| done | BOOLEAN | NOT NULL | Completion status |
| notified | BOOLEAN | DEFAULT false | Notification sent |
| recurrence_type | VARCHAR(20) | DEFAULT "none" | minutely/hourly/daily/weekly/monthly |
| recurrence_interval | INTEGER | DEFAULT 1 | Every N intervals |
| task_type | VARCHAR(20) | DEFAULT "reminder" | reminder/ai_response/mcp_tool |
| task_context | TEXT | NULLABLE | JSON task details |

#### DocumentChunks Table (RAG)
```kotlin
object DocumentChunks : Table("document_chunks") {
    val id = varchar("id", 500)                     // "docId:chunkIndex"
    val docId = varchar("doc_id", 500)
    val chunkIndex = integer("chunk_index")
    val text = text("text")
    val vector = blob("vector")                     // Normalized embedding
    val createdAt = varchar("created_at", 50)

    override val primaryKey = PrimaryKey(id)
    init {
        index(isUnique = false, docId)
    }
}
```

**Schema**:
| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| id | VARCHAR(500) | PRIMARY KEY | Composite: "{docId}:{chunkIndex}" |
| doc_id | VARCHAR(500) | INDEX | Source document path |
| chunk_index | INTEGER | NOT NULL | Chunk position in document |
| text | TEXT | NOT NULL | Chunk text content |
| vector | BLOB | NOT NULL | Normalized embedding (768 floats) |
| created_at | VARCHAR(50) | NOT NULL | Index timestamp |

### ConversationRepository

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/database/ConversationRepository.kt`

**Key Methods**:

```kotlin
class ConversationRepository {
    // Sessions
    fun createSession(sessionId: String, title: String): Boolean
    fun getAllSessions(): List<SessionInfo>
    fun deleteSession(sessionId: String): Boolean

    // Messages
    fun saveMessage(sessionId: String, role: String, content: String, ...): Message?
    fun getSessionHistory(sessionId: String, limit: Int): List<Message>
    fun getSessionHistoryWithStats(sessionId: String): SessionHistoryResponse
    fun markMessagesAsRead(sessionId: String): Int

    // Reminders
    fun createReminder(reminder: Reminder): Boolean
    fun getAllReminders(): List<Reminder>
    fun checkDueReminders(): List<Reminder>
    fun markNotified(reminderId: String): Boolean
    fun markDone(reminderId: String): Boolean
    fun deleteReminder(reminderId: String): Boolean
}
```

**Transaction Management**:
```kotlin
private fun <T> mainDbTransaction(
    statement: org.jetbrains.exposed.sql.Transaction.() -> T
): T {
    return transaction(DatabaseFactory.getMainDatabase(), statement)
}
```

---

## 7. RAG System Implementation

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                   RAG Indexing (Offline)                      │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  Document  │→ │ Text Chunking│→ │  Embedding          │ │
│  │   Reader   │  │  (300 words) │  │  Generation         │ │
│  │            │  │               │  │  (Ollama)           │ │
│  └────────────┘  └──────────────┘  └──────────────────────┘ │
│                                               │               │
│                                               ▼               │
│                                    ┌──────────────────────┐  │
│                                    │  Normalization       │  │
│                                    │  to [0,1] range      │  │
│                                    └──────────────────────┘  │
│                                               │               │
│                                               ▼               │
│                                    ┌──────────────────────┐  │
│                                    │  SQLite Storage      │  │
│                                    │  (DocumentChunks)    │  │
│                                    └──────────────────────┘  │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                   RAG Query (Runtime)                         │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │   User     │→ │  Embedding   │→ │  Normalization       │ │
│  │  Question  │  │  Generation  │  │  to [0,1] range      │ │
│  │            │  │  (Ollama)    │  │                      │ │
│  └────────────┘  └──────────────┘  └──────────────────────┘ │
│                                               │               │
│                                               ▼               │
│                                    ┌──────────────────────┐  │
│                                    │  Cosine Similarity   │  │
│                                    │  vs All Chunks       │  │
│                                    └──────────────────────┘  │
│                                               │               │
│                                               ▼               │
│                          ┌─────────────────────────────────┐ │
│                          │  Filtering & Ranking            │ │
│                          │  • Filter: similarity ≥ thresh  │ │
│                          │  • Sort: by similarity DESC     │ │
│                          │  • Take: top-K results          │ │
│                          └─────────────────────────────────┘ │
│                                               │               │
│                                               ▼               │
│                                    ┌──────────────────────┐  │
│                                    │  Format Context      │  │
│                                    │  (Markdown)          │  │
│                                    └──────────────────────┘  │
│                                               │               │
│                                               ▼               │
│                                    ┌──────────────────────┐  │
│                                    │  Add to Claude       │  │
│                                    │  Request (cached)    │  │
│                                    └──────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### Indexing Process

**Command**:
```bash
./gradlew :rag:run --args="./docs rag_index.db http://localhost:11434"
```

**Main.kt** (`/rag/src/main/kotlin/com/clauder/agent/Main.kt`):
```kotlin
fun main(args: Array<String>) = runBlocking {
    val docsDir = File(args[0])
    val dbPath = args.getOrElse(1) { "rag_index.db" }
    val ollamaUrl = args.getOrElse(2) { "http://localhost:11434" }

    // Initialize
    dataBase.initDatabase(dbPath)
    dataBase.initSchema()
    val ollama = OllamaClient(ollamaUrl)

    // Build index
    buildIndex(docsDir, ollama)
}
```

**Document Loading** (`DocumentReader.kt`):
```kotlin
fun loadDocuments(directory: File): Map<String, String> {
    val docs = mutableMapOf<String, String>()

    directory.walkTopDown()
        .filter { it.isFile && it.extension in listOf("md", "txt", "kt", "java", "py") }
        .forEach { file ->
            val docId = file.relativeTo(directory).path
            val text = file.readText()
            docs[docId] = text
        }

    return docs
}
```

**Text Chunking** (`TextChunk.kt`):
```kotlin
fun chunkText(docId: String, text: String, chunkSize: Int = 300, overlap: Int = 50): List<TextChunk> {
    val words = text.split(Regex("\\s+"))
    val chunks = mutableListOf<TextChunk>()
    var index = 0
    var i = 0

    while (i < words.size) {
        val end = minOf(i + chunkSize, words.size)
        val chunkText = words.subList(i, end).joinToString(" ")

        chunks.add(TextChunk(
            docId = docId,
            chunkIndex = index,
            text = chunkText
        ))

        index++
        i += chunkSize - overlap
    }

    return chunks
}
```

**Embedding Generation** (`OllamaClient.kt`):
```kotlin
suspend fun embed(text: String): FloatArray {
    val requestBody = OllamaEmbedingsRequest(
        model = "mxbai-embed-large",
        prompt = text
    )

    val response = client.post("$baseUrl/api/embeddings") {
        contentType(ContentType.Application.Json)
        setBody(requestBody)
    }

    val parsed = json.decodeFromString<OllamaEmbededResponse>(
        response.bodyAsText()
    )

    return parsed.embedding.map { it.toFloat() }.toFloatArray()
}
```

**Normalization** (`Main.kt`):
```kotlin
fun normalizeToRange(vector: FloatArray): FloatArray {
    val min = vector.minOrNull() ?: 0f
    val max = vector.maxOrNull() ?: 1f
    val range = max - min

    return if (range > 0) {
        vector.map { (it - min) / range }.toFloatArray()
    } else {
        vector
    }
}
```

**Database Storage** (`DataBase.kt`):
```kotlin
fun insertEmbedding(docId: String, chunkIndex: Int, text: String, vector: FloatArray) {
    transaction(database) {
        DocumentChunks.insert {
            it[id] = "$docId:$chunkIndex"
            it[DocumentChunks.docId] = docId
            it[DocumentChunks.chunkIndex] = chunkIndex
            it[DocumentChunks.text] = text
            it[DocumentChunks.vector] = vector.toByteArray()
            it[createdAt] = Instant.now().toString()
        }
    }
}
```

### Query Process

**RagService.search()** (`/remoteAgentServer/.../service/RagService.kt`):
```kotlin
fun search(
    queryEmbedding: FloatArray,
    topK: Int = 5,
    minSimilarity: Double = 0.0
): List<SearchResult> {
    return transaction(ragDatabase) {
        val results = mutableListOf<SearchResult>()

        // Scan all chunks
        DocumentChunks.selectAll().forEach { row ->
            val chunkVector = row[DocumentChunks.vector].toFloatArray()

            // Calculate similarity
            val similarity = cosineSimilarity(queryEmbedding, chunkVector)

            // Filter
            if (similarity >= minSimilarity) {
                results.add(SearchResult(
                    docId = row[DocumentChunks.docId],
                    chunkIndex = row[DocumentChunks.chunkIndex],
                    text = row[DocumentChunks.text],
                    similarity = similarity
                ))
            }
        }

        // Sort and limit
        results.sortedByDescending { it.similarity }.take(topK)
    }
}
```

**Cosine Similarity**:
```kotlin
private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size) { "Vectors must have same dimension" }

    var dotProduct = 0.0
    var normA = 0.0
    var normB = 0.0

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    normA = sqrt(normA)
    normB = sqrt(normB)

    return if (normA > 0 && normB > 0) {
        dotProduct / (normA * normB)
    } else {
        0.0
    }
}
```

**Context Formatting**:
```kotlin
fun formatContext(results: List<SearchResult>): String {
    if (results.isEmpty()) return ""

    return buildString {
        appendLine("# Relevant Documentation Context")
        appendLine()

        results.forEachIndexed { index, result ->
            appendLine("## Document ${index + 1}: ${result.docId} (similarity: ${"%.3f".format(result.similarity)})")
            appendLine()
            appendLine(result.text.trim())
            appendLine()
            appendLine("---")
            appendLine()
        }
    }
}
```

### RAG Integration in Claude Requests

**Content Blocks with Caching** (`ClaudeClient.buildMessages()`):
```kotlin
if (ragContext != null && ragContext.isNotBlank()) {
    logger.info("✅ Adding RAG context as separate content block with caching (${ragContext.length} chars)")

    messages.add(JsonObject(mapOf(
        "role" to "user",
        "content" to JsonArray(listOf(
            // Block 1: RAG context (cached)
            JsonObject(mapOf(
                "type" to "text",
                "text" to ragContext,
                "cache_control" to JsonObject(mapOf(
                    "type" to "ephemeral"
                ))
            )),
            // Block 2: User question
            JsonObject(mapOf(
                "type" to "text",
                "text" to userMessage
            ))
        ))
    )))
}
```

**Benefits**:
- RAG context separated from user question
- Prompt caching reduces token costs by ~90%
- Cache TTL: 5 minutes (refreshed on each use)
- Clean message history (RAG not saved to database)

---

## 8. MCP Tools System

### Overview

MCP (Model Context Protocol) provides a standardized interface for Claude to call tools/functions.

### Architecture

```
MCPTools (Orchestrator)
    │
    ├── LocalMcpProvider (tools executed on server)
    │   ├── WeatherMcp
    │   │   └── get_weather_forecast(latitude, longitude, units)
    │   │
    │   ├── SolarActivityMcp
    │   │   └── get_solar_activity()
    │   │
    │   ├── ActionPlannerMcp
    │   │   └── plan_actions(goal, current_state)
    │   │
    │   ├── ChatSummaryMcp
    │   │   └── summarize_conversation(session_id)
    │   │
    │   ├── ReminderMcp
    │   │   └── create_reminder(text, due_at, recurrence, task_type)
    │   │
    │   └── AndroidStudioLocalMcp (proxied via WebSocket)
    │       ├── control_emulator(action)
    │       ├── execute_adb(command)
    │       └── browse_files(path)
    │
    └── RemoteMcpProvider (tools via external MCP servers)
        └── AirTicketsMcp
            └── search_flights(from, to, date)
```

### MCPTools Orchestrator

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/llm/mcp/MCPTools.kt`

```kotlin
class MCPTools(
    private val localMcpProvider: LocalMcpProvider,
    private val remoteMcpProvider: RemoteMcpProvider
) {
    private val logger = LoggerFactory.getLogger(MCPTools::class.java)

    // Get tool definitions for Claude
    fun getLocalToolsDefinitions(enabledTools: List<String>): List<LocalToolDefinition> {
        return localMcpProvider.getToolsDefinitions(enabledTools)
    }

    fun getRemoteMcpParams(): JsonArray {
        return remoteMcpProvider.getMcpParams()
    }

    // Execute tool
    suspend fun callLocalTool(
        toolName: String,
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null,
        sessionId: String? = null
    ): String {
        logger.info("Executing tool: $toolName")

        val tool = localMcpProvider.getTool(toolName)
        if (tool == null) {
            logger.error("Tool not found: $toolName")
            return """{"error": "Tool $toolName not found"}"""
        }

        return try {
            tool.executeTool(arguments, clientIp, userLocation, sessionId)
        } catch (e: Exception) {
            logger.error("Tool execution failed: ${e.message}", e)
            """{"error": "${e.message}"}"""
        }
    }
}
```

### Local MCP Provider

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/llm/mcp/providers/LocalMcpProvider.kt`

```kotlin
class LocalMcpProvider(
    weatherMcp: WeatherMcp,
    solarActivityMcp: SolarActivityMcp,
    actionPlannerMcp: ActionPlannerMcp,
    chatSummaryMcp: ChatSummaryMcp,
    reminderMcp: ReminderMcp,
    androidStudioLocalMcp: AndroidStudioLocalMcp
) {
    private val tools: Map<String, McpTool> = mapOf(
        weatherMcp.tool.first to weatherMcp,
        solarActivityMcp.tool.first to solarActivityMcp,
        actionPlannerMcp.tool.first to actionPlannerMcp,
        chatSummaryMcp.tool.first to chatSummaryMcp,
        reminderMcp.tool.first to reminderMcp,
        androidStudioLocalMcp.tool.first to androidStudioLocalMcp
    )

    fun getToolsDefinitions(enabledTools: List<String>): List<LocalToolDefinition> {
        if (enabledTools.isEmpty()) {
            // All tools enabled by default
            return tools.values.map { it.tool.second }
        }

        return tools.filterKeys { it in enabledTools }
            .values
            .map { it.tool.second }
    }

    fun getTool(name: String): McpTool? = tools[name]
}
```

### Tool Definition Structure

**LocalToolDefinition**:
```kotlin
@Serializable
data class LocalToolDefinition(
    val name: String,
    val description: String,
    val enabled: Boolean = true,
    val input_schema: JsonObject
)
```

**Example: WeatherMcp** (`/remoteAgentServer/.../mcp/local/WeatherMcp.kt`):
```kotlin
class WeatherMcp(
    private val httpClient: HttpClient,
    private val geolocationService: GeolocationService
) : McpTool {

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "get_weather_forecast",
        second = LocalToolDefinition(
            name = "get_weather_forecast",
            description = """
                Получает прогноз погоды для указанного местоположения.
                Если координаты не указаны, определяет местоположение по IP.
            """.trimIndent(),
            enabled = true,
            input_schema = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "latitude" to JsonObject(mapOf(
                        "type" to JsonPrimitive("number"),
                        "description" to JsonPrimitive("Широта")
                    )),
                    "longitude" to JsonObject(mapOf(
                        "type" to JsonPrimitive("number"),
                        "description" to JsonPrimitive("Долгота")
                    )),
                    "units" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "enum" to JsonArray(listOf(
                            JsonPrimitive("metric"),
                            JsonPrimitive("imperial")
                        )),
                        "description" to JsonPrimitive("Система единиц измерения")
                    ))
                )),
                "required" to JsonArray(emptyList())
            ))
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        // 1. Get location
        val location = getLocationFromArguments(
            arguments, clientIp, userLocation, geolocationService
        )

        // 2. Call weather API
        val response = httpClient.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("current", "temperature_2m,weather_code,wind_speed_10m")
            parameter("timezone", "auto")
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val current = json["current"]?.jsonObject ?: return """{"error": "No data"}"""

        // 3. Format response
        return JsonObject(mapOf(
            "temperature" to current["temperature_2m"],
            "weather" to JsonPrimitive(getWeatherDescription(
                current["weather_code"]?.jsonPrimitive?.int ?: 0
            )),
            "wind_speed" to current["wind_speed_10m"],
            "location" to JsonObject(mapOf(
                "latitude" to JsonPrimitive(location.latitude),
                "longitude" to JsonPrimitive(location.longitude)
            ))
        )).toString()
    }
}
```

### Remote MCP (WebSocket Proxy)

**AndroidStudioLocalMcp** - Proxies commands to local agent running on developer's machine.

**Server Side** (`/remoteAgentServer/.../mcp/local/AndroidStudioLocalMcp.kt`):
```kotlin
class AndroidStudioLocalMcp(
    private val localAgentManager: LocalAgentManager
) : McpTool {

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "control_emulator",
        second = LocalToolDefinition(
            name = "control_emulator",
            description = "Controls Android emulator via ADB commands",
            enabled = true,
            input_schema = JsonObject(mapOf(
                "type" to "object",
                "properties" to JsonObject(mapOf(
                    "action" to JsonObject(mapOf(
                        "type" to "string",
                        "description" to "Action: start_app, stop_app, clear_data, etc."
                    ))
                ))
            ))
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        // Send command to local agent via WebSocket
        return localAgentManager.executeToolOnLocalAgent(
            toolName = "control_emulator",
            arguments = arguments
        )
    }
}
```

**Client Side** (`/localAgentClient/src/main/kotlin/com/claude/agent/client/LocalAgent.kt`):
```kotlin
suspend fun connectToServer(serverUrl: String) {
    client.webSocket(urlString = serverUrl) {
        // Register with server
        send(Frame.Text(Json.encodeToString(RegisterMessage(
            type = "register",
            clientId = clientId
        ))))

        // Listen for tool execution requests
        for (frame in incoming) {
            val message = Json.decodeFromString<ToolExecutionRequest>(frame.data)

            // Execute tool locally
            val result = when (message.toolName) {
                "control_emulator" -> executeAdbCommand(message.arguments)
                "browse_files" -> browseFiles(message.arguments)
                else -> """{"error": "Unknown tool"}"""
            }

            // Send result back
            send(Frame.Text(Json.encodeToString(ToolExecutionResult(
                requestId = message.requestId,
                result = result
            ))))
        }
    }
}
```

---

## 9. Key Services

### 9.1 ReminderService

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/service/ReminderService.kt`

**Purpose**: Manages scheduled tasks with three execution types:
1. Simple text reminders
2. AI-powered responses
3. MCP tool execution

#### Scheduler Loop

```kotlin
private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun startScheduler() {
    scope.launch {
        logger.info("⏰ Reminder scheduler started")
        while (isActive) {
            try {
                checkDueReminders()
            } catch (e: Exception) {
                logger.error("Scheduler error: ${e.message}", e)
            }
            delay(1_000)  // Check every second
        }
    }
}

private suspend fun checkDueReminders() {
    val dueReminders = conversationRepository.checkDueReminders()

    for (reminder in dueReminders) {
        handleReminder(reminder)
    }
}
```

#### Reminder Types

**1. Simple Reminder**:
```kotlin
private fun handleSimpleReminder(reminder: Reminder) {
    if (reminder.sessionId != null) {
        val reminderMessage = "🔔 **Напоминание:**\n\n${reminder.text}"

        val message = conversationRepository.saveMessage(
            sessionId = reminder.sessionId,
            role = "assistant",
            content = reminderMessage
        )

        webSocketService.broadcastToSession(
            reminder.sessionId,
            WebSocketMessage(
                type = "reminder_triggered",
                sessionId = reminder.sessionId,
                data = Json.encodeToString(message)
            )
        )
    }

    conversationRepository.markNotified(reminder.id)
}
```

**2. AI Response**:
```kotlin
private suspend fun handleAIResponse(reminder: Reminder) {
    // Parse task context
    val taskContext = Json.parseToJsonElement(
        reminder.taskContext ?: "{}"
    ).jsonObject

    val userRequest = taskContext["user_request"]?.jsonPrimitive?.content ?: reminder.text

    // Get conversation history
    val history = if (reminder.sessionId != null) {
        conversationRepository.getSessionHistory(reminder.sessionId, limit = 50)
    } else emptyList()

    // Call Claude API
    val (reply, usage, error) = claudeClient.sendMessage(
        userMessage = userRequest,
        conversationHistory = history,
        sessionId = reminder.sessionId,
        enabledTools = listOf("weather", "solar_activity")
    )

    if (reply != null && reminder.sessionId != null) {
        conversationRepository.saveMessage(
            reminder.sessionId,
            "assistant",
            reply,
            usage?.input_tokens,
            usage?.output_tokens
        )

        webSocketService.broadcastToSession(...)
    }

    conversationRepository.markNotified(reminder.id)
}
```

**3. MCP Tool**:
```kotlin
private suspend fun handleMCPTool(reminder: Reminder) {
    val taskContext = Json.parseToJsonElement(
        reminder.taskContext ?: "{}"
    ).jsonObject

    val toolName = taskContext["tool_name"]?.jsonPrimitive?.content ?: return
    val toolArguments = taskContext["tool_arguments"]?.jsonObject ?: JsonObject(emptyMap())

    // Execute tool
    val toolResult = mcpTools.callLocalTool(
        toolName = toolName,
        arguments = toolArguments,
        sessionId = reminder.sessionId
    )

    // Format result with Claude
    val (formattedResponse, _, _) = claudeClient.sendMessage(
        userMessage = """
            Инструмент "$toolName" вернул следующий результат:

            $toolResult

            Отформатируй результат понятным языком для пользователя.
        """.trimIndent(),
        maxTokens = 500,
        sessionId = reminder.sessionId
    )

    if (formattedResponse != null && reminder.sessionId != null) {
        conversationRepository.saveMessage(
            reminder.sessionId,
            "assistant",
            formattedResponse
        )

        webSocketService.broadcastToSession(...)
    }

    conversationRepository.markNotified(reminder.id)
}
```

#### Recurring Reminders

```kotlin
private fun handleRecurringReminder(reminder: Reminder) {
    val currentDueAt = Instant.parse(reminder.due_at)

    // Calculate next occurrence
    val nextDueAt = when (reminder.recurrenceType) {
        "minutely" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.MINUTES)
        "hourly" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.HOURS)
        "daily" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.DAYS)
        "weekly" -> currentDueAt.plus((reminder.recurrenceInterval * 7).toLong(), ChronoUnit.DAYS)
        "monthly" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.MONTHS)
        else -> return
    }

    // Check end date
    val shouldContinue = if (reminder.recurrenceEndDate != null) {
        val endDate = Instant.parse(reminder.recurrenceEndDate)
        nextDueAt.isBefore(endDate)
    } else {
        true
    }

    if (shouldContinue) {
        // Delete current
        conversationRepository.deleteReminder(reminder.id)

        // Create next occurrence
        val nextReminder = reminder.copy(
            id = UUID.randomUUID().toString(),
            due_at = nextDueAt.toString(),
            notified = false
        )
        conversationRepository.createReminder(nextReminder)
    } else {
        // End of recurrence - mark as done
        conversationRepository.markDone(reminder.id)
    }
}
```

---

### 9.2 WebSocketService

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/service/WebSocketService.kt`

**Purpose**: Real-time communication with web clients

**Architecture**:
```kotlin
class WebSocketService {
    // Map: sessionId -> Set of WebSocket connections
    private val sessionConnections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val mutex = Mutex()

    suspend fun registerConnection(sessionId: String, connection: WebSocketSession) {
        mutex.withLock {
            val connections = sessionConnections.getOrPut(sessionId) { mutableSetOf() }
            connections.add(connection)
            logger.info("WebSocket registered for session $sessionId (total: ${connections.size})")
        }
    }

    suspend fun unregisterConnection(sessionId: String, connection: WebSocketSession) {
        mutex.withLock {
            sessionConnections[sessionId]?.remove(connection)
            logger.info("WebSocket unregistered from session $sessionId")
        }
    }

    suspend fun broadcastToSession(sessionId: String, message: WebSocketMessage) {
        val connections = sessionConnections[sessionId] ?: return
        val messageJson = Json.encodeToString(message)
        val deadConnections = mutableSetOf<WebSocketSession>()

        for (connection in connections) {
            try {
                connection.send(Frame.Text(messageJson))
            } catch (e: Exception) {
                logger.error("Failed to send WebSocket message: ${e.message}")
                deadConnections.add(connection)
            }
        }

        // Clean up dead connections
        if (deadConnections.isNotEmpty()) {
            mutex.withLock {
                sessionConnections[sessionId]?.removeAll(deadConnections)
            }
        }
    }
}
```

**Message Types**:
```kotlin
@Serializable
data class WebSocketMessage(
    val type: String,      // "new_message", "streaming_text", "tool_result", "reminder_triggered"
    val sessionId: String,
    val data: String       // JSON-encoded payload
)
```

**Usage in ClaudeClient**:
```kotlin
// Broadcast tool result
webSocketService.broadcastToSession(sessionId, WebSocketMessage(
    type = "tool_result",
    sessionId = sessionId,
    data = Json.encodeToString(mapOf(
        "tool_name" to toolName,
        "result" to toolResult
    ))
))

// Broadcast streaming text
webSocketService.broadcastToSession(sessionId, WebSocketMessage(
    type = "streaming_text",
    sessionId = sessionId,
    data = Json.encodeToString(mapOf(
        "text" to streamingText,
        "is_final" to false
    ))
))
```

---

### 9.3 TokenMetricsService

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/service/TokenMetricsService.kt`

**Purpose**: Track token usage and optimization savings

**Metrics Storage**:
```kotlin
class TokenMetricsService {
    // Global counters
    private val totalInputTokens = AtomicLong(0)
    private val totalOutputTokens = AtomicLong(0)
    private val totalCachedTokens = AtomicLong(0)
    private val totalRequests = AtomicInteger(0)

    // Optimization savings
    private val compressionSavings = AtomicLong(0)
    private val cachingSavings = AtomicLong(0)
    private val toolFilteringSavings = AtomicLong(0)

    // Per-session metrics
    private val sessionMetrics = ConcurrentHashMap<String, SessionTokenMetrics>()
}
```

**Recording Usage**:
```kotlin
fun recordTokenUsage(
    sessionId: String?,
    usage: TokenUsage,
    cachedTokens: Int = 0
) {
    val inputTokens = usage.input_tokens?.toLong() ?: 0
    val outputTokens = usage.output_tokens?.toLong() ?: 0

    // Update globals
    totalInputTokens.addAndGet(inputTokens)
    totalOutputTokens.addAndGet(outputTokens)
    totalCachedTokens.addAndGet(cachedTokens.toLong())
    totalRequests.incrementAndGet()

    // Update session metrics
    if (sessionId != null) {
        sessionMetrics.compute(sessionId) { _, existing ->
            val metrics = existing ?: SessionTokenMetrics(sessionId)
            metrics.apply {
                this.inputTokens += inputTokens
                this.outputTokens += outputTokens
                this.cachedTokens += cachedTokens.toLong()
                this.requestCount++
            }
        }
    }

    // Log cache hit
    if (cachedTokens > 0) {
        logger.info("💾 Cache hit: $cachedTokens tokens saved")
        cachingSavings.addAndGet(cachedTokens.toLong())
    }
}
```

**Getting Statistics**:
```kotlin
fun getGlobalMetrics(): GlobalMetrics {
    val total = totalInputTokens.get() + totalOutputTokens.get()
    val savings = compressionSavings.get() + cachingSavings.get() + toolFilteringSavings.get()
    val requests = totalRequests.get()

    return GlobalMetrics(
        totalInputTokens = totalInputTokens.get(),
        totalOutputTokens = totalOutputTokens.get(),
        totalCachedTokens = totalCachedTokens.get(),
        totalRequests = requests,
        compressionSavings = compressionSavings.get(),
        cachingSavings = cachingSavings.get(),
        toolFilteringSavings = toolFilteringSavings.get(),
        totalSavings = savings,
        averageInputPerRequest = if (requests > 0) totalInputTokens.get() / requests else 0,
        averageOutputPerRequest = if (requests > 0) totalOutputTokens.get() / requests else 0,
        cacheHitRate = if (total > 0) totalCachedTokens.get().toDouble() / total else 0.0
    )
}
```

---

### 9.4 HistoryCompressor

**Location**: `/remoteAgentServer/src/main/kotlin/com/claude/agent/service/HistoryCompressor.kt`

**Purpose**: Compress conversation history to save context window tokens

**Strategy**:
1. Apply sliding window (keep last N messages)
2. Summarize older messages with Claude
3. Prepend summary to recent messages

**Compression Flow**:
```kotlin
suspend fun compressHistory(
    history: List<Message>,
    keepRecent: Int = 10
): List<Message> {
    // 1. Apply sliding window
    val windowedMessages = applySlidingWindow(history)

    // 2. Check if compression needed
    if (windowedMessages.size <= keepRecent) {
        return windowedMessages
    }

    // 3. Split into old and recent
    val messagesToCompress = windowedMessages.dropLast(keepRecent)
    val messagesToKeep = windowedMessages.takeLast(keepRecent)

    // 4. Create summary
    val summaryText = createSummary(messagesToCompress)

    // 5. Build compressed history
    val compressedHistory = mutableListOf<Message>()
    compressedHistory.add(
        Message(
            role = "user",
            content = "[Ранее обсуждали: $summaryText]",
            timestamp = Instant.now().toString()
        )
    )
    compressedHistory.addAll(messagesToKeep)

    // 6. Estimate savings
    val originalTokens = estimateTokens(windowedMessages)
    val compressedTokens = estimateTokens(compressedHistory)
    val savedTokens = originalTokens - compressedTokens

    tokenMetricsService?.recordCompressionSavings(savedTokens)
    logger.info("💾 Compressed history: ${windowedMessages.size} → ${compressedHistory.size} messages, saved ~$savedTokens tokens")

    return compressedHistory
}
```

**Summary Generation**:
```kotlin
private suspend fun createSummary(messages: List<Message>): String {
    if (messages.isEmpty()) return ""

    val conversationText = formatConversation(messages)

    val summaryPrompt = """
        Создай ОЧЕНЬ краткое резюме диалога (максимум 2-3 предложения).
        Укажи только ключевые темы, о которых говорили.

        Диалог:
        $conversationText

        КРАТКОЕ резюме (2-3 предложения):
    """.trimIndent()

    val (reply, _, error) = claudeClient.sendMessage(
        userMessage = summaryPrompt,
        maxTokens = 200,
        temperature = 0.0,
        conversationHistory = emptyList()
    )

    return reply?.trim() ?: fallbackSummary(messages)
}

private fun fallbackSummary(messages: List<Message>): String {
    val topics = messages
        .filter { it.role == "user" }
        .take(5)
        .map { it.content.take(50) }
        .joinToString(", ")

    return "Обсуждали: $topics..."
}
```

---

## 10. API Reference

### Endpoints

#### Chat

**POST /api/chat**

Send message to Claude with optional RAG and tools.

**Request**:
```json
{
  "message": "How to start KotlinAgent?",
  "session_id": "uuid",
  "output_format": "default",
  "max_tokens": 1024,
  "temperature": 1.0,
  "spec_mode": false,
  "conversation_history": [],
  "enabled_tools": ["weather", "reminder"],
  "user_location": {
    "latitude": 52.5,
    "longitude": 13.4,
    "source": "browser_geolocation"
  },
  "use_rag": true,
  "rag_top_k": 3,
  "rag_min_similarity": 0.3,
  "rag_filter_enabled": true
}
```

**Response**:
```json
{
  "reply": "To start KotlinAgent, run: ./run.sh",
  "usage": {
    "input_tokens": 150,
    "output_tokens": 200
  },
  "compressed_history": null,
  "compression_applied": false
}
```

---

#### Sessions

**GET /api/sessions**

List all chat sessions.

**Response**:
```json
{
  "sessions": [
    {
      "id": "uuid",
      "title": "How to start?",
      "created_at": "2025-12-24T10:00:00Z",
      "last_updated": "2025-12-24T10:30:00Z"
    }
  ]
}
```

**POST /api/sessions**

Create new session.

**Request**:
```json
{
  "session_id": "uuid",
  "title": "New Chat"
}
```

**GET /api/sessions/{id}**

Get session history.

**Response**:
```json
{
  "session_id": "uuid",
  "history": [
    {
      "role": "user",
      "content": "Hello",
      "usage": null,
      "timestamp": "2025-12-24T10:00:00Z",
      "read": false
    }
  ],
  "stats": {
    "total_messages": 10,
    "user_messages": 5,
    "assistant_messages": 5
  }
}
```

**DELETE /api/sessions/{id}**

Delete session.

---

#### Reminders

**GET /api/reminders**

List all reminders.

**POST /api/reminders**

Create reminder.

**Request**:
```json
{
  "id": "uuid",
  "session_id": "uuid",
  "text": "Call doctor",
  "due_at": "2025-12-25T10:00:00Z",
  "done": false,
  "notified": false,
  "recurrence_type": "daily",
  "recurrence_interval": 1,
  "recurrence_end_date": "2026-01-01T00:00:00Z",
  "task_type": "reminder",
  "task_context": null
}
```

---

#### RAG

**POST /api/rag/search**

Direct RAG search.

**Request**:
```json
{
  "query": "How to deploy?",
  "top_k": 5,
  "min_similarity": 0.3
}
```

**Response**:
```json
{
  "results": [
    {
      "doc_id": "docs/deployment.md",
      "chunk_index": 0,
      "text": "To deploy...",
      "similarity": 0.85
    }
  ],
  "formatted_context": "# Relevant Documentation..."
}
```

---

#### Metrics

**GET /api/metrics**

Token usage statistics.

**Response**:
```json
{
  "totalInputTokens": 50000,
  "totalOutputTokens": 30000,
  "totalCachedTokens": 10000,
  "totalRequests": 250,
  "compressionSavings": 5000,
  "cachingSavings": 10000,
  "toolFilteringSavings": 2000,
  "totalSavings": 17000,
  "averageInputPerRequest": 200,
  "averageOutputPerRequest": 120,
  "cacheHitRate": 0.2,
  "topSessions": [...]
}
```

---

#### Health

**GET /health**

Service health check.

**Response**:
```json
{
  "status": "healthy",
  "timestamp": "2025-12-24T10:00:00Z",
  "api_key_configured": true
}
```

---

#### WebSocket

**ws://{host}:{port}/ws/{sessionId}**

Subscribe to session updates.

**Messages**:
```json
{
  "type": "new_message" | "streaming_text" | "tool_result" | "reminder_triggered",
  "sessionId": "uuid",
  "data": "{...}"
}
```

---

## 11. Data Flow Diagrams

### User Request → Claude Response

```
1. HTTP POST /api/chat
   │
   ├─ ChatRequest validation
   ├─ IP address extraction
   └─ Route to ChatRoutes.chatRoutes()

2. ChatRoutes
   │
   ├─ Save user message to DB
   ├─ Compress history if needed (HistoryCompressor)
   └─ Call ClaudeClient.sendMessage()

3. ClaudeClient.sendMessage()
   │
   ├─ RAG Context (if enabled)
   │  ├─ Generate query embedding (OllamaEmbeddingClient)
   │  ├─ Normalize embedding
   │  ├─ Semantic search (RagService.search)
   │  └─ Format context
   │
   ├─ Build system prompt (with tools, RAG instructions)
   ├─ Build messages (history + RAG context)
   ├─ Filter tools (ToolsFilterService)
   │
   ├─ POST https://api.anthropic.com/v1/messages
   │  ├─ Headers: x-api-key, anthropic-beta (caching)
   │  ├─ Body: model, messages, system, tools, temperature
   │  └─ Caching: system, tools, RAG context
   │
   └─ Handle response (ClaudeClient.handleResponse)
      │
      ├─ Check for tool_use blocks
      │
      ├─ If tool_use found:
      │  ├─ Execute tool (MCPTools.callLocalTool)
      │  │  ├─ Local tool: execute on server
      │  │  └─ Remote tool: WebSocket to local agent
      │  │
      │  ├─ Broadcast tool result (WebSocketService)
      │  ├─ Send tool results back to Claude API
      │  └─ Loop (max 20 iterations)
      │
      └─ Extract final text

4. ChatRoutes
   │
   ├─ Save assistant response to DB
   ├─ Record token usage (TokenMetricsService)
   │
   └─ Return ChatResponse to client

5. HTTP 200 OK
   └─ { reply, usage, compressed_history }
```

---

## 12. Configuration & Deployment

### Environment Variables

**.env File**:
```bash
# Required
ANTHROPIC_API_KEY=sk-ant-...

# Optional (with defaults)
PORT=8001
HOST=0.0.0.0
DATABASE_PATH=conversations.db
STATIC_FOLDER=../ui
```

**Loading** (`AppConfig.kt`):
```kotlin
object AppConfig {
    init {
        // Try multiple locations for .env file
        val possibleEnvPaths = listOf(
            File(".env"),
            File("../.env"),
            File(System.getProperty("user.dir"), ".env")
        )

        val envFile = possibleEnvPaths.firstOrNull { it.exists() }
        if (envFile != null) {
            dotenv(envFile.absolutePath)
            logger.info("✅ Loaded .env from: ${envFile.absolutePath}")
        }
    }

    val anthropicApiKey: String = System.getenv("ANTHROPIC_API_KEY")
        ?: throw IllegalStateException("ANTHROPIC_API_KEY not set")

    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8001
    val host: String = System.getenv("HOST") ?: "0.0.0.0"
    val databasePath: String = System.getenv("DATABASE_PATH") ?: "conversations.db"
    val staticFolder: String = System.getenv("STATIC_FOLDER") ?: "../ui"
}
```

### SSL/TLS Configuration

**Self-Signed Certificate** (auto-generated):
```kotlin
fun generateCertificateIfNeeded() {
    val keyStoreFile = File("ktor.p12")

    if (!keyStoreFile.exists()) {
        logger.info("Generating self-signed certificate...")

        val keyStore = generateCertificate(
            file = keyStoreFile,
            keyAlias = "ktor",
            keyPassword = "changeit",
            jksPassword = "changeit",
            keySizeInBits = 2048
        )

        logger.info("✅ Certificate generated: ${keyStoreFile.absolutePath}")
    }
}
```

**HTTPS Connector**:
```kotlin
sslConnector(
    keyStore = keyStore,
    keyAlias = "ktor",
    keyStorePassword = { "changeit".toCharArray() },
    privateKeyPassword = { "changeit".toCharArray() }
) {
    port = 8443
    host = "0.0.0.0"
}
```

### Production Deployment

**deploy.sh** (`/deploy/deploy.sh`):
```bash
#!/bin/bash

SERVER="user@server.com"
REMOTE_DIR="/home/user/KotlinAgent"

# 1. Build
echo "Building application..."
./gradlew :remoteAgentServer:build

# 2. Copy files
echo "Copying files to server..."
scp remoteAgentServer/build/libs/remoteAgentServer.jar $SERVER:$REMOTE_DIR/
scp -r ui $SERVER:$REMOTE_DIR/
scp .env $SERVER:$REMOTE_DIR/
scp deploy/*.sh $SERVER:$REMOTE_DIR/

# 3. Install systemd service
echo "Installing systemd service..."
ssh $SERVER "sudo cp $REMOTE_DIR/kotlinagent.service /etc/systemd/system/"
ssh $SERVER "sudo systemctl daemon-reload"

# 4. Restart service
echo "Restarting service..."
ssh $SERVER "sudo systemctl restart kotlinagent"

# 5. Health check
sleep 5
curl http://$SERVER:8001/health
```

**Systemd Service** (`/deploy/kotlinagent.service`):
```ini
[Unit]
Description=KotlinAgent AI Chatbot
After=network.target

[Service]
Type=simple
User=agent
WorkingDirectory=/home/agent/KotlinAgent
EnvironmentFile=/home/agent/KotlinAgent/.env
ExecStart=/usr/bin/java -jar remoteAgentServer/build/libs/remoteAgentServer.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Commands**:
```bash
# Deploy
./deploy/deploy.sh

# Service management
sudo systemctl start kotlinagent
sudo systemctl stop kotlinagent
sudo systemctl restart kotlinagent
sudo systemctl status kotlinagent

# View logs
journalctl -u kotlinagent -f
```

---

## 13. Development Guide

### Prerequisites

- **Java 24** (required for Kotlin 2.2.20)
- **Ollama** (for RAG embeddings)
- **Anthropic API Key**

**Install Java 24**:
```bash
# macOS
brew install openjdk@24
export JAVA_HOME=$(/usr/libexec/java_home -v 24)

# Verify
java -version
```

**Install Ollama**:
```bash
# macOS
brew install ollama

# Start Ollama
ollama serve

# Pull embedding model
ollama pull mxbai-embed-large
```

### Running Locally

**1. Configure Environment**:
```bash
cp .env.example .env
# Edit .env and add ANTHROPIC_API_KEY
```

**2. Run Server**:
```bash
# Simple run
./run.sh

# Or with Gradle
./gradlew :remoteAgentServer:run

# Or with specific Java version
export JAVA_HOME=$(/usr/libexec/java_home -v 24)
./gradlew :remoteAgentServer:run
```

**3. Index Documents** (optional):
```bash
./gradlew :rag:run --args="./docs rag_index.db http://localhost:11434"
```

**4. Access UI**:
```
http://localhost:8001
https://localhost:8443 (self-signed SSL)
```

### Building

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :remoteAgentServer:build

# Clean build
./gradlew clean build

# Run tests
./gradlew check
```

### Project Structure Best Practices

**Adding a New MCP Tool**:

1. Create tool class in `/remoteAgentServer/.../llm/mcp/local/`:
```kotlin
class MyToolMcp : McpTool {
    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "my_tool",
        second = LocalToolDefinition(
            name = "my_tool",
            description = "What this tool does",
            input_schema = JsonObject(...)
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        // Implementation
    }
}
```

2. Register in `LocalMcpProvider`:
```kotlin
class LocalMcpProvider(..., myToolMcp: MyToolMcp) {
    private val tools: Map<String, McpTool> = mapOf(
        ...,
        myToolMcp.tool.first to myToolMcp
    )
}
```

3. Initialize in `ApplicationServer.kt`:
```kotlin
val myToolMcp = MyToolMcp(httpClient)
val localMcpProvider = LocalMcpProvider(..., myToolMcp)
```

---

## Summary

KotlinAgent is a production-ready AI chatbot platform built with modern Kotlin practices:

**Architecture Highlights**:
- Ktor-based async server with dual HTTP/HTTPS
- Multi-module Gradle structure
- Repository pattern for data access
- Service layer for business logic
- Provider pattern for extensible tools

**Key Features**:
- Full Claude API integration with tool calling
- RAG with vector search (Ollama embeddings)
- Prompt caching (90% token savings)
- History compression
- Scheduled reminders with AI responses
- WebSocket real-time updates
- Token metrics tracking

**Production Ready**:
- Systemd integration
- SSL/TLS support
- Error handling and logging
- Database migrations
- Deployment automation

**Code Quality**:
- Type-safe with Kotlin
- Coroutines for async operations
- Exposed ORM for database
- kotlinx.serialization for JSON
- Comprehensive logging

This architecture provides a solid foundation for building sophisticated AI applications with Claude, demonstrating best practices in Kotlin backend development, API integration, and real-time communication.
