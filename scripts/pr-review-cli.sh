#!/bin/bash

# Wrapper скрипт для запуска PR Review CLI
# Автоматически собирает проект и запускает CLI с переданными аргументами

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

# Проверяем наличие JAR файла
JAR_FILE="pr-review-cli/build/libs/pr-review-cli.jar"

if [ ! -f "$JAR_FILE" ] || [ "$1" == "--rebuild" ]; then
    echo "🔨 Building PR Review CLI..."
    ./gradlew :pr-review-cli:jar
    
    # Удаляем --rebuild из аргументов если он был
    if [ "$1" == "--rebuild" ]; then
        shift
    fi
fi

echo "🚀 Running PR Review CLI..."
java -jar "$JAR_FILE" "$@"

