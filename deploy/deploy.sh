#!/bin/bash
# Деплой KotlinAgent на VPS (systemd-first)

set -euo pipefail

### === НАСТРОЙКИ ===

SERVER="root@95.217.187.167"
REMOTE_DIR="/home/agent/KotlinAgent"
LOCAL_DIR="/Users/anton/IdeaProjects/KotlinAgent"

APP_JAR="remoteAgentServer/build/libs/remoteAgentServer.jar"
COMPOSE_UI_DIR="compose-ui/build/dist/js/productionExecutable"
APP_PORT="8001"

# SSH ControlMaster для переиспользования соединения
SSH_CONTROL_PATH="/tmp/ssh-kotlinagent-deploy-%r@%h:%p"
SSH_OPTS="-o ControlMaster=auto -o ControlPath=$SSH_CONTROL_PATH -o ControlPersist=10m"

echo "=== Деплой KotlinAgent ==="

# Функция для очистки SSH соединения при выходе
cleanup_ssh() {
    ssh $SSH_OPTS -O exit "$SERVER" 2>/dev/null || true
}
trap cleanup_ssh EXIT

### === 1. СБОРКА ЛОКАЛЬНО ===

echo "Шаг 1: Сборка проекта..."
cd "$LOCAL_DIR"

# Очистка
./gradlew clean

# Сборка Compose UI
echo "Сборка Compose UI..."
./gradlew :compose-ui:jsBrowserDistribution

if [ ! -d "$COMPOSE_UI_DIR" ]; then
    echo "❌ Compose UI не собран: $COMPOSE_UI_DIR"
    exit 1
fi

echo "✅ Compose UI собран: $COMPOSE_UI_DIR"

# Сборка сервера
echo "Сборка сервера..."
./gradlew :remoteAgentServer:build -x test

if [ ! -f "$APP_JAR" ]; then
    echo "❌ JAR файл не найден: $APP_JAR"
    exit 1
fi

echo "✅ JAR собран: $APP_JAR"
echo ""

### === 2. ПОДГОТОВКА СЕРВЕРА ===

echo "Шаг 2: Подготовка сервера..."
ssh $SSH_OPTS "$SERVER" << ENDSSH
set -e
mkdir -p \
  $REMOTE_DIR/remoteAgentServer/build/libs \
  $REMOTE_DIR/ui \
  $REMOTE_DIR/deploy \
  $REMOTE_DIR/scripts
chown -R agent:agent $REMOTE_DIR
ENDSSH

echo "✅ Сервер готов"
echo ""

### === 3. КОПИРОВАНИЕ ФАЙЛОВ ===

echo "Шаг 3: Копирование файлов..."

# Копируем JAR файл
scp "$APP_JAR" "$SERVER:$REMOTE_DIR/remoteAgentServer/build/libs/"

# Копируем собранный Compose UI в ui/
echo "Копирование Compose UI..."
scp -r "$COMPOSE_UI_DIR"/* "$SERVER:$REMOTE_DIR/ui/"

# Копируем deploy и scripts
scp -r deploy/ "$SERVER:$REMOTE_DIR/"
if [ -d "scripts" ]; then
    scp -r scripts/ "$SERVER:$REMOTE_DIR/"
fi

# Копируем .env если существует
if [ -f ".env" ]; then
    scp .env "$SERVER:$REMOTE_DIR/"
fi

# Копируем RAG базу данных если существует
if [ -f "rag_index.db" ]; then
    echo "Копирование RAG базы данных..."
    scp rag_index.db "$SERVER:$REMOTE_DIR/"
    echo "✅ RAG база данных скопирована"
else
    echo "⚠️  rag_index.db не найден - RAG функционал будет недоступен"
fi

# Копируем RAG данные если существуют
if [ -d "rag/rag_data" ]; then
    echo "Копирование RAG данных..."
    ssh "$SERVER" "mkdir -p $REMOTE_DIR/rag"
    scp -r rag/rag_data "$SERVER:$REMOTE_DIR/rag/"
    echo "✅ RAG данные скопированы"
fi

# Делаем скрипты исполняемыми на сервере
ssh "$SERVER" "chmod +x $REMOTE_DIR/deploy/*.sh"
if ssh "$SERVER" "[ -d $REMOTE_DIR/scripts ]"; then
    ssh "$SERVER" "chmod +x $REMOTE_DIR/scripts/*.sh"
fi

echo "✅ Файлы скопированы"
echo ""

### === 4. ПРОВЕРКА .env ===

echo "Шаг 4: Проверка .env..."
ssh "$SERVER" << 'ENDSSH'
cd /home/agent/KotlinAgent
if [ ! -f ".env" ]; then
    echo "❌ .env не найден"
    exit 1
fi
echo "✅ .env найден"
ENDSSH

echo ""

### === 5. ПЕРЕЗАПУСК ===

echo "Шаг 5: Перезапуск приложения..."

ssh "$SERVER" << 'ENDSSH'
set -e

if systemctl list-unit-files | grep -q kotlinagent.service; then
    echo "Используем systemd"
    sudo systemctl restart kotlinagent
    sudo systemctl status kotlinagent --no-pager
else
    echo "systemd не найден — ручной режим"

    cd /home/agent/KotlinAgent
    chmod +x deploy/*.sh

    ./deploy/stop.sh || true
    nohup ./deploy/start.sh > remoteAgentServer.log 2>&1 &

    sleep 3
    echo "Java процессы:"
    ps aux | grep java | grep -v grep || true
fi
ENDSSH

echo ""

### === 6. HEALTH CHECK ===

echo "Шаг 6: Health check..."
sleep 2

# Проверяем HTTP (основной порт)
if curl -s "http://95.217.187.167:$APP_PORT/health" | grep -q "ok"; then
    echo "✅ HTTP Health check passed"
else
    echo "❌ HTTP Health check failed"
fi

# Проверяем HTTPS (порт 8443) с игнорированием самоподписанного сертификата
if curl -k -s "https://95.217.187.167:8443/health" | grep -q "ok"; then
    echo "✅ HTTPS Health check passed"
else
    echo "⚠️  HTTPS Health check failed (возможно, нужно настроить SSL)"
fi

echo ""

### === 7. ПРОВЕРКА OLLAMA ===

echo "Шаг 7: Проверка Ollama для RAG..."

# Проверяем Ollama на сервере
ssh "$SERVER" << 'ENDSSH'
if command -v ollama &> /dev/null; then
    echo "✅ Ollama установлена"

    # Проверяем, что Ollama запущена
    if curl -s http://localhost:11434/api/tags &> /dev/null; then
        echo "✅ Ollama сервис работает"

        # Проверяем наличие модели nomic-embed-text
        if ollama list | grep -q "nomic-embed-text"; then
            echo "✅ Модель nomic-embed-text установлена"
        else
            echo "⚠️  Модель nomic-embed-text не найдена"
            echo "   Установите модель: ollama pull nomic-embed-text"
        fi
    else
        echo "⚠️  Ollama не запущена"
        echo "   Запустите: sudo systemctl start ollama"
    fi
else
    echo "⚠️  Ollama не установлена - RAG функционал будет недоступен"
    echo "   Установите Ollama: curl -fsSL https://ollama.com/install.sh | sh"
    echo "   Затем установите модель: ollama pull nomic-embed-text"
fi
ENDSSH

echo ""
echo "=== ✅ Деплой завершён ==="
echo ""
echo "Доступные URL:"
echo "  HTTP:  http://95.217.187.167:$APP_PORT"
echo "  HTTPS: https://95.217.187.167:8443 (самоподписанный сертификат)"
echo ""
echo "Для настройки настоящего SSL сертификата:"
echo "  ./scripts/setup-duckdns-ssl.sh myapp TOKEN email@example.com"
echo "  или"
echo "  sudo ./scripts/setup-ssl-nginx.sh yourdomain.com email@example.com"
echo ""
echo "Для настройки RAG (если не установлено):"
echo "  ssh $SERVER"
echo "  curl -fsSL https://ollama.com/install.sh | sh"
echo "  ollama pull nomic-embed-text"