#!/bin/bash
# Скрипт остановки KotlinAgent

echo "=== Остановка KotlinAgent ==="

# Ищем процесс Java с app.jar
PID=$(ps aux | grep "java -jar.*app.jar" | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "KotlinAgent не запущен"
    exit 0
fi

echo "Останавливаем процесс PID: $PID"
kill $PID

# Ждём завершения
sleep 2

# Проверяем, что процесс завершён
if ps -p $PID > /dev/null; then
    echo "Процесс не завершился, используем kill -9"
    kill -9 $PID
fi

echo "KotlinAgent остановлен"
