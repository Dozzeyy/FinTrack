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
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.EncryptedPrefsHelper

@Composable
fun SetupScreen(viewModel: ExpenseViewModel, onComplete: () -> Unit) {
    val context = LocalContext.current
    val passwordsNotMatchMsg = stringResource(R.string.msg_passwords_not_match)
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
            title = { Text(stringResource(R.string.title_set_backup_password)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.msg_password_responsibility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(stringResource(R.string.msg_backup_password_desc))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; error = null },
                        label = { Text(stringResource(R.string.label_master_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; error = null },
                        label = { Text(stringResource(R.string.label_confirm_password_plain)) },
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
                        error = passwordsNotMatchMsg
                    }
                }, enabled = pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                    Text(stringResource(R.string.btn_set_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRemoteEncryptPassDialog = false 
                    e2eeEnabled = false
                }) { Text(stringResource(R.string.btn_cancel)) }
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
            stringResource(R.string.title_welcome_fintrack),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            stringResource(R.string.msg_setup_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.label_backup_location), style = MaterialTheme.typography.labelSmall)
                Text(selectedPath, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.btn_schedule_backup), style = MaterialTheme.typography.bodyLarge)
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
                Text(stringResource(R.string.label_e2ee_encryption), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.label_encrypted_backup_files), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
            Text(if (isPathSelected) stringResource(R.string.btn_change_backup_folder) else stringResource(R.string.btn_select_backup_folder))
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
                
                viewModel.triggerRefresh() 

                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isPathSelected,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(stringResource(R.string.btn_get_started))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = {
                prefs.edit().putBoolean("setup_complete", true).apply()
                backupPrefs.edit().putBoolean("enabled", false).apply()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_skip_setup))
        }
    }
}
