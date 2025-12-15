package com.claude.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch

// Main Application
fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude AI Chat",
        state = windowState
    ) {
        ClaudeChatApp()
    }
}

@Composable
@Preview
fun ClaudeChatApp() {
    val apiClient = remember { ClaudeApiClient() }
    val viewModel = remember { ChatViewModel(apiClient) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadTools()
    }

    MaterialTheme{
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E3C72),
                            Color(0xFF2A5298),
                            Color(0xFF7E22CE)
                        )
                    )
                )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xF2FFFFFF),
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // История чатов (слева)
                    AnimatedVisibility(
                        visible = viewModel.showHistoryPanel,
                        enter = slideInHorizontally(initialOffsetX = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it })
                    ) {
                        HistoryPanel(
                            sessions = viewModel.sessions,
                            currentSessionId = viewModel.currentSessionId,
                            onClose = { viewModel.showHistoryPanel = false },
                            onNewChat = {
                                viewModel.startNewChat()
                            },
                            onLoadSession = {
                                scope.launch { viewModel.loadSession(it.id) }
                            },
                            onDeleteSession = {
                                scope.launch { viewModel.deleteSession(it) }
                            },
                            onRefresh = {
                                scope.launch { viewModel.loadSessions() }
                            }
                        )
                    }

                    // Основная область чата
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        ChatHeader(
                            onHistoryClick = {
                                viewModel.toggleHistoryPanel()
                                if (viewModel.showHistoryPanel) {
                                    scope.launch { viewModel.loadSessions() }
                                }
                            },
                            onTokensClick = { },
                            onSettingsClick = {
                                viewModel.toggleSettingsPanel()
                                if (viewModel.showSettingsPanel) {
                                    scope.launch { viewModel.loadTools() }
                                }
                            }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            ChatMessages(
                                messages = viewModel.messages,
                                isLoading = viewModel.isLoading,
                                showTokenCount = viewModel.settings.showTokenCount,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        var inputText by remember { mutableStateOf("") }
                        InputPanel(
                            text = inputText,
                            onTextChange = { inputText = it },
                            onSend = {
                                scope.launch {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            onCheckTokens = {
                                scope.launch {
                                    viewModel.checkTokens(inputText)
                                }
                            },
                            enabled = !viewModel.isLoading
                        )
                    }

                    // Панель настроек (справа)
                    AnimatedVisibility(
                        visible = viewModel.showSettingsPanel,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it })
                    ) {
                        SettingsPanel(
                            settings = viewModel.settings,
                            availableTools = viewModel.availableTools,
                            onClose = { viewModel.showSettingsPanel = false },
                            onSettingsChange = { viewModel.updateSettings(Settings()) },
                            onClearHistory = { viewModel.clearHistory() }
                        )
                    }
                }
            }

            if (viewModel.showTokenModal) {
                TokenModal(
                    tokenCount = viewModel.tokenCount,
                    onDismiss = { viewModel.showTokenModal = false }
                )
            }
        }
    }
}

@Composable
fun TokenModal(tokenCount: Int, onDismiss: () -> Unit) {
    TODO("Not yet implemented")
}

@Composable
fun SettingsPanel(
    settings: Settings,
    availableTools: List<Tool>,
    onClose: () -> Unit,
    onSettingsChange: () -> Unit,
    onClearHistory: () -> Unit
) {
    TODO("Not yet implemented")
}

// UI Components
@Composable
fun ChatHeader(
    onHistoryClick: () -> Unit,
    onTokensClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        ),
                        CircleShape
                    )
                    .shadow(4.dp, CircleShape)
            ) {
                Text("💬", fontSize = 20.sp)
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🤖", fontSize = 32.sp, modifier = Modifier.padding(end = 12.dp))
                Text(
                    text = "Claude AI Chat",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onTokensClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Text("🔢", fontSize = 20.sp)
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Text("⚙️", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun ChatMessages(
    messages: List<Message>,
    isLoading: Boolean,
    showTokenCount: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF9FAFB), Color(0xFFF3F4F6))
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages) { message ->
            MessageItem(message, showTokenCount)
        }

        if (isLoading) {
            item {
                LoadingIndicator()
            }
        }
    }
}

@Composable
fun MessageItem(message: Message, showTokenCount: Boolean) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Avatar(emoji = "🤖", isUser = false)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isUser) Color(0xFF6366F1) else Color.White,
                border = if (!isUser) BorderStroke(1.dp, Color(0x0D000000)) else null
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(14.dp),
                        color = if (isUser) Color.White else Color(0xFF1F2937),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            if (showTokenCount && message.usage != null) {
                val total = message.usage.input_tokens + message.usage.output_tokens
                Text(
                    text = "📊 Потрачено токенов: $total (вход: ${message.usage.input_tokens}, выход: ${message.usage.output_tokens})",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(12.dp))
            Avatar(emoji = "👤", isUser = true)
        }
    }
}

@Composable
fun Avatar(emoji: String, isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape)
            .background(
                if (isUser)
                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                else
                    Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(emoji = "🤖", isUser = false)

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0x0D000000))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Claude думает",
                    fontSize = 15.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
                TypingDots()
            }
        }
    }
}

@Composable
fun TypingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
            val animatedY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "y$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = animatedY.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        ),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
fun InputPanel(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCheckTokens: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFAFFFFFF),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите ваше сообщение...") },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                enabled = enabled,
                minLines = 1,
                maxLines = 5
            )

            IconButton(
                onClick = onCheckTokens,
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .shadow(4.dp, RoundedCornerShape(28.dp)),
                enabled = enabled && text.isNotBlank()
            ) {
                Text("🔢", fontSize = 20.sp)
            }

            Button(
                onClick = onSend,
                modifier = Modifier
                    .height(52.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                ),
                contentPadding = PaddingValues(0.dp),
                enabled = enabled && text.isNotBlank()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            )
                        )
                        .padding(16.dp, 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✨ Отправить",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryPanel(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onLoadSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Surface(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFFE5E7EB))
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp)
                ) {
                    Text("❌", fontSize = 20.sp)
                }
            }
        }
    }
}