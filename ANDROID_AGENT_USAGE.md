# Android Agent Usage Guide

This guide demonstrates how to use the Android Agent features through the AI assistant.

## Architecture

```
┌─────────────────────┐         WebSocket          ┌──────────────────────┐
│  AI Assistant       │ ◄─────────────────────────► │  Remote Agent Server │
│  (Claude)           │                             │  (VPS)               │
└─────────────────────┘                             └──────────────────────┘
                                                              │
                                                              │ WebSocket
                                                              ▼
                                                    ┌──────────────────────┐
                                                    │  Local Agent Client  │
                                                    │  (Your Dev Machine)  │
                                                    └──────────────────────┘
                                                              │
                                                              ▼
                                                    ┌──────────────────────┐
                                                    │  Android SDK/Emulator│
                                                    │  Android Project     │
                                                    └──────────────────────┘
```

## Setup Instructions

### 1. Start the Remote Agent Server (VPS)

```bash
cd remoteAgentServer
./gradlew run
```

The server will start on port 8443 (configurable).

### 2. Start the Local Agent Client (Development Machine)

```bash
cd localAgentClient
./gradlew build

# Run with Android project path
java -jar build/libs/localAgentClient.jar \
  wss://your-vps-server.com:8443 \
  /path/to/your/android/project
```

**Example:**
```bash
java -jar build/libs/localAgentClient.jar \
  wss://myserver.com:8443 \
  /Users/john/AndroidStudioProjects/MyApp
```

### 3. Verify Connection

Check the server endpoint:
```bash
curl https://your-vps-server.com:8443/mcp/agents/status
```

Expected response:
```json
{
  "connected_agents": ["android-studio-COMPUTERNAME"],
  "count": 1
}
```

## Usage Examples

### Example 1: Complete Development Workflow

**User:** "Start the Android emulator, build my app, install it, and take a screenshot"

**AI Assistant will:**
1. Start emulator: `action: start_emulator`
2. Build app: `action: gradle_install_run`
3. Take screenshot: `action: screenshot`

### Example 2: Debug Crash

**User:** "My app crashed. Can you get the recent error logs and save them?"

**AI Assistant will:**
1. Get logcat with error level: `action: logcat, log_level: E`
2. Save logs: `action: save_log`

### Example 3: Check Manifest File

**User:** "Show me the AndroidManifest.xml file"

**AI Assistant will:**
1. Read file: `action: read_file, file_path: app/src/main/AndroidManifest.xml`

### Example 4: Monitor App Logs

**User:** "Show me the logs for my app com.example.myapp"

**AI Assistant will:**
1. Clear old logs: `action: logcat_clear`
2. Get filtered logs: `action: logcat, filter_package: com.example.myapp`

### Example 5: Build Release APK

**User:** "Build a release version of my app"

**AI Assistant will:**
1. Build release: `action: gradle_build, build_variant: release`
2. Returns APK path for distribution

## Common Workflows

### Initial Setup Workflow

```
1. List available emulators
2. Start preferred emulator
3. Browse project structure
4. Build debug APK
5. Install and run
```

### Testing Workflow

```
1. Clear logcat
2. Install latest build
3. Run app
4. Take screenshot of UI
5. Capture logs
6. Save logs for analysis
```

### Release Workflow

```
1. Build release variant
2. Get APK path
3. (Manual: Sign and distribute)
```

## Features Summary

| Feature | Action | Project Path Required |
|---------|--------|----------------------|
| Start Emulator | `start_emulator` | No |
| Stop Emulator | `stop_emulator` | No |
| List Emulators | `list_emulators` | No |
| Install APK | `install_apk` | No |
| Run App | `run_app` | No |
| ADB Shell | `adb_shell` | No |
| Screenshot | `screenshot` | No (but recommended) |
| Gradle Build | `gradle_build` | **Yes** |
| Gradle Install & Run | `gradle_install_run` | **Yes** |
| Logcat | `logcat` | No |
| Clear Logcat | `logcat_clear` | No |
| Browse Files | `browse_files` | **Yes** |
| Read File | `read_file` | **Yes** |
| Save Log | `save_log` | No (but recommended) |

## File Organization

With project path configured:

```
/path/to/android/project/
├── screenshots/           # Auto-created
│   └── screenshot_2024-01-15_10-30-45.png
├── logs/                  # Auto-created
│   └── logcat_2024-01-15_10-30-00.txt
├── app/
│   ├── build/
│   │   └── outputs/
│   │       └── apk/
│   │           ├── debug/
│   │           └── release/
│   └── src/
├── gradle/
├── gradlew
└── build.gradle
```

## Environment Variables

Required:
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`: Path to Android SDK

Optional:
- `COMPUTERNAME` or `HOSTNAME`: Used for agent identification

## Troubleshooting

### Agent Not Connecting

1. Check VPS server is running
2. Verify WebSocket URL is correct
3. Check firewall settings
4. Verify SSL certificates (if using wss://)

### Gradle Build Fails

1. Ensure project builds from command line: `./gradlew assembleDebug`
2. Check Gradle wrapper exists
3. Verify project path is correct
4. Check build variant name

### Emulator Won't Start

1. Verify ANDROID_HOME is set
2. Check emulator is installed: `$ANDROID_HOME/emulator/emulator -list-avds`
3. Ensure no other emulator is running
4. Check system resources (RAM, disk space)

### Screenshots Not Saving

1. Verify emulator/device is running
2. Check project path is configured
3. Ensure write permissions on project directory

## Security Considerations

1. **Local Network**: For development, use local network (ws://localhost:8443)
2. **Production**: Use WSS with valid SSL certificates
3. **Firewall**: Restrict access to VPS server
4. **Authentication**: Consider adding authentication to WebSocket endpoint

## Performance Tips

1. **Gradle Builds**: First build may be slow (downloading dependencies)
2. **Logcat**: Use filters to reduce data transfer
3. **Screenshots**: Saved locally, minimal network impact
4. **File Reading**: Limited to 1MB per file

## Next Steps

1. Configure your Android project path
2. Start the local agent
3. Ask the AI assistant to help with your Android development tasks!

Example prompts:
- "Build and run my Android app"
- "Take a screenshot of the current screen"
- "Show me the recent crash logs"
- "What's in my AndroidManifest.xml?"
- "Build a release APK"

