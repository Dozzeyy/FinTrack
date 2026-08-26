/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
fun SettingsScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit, onRequireAuth: (() -> Unit) -> Unit) {
    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            
            val themes = listOf("Light", "Dark", "OLED")
            var themeExpanded by remember { mutableStateOf(false) }
            var showColorPicker by remember { mutableStateOf(false) }
            
            val languages = mapOf("en" to "English", "es" to "Español", "de" to "Deutsch", "zh" to "中文", "ru" to "Русский")
            var langExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { themeExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_theme) + ": ${viewModel.currentTheme}")
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

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showColorPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Palette, null, tint = Color(viewModel.currentPrimaryColor))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_color))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { langExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_language) + ": ${languages[viewModel.appLanguage] ?: viewModel.appLanguage}")
                    }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        languages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.updateLanguage(code)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (showColorPicker) {
                var red by remember { mutableFloatStateOf(Color(viewModel.currentPrimaryColor).red * 255f) }
                var green by remember { mutableFloatStateOf(Color(viewModel.currentPrimaryColor).green * 255f) }
                var blue by remember { mutableFloatStateOf(Color(viewModel.currentPrimaryColor).blue * 255f) }
                val currentColor = Color(red.toInt(), green.toInt(), blue.toInt())

                AlertDialog(
                    onDismissRequest = { showColorPicker = false },
                    title = { Text(stringResource(R.string.settings_color_picker_title)) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(100.dp, 40.dp).background(currentColor, MaterialTheme.shapes.small).border(1.dp, Color.Gray, MaterialTheme.shapes.small))
                            
                            Column {
                                Text(stringResource(R.string.settings_red, red.toInt()), style = MaterialTheme.typography.labelSmall)
                                Slider(value = red, onValueChange = { red = it }, valueRange = 0f..255f)
                            }
                            Column {
                                Text(stringResource(R.string.settings_green, green.toInt()), style = MaterialTheme.typography.labelSmall)
                                Slider(value = green, onValueChange = { green = it }, valueRange = 0f..255f)
                            }
                            Column {
                                Text(stringResource(R.string.settings_blue, blue.toInt()), style = MaterialTheme.typography.labelSmall)
                                Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..255f)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.updatePrimaryColor(currentColor.toArgb())
                            showColorPicker = false
                        }) { Text(stringResource(R.string.btn_apply)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showColorPicker = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_dashboard_accounts), style = MaterialTheme.typography.titleMedium)
            
            var accountExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { accountExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedNames = accounts.filter { it.id in viewModel.dashboardAccountIds }.joinToString { it.name }
                    Text(if (selectedNames.isEmpty()) stringResource(R.string.settings_select_accounts) else selectedNames)
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
            Text(stringResource(R.string.settings_dashboard_budgets), style = MaterialTheme.typography.titleMedium)
            
            val budgets by viewModel.getAllBudgets().collectAsState(initial = emptyList())
            var budgetExpanded by remember { mutableStateOf(false) }
            val budgetLabel = stringResource(R.string.label_budget)
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { budgetExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedNames = budgets.filter { it.id in viewModel.dashboardBudgetIds }.joinToString { it.name ?: "$budgetLabel ${it.id}" }
                    Text(if (selectedNames.isEmpty()) stringResource(R.string.settings_select_budgets) else selectedNames)
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
            Text(stringResource(R.string.settings_tab_order), style = MaterialTheme.typography.titleMedium)
            
            val tabLabels = mapOf(
                "home" to stringResource(R.string.menu_home),
                "analysis" to stringResource(R.string.menu_analysis),
                "transactions" to stringResource(R.string.menu_entries),
                "budgets" to stringResource(R.string.menu_budgets)
            )
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
                            }) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.settings_move_up)) }
                        }
                        if (index < viewModel.bottomTabOrder.size - 1) {
                            IconButton(onClick = {
                                val newOrder = viewModel.bottomTabOrder.toMutableList()
                                val tmp = newOrder[index]
                                newOrder[index] = newOrder[index + 1]
                                newOrder[index + 1] = tmp
                                viewModel.updateTabOrder(newOrder)
                            }) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.settings_move_down)) }
                        }
                    }
                }
                Divider()
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_advanced_trackers), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_negotiation))
                    Text(stringResource(R.string.settings_negotiation_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.negotiationTrackerEnabled, onCheckedChange = { viewModel.updateNegotiationTrackerEnabled(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_merchant))
                    Text(stringResource(R.string.settings_merchant_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.merchantTrackerEnabled, onCheckedChange = { viewModel.updateMerchantTrackerEnabled(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_discretionary))
                    Text(stringResource(R.string.settings_discretionary_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.discretionarySpendingTrackerEnabled, onCheckedChange = { viewModel.updateDiscretionarySpendingTrackerEnabled(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_financial_behavior), style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_income_end_month))
                    Text(stringResource(R.string.settings_income_end_month_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.incomeAtMonthEnd, onCheckedChange = { viewModel.updateIncomeAtMonthEnd(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_invoice_ageing))
                    Text(stringResource(R.string.settings_invoice_ageing_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.invoiceAgeTrackingEnabled, onCheckedChange = { viewModel.updateInvoiceAgeTrackingEnabled(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_delete_protection))
                    Text(stringResource(R.string.settings_delete_protection_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = viewModel.disableTransactionDeletion, onCheckedChange = { viewModel.updateDisableTransactionDeletion(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_app_lock))
                Switch(
                    checked = viewModel.appLockEnabled, 
                    onCheckedChange = { newValue ->
                        onRequireAuth {
                            viewModel.updateAppLockEnabled(newValue)
                        }
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_disable_screenshot))
                Switch(
                    checked = viewModel.disableScreenshots, 
                    onCheckedChange = { viewModel.updateDisableScreenshots(it) }
                )
            }

            if (viewModel.appLockEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_auto_lock_timeout), style = MaterialTheme.typography.labelSmall)
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
            Text(stringResource(R.string.settings_server_mode), style = MaterialTheme.typography.titleMedium)
            
            val isServerRunning by viewModel.isServerRunning.collectAsState()
            val isServerStopping by viewModel.isStopping.collectAsState()
            val serverError by viewModel.serverError.collectAsState()
            val httpUrl by viewModel.httpUrl.collectAsState()
            val httpsUrl by viewModel.httpsUrl.collectAsState()
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current

            var showErrorDialog by remember { mutableStateOf(false) }
            var showWebWarning by remember { mutableStateOf(false) }

            if (showWebWarning) {
                AlertDialog(
                    onDismissRequest = { showWebWarning = false },
                    title = { Text(stringResource(R.string.settings_web_warning_title)) },
                    text = { Text(stringResource(R.string.settings_web_warning_desc)) },
                    confirmButton = {
                        TextButton(onClick = { 
                            viewModel.toggleServer(true)
                            showWebWarning = false 
                        }) { Text(stringResource(R.string.btn_enable)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWebWarning = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                )
            }

            LaunchedEffect(serverError) {
                if (serverError != null) {
                    showErrorDialog = true
                }
            }

            if (showErrorDialog && serverError != null) {
                AlertDialog(
                    onDismissRequest = { showErrorDialog = false },
                    title = { Text(stringResource(R.string.settings_server_error)) },
                    text = { Text(serverError!!) },
                    confirmButton = {
                        TextButton(onClick = { showErrorDialog = false }) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    }
                )
            }

            val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                if (result.contents != null) {
                    val success = viewModel.authorizePairing(result.contents)
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.settings_device_authorized), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.settings_invalid_qr), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_enable_web))
                    Text(stringResource(R.string.settings_web_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = isServerRunning,
                    enabled = !isServerStopping,
                    onCheckedChange = { running ->
                        if (running) {
                            showWebWarning = true
                        } else {
                            viewModel.toggleServer(false)
                        }
                    }
                )
            }
            
            if (isServerRunning) {
                Text(
                    stringResource(R.string.settings_server_perf_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(stringResource(R.string.settings_conn_url), style = MaterialTheme.typography.labelSmall)
                    
                    if (httpUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(httpUrl!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(httpUrl!!))
                                Toast.makeText(context, context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.label_copy)) }
                        }
                    }
                    
                    if (httpsUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(httpsUrl!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(httpsUrl!!))
                                Toast.makeText(context, context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.label_copy)) }
                        }
                        Text(stringResource(R.string.settings_https_note), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            val options = ScanOptions()
                            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            options.setPrompt(context.getString(R.string.settings_scan_qr))
                            options.setBeepEnabled(false)
                            options.setOrientationLocked(false)
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_scan_qr))
                    }

                    val activeClients by viewModel.activeClients.collectAsState()
                    val terminationLogs by viewModel.terminationLogs.collectAsState()

                    if (activeClients.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.settings_active_clients), style = MaterialTheme.typography.titleSmall)
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
                                        Icon(Icons.Default.Close, stringResource(R.string.settings_disconnect), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    if (terminationLogs.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.settings_termination_logs), style = MaterialTheme.typography.titleSmall)
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
            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_expense_reminders))
                Switch(
                    checked = viewModel.remindersEnabled,
                    onCheckedChange = { viewModel.updateReminderEnabled(it) }
                )
            }

            if (viewModel.remindersEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                    Text(stringResource(R.string.settings_frequency), style = MaterialTheme.typography.labelSmall)
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
                    Text(stringResource(R.string.label_time), style = MaterialTheme.typography.labelSmall)
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
                        label = { Text(stringResource(R.string.settings_notification_msg)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_cc_alert))
                    Text(stringResource(R.string.settings_cc_alert_desc), style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = viewModel.ccAlertEnabled,
                    onCheckedChange = { viewModel.updateCcAlertEnabled(it) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_regional), style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_millions_system))
                Switch(checked = viewModel.useMillionsSystem, onCheckedChange = { viewModel.updateNumberSystem(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_tap_net_position))
                Switch(checked = viewModel.tapToShowNetPosition, onCheckedChange = { viewModel.updateTapToShowNetPosition(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_data_entry), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_multi_tag))
                Switch(checked = viewModel.multiTagEnabled, onCheckedChange = { viewModel.updateMultiTagEnabled(it) })
            }

            Spacer(Modifier.height(16.dp))
            MultiCurrencySettings(viewModel)

            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_auto_read))
                Switch(checked = viewModel.autoReadEnabled, onCheckedChange = { viewModel.updateAutoReadEnabled(it) })
            }
            
            if (viewModel.autoReadEnabled) {
                AutoReadCriteria(viewModel, onNavigate)
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_auto_read_note),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun MultiCurrencySettings(viewModel: ExpenseViewModel) {
    var showBaseCurrencyDialog by remember { mutableStateOf(false) }
    
    val currencies = remember { 
        java.util.Currency.getAvailableCurrencies()
            .map { it.currencyCode }
            .sorted()
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_multi_currency))
            Text(stringResource(R.string.settings_multi_currency_desc), style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = viewModel.enableMultiCurrency,
            onCheckedChange = { 
                if (it) showBaseCurrencyDialog = true
                else viewModel.updateMultiCurrencyEnabled(false)
            }
        )
    }

    if (viewModel.enableMultiCurrency) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_base_currency, viewModel.baseCurrency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showBaseCurrencyDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isRefreshingRates
            ) {
                Text(stringResource(R.string.settings_change_base_currency))
            }
            Button(
                onClick = { viewModel.refreshExchangeRates() },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isRefreshingRates
            ) {
                if (viewModel.isRefreshingRates) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_refreshing))
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_refresh_rates))
                }
            }
        }

        if (viewModel.isRefreshingRates || viewModel.rateRefreshStatus != null) {
            Text(
                text = stringResource(R.string.settings_status, viewModel.rateRefreshStatus ?: stringResource(R.string.settings_waiting)),
                style = MaterialTheme.typography.labelSmall,
                color = if (viewModel.rateRefreshStatus == "Failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        viewModel.lastRateRefreshResult.value?.let { result ->
            Text(result, style = MaterialTheme.typography.labelSmall, color = if (result.contains("Successful")) Color(0xFF4CAF50) else Color.Red)
        }
        Text(stringResource(R.string.settings_last_refresh, viewModel.lastRateRefreshTime), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }

    if (showBaseCurrencyDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filtered = remember(searchQuery) {
            if (searchQuery.isBlank()) currencies
            else currencies.filter { it.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { if (viewModel.enableMultiCurrency) showBaseCurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_select_base_currency)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.settings_search_currency)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn {
                            items(filtered) { code ->
                                val currency = java.util.Currency.getInstance(code)
                                ListItem(
                                    headlineContent = { Text("$code - ${currency.getDisplayName(java.util.Locale.getDefault())}") },
                                    modifier = Modifier.clickable {
                                        viewModel.updateBaseCurrency(code)
                                        viewModel.updateMultiCurrencyEnabled(true)
                                        showBaseCurrencyDialog = false
                                    }
                                )
                                Divider()
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (viewModel.enableMultiCurrency) {
                    TextButton(onClick = { showBaseCurrencyDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReadCriteria(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Button(onClick = { onNavigate("rules") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Rule, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_manage_rules))
        }
        
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.smsCurrencies,
            onValueChange = { viewModel.updateSmsCurrencies(it) },
            label = { Text(stringResource(R.string.settings_sms_currencies)) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.settings_sms_currencies_tip),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = viewModel.smsKeywords,
            onValueChange = { viewModel.updateSmsKeywords(it) },
            label = { Text(stringResource(R.string.settings_sms_keywords)) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_match_criteria), style = MaterialTheme.typography.labelSmall)
        
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
