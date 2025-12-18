#!/bin/bash

# Скрипт для настройки бесплатного домена DuckDNS + Let's Encrypt SSL
# Использование: ./setup-duckdns-ssl.sh subdomain token email

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Проверка аргументов
if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    echo -e "${RED}Ошибка: Недостаточно аргументов${NC}"
    echo ""
    echo "Использование: $0 subdomain token email"
    echo ""
    echo "Где:"
    echo "  subdomain - ваш поддомен на DuckDNS (например: myapp)"
    echo "  token     - токен от DuckDNS (получите на https://www.duckdns.org)"
    echo "  email     - ваш email для Let's Encrypt"
    echo ""
    echo "Пример:"
    echo "  $0 myapp a1b2c3d4-e5f6-7890-abcd-ef1234567890 your@email.com"
    echo ""
    echo "Результат: myapp.duckdns.org с бесплатным SSL сертификатом"
    exit 1
fi

SUBDOMAIN=$1
TOKEN=$2
EMAIL=$3
DOMAIN="$SUBDOMAIN.duckdns.org"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Настройка DuckDNS + Let's Encrypt SSL             ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Поддомен: $SUBDOMAIN"
echo "Полный домен: $DOMAIN"
echo "Email: $EMAIL"
echo "Проект: $PROJECT_DIR"
echo ""

# Шаг 1: Обновление IP в DuckDNS
echo -e "${YELLOW}[1/5] Обновление IP адреса в DuckDNS...${NC}"

RESPONSE=$(curl -s "https://www.duckdns.org/update?domains=$SUBDOMAIN&token=$TOKEN&ip=")

if [ "$RESPONSE" = "OK" ]; then
    echo -e "${GREEN}✅ IP адрес успешно обновлен в DuckDNS${NC}"
else
    echo -e "${RED}❌ Ошибка обновления IP в DuckDNS: $RESPONSE${NC}"
    echo "Проверьте поддомен и токен на https://www.duckdns.org"
    exit 1
fi

# Получение текущего IP
CURRENT_IP=$(curl -s ifconfig.me)
echo "Текущий IP: $CURRENT_IP"

# Шаг 2: Настройка автообновления IP
echo -e "${YELLOW}[2/5] Настройка автообновления IP...${NC}"

DUCKDNS_DIR="$HOME/duckdns"
mkdir -p "$DUCKDNS_DIR"

cat > "$DUCKDNS_DIR/duck.sh" << EOF
#!/bin/bash
echo url="https://www.duckdns.org/update?domains=$SUBDOMAIN&token=$TOKEN&ip=" | curl -k -o $DUCKDNS_DIR/duck.log -K -
EOF

chmod +x "$DUCKDNS_DIR/duck.sh"

# Добавление в crontab (обновление каждые 5 минут)
(crontab -l 2>/dev/null | grep -v "duckdns/duck.sh"; echo "*/5 * * * * $DUCKDNS_DIR/duck.sh >/dev/null 2>&1") | crontab -

echo -e "${GREEN}✅ Автообновление IP настроено (каждые 5 минут)${NC}"

# Шаг 3: Установка Certbot
echo -e "${YELLOW}[3/5] Проверка Certbot...${NC}"

if ! command -v certbot &> /dev/null; then
    echo "Certbot не установлен. Установка..."
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        brew install certbot
    elif [[ -f /etc/debian_version ]]; then
        # Debian/Ubuntu
        sudo apt update
        sudo apt install -y certbot
    elif [[ -f /etc/redhat-release ]]; then
        # CentOS/RHEL
        sudo yum install -y certbot
    else
        echo -e "${RED}Не удалось определить ОС. Установите certbot вручную.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ Certbot установлен${NC}"

# Шаг 4: Получение SSL сертификата
echo -e "${YELLOW}[4/5] Получение SSL сертификата от Let's Encrypt...${NC}"
echo ""
echo -e "${BLUE}Certbot запустит временный веб-сервер на порту 80.${NC}"
echo -e "${BLUE}Убедитесь, что порт 80 открыт и не занят.${NC}"
echo ""

# Проверка, запущен ли сервер на порту 80
if lsof -Pi :80 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Порт 80 занят. Остановите сервер перед продолжением.${NC}"
    read -p "Продолжить? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Получение сертификата
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS - без sudo
    certbot certonly --standalone \
        -d "$DOMAIN" \
        --non-interactive \
        --agree-tos \
        --email "$EMAIL"
else
    # Linux - с sudo
    sudo certbot certonly --standalone \
        -d "$DOMAIN" \
        --non-interactive \
        --agree-tos \
        --email "$EMAIL"
fi

echo -e "${GREEN}✅ SSL сертификат получен${NC}"

# Шаг 5: Конвертация сертификата в PKCS12
echo -e "${YELLOW}[5/5] Конвертация сертификата в формат PKCS12...${NC}"

CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
OUTPUT_FILE="$PROJECT_DIR/ktor.p12"

if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    openssl pkcs12 -export \
        -in "$CERT_DIR/fullchain.pem" \
        -inkey "$CERT_DIR/privkey.pem" \
        -out "$OUTPUT_FILE" \
        -name ktor \
        -passout pass:changeit
else
    # Linux
    sudo openssl pkcs12 -export \
        -in "$CERT_DIR/fullchain.pem" \
        -inkey "$CERT_DIR/privkey.pem" \
        -out "$OUTPUT_FILE" \
        -name ktor \
        -passout pass:changeit
    
    sudo chown $USER:$USER "$OUTPUT_FILE"
fi

chmod 600 "$OUTPUT_FILE"

echo -e "${GREEN}✅ Сертификат конвертирован${NC}"

# Итоговая информация
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              Настройка завершена!                      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}✅ Бесплатный домен и SSL сертификат настроены${NC}"
echo ""
echo -e "${YELLOW}Ваш сайт доступен по адресу:${NC}"
echo -e "  ${GREEN}https://$DOMAIN${NC}"
echo ""
echo -e "${YELLOW}Файлы:${NC}"
echo "  📄 $OUTPUT_FILE (PKCS12 для Ktor)"
echo "  📄 $CERT_DIR/fullchain.pem"
echo "  📄 $CERT_DIR/privkey.pem"
echo ""
echo -e "${YELLOW}Следующие шаги:${NC}"
echo "1. Запустите приложение: cd $PROJECT_DIR && ./gradlew :app:run"
echo "2. Откройте в браузере: https://$DOMAIN:8443"
echo ""
echo -e "${YELLOW}Автообновление:${NC}"
echo "  • IP адрес обновляется каждые 5 минут"
echo "  • Сертификат обновится автоматически за 30 дней до истечения"
echo ""
echo -e "${YELLOW}Полезные команды:${NC}"
echo "  $DUCKDNS_DIR/duck.sh          # Обновить IP вручную"
echo "  certbot certificates           # Список сертификатов"
echo "  certbot renew --dry-run        # Тест обновления сертификата"
echo ""

