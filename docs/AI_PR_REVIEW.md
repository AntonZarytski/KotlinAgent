# AI Pull Request Review Pipeline

Автоматизированная система для ревью Pull Request'ов с использованием Claude AI, интегрированная с GitHub Actions.

## 🎯 Возможности

- ✅ **Автоматический триггер** на создание/обновление PR
- 🔍 **Анализ изменений** через GitRepositoryMcp (diff, commits, file history)
- 📚 **RAG контекстуализация** - поиск релевантной документации
- 🤖 **AI-генерация ревью** с детальными комментариями
- 📤 **Автоматическая публикация** в PR через GitHub API

## 📋 Структура компонентов

### 1. PRReviewService (`remoteAgentServer/src/main/kotlin/com/claude/agent/service/PRReviewService.kt`)

Основной сервис для анализа PR:

```kotlin
suspend fun reviewPR(prInfo: PRInfo, sessionId: String?, repoPath: String?): PRReview
```

**Этапы работы:**
1. Сбор данных о PR через GitRepositoryMcp:
   - `compare_branches` - список измененных файлов
   - `get_branch_commits` - коммиты в ветке
   - `get_diff` - содержимое изменений
2. Получение контекста из RAG (опционально)
3. Генерация ревью через Claude API
4. Парсинг и структурирование результата

**Структура ревью:**
```kotlin
data class PRReview(
    val summary: String,                       // Краткое описание
    val overallAssessment: String,             // APPROVE / REQUEST_CHANGES / COMMENT
    val comments: List<ReviewComment>,         // Детальные замечания
    val suggestions: List<String>,             // Рекомендации
    val complianceChecks: List<ComplianceCheck> // Проверки стандартов
)

data class ReviewComment(
    val file: String,
    val line: Int?,
    val severity: Severity,  // CRITICAL, WARNING, INFO, SUGGESTION
    val message: String,
    val suggestion: String?
)
```

### 2. GitHub Actions Workflow (`.github/workflows/pr-review.yml`)

Автоматический пайплайн для CI/CD:

```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened]
```

**Шаги:**
1. Checkout кода с полной историей (`fetch-depth: 0`)
2. Установка JDK 17 и Gradle
3. Установка Ollama для RAG embeddings
4. Сборка проекта
5. Запуск AI ревью через bash скрипт
6. Публикация результата в PR

**Необходимые secrets:**
- `ANTHROPIC_API_KEY` - API ключ Claude
- `GITHUB_TOKEN` - автоматически предоставляется GitHub

### 3. CLI инструмент (`remoteAgentServer/src/main/kotlin/com/claude/agent/cli/PRReviewCLI.kt`)

Standalone утилита для запуска ревью из командной строки:

```bash
java -jar app.jar review-pr --branch feature/new-feature --output review.md
```

**Опции:**
- `--branch` - ветка для ревью (обязательно)
- `--base-branch` - целевая ветка (по умолчанию: main)
- `--output` - путь для сохранения результата
- `--pr-title` - заголовок PR
- `--pr-description` - описание PR
- `--repo-path` - путь к репозиторию
- `--enable-rag` - включить RAG контекст
- `--rag-db-path` - путь к RAG базе данных

### 4. Bash скрипт (`scripts/run-pr-review.sh`)

Обёртка для запуска в CI окружении:

```bash
#!/bin/bash
./gradlew :remoteAgentServer:shadowJar
java -jar remoteAgentServer/build/libs/remoteAgentServer-all.jar review-pr ...
```

Автоматически:
- Компилирует проект
- Запускает CLI с параметрами из environment
- Публикует результат в PR через GitHub API

## 🚀 Быстрый старт

### Локальное использование

1. **Установите зависимости:**
```bash
# Ollama для RAG (опционально)
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
ollama pull nomic-embed-text
```

2. **Настройте environment:**
```bash
export ANTHROPIC_API_KEY="your-key-here"
```

3. **Соберите проект:**
```bash
./gradlew :remoteAgentServer:shadowJar
```

4. **Запустите ревью:**
```bash
# Базовый запуск
java -jar remoteAgentServer/build/libs/remoteAgentServer-all.jar \
  review-pr --branch feature/my-feature

# С RAG и кастомными параметрами
java -jar remoteAgentServer/build/libs/remoteAgentServer-all.jar \
  review-pr \
  --branch feature/auth \
  --base-branch develop \
  --enable-rag \
  --output reports/pr-review.md \
  --pr-title "Add authentication"
```

### Настройка CI/CD

1. **Добавьте secrets в GitHub:**
   - Settings → Secrets and variables → Actions
   - Добавьте `ANTHROPIC_API_KEY`

2. **Скопируйте workflow файл:**
```bash
mkdir -p .github/workflows
cp .github/workflows/pr-review.yml .github/workflows/
```

3. **Сделайте скрипт исполняемым:**
```bash
chmod +x scripts/run-pr-review.sh
```

4. **Создайте PR и проверьте:**
   - Workflow автоматически запустится
   - Результат будет опубликован как комментарий
   - Artifact с полным отчётом будет доступен в Actions

## 📊 Пример ревью

Результат публикуется в формате Markdown:

