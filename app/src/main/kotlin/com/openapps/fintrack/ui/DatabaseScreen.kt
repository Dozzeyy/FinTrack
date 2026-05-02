package com.openapps.fintrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.openapps.fintrack.data.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbFile = context.getDatabasePath("expenses_database")
    val displayPath = dbFile.absolutePath
    
    var showSchedule by remember { mutableStateOf(false) }

    val openDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { 
                scope.launch {
                    viewModel.closeDatabase()
                    val success = importDatabaseFile(context, it)
                    if (success) {
                        viewModel.refreshDatabase(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Database imported.", Toast.LENGTH_LONG).show()
                            context.findActivity()?.recreate()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Import failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

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
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Current Database:", style = MaterialTheme.typography.labelSmall)
            Text(displayPath, style = MaterialTheme.typography.bodySmall)
            
            Spacer(Modifier.height(24.dp))
            
            Button(onClick = { 
                openDbLauncher.launch(arrayOf("*/*"))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Different Database")
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(onClick = { 
                backupLauncher.launch("expenses_backup.db")
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
        }
    }
}

suspend fun importDatabaseFile(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("expenses_database")
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-journal").delete()

        context.contentResolver.openInputStream(uri)?.use { input ->
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

@Composable
fun ScheduleBackupDashboard(context: Context, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    
    var hour by remember { mutableStateOf(prefs.getString("hour", "00") ?: "00") }
    var day by remember { mutableStateOf(prefs.getString("day", "01") ?: "01") }
    var month by remember { mutableStateOf(prefs.getString("month", "*") ?: "*") }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Enable Schedule")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
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

                Toast.makeText(context, "Schedule saved and activated", Toast.LENGTH_LONG).show()
                onDismiss() 
            }) {
                Text("Save")
            }
        }
    )
}
