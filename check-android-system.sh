#!/bin/bash

# Check Android Agent System Status

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC}  $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC}  $1"
}

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         Android Agent System - Status Check                   ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Check Remote Server
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Remote Server (Port 8443)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if lsof -Pi :8443 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    SERVER_PID=$(lsof -ti:8443)
    print_status "Running (PID: $SERVER_PID)"
else
    print_error "NOT running"
fi

# Check Local Agent
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Local Agent"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

LOCAL_AGENT_PID=$(pgrep -f "localAgentClient.jar")
if [ -n "$LOCAL_AGENT_PID" ]; then
    print_status "Running (PID: $LOCAL_AGENT_PID)"
else
    print_error "NOT running"
fi

# Check Android Project
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Android Project"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ANDROID_PROJECT="/Users/anton/StudioProjects/RoundTimer"
if [ -d "$ANDROID_PROJECT" ]; then
    print_status "Found: $ANDROID_PROJECT"
    
    if [ -d "$ANDROID_PROJECT/screenshots" ]; then
        SCREENSHOT_COUNT=$(ls -1 "$ANDROID_PROJECT/screenshots" 2>/dev/null | wc -l)
        print_info "Screenshots directory exists ($SCREENSHOT_COUNT files)"
    else
        print_warning "Screenshots directory not created yet"
    fi
    
    if [ -d "$ANDROID_PROJECT/logs" ]; then
        LOG_COUNT=$(ls -1 "$ANDROID_PROJECT/logs" 2>/dev/null | wc -l)
        print_info "Logs directory exists ($LOG_COUNT files)"
    else
        print_warning "Logs directory not created yet"
    fi
else
    print_error "NOT found: $ANDROID_PROJECT"
fi

# Check ANDROID_HOME
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Android SDK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ -n "$ANDROID_HOME" ]; then
    print_status "ANDROID_HOME: $ANDROID_HOME"
    
    if [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
        print_status "ADB found"
    else
        print_warning "ADB not found"
    fi
    
    if [ -f "$ANDROID_HOME/emulator/emulator" ]; then
        print_status "Emulator found"
    else
        print_warning "Emulator not found"
    fi
else
    print_error "ANDROID_HOME not set"
fi

# Check Logs
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Recent Logs"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ -f "logs/server.log" ]; then
    print_info "Server log (last 5 lines):"
    tail -n 5 logs/server.log | sed 's/^/  /'
else
    print_warning "No server log found"
fi

echo ""

if [ -f "logs/local-agent.log" ]; then
    print_info "Local agent log (last 5 lines):"
    tail -n 5 logs/local-agent.log | sed 's/^/  /'
else
    print_warning "No local agent log found"
fi

# Summary
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                         Summary                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

ALL_OK=true

if ! lsof -Pi :8443 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    print_error "Remote server is not running"
    print_info "Start it with: ./start-android-system.sh"
    ALL_OK=false
fi

if [ -z "$LOCAL_AGENT_PID" ]; then
    print_error "Local agent is not running"
    print_info "Start it with: ./start-android-system.sh"
    ALL_OK=false
fi

if [ "$ALL_OK" = true ]; then
    echo ""
    print_status "All systems operational!"
    print_info "You can now ask the AI to work with your Android project."
    echo ""
fi

