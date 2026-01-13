#!/bin/bash

# Скрипт для остановки локального PR Review сервера и ngrok

echo "🛑 Stopping Local PR Review Server..."

# Останавливаем сервер
if [ -f /tmp/pr-review-server.pid ]; then
    SERVER_PID=$(cat /tmp/pr-review-server.pid)
    if ps -p $SERVER_PID > /dev/null 2>&1; then
        echo "🔴 Stopping server (PID: $SERVER_PID)..."
        kill $SERVER_PID
        rm /tmp/pr-review-server.pid
        echo "✅ Server stopped"
    else
        echo "ℹ️  Server is not running"
        rm /tmp/pr-review-server.pid
    fi
else
    echo "ℹ️  Server PID file not found"
fi

# Останавливаем ngrok
if [ -f /tmp/ngrok.pid ]; then
    NGROK_PID=$(cat /tmp/ngrok.pid)
    if ps -p $NGROK_PID > /dev/null 2>&1; then
        echo "🔴 Stopping ngrok (PID: $NGROK_PID)..."
        kill $NGROK_PID
        rm /tmp/ngrok.pid
        echo "✅ Ngrok stopped"
    else
        echo "ℹ️  Ngrok is not running"
        rm /tmp/ngrok.pid
    fi
else
    echo "ℹ️  Ngrok PID file not found"
fi

# Очистка временных файлов
rm -f /tmp/ngrok-url.txt /tmp/ngrok.log

# Альтернативный метод - убиваем все процессы на порту
PORT=${PORT:-8001}
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  Found process still using port $PORT, killing..."
    lsof -ti:$PORT | xargs kill -9 2>/dev/null || true
fi

# Убиваем все процессы ngrok
pkill -f ngrok 2>/dev/null || true

echo "✅ Cleanup complete"
