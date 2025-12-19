# Android Agent System - Troubleshooting Guide

## Quick Diagnosis

Run the status check script first:

```bash
./check-android-system.sh
```

This will show you exactly what's running and what's not.

## Common Issues and Solutions

### Issue 1: "Android проект не настроен в системе"

**Symptom:** AI says it cannot access the Android project.

**Root Cause:** Local agent is NOT running or NOT connected.

**Solution:**

1. **Check if local agent is running:**
   ```bash
   ./check-android-system.sh
   ```

2. **If not running, start the system:**
   ```bash
   ./start-android-system.sh
   ```

3. **Verify connection:**
   ```bash
   # Check logs
   tail -f logs/local-agent.log
   
   # Look for:
   # "✅ Connected to VPS"
   # "📤 Registered with capabilities"
   ```

4. **If still not working, check server logs:**
   ```bash
   tail -f logs/server.log
   
   # Look for:
   # "Agent registered: android-studio-XXX"
   ```

### Issue 2: AI Stops Execution Prematurely

**Symptom:** AI gives up after first attempt, doesn't retry.

**Root Cause:** The tool returns an error, and AI interprets it as "not configured".

**Solution:**

1. **Ensure local agent is running BEFORE asking AI:**
   ```bash
   ./check-android-system.sh
   ```

2. **If you see errors, restart the system:**
   ```bash
   ./stop-android-system.sh
   ./start-android-system.sh
   ```

3. **Ask AI to check connection first:**
   ```
   User: "Is the Android agent connected?"
   ```

4. **Then proceed with your request:**
   ```
   User: "Now show me my AndroidManifest.xml"
   ```

### Issue 3: Local Agent Won't Start

**Symptom:** `start-android-system.sh` fails to start local agent.

**Diagnosis:**

```bash
# Check the log
cat logs/local-agent.log

# Common errors:
# - "ANDROID_HOME not found"
# - "Connection refused"
# - "Project directory does not exist"
```

**Solutions:**

**If ANDROID_HOME not found:**
```bash
# Find your Android SDK
ls -la ~/Library/Android/sdk  # macOS
ls -la ~/Android/Sdk          # Linux

# Set it
export ANDROID_HOME=~/Library/Android/sdk

# Add to ~/.zshrc or ~/.bashrc for persistence
echo 'export ANDROID_HOME=~/Library/Android/sdk' >> ~/.zshrc
```

**If Connection refused:**
```bash
# Server is not running
# Start it first:
cd remoteAgentServer
./gradlew run

# Then in another terminal:
cd localAgentClient
java -jar build/libs/localAgentClient.jar \
  ws://127.0.0.1:8443 \
  /Users/anton/StudioProjects/RoundTimer
```

**If Project directory does not exist:**
```bash
# Verify path
ls -la /Users/anton/StudioProjects/RoundTimer

# If wrong path, update start-android-system.sh:
# Change ANDROID_PROJECT_PATH to correct path
```

### Issue 4: Remote Server Won't Start

**Symptom:** Port 8443 already in use or server crashes.

**Diagnosis:**

```bash
# Check if port is in use
lsof -i :8443

# Check server log
cat logs/server.log
```

**Solutions:**

**If port is in use:**
```bash
# Kill existing process
kill $(lsof -ti:8443)

# Or use different port (requires code changes)
```

**If server crashes:**
```bash
# Check Java version
java -version  # Should be 11+

# Rebuild server
cd remoteAgentServer
./gradlew clean build
./gradlew run
```

### Issue 5: Gradle Build Fails

**Symptom:** AI tries to build but gets error.

**Diagnosis:**

```bash
# Test build manually
cd /Users/anton/StudioProjects/RoundTimer
./gradlew assembleDebug
```

**Solutions:**

**If gradlew not found:**
```bash
# Ensure you're in the right directory
ls -la gradlew

# If missing, regenerate wrapper:
gradle wrapper
```

**If build fails:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug

