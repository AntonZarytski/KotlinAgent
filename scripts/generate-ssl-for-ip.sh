#!/bin/bash

# Скрипт для генерации самоподписанного SSL сертификата для IP адреса
# Использование: ./generate-ssl-for-ip.sh [IP_ADDRESS]

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="$PROJECT_DIR/ktor.p12"
PASSWORD="changeit"

echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Генерация SSL сертификата для IP адреса             ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""

# Определение IP адреса
if [ -z "$1" ]; then
    echo -e "${YELLOW}IP адрес не указан. Попытка автоопределения...${NC}"
    
    # Попытка определить публичный IP
    PUBLIC_IP=$(curl -s ifconfig.me || curl -s icanhazip.com || echo "")
    
    if [ -z "$PUBLIC_IP" ]; then
        # Попытка определить локальный IP
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            LOCAL_IP=$(ipconfig getifaddr en0 || ipconfig getifaddr en1 || echo "127.0.0.1")
        else
            # Linux
            LOCAL_IP=$(hostname -I | awk '{print $1}' || echo "127.0.0.1")
        fi
        IP_ADDRESS=$LOCAL_IP
    else
        IP_ADDRESS=$PUBLIC_IP
    fi
    
    echo -e "${GREEN}Определен IP адрес: $IP_ADDRESS${NC}"
    echo -e "${YELLOW}Если это неправильный IP, запустите: $0 YOUR_IP${NC}"
    echo ""
    read -p "Продолжить с IP $IP_ADDRESS? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}Отменено${NC}"
        exit 1
    fi
else
    IP_ADDRESS=$1
fi

echo ""
echo "IP адрес: $IP_ADDRESS"
echo "Проект: $PROJECT_DIR"
echo "Выходной файл: $OUTPUT_FILE"
echo ""

# Создание временной директории
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

echo -e "${YELLOW}[1/4] Создание конфигурации OpenSSL...${NC}"

# Создание конфигурационного файла с SAN (Subject Alternative Name)
cat > openssl-san.cnf << EOF
[req]
default_bits = 4096
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req
x509_extensions = v3_ca

[dn]
C=RU
ST=Moscow
L=Moscow
O=KotlinAgent
OU=Development
CN=$IP_ADDRESS

[v3_req]
subjectAltName = @alt_names
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

[v3_ca]
subjectAltName = @alt_names
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

[alt_names]
IP.1 = $IP_ADDRESS
IP.2 = 127.0.0.1
DNS.1 = localhost
EOF

echo -e "${GREEN}✅ Конфигурация создана${NC}"

echo -e "${YELLOW}[2/4] Генерация приватного ключа и сертификата...${NC}"

# Генерация приватного ключа и самоподписанного сертификата
openssl req -new -x509 -nodes -days 365 \
    -keyout server.key \
    -out server.crt \
    -config openssl-san.cnf \
    -extensions v3_ca

echo -e "${GREEN}✅ Сертификат создан (действителен 365 дней)${NC}"

echo -e "${YELLOW}[3/4] Конвертация в формат PKCS12...${NC}"

# Конвертация в PKCS12 для Ktor
openssl pkcs12 -export \
    -in server.crt \
    -inkey server.key \
    -out ktor.p12 \
    -name ktor \
    -passout pass:$PASSWORD

echo -e "${GREEN}✅ Конвертация завершена${NC}"

echo -e "${YELLOW}[4/4] Копирование файлов...${NC}"

# Копирование в проект
cp ktor.p12 "$OUTPUT_FILE"
cp server.crt "$PROJECT_DIR/server.crt"
cp server.key "$PROJECT_DIR/server.key"

# Установка прав доступа
chmod 600 "$OUTPUT_FILE"
chmod 600 "$PROJECT_DIR/server.key"

# Очистка
cd "$PROJECT_DIR"
rm -rf "$TEMP_DIR"

echo -e "${GREEN}✅ Файлы скопированы${NC}"

# Итоговая информация
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              SSL сертификат создан!                    ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Созданные файлы:${NC}"
echo "  📄 $OUTPUT_FILE (PKCS12 для Ktor)"
echo "  📄 $PROJECT_DIR/server.crt (публичный сертификат)"
echo "  📄 $PROJECT_DIR/server.key (приватный ключ)"
echo ""
echo -e "${YELLOW}Параметры:${NC}"
echo "  IP адрес: $IP_ADDRESS"
echo "  Пароль: $PASSWORD"
echo "  Алиас: ktor"
echo "  Срок действия: 365 дней"
echo ""
echo -e "${YELLOW}Ваш сервер будет доступен по адресу:${NC}"
echo -e "  ${GREEN}https://$IP_ADDRESS:8443${NC}"
echo ""
echo -e "${YELLOW}⚠️  Браузер покажет предупреждение о безопасности${NC}"
echo ""
echo -e "${BLUE}Как добавить сертификат в доверенные:${NC}"
echo ""
echo -e "${YELLOW}macOS:${NC}"
echo "  sudo security add-trusted-cert -d -r trustRoot \\"
echo "    -k /Library/Keychains/System.keychain $PROJECT_DIR/server.crt"
echo ""
echo -e "${YELLOW}Linux:${NC}"
echo "  sudo cp $PROJECT_DIR/server.crt /usr/local/share/ca-certificates/kotlinagent.crt"
echo "  sudo update-ca-certificates"
echo ""
echo -e "${YELLOW}Windows:${NC}"
echo "  certutil -addstore \"Root\" $PROJECT_DIR/server.crt"
echo ""
echo -e "${BLUE}Следующие шаги:${NC}"
echo "1. Запустите приложение: ./gradlew :app:run"
echo "2. Откройте в браузере: https://$IP_ADDRESS:8443"
echo "3. Добавьте сертификат в доверенные (команды выше)"
echo ""