```markdown
# 🤖 AI Code Review

**Branch:** `feature/auth` → `main`

## 📝 Summary
Implements JWT-based authentication with secure token storage...

## 🎯 Overall Assessment
**REQUEST_CHANGES** - The implementation is solid but requires...

## 💬 Detailed Comments

### 🔴 CRITICAL (1)
- **`src/auth/TokenService.kt:45`**
  Security issue: Hardcoded secret key detected
  > 💡 Suggestion: Move to environment variable

### 🟡 WARNING (3)
- **`src/auth/AuthController.kt:89`**
  Missing error handling for expired tokens
  
...

## 💡 Suggestions for Improvement
- Add unit tests for token expiration
- Implement rate limiting for login endpoint
- Document authentication flow in README

## ✅ Compliance Checks
- ✅ **Code style**: Follows Kotlin conventions
- ❌ **Test coverage**: Missing tests for critical paths
- ✅ **Documentation**: API endpoints documented
- ❌ **Security**: Hardcoded credentials detected
```

## 🔧 Настройка и конфигурация

### RAG Integration

Для улучшения качества ревью используется RAG для поиска релевантной документации:

1. **Индексация документации:**
```bash
# Добавьте документацию в rag/rag_data/
# Запустите индексацию
./gradlew :rag:run
```

2. **Включение в workflow:**
```yaml
# В .github/workflows/pr-review.yml уже настроено
- name: Install Ollama
  run: ollama pull nomic-embed-text
```

### Кастомизация промптов

Промпт для генерации ревью находится в `PRReviewService.buildReviewPrompt()`:

```kotlin
private fun buildReviewPrompt(prInfo: PRInfo, prData: PRData, ragContext: String?): String {
    // Можно модифицировать секции:
    // - SUMMARY
    // - OVERALL ASSESSMENT
    // - DETAILED COMMENTS
    // - SUGGESTIONS
    // - COMPLIANCE CHECKS
}
```

### Настройка severity levels

В `PRReviewService.kt` определены уровни важности:

```kotlin
enum class Severity {
    CRITICAL,   // Блокирующие проблемы безопасности
    WARNING,    // Важные замечания, требующие внимания
    INFO,       // Информационные комментарии
    SUGGESTION  // Рекомендации по улучшению
}
```

## 🧪 Тестирование

### Локальное тестирование

```bash
# Создайте тестовую ветку
git checkout -b test/pr-review
git commit --allow-empty -m "Test commit"
git push origin test/pr-review

# Запустите ревью локально
export ANTHROPIC_API_KEY="your-key"
java -jar remoteAgentServer/build/libs/remoteAgentServer-all.jar \
  review-pr --branch test/pr-review
```

### Отладка в CI

Проверьте логи GitHub Actions:
```
Actions → Your PR → ai-review job → Logs
```

Скачайте артефакт с полным отчётом:
```
Actions → Your PR → Artifacts → pr-review-report
```

## 📈 Метрики и мониторинг

Сервис логирует следующие метрики:

- ✅ Количество проанализированных файлов
- ✅ Количество найденных замечаний (по severity)
- ✅ Использование токенов Claude API
- ✅ Время выполнения анализа
- ✅ RAG релевантность (если включено)

Просмотр метрик:
```bash
# Логи в GitHub Actions
cat logs/pr-review.log

# Локально
tail -f logs/pr-review.log
```

## ⚡ Оптимизация производительности

### Ограничения для больших PR

В `PRReviewService.collectPRData()`:

```kotlin
// Ограничение файлов для diff анализа
val diffs = files.take(20).mapNotNull { file -> ... }

// Ограничение размера diff
val truncatedDiff = if (fileDiff.diff.length > 3000) {
    fileDiff.diff.take(3000) + "\n... (truncated)"
} else {
    fileDiff.diff
}
```

### Настройка RAG параметров

```kotlin
// В PRReviewService.retrieveRelevantContext()
val results = ragService.search(
    queryEmbedding = normalizedEmbedding,
    topK = 5,              // Количество релевантных документов
    minSimilarity = 0.7    // Порог релевантности
)
```

### Кэширование Claude API

Используется prompt caching для экономии токенов:
- Системный промпт кэшируется
- RAG контекст кэшируется
- Экономия до 90% на повторных запросах

## 🔒 Безопасность

- ✅ API ключи хранятся в GitHub Secrets
- ✅ GITHUB_TOKEN с минимальными правами (read:contents, write:pull-requests)
- ✅ Нет логирования sensitive данных
- ✅ Ollama работает локально (эмбеддинги не уходят наружу)

## 🐛 Troubleshooting

### Проблема: "Git repository not found"
```bash
# Проверьте fetch-depth в workflow
- uses: actions/checkout@v4
  with:
    fetch-depth: 0  # Должно быть 0 для полной истории
```

### Проблема: "Ollama connection failed"
```bash
# Проверьте, что Ollama запущен
ollama serve &
sleep 5  # Дайте время на запуск
```

### Проблема: "RAG context not found"
```bash
# Проверьте наличие RAG базы
ls -la rag_index.db

# Переиндексируйте документацию
./gradlew :rag:run
```

## 📚 Дополнительные ресурсы

- [GitRepositoryMcp API](../remoteAgentServer/src/main/kotlin/com/claude/agent/llm/mcp/local/GitRepositoryMcp.kt)
- [RAG Integration Guide](../rag/README.md)
- [Claude API Documentation](https://docs.anthropic.com/claude/docs)
- [GitHub Actions Workflow Syntax](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)

## 🤝 Contributing

Для улучшения системы ревью:

1. Добавьте тесты для новых типов анализа
2. Улучшите промпты для более точных рекомендаций
3. Добавьте поддержку других Git платформ (GitLab, Bitbucket)
4. Оптимизируйте RAG для специфичных типов проектов

## 📄 Лицензия

Следует лицензии основного проекта KotlinAgent.
