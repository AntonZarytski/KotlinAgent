package com.claude.agent.ui

import androidx.compose.runtime.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement

@Composable
fun FileTreeItem(
    node: FileTreeNode,
    level: Int,
    selectedFiles: Set<String>,
    onFileToggle: (String) -> Unit,
    expandedDirs: Set<String>,
    onDirToggle: (String) -> Unit,
    onProjectPathChange: ((String) -> Unit)? = null
) {
    val isDirectory = node.type == "directory"
    val isExpanded = expandedDirs.contains(node.path)
    val isSelected = selectedFiles.contains(node.path)
    val indent = level * 16

    // Item row
    Div({
        style {
            property("padding", "4px 8px 4px ${indent + 8}px")
            property("cursor", if (isDirectory) "pointer" else "default")
            property("display", "flex")
            property("align-items", "center")
            property("font-size", "13px")
            property("user-select", "none")
            if (isSelected) {
                property("background", "#dbeafe")
            }
        }
        if (isDirectory) {
            onClick { onDirToggle(node.path) }
            // Double-click on ANY folder to set project path
            if (onProjectPathChange != null) {
                onDoubleClick {
                    console.log("📁 Double-clicked on folder: ${node.path}")
                    onProjectPathChange(node.path)
                }
            }
        }
        onMouseEnter {
            it.currentTarget.asDynamic().style.background = if (isSelected) "#bfdbfe" else "#f3f4f6"
        }
        onMouseLeave {
            it.currentTarget.asDynamic().style.background = if (isSelected) "#dbeafe" else "transparent"
        }
    }) {
        // Checkbox для всех элементов (файлов и папок)
        CheckboxInput(isSelected) {
            onInput { event ->
                event.stopPropagation()
                onFileToggle(node.path)
            }
            style {
                property("margin-right", "6px")
            }
        }

        // Directory arrow (только для папок)
        if (isDirectory) {
            Span({
                style {
                    property("margin-right", "6px")
                    property("font-size", "10px")
                    property("transition", "transform 0.2s")
                    if (isExpanded) {
                        property("transform", "rotate(90deg)")
                    }
                }
            }) {
                Text("▶")
            }
        }

        // Icon
        Span({
            style {
                property("margin-right", "6px")
            }
        }) {
            Text(if (isDirectory) "📁" else getFileIcon(node.name))
        }

        // Name
        Span({
            style {
                property("flex", "1")
                property("overflow", "hidden")
                property("text-overflow", "ellipsis")
                property("white-space", "nowrap")
            }
        }) {
            Text(node.name)
        }
    }

    // Children (if directory is expanded)
    if (isDirectory && isExpanded && node.children != null) {
        node.children.forEach { child ->
            FileTreeItem(
                node = child,
                level = level + 1,
                selectedFiles = selectedFiles,
                onFileToggle = onFileToggle,
                expandedDirs = expandedDirs,
                onDirToggle = onDirToggle,
                onProjectPathChange = onProjectPathChange
            )
        }
    }
}

private fun getFileIcon(fileName: String): String {
    return when {
        fileName.endsWith(".kt") -> "🟣"
        fileName.endsWith(".java") -> "☕"
        fileName.endsWith(".xml") -> "📄"
        fileName.endsWith(".json") -> "📋"
        fileName.endsWith(".gradle") || fileName.endsWith(".kts") -> "🔧"
        fileName.endsWith(".md") -> "📝"
        fileName.endsWith(".png") || fileName.endsWith(".jpg") -> "🖼️"
        else -> "📄"
    }
}
