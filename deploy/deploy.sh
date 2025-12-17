#!/bin/bash
# Деплой KotlinAgent на VPS (systemd-first)

set -euo pipefail

### === НАСТРОЙКИ ===

SERVER="root@95.217.187.167"
REMOTE_DIR="/home/agent/KotlinAgent"
LOCAL_DIR="/Users/anton/IdeaProjects/KotlinAgent"

APP_JAR="app/build/libs/app.jar"
APP_PORT="8001"

echo "=== Деплой KotlinAgent ==="

### === 1. СБОРКА ЛОКАЛЬНО ===

echo "Шаг 1: Сборка проекта..."
cd "$LOCAL_DIR"

./gradlew clean
./gradlew :app:build -x test

if [ ! -f "$APP_JAR" ]; then
    echo "❌ JAR файл не найден: $APP_JAR"
    exit 1
fi

echo "✅ JAR собран: $APP_JAR"
echo ""

### === 2. ПОДГОТОВКА СЕРВЕРА ===

echo "Шаг 2: Подготовка сервера..."
ssh "$SERVER" << ENDSSH
set -e
mkdir -p \
  $REMOTE_DIR/app/build/libs \
  $REMOTE_DIR/ui \
  $REMOTE_DIR/deploy \
  $REMOTE_DIR/scripts
chown -R agent:agent $REMOTE_DIR
ENDSSH

echo "✅ Сервер готов"
echo ""

### === 3. КОПИРОВАНИЕ ФАЙЛОВ ===

echo "Шаг 3: Копирование файлов..."
scp "$APP_JAR" "$SERVER:$REMOTE_DIR/app/build/libs/"
scp -r ui/ "$SERVER:$REMOTE_DIR/"
scp -r deploy/ "$SERVER:$REMOTE_DIR/"
scp -r scripts/ "$SERVER:$REMOTE_DIR/"
scp .env "$SERVER:$REMOTE_DIR/"

# Делаем скрипты исполняемыми на сервере
ssh "$SERVER" "chmod +x $REMOTE_DIR/scripts/*.sh $REMOTE_DIR/deploy/*.sh"

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
    nohup ./deploy/start.sh > app.log 2>&1 &

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