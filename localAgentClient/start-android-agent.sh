#!/bin/bash

# Android Agent Startup Script
# This script helps you start the Local Android Studio Agent with proper configuration

set -e

echo "🚀 Android Agent Startup Script"
echo "================================"
echo ""

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  WARNING: ANDROID_HOME is not set!"
    echo "   Please set it to your Android SDK location:"
    echo "   export ANDROID_HOME=/path/to/android/sdk"
    echo ""
    
    # Try to find Android SDK in common locations
    COMMON_LOCATIONS=(
        "$HOME/Android/Sdk"
        "$HOME/Library/Android/sdk"
        "/usr/local/android-sdk"
    )
    
    for location in "${COMMON_LOCATIONS[@]}"; do
        if [ -d "$location" ]; then
            echo "   Found Android SDK at: $location"
            echo "   You can set it with: export ANDROID_HOME=$location"
            break
        fi
    done
    echo ""
fi

# Parse arguments
VPS_URL="${1:-ws://127.0.0.1:8443}"
ANDROID_PROJECT_PATH="${2:-}"

echo "Configuration:"
echo "  VPS URL: $VPS_URL"

if [ -n "$ANDROID_PROJECT_PATH" ]; then
    echo "  Android Project: $ANDROID_PROJECT_PATH"
    
    # Validate project path
    if [ ! -d "$ANDROID_PROJECT_PATH" ]; then
        echo ""
        echo "❌ ERROR: Android project directory not found!"
        echo "   Path: $ANDROID_PROJECT_PATH"
        exit 1
    fi
    
    # Check for gradlew
    if [ ! -f "$ANDROID_PROJECT_PATH/gradlew" ]; then
        echo ""
        echo "⚠️  WARNING: gradlew not found in project directory"
        echo "   Gradle build features will not work"
    fi
else
    echo "  Android Project: Not specified (limited features)"
    echo ""
    echo "  💡 Tip: Specify project path for full features:"
    echo "     $0 $VPS_URL /path/to/your/android/project"
fi

echo ""

# Build the agent if needed
if [ ! -f "build/libs/localAgentClient.jar" ]; then
    echo "📦 Building agent..."
    ./gradlew build
    echo ""
fi

# Start the agent
echo "🔄 Starting Android Agent..."
echo ""

if [ -n "$ANDROID_PROJECT_PATH" ]; then
    java -jar build/libs/localAgentClient.jar "$VPS_URL" "$ANDROID_PROJECT_PATH"
else
    java -jar build/libs/localAgentClient.jar "$VPS_URL"
fi

