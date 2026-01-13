#!/bin/bash

# Скрипт для настройки только ngrok туннеля (без запуска сервера)
# Используйте если сервер уже запущен

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🌐 Setting up Ngrok Tunnel Only"
echo "================================"

# Проверяем наличие ngrok
if ! command -v ngrok &> /dev/null; then
    echo "❌ ngrok is not installed"
    echo "📥 Please install ngrok:"
    echo "   macOS: brew install ngrok"
    echo "   Linux: https://ngrok.com/download"
    exit 1
fi

# Проверяем authtoken
if ! ngrok config check &> /dev/null; then
    echo "⚠️  ngrok authtoken not configured"
    echo "📝 Please sign up at https://ngrok.com and get your authtoken"
    read -p "Enter your ngrok authtoken: " NGROK_TOKEN
    ngrok config add-authtoken "$NGROK_TOKEN"
fi

# Проверяем порт
PORT=${PORT:-8001}
echo "🔍 Checking server on port $PORT..."

if ! lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null ; then
    echo "❌ No server running on port $PORT"
    echo "💡 Please start the server first:"
    echo "   ./gradlew :remoteAgentServer:run"
    exit 1
fi
#
## Проверяем доступность API
#if ! curl -sf --max-time 2 http://localhost:$PORT/api/health > /dev/null 2>&1; then
#    echo "⚠️  Server is running but health check failed"
#    echo "💡 Make sure the server is fully started and accessible"
#    exit 1
#fi
#
#echo "✅ Server is healthy on port $PORT"

# Останавливаем существующий ngrok если запущен
if [ -f /tmp/ngrok.pid ]; then
    OLD_NGROK_PID=$(cat /tmp/ngrok.pid)
    if ps -p $OLD_NGROK_PID > /dev/null 2>&1; then
        echo "🔴 Stopping existing ngrok (PID: $OLD_NGROK_PID)..."
        kill $OLD_NGROK_PID 2>/dev/null || true
        sleep 2
    fi
    rm /tmp/ngrok.pid
fi

# Запускаем ngrok
echo "🌐 Starting ngrok tunnel..."
ngrok http $PORT --log=stdout > /tmp/ngrok.log 2>&1 &
NGROK_PID=$!
echo $NGROK_PID > /tmp/ngrok.pid

# Ждем запуска ngrok
echo "⏳ Waiting for ngrok to start..."
sleep 3

# Получаем публичный URL
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[0].public_url')

if [ -z "$NGROK_URL" ] || [ "$NGROK_URL" = "null" ]; then
    echo "❌ Failed to get ngrok URL"
    echo "📋 Ngrok log:"
    tail -20 /tmp/ngrok.log
    kill $NGROK_PID 2>/dev/null || true
    exit 1
fi

# Сохраняем URL
echo "$NGROK_URL" > /tmp/ngrok-url.txt

SERVER_PID=$(lsof -ti:$PORT | head -1)

echo ""
echo "✅ Ngrok tunnel is ready!"
echo "========================="
echo "🌐 Public URL: $NGROK_URL"
echo "🏠 Local URL: http://localhost:$PORT"
echo "🆔 Server PID: $SERVER_PID"
echo "🆔 Ngrok PID: $NGROK_PID"
echo ""
echo "📝 Next steps:"
echo "1. Add to GitHub Secrets:"
echo "   PR_REVIEW_LOCAL_SERVER = $NGROK_URL"
echo ""
echo "2. Test the tunnel:"
echo "   curl $NGROK_URL/api/pr-review/health"
echo ""
echo "🛑 To stop ngrok:"
echo "   kill $NGROK_PID"
echo "   # or"
echo "   ./scripts/stop-local-pr-review.sh"
echo ""
echo "🌐 Ngrok Web Inspector:"
echo "   http://localhost:4040"
echo ""
echo "📊 Monitor ngrok traffic:"
echo "   tail -f /tmp/ngrok.log"
