#!/bin/bash

# Скрипт для настройки локального сервера PR Review с ngrok туннелем
# Позволяет GitHub Actions обращаться к локальному серверу на вашем ПК

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🚀 Setting up Local PR Review Server"
echo "===================================="

# Проверяем наличие ngrok
if ! command -v ngrok &> /dev/null; then
    echo "❌ ngrok is not installed"
    echo "📥 Installing ngrok..."
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        brew install ngrok/ngrok/ngrok
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        curl -s https://ngrok-agent.s3.amazonaws.com/ngrok.asc | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null
        echo "deb https://ngrok-agent.s3.amazonaws.com buster main" | sudo tee /etc/apt/sources.list.d/ngrok.list
        sudo apt update && sudo apt install ngrok
    else
        echo "⚠️  Please install ngrok manually: https://ngrok.com/download"
        exit 1
    fi
fi

# Проверяем authtoken
if ! ngrok config check &> /dev/null; then
    echo "⚠️  ngrok authtoken not configured"
    echo "📝 Please sign up at https://ngrok.com and get your authtoken"
    read -p "Enter your ngrok authtoken: " NGROK_TOKEN
    ngrok config add-authtoken "$NGROK_TOKEN"
fi

# Проверяем .env файл
ENV_FILE="$PROJECT_ROOT/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "❌ .env file not found at $ENV_FILE"
    echo "Please create .env with ANTHROPIC_API_KEY"
    exit 1
fi

# Загружаем переменные
source "$ENV_FILE"

if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "❌ ANTHROPIC_API_KEY not set in .env"
    exit 1
fi

echo "✅ Environment configured"

# Проверяем порт
PORT=${PORT:-8080}
echo "🔍 Checking port $PORT..."

SERVER_ALREADY_RUNNING=false

if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null ; then
    echo "ℹ️  Port $PORT is already in use"

    # Проверяем доступность API
    if curl -sf --max-time 2 http://localhost:$PORT/api/health > /dev/null 2>&1; then
        echo "✅ Server is already running and healthy"
        SERVER_ALREADY_RUNNING=true

        # Сохраняем PID существующего процесса
        EXISTING_PID=$(lsof -ti:$PORT | head -1)
        echo $EXISTING_PID > /tmp/pr-review-server.pid
        echo "🆔 Server PID: $EXISTING_PID"
    else
        echo "⚠️  Port is in use but server health check failed"
        read -p "Kill the process and restart server? (y/n): " KILL_PROCESS
        if [ "$KILL_PROCESS" != "y" ]; then
            echo "❌ Cannot proceed. Please free port $PORT or fix the server"
            exit 1
        fi
        lsof -ti:$PORT | xargs kill -9
        echo "✅ Process killed"
    fi
fi

# Запускаем сервер только если он еще не запущен
if [ "$SERVER_ALREADY_RUNNING" = false ]; then
    echo "🔨 Building project..."
    cd "$PROJECT_ROOT"
    ./gradlew :remoteAgentServer:build -x test

    echo "🚀 Starting Ktor server on port $PORT..."
    ./gradlew :remoteAgentServer:run > /tmp/pr-review-server.log 2>&1 &
    SERVER_PID=$!
    echo $SERVER_PID > /tmp/pr-review-server.pid

    # Ждем запуска сервера
    echo "⏳ Waiting for server to start..."
    for i in {1..30}; do
        if curl -sf --max-time 2 http://localhost:$PORT/api/health > /dev/null 2>&1; then
            echo "✅ Server started successfully (PID: $SERVER_PID)"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "❌ Server failed to start"
            echo "📋 Last 20 lines of server log:"
            tail -20 /tmp/pr-review-server.log
            kill $SERVER_PID 2>/dev/null || true
            exit 1
        fi
        sleep 1
    done
fi

# Запускаем ngrok
echo "🌐 Starting ngrok tunnel..."
ngrok http $PORT --log=stdout > /tmp/ngrok.log &
NGROK_PID=$!
echo $NGROK_PID > /tmp/ngrok.pid

# Ждем запуска ngrok
sleep 3

# Получаем публичный URL
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[0].public_url')

if [ -z "$NGROK_URL" ] || [ "$NGROK_URL" = "null" ]; then
    echo "❌ Failed to get ngrok URL"
    kill $SERVER_PID $NGROK_PID 2>/dev/null || true
    exit 1
fi

SERVER_PID=$(cat /tmp/pr-review-server.pid 2>/dev/null || echo "unknown")

echo ""
echo "✅ Setup complete!"
echo "===================="
echo "🌐 Public URL: $NGROK_URL"
echo "🏠 Local URL: http://localhost:$PORT"
echo "🆔 Server PID: $SERVER_PID"
echo "🆔 Ngrok PID: $NGROK_PID"
if [ "$SERVER_ALREADY_RUNNING" = true ]; then
    echo "ℹ️  Server was already running (not started by this script)"
fi
echo ""
echo "📝 Next steps:"
echo "1. Go to your GitHub repository settings"
echo "2. Settings → Secrets and variables → Actions"
echo "3. Add/Update secret: PR_REVIEW_LOCAL_SERVER"
echo "   Value: $NGROK_URL"
echo ""
echo "4. (Optional) Set repository variable:"
echo "   Settings → Secrets and variables → Actions → Variables tab"
echo "   Name: PR_REVIEW_MODE"
echo "   Value: local   (or 'remote-or-local' for fallback)"
echo ""
echo "🧪 Test the server:"
echo "curl -X POST $NGROK_URL/api/pr-review \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"branch\":\"test\",\"baseBranch\":\"main\"}'"
echo ""
echo "🛑 To stop servers run:"
echo "./scripts/stop-local-pr-review.sh"
echo ""

# Сохраняем URL для последующего использования
echo "$NGROK_URL" > /tmp/ngrok-url.txt

echo "Press Ctrl+C to stop (servers will continue running in background)"
echo "Or close this terminal - servers will keep running"
echo ""
echo "📊 Monitoring logs:"
echo "   Server: tail -f logs/server.log"
echo "   Ngrok: tail -f /tmp/ngrok.log"
