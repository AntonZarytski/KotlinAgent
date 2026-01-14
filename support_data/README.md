# Support Data - AI-ассистент службы поддержки

Этот каталог содержит данные для AI-ассистента службы поддержки KotlinAgent.

## Структура

```
support_data/
├── README.md              # Этот файл
├── users.json            # База пользователей
├── tickets.json          # База тикетов поддержки
└── docs/                 # Документация для RAG
    ├── authentication.md
    └── api-integration.md
```

## Компоненты системы

### 1. SupportService
**Путь:** `remoteAgentServer/src/main/kotlin/com/claude/agent/service/SupportService.kt`

Основной сервис для обработки запросов поддержки. Интегрирует:
- **RAG** - поиск релевантной документации
- **MCP support_crm** - доступ к данным пользователей и тикетов
- **Claude API** - генерация персонализированных ответов

### 2. SupportMcp
**Путь:** `remoteAgentServer/src/main/kotlin/com/claude/agent/llm/mcp/local/SupportMcp.kt`

MCP инструмент для работы с CRM системой. Предоставляет функции:
- `get_user` - получить информацию о пользователе
- `get_ticket` - получить детали тикета
- `search_tickets` - найти тикеты пользователя
- `update_ticket_status` - обновить статус тикета
- `get_user_history` - получить историю обращений

### 3. Support Routes
**Путь:** `remoteAgentServer/src/main/kotlin/com/claude/agent/routes/SupportRoutes.kt`

API endpoints:
- `POST /support/ask` - задать вопрос службе поддержки
- `POST /support/ticket/update` - обновить статус тикета
- `GET /support/user/{userId}` - получить информацию о пользователе
- `GET /support/ticket/{ticketId}` - получить информацию о тикете

## Использование

### Запрос к службе поддержки

```bash
curl -X POST http://localhost:8000/support/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Как настроить API ключ?",
    "userId": "user_001",
    "ticketId": "T-001",
    "useRag": true,
    "ragTopK": 5,
    "ragMinSimilarity": 0.7
  }'
```

**Ответ:**
```json
{
  "answer": "Персонализированный ответ от AI...",
  "sources": [
    {
      "docId": "authentication.md",
      "chunkIndex": 0,
      "text": "Фрагмент документации...",
      "similarity": 0.95
    }
  ],
  "userContext": {
    "id": "user_001",
    "name": "Иван Петров",
    "email": "ivan@example.com",
    "status": "ACTIVE"
  },
  "ticketContext": {
    "id": "T-001",
    "title": "Не работает авторизация",
    "status": "OPEN",
    "priority": "HIGH"
  },
  "confidence": 0.85,
  "suggestedActions": [
    "Проверить конфигурацию",
    "Проверить логи"
  ]
}
```

## Добавление документации

1. Создайте markdown файл в `support_data/docs/`
2. Запустите индексацию RAG:

```bash
./gradlew :rag:run --args="index support_data/docs"
```

3. Документация будет доступна для поиска через RAG

## Добавление пользователей и тикетов

Отредактируйте `users.json` и `tickets.json` в формате JSON:

**users.json:**
```json
[
  {
    "id": "user_001",
    "name": "Иван Петров",
    "email": "ivan@example.com",
    "status": "ACTIVE",
    "registeredAt": "2024-01-15T10:00:00Z",
    "lastActivity": "2024-12-20T15:30:00Z"
  }
]
```

**tickets.json:**
```json
[
  {
    "id": "T-001",
    "userId": "user_001",
    "title": "Не работает авторизация",
    "description": "При попытке войти получаю ошибку...",
    "status": "OPEN",
    "priority": "HIGH",
    "category": "authentication",
    "createdAt": "2024-12-20T10:00:00Z",
    "updatedAt": "2024-12-20T10:00:00Z",
    "tags": ["auth", "api-key"]
  }
]
```

## Тестирование

Запустите тестовый скрипт:

```bash
./test_support_api.sh
```

## Архитектура

```
User Question
     ↓
SupportService
     ├─→ RAG (поиск в документации)
     ├─→ MCP support_crm (данные пользователя/тикета)
     └─→ Claude API (генерация ответа)
          ↓
Персонализированный ответ
```

## Дополнительная информация

- [Документация по аутентификации](docs/authentication.md)
- [Руководство по API интеграции](docs/api-integration.md)

