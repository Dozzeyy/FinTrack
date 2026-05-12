/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, onRequireAuth: (() -> Unit) -> Unit) {
    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            
            val themes = listOf("Light", "Dark", "OLED Dark")
            var themeExpanded by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { themeExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Theme: ${viewModel.currentTheme}")
                }
                DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                    themes.forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(theme) },
                            onClick = {
                                viewModel.updateTheme(theme)
                                themeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Home Dashboard Accounts (Max 3)", style = MaterialTheme.typography.titleMedium)
            
            var accountExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { accountExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedNames = accounts.filter { it.id in viewModel.dashboardAccountIds }.joinToString { it.name }
                    Text(if (selectedNames.isEmpty()) "Select Accounts" else selectedNames)
                }
                DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                    accounts.forEach { account ->
                        val isSelected = account.id in viewModel.dashboardAccountIds
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(account.name)
                                }
                            },
                            onClick = {
                                val current = viewModel.dashboardAccountIds.toMutableList()
                                if (isSelected) {
                                    current.remove(account.id)
                                } else if (current.size < 3) {
                                    current.add(account.id)
                                }
                                viewModel.updateDashboardAccounts(current)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Home Dashboard Budgets (Max 3)", style = MaterialTheme.typography.titleMedium)
            
            val budgets by viewModel.getAllBudgets().collectAsState(initial = emptyList())
            var budgetExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { budgetExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedNames = budgets.filter { it.id in viewModel.dashboardBudgetIds }.joinToString { it.name ?: "Budget ${it.id}" }
                    Text(if (selectedNames.isEmpty()) "Select Budgets" else selectedNames)
                }
                DropdownMenu(expanded = budgetExpanded, onDismissRequest = { budgetExpanded = false }) {
                    budgets.forEach { budget ->
                        val isSelected = budget.id in viewModel.dashboardBudgetIds
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(budget.name ?: "Budget ${budget.id}")
                                }
                            },
                            onClick = {
                                val current = viewModel.dashboardBudgetIds.toMutableList()
                                if (isSelected) {
                                    current.remove(budget.id)
                                } else if (current.size < 3) {
                                    current.add(budget.id)
                                }
                                viewModel.updateDashboardBudgets(current)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Bottom Tab Order", style = MaterialTheme.typography.titleMedium)
            
            val tabLabels = mapOf("home" to "Home", "analysis" to "Analysis", "transactions" to "Transactns", "budgets" to "Budgets")
            viewModel.bottomTabOrder.forEachIndexed { index, tabKey ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tabLabels[tabKey] ?: tabKey)
                    Row {
                        if (index > 0) {
                            IconButton(onClick = {
                                val newOrder = viewModel.bottomTabOrder.toMutableList()
                                val tmp = newOrder[index]
                                newOrder[index] = newOrder[index - 1]
                                newOrder[index - 1] = tmp
                                viewModel.updateTabOrder(newOrder)
                            }) { Icon(Icons.Default.ArrowUpward, "Move Up") }
                        }
                        if (index < viewModel.bottomTabOrder.size - 1) {
                            IconButton(onClick = {
                                val newOrder = viewModel.bottomTabOrder.toMutableList()
                                val tmp = newOrder[index]
                                newOrder[index] = newOrder[index + 1]
                                newOrder[index + 1] = tmp
                                viewModel.updateTabOrder(newOrder)
                            }) { Icon(Icons.Default.ArrowDownward, "Move Down") }
                        }
                    }
                }
                Divider()
            }

            Spacer(Modifier.height(16.dp))
            Text("Security", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("App Lock (Biometrics/PIN)")
                Switch(
                    checked = viewModel.appLockEnabled, 
                    onCheckedChange = { newValue ->
                        onRequireAuth {
                            viewModel.updateAppLockEnabled(newValue)
                        }
                    }
                )
            }

            if (viewModel.appLockEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("Auto-lock Inactivity Timeout:", style = MaterialTheme.typography.labelSmall)
                var timeoutExpanded by remember { mutableStateOf(false) }
                val timeouts = listOf("5 seconds", "15 seconds", "30 seconds", "1 minute", "2 minute", "5 minute", "10 minute", "30 minutes", "keep unlocked until app closure")
                
                Box {
                    OutlinedButton(
                        onClick = { timeoutExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.inactivityTimeout)
                    }
                    DropdownMenu(expanded = timeoutExpanded, onDismissRequest = { timeoutExpanded = false }) {
                        timeouts.forEach { timeout ->
                            DropdownMenuItem(text = { Text(timeout) }, onClick = {
                                viewModel.updateInactivityTimeout(timeout)
                                timeoutExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Local Server Mode", style = MaterialTheme.typography.titleMedium)
            
            val isServerRunning by viewModel.isServerRunning.collectAsState()
            val isServerStopping by viewModel.isStopping.collectAsState()
            val serverError by viewModel.serverError.collectAsState()
            val httpUrl by viewModel.httpUrl.collectAsState()
            val httpsUrl by viewModel.httpsUrl.collectAsState()
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current

            var showErrorDialog by remember { mutableStateOf(false) }

            LaunchedEffect(serverError) {
                if (serverError != null) {
                    showErrorDialog = true
                }
            }

            if (showErrorDialog && serverError != null) {
                AlertDialog(
                    onDismissRequest = { showErrorDialog = false },
                    title = { Text("Server Error") },
                    text = { Text(serverError!!) },
                    confirmButton = {
                        TextButton(onClick = { showErrorDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                if (result.contents != null) {
                    val success = viewModel.authorizePairing(result.contents)
                    if (success) {
                        Toast.makeText(context, "Device Authorized Successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid Pairing QR", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Web Interface")
                    Text("Access data from other devices.", style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = isServerRunning,
                    enabled = !isServerStopping,
                    onCheckedChange = { running ->
                        viewModel.toggleServer(running)
                    }
                )
            }
            
            if (isServerRunning) {
                Text(
                    "Keep the app open for best server performance. Android may stop the server if the app is in the background.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Select connection URL:", style = MaterialTheme.typography.labelSmall)
                    
                    if (httpUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(httpUrl!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(httpUrl!!))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        }
                    }
                    
                    if (httpsUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(httpsUrl!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(httpsUrl!!))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        }
                        Text("Note: HTTPS uses a self-signed certificate. You may need to accept a security warning in your browser.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            val options = ScanOptions()
                            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            options.setPrompt("Scan the QR code shown on the Web Client")
                            options.setBeepEnabled(false)
                            options.setOrientationLocked(false)
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Web QR to Authorize")
                    }

                    val activeClients by viewModel.activeClients.collectAsState()
                    val terminationLogs by viewModel.terminationLogs.collectAsState()

                    if (activeClients.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Active Client Connections", style = MaterialTheme.typography.titleSmall)
                        activeClients.forEach { (ip, connection) ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ip, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(connection.userAgent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    IconButton(onClick = { viewModel.disconnectClient(ip) }) {
                                        Icon(Icons.Default.Close, "Disconnect", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    if (terminationLogs.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Recent Termination Logs", style = MaterialTheme.typography.titleSmall)
                        terminationLogs.forEach { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(log.client, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(java.time.Instant.ofEpochMilli(log.timestamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(log.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Expense Reminders")
                Switch(
                    checked = viewModel.remindersEnabled,
                    onCheckedChange = { viewModel.updateReminderEnabled(it) }
                )
            }

            if (viewModel.remindersEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                    Text("Frequency", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var freqText by remember { mutableStateOf(viewModel.reminderFrequency.toString()) }
                        OutlinedTextField(
                            value = freqText,
                            onValueChange = { 
                                freqText = it
                                it.toIntOrNull()?.let { freq -> viewModel.updateReminderFrequency(freq) }
                            },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("1") }
                        )
                        Spacer(Modifier.width(8.dp))
                        var unitExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { unitExpanded = true }) {
                                Text(viewModel.reminderUnit)
                            }
                            DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                listOf("day/s", "week/s", "month/s", "year/s").forEach { unit ->
                                    DropdownMenuItem(text = { Text(unit) }, onClick = {
                                        viewModel.updateReminderUnit(unit)
                                        unitExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Text("Time", style = MaterialTheme.typography.labelSmall)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    OutlinedButton(
                        onClick = {
                            val parts = viewModel.reminderTime.split(":")
                            android.app.TimePickerDialog(context, { _, h, m ->
                                viewModel.updateReminderTime(String.format("%02d:%02d", h, m))
                            }, parts[0].toInt(), parts[1].toInt(), true).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.reminderTime)
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.reminderMessage,
                        onValueChange = { viewModel.updateReminderMessage(it) },
                        label = { Text("Notification Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Regional Settings", style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Use Millions/Billions System")
                Switch(checked = viewModel.useMillionsSystem, onCheckedChange = { viewModel.updateNumberSystem(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text("Data Entry", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Multi-Tag Selection")
                Switch(checked = viewModel.multiTagEnabled, onCheckedChange = { viewModel.updateMultiTagEnabled(it) })
            }

            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto Read Messages")
                Switch(checked = viewModel.autoReadEnabled, onCheckedChange = { viewModel.updateAutoReadEnabled(it) })
            }
            
            if (viewModel.autoReadEnabled) {
                AutoReadCriteria(viewModel)
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                "Note: This feature is optional and App is not designed to send your SMS data to developers.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReadCriteria(viewModel: ExpenseViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(
            value = viewModel.smsCurrencies,
            onValueChange = { viewModel.updateSmsCurrencies(it) },
            label = { Text("Currencies (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Tip: Currencies field is used to detect amount of transaction from SMS.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = viewModel.smsKeywords,
            onValueChange = { viewModel.updateSmsKeywords(it) },
            label = { Text("Keywords (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        Text("Match Criteria:", style = MaterialTheme.typography.labelSmall)
        
        var criteriaExpanded by remember { mutableStateOf(false) }
        val criteriaOptions = listOf("OR" to "A or B", "AND" to "A and B", "ONLY_A" to "Only A", "ONLY_B" to "Only B")
        
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { criteriaExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(criteriaOptions.find { it.first == viewModel.smsConditionType }?.second ?: viewModel.smsConditionType)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = criteriaExpanded, onDismissRequest = { criteriaExpanded = false }) {
                criteriaOptions.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.updateSmsConditionType(key)
                            criteriaExpanded = false
                        }
                    )
                }
            }
        }
    }
}
