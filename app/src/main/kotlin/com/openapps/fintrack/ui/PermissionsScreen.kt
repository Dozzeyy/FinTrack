/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class PermissionInfo(
    val permission: String,
    val name: String,
    val reason: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val isSetupComplete = prefs.getBoolean("setup_complete", false)
    
    var refreshKey by remember { mutableIntStateOf(0) }

    val permissions = remember(refreshKey) {
        val list = mutableListOf(
            PermissionInfo(Manifest.permission.INTERNET, "Network", "Needed to sync your data to your cloud provider (WebDAV)."),
            PermissionInfo(Manifest.permission.RECEIVE_SMS, "SMS Receive", "Needed to automatically capture transaction details from bank messages."),
            PermissionInfo(Manifest.permission.READ_SMS, "SMS Read", "Used to parse message content for amounts and dates."),
            PermissionInfo(Manifest.permission.CAMERA, "Camera", "Required to scan QR codes for pairing with the web client.")
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "Required to alert you when a transaction is detected."))
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Note for Restricted Settings
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "If 'Allow' is disabled in system settings: Go to App Info -> tap (⋮) in corner -> 'Allow restricted settings'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(permissions) { info ->
                    PermissionItem(info, context) {
                        refreshKey++
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                }
                
                item {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Open System Settings")
                    }
                    
                    if (isSetupComplete) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Skip / Continue to App")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(info: PermissionInfo, context: Context, onResult: () -> Unit) {
    var isGranted by remember(info.permission) { 
        mutableStateOf(
            if (info.permission == "android.permission.MANAGE_EXTERNAL_STORAGE") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            } else {
                ContextCompat.checkSelfPermission(context, info.permission) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isGranted = true
        }
        onResult()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(info.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (isGranted) "Granted" else "Not Granted",
                color = if (isGranted) Color(0xFF4CAF50) else Color.Red,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(info.reason, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        if (!isGranted) {
            Button(onClick = {
                if (info.permission == "android.permission.MANAGE_EXTERNAL_STORAGE" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    launcher.launch(info.permission)
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Enable")
            }
        }
    }
}
