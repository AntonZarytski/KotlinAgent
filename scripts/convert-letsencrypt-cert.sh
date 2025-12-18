#!/bin/bash

# Скрипт для конвертации Let's Encrypt сертификата в формат PKCS12 для Ktor
# Использование: ./convert-letsencrypt-cert.sh yourdomain.com

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Проверка аргументов
if [ -z "$1" ]; then
    echo -e "${RED}Ошибка: Укажите доменное имя${NC}"
    echo "Использование: $0 yourdomain.com [password]"
    exit 1
fi

DOMAIN=$1
PASSWORD=${2:-changeit}
LETSENCRYPT_DIR="/etc/letsencrypt/live/$DOMAIN"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="$PROJECT_DIR/ktor.p12"

echo -e "${YELLOW}=== Конвертация Let's Encrypt сертификата ===${NC}"
echo "Домен: $DOMAIN"
echo "Проект: $PROJECT_DIR"
echo "Выходной файл: $OUTPUT_FILE"
echo ""

# Проверка наличия сертификатов Let's Encrypt
if [ ! -d "$LETSENCRYPT_DIR" ]; then
    echo -e "${RED}Ошибка: Сертификаты Let's Encrypt не найдены в $LETSENCRYPT_DIR${NC}"
    echo ""
    echo "Сначала получите сертификат:"
    echo "  sudo certbot certonly --standalone -d $DOMAIN"
    exit 1
fi

# Проверка наличия необходимых файлов
if [ ! -f "$LETSENCRYPT_DIR/fullchain.pem" ] || [ ! -f "$LETSENCRYPT_DIR/privkey.pem" ]; then
    echo -e "${RED}Ошибка: Не найдены файлы сертификата${NC}"
    echo "Ожидаемые файлы:"
    echo "  $LETSENCRYPT_DIR/fullchain.pem"
    echo "  $LETSENCRYPT_DIR/privkey.pem"
    exit 1
fi

# Конвертация сертификата
echo -e "${YELLOW}Конвертация сертификата...${NC}"
sudo openssl pkcs12 -export \
    -in "$LETSENCRYPT_DIR/fullchain.pem" \
    -inkey "$LETSENCRYPT_DIR/privkey.pem" \
    -out "$OUTPUT_FILE" \
    -name ktor \
    -passout pass:$PASSWORD

# Установка прав доступа
echo -e "${YELLOW}Установка прав доступа...${NC}"
sudo chown $USER:$USER "$OUTPUT_FILE"
chmod 600 "$OUTPUT_FILE"

echo ""
echo -e "${GREEN}✅ Сертификат успешно конвертирован!${NC}"
echo ""
echo "Файл: $OUTPUT_FILE"
echo "Пароль: $PASSWORD"
echo "Алиас: ktor"
echo ""
echo -e "${YELLOW}Следующие шаги:${NC}"
echo "1. Убедитесь, что в Application.kt используется файл ktor.p12"
echo "2. Перезапустите приложение: ./gradlew :app:run"
echo "3. Проверьте работу: https://$DOMAIN:8443"
echo ""
echo -e "${YELLOW}Для автоматического обновления:${NC}"
echo "Создайте файл /etc/letsencrypt/renewal-hooks/deploy/convert-to-p12.sh"
echo "со следующим содержимым:"
echo ""
echo "#!/bin/bash"
echo "$PROJECT_DIR/scripts/convert-letsencrypt-cert.sh $DOMAIN $PASSWORD"
echo "systemctl restart kotlinagent  # если используете systemd"

