# Android Agent Architecture and Setup Guide

## 🏗️ Architecture Overview

### Complete Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          USER INTERACTION                                │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  AI ASSISTANT (Claude)                                                   │
│  - Receives user request                                                 │
│  - Decides which MCP tool to use                                         │
│  - Calls: android_studio tool with action and parameters                 │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  REMOTE AGENT SERVER (VPS)                                               │
│  Location: /Users/anton/IdeaProjects/KotlinAgent/remoteAgentServer      │
│                                                                           │
│  Components:                                                              │
│  1. AndroidStudioLocalMcp.kt                                             │
│     - Receives tool call from AI                                         │
│     - Validates parameters                                               │
│     - Calls LocalAgentManager.executeOnLocalAgent()                      │
│                                                                           │
│  2. LocalAgentManager (ConnectedAgent.kt)                                │
│     - Maintains WebSocket connections to local agents                    │
│     - Routes requests to appropriate agent                               │
│     - Waits for response with timeout                                    │
│                                                                           │
│  3. WebSocket Server                                                      │
│     - Listens on port 8443                                               │
│     - Accepts connections from local agents                              │
│     - Sends ExecuteRequest messages                                      │
│     - Receives ExecuteResponse messages                                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ WebSocket (ws:// or wss://)
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  LOCAL AGENT CLIENT (Your Development Machine)                           │
│  Location: /Users/anton/IdeaProjects/KotlinAgent/localAgentClient       │
│                                                                           │
│  Components:                                                              │
│  1. LocalAndroidStudioAgent.kt                                           │
│     - Connects to VPS via WebSocket                                      │
│     - Registers capabilities with server                                 │
│     - Receives ExecuteRequest messages                                   │
│     - Executes commands locally                                          │
│     - Sends ExecuteResponse back to server                               │
│                                                                           │
│  2. Command Executors                                                     │
│     - gradleBuild() - Runs ./gradlew commands                            │
│     - takeScreenshot() - Runs adb commands                               │
│     - browseFiles() - Reads local file system                            │
│     - readFile() - Reads file content                                    │
│     - etc.                                                                │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  ANDROID PROJECT & SDK (Your Development Machine)                        │
│  Project: /Users/anton/StudioProjects/RoundTimer                         │
│  SDK: $ANDROID_HOME                                                       │
│                                                                           │
│  - Gradle builds                                                          │
│  - ADB commands                                                           │
│  - File system access                                                     │
│  - Screenshots saved to: /Users/anton/StudioProjects/RoundTimer/screenshots/│
│  - Logs saved to: /Users/anton/StudioProjects/RoundTimer/logs/           │
└─────────────────────────────────────────────────────────────────────────┘
```

## 🔧 Setup Instructions

### Step 1: Start the Remote Agent Server (VPS)

The remote server must be running first to accept connections.

```bash
cd /Users/anton/IdeaProjects/KotlinAgent/remoteAgentServer
./gradlew run
```

**Expected output:**
```
🚀 Starting Remote Agent Server...
📡 WebSocket server listening on port 8443
✅ Server ready to accept agent connections
```

### Step 2: Start the Local Agent Client (Development Machine)

The local agent connects to the remote server and provides Android capabilities.

```bash
cd /Users/anton/IdeaProjects/KotlinAgent/localAgentClient

# Build first (if not already built)
./gradlew build

# Start with your Android project path
java -jar build/libs/localAgentClient.jar \
  ws://127.0.0.1:8443 \
  /Users/anton/StudioProjects/RoundTimer
```

**Expected output:**
```
🚀 Starting Local Android Studio Agent...
📍 Agent ID: android-studio-Antons-MacBook
🌐 VPS URL: ws://127.0.0.1:8443
📂 Android Project: /Users/anton/StudioProjects/RoundTimer
   ✅ Project directory found
✅ ANDROID_HOME found: /Users/anton/Library/Android/sdk
   📱 ADB: /Users/anton/Library/Android/sdk/platform-tools/adb
   🖥️  Emulator: /Users/anton/Library/Android/sdk/emulator/emulator

🔄 Connecting to VPS...
✅ Connected to VPS
📤 Registered with capabilities: [emulator_control, apk_management, adb_shell, screenshots, gradle_build, logcat, file_browsing, log_saving]
```

### Step 3: Verify Connection

Check that the agent is connected:

```bash
# If server has a status endpoint
curl http://localhost:8443/mcp/agents/status

# Or check server logs for:
# "✅ Agent registered: android-studio-Antons-MacBook"
```

## 🎯 How AI Accesses Your Android Project

### Example: AI reads AndroidManifest.xml

**User says:** "Show me my AndroidManifest.xml"

**Flow:**

1. **AI (Claude)** decides to use `android_studio` tool:
   ```json
   {
     "action": "read_file",
     "file_path": "app/src/main/AndroidManifest.xml"
   }
   ```

2. **Remote Server** receives the tool call:
   - `AndroidStudioLocalMcp.executeTool()` is called
   - Calls `LocalAgentManager.executeOnLocalAgent("android_studio", arguments)`

3. **LocalAgentManager** finds connected agent:
   - Looks for agent with tool name "android_studio"
   - Creates ExecuteRequest with unique requestId
   - Sends via WebSocket to local agent

4. **Local Agent** receives ExecuteRequest:
   - `LocalAndroidStudioAgent.executeCommand()` is called
   - Matches action "read_file"
   - Calls `readFile(arguments)`

5. **readFile()** executes:
   ```kotlin
   val filePath = arguments["file_path"]?.jsonPrimitive?.content
   // filePath = "app/src/main/AndroidManifest.xml"
   
   val fullPath = File(androidProjectPath, filePath)
   // fullPath = "/Users/anton/StudioProjects/RoundTimer/app/src/main/AndroidManifest.xml"
   
   val content = fullPath.readText()
   // Reads the actual file from your disk
   
   return buildJsonObject {
       put("status", "success")
       put("content", content)
       put("file_path", filePath)
   }.toString()
   ```

6. **Local Agent** sends ExecuteResponse back to server

7. **Remote Server** receives response and returns to AI

8. **AI** receives file content and shows it to user

## 🐛 Troubleshooting Your Issues

### Issue 1: "Android проект не настроен в системе"

**Problem:** The AI cannot access your project files.

**Root Cause:** The local agent is NOT running, or NOT connected to the server.

**Solution:**

1. **Check if local agent is running:**
   ```bash
   # Look for java process
   ps aux | grep localAgentClient
   ```

2. **If not running, start it:**
   ```bash
   cd /Users/anton/IdeaProjects/KotlinAgent/localAgentClient
   java -jar build/libs/localAgentClient.jar \
     ws://127.0.0.1:8443 \
     /Users/anton/StudioProjects/RoundTimer
   ```

3. **Keep it running in a terminal** - Don't close the terminal!

### Issue 2: AI Stops Execution Prematurely

**Problem:** AI gives up after first attempt.

**Root Cause:** The tool returns an error, and AI interprets it as "not configured".

**Solution:**

The AI should check if the agent is connected first. The proper flow is:

1. AI checks: `LocalAgentManager.isAgentConnected("android_studio")`
2. If not connected → Tell user to start local agent
3. If connected → Execute commands

**For you:** Make sure the local agent is running BEFORE asking AI to do Android tasks.

## ✅ Verification Checklist

Run through this checklist to ensure everything is working:

- [ ] Remote server is running (`remoteAgentServer/gradlew run`)
- [ ] Local agent is built (`localAgentClient/gradlew build`)
- [ ] Local agent is running with correct project path
- [ ] Local agent shows "✅ Connected to VPS"
- [ ] Local agent shows "✅ Project directory found"
- [ ] ANDROID_HOME is set and valid
- [ ] Project path exists: `/Users/anton/StudioProjects/RoundTimer`

## 📝 Quick Start Commands

```bash
# Terminal 1: Start Remote Server
cd /Users/anton/IdeaProjects/KotlinAgent/remoteAgentServer
./gradlew run

# Terminal 2: Start Local Agent
cd /Users/anton/IdeaProjects/KotlinAgent/localAgentClient
./gradlew build
java -jar build/libs/localAgentClient.jar \
  ws://127.0.0.1:8443 \
  /Users/anton/StudioProjects/RoundTimer

# Now you can ask AI to work with your Android project!
```

## 🔍 Debugging Tips

### Check Server Logs
Look for:
- "Agent registered: android-studio-XXX"
- "Executing tool: android_studio"
- Any error messages

### Check Local Agent Logs
Look for:
- "Connected to VPS"
- "Received command: XXX"
- "Executing action: XXX"
- Any error messages

### Test Manually
You can test if the connection works by checking the server's agent list.

## 📚 Related Documentation

- `ANDROID_AGENT_USAGE.md` - Complete usage guide
- `localAgentClient/README.md` - Local agent setup
- `ANDROID_AGENT_QUICK_REFERENCE.md` - Quick reference
- `TROUBLESHOOTING.md` - Troubleshooting guide

## 🚀 Quick Start (TL;DR)

```bash
# 1. Start the system
./start-android-system.sh

# 2. Check status
./check-android-system.sh

# 3. Ask AI to work with your Android project!
# Example: "Show me my AndroidManifest.xml"
# Example: "Build and run my app"
# Example: "Take a screenshot"

# 4. When done, stop the system
./stop-android-system.sh
```

