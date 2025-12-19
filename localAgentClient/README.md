# Local Android Studio Agent

This is the local agent that runs on your development machine and provides Android development capabilities to the AI assistant.

## Features

### Core Capabilities
- ✅ Android Emulator control (start, stop, list)
- ✅ APK installation and app launching
- ✅ ADB shell command execution
- ✅ Screenshot capture with organized storage
- ✅ Gradle build automation (debug/release)
- ✅ Logcat monitoring with filtering
- ✅ Project file browsing and reading
- ✅ Log file management

### Requirements

- **Java 11+** - Required to run the agent
- **Android SDK** - Must be installed and ANDROID_HOME set
- **Gradle** - Wrapper should be present in your Android project
- **Network** - Connection to remote agent server

## Quick Start

### 1. Set up Android SDK

Ensure ANDROID_HOME is set:

```bash
# Linux/macOS
export ANDROID_HOME=$HOME/Android/Sdk

# Windows
set ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk
```

### 2. Build the Agent

```bash
./gradlew build
```

### 3. Start the Agent

**Using the startup script (recommended):**

```bash
# Linux/macOS
./start-android-agent.sh wss://your-server.com:8443 /path/to/android/project

# Windows
start-android-agent.bat wss://your-server.com:8443 C:\path\to\android\project
```

**Manual start:**

```bash
# With Android project path (full features)
java -jar build/libs/localAgentClient.jar wss://your-server.com:8443 /path/to/android/project

# Without project path (limited features)
java -jar build/libs/localAgentClient.jar wss://your-server.com:8443
```

## Configuration

### Command Line Arguments

```
java -jar localAgentClient.jar [VPS_URL] [ANDROID_PROJECT_PATH]
```

- **VPS_URL** (optional): WebSocket URL of remote server
  - Default: `ws://127.0.0.1:8443`
  - Use `wss://` for secure connections
  
- **ANDROID_PROJECT_PATH** (optional): Path to your Android project
  - Enables Gradle builds, file browsing, and organized storage
  - Example: `/Users/john/AndroidStudioProjects/MyApp`

### Environment Variables

- **ANDROID_HOME** (required): Path to Android SDK
- **ANDROID_SDK_ROOT** (alternative): Alternative to ANDROID_HOME
- **COMPUTERNAME** / **HOSTNAME**: Used for agent identification

## Features by Configuration

### Without Project Path

Available features:
- ✅ Emulator control
- ✅ APK installation
- ✅ App launching
- ✅ ADB commands
- ✅ Screenshots (saved to current directory)
- ✅ Logcat
- ❌ Gradle builds
- ❌ File browsing
- ❌ Organized storage

### With Project Path

All features enabled:
- ✅ All features from above
- ✅ Gradle builds (debug/release)
- ✅ File browsing in project
- ✅ Screenshots saved to `project/screenshots/`
- ✅ Logs saved to `project/logs/`

## Directory Structure

When project path is configured:

```
/your/android/project/
├── screenshots/              # Auto-created
│   └── screenshot_2024-01-15_10-30-45.png
├── logs/                     # Auto-created
│   └── logcat_2024-01-15_10-30-00.txt
├── app/
│   ├── build/
│   │   └── outputs/apk/
│   └── src/
├── gradle/
├── gradlew
└── build.gradle
```

## Troubleshooting

### Agent Won't Connect

1. Check VPS server is running
2. Verify WebSocket URL is correct
3. Check firewall settings
4. For WSS, verify SSL certificates

### ANDROID_HOME Not Found

```bash
# Find your Android SDK
# Common locations:
# - Linux: ~/Android/Sdk
# - macOS: ~/Library/Android/sdk
# - Windows: C:\Users\YourName\AppData\Local\Android\Sdk

# Set it permanently
# Linux/macOS: Add to ~/.bashrc or ~/.zshrc
export ANDROID_HOME=/path/to/android/sdk

# Windows: Set in System Environment Variables
```

### Gradle Build Fails

1. Ensure project builds from command line:
   ```bash
   cd /path/to/project
   ./gradlew assembleDebug
   ```

2. Check Gradle wrapper exists:
   ```bash
   ls -la gradlew
   ```

3. Verify project path is correct

### Emulator Won't Start

1. List available emulators:
   ```bash
   $ANDROID_HOME/emulator/emulator -list-avds
   ```

2. Ensure no other emulator is running:
   ```bash
   adb devices
   ```

3. Check system resources (RAM, disk space)

## Security

### Development (Local Network)

```bash
java -jar localAgentClient.jar ws://localhost:8443 /path/to/project
```

### Production (Remote Server)

```bash
java -jar localAgentClient.jar wss://your-server.com:8443 /path/to/project
```

**Important:**
- Use WSS (WebSocket Secure) for remote connections
- Ensure server has valid SSL certificate
- Consider VPN for additional security
- Restrict server access with firewall rules

## Advanced Usage

### Custom Agent ID

The agent ID is automatically generated from your computer name. To customize:

```kotlin
// Modify LocalAndroidStudioAgent.kt
private val agentId: String = "my-custom-agent-id"
```

### Multiple Projects

Run multiple agents for different projects:

```bash
# Terminal 1 - Project A
java -jar localAgentClient.jar wss://server:8443 /path/to/projectA

# Terminal 2 - Project B  
java -jar localAgentClient.jar wss://server:8443 /path/to/projectB
```

Each agent will have a unique ID based on the computer name.

## Documentation

- **ANDROID_FEATURES.md** - Detailed feature documentation
- **../ANDROID_AGENT_USAGE.md** - Complete usage guide
- **../ANDROID_AGENT_QUICK_REFERENCE.md** - Quick reference for AI
- **../IMPLEMENTATION_SUMMARY.md** - Technical implementation details

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the documentation files
3. Check server logs for connection issues
4. Verify Android SDK installation

## License

[Your License Here]

