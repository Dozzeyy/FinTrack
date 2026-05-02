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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    var selectedPath by remember { mutableStateOf("Not Selected") }
    var isPathSelected by remember { mutableStateOf(false) }

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
                    context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE).edit()
                        .putString("path", it.toString())
                        .putBoolean("enabled", true)
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
            "To keep your data safe, please select a folder where your automatic backups will be saved.",
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
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                prefs.edit().putBoolean("setup_complete", true).apply()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isPathSelected,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Get Started")
        }
    }
}
