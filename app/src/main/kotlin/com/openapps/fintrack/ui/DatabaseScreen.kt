/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.*
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
    
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    
    var showBackupOptionsDialog by remember { mutableStateOf(false) }

    val openDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val tempPicked = File(context.cacheDir, "picked_import.db")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tempPicked).use { output -> input.copyTo(output) }
                    }

                    if (!validateDatabaseSchema(tempPicked) && !EncryptionService.isEncrypted(tempPicked)) {
                        Toast.makeText(context, "Invalid database file structure.", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    if (EncryptionService.isValidSQLite(tempPicked)) {
                        viewModel.closeDatabase()
                        if (importFileDirectly(context, tempPicked)) {
                            Toast.makeText(context, "Database imported successfully.", Toast.LENGTH_LONG).show()
                            (context as Activity).recreate()
                        } else {
                            Toast.makeText(context, "Import failed.", Toast.LENGTH_SHORT).show()
                        }
                    } else if (EncryptionService.isEncrypted(tempPicked)) {
                        showImportPasswordDialog = true
                    } else {
                        Toast.makeText(context, "Selected file is not a valid database.", Toast.LENGTH_LONG).show()
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
            title = { Text("Encrypted Backup Detected") },
            text = {
                Column {
                    if (isProcessing) {
                        Text("Decrypting file...")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("This file is encrypted. Please enter the password to decrypt and open it.")
                        OutlinedTextField(
                            value = importPass,
                            onValueChange = { importPass = it },
                            label = { Text("Password") },
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
                                EncryptionService.decryptFile(tempPicked, decrypted, importPass) { p ->
                                    progress = p
                                }
                            }
                            
                            if (result.isSuccess && validateDatabaseSchema(decrypted)) {
                                viewModel.closeDatabase()
                                if (importFileDirectly(context, decrypted)) {
                                    Toast.makeText(context, "Decrypted and imported successfully.", Toast.LENGTH_LONG).show()
                                    showImportPasswordDialog = false
                                    (context as Activity).recreate()
                                } else {
                                    Toast.makeText(context, "Import failed after decryption.", Toast.LENGTH_SHORT).show()
                                    isProcessing = false
                                }
                            } else {
                                val msg = result.exceptionOrNull()?.message ?: "Decryption failed."
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                isProcessing = false
                            }
                        }
                    }, enabled = importPass.isNotEmpty()) {
                        Text("Decrypt & Open")
                    }
                }
            },
            dismissButton = {
                if (!isProcessing) {
                    TextButton(onClick = { showImportPasswordDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showDisableE2EEDialog) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableE2EEDialog = false },
            title = { Text("Verify Password") },
            text = {
                Column {
                    Text("Please enter your current master password to disable remote encryption.")
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pass == viewModel.remoteMasterPassword) {
                        viewModel.updateEncryptRemote(false)
                        showDisableE2EEDialog = false
                        Toast.makeText(context, "E2EE disabled.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Incorrect password.", Toast.LENGTH_SHORT).show()
                    }
                }, enabled = pass.isNotEmpty()) {
                    Text("Verify & Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableE2EEDialog = false }) { Text("Cancel") }
            }
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    viewModel.prepareForBackup()
                    performBackupFile(context, dbFile, it)
                }
            }
        }
    )
    
    val encryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val tempEnc = File(context.cacheDir, "temp_export.ftd")
                    viewModel.prepareForBackup()
                    val result = withContext(Dispatchers.IO) {
                        EncryptionService.encryptFile(dbFile, tempEnc, viewModel.remoteMasterPassword)
                    }
                    if (result.isSuccess) {
                        context.contentResolver.openOutputStream(it)?.use { output ->
                            tempEnc.inputStream().use { input -> input.copyTo(output) }
                        }
                        Toast.makeText(context, "Encrypted backup saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Encryption failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    if (showBackupOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showBackupOptionsDialog = false },
            title = { Text("Backup Options") },
            text = { Text("How would you like to export your current database?") },
            confirmButton = {
                Button(onClick = { 
                    showBackupOptionsDialog = false
                    encryptedBackupLauncher.launch("fintrack_backup.ftd")
                }) {
                    Text("Encrypted (E2EE)")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showBackupOptionsDialog = false
                    backupLauncher.launch("fintrack_backup.db")
                }) {
                    Text("Plaintext")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Database Management") },
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
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text("Current Database:", style = MaterialTheme.typography.labelSmall)
            Text(dbFile.absolutePath, style = MaterialTheme.typography.bodySmall)
            
            Spacer(Modifier.height(24.dp))
            
            Button(onClick = { 
                openDbLauncher.launch(arrayOf("*/*"))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Different Database")
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(onClick = { 
                if (viewModel.encryptRemoteEnabled) {
                    showBackupOptionsDialog = true
                } else {
                    backupLauncher.launch("fintrack_backup.db")
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Backup Current Database")
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(onClick = { showSchedule = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Schedule Backup")
            }
            
            if (showSchedule) {
                ScheduleBackupDashboard(context) { showSchedule = false }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text("Encryption (E2EE)", style = MaterialTheme.typography.titleMedium)
            
            // Encrypt Remote Toggle (E2EE)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Encrypt Backups/Remote")
                        Text("Secure local DB before exporting or uploading.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(checked = viewModel.encryptRemoteEnabled, onCheckedChange = { 
                    if (it) showRemoteEncryptPassDialog = true
                    else showDisableE2EEDialog = true
                })
            }

            if (showRemoteEncryptPassDialog) {
                var pass by remember { mutableStateOf("") }
                var confirmPass by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                
                AlertDialog(
                    onDismissRequest = { showRemoteEncryptPassDialog = false },
                    title = { Text("Set Sync Password") },
                    text = {
                        Column {
                            Text("This password is used to encrypt your cloud data. Keep it safe.")
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
                                viewModel.updateRemoteMasterPassword(pass)
                                viewModel.updateEncryptRemote(true)
                                showRemoteEncryptPassDialog = false
                            } else {
                                error = "Passwords do not match"
                            }
                        }, enabled = pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                            Text("Set & Enable")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoteEncryptPassDialog = false }) { Text("Cancel") }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text("Remote Sync (WebDAV)", style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Remote Sync")
                    Text("Sync database to your cloud provider.", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.remoteSyncEnabled, onCheckedChange = { 
                    viewModel.updateRemoteSyncEnabled(it)
                    if (it) {
                        scheduleWebDavSync(context, viewModel.syncFrequency)
                    } else {
                        WorkManager.getInstance(context).cancelUniqueWork("webdav_sync")
                    }
                })
            }

            if (viewModel.remoteSyncEnabled) {
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = viewModel.webdavUrl,
                    onValueChange = { viewModel.updateWebdavUrl(it) },
                    label = { Text("WebDAV URL") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/remote.php/dav/files/user/") }
                )
                OutlinedTextField(
                    value = viewModel.webdavUsername,
                    onValueChange = { viewModel.updateWebdavUsername(it) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.webdavPassword,
                    onValueChange = { viewModel.updateWebdavPassword(it) },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val result = viewModel.testWebdavConnection()
                            result.onSuccess {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isTestingConnection
                ) {
                    Text(if (viewModel.isTestingConnection) "Testing..." else "Test Connection")
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        val result = viewModel.syncNow()
                        result.onSuccess {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.isSyncing) {
                    Text(if (viewModel.isSyncing) "Syncing..." else "Sync Now")
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
                            "$progressPercent% (${viewModel.syncMessage.lowercase()}) - $processedKb / $totalKb KB",
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
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
                    Text("Copy Remote to Local Path")
                }

                Spacer(Modifier.height(16.dp))
                Text("Sync Frequency:", style = MaterialTheme.typography.labelSmall)
                var freqExpanded by remember { mutableStateOf(false) }
                val frequencies = listOf("5 minutes", "10 minutes", "30 minutes", "2 hours", "8 hours", "1 day", "1 week")
                
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

                if (viewModel.lastSyncTime != "Never") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Last Sync: ${viewModel.lastSyncTime} (${viewModel.lastSyncSize})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleBackupDashboard(context: Context, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    
    var hour by remember { mutableStateOf(prefs.getString("hour", "00") ?: "00") }
    var day by remember { mutableStateOf(prefs.getString("day", "01") ?: "01") }
    var month by remember { mutableStateOf(prefs.getString("month", "*") ?: "*") }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var encryptScheduled by remember { mutableStateOf(prefs.getBoolean("encrypt_scheduled_backup", false)) }
    
    var backupPath by remember { mutableStateOf(prefs.getString("path", "Not Set") ?: "Not Set") }

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
        title = { Text("Set Backup Frequency") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text("Hour") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Day") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("Month") }, modifier = Modifier.weight(1f))
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text("Backup Path:", style = MaterialTheme.typography.labelSmall)
                Text(backupPath, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { pathLauncher.launch(null) }) {
                    Text("Select Backup Folder")
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Schedule")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Encrypt the backup (E2EE)")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = encryptScheduled, onCheckedChange = { encryptScheduled = it })
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

                Toast.makeText(context, "Schedule saved", Toast.LENGTH_LONG).show()
                onDismiss() 
            }) {
                Text("Save")
            }
        }
    )
}

fun scheduleWebDavSync(context: Context, frequency: String) {
    val minutes = when (frequency) {
        "5 minutes" -> 15L // WorkManager minimum
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

    // Using REPLACE to ensure interval change is applied immediately
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "webdav_sync",
        ExistingPeriodicWorkPolicy.REPLACE,
        syncWorkRequest
    )
    
    SafeLogger.d("WebDAV Sync Scheduled for every $minutes minutes")
}

fun validateDatabaseSchema(file: File): Boolean {
    return try {
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(100)
            if (fis.read(buffer) < 16) return false
            val header = String(buffer, 0, 16)
            header.startsWith("SQLite format 3")
        }
    } catch (e: Exception) {
        false
    }
}

suspend fun importFileDirectly(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("expenses_database")
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-journal").delete()

        FileInputStream(file).use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

suspend fun performBackupFile(context: Context, dbFile: File, destUri: Uri) = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(destUri)?.use { output ->
            dbFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Backup Successful!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Backup Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
