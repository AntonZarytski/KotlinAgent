package com.claude.agent.ui

import org.jetbrains.compose.web.renderComposable

fun main() {
    console.log("Main.kt: starting application")
    try {
        renderComposable(rootElementId = "root") {
            console.log("renderComposable: rendering com.claude.agent.ui.ClaudeChatApp")
            ClaudeChatApp()
        }
        console.log("Main.kt: renderComposable completed")
    } catch (e: Exception) {
        console.error("Main.kt: error during rendering", e)
    }
}
