#!/bin/bash

# Скрипт для запуска Local Android Studio Agent с проверкой Android SDK

echo "🚀 Local Android Studio Agent Launcher"
echo "========================================"
echo ""

# Проверка ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  WARNING: ANDROID_HOME is not set!"
    echo ""
    echo "Trying to find Android SDK in common locations..."
    
    # Пробуем найти в стандартных местах
    POSSIBLE_LOCATIONS=(
        "$HOME/Android/Sdk"
        "$HOME/Library/Android/sdk"
        "/usr/local/android-sdk"
    )
    
    for location in "${POSSIBLE_LOCATIONS[@]}"; do
        if [ -d "$location" ]; then
            echo "✅ Found Android SDK at: $location"
            export ANDROID_HOME="$location"
            break
        fi
    done
    
    if [ -z "$ANDROID_HOME" ]; then
        echo ""
        echo "❌ ERROR: Could not find Android SDK!"
        echo ""
        echo "Please install Android SDK and set ANDROID_HOME:"
        echo "  export ANDROID_HOME=/path/to/android/sdk"
        echo ""
        echo "Or see ANDROID_SDK_SETUP.md for detailed instructions."
        exit 1
    fi
else
    echo "✅ ANDROID_HOME is set: $ANDROID_HOME"
fi

# Проверка наличия необходимых инструментов
echo ""
echo "Checking Android SDK tools..."

# Проверка ADB
ADB_PATH="$ANDROID_HOME/platform-tools/adb"
if [ -f "$ADB_PATH" ]; then
    echo "  ✅ ADB found: $ADB_PATH"
else
    echo "  ❌ ADB not found at: $ADB_PATH"
    echo "     Install via: sdkmanager 'platform-tools'"
fi

# Проверка Emulator
EMULATOR_PATH="$ANDROID_HOME/emulator/emulator"
if [ -f "$EMULATOR_PATH" ]; then
    echo "  ✅ Emulator found: $EMULATOR_PATH"
else
    echo "  ❌ Emulator not found at: $EMULATOR_PATH"
    echo "     Install via: sdkmanager 'emulator'"
fi

# Проверка AVD Manager
AVD_MANAGER_PATH="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
if [ -f "$AVD_MANAGER_PATH" ]; then
    echo "  ✅ AVD Manager found: $AVD_MANAGER_PATH"
else
    echo "  ⚠️  AVD Manager not found at: $AVD_MANAGER_PATH"
    echo "     Install via: sdkmanager 'cmdline-tools;latest'"
fi

# Проверка наличия AVD
echo ""
echo "Checking available AVDs..."
if [ -f "$AVD_MANAGER_PATH" ]; then
    AVDS=$("$AVD_MANAGER_PATH" list avd 2>/dev/null | grep "Name:" | wc -l)
    if [ "$AVDS" -gt 0 ]; then
        echo "  ✅ Found $AVDS AVD(s)"
        "$AVD_MANAGER_PATH" list avd | grep "Name:" | sed 's/^/     /'
    else
        echo "  ⚠️  No AVDs found. Create one with:"
        echo "     avdmanager create avd -n Pixel_5_API_33 -k 'system-images;android-33;google_apis;x86_64'"
    fi
fi

echo ""
echo "========================================"
echo ""

# Запрос URL сервера
read -p "Enter VPS URL (default: ws://127.0.0.1:8443): " VPS_URL
VPS_URL=${VPS_URL:-ws://127.0.0.1:8443}

echo ""
echo "Starting Local Agent..."
echo "VPS URL: $VPS_URL"
echo ""

# Переход в корень проекта
cd "$(dirname "$0")/.."

# Запуск через Gradle
./gradlew :localAgentClient:run --args="$VPS_URL"

