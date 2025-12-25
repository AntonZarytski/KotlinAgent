#!/bin/bash
# Остановка KotlinAgent (ручной режим)

echo "=== Остановка KotlinAgent ==="

PID=$(pgrep -f "java -jar.*remoteAgentServer.jar" || true)

if [ -z "$PID" ]; then
    echo "KotlinAgent не запущен"
    exit 0
fi

echo "Останавливаем PID: $PID"
kill "$PID"

sleep 2

if ps -p "$PID" > /dev/null; then
    echo "Процесс не завершился — kill -9"
    kill -9 "$PID"
fi

echo "KotlinAgent остановлен"