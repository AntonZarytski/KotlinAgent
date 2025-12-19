@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Local Android Studio Agent Launcher
echo ========================================
echo.

REM Проверка ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    echo WARNING: ANDROID_HOME is not set!
    echo.
    echo Trying to find Android SDK in common locations...
    
    set "SDK_FOUND="
    
    REM Пробуем стандартные пути
    if exist "C:\Android\sdk" (
        set "ANDROID_HOME=C:\Android\sdk"
        set "SDK_FOUND=1"
        echo Found Android SDK at: !ANDROID_HOME!
    ) else if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
        set "SDK_FOUND=1"
        echo Found Android SDK at: !ANDROID_HOME!
    ) else if exist "%USERPROFILE%\Android\Sdk" (
        set "ANDROID_HOME=%USERPROFILE%\Android\Sdk"
        set "SDK_FOUND=1"
        echo Found Android SDK at: !ANDROID_HOME!
    )
    
    if "!SDK_FOUND!"=="" (
        echo.
        echo ERROR: Could not find Android SDK!
        echo.
        echo Please install Android SDK and set ANDROID_HOME:
        echo   set ANDROID_HOME=C:\path\to\android\sdk
        echo.
        echo Or see ANDROID_SDK_SETUP.md for detailed instructions.
        pause
        exit /b 1
    )
) else (
    echo ANDROID_HOME is set: %ANDROID_HOME%
)

echo.
echo Checking Android SDK tools...

REM Проверка ADB
set "ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe"
if exist "%ADB_PATH%" (
    echo   [OK] ADB found: %ADB_PATH%
) else (
    echo   [X] ADB not found at: %ADB_PATH%
    echo       Install via: sdkmanager "platform-tools"
)

REM Проверка Emulator
set "EMULATOR_PATH=%ANDROID_HOME%\emulator\emulator.exe"
if exist "%EMULATOR_PATH%" (
    echo   [OK] Emulator found: %EMULATOR_PATH%
) else (
    echo   [X] Emulator not found at: %EMULATOR_PATH%
    echo       Install via: sdkmanager "emulator"
)

REM Проверка AVD Manager
set "AVD_MANAGER_PATH=%ANDROID_HOME%\cmdline-tools\latest\bin\avdmanager.bat"
if exist "%AVD_MANAGER_PATH%" (
    echo   [OK] AVD Manager found: %AVD_MANAGER_PATH%
) else (
    echo   [!] AVD Manager not found at: %AVD_MANAGER_PATH%
    echo       Install via: sdkmanager "cmdline-tools;latest"
)

echo.
echo Checking available AVDs...
if exist "%AVD_MANAGER_PATH%" (
    "%AVD_MANAGER_PATH%" list avd 2>nul | find "Name:" >nul
    if !errorlevel! equ 0 (
        echo   [OK] AVDs found:
        "%AVD_MANAGER_PATH%" list avd | find "Name:"
    ) else (
        echo   [!] No AVDs found. Create one with:
        echo       avdmanager create avd -n Pixel_5_API_33 -k "system-images;android-33;google_apis;x86_64"
    )
)

echo.
echo ========================================
echo.

REM Запрос URL сервера
set /p VPS_URL="Enter VPS URL (default: ws://127.0.0.1:8443): "
if "%VPS_URL%"=="" set "VPS_URL=ws://127.0.0.1:8443"

echo.
echo Starting Local Agent...
echo VPS URL: %VPS_URL%
echo.

REM Переход в корень проекта
cd /d "%~dp0\.."

REM Запуск через Gradle
call gradlew.bat :localAgentClient:run --args="%VPS_URL%"

pause

