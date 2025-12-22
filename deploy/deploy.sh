#!/bin/bash
# Деплой KotlinAgent на VPS (systemd-first)

set -euo pipefail

### === НАСТРОЙКИ ===

SERVER="root@95.217.187.167"
REMOTE_DIR="/home/agent/KotlinAgent"
LOCAL_DIR="/Users/anton/IdeaProjects/KotlinAgent"

APP_JAR="remoteAgentServer/build/libs/remoteAgentServer.jar"
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

./gradlew clean
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
scp $SSH_OPTS "$APP_JAR" "$SERVER:$REMOTE_DIR/remoteAgentServer/build/libs/"
scp $SSH_OPTS -r ui/ "$SERVER:$REMOTE_DIR/"
scp $SSH_OPTS -r deploy/ "$SERVER:$REMOTE_DIR/"
scp $SSH_OPTS -r scripts/ "$SERVER:$REMOTE_DIR/"
scp $SSH_OPTS .env "$SERVER:$REMOTE_DIR/"

# Делаем скрипты исполняемыми на сервере
ssh $SSH_OPTS "$SERVER" "chmod +x $REMOTE_DIR/scripts/*.sh $REMOTE_DIR/deploy/*.sh"

echo "✅ Файлы скопированы"
echo ""

### === 4. ПРОВЕРКА .env ===

echo "Шаг 4: Проверка .env..."
ssh $SSH_OPTS "$SERVER" << 'ENDSSH'
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

ssh $SSH_OPTS "$SERVER" << 'ENDSSH'
set -e

if systemctl list-unit-files | grep -q kotlinagent.service; then
    echo "Используем systemd"
    # Используем sudo без пароля (требуется настройка sudoers)
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