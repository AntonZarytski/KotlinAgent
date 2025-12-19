# Настройка Android SDK для Local Agent

Для работы Local Android Studio Agent необходимо установить Android SDK и настроить переменные окружения.

## Автоматическое определение Android SDK

Agent автоматически ищет Android SDK в следующих местах:

### Переменные окружения (в порядке приоритета):
1. `ANDROID_HOME`
2. `ANDROID_SDK_ROOT`
3. `ANDROID_SDK`

### Стандартные пути:
- **Linux/macOS**: `~/Android/Sdk` или `~/Library/Android/sdk`
- **Windows**: `C:\Android\sdk` или `C:\Users\<username>\AppData\Local\Android\Sdk`

## Установка Android SDK

### Вариант 1: Через Android Studio (рекомендуется)

1. Скачайте и установите [Android Studio](https://developer.android.com/studio)
2. Запустите Android Studio
3. Откройте **Settings/Preferences → Appearance & Behavior → System Settings → Android SDK**
4. Установите необходимые компоненты:
   - ✅ Android SDK Platform-Tools (содержит `adb`)
   - ✅ Android SDK Command-line Tools (содержит `avdmanager`)
   - ✅ Android Emulator
5. Запомните путь к SDK (обычно показан вверху окна)

### Вариант 2: Command Line Tools (без Android Studio)

1. Скачайте [Android Command Line Tools](https://developer.android.com/studio#command-tools)
2. Распакуйте в удобное место (например, `~/Android/Sdk` или `C:\Android\sdk`)
3. Установите необходимые компоненты:

```bash
# Linux/macOS
cd ~/Android/Sdk/cmdline-tools/bin
./sdkmanager "platform-tools" "emulator" "cmdline-tools;latest"

# Windows
cd C:\Android\sdk\cmdline-tools\bin
sdkmanager.bat "platform-tools" "emulator" "cmdline-tools;latest"
```

## Настройка переменных окружения

### Linux/macOS

Добавьте в `~/.bashrc`, `~/.zshrc` или `~/.profile`:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

Примените изменения:
```bash
source ~/.bashrc  # или ~/.zshrc
```

### Windows

1. Откройте **Панель управления → Система → Дополнительные параметры системы**
2. Нажмите **Переменные среды**
3. Добавьте новую системную переменную:
   - Имя: `ANDROID_HOME`
   - Значение: `C:\Android\sdk` (или ваш путь)
4. Добавьте в переменную `Path`:
   - `%ANDROID_HOME%\platform-tools`
   - `%ANDROID_HOME%\emulator`
   - `%ANDROID_HOME%\cmdline-tools\latest\bin`

## Проверка установки

После настройки проверьте, что команды доступны:

```bash
# Проверка ADB
adb version

# Проверка AVD Manager
avdmanager list avd

# Проверка Emulator
emulator -list-avds
```

## Создание виртуального устройства (AVD)

Если у вас еще нет эмулятора:

```bash
# Установите образ системы (например, Android 13)
sdkmanager "system-images;android-33;google_apis;x86_64"

# Создайте AVD
avdmanager create avd -n Pixel_5_API_33 -k "system-images;android-33;google_apis;x86_64" -d pixel_5
```

## Устранение проблем

### Ошибка: "Cannot run program 'adb'"

**Причина**: ADB не найден в PATH или ANDROID_HOME не установлен

**Решение**:
1. Проверьте, что `ANDROID_HOME` установлен: `echo $ANDROID_HOME` (Linux/macOS) или `echo %ANDROID_HOME%` (Windows)
2. Проверьте, что файл существует: `ls $ANDROID_HOME/platform-tools/adb` (Linux/macOS)
3. Перезапустите терминал после изменения переменных окружения

### Ошибка: "Cannot run program 'avdmanager'"

**Причина**: Command-line tools не установлены

**Решение**:
1. Установите через Android Studio SDK Manager: **SDK Tools → Android SDK Command-line Tools**
2. Или через sdkmanager: `sdkmanager "cmdline-tools;latest"`

### Ошибка: "No AVDs available"

**Причина**: Не созданы виртуальные устройства

**Решение**:
1. Создайте AVD через Android Studio: **Tools → Device Manager → Create Device**
2. Или через командную строку (см. раздел "Создание виртуального устройства")

## Запуск Local Agent

После настройки SDK запустите агент:

```bash
# Из корня проекта
./gradlew :localAgentClient:run

# Или соберите JAR и запустите
./gradlew :localAgentClient:shadowJar
java -jar localAgentClient/build/libs/localAgentClient-all.jar
```

При запуске агент покажет найденные пути:

```
🚀 Starting Local Android Studio Agent...
✅ ANDROID_HOME found: /Users/username/Library/Android/sdk
   ADB: /Users/username/Library/Android/sdk/platform-tools/adb
   Emulator: /Users/username/Library/Android/sdk/emulator/emulator
   AVD Manager: /Users/username/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager
```

Если пути не найдены, вернитесь к разделу "Настройка переменных окружения".

