#!/bin/bash

# Complete Android Agent System Startup Script
# This script starts both the remote server and local agent

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         Android Agent System - Complete Startup               ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Configuration
ANDROID_PROJECT_PATH="/Users/anton/StudioProjects/RoundTimer"
VPS_URL="ws://127.0.0.1:8443"
SERVER_PORT=8443

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
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

# Step 1: Verify Android Project
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 1: Verifying Android Project"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ -d "$ANDROID_PROJECT_PATH" ]; then
    print_status "Android project found: $ANDROID_PROJECT_PATH"
    
    if [ -f "$ANDROID_PROJECT_PATH/gradlew" ]; then
        print_status "Gradle wrapper found"
    else
        print_warning "Gradle wrapper not found - build features may not work"
    fi
else
    print_error "Android project NOT found: $ANDROID_PROJECT_PATH"
    echo ""
    echo "Please update ANDROID_PROJECT_PATH in this script to point to your Android project."
    exit 1
fi

# Step 2: Check ANDROID_HOME
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 2: Checking Android SDK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ -n "$ANDROID_HOME" ]; then
    print_status "ANDROID_HOME is set: $ANDROID_HOME"
else
    print_warning "ANDROID_HOME is not set"
    
    # Try to find it
    COMMON_LOCATIONS=(
        "$HOME/Library/Android/sdk"
        "$HOME/Android/Sdk"
        "/usr/local/android-sdk"
    )
    
    for location in "${COMMON_LOCATIONS[@]}"; do
        if [ -d "$location" ]; then
            print_info "Found Android SDK at: $location"
            print_info "You can set it with: export ANDROID_HOME=$location"
            break
        fi
    done
fi

# Step 3: Build Local Agent
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 3: Building Local Agent"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ ! -f "localAgentClient/build/libs/localAgentClient.jar" ]; then
    print_info "Building local agent..."
    cd localAgentClient
    ./gradlew build
    cd ..
    print_status "Local agent built successfully"
else
    print_status "Local agent already built"
fi

# Step 4: Check if server is already running
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 4: Checking Remote Server"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if lsof -Pi :$SERVER_PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    print_status "Remote server is already running on port $SERVER_PORT"
    SERVER_RUNNING=true
else
    print_warning "Remote server is NOT running"
    SERVER_RUNNING=false
fi

# Step 5: Start components
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 5: Starting Components"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Create log directory
mkdir -p logs

if [ "$SERVER_RUNNING" = false ]; then
    print_info "Starting remote server..."
    cd remoteAgentServer
    nohup ./gradlew run > ../logs/server.log 2>&1 &
    SERVER_PID=$!
    cd ..
    
    # Wait for server to start
    print_info "Waiting for server to start..."
    sleep 5
    
    if lsof -Pi :$SERVER_PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        print_status "Remote server started (PID: $SERVER_PID)"
        echo "$SERVER_PID" > logs/server.pid
    else
        print_error "Failed to start remote server"
        print_info "Check logs/server.log for details"
        exit 1
    fi
else
    print_info "Using existing remote server"
fi

# Start local agent
print_info "Starting local agent..."
cd localAgentClient

nohup java -jar build/libs/localAgentClient.jar \
    "$VPS_URL" \
    "$ANDROID_PROJECT_PATH" \
    > ../logs/local-agent.log 2>&1 &
    
LOCAL_AGENT_PID=$!
cd ..

# Wait for connection
print_info "Waiting for local agent to connect..."
sleep 3

if ps -p $LOCAL_AGENT_PID > /dev/null 2>&1; then
    print_status "Local agent started (PID: $LOCAL_AGENT_PID)"
    echo "$LOCAL_AGENT_PID" > logs/local-agent.pid
else
    print_error "Failed to start local agent"
    print_info "Check logs/local-agent.log for details"
    exit 1
fi

# Final status
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    System Status                               ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
print_status "Remote Server: Running on port $SERVER_PORT"
print_status "Local Agent: Connected to $VPS_URL"
print_status "Android Project: $ANDROID_PROJECT_PATH"
echo ""
print_info "Logs:"
echo "  - Server: logs/server.log"
echo "  - Local Agent: logs/local-agent.log"
echo ""
print_info "To stop the system, run: ./stop-android-system.sh"
echo ""
print_status "System is ready! You can now ask the AI to work with your Android project."
echo ""

