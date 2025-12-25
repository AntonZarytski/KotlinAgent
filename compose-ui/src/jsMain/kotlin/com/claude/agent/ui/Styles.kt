package com.claude.agent.ui

import org.jetbrains.compose.web.css.*

object AppStyles : StyleSheet() {
    init {
        // Root CSS variables
        "root" style {
            property("--vvh", "100dvh")
            property("--kbd", "0px")
        }

        // Global reset
        universal style {
            margin(0.px)
            padding(0.px)
            property("box-sizing", "border-box")
        }

        // Body styles
        "body" style {
            fontFamily("Inter", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Arial", "sans-serif")
            property("background", "linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #7e22ce 100%)")
            property("background-attachment", "fixed")
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
            height(100.vh)
            width(100.vw)
            property("position", "fixed")
            property("overflow", "hidden")
        }

        // Animated background effects
        "body::before" style {
            property("content", "''")
            position(Position.Absolute)
            width(500.px)
            height(500.px)
            property("background", "radial-gradient(circle, rgba(139, 92, 246, 0.3) 0%, transparent 70%)")
            borderRadius(50.percent)
            top((-250).px)
            right((-250).px)
            property("animation", "float 20s infinite ease-in-out")
        }

        "body::after" style {
            property("content", "''")
            position(Position.Absolute)
            width(400.px)
            height(400.px)
            property("background", "radial-gradient(circle, rgba(59, 130, 246, 0.3) 0%, transparent 70%)")
            borderRadius(50.percent)
            bottom((-200).px)
            left((-200).px)
            property("animation", "float 15s infinite ease-in-out reverse")
        }

        // Keyframes
        "@keyframes float" {
            "0%, 100%" {
                property("transform", "translate(0, 0) scale(1)")
            }
            "50%" {
                property("transform", "translate(50px, 50px) scale(1.1)")
            }
        }

        "@keyframes slideUp" {
            "from" {
                opacity(0)
                property("transform", "translateY(30px)")
            }
            "to" {
                opacity(1)
                property("transform", "translateY(0)")
            }
        }

        "@keyframes shimmer" {
            "0%" {
                left((-100).percent)
            }
            "100%" {
                left(100.percent)
            }
        }

        "@keyframes bounce" {
            "0%, 100%" {
                property("transform", "translateY(0)")
            }
            "50%" {
                property("transform", "translateY(-5px)")
            }
        }

        "@keyframes pulse" {
            "0%, 100%" {
                opacity(1)
            }
            "50%" {
                opacity(0.5)
            }
        }

        "@keyframes messageSlide" {
            "from" {
                opacity(0)
                property("transform", "translateY(20px) scale(0.95)")
            }
            "to" {
                opacity(1)
                property("transform", "translateY(0) scale(1)")
            }
        }

        "@keyframes loadingDots" {
            "0%, 80%, 100%" {
                property("transform", "scale(0)")
                opacity(0.5)
            }
            "40%" {
                property("transform", "scale(1)")
                opacity(1)
            }
        }

        "@keyframes typing" {
            "0%, 60%, 100%" {
                property("transform", "translateY(0)")
                opacity(0.4)
            }
            "30%" {
                property("transform", "translateY(-10px)")
                opacity(1)
            }
        }

        "@keyframes streamingPulse" {
            "0%, 100%" {
                opacity(1)
            }
            "50%" {
                opacity(0.85)
            }
        }
    }

    val container by style {
        property("background", "rgba(255, 255, 255, 0.95)")
        property("backdrop-filter", "blur(20px)")
        borderRadius(0.px)
        property("box-shadow", "none")
        width(100.vw)
        maxWidth(100.vw)
        property("height", "var(--vvh)")
        property("max-height", "100dvh")
        property("overflow", "hidden")
        position(Position.Relative)
        property("z-index", "1")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Row)
        property("transform", "translateZ(0)")
    }

    val header by style {
        property("z-index", "5")
        position(Position.Relative)
        backgroundColor(Color.white)
        padding(20.px, 24.px)
        property("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.08)")
        property("flex-shrink", "0")
    }

