/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.EncryptedPrefsHelper

@Composable
fun SetupScreen(viewModel: ExpenseViewModel, onComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val backupPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    
    var selectedPath by remember { mutableStateOf("Not Selected") }
    var isPathSelected by remember { mutableStateOf(false) }
    
    var scheduleBackupEnabled by remember { mutableStateOf(true) }
    var e2eeEnabled by remember { mutableStateOf(false) }
    var showRemoteEncryptPassDialog by remember { mutableStateOf(false) }
    var tempPassword by remember { mutableStateOf("") }

    if (showRemoteEncryptPassDialog) {
        var pass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                showRemoteEncryptPassDialog = false 
                e2eeEnabled = false
            },
            title = { Text("Set Backup Password") },
            text = {
                Column {
                    Text(
                        "We do not store your passwords anywhere. It's your sole responsibility to keep this password in a safe recoverable place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("This password will be used to encrypt your backup files (E2EE).")
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; error = null },
                        label = { Text("Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; error = null },
                        label = { Text("Confirm password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pass == confirmPass) {
                        tempPassword = pass
                        e2eeEnabled = true
                        showRemoteEncryptPassDialog = false
                    } else {
                        error = "Passwords do not match"
                    }
                }, enabled = pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                    Text("Set & Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRemoteEncryptPassDialog = false 
                    e2eeEnabled = false
                }) { Text("Cancel") }
            }
        )
    }

    val pathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                selectedPath = it.toString()
                isPathSelected = true
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    
                    // Save to backup prefs
                    backupPrefs.edit()
                        .putString("path", it.toString())
                        .putBoolean("enabled", scheduleBackupEnabled)
                        .putBoolean("encrypt_scheduled_backup", e2eeEnabled)
                        .apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Welcome to FinTrack",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "This app doesn't use a central server. To keep your data safe, please select a folder where your automatic backups will be saved.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backup Location:", style = MaterialTheme.typography.labelSmall)
                Text(selectedPath, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Schedule Backup", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = scheduleBackupEnabled,
                onCheckedChange = { scheduleBackupEnabled = it }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("E2EE (Encryption)", style = MaterialTheme.typography.bodyLarge)
                Text("Encrypted backup files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = e2eeEnabled,
                onCheckedChange = { 
                    if (it) {
                        showRemoteEncryptPassDialog = true
                    } else {
                        e2eeEnabled = false
                        tempPassword = ""
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { 
                try {
                    pathLauncher.launch(null) 
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isPathSelected) "Change Backup Folder" else "Select Backup Folder")
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = {
                backupPrefs.edit()
                    .putBoolean("enabled", scheduleBackupEnabled)
                    .putBoolean("encrypt_scheduled_backup", e2eeEnabled)
                    .apply()
                
                if (e2eeEnabled && tempPassword.isNotEmpty()) {
                    EncryptedPrefsHelper.putString("remote_master_password", tempPassword)
                    prefs.edit().putBoolean("encrypt_remote_enabled", true).apply()
                    viewModel.updateEncryptRemote(true)
                    viewModel.updateRemoteMasterPassword(tempPassword)
                }

                if (scheduleBackupEnabled && isPathSelected) {
                    viewModel.scheduleBackup(context)
                }

                prefs.edit().putBoolean("setup_complete", true).apply()
                
                // Touch the database once to ensure seeding happens BEFORE user reaches home
                viewModel.triggerRefresh() 

                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isPathSelected,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Get Started")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = {
                prefs.edit().putBoolean("setup_complete", true).apply()
                // Ensure backup is disabled if skipped
                backupPrefs.edit().putBoolean("enabled", false).apply()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip and set up later")
        }
    }
}
