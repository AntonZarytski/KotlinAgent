# KotlinAgent

## Требования

- **Java 24** (Kotlin 2.2.20 не поддерживает Java 25)
- Gradle 8.14 (используется через wrapper)

## Настройка

1. Создайте файл `.env` в корне проекта (или скопируйте `.env.example`):
```bash
ANTHROPIC_API_KEY=sk-ant-...
PORT=8000
HOST=0.0.0.0
DATABASE_PATH=conversations.db
STATIC_FOLDER=ui
```

## Запуск

### Простой запуск
```bash
./run.sh
```

### Или с явным указанием Java 24
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 24)
./gradlew :app:run
```

Сервер будет доступен по адресам:
- HTTP: `http://localhost:8001`
- HTTPS: `https://localhost:8443` (самоподписанный сертификат)

### Другие команды
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `app` and `utils` subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).