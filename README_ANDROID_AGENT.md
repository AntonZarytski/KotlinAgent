# Android Agent System

AI-powered Android development assistant that allows Claude to interact with your Android projects, build apps, capture screenshots, monitor logs, and browse code.

## 🚀 Quick Start

### 1. Start the System

```bash
./start-android-system.sh
```

This will:
- ✅ Verify your Android project exists
- ✅ Check Android SDK configuration
- ✅ Build the local agent (if needed)
- ✅ Start the remote server
- ✅ Start the local agent with your project path
- ✅ Create log files for debugging

### 2. Verify Everything is Running

```bash
./check-android-system.sh
```

Expected output:
```
✅ Remote Server: Running
✅ Local Agent: Running
✅ Android Project: Found
✅ Android SDK: Configured
```

### 3. Ask AI to Work with Your Android Project

Now you can ask Claude:

```
"Show me my AndroidManifest.xml"
"Build my app in debug mode"
"Take a screenshot of the emulator"
"Show me the recent error logs"
"List files in the app/src/main directory"
```

### 4. Stop the System When Done

```bash
./stop-android-system.sh
```

## 📋 What Can AI Do?

### Emulator Control
- ✅ Start/stop Android emulator
- ✅ List available AVDs
- ✅ Install APK files
- ✅ Launch apps

### Build & Deploy
- ✅ Build APK with Gradle (debug/release)
- ✅ Build, install, and run in one command
- ✅ Find built APK paths

### Debugging & Monitoring
- ✅ Capture screenshots (saved to `project/screenshots/`)
- ✅ Retrieve logcat logs with filtering
- ✅ Filter by tag, package, or log level
- ✅ Save logs to files (saved to `project/logs/`)

### Code Browsing
- ✅ Browse project directories
- ✅ Read file contents
- ✅ View file metadata

### ADB Commands
- ✅ Execute arbitrary ADB shell commands

## 🏗️ Architecture

```
┌─────────────┐
│   You       │
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│   AI (Claude)           │
│   - Decides actions     │
│   - Calls tools         │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────────────┐
│   Remote Server (VPS)           │
│   - Port 8443                   │
│   - Routes requests             │
│   - Manages connections         │
└──────────┬──────────────────────┘
           │ WebSocket
           ▼
┌─────────────────────────────────┐
│   Local Agent (Your Mac)        │
│   - Executes commands           │
│   - Accesses file system        │
│   - Runs Gradle builds          │
│   - Captures screenshots        │
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│   Android Project & SDK         │
│   /Users/anton/StudioProjects/  │
│   RoundTimer                    │
└─────────────────────────────────┘
```

## 📁 Project Structure

```
KotlinAgent/
├── remoteAgentServer/          # VPS server component
├── localAgentClient/           # Local machine component
├── start-android-system.sh     # Start everything
├── stop-android-system.sh      # Stop everything
├── check-android-system.sh     # Check status
├── logs/                       # System logs
│   ├── server.log
│   └── local-agent.log
└── Documentation:
    ├── SOLUTION_SUMMARY.md           # Start here!
    ├── ARCHITECTURE_AND_SETUP.md     # Architecture details
    ├── TROUBLESHOOTING.md            # Problem solving
    ├── ANDROID_AGENT_USAGE.md        # Usage guide
    └── ANDROID_AGENT_QUICK_REFERENCE.md
```

## 🔧 Requirements

### System Requirements
- **Java 11+** - For running the agents
- **Android SDK** - Must be installed
- **Gradle** - Wrapper in your Android project
- **macOS/Linux** - Scripts are bash-based

### Environment Variables
```bash
# Required
export ANDROID_HOME=/path/to/android/sdk

# Optional (auto-detected)
export COMPUTERNAME=your-computer-name
```

### Your Android Project
- Location: `/Users/anton/StudioProjects/RoundTimer`
- Must have: `gradlew` (Gradle wrapper)
- Must be: Valid Android/Gradle project

## 🐛 Troubleshooting

### Issue: "Android project not configured"

**Solution:**
```bash
# 1. Check if system is running
./check-android-system.sh

# 2. If not running, start it
./start-android-system.sh

# 3. Try again
```

### Issue: Local agent won't start

**Solution:**
```bash
# Check ANDROID_HOME
echo $ANDROID_HOME

# If not set:
export ANDROID_HOME=~/Library/Android/sdk

# Add to ~/.zshrc for persistence
echo 'export ANDROID_HOME=~/Library/Android/sdk' >> ~/.zshrc
```

### Issue: Build fails

**Solution:**
```bash
# Test build manually
cd /Users/anton/StudioProjects/RoundTimer
./gradlew assembleDebug

# If it works manually but not through AI:
# Check logs/local-agent.log for errors
```

**For more issues:** See `TROUBLESHOOTING.md`

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **SOLUTION_SUMMARY.md** | Overview of problems and solutions |
| **ARCHITECTURE_AND_SETUP.md** | Complete architecture and setup guide |
| **TROUBLESHOOTING.md** | Common issues and solutions |
| **ANDROID_AGENT_USAGE.md** | Detailed usage examples |
| **ANDROID_AGENT_QUICK_REFERENCE.md** | Quick reference for AI |
| **IMPLEMENTATION_SUMMARY.md** | Technical implementation details |

## 🎯 Example Workflows

### Workflow 1: Build and Test

```
User: "Start the Android emulator"
AI: [Starts emulator]

User: "Build my app in debug mode"
AI: [Runs gradle build]

User: "Install and run the app"
AI: [Installs and launches]

User: "Take a screenshot"
AI: [Captures screenshot to project/screenshots/]
```

### Workflow 2: Debug Crash

```
User: "My app crashed, show me the error logs"
AI: [Retrieves logcat with error level]

User: "Save these logs for later"
AI: [Saves to project/logs/crash_YYYY-MM-DD_HH-mm-ss.txt]
```

### Workflow 3: Code Review

```
User: "Show me my AndroidManifest.xml"
AI: [Displays file content]

User: "List all files in app/src/main"
AI: [Shows directory structure]

User: "Show me MainActivity.kt"
AI: [Displays file content]
```

## ⚠️ Important Notes

1. **Always start the system before asking AI to work with Android**
   ```bash
   ./start-android-system.sh
   ```

2. **Keep the system running while working**
   - Don't close the terminal
   - Or run in background/screen/tmux

3. **Check status if something doesn't work**
   ```bash
   ./check-android-system.sh
   ```

4. **Check logs for debugging**
   ```bash
   tail -f logs/local-agent.log
   tail -f logs/server.log
   ```

## 🎓 How It Works

When you ask AI: **"Show me my AndroidManifest.xml"**

1. **AI** decides to use `android_studio` tool with action `read_file`
2. **Remote Server** receives the request and forwards to local agent
3. **Local Agent** reads `/Users/anton/StudioProjects/RoundTimer/app/src/main/AndroidManifest.xml`
4. **Local Agent** sends content back to server
5. **Server** returns to AI
6. **AI** shows you the file content

**Key Point:** The local agent MUST be running for this to work!

## 🚦 Status Indicators

When you run `./check-android-system.sh`:

- ✅ **Green checkmark** - Component is working
- ❌ **Red X** - Component is not running
- ⚠️ **Yellow warning** - Component has issues
- ℹ️ **Blue info** - Additional information

## 📞 Getting Help

1. **Read:** `SOLUTION_SUMMARY.md` - Explains everything
2. **Check:** `./check-android-system.sh` - Shows current status
3. **Debug:** `logs/local-agent.log` and `logs/server.log`
4. **Consult:** `TROUBLESHOOTING.md` - Common solutions

## 🎉 Success Criteria

You know it's working when:

1. ✅ `./check-android-system.sh` shows all green
2. ✅ AI can read your AndroidManifest.xml
3. ✅ AI can build your app
4. ✅ AI can take screenshots
5. ✅ AI can browse your project files

## 🔄 Daily Workflow

```bash
# Morning
./start-android-system.sh

# Work with AI all day
# AI can now access your Android project!

# Evening
./stop-android-system.sh
```

That's it! 🚀

