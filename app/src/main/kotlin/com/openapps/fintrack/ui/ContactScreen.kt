/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val success = exportLogs(context, it)
                    withContext(Dispatchers.Main) {
                        if (success) Toast.makeText(context, "Logs exported successfully", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "Log export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Us") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Developer Contact",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(24.dp))
            
            ContactRow(
                icon = Icons.Default.Email,
                label = "Email",
                value = "app.upstream242@passmail.com",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:app.upstream242@passmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, "FinTrack Feedback")
                    }
                    context.startActivity(intent)
                }
            )
            
            Spacer(Modifier.height(16.dp))
            
            ContactRow(
                icon = Icons.Default.Language,
                label = "GitHub",
                value = "https://github.com/Bhuvan",
                isLink = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Bhuvan"))
                    context.startActivity(intent)
                }
            )
            
            Spacer(Modifier.height(48.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "facing any app issues?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Send the log by clicking on the button below to help us debug and fix the problem.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = { logExportLauncher.launch("fintrack_logs_${System.currentTimeMillis()}.txt") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Logs")
            }
        }
    }
}

@Composable
fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = TextStyle(
                    color = if (isLink) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isLink) TextDecoration.Underline else TextDecoration.None,
                    fontSize = 16.sp
                )
            )
        }
    }
}

suspend fun exportLogs(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val logFile = File(context.cacheDir, "temp_logs.txt")
        // Get logs for the last 2 days using logcat
        // On Android, we can filter by time
        val process = Runtime.getRuntime().exec("logcat -d -t 1000") // Simplified: last 1000 lines
        val reader = process.inputStream.bufferedReader()
        val logs = reader.readText()
        
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(logs.toByteArray())
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
