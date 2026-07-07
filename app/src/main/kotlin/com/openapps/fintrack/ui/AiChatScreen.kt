/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.ModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val modelState by viewModel.aiManager.modelState.collectAsState()
    val downloadProgress by viewModel.aiManager.downloadProgress.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.aiMessages.size, viewModel.aiCurrentResponse) {
        if (viewModel.aiMessages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.aiMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FinTrack AI Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (modelState) {
                ModelState.NOT_DOWNLOADED -> {
                    DownloadModelView(onDownload = { viewModel.aiManager.startDownload() })
                }
                ModelState.DOWNLOADING -> {
                    LoadingView("Downloading Model...", downloadProgress.toFloat() / 100f)
                }
                ModelState.INITIALIZING -> {
                    LoadingView("Initializing AI...", null)
                }
                ModelState.READY, ModelState.ERROR -> {
                    ChatView(
                        messages = viewModel.aiMessages,
                        currentResponse = viewModel.aiCurrentResponse,
                        isLoading = viewModel.aiIsLoading,
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        onSend = {
                            viewModel.sendMessageToAi(inputText)
                            inputText = ""
                        },
                        listState = listState
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadModelView(onDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("AI Features Ready", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "To use FinTrack AI, you need to download the model (~1.6 GB). This model runs entirely on your device for maximum privacy.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("Download Model")
        }
    }
}

@Composable
fun LoadingView(label: String, progress: Float?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (progress != null) {
            CircularProgressIndicator(progress = progress)
            Spacer(Modifier.height(16.dp))
            Text("$label ${(progress * 100).toInt()}%")
        } else {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(label)
        }
    }
}

@Composable
fun ChatView(
    messages: List<ChatMessage>,
    currentResponse: String,
    isLoading: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg.message, msg.isUser)
            }
            if (currentResponse.isNotEmpty()) {
                item {
                    ChatBubble(currentResponse, false)
                }
            }
            if (isLoading && currentResponse.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Ask about your finances...") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 0.dp,
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
        }
    }
}
