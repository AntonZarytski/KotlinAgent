# PR Review CLI - Модульная архитектура

## 📋 Обзор

PR Review CLI был рефакторен в отдельный модуль для улучшения модульности и переиспользования кода.

## 🏗️ Структура модулей

```
KotlinAgent/
├── common/                          # Общие модели и утилиты
│   └── src/main/kotlin/com/claude/agent/common/
│       └── models/
│           └── PRModels.kt          # PRInfo, PRReview, ReviewComment, etc.
│
├── remoteAgentServer/               # Ktor сервер и бизнес-логика
│   └── src/main/kotlin/com/claude/agent/
│       ├── service/
│       │   └── PRReviewService.kt   # Сервис для ревью PR
│       └── routes/
│           └── PRReviewRoutes.kt    # HTTP API endpoints
│
└── pr-review-cli/                   # CLI приложение (новый модуль)
    ├── build.gradle.kts
    └── src/main/kotlin/com/claude/agent/cli/
        └── PRReviewCLI.kt           # Entry point для CLI
```

## 🔗 Граф зависимостей

```
pr-review-cli
    ├── remoteAgentServer (сервисы и бизнес-логика)
    │   ├── common (модели данных)
    │   └── utils
    └── common (модели данных)

remoteAgentServer (HTTP сервер)
    ├── common (модели данных)
    └── utils
```

## 📦 Модули

### 1. `common` - Общие компоненты

**Назначение**: Переиспользуемые модели данных и утилиты

**Содержимое**:
- `PRInfo` - информация о Pull Request
- `PRReview` - результат ревью
- `ReviewComment` - комментарий к коду
- `Severity` - уровень важности (CRITICAL, WARNING, INFO, SUGGESTION)
- `ComplianceCheck` - проверка соответствия стандартам

**Зависимости**:
- `kotlinx-serialization` - для сериализации моделей
- `exposed` - для работы с БД

### 2. `remoteAgentServer` - Серверная логика

**Назначение**: Ktor HTTP сервер и бизнес-логика

**Содержимое**:
- `PRReviewService` - сервис для выполнения ревью
- `PRReviewRoutes` - HTTP API endpoints
- `ClaudeClient` - интеграция с Claude API
- `MCPTools` - инструменты для работы с кодом

**API Endpoints**:
- `POST /api/pr-review` - запуск ревью
- `POST /api/pr-review/webhook` - GitHub webhook

### 3. `pr-review-cli` - CLI приложение

**Назначение**: Standalone CLI для запуска ревью из командной строки или CI/CD

**Содержимое**:
- `PRReviewCLI` - entry point
- Парсинг аргументов командной строки
- Инициализация сервисов
- Вывод результатов

**Зависимости**:
- `remoteAgentServer` - для доступа к сервисам
- `common` - для моделей данных

## 🚀 Использование

### Сборка CLI

```bash
# Собрать JAR файл
./gradlew :pr-review-cli:jar

# JAR будет создан в:
# pr-review-cli/build/libs/pr-review-cli.jar
```

### Запуск CLI

```bash
# Через wrapper скрипт (рекомендуется)
./scripts/pr-review-cli.sh review-pr --branch feature/new-api

# Напрямую через JAR
java -jar pr-review-cli/build/libs/pr-review-cli.jar review-pr --branch feature/new-api

# С дополнительными опциями
./scripts/pr-review-cli.sh review-pr \
  --branch feature/payment \
  --base-branch main \
  --pr-title "Add payment gateway" \
  --enable-rag \
  --output reports/review.md
```

### Использование в GitHub Actions

```yaml
- name: Build PR Review CLI
  run: ./gradlew :pr-review-cli:jar

- name: Run AI Code Review
  env:
    ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
  run: |
    java -jar pr-review-cli/build/libs/pr-review-cli.jar \
      review-pr \
      --branch ${{ github.head_ref }} \
      --base-branch ${{ github.base_ref }} \
      --output pr-review-report.md
```

## ✅ Преимущества новой архитектуры

1. **Модульность**
   - CLI отделен от серверной логики
   - Легко добавлять новые интерфейсы (GUI, API, etc.)

2. **Переиспользование кода**
   - Модели в `common` используются всеми модулями
   - Сервисы из `remoteAgentServer` доступны для CLI и API

3. **Независимая сборка**
   - CLI можно собрать отдельно: `./gradlew :pr-review-cli:jar`
   - Сервер можно собрать отдельно: `./gradlew :remoteAgentServer:jar`

4. **Упрощенное тестирование**
   - Модели можно тестировать изолированно
   - CLI логику можно тестировать без запуска сервера

5. **Обратная совместимость**
   - HTTP API остается без изменений
   - CLI команды работают так же как раньше

## 🔧 Разработка

### Добавление новых моделей

Добавьте модели в `common/src/main/kotlin/com/claude/agent/common/models/`:

```kotlin
@Serializable
data class NewModel(
    val field: String
)
```

### Использование моделей в других модулях

```kotlin
import com.claude.agent.common.models.PRInfo
import com.claude.agent.common.models.PRReview

val prInfo = PRInfo(
    branch = "feature/new",
    targetBranch = "main"
)
```

## 📚 Дополнительная документация

- [AI PR Review Setup](AI_PR_REVIEW_SETUP.md) - настройка для GitHub Actions
- [Local PR Review Setup](LOCAL_PR_REVIEW_SETUP.md) - локальная настройка

