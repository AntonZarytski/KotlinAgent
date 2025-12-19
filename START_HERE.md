# 🚀 START HERE - Android Agent Setup

## Welcome!

This guide will get your Android Agent system up and running in **5 minutes**.

## ⚡ Super Quick Start

If you just want to get started immediately:

```bash
cd /Users/anton/IdeaProjects/KotlinAgent
./start-android-system.sh
```

Wait for it to finish, then ask AI:
```
"Show me my AndroidManifest.xml"
```

If that works, you're done! 🎉

If not, continue reading...

## 📋 Step-by-Step Setup

### Step 1: Set ANDROID_HOME (If Not Already Set)

```bash
# Check if it's set
echo $ANDROID_HOME

# If empty, set it (macOS):
export ANDROID_HOME=~/Library/Android/sdk

# Make it permanent:
echo 'export ANDROID_HOME=~/Library/Android/sdk' >> ~/.zshrc
source ~/.zshrc
```

### Step 2: Navigate to Project

```bash
cd /Users/anton/IdeaProjects/KotlinAgent
```

### Step 3: Start the System

```bash
./start-android-system.sh
```

**What this does:**
- ✅ Checks your Android project exists
- ✅ Verifies Android SDK
- ✅ Builds the local agent
- ✅ Starts the remote server
- ✅ Starts the local agent
- ✅ Connects everything together

**Expected output:**
```
╔════════════════════════════════════════════════════════════════╗
║                    System Status                               ║
╚════════════════════════════════════════════════════════════════╝

✅ Remote Server: Running on port 8443
✅ Local Agent: Connected to ws://127.0.0.1:8443
✅ Android Project: /Users/anton/StudioProjects/RoundTimer

ℹ️  Logs:
  - Server: logs/server.log
  - Local Agent: logs/local-agent.log

✅ System is ready! You can now ask the AI to work with your Android project.
```

### Step 4: Verify It's Working

```bash
./check-android-system.sh
```

**Expected output:**
```
✅ Remote Server: Running
✅ Local Agent: Running
✅ Android Project: Found
✅ Android SDK: Configured
```

### Step 5: Test with AI

Ask Claude:

**Test 1: Read a file**
```
"Show me my AndroidManifest.xml"
```

**Test 2: List directory**
```
"List files in the app/src/main directory"
```

**Test 3: Check emulators**
```
"List available Android emulators"
```

If all three work, you're all set! 🎉

## 🐛 If Something Goes Wrong

### Problem: ANDROID_HOME not found

```bash
# Find your Android SDK
ls -la ~/Library/Android/sdk  # macOS
ls -la ~/Android/Sdk          # Linux

# Set it
export ANDROID_HOME=~/Library/Android/sdk

# Make permanent
echo 'export ANDROID_HOME=~/Library/Android/sdk' >> ~/.zshrc
```

### Problem: Server won't start

```bash
# Check if port is in use
lsof -i :8443

# Kill existing process
kill $(lsof -ti:8443)

# Try again
./start-android-system.sh
```

### Problem: Local agent won't connect

```bash
# Check server is running
lsof -i :8443

# Check logs
tail -f logs/local-agent.log

# Restart everything
./stop-android-system.sh
./start-android-system.sh
```

### Problem: AI says "project not configured"

```bash
# Check if local agent is running
./check-android-system.sh

# If not running, start it
./start-android-system.sh

# Try asking AI again
```

## 📚 What to Read Next

### If you want to understand how it works:
→ Read `SOLUTION_SUMMARY.md`

### If you want detailed architecture:
→ Read `ARCHITECTURE_AND_SETUP.md`

### If you encounter problems:
→ Read `TROUBLESHOOTING.md`

### If you want to see all features:
→ Read `ANDROID_AGENT_USAGE.md`

### If you want quick reference:
→ Read `ANDROID_AGENT_QUICK_REFERENCE.md`

## 🎯 What You Can Do Now

### Build & Deploy
```
"Build my app in debug mode"
"Build and install my app"
"Build a release version"
```

### Screenshots
```
"Take a screenshot"
"Take a screenshot of the current screen"
```

### Logs
```
"Show me the recent logs"
"Show me error logs from my app"
"Save the current logs to a file"
```

### Code Browsing
```
"Show me my AndroidManifest.xml"
"List files in app/src/main"
"Show me MainActivity.kt"
"What's in my build.gradle?"
```

### Emulator Control
```
"List available emulators"
"Start the Pixel 5 emulator"
"Stop the emulator"
```

## 🔄 Daily Workflow

```bash
# When you start working:
cd /Users/anton/IdeaProjects/KotlinAgent
./start-android-system.sh

# Work with AI all day...

# When you're done:
./stop-android-system.sh
```

## ✅ Success Checklist

- [ ] ANDROID_HOME is set
- [ ] Ran `./start-android-system.sh`
- [ ] Saw "System is ready!" message
- [ ] Ran `./check-android-system.sh`
- [ ] All components show ✅
- [ ] Asked AI to show AndroidManifest.xml
- [ ] AI successfully displayed the file

If all checked, you're ready to go! 🚀

## 🆘 Need Help?

1. **Check status:** `./check-android-system.sh`
2. **Check logs:** `tail -f logs/local-agent.log`
3. **Restart system:** `./stop-android-system.sh && ./start-android-system.sh`
4. **Read troubleshooting:** `TROUBLESHOOTING.md`

## 🎉 You're All Set!

The Android Agent system is now configured and ready to use.

**Remember:** Always start the system before asking AI to work with your Android project!

```bash
./start-android-system.sh
```

Happy coding! 🚀