# Check for errors in output
```

**If Gradle version issues:**
```bash
# Update Gradle wrapper
./gradlew wrapper --gradle-version=8.0
```

### Issue 6: Screenshots Not Saving

**Symptom:** Screenshot command succeeds but file not found.

**Diagnosis:**

```bash
# Check if directory exists
ls -la /Users/anton/StudioProjects/RoundTimer/screenshots/

# Check local agent log
grep -i screenshot logs/local-agent.log
```

**Solutions:**

**If directory doesn't exist:**
```bash
# Create it manually
mkdir -p /Users/anton/StudioProjects/RoundTimer/screenshots

# Or restart local agent (it creates directories on startup)
./stop-android-system.sh
./start-android-system.sh
```

**If permission denied:**
```bash
# Fix permissions
chmod 755 /Users/anton/StudioProjects/RoundTimer/screenshots
```

### Issue 7: Logcat Returns Empty

**Symptom:** Logcat command returns no logs.

**Solutions:**

1. **Clear logcat first:**
   ```
   AI: Use action "logcat_clear"
   ```

2. **Run the app:**
   ```
   AI: Use action "run_app" with package_name
   ```

3. **Then get logs:**
   ```
   AI: Use action "logcat" with filter_package
   ```

4. **Check if device is connected:**
   ```bash
   $ANDROID_HOME/platform-tools/adb devices
   ```

### Issue 8: File Browsing Returns Error

**Symptom:** Cannot browse or read files.

**Diagnosis:**

```bash
# Check if project path is correct
ls -la /Users/anton/StudioProjects/RoundTimer/app/src/main/

# Check local agent log
grep -i "browse\|read" logs/local-agent.log
```

**Solutions:**

**If path is wrong:**
- Ensure you're using relative paths from project root
- Example: `app/src/main/AndroidManifest.xml` NOT `/app/src/main/AndroidManifest.xml`

**If file doesn't exist:**
```bash
# Verify file exists
ls -la /Users/anton/StudioProjects/RoundTimer/app/src/main/AndroidManifest.xml
```

## Debugging Workflow

### Step 1: Check System Status

```bash
./check-android-system.sh
```

### Step 2: Check Logs

```bash
# Server log
tail -f logs/server.log

# Local agent log
tail -f logs/local-agent.log
```

### Step 3: Restart System

```bash
./stop-android-system.sh
./start-android-system.sh
```

### Step 4: Test Manually

```bash
# Test Android SDK
$ANDROID_HOME/platform-tools/adb devices

# Test Gradle
cd /Users/anton/StudioProjects/RoundTimer
./gradlew tasks

# Test emulator
$ANDROID_HOME/emulator/emulator -list-avds
```

### Step 5: Ask AI to Test

```
User: "List available Android emulators"
User: "Take a screenshot"
User: "Show me the app directory structure"
```

## Getting Help

If you're still stuck:

1. **Collect diagnostic information:**
   ```bash
   ./check-android-system.sh > diagnostic.txt
   cat logs/server.log >> diagnostic.txt
   cat logs/local-agent.log >> diagnostic.txt
   ```

2. **Check documentation:**
   - `ARCHITECTURE_AND_SETUP.md` - System architecture
   - `ANDROID_AGENT_USAGE.md` - Usage guide
   - `localAgentClient/README.md` - Local agent setup

3. **Common fixes:**
   - Restart the system
   - Check ANDROID_HOME
   - Verify project path
   - Ensure server is running
   - Check firewall settings

## Prevention

To avoid issues:

1. **Always start system before using AI:**
   ```bash
   ./start-android-system.sh
   ```

2. **Check status periodically:**
   ```bash
   ./check-android-system.sh
   ```

3. **Keep logs clean:**
   ```bash
   # Rotate logs weekly
   mv logs/server.log logs/server.log.old
   mv logs/local-agent.log logs/local-agent.log.old
   ```

4. **Monitor resources:**
   ```bash
   # Check if processes are running
   ps aux | grep -E "localAgentClient|gradlew"
   ```

