@echo off
REM Android Agent Startup Script for Windows
REM This script helps you start the Local Android Studio Agent with proper configuration

echo.
echo Android Agent Startup Script
echo ================================
echo.

REM Check if ANDROID_HOME is set
if "%ANDROID_HOME%"=="" (
    echo WARNING: ANDROID_HOME is not set!
    echo    Please set it to your Android SDK location:
    echo    set ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk
    echo.
    
    REM Try to find Android SDK in common location
    set "COMMON_LOCATION=%LOCALAPPDATA%\Android\Sdk"
    if exist "%COMMON_LOCATION%" (
        echo    Found Android SDK at: %COMMON_LOCATION%
        echo    You can set it with: set ANDROID_HOME=%COMMON_LOCATION%
    )
    echo.
)

REM Parse arguments
set "VPS_URL=%1"
if "%VPS_URL%"=="" set "VPS_URL=ws://127.0.0.1:8443"

set "ANDROID_PROJECT_PATH=%2"

echo Configuration:
echo   VPS URL: %VPS_URL%

if not "%ANDROID_PROJECT_PATH%"=="" (
    echo   Android Project: %ANDROID_PROJECT_PATH%
    
    REM Validate project path
    if not exist "%ANDROID_PROJECT_PATH%" (
        echo.
        echo ERROR: Android project directory not found!
        echo    Path: %ANDROID_PROJECT_PATH%
        exit /b 1
    )
    
    REM Check for gradlew
    if not exist "%ANDROID_PROJECT_PATH%\gradlew.bat" (
        echo.
        echo WARNING: gradlew.bat not found in project directory
        echo    Gradle build features will not work
    )
) else (
    echo   Android Project: Not specified (limited features)
    echo.
    echo   Tip: Specify project path for full features:
    echo      %0 %VPS_URL% C:\path\to\your\android\project
)

echo.

REM Build the agent if needed
if not exist "build\libs\localAgentClient.jar" (
    echo Building agent...
    call gradlew.bat build
    echo.
)

REM Start the agent
echo Starting Android Agent...
echo.

if not "%ANDROID_PROJECT_PATH%"=="" (
    java -jar build\libs\localAgentClient.jar "%VPS_URL%" "%ANDROID_PROJECT_PATH%"
) else (
    java -jar build\libs\localAgentClient.jar "%VPS_URL%"
)