    val headerButtons by style {
        position(Position.Absolute)
        top(50.percent)
        property("transform", "translateY(-50%)")
        display(DisplayStyle.Flex)
        property("gap", 12.px)
        alignItems(AlignItems.Center)
        property("z-index", "10")
    }

    val headerButtonsLeft by style {
        left(20.px)
        property("right", "auto")
    }

    val headerButtonsRight by style {
        right(20.px)
        property("left", "auto")
    }

    val iconButton by style {
        width(40.px)
        height(40.px)
        padding(0.px)
        property("cursor", "pointer")
        property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)")
        border(0.px)
        fontSize(20.px)
        borderRadius(12.px)
        color(Color.white)
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        property("transition", "all 0.3s ease")
        property("box-shadow", "0 4px 12px rgba(99, 102, 241, 0.3)")
    }

    val headerTitle by style {
        fontSize(28.px)
        fontWeight(700)
        property("letter-spacing", "-0.5px")
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        property("gap", 12.px)
        position(Position.Relative)
        property("z-index", "1")
    }

    val statusIndicator by style {
        display(DisplayStyle.InlineBlock)
        width(8.px)
        height(8.px)
        property("background", "#10b981")
        borderRadius(50.percent)
        marginLeft(8.px)
        property("box-shadow", "0 0 10px #10b981")
        property("animation", "pulse 2s infinite")
    }

    val chatArea by style {
        property("flex", "1")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("min-width", "0")
        property("min-height", "0")
        property("overflow", "hidden")
        position(Position.Relative)
    }

    val chat by style {
        property("flex", "1")
        property("overflow-y", "auto")
        padding(24.px)
        property("background", "linear-gradient(to bottom, #f9fafb 0%, #f3f4f6 100%)")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", 16.px)
        property("min-height", "0")
        property("-webkit-overflow-scrolling", "touch")
        property("overscroll-behavior", "contain")
    }

    val messageWrapper by style {
        display(DisplayStyle.Flex)
        property("gap", 12.px)
        property("animation", "messageSlide 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)")
    }

    val messageWrapperUser by style {
        property("flex-direction", "row-reverse")
    }

    val avatar by style {
        width(40.px)
        height(40.px)
        borderRadius(50.percent)
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        fontSize(20.px)
        property("flex-shrink", "0")
        property("box-shadow", "0 4px 12px rgba(0, 0, 0, 0.1)")
    }

    val avatarUser by style {
        property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)")
    }

    val avatarAssistant by style {
        property("background", "linear-gradient(135deg, #10b981 0%, #059669 100%)")
    }

    val messageContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", 4.px)
        property("flex", "1")
    }

    val message by style {
        padding(14.px, 18.px)
        borderRadius(18.px)
        maxWidth(70.percent)
        property("word-wrap", "break-word")
        property("line-height", "1.5")
        fontSize(15.px)
        position(Position.Relative)
    }

    val messageUser by style {
        property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)")
        color(Color.white)
        property("box-shadow", "0 4px 12px rgba(99, 102, 241, 0.3)")
        property("white-space", "pre-wrap")
    }

    val messageAssistant by style {
        backgroundColor(Color.white)
        color(rgb(31, 41, 55))
        property("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.08)")
        property("border", "1px solid rgba(0, 0, 0, 0.05)")
    }

    val inputGroup by style {
        property("gap", 8.px)
        padding(12.px, 16.px)
        position(Position.Fixed)
        bottom(0.px)
        left(0.px)
        right(0.px)
        property("background", "rgba(255,255,255,0.98)")
        property("z-index", "1200")
        property("box-shadow", "0 -8px 24px rgba(0,0,0,0.08)")
        property("transform", "translateZ(0)")
        property("backface-visibility", "hidden")
    }

    val inputRow by style {
        display(DisplayStyle.Flex)
        property("gap", 12.px)
        property("flex", "1")
        alignItems(AlignItems.FlexEnd)
    }

    val textarea by style {
        property("flex", "1")
        padding(16.px, 20.px)
        property("border", "2px solid #e5e7eb")
        borderRadius(20.px)
        fontSize(15.px)
        property("outline", "none")
        property("transition", "all 0.3s ease")
        property("background", "#f9fafb")
        property("resize", "none")
        property("min-height", "52px")
        property("max-height", "150px")
        property("line-height", "1.4")
        property("font-family", "inherit")
        property("overflow-y", "auto")
        position(Position.Relative)
        property("z-index", "5")
        property("-webkit-appearance", "none")
        property("appearance", "none")
    }

    val button by style {
        padding(16.px, 32.px)
        property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)")
        color(Color.white)
        border(0.px)
        borderRadius(28.px)
        property("cursor", "pointer")
        fontSize(15.px)
        fontWeight(600)
        property("transition", "all 0.3s ease")
        property("box-shadow", "0 4px 12px rgba(99, 102, 241, 0.3)")
        position(Position.Relative)
        property("overflow", "hidden")
        property("-webkit-tap-highlight-color", "transparent")
    }

    val loadingIndicator by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        property("gap", 12.px)
        property("animation", "messageSlide 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)")
    }

    val loadingContent by style {
        backgroundColor(Color.white)
        padding(14.px, 18.px)
        borderRadius(18.px)
        property("box-shadow", "0 2px 8px rgba(0, 0, 0, 0.08)")
        property("border", "1px solid rgba(0, 0, 0, 0.05)")
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        property("gap", 12.px)
    }

    val typingDots by style {
        display(DisplayStyle.Flex)
        property("gap", 4.px)
        alignItems(AlignItems.Center)
    }

    val typingDot by style {
        width(8.px)
        height(8.px)
        borderRadius(50.percent)
        property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)")
        property("animation", "typing 1.4s infinite ease-in-out")
    }

    val panel by style {
        width(0.px)
        property("overflow", "hidden")
        backgroundColor(Color.white)
        property("box-shadow", "-5px 0 20px rgba(0, 0, 0, 0.1)")
        property("transition", "width 0.3s ease")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("border-left", "1px solid rgba(0, 0, 0, 0.08)")
        property("flex-shrink", "0")
    }

    val panelActive by style {
        width(320.px)
    }

    val historyPanel by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        property("right", "auto")
        width(0.px)
        height(100.vh)
        property("overflow", "hidden")
        backgroundColor(Color.white)
        property("box-shadow", "5px 0 20px rgba(0, 0, 0, 0.1)")
        property("transition", "width 0.3s ease")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("border-right", "1px solid rgba(0, 0, 0, 0.08)")
        property("flex-shrink", "0")
        property("z-index", "9999")
    }

    val reminderPanel by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        property("right", "auto")
        width(0.px)
        height(100.vh)
        property("overflow", "hidden")
        backgroundColor(Color.white)
        property("box-shadow", "5px 0 20px rgba(0, 0, 0, 0.1)")
        property("transition", "width 0.3s ease")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("border-right", "1px solid rgba(0, 0, 0, 0.08)")
        property("flex-shrink", "0")
        property("z-index", "9998")
    }

    val panelHeader by style {
        property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #d946ef 100%)")
        color(Color.white)
        padding(20.px, 24.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("min-width", "320px")
    }

    val panelContent by style {
        property("flex", "1")
        padding(24.px)
        property("padding-bottom", "100px")
        property("overflow-y", "auto")
        property("min-width", "320px")
    }

    val closeBtn by style {
        property("background", "transparent")
        border(0.px)
        color(Color.white)
        fontSize(28.px)
        property("cursor", "pointer")
        padding(4.px, 8.px)
        borderRadius(8.px)
        property("transition", "background 0.2s")
        property("box-shadow", "none")
    }

    val modalOverlay by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.percent)
        property("background", "rgba(0, 0, 0, 0.5)")
        property("backdrop-filter", "blur(4px)")
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("z-index", "1000")
        opacity(0)
        property("visibility", "hidden")
        property("transition", "opacity 0.3s ease, visibility 0.3s ease")
    }

    val modalOverlayActive by style {
        opacity(1)
        property("visibility", "visible")
    }

    val modalContent by style {
        backgroundColor(Color.white)
        borderRadius(20.px)
        padding(0.px)
        maxWidth(400.px)
        width(90.percent)
        property("box-shadow", "0 20px 60px rgba(0, 0, 0, 0.3)")
        property("transform", "scale(0.9) translateY(20px)")
        property("transition", "transform 0.3s ease")
        property("overflow", "hidden")
    }
}
