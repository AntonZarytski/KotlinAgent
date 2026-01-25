#!/bin/bash

# Скрипт для полной очистки кешей и пересборки Compose UI
# Использовать когда нужна чистая сборка с нуля

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         Полная очистка и пересборка Compose UI                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}✅${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC}  $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC}  $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

# Шаг 1: Останавливаем сервер
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Шаг 1: Остановка сервера"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if lsof -Pi :8001 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    print_info "Останавливаем сервер на порту 8001..."
    lsof -ti :8001 | xargs kill -9 2>/dev/null || true
    sleep 2
    print_status "Сервер остановлен"
else
    print_info "Сервер не запущен"
fi

# Шаг 2: Очистка всех кешей
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Шаг 2: Очистка всех кешей"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

print_info "Очистка Gradle кешей..."
./gradlew clean --no-configuration-cache 2>&1 | tail -5
print_status "Gradle кеши очищены"

print_info "Удаление build директорий..."
rm -rf compose-ui/build
rm -rf remoteAgentServer/build
rm -rf common/build
rm -rf .gradle/configuration-cache
print_status "Build директории удалены"

print_info "Удаление node_modules и yarn кешей..."
rm -rf build/js
rm -rf kotlin-js-store
print_status "JS кеши удалены"

print_info "Очистка логов..."
> app.log
print_status "Логи очищены"

# Шаг 3: Сборка Compose UI
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Шаг 3: Сборка Compose UI (production)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

print_info "Запуск сборки..."
./gradlew :compose-ui:jsBrowserProductionWebpack --no-configuration-cache 2>&1 | tee -a app.log

if [ ${PIPESTATUS[0]} -eq 0 ]; then
    print_status "Compose UI успешно собран"
else
    print_error "Ошибка сборки Compose UI"
    exit 1
fi

# Шаг 4: Проверка результата
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Шаг 4: Проверка результата"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

WEBPACK_DIR="compose-ui/build/kotlin-webpack/js/productionExecutable"
RESOURCES_DIR="compose-ui/build/processedResources/js/main"

if [ -f "$WEBPACK_DIR/compose-web.js" ]; then
    print_status "compose-web.js найден в $WEBPACK_DIR"
    ls -lh "$WEBPACK_DIR/compose-web.js"
else
    print_warning "compose-web.js не найден в $WEBPACK_DIR"
fi

if [ -f "$RESOURCES_DIR/index.html" ]; then
    print_status "index.html найден в $RESOURCES_DIR"
else
    print_warning "index.html не найден в $RESOURCES_DIR"
fi

echo ""
print_status "Готово! Теперь можно запустить сервер:"
echo "   ./gradlew :remoteAgentServer:run"
echo ""

