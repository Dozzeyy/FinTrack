/*
 * FinTrack
 * Copyright (C) 2026 Bhuvan (app.upstream242@passmail.com)
 * SPDX-License-Identifier: GPL-3.0-or-later

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
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
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logsExportedMsg = stringResource(R.string.msg_logs_exported)
    val logExportFailedMsg = stringResource(R.string.msg_log_export_failed)

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val success = exportLogs(context, it)
                    withContext(Dispatchers.Main) {
                        if (success) Toast.makeText(context, logsExportedMsg, Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, logExportFailedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_contact_us)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
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
                stringResource(R.string.label_developer_contact),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(24.dp))
            
            ContactRow(
                icon = Icons.Default.Email,
                label = stringResource(R.string.label_email),
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
                label = stringResource(R.string.label_github_issues),
                value = "https://github.com/Dozzeyy/FinTrack/issues",
                isLink = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Dozzeyy/FinTrack/issues"))
                    context.startActivity(intent)
                }
            )
            
            Spacer(Modifier.height(16.dp))

            ContactRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.label_website),
                value = "vahak.org",
                isLink = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.vahak.org/documentation"))
                    context.startActivity(intent)
                }
            )

            Spacer(Modifier.height(16.dp))

            ContactRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.label_youtube),
                value = "@StoptheStalk",
                isLink = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@stopthestalk"))
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
                        stringResource(R.string.msg_facing_issues),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.msg_send_logs_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = { logExportLauncher.launch("fintrack_logs_${System.currentTimeMillis()}.txt") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_export_logs))
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Copyright (C) 2026 Vahak Apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    "Licensed under GPL-3.0-or-later",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    "Version 1.0.22",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

        val process = Runtime.getRuntime().exec("logcat -d -t 1000")
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
