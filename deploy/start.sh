#!/bin/bash
# Скрипт запуска KotlinAgent на VPS

set -e

APP_DIR="/home/agent/KotlinAgent"
JAR_FILE="$APP_DIR/app/build/libs/app.jar"

echo "=== Запуск KotlinAgent ==="

# Переходим в директорию проекта
cd "$APP_DIR"

# Проверяем наличие JAR файла
if [ ! -f "$JAR_FILE" ]; then
    echo "Ошибка: JAR файл не найден: $JAR_FILE"
    echo "Запустите сборку: ./gradlew :app:build"
    exit 1
fi

# Проверяем .env
if [ ! -f ".env" ]; then
    echo "Ошибка: файл .env не найден!"
    echo "Создайте .env на основе .env.example"
    exit 1
fi

# Запускаем приложение
echo "Запуск из JAR: $JAR_FILE"
java -jar "$JAR_FILE"
