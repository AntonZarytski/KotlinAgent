package com.claude.agent.client

import com.claude.agent.common.AGENT_ID
import kotlinx.coroutines.runBlocking

/**
 * Main entry point for the local Android Studio agent.
 * This agent connects to the VPS server and executes Android emulator commands.
 *
 * Before running:
 * 1. Make sure Android SDK is installed and ANDROID_HOME is set
 * 2. Update vpsUrl to point to your VPS server
 * 3. Ensure the VPS server is running and accessible
 */
fun main() = runBlocking {
    // TODO: Replace with your actual VPS URL
    // Use wss://95.217.187.167:8443 for secure WebSocket connection(when server deployed on VPS)
    // Or ws://95.217.187.167:8001 for non-secure WebSocket connection(when server deployed on VPS)
    // Or ws://127.0.0.1:8001 for debug(when server run locally)
    // Or wss://127.0.0.1:8001 for debug with https(when server run locally)
    val vpsUrl = "ws://127.0.0.1:8001"

    val agent = LocalAndroidStudioAgent(
        vpsUrl = vpsUrl,
        agentId = AGENT_ID
    )

    println("🚀 Starting Android Studio Local Agent...")
    println("📍 Agent ID: $AGENT_ID")
    println("🔗 Connecting to VPS at $vpsUrl...")
    println("⚙️  Make sure ANDROID_HOME is set: ${System.getenv("ANDROID_HOME") ?: "NOT SET"}")

    agent.start()
}