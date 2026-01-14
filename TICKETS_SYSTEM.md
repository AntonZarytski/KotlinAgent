# Система управления тикетами поддержки

## Обзор

Реализована полноценная система управления тикетами с автоматическим созданием через AI и интеграцией в чат.

## Компоненты

### 1. База данных

**Таблица `tickets`:**
- `id` - уникальный ID (T-001, T-002, ...)
- `session_id` - ID сессии чата (обязательное поле)
- `title` - заголовок тикета
- `description` - описание проблемы
- `status` - OPEN, IN_PROGRESS, WAITING_FOR_USER, RESOLVED, CLOSED
- `priority` - LOW, MEDIUM, HIGH, CRITICAL
- `category` - категория проблемы
- `created_at`, `updated_at`, `resolved_at`, `closed_at` - временные метки
- `assigned_to` - кому назначен
- `tags` - JSON массив тегов
- `auto_created` - создан автоматически AI

**ВАЖНО:** Тикеты привязаны к сессиям чата, а не к пользователям. Это позволяет отслеживать проблемы в контексте конкретной беседы.

### 2. TicketRepository

CRUD операции для работы с тикетами:
- `createTicket()` - создание нового тикета
- `getTicket()` - получение по ID
- `getUserTickets()` - все тикеты пользователя
- `getSessionTickets()` - тикеты сессии
- `getAllTickets()` - список с пагинацией
- `updateTicket()` - обновление тикета
- `updateTicketStatus()` - обновление статуса
- `deleteTicket()` - удаление

### 3. SupportService

Расширен новыми возможностями:
- `analyzeProblem()` - анализ вопроса для определения необходимости создания тикета
- `createTicketFromAnalysis()` - автоматическое создание тикета на основе анализа

**Анализ проблемы:**
AI определяет:
- Нужен ли тикет
- Заголовок и описание
- Приоритет (LOW/MEDIUM/HIGH/CRITICAL)
- Категорию (authentication/api/configuration/bug/feature_request/other)
- Теги
- Обоснование решения

### 4. API Endpoints

#### Создание тикета
```http
POST /api/tickets
Content-Type: application/json

{
  "userId": "user_001",
  "sessionId": "session-123",
  "title": "Не работает авторизация",
  "description": "При попытке войти получаю ошибку 401",
  "priority": "HIGH",
  "category": "authentication",
  "tags": ["auth", "api-key"]
}
```

#### Получение списка тикетов
```http
GET /api/tickets?page=0&pageSize=20
```

#### Получение тикета по ID
```http
GET /api/tickets/T-001
```

#### Обновление тикета
```http
PATCH /api/tickets/T-001
Content-Type: application/json

{
  "status": "RESOLVED",
  "priority": "MEDIUM"
}
```

#### Удаление тикета
```http
DELETE /api/tickets/T-001
```

#### Тикеты сессии
```http
GET /api/sessions/{sessionId}/tickets
```

## Автоматическое создание тикетов

### Пример использования

```kotlin
// 1. Анализ проблемы
val analysis = supportService.analyzeProblem(
    question = "Не могу войти в систему, получаю ошибку 401",
    userId = "user_001",
    sessionId = "session-123"
)

// 2. Если нужен тикет - создаем автоматически
if (analysis.needsTicket) {
    val ticket = supportService.createTicketFromAnalysis(
        analysis = analysis,
        userId = "user_001",
        sessionId = "session-123",
        originalQuestion = "Не могу войти в систему"
    )
    println("Created ticket: ${ticket?.id}")
}
```

### Логика определения необходимости тикета

**Тикет создается если:**
- Пользователь сообщает о проблеме или ошибке
- Требуется помощь в решении технической проблемы
- Запрашивается функциональность, которая не работает
- Проблема требует отслеживания и решения

**Тикет НЕ создается если:**
- Это простой вопрос с быстрым ответом
- Запрос общей информации
- Благодарность или обратная связь

## Интеграция с чатом

### Связь тикетов с сессиями

Каждый тикет может быть привязан к сессии чата через поле `session_id`. Это позволяет:
- Отслеживать контекст создания тикета
- Показывать связанные тикеты в чате
- Автоматически обновлять статус при решении проблемы

### Автоматическое обновление статуса

AI анализирует ответы пользователя и может автоматически:
- Перевести тикет в статус `RESOLVED` когда проблема решена
- Предложить закрыть тикет (статус `CLOSED`)
- Определить прогресс решения проблемы

## Следующие шаги

### TODO: UI компоненты (не реализовано)
- [ ] Панель "Тикеты" в основном интерфейсе
- [ ] Список тикетов с фильтрацией
- [ ] Детальный вид тикета
- [ ] Секция "Связанные тикеты" в чате
- [ ] WebSocket уведомления об изменениях

### TODO: Интеграция в чат (не реализовано)
- [ ] Автоматическое создание тикетов при обращении
- [ ] Отображение связанных тикетов в чате
- [ ] Кнопки для изменения статуса
- [ ] Уведомления о создании/обновлении тикетов

## Тестирование

```bash
# Создать тикет
curl -X POST http://localhost:8000/api/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_001",
    "title": "Test ticket",
    "description": "Test description",
    "priority": "MEDIUM"
  }'

# Получить список
curl http://localhost:8000/api/tickets

# Получить тикет
curl http://localhost:8000/api/tickets/T-001

# Обновить статус
curl -X PATCH http://localhost:8000/api/tickets/T-001 \
  -H "Content-Type: application/json" \
  -d '{"status": "RESOLVED"}'
```

## Архитектура

```
User Question
     ↓
SupportService.analyzeProblem()
     ↓
ProblemAnalysis (AI определяет нужен ли тикет)
     ↓
[if needsTicket]
     ↓
SupportService.createTicketFromAnalysis()
     ↓
TicketRepository.createTicket()
     ↓
Database (tickets table)
```

## Статусы тикетов

- **OPEN** - новый тикет, ожидает обработки
- **IN_PROGRESS** - тикет в работе
- **WAITING_FOR_USER** - ожидание ответа от пользователя
- **RESOLVED** - проблема решена
- **CLOSED** - тикет закрыт

## Приоритеты

- **LOW** - низкий приоритет
- **MEDIUM** - средний приоритет (по умолчанию)
- **HIGH** - высокий приоритет
- **CRITICAL** - критический приоритет

