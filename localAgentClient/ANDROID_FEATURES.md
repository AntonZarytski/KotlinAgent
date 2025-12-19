# Android Agent Features

This document describes the Android development features available through the Local Android Studio Agent.

## Overview

The Local Android Studio Agent connects to the remote server and provides comprehensive Android development capabilities including emulator control, Gradle builds, logcat monitoring, and file browsing.

## Setup

### Starting the Agent

```bash
# Basic usage (no project path)
java -jar localAgentClient.jar ws://your-server:8443

# With Android project path (recommended)
java -jar localAgentClient.jar ws://your-server:8443 /path/to/your/android/project
```

### Project Path Benefits

When you specify an Android project path:
- Screenshots are saved to `<project>/screenshots/`
- Logs are saved to `<project>/logs/`
- File browsing is enabled for the project
- Gradle builds can be executed
- Organized storage of all artifacts

## Available Actions

### 1. Emulator Control

#### Start Emulator
```json
{
  "action": "start_emulator",
  "avd_name": "Pixel_5_API_33"
}
```

#### Stop Emulator
```json
{
  "action": "stop_emulator"
}
```

#### List Available Emulators
```json
{
  "action": "list_emulators"
}
```

### 2. App Installation & Launch

#### Install APK
```json
{
  "action": "install_apk",
  "apk_path": "/path/to/app.apk"
}
```

#### Run App
```json
{
  "action": "run_app",
  "package_name": "com.example.myapp"
}
```

### 3. Gradle Build & Deploy

#### Build APK
```json
{
  "action": "gradle_build",
  "build_variant": "debug"
}
```
Builds the APK using Gradle. Returns the path to the built APK.

#### Build, Install & Run
```json
{
  "action": "gradle_install_run",
  "build_variant": "debug",
  "package_name": "com.example.myapp"
}
```
Builds, installs, and launches the app in one step.

### 4. Screenshot Capture

```json
{
  "action": "screenshot"
}
```
Takes a screenshot and saves it to:
- `<project>/screenshots/screenshot_YYYY-MM-DD_HH-mm-ss.png` (if project path configured)
- `screenshot_YYYY-MM-DD_HH-mm-ss.png` (otherwise)

### 5. Logcat Monitoring

#### Get Logs
```json
{
  "action": "logcat",
  "filter_tag": "MyApp",
  "log_level": "D",
  "max_lines": 500
}
```

Parameters:
- `filter_tag`: Filter by log tag (optional)
- `filter_package`: Filter by package name (optional)
- `log_level`: V, D, I, W, E, F (default: V)
- `max_lines`: Maximum lines to retrieve (default: 500)

#### Clear Logcat
```json
{
  "action": "logcat_clear"
}
```

### 6. File Browsing

#### Browse Directory
```json
{
  "action": "browse_files",
  "directory_path": "app/src/main"
}
```
Lists files and directories. Path is relative to Android project root.

#### Read File
```json
{
  "action": "read_file",
  "file_path": "app/src/main/AndroidManifest.xml"
}
```
Reads file content (max 1MB). Path is relative to Android project root.

### 7. Log Saving

```json
{
  "action": "save_log",
  "log_content": "Log content here...",
  "log_name": "crash_report"
}
```
Saves log content to:
- `<project>/logs/<log_name>_YYYY-MM-DD_HH-mm-ss.txt` (if project path configured)
- `<log_name>_YYYY-MM-DD_HH-mm-ss.txt` (otherwise)

### 8. ADB Shell Commands

```json
{
  "action": "adb_shell",
  "command": "pm list packages"
}
```
Executes arbitrary ADB shell commands.

## Directory Structure

When Android project path is configured:

```
/your/android/project/
├── screenshots/
│   ├── screenshot_2024-01-15_10-30-45.png
│   └── screenshot_2024-01-15_10-31-20.png
├── logs/
│   ├── logcat_2024-01-15_10-30-00.txt
│   └── crash_report_2024-01-15_11-00-00.txt
├── app/
├── gradle/
└── ...
```

## Requirements

- Android SDK installed
- ANDROID_HOME environment variable set
- Gradle wrapper in project (for build actions)
- Android emulator or device connected

## Troubleshooting

### Android SDK Not Found
Set the ANDROID_HOME environment variable:
```bash
export ANDROID_HOME=/path/to/android/sdk
```

### Gradle Build Fails
Ensure:
- Gradle wrapper exists in project root
- Project builds successfully from command line
- Correct build variant specified

### Logcat Empty
- Clear logcat first: `"action": "logcat_clear"`
- Run the app to generate logs
- Then retrieve logs: `"action": "logcat"`

