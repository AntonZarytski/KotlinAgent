#!/bin/bash
# Скрипт деплоя KotlinAgent на VPS

set -e

SERVER="root@95.217.187.167"
REMOTE_DIR="/home/agent/KotlinAgent"
LOCAL_DIR="/Users/anton/IdeaProjects/KotlinAgent"

echo "=== Деплой KotlinAgent на VPS ==="

# 1. Собираем проект локально
echo "Шаг 1: Сборка проекта..."
cd "$LOCAL_DIR"
./gradlew :app:build -x test

# Проверяем что JAR создан
if [ ! -f "app/build/libs/app.jar" ]; then
    echo "Ошибка: JAR файл не собрался!"
    exit 1
fi

echo "JAR создан: app/build/libs/app.jar"

# 2. Создаём директорию на сервере (если нужно)
echo "Шаг 2: Подготовка сервера..."
ssh $SERVER "mkdir -p $REMOTE_DIR/app/build/libs $REMOTE_DIR/ui $REMOTE_DIR/deploy"

# 3. Копируем файлы на сервер
echo "Шаг 3: Копирование файлов..."
scp app/build/libs/app.jar $SERVER:$REMOTE_DIR/app/build/libs/
scp -r ui/ $SERVER:$REMOTE_DIR/
scp -r deploy/ $SERVER:$REMOTE_DIR/
scp .env.example $SERVER:$REMOTE_DIR/

echo "Файлы скопированы"

# 4. Проверяем .env на сервере
echo "Шаг 4: Проверка .env..."
ssh $SERVER << 'ENDSSH'
cd /home/agent/KotlinAgent
if [ ! -f ".env" ]; then
    echo "⚠️  ВНИМАНИЕ: файл .env не найден на сервере!"
    echo "Создайте .env на основе .env.example и добавьте ANTHROPIC_API_KEY"
    exit 1
fi
echo "✓ .env найден"
ENDSSH

# 5. Перезапускаем приложение через systemd (если настроен) или вручную
echo "Шаг 5: Перезапуск приложения..."
ssh $SERVER << 'ENDSSH'
# Проверяем systemd service
if systemctl is-active --quiet kotlinagent; then
    echo "Перезапуск через systemd..."
    sudo systemctl restart kotlinagent
    sudo systemctl status kotlinagent --no-pager
else
    echo "Systemd service не найден, перезапуск вручную..."
    cd /home/agent/KotlinAgent
    
    # Останавливаем старый процесс
    ./deploy/stop.sh || true
    
    # Запускаем новый (в фоне)
    nohup ./deploy/start.sh > app.log 2>&1 &
    
    sleep 3
    echo "Процессы Java:"
    ps aux | grep java || true
fi
ENDSSH

# 6. Проверка работоспособности
echo "Шаг 6: Проверка работоспособности..."
sleep 2

echo "Health check:"
curl -s http://95.217.187.167:8000/health | jq '.' || echo "Health check failed"

echo ""
echo "=== Деплой завершён ==="
echo "URL: http://95.217.187.167:8000"
