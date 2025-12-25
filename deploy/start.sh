#!/bin/bash
# Запуск KotlinAgent (ручной режим, без systemd)

set -e

APP_DIR="/home/agent/KotlinAgent"
JAR_FILE="$APP_DIR/remoteAgentServer/build/libs/remoteAgentServer.jar"

echo "=== Запуск KotlinAgent ==="

cd "$APP_DIR"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR файл не найден: $JAR_FILE"
    exit 1
fi

if [ ! -f ".env" ]; then
    echo "❌ .env не найден"
    exit 1
fi

# Проверяем, не запущен ли уже
if pgrep -f "remoteAgentServer.jar" > /dev/null; then
    echo "⚠️ KotlinAgent уже запущен — пропускаем старт"
    exit 0
fi

echo "Запуск: $JAR_FILE"
exec java -jar "$JAR_FILE"