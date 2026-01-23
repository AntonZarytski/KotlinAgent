#!/bin/bash
# Деплой KotlinAgent на VPS (systemd-first)

set -euo pipefail

### === НАСТРОЙКИ ===

SERVER="root@95.217.187.167"
REMOTE_DIR="/home/agent/KotlinAgent"
LOCAL_DIR="/Users/anton/StudioProjects/KotlinAgent"

APP_JAR="remoteAgentServer/build/libs/remoteAgentServer.jar"
COMPOSE_UI_DIR="compose-ui/build/processedResources/js/main"
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

### === 7. ПРОВЕРКА И НАСТРОЙКА OLLAMA ===

echo "Шаг 7: Проверка и настройка Ollama..."

# Проверяем и настраиваем Ollama на сервере
ssh "$SERVER" << 'ENDSSH'
set -e

echo "=== Проверка Ollama ==="

# Проверка установки Ollama
if ! command -v ollama &> /dev/null; then
    echo "⚠️  Ollama не установлена"
    echo "📥 Установка Ollama..."
    curl -fsSL https://ollama.com/install.sh | sh

    if ! command -v ollama &> /dev/null; then
        echo "❌ Ошибка установки Ollama"
        exit 1
    fi
    echo "✅ Ollama установлена"
else
    echo "✅ Ollama уже установлена"
    ollama --version
fi

echo ""
echo "=== Проверка сервиса Ollama ==="

# Проверка и запуск сервиса
if ! curl -s http://localhost:11434/api/tags &> /dev/null; then
    echo "⚠️  Ollama не запущена, пытаемся запустить..."

    # Пробуем через systemd
    if systemctl list-unit-files | grep -q ollama.service; then
        sudo systemctl enable ollama 2>/dev/null || true
        sudo systemctl start ollama
        sleep 3
    else
        # Запускаем вручную в фоне
        echo "Запуск Ollama в фоновом режиме..."
        nohup ollama serve > /tmp/ollama.log 2>&1 &
        sleep 5
    fi

    # Проверяем снова
    if curl -s http://localhost:11434/api/tags &> /dev/null; then
        echo "✅ Ollama сервис запущен"
    else
        echo "❌ Не удалось запустить Ollama"
        echo "Логи: /tmp/ollama.log"
        exit 1
    fi
else
    echo "✅ Ollama сервис работает"
fi

echo ""
echo "=== Проверка системных ресурсов ==="

# Проверяем доступную память
TOTAL_RAM=$(free -m | awk '/^Mem:/{print $2}')
AVAILABLE_RAM=$(free -m | awk '/^Mem:/{print $7}')

echo "💾 Общая RAM: ${TOTAL_RAM} MB"
echo "💾 Доступная RAM: ${AVAILABLE_RAM} MB"

if [ "$TOTAL_RAM" -lt 2048 ]; then
    echo "⚠️  Внимание: Мало RAM (< 2 GB). Используем легкую модель qwen2:0.5b"
    LLM_MODEL="qwen2:0.5b"
elif [ "$TOTAL_RAM" -lt 4096 ]; then
    echo "⚠️  RAM ограничена (< 4 GB). Рекомендуется qwen2:0.5b или phi:2.7b"
    LLM_MODEL="qwen2:0.5b"
else
    echo "✅ Достаточно RAM для больших моделей"
    LLM_MODEL="llama3.2"
fi

echo "🤖 Выбранная LLM модель: $LLM_MODEL"

echo ""
echo "=== Установка моделей ==="

# Функция для проверки и установки модели
check_and_pull_model() {
    local model_name=$1
    local model_purpose=$2

    echo "Проверка модели $model_name ($model_purpose)..."

    if ollama list | grep -q "$model_name"; then
        echo "✅ Модель $model_name уже установлена"
        ollama list | grep "$model_name"
    else
        echo "📥 Загрузка модели $model_name..."
        echo "   Это может занять несколько минут..."

        if ollama pull "$model_name"; then
            echo "✅ Модель $model_name установлена успешно"
            ollama list | grep "$model_name"
        else
            echo "❌ Ошибка установки модели $model_name"
            return 1
        fi
    fi
}

# Устанавливаем embedding модель для RAG
check_and_pull_model "nomic-embed-text" "RAG embeddings"

echo ""

# Устанавливаем LLM модель
check_and_pull_model "$LLM_MODEL" "Chat LLM"

echo ""
echo "=== Тест моделей ==="

# Тестируем embedding модель
echo "Тест nomic-embed-text..."
if curl -s http://localhost:11434/api/embeddings -d '{
  "model": "nomic-embed-text",
  "prompt": "test"
}' | grep -q "embedding"; then
    echo "✅ nomic-embed-text работает"
else
    echo "⚠️  Проблема с nomic-embed-text"
fi

echo ""

# Тестируем LLM модель
echo "Тест $LLM_MODEL..."
TEST_RESPONSE=$(curl -s http://localhost:11434/api/generate -d "{
  \"model\": \"$LLM_MODEL\",
  \"prompt\": \"Say 'OK' if you work\",
  \"stream\": false
}" 2>&1)

if echo "$TEST_RESPONSE" | grep -q "response"; then
    echo "✅ $LLM_MODEL работает"

    # Показываем использование памяти
    echo ""
    echo "📊 Использование памяти после загрузки модели:"
    free -m | awk '/^Mem:/{printf "   Использовано: %d MB / %d MB (%.1f%%)\n", $3, $2, ($3/$2)*100}'
else
    echo "⚠️  Проблема с $LLM_MODEL"
    echo "Ответ: $TEST_RESPONSE"
fi

# Сохраняем выбранную модель в переменную окружения
echo ""
echo "=== Обновление .env ==="
cd /home/agent/KotlinAgent

if [ -f ".env" ]; then
    # Обновляем OLLAMA_MODEL в .env
    if grep -q "^OLLAMA_MODEL=" .env; then
        sed -i "s|^OLLAMA_MODEL=.*|OLLAMA_MODEL=$LLM_MODEL|" .env
        echo "✅ OLLAMA_MODEL обновлена в .env: $LLM_MODEL"
    else
        echo "OLLAMA_MODEL=$LLM_MODEL" >> .env
        echo "✅ OLLAMA_MODEL добавлена в .env: $LLM_MODEL"
    fi

    # Показываем текущую конфигурацию
    echo ""
    echo "Текущая конфигурация Ollama в .env:"
    grep "^OLLAMA" .env || echo "  (настройки Ollama не найдены)"
else
    echo "⚠️  .env не найден"
fi

ENDSSH

echo ""
echo "=== ✅ Деплой завершён ==="
echo ""
echo "Доступные URL:"
echo "  HTTP:  http://95.217.187.167:$APP_PORT"
echo "  HTTPS: https://95.217.187.167:8443 (самоподписанный сертификат)"
echo ""
echo "Ollama конфигурация:"
echo "  Статус: Проверен и настроен"
echo "  LLM модель: Автоматически выбрана на основе доступной RAM"
echo "  RAG модель: nomic-embed-text"
echo ""
echo "Для настройки настоящего SSL сертификата:"
echo "  ./scripts/setup-duckdns-ssl.sh myapp TOKEN email@example.com"
echo "  или"
echo "  sudo ./scripts/setup-ssl-nginx.sh yourdomain.com email@example.com"
echo ""
echo "Для проверки логов Ollama:"
echo "  ssh $SERVER"
echo "  journalctl -u ollama -f  # если systemd"
echo "  tail -f /tmp/ollama.log   # если background process"