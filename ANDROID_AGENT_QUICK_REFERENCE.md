# Android Agent Quick Reference

## Tool Name
`android_studio`

## Quick Action Reference

### Emulator Management
```
start_emulator      - Start Android emulator (avd_name)
stop_emulator       - Stop running emulator
list_emulators      - List available AVDs
```

### App Deployment
```
install_apk         - Install APK file (apk_path)
run_app             - Launch app (package_name)
gradle_build        - Build APK with Gradle (build_variant)
gradle_install_run  - Build, install & run (build_variant, package_name)
```

### Debugging & Monitoring
```
screenshot          - Capture screen to project/screenshots/
logcat              - Get device logs (filter_tag, filter_package, log_level, max_lines)
logcat_clear        - Clear logcat buffer
save_log            - Save log content to project/logs/ (log_content, log_name)
```

### File Operations
```
browse_files        - List directory contents (directory_path)
read_file           - Read file content (file_path)
adb_shell           - Execute ADB shell command (command)
```

## Common Patterns

### Pattern 1: Fresh Start
```
1. list_emulators
2. start_emulator (avd_name)
3. gradle_install_run (build_variant, package_name)
```

### Pattern 2: Debug Workflow
```
1. logcat_clear
2. run_app (package_name)
3. screenshot
4. logcat (filter_package, log_level: "E")
5. save_log (log_content, log_name)
```

### Pattern 3: Code Review
```
1. browse_files (directory_path: "app/src/main")
2. read_file (file_path: "app/src/main/AndroidManifest.xml")
```

### Pattern 4: Build & Deploy
```
1. gradle_build (build_variant: "debug")
2. install_apk (apk_path: from build result)
3. run_app (package_name)
```

## Parameter Quick Reference

| Parameter | Type | Used In | Description |
|-----------|------|---------|-------------|
| action | string | ALL | Required: Action to perform |
| avd_name | string | start_emulator | AVD name (e.g., "Pixel_5_API_33") |
| apk_path | string | install_apk | Full path to APK file |
| package_name | string | run_app, gradle_install_run, logcat | Android package (e.g., "com.example.app") |
| build_variant | string | gradle_build, gradle_install_run | "debug" or "release" |
| command | string | adb_shell | Shell command to execute |
| filter_tag | string | logcat | Log tag filter |
| filter_package | string | logcat | Package name filter |
| log_level | string | logcat | V, D, I, W, E, F (default: V) |
| max_lines | integer | logcat | Max log lines (default: 500) |
| file_path | string | read_file | Relative path in project |
| directory_path | string | browse_files | Relative path in project |
| log_content | string | save_log | Log text to save |
| log_name | string | save_log | Log file name |

## Response Formats

### Success Response
```json
{
  "status": "success",
  "message": "...",
  // action-specific fields
}
```

### Error Response
```json
{
  "status": "error",
  "error": "Error message"
}
```

### Specific Responses

**gradle_build:**
```json
{
  "status": "success",
  "message": "Build completed successfully",
  "variant": "debug",
  "apk_path": "/path/to/app-debug.apk",
  "output": "..."
}
```

**screenshot:**
```json
{
  "status": "success",
  "screenshot_path": "/full/path/to/screenshot.png",
  "relative_path": "screenshots/screenshot_2024-01-15_10-30-45.png"
}
```

**logcat:**
```json
{
  "status": "success",
  "logs": "log content...",
  "line_count": 234,
  "filter_tag": "MyApp",
  "log_level": "D"
}
```

**browse_files:**
```json
{
  "status": "success",
  "path": "/full/path",
  "relative_path": "app/src",
  "files": [
    {"name": "main", "type": "directory", "size": 0, "last_modified": 123456789},
    {"name": "build.gradle", "type": "file", "size": 1234, "last_modified": 123456789}
  ]
}
```

**read_file:**
```json
{
  "status": "success",
  "file_path": "app/src/main/AndroidManifest.xml",
  "absolute_path": "/full/path/to/file",
  "content": "file content...",
  "size": 1234
}
```

## Timeouts

- Standard operations: 60 seconds
- Emulator start: 120 seconds
- Gradle builds: 300 seconds (5 minutes)

## Prerequisites

- Android SDK installed
- ANDROID_HOME environment variable set
- For Gradle actions: Android project path configured
- For file operations: Android project path configured

## Tips for AI Assistant

1. **Always check prerequisites** before suggesting actions
2. **Chain actions logically**: clear logcat → run app → get logs
3. **Use filters** to reduce logcat output
4. **Save important logs** for user reference
5. **Provide relative paths** in responses when project path is configured
6. **Handle errors gracefully** and suggest alternatives
7. **Explain what you're doing** before executing actions

## Example AI Responses

**User:** "Build and run my app"
**AI:** "I'll build your app using Gradle and install it on the emulator. Let me:
1. Build the debug APK
2. Install and launch it
3. Take a screenshot to confirm it's running"

**User:** "My app crashed"
**AI:** "Let me help you debug that. I'll:
1. Get the error logs from logcat
2. Save them to a file for your review
3. Show you the relevant error messages"

**User:** "Show me my manifest"
**AI:** "I'll read your AndroidManifest.xml file and display its contents."

