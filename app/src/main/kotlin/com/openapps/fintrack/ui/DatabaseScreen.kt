/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.*
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.AppDatabase
import com.openapps.fintrack.data.BackupWorker
import com.openapps.fintrack.data.EncryptionService
import com.openapps.fintrack.data.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbFile = context.getDatabasePath("expenses_database")
    val scrollState = rememberScrollState()
    
    var showSchedule by remember { mutableStateOf(false) }
    var showRemoteEncryptPassDialog by remember { mutableStateOf(false) }
    var showDisableE2EEDialog by remember { mutableStateOf(false) }
    var showSecureModeE2EEPrompt by remember { mutableStateOf(false) }
    
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    
    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var showRemoteSyncWarning by remember { mutableStateOf(false) }
    var showOpenDifferentDbConfirm by remember { mutableStateOf(false) }

    val openDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            viewModel.isPickingFile = false
            uri?.let {
                scope.launch {
                    val tempPicked = File(context.cacheDir, "picked_import.db")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tempPicked).use { output -> input.copyTo(output) }
                    }

                    if (!validateDatabaseSchema(tempPicked) && !EncryptionService.isEncrypted(tempPicked)) {
                        Toast.makeText(context, context.getString(R.string.msg_invalid_db_structure), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    if (EncryptionService.isValidSQLite(tempPicked)) {
                        viewModel.refreshDatabase(enqueueWorker = false)
                        if (importFileDirectly(context, tempPicked)) {
                            Toast.makeText(context, context.getString(R.string.msg_db_imported_success), Toast.LENGTH_LONG).show()
                            (context as Activity).recreate()
                        } else {
                            Toast.makeText(context, context.getString(R.string.msg_import_failed), Toast.LENGTH_SHORT).show()
                        }
                    } else if (EncryptionService.isEncrypted(tempPicked)) {
                        showImportPasswordDialog = true
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_not_valid_db), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    if (showImportPasswordDialog) {
        var importPass by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0f) }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showImportPasswordDialog = false },
            title = { Text(stringResource(R.string.title_encrypted_backup_detected)) },
            text = {
                Column {
                    if (isProcessing) {
                        Text(stringResource(R.string.msg_decrypting_file))
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text(stringResource(R.string.msg_file_encrypted_enter_pass))
                        OutlinedTextField(
                            value = importPass,
                            onValueChange = { importPass = it },
                            label = { Text(stringResource(R.string.label_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (!isProcessing) {
                    Button(onClick = {
                        scope.launch {
                            isProcessing = true
                            val tempPicked = File(context.cacheDir, "picked_import.db")
                            val decrypted = File(context.cacheDir, "import_decrypted.db")
                            
                            val result = withContext(Dispatchers.IO) {
                                val pChars = importPass.toCharArray()
                                val res = EncryptionService.decryptFile(tempPicked, decrypted, pChars) { p ->
                                    progress = p
                                }
                                pChars.fill('\u0000')
                                res
                            }
                            
                            if (result.isSuccess && validateDatabaseSchema(decrypted)) {
                                viewModel.refreshDatabase(enqueueWorker = false)
                                if (importFileDirectly(context, decrypted)) {
                                    Toast.makeText(context, context.getString(R.string.msg_decrypted_imported_success), Toast.LENGTH_LONG).show()
                                    showImportPasswordDialog = false
                                    (context as Activity).recreate()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.msg_import_failed_after_decryption), Toast.LENGTH_SHORT).show()
                                    isProcessing = false
                                }
                            } else {
                                val msg = result.exceptionOrNull()?.message ?: context.getString(R.string.msg_decryption_failed)
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                isProcessing = false
                            }
                        }
                    }, enabled = importPass.isNotEmpty()) {
                        Text(stringResource(R.string.btn_decrypt_open))
                    }
                }
            },
            dismissButton = {
                if (!isProcessing) {
                    TextButton(onClick = { showImportPasswordDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
        )
    }

    if (showDisableE2EEDialog) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableE2EEDialog = false },
            title = { Text(stringResource(R.string.title_verify_password)) },
            text = {
                Column {
                    Text(stringResource(R.string.msg_enter_master_pass_disable))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text(stringResource(R.string.label_master_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pChars = pass.toCharArray()
                    if (pChars.contentEquals(viewModel.remoteMasterPassword)) {
                        viewModel.updateEncryptRemote(false)
                        showDisableE2EEDialog = false
                        Toast.makeText(context, context.getString(R.string.msg_e2ee_disabled), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_incorrect_password), Toast.LENGTH_SHORT).show()
                    }
                    pChars.fill('\u0000')
                }, enabled = pass.isNotEmpty()) {
                    Text(stringResource(R.string.btn_verify_disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableE2EEDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showRemoteSyncWarning) {
        AlertDialog(
            onDismissRequest = { showRemoteSyncWarning = false },
            title = { Text(stringResource(R.string.title_privacy_warning)) },
            text = { Text(stringResource(R.string.msg_privacy_warning_desc)) },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.updateRemoteSyncEnabled(true)
                    scheduleWebDavSync(context, viewModel.syncFrequency)
                    showRemoteSyncWarning = false 
                }) { Text(stringResource(R.string.btn_enable_sync)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoteSyncWarning = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            viewModel.isPickingFile = false
            uri?.let {
                scope.launch {
                    val success = viewModel.performSafeBackup(context, it)
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.msg_backup_saved_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_backup_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )
    
    val encryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            viewModel.isPickingFile = false
            uri?.let {
                scope.launch {
                    val tempEnc = File(context.cacheDir, "temp_export.xpt")
                    val success = viewModel.performSafeEncryptedBackup(context, tempEnc, uri)
                    if (!success) {
                        Toast.makeText(context, context.getString(R.string.msg_encrypted_backup_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    if (showBackupOptionsDialog) {
        AlertDialog(
            onDismissRequest = { 
                showBackupOptionsDialog = false 
                viewModel.isPickingFile = false
            },
            title = { Text(stringResource(R.string.title_backup_options)) },
            text = { Text(if (viewModel.secureModeEnabled) stringResource(R.string.msg_ultra_secure_backup) else stringResource(R.string.msg_how_to_export)) },
            confirmButton = {
                Button(onClick = { 
                    showBackupOptionsDialog = false
                    encryptedBackupLauncher.launch("fintrack_backup.xpt")
                }) {
                    Text(stringResource(R.string.btn_encrypted_e2ee))
                }
            },
            dismissButton = {
                if (!viewModel.secureModeEnabled) {
                    TextButton(onClick = { 
                        showBackupOptionsDialog = false
                        backupLauncher.launch("fintrack_backup.db")
                    }) {
                        Text(stringResource(R.string.btn_plaintext))
                    }
                }
            }
        )
    }

    if (showOpenDifferentDbConfirm) {
        AlertDialog(
            onDismissRequest = { 
                showOpenDifferentDbConfirm = false 
                viewModel.isPickingFile = false
            },
            title = { Text(stringResource(R.string.title_replace_database)) },
            text = { Text(stringResource(R.string.msg_replace_database_desc)) },
            confirmButton = {
                Button(onClick = {
                    showOpenDifferentDbConfirm = false
                    viewModel.isPickingFile = true
                    openDbLauncher.launch(arrayOf("*/*"))
                }) {
                    Text(stringResource(R.string.btn_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showOpenDifferentDbConfirm = false 
                    viewModel.isPickingFile = false
                }) { Text("Cancel") }
            }
        )
    }

    val exportConfigsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            viewModel.isPickingFile = false
            uri?.let {
                    scope.launch {
                        val json = viewModel.exportConfigsJson()
                        context.contentResolver.openOutputStream(it)?.use { output ->
                            output.write(json.toByteArray())
                        }
                        Toast.makeText(context, context.getString(R.string.msg_configs_exported), Toast.LENGTH_SHORT).show()
                    }
            }
        }
    )

    val importConfigsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            viewModel.isPickingFile = false
            uri?.let {
                    scope.launch {
                        val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
                        if (json != null) {
                            val result = viewModel.importConfigsJson(json)
                            if (result.isSuccess) {
                                Toast.makeText(context, context.getString(R.string.msg_configs_imported_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.msg_import_failed) + ": ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_database_management)) },
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
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.label_current_database), style = MaterialTheme.typography.labelSmall)
            Text(dbFile.absolutePath, style = MaterialTheme.typography.bodySmall)
            
            Spacer(Modifier.height(24.dp))
            
            Button(onClick = { 
                viewModel.isPickingFile = true
                if (viewModel.secureModeEnabled || viewModel.encryptRemoteEnabled) {
                    showBackupOptionsDialog = true
                } else {
                    backupLauncher.launch("fintrack_backup.db")
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_backup_database))
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(onClick = { showSchedule = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_schedule_backup))
            }
            
            Spacer(Modifier.height(8.dp))

            Button(onClick = {
                viewModel.isPickingFile = true
                showOpenDifferentDbConfirm = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_open_different_db))
            }
            
            if (showSchedule) {
                ScheduleBackupDashboard(context, viewModel) { showSchedule = false }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.title_encryption_e2ee), style = MaterialTheme.typography.titleMedium)
            
            // Secure Mode Toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_ultra_secure_mode), color = if (viewModel.secureModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.label_ultra_secure_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.secureModeEnabled, onCheckedChange = { 
                    if (it && !viewModel.encryptRemoteEnabled) {
                        showSecureModeE2EEPrompt = true
                    } else {
                        viewModel.updateSecureMode(it)
                    }
                })
            }

            if (showSecureModeE2EEPrompt) {
                AlertDialog(
                    onDismissRequest = { showSecureModeE2EEPrompt = false },
                    title = { Text(stringResource(R.string.title_e2ee_required)) },
                    text = { Text(stringResource(R.string.msg_e2ee_required_desc)) },
                    confirmButton = {
                        Button(onClick = { showSecureModeE2EEPrompt = false }) { Text(stringResource(R.string.btn_ok)) }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Encrypt Remote Toggle (E2EE)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.label_encrypt_backups_remote))
                        Text(stringResource(R.string.label_encrypt_remote_desc), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(checked = viewModel.encryptRemoteEnabled, onCheckedChange = { 
                    if (it) {
                        showRemoteEncryptPassDialog = true
                    } else {
                        if (viewModel.secureModeEnabled) {
                            Toast.makeText(context, context.getString(R.string.msg_turn_off_ultra_secure), Toast.LENGTH_LONG).show()
                        } else {
                            showDisableE2EEDialog = true
                        }
                    }
                })
            }

            if (showRemoteEncryptPassDialog) {
                var pass by remember { mutableStateOf("") }
                var confirmPass by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                
                AlertDialog(
                    onDismissRequest = { showRemoteEncryptPassDialog = false },
                    title = { Text(stringResource(R.string.title_set_sync_password)) },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.msg_password_responsibility),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(stringResource(R.string.msg_sync_password_desc))
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
                                viewModel.updateRemoteMasterPassword(pass)
                                viewModel.updateEncryptRemote(true)
                                showRemoteEncryptPassDialog = false
                            } else {
                                error = context.getString(R.string.msg_passwords_not_match)
                            }
                        }, enabled = pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                            Text(stringResource(R.string.btn_set_enable))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoteEncryptPassDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.title_export_configs), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.label_export_configs_desc), style = MaterialTheme.typography.labelSmall)
            
            Spacer(Modifier.height(16.dp))
            
            Button(onClick = { 
                viewModel.isPickingFile = true
                exportConfigsLauncher.launch("fintrack_configs.json") 
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.title_export_configs))
            }
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedButton(onClick = { 
                viewModel.isPickingFile = true
                importConfigsLauncher.launch(arrayOf("application/json")) 
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_import_configs))
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.title_remote_sync_webdav), style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_enable_remote_sync))
                    Text(stringResource(R.string.label_remote_sync_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.remoteSyncEnabled, onCheckedChange = { 
                    if (it && !viewModel.encryptRemoteEnabled) {
                        showRemoteSyncWarning = true
                    } else {
                        viewModel.updateRemoteSyncEnabled(it)
                        if (it) {
                            scheduleWebDavSync(context, viewModel.syncFrequency)
                        } else {
                            WorkManager.getInstance(context).cancelUniqueWork("webdav_sync")
                        }
                    }
                })
            }

            if (viewModel.remoteSyncEnabled) {
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = viewModel.webdavUrl,
                    onValueChange = { viewModel.updateWebdavUrl(it) },
                    label = { Text(stringResource(R.string.label_webdav_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/remote.php/dav/files/user/") }
                )
                OutlinedTextField(
                    value = viewModel.webdavUsername,
                    onValueChange = { viewModel.updateWebdavUsername(it) },
                    label = { Text(stringResource(R.string.label_username)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.webdavPassword,
                    onValueChange = { viewModel.updateWebdavPassword(it) },
                    label = { Text(stringResource(R.string.label_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val result = viewModel.testWebdavConnection()
                            result.onSuccess {
                                Toast.makeText(context, context.getString(R.string.msg_connection_success), Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, context.getString(R.string.title_error) + ": ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isTestingConnection
                ) {
                    Text(if (viewModel.isTestingConnection) stringResource(R.string.label_testing) else stringResource(R.string.btn_test_connection))
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        val result = viewModel.syncNow()
                        result.onSuccess {
                            Toast.makeText(context, context.getString(R.string.msg_sync_success), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.title_error) + ": ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.isSyncing) {
                    Text(if (viewModel.isSyncing) stringResource(R.string.label_syncing) else stringResource(R.string.btn_sync_now))
                }

                if (viewModel.isSyncing) {
                    Spacer(Modifier.height(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(viewModel.syncMessage, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = viewModel.syncProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val progressPercent = (viewModel.syncProgress * 100).toInt()
                        val processedKb = String.format("%.2f", (viewModel.syncProcessedSize / 1024.0))
                        val totalKb = String.format("%.2f", viewModel.syncTotalSize / 1024.0)
                        
                        Text(
                            "$progressPercent% (${viewModel.syncMessage.lowercase()}) $processedKb / $totalKb KB",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                val downloadLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
                    onResult = { uri ->
                        uri?.let {
                            scope.launch {
                                val result = viewModel.downloadRemoteToLocal(it)
                                result.onSuccess {
                                    Toast.makeText(context, "File downloaded successfully!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    downloadLauncher.launch("expenses_database_cloud.db")
                }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.isSyncing) {
                    Text(stringResource(R.string.label_copy_remote_to_local))
                }

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.label_sync_frequency), style = MaterialTheme.typography.labelSmall)
                var freqExpanded by remember { mutableStateOf(false) }
                val frequencies = listOf("On new record", "5 minutes", "10 minutes", "30 minutes", "2 hours", "8 hours", "1 day", "1 week")
                
                Box {
                    OutlinedTextField(
                        value = viewModel.syncFrequency,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { freqExpanded = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
                    )
                    DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                        frequencies.forEach { freq ->
                            DropdownMenuItem(text = { Text(freq) }, onClick = {
                                viewModel.updateSyncFrequency(freq)
                                scheduleWebDavSync(context, freq)
                                freqExpanded = false
                            })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.label_last_success), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(viewModel.syncLastSuccessTime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.label_last_attempt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(viewModel.syncLastAttemptTime, style = MaterialTheme.typography.labelSmall)
                    }
                    if (viewModel.syncLastAttemptError.isNotEmpty()) {
                        Text(
                            stringResource(R.string.label_status, viewModel.syncLastStatus) + " - ${viewModel.syncLastAttemptError}", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = if (viewModel.syncLastStatus == "Failed" || viewModel.syncLastStatus == "Error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else if (viewModel.syncLastStatus != "Never") {
                        Text(
                            stringResource(R.string.label_status, viewModel.syncLastStatus),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleBackupDashboard(context: Context, viewModel: ExpenseViewModel, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    
    var hour by remember { mutableStateOf(prefs.getString("hour", "00") ?: "00") }
    var day by remember { mutableStateOf(prefs.getString("day", "01") ?: "01") }
    var month by remember { mutableStateOf(prefs.getString("month", "*") ?: "*") }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var encryptScheduled by remember { mutableStateOf(prefs.getBoolean("encrypt_scheduled_backup", false)) }
    
    var backupPath by remember { mutableStateOf(prefs.getString("path", context.getString(R.string.label_not_set)) ?: context.getString(R.string.label_not_set)) }
    var showE2EEWarning by remember { mutableStateOf(false) }

    if (showE2EEWarning) {
        AlertDialog(
            onDismissRequest = { showE2EEWarning = false },
            title = { Text(stringResource(R.string.title_e2ee_required)) },
            text = { Text(stringResource(R.string.msg_e2ee_required_scheduled)) },
            confirmButton = {
                TextButton(onClick = { showE2EEWarning = false }) { Text(stringResource(R.string.btn_ok)) }
            }
        )
    }

    val pathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                backupPath = it.toString()
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_set_backup_frequency)) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text(stringResource(R.string.label_hour)) }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text(stringResource(R.string.label_day)) }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text(stringResource(R.string.label_month)) }, modifier = Modifier.weight(1f))
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(stringResource(R.string.label_backup_path), style = MaterialTheme.typography.labelSmall)
                Text(backupPath, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { pathLauncher.launch(null) }) {
                    Text(stringResource(R.string.btn_select_backup_folder))
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_enable_schedule))
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_encrypt_backup_e2ee))
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = encryptScheduled || viewModel.secureModeEnabled, 
                        enabled = !viewModel.secureModeEnabled,
                        onCheckedChange = { 
                            if (it && !viewModel.encryptRemoteEnabled) {
                                showE2EEWarning = true
                            } else {
                                encryptScheduled = it 
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                prefs.edit().apply {
                    putString("hour", hour)
                    putString("day", day)
                    putString("month", month)
                    putBoolean("enabled", isEnabled)
                    putBoolean("encrypt_scheduled_backup", encryptScheduled)
                    putString("path", backupPath)
                    apply()
                }

                if (isEnabled) {
                    val backupWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                        .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "scheduled_backup",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        backupWorkRequest
                    )
                } else {
                    WorkManager.getInstance(context).cancelUniqueWork("scheduled_backup")
                }

                Toast.makeText(context, context.getString(R.string.msg_schedule_saved), Toast.LENGTH_LONG).show()
                onDismiss() 
            }) {
                Text(stringResource(R.string.btn_save))
            }
        }
    )
}

fun scheduleWebDavSync(context: Context, frequency: String) {
    if (frequency == "On new record") {
        WorkManager.getInstance(context).cancelUniqueWork("webdav_sync")
        return
    }

    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<com.openapps.fintrack.data.WebDavWorker>().build())

    val minutes = when (frequency) {
        "5 minutes" -> 15L
        "10 minutes" -> 15L
        "30 minutes" -> 30L
        "2 hours" -> 120L
        "8 hours" -> 480L
        "1 day" -> 1440L
        "1 week" -> 10080L
        else -> 1440L
    }

    val syncWorkRequest = PeriodicWorkRequestBuilder<com.openapps.fintrack.data.WebDavWorker>(minutes, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "webdav_sync",
        ExistingPeriodicWorkPolicy.REPLACE,
        syncWorkRequest
    )
    
    SafeLogger.d("WebDAV Sync Scheduled for every $minutes minutes")
}

fun validateDatabaseSchema(file: File): Boolean {
    if (!EncryptionService.isValidSQLite(file)) return false
    var db: android.database.sqlite.SQLiteDatabase? = null
    return try {

        db = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        
        val tablesToCheck = listOf("transactions", "major_heads", "minor_heads", "accounts", "categories")
        var allTablesExist = true
        
        for (tableName in tablesToCheck) {
            val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
            val exists = cursor.count > 0
            cursor.close()
            if (!exists) {
                allTablesExist = false
                break
            }
        }
        
        allTablesExist
    } catch (e: Exception) {
        Log.e("DatabaseValidator", "Schema validation failed", e)
        false
    } finally {
        db?.close()
    }
}

suspend fun importFileDirectly(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("expenses_database")
        
        File(dbFile.path).delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-journal").delete()

        FileInputStream(file).use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        Log.d("DatabaseImport", "Full 360-degree file replacement successful")
        true
    } catch (e: Exception) {
        Log.e("DatabaseImport", "Failed to import file", e)
        false
    }
}

suspend fun performBackupFile(context: Context, dbFile: File, destUri: Uri) = withContext(Dispatchers.IO) {
    try {
        if (!dbFile.exists()) {
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.msg_db_file_not_found), Toast.LENGTH_LONG).show() }
            return@withContext
        }
        context.contentResolver.openOutputStream(destUri)?.use { output ->
            FileInputStream(dbFile).use { input ->
                input.copyTo(output)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.msg_backup_saved_success), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.msg_backup_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
