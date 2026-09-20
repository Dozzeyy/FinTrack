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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.EncryptionService
import com.openapps.fintrack.data.TransactionLegacy
import com.openapps.fintrack.data.TransactionWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    onNavigate: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("home") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showLoadRemoteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDownloadedEncryptedFile by remember { mutableStateOf<File?>(null) }
    var downloadMasterPasswordInput by remember { mutableStateOf("") }
    var downloadDecryptionError by remember { mutableStateOf<String?>(null) }
    
    var analysisInitialType by remember { mutableStateOf<String?>(null) }
    var sharedDateRange by remember { mutableStateOf<Pair<String, String>?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())

    val filteredSearchTransactions = remember(searchQuery, allTransactions) {
        if (searchQuery.isBlank()) emptyList()
        else {
            allTransactions.filter { t ->
                t.transaction.note?.contains(searchQuery, ignoreCase = true) == true ||
                t.categoryName?.contains(searchQuery, ignoreCase = true) == true ||
                t.accountName.contains(searchQuery, ignoreCase = true) == true ||
                t.toAccountName?.contains(searchQuery, ignoreCase = true) == true ||
                t.transaction.amount.toString().contains(searchQuery) ||
                t.transaction.date.contains(searchQuery) ||
                t.transaction.transactionNumber?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.app_name) + stringResource(R.string.label_menu_suffix), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                        Divider()
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_home)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; selectedTab = "home" },
                            icon = { Icon(Icons.Default.Home, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_summary)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("summary") },
                            icon = { Icon(Icons.Default.Assessment, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_import)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("import_statement") },
                            icon = { Icon(Icons.Default.FileUpload, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_credit_cards)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("credit_cards") },
                            icon = { Icon(Icons.Default.CreditCard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_subscriptions)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("subscriptions") },
                            icon = { Icon(Icons.Default.CardMembership, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_investments)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("investments") },
                            icon = { Icon(Icons.Default.TrendingUp, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_fixed_deposits)) },
                            selected = false,
                            modifier = Modifier.padding(start = 16.dp),
                            onClick = { scope.launch { drawerState.close() }; onNavigate("fixed_deposits") },
                            icon = { Icon(Icons.Default.Savings, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_performance)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("performance") },
                            icon = { Icon(Icons.Default.Speed, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_categories)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_categories") },
                            icon = { Icon(Icons.Default.Category, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_templates)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("templates") },
                            icon = { Icon(Icons.Default.Dashboard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_tags)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_tags") },
                            icon = { Icon(Icons.Default.Label, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_goals)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("goals") },
                            icon = { Icon(Icons.Default.Flag, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_budgets)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_budgets") },
                            icon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_notes)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("notes") },
                            icon = { Icon(Icons.Default.Notes, null) }
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_permissions)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("permissions") },
                            icon = { Icon(Icons.Default.Security, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_tutorial)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("tutorial") },
                            icon = { Icon(Icons.Default.HelpCenter, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_settings)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("settings") },
                            icon = { Icon(Icons.Default.Settings, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_database)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("database") },
                            icon = { Icon(Icons.Default.Storage, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_contact)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("contact") },
                            icon = { Icon(Icons.Default.Email, null) }
                        )
                    }
                    
                    Text(
                        "v1.0.20",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                        } else {
                            (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                        }
                    },
                    label = "SearchTransition"
                ) { searchActive ->
                    if (searchActive) {
                        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(stringResource(R.string.label_search_placeholder)) },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    singleLine = true,
                                    leadingIcon = {
                                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                            Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                                        }
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                items(filteredSearchTransactions) { detail ->
                                    Box(modifier = Modifier.clickable { 
                                        viewModel.selectedTransactionDetail = detail 
                                    }) {
                                        TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = false)
                                    }
                                }
                            }
                        }
                        if (viewModel.selectedTransactionDetail != null) {
                            BackHandler { viewModel.selectedTransactionDetail = null }
                            AddTransactionScreen(
                                viewModel = viewModel, 
                                onBack = { viewModel.selectedTransactionDetail = null }, 
                                onNavigate = onNavigate, 
                                readOnly = true
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            UnifiedTopHeaderBar(
                                viewModel = viewModel,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onShowLoadRemoteDialog = { showLoadRemoteConfirmDialog = true }
                            )
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    fadeIn().togetherWith(fadeOut())
                                },
                                label = "TabTransition",
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) { tab ->
                                when (tab) {
                                    "home" -> HomeView(
                                        viewModel, 
                                        onNavigate, 
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onNavigateToAnalysis = { type, start, end ->
                                            analysisInitialType = type
                                            sharedDateRange = Pair(start, end)
                                            selectedTab = "analysis"
                                        },
                                        onTabChange = { selectedTab = it },
                                        onShowLoadRemoteDialog = { showLoadRemoteConfirmDialog = true }
                                    )
                                    "analysis" -> {
                                        AnalysisView(
                                            viewModel, 
                                            onNavigate, 
                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                            initialType = analysisInitialType,
                                            initialDateRange = sharedDateRange
                                        )
                                    }
                                    "transactions" -> TransactionsView(viewModel, onNavigate, onOpenDrawer = { scope.launch { drawerState.open() } })
                                    "budgets" -> BudgetsTab(viewModel, onNavigate, onOpenDrawer = { scope.launch { drawerState.open() } })
                                    "credit_cards" -> CreditCardDashboard(viewModel = viewModel, onBack = {}, isEmbedded = true)
                                    "goals" -> GoalsScreen(viewModel = viewModel, onBack = {}, onNavigate = onNavigate, isEmbedded = true)
                                    "notes" -> NotesScreen(viewModel = viewModel, onBack = {}, isEmbedded = true)
                                    "performance" -> PerformanceScreen(viewModel = viewModel, onBack = {}, isEmbedded = true)
                                    "subscriptions" -> SubscriptionDashboard(viewModel = viewModel, onBack = {}, onNavigate = onNavigate, isEmbedded = true)
                                    "fd" -> FixedDepositsScreen(viewModel = viewModel, onBack = {}, isEmbedded = true)
                                    "summary" -> SummaryScreen(viewModel = viewModel, onBack = {}, isEmbedded = true)
                                    else -> HomeView(
                                        viewModel, 
                                        onNavigate, 
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onNavigateToAnalysis = { type, start, end ->
                                            analysisInitialType = type
                                            sharedDateRange = Pair(start, end)
                                            selectedTab = "analysis"
                                        },
                                        onTabChange = { selectedTab = it },
                                        onShowLoadRemoteDialog = { showLoadRemoteConfirmDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isSearchActive && viewModel.selectedTransactionDetail == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        FloatingBottomNav(
                            selectedTab = selectedTab,
                            onTabChange = { selectedTab = it },
                            onSearchClick = { isSearchActive = true },
                            tabOrder = viewModel.bottomTabOrder
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 72.dp, end = 16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val fabLabel = when (selectedTab) {
                            "budgets" -> stringResource(R.string.btn_add_budget_fab)
                            else -> stringResource(R.string.btn_add_transaction_fab)
                        }
                        
                        val isServerRunning by viewModel.isServerRunning.collectAsState()
                        val snackbarHostState = remember { SnackbarHostState() }
                        val scope = rememberCoroutineScope()
                        val editingDisabledMsg = stringResource(R.string.msg_editing_disabled_server)

                        FloatingActionButton(
                            onClick = { 
                                if (isServerRunning) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(editingDisabledMsg)
                                    }
                                } else {
                                    if (selectedTab == "budgets") onNavigate("add_budget")
                                    else onNavigate("add_transaction")
                                }
                            },
                            containerColor = if (isServerRunning) Color.Gray else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(Icons.Default.Add, fabLabel)
                        }
                        
                        SnackbarHost(hostState = snackbarHostState)
                    }
                }
            }
        }

        if (showLoadRemoteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLoadRemoteConfirmDialog = false },
                title = { Text("Confirm Remote Database Import") },
                text = { Text("This action will replace your existing local database with the database from WebDAV. Are you sure you want to proceed?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLoadRemoteConfirmDialog = false
                            Toast.makeText(context, "Downloading database from WebDAV...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val downloadRes = viewModel.downloadRemoteFileToTempCache()
                                if (downloadRes.isSuccess) {
                                    val tempFile = downloadRes.getOrNull()!!
                                    if (EncryptionService.isEncrypted(tempFile) || tempFile.name.endsWith(".xpt")) {
                                        pendingDownloadedEncryptedFile = tempFile
                                        downloadMasterPasswordInput = ""
                                        downloadDecryptionError = null
                                    } else if (EncryptionService.isValidSQLite(tempFile) || tempFile.name.endsWith(".ftd")) {
                                        if (validateDatabaseSchema(tempFile)) {
                                            if (importFileDirectly(context, tempFile, viewModel)) {
                                                Toast.makeText(context, "Remote database loaded successfully!", Toast.LENGTH_LONG).show()
                                                restartApp(context)
                                            } else {
                                                Toast.makeText(context, "Failed to replace database with remote file", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Downloaded database has invalid schema structure", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Downloaded file is not a valid SQLite database", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    val err = downloadRes.exceptionOrNull()?.message ?: "Download failed"
                                    Toast.makeText(context, "Download failed: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text("Proceed")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLoadRemoteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (pendingDownloadedEncryptedFile != null) {
            val encFile = pendingDownloadedEncryptedFile!!
            AlertDialog(
                onDismissRequest = {
                    encFile.delete()
                    pendingDownloadedEncryptedFile = null
                },
                title = { Text("Encrypted Database Downloaded") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The downloaded database is encrypted at rest (.xpt). Enter Master Password to decrypt and load it.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = downloadMasterPasswordInput,
                            onValueChange = {
                                downloadMasterPasswordInput = it
                                downloadDecryptionError = null
                            },
                            label = { Text("Master Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        downloadDecryptionError?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val decryptedFile = File(context.cacheDir, "decrypted_remote_import.db")
                            scope.launch {
                                val decRes = withContext(Dispatchers.IO) {
                                    val passChars = downloadMasterPasswordInput.toCharArray()
                                    val res = EncryptionService.decryptFile(encFile, decryptedFile, passChars)
                                    passChars.fill('\u0000')
                                    res
                                }

                                if (decRes.isSuccess && validateDatabaseSchema(decryptedFile)) {
                                    if (importFileDirectly(context, decryptedFile, viewModel, downloadMasterPasswordInput)) {
                                        Toast.makeText(context, "Remote database decrypted and loaded successfully!", Toast.LENGTH_LONG).show()
                                        pendingDownloadedEncryptedFile?.delete()
                                        pendingDownloadedEncryptedFile = null
                                        restartApp(context)
                                    } else {
                                        downloadDecryptionError = "Failed to replace database file"
                                    }
                                } else {
                                    val err = decRes.exceptionOrNull()?.message ?: "Incorrect Master Password"
                                    downloadDecryptionError = err
                                }
                            }
                        },
                        enabled = downloadMasterPasswordInput.isNotEmpty()
                    ) {
                        Text("Decrypt & Load")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            encFile.delete()
                            pendingDownloadedEncryptedFile = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun FloatingBottomNav(
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    tabOrder: List<String>
) {
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .height(60.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            tabOrder.forEach { tabKey ->
                val isSelected = selectedTab == tabKey
                val (label, icon) = when (tabKey) {
                    "home" -> stringResource(R.string.menu_home) to Icons.Default.Home
                    "analysis" -> stringResource(R.string.menu_analysis) to Icons.Default.PieChart
                    "transactions" -> stringResource(R.string.menu_entries) to Icons.Default.List
                    "budgets" -> stringResource(R.string.menu_budgets) to Icons.Default.AccountBalanceWallet
                    "credit_cards" -> stringResource(R.string.menu_credit_cards) to Icons.Default.CreditCard
                    "goals" -> stringResource(R.string.goal_title_financial_goals) to Icons.Default.Star
                    "notes" -> stringResource(R.string.menu_notes) to Icons.Default.Description
                    "performance" -> stringResource(R.string.menu_performance) to Icons.Default.TrendingUp
                    "subscriptions" -> stringResource(R.string.menu_subscriptions) to Icons.Default.Repeat
                    "fd" -> stringResource(R.string.menu_fixed_deposits) to Icons.Default.AccountBalance
                    "summary" -> stringResource(R.string.menu_summary) to Icons.Default.Assessment
                    else -> tabKey to Icons.Default.Home
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onTabChange(tabKey) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.btn_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    viewModel: ExpenseViewModel, 
    onNavigate: (String) -> Unit, 
    onOpenDrawer: () -> Unit,
    onNavigateToAnalysis: (String, String, String) -> Unit,
    onTabChange: (String) -> Unit,
    onShowLoadRemoteDialog: () -> Unit = {}
) {
    val refreshTrigger by viewModel.refreshTrigger.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var startDate by remember(refreshTrigger) { mutableStateOf(LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember(refreshTrigger) { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    
    val s = LocalDate.parse(startDate)
    val e = LocalDate.parse(endDate)
    val prevStart = s.minusMonths(1).format(DateTimeFormatter.ISO_DATE)
    val prevEnd = e.minusMonths(1).format(DateTimeFormatter.ISO_DATE)
    val prevTransactions by viewModel.getFilteredTransactions(prevStart, prevEnd).collectAsState(initial = emptyList())
    val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())

    var showTransactionListType by remember { mutableStateOf<String?>(null) }
    var showDiscretionaryOnly by remember { mutableStateOf(false) }

    val expenseLabel = stringResource(R.string.label_expense)
    val incomeLabel = stringResource(R.string.label_income)

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showTransactionListType != null || showDiscretionaryOnly) {
        BackHandler { 
            showTransactionListType = null
            showDiscretionaryOnly = false
        }
        TransactionListOverlay(
            title = if (showDiscretionaryOnly) stringResource(R.string.label_discretionary_spending) else if (showTransactionListType == "expense") expenseLabel else incomeLabel,
            transactions = if (showDiscretionaryOnly) transactions.filter { it.transaction.isDiscretionary } else transactions.filter { it.categoryType == showTransactionListType },
            viewModel = viewModel,
            onBack = { 
                showTransactionListType = null
                showDiscretionaryOnly = false
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)) {
            val ccAlerts by viewModel.getCcAlerts().collectAsState(initial = emptyList())
            val subAlerts by viewModel.getSubscriptionAlerts().collectAsState(initial = emptyList())
            val fdAlerts by viewModel.getFdMaturityAlerts().collectAsState(initial = emptyList())

            ccAlerts.forEach { alert ->
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> offsetX += delta },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.toggleCcPaid(alert.accountId, true)
                                }
                                offsetX = 0f
                            }
                        ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${alert.accountName} " + stringResource(R.string.label_due_colon) + alert.dueDate, style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.label_amt_payable_colon) + viewModel.formatAmount(kotlin.math.abs(alert.amount)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            subAlerts.forEach { alert ->
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> offsetX += delta },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.toggleCcPaidCustom("SUB_${alert.subName}", true)
                                }
                                offsetX = 0f
                            }
                        ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (alert.isTransfer) Icons.Default.SwapHoriz else Icons.Default.CardMembership, 
                            null, 
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${alert.subName} " + stringResource(R.string.label_due_colon) + alert.dueDate, style = MaterialTheme.typography.labelLarge)
                            Text(
                                (if(alert.isTransfer) stringResource(R.string.label_recurring_transfers) + ": " else stringResource(R.string.label_total_due) + ": ") + viewModel.formatAmount(alert.amount), 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            fdAlerts.forEach { alert ->
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> offsetX += delta },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.toggleCcPaidCustom("FD_${alert.accountId}", true)
                                }
                                offsetX = 0f
                            }
                        ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${alert.accountName} Matures: ${alert.maturityDate}", style = MaterialTheme.typography.labelLarge)
                            Text("Balance: " + viewModel.formatAmount(alert.balance), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (viewModel.draftTransaction != null) {
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                offsetX += delta
                            },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.draftTransaction = null
                                }
                                offsetX = 0f
                            }
                        )
                        .clickable { onNavigate("add_transaction") },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Drafts, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.msg_draft_pending), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val hour = java.time.LocalTime.now().hour
                val greetingRes = when (hour) {
                    in 5..11 -> R.string.greeting_good_morning
                    in 12..16 -> R.string.greeting_good_afternoon
                    else -> R.string.greeting_good_evening
                }
                Text(
                    text = stringResource(greetingRes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val smsDrafts by viewModel.smsDrafts.collectAsState(initial = emptyList())
                    if (smsDrafts.isNotEmpty()) {
                        IconButton(onClick = { onNavigate("sms_inbox") }) {
                            BadgedBox(
                                badge = { Badge { Text(smsDrafts.size.toString()) } }
                            ) {
                                Icon(Icons.Default.Sms, stringResource(R.string.title_sms_inbox))
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.generateFinancialInsights() }) {
                        if (viewModel.isGeneratingInsights) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                stringResource(R.string.label_insights),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.DateRange, stringResource(R.string.label_filter))
                    }
                }
            }

            if (viewModel.showInsightsOverlay && viewModel.financialInsights.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.label_insights), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                viewModel.financialInsights.forEach { insight ->
                    var offsetX by remember { mutableStateOf(0f) }
                    val backgroundColor = when(insight.type) {
                        com.openapps.fintrack.data.InsightType.WARNING -> MaterialTheme.colorScheme.errorContainer
                        com.openapps.fintrack.data.InsightType.ANOMALY -> MaterialTheme.colorScheme.tertiaryContainer
                        com.openapps.fintrack.data.InsightType.TREND -> MaterialTheme.colorScheme.secondaryContainer
                        com.openapps.fintrack.data.InsightType.OPPORTUNITY -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val icon = when(insight.type) {
                        com.openapps.fintrack.data.InsightType.WARNING -> Icons.Default.Warning
                        com.openapps.fintrack.data.InsightType.ANOMALY -> Icons.Default.ErrorOutline
                        com.openapps.fintrack.data.InsightType.TREND -> Icons.Default.TrendingUp
                        com.openapps.fintrack.data.InsightType.OPPORTUNITY -> Icons.Default.Lightbulb
                    }

                    Surface(
                        color = backgroundColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta -> offsetX += delta },
                                onDragStopped = {
                                    if (kotlin.math.abs(offsetX) > 300) {
                                        viewModel.dismissInsight(insight)
                                    }
                                    offsetX = 0f
                                }
                            ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(insight.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(insight.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { viewModel.pauseInsight(insight.id) },
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.btn_pause_7_days), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (viewModel.financialInsights.isEmpty()) {
                    viewModel.showInsightsOverlay = false
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            val curInc = transactions.filter { it.categoryType?.lowercase() == "income" }.sumOf { it.transaction.amount }
            val curExp = transactions.filter { it.categoryType?.lowercase() == "expense" }.sumOf { it.transaction.amount }
            val prevInc = prevTransactions.filter { it.categoryType?.lowercase() == "income" }.sumOf { it.transaction.amount }
            val prevExp = prevTransactions.filter { it.categoryType?.lowercase() == "expense" }.sumOf { it.transaction.amount }

            val context = LocalContext.current
            val homeBackgrounds = remember {
                val list = mutableListOf<Int>()
                list.add(0) 
                list.add(R.drawable.bg_summary_card) 
                for (i in 1..10) {
                    val id = context.resources.getIdentifier("bg_home_$i", "drawable", context.packageName)
                    if (id != 0) list.add(id)
                }
                list
            }
            val currentBg = homeBackgrounds.getOrElse(viewModel.homeBackgroundIndex) { homeBackgrounds[0] }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                if (currentBg != 0) {
                    Image(
                        painter = painterResource(id = currentBg),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                val contentBg = if (currentBg == 0) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp) else Color.Black.copy(alpha = 0.4f)
                val textColor = if (currentBg == 0) MaterialTheme.colorScheme.onSurface else Color.White
                val secondaryTextColor = if (currentBg == 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.7f)
                val dividerColor = if (currentBg == 0) MaterialTheme.colorScheme.outlineVariant else Color.White.copy(alpha = 0.2f)

                Column(modifier = Modifier
                    .background(contentBg)
                    .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val periodRangeText = try {
                            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                            "${s.format(formatter)} - ${e.format(formatter)}"
                        } catch (_: Exception) {
                            "$startDate - $endDate"
                        }
                        Text(
                            text = periodRangeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        IconButton(
                            onClick = { 
                                val next = (viewModel.homeBackgroundIndex + 1) % homeBackgrounds.size
                                viewModel.updateHomeBackgroundIndex(next)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (currentBg == 0) Icons.Default.Image else Icons.Default.Wallpaper, 
                                contentDescription = null, 
                                tint = if (currentBg == 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Column {
                        Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "expense" }) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.label_expense), style = MaterialTheme.typography.labelMedium, color = if (currentBg == 0) Color.Red else Color(0xFFFF8A80))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(viewModel.formatAmount(curExp), style = MaterialTheme.typography.titleLarge, color = if (currentBg == 0) Color.Red else Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                    if (curExp > prevExp) {
                                        Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = if (currentBg == 0) Color.Red else Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold)
                                    } else if (curExp < prevExp) {
                                        Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = if (currentBg == 0) Color.Red else Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            Text(stringResource(R.string.label_prev_month_colon) + viewModel.formatAmount(prevExp), style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor)
                        }

                        Spacer(Modifier.height(16.dp))

                        Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "income" }) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.label_income), style = MaterialTheme.typography.labelMedium, color = if (currentBg == 0) Color(0xFF4CAF50) else Color(0xFFA5D6A7))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(viewModel.formatAmount(curInc), style = MaterialTheme.typography.titleLarge, color = if (currentBg == 0) Color(0xFF4CAF50) else Color(0xFF66BB6A), fontWeight = FontWeight.Bold)
                                    if (curInc > prevInc) {
                                        Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = if (currentBg == 0) Color(0xFF4CAF50) else Color(0xFF66BB6A), fontWeight = FontWeight.ExtraBold)
                                    } else if (curInc < prevInc) {
                                        Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = if (currentBg == 0) Color(0xFF4CAF50) else Color(0xFF66BB6A), fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            Text(stringResource(R.string.label_prev_month_colon) + viewModel.formatAmount(prevInc), style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor)
                        }

                        Spacer(Modifier.height(8.dp))
                        Divider(color = dividerColor)
                        Spacer(Modifier.height(8.dp))
                        
                        val netValue = curInc - curExp
                        val netLabel = if (netValue >= 0) "Net Income" else "Net Expense"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(netLabel, fontWeight = FontWeight.Bold, color = textColor)
                            Text(viewModel.formatAmount(netValue), fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }

                    if (viewModel.negotiationTrackerEnabled) {
                        val totalSavings = transactions.sumOf { (it.transaction.negotiationAmountOriginal ?: it.transaction.amount) - it.transaction.amount }
                        if (totalSavings > 0) {
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_negotiated_savings), fontWeight = FontWeight.Bold, color = if (currentBg == 0) Color(0xFF4CAF50) else Color(0xFFA5D6A7))
                                Text(viewModel.formatAmount(totalSavings), fontWeight = FontWeight.Bold, color = if (currentBg == 0) Color(0xFF4CAF50) else Color(0xFFA5D6A7))
                            }
                        }
                    }

                    if (viewModel.discretionarySpendingTrackerEnabled) {
                        val discTotal = transactions.filter { it.transaction.isDiscretionary }.sumOf { it.transaction.amount }
                        if (discTotal > 0) {
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth().clickable { showDiscretionaryOnly = true }, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_discretionary_spend_title), fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.9f))
                                Text(viewModel.formatAmount(discTotal), fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.9f))
                            }
                        }
                    }

                    val netPosition = balances.sumOf { it.balance }
                    var isNetPositionVisible by remember { mutableStateOf(!viewModel.tapToShowNetPosition) }
                    
                    Spacer(Modifier.height(12.dp))
                    Divider(color = dividerColor)
                    Spacer(Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.label_net_position), 
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.clickable {
                                viewModel.summaryInitialTab = "Assets"
                                onNavigate("summary")
                            }
                        )
                        Text(
                            if (isNetPositionVisible) viewModel.formatAmount(netPosition) else "****",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.clickable {
                                isNetPositionVisible = !isNetPositionVisible
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Text(stringResource(R.string.label_top_expenses), style = MaterialTheme.typography.titleMedium)
            val topExpenses = transactions.filter { it.categoryType?.lowercase() == "expense" }
                .groupBy { it.categoryName ?: stringResource(R.string.label_uncategorized) }
                .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(4)
            
            if (topExpenses.isNotEmpty()) {
                Box(modifier = Modifier.clickable { onNavigateToAnalysis("Expense", startDate, endDate) }) {
                    ExpensePieChart(topExpenses, viewModel)
                }
            } else {
                Text(stringResource(R.string.label_no_expenses_period), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            if (viewModel.dashboardAccountIds.isNotEmpty()) {
                val selectedBalances = balances.filter { it.id in viewModel.dashboardAccountIds }
                if (selectedBalances.isNotEmpty()) {
                    Text(stringResource(R.string.label_dashboard_accounts), style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clickable {
                                viewModel.summaryInitialTab = "Assets"
                                onNavigate("summary")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        HorizontalBalanceChart(accounts = selectedBalances, viewModel = viewModel)
                    }
                }
            }

            if (viewModel.dashboardBudgetIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val budgetVsActual by viewModel.getBudgetVsActual(endDate).collectAsState(initial = emptyList())
                val selectedBudgetsRaw by viewModel.getAllBudgetsDetailed().collectAsState(initial = emptyList())
                
                val selectedBudgets = remember(selectedBudgetsRaw, budgetVsActual, viewModel.dashboardBudgetIds) {
                    selectedBudgetsRaw.filter { it.budget.id in viewModel.dashboardBudgetIds }
                        .sortedBy { bdr ->
                            val performance = budgetVsActual.find { it.budgetId == bdr.budget.id }
                            val actual = performance?.actualAmount ?: 0.0
                            val limit = bdr.budget.amount
                            val isGoalMet = if (bdr.budget.higherIsBetter) actual >= limit else actual <= limit
                            if (isGoalMet) 1 else 0
                        }
                }

                if (selectedBudgets.isNotEmpty()) {
                    Text(stringResource(R.string.label_budget_performance), style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedBudgets) { budgetWithRelations ->
                            val budget = budgetWithRelations.budget
                            val performance = budgetVsActual.find { it.budgetId == budget.id }
                            val actual = performance?.actualAmount ?: 0.0
                            val limit = budget.amount
                            val percent = if (limit != 0.0) (actual / limit * 100).toInt() else 0
                            
                            val isGoalMet = if (budget.higherIsBetter) {
                                actual >= limit
                            } else {
                                actual <= limit
                            }
                            val statusColor = if (isGoalMet) Color(0xFF4CAF50) else Color.Red
                            val catNames = budgetWithRelations.categories.joinToString(", ") { it.name }

                            Card(
                                modifier = Modifier.width(180.dp).clickable { onTabChange("budgets") }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(budget.name ?: catNames, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("$percent%", style = MaterialTheme.typography.titleMedium, color = statusColor, fontWeight = FontWeight.Bold)
                                    LinearProgressIndicator(
                                        progress = (actual / limit).toFloat().coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        color = statusColor,
                                        trackColor = statusColor.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showFilter) {
        DateRangeFilterDialog(
            onDismiss = { showFilter = false },
            onApply = { start, end ->
                startDate = start
                endDate = end
                showFilter = false
            }
        )
    }
}

@Composable
fun SummaryColumn(label: String, current: Double, previous: Double, color: Color, viewModel: ExpenseViewModel, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable { onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(viewModel.formatAmount(current), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            if (current > previous) {
                Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.ExtraBold)
            } else if (current < previous) {
                Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(stringResource(R.string.label_prev_colon) + viewModel.formatAmount(previous), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
fun ExpensePieChart(expenses: List<Pair<String, Double>>, viewModel: ExpenseViewModel) {
    val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow)
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PieChart(data = expenses, colors = colors)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            expenses.forEachIndexed { index, pair ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.size(12.dp).background(colors[index % colors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text(pair.first, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    Text(viewModel.formatAmount(pair.second), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisView(
    viewModel: ExpenseViewModel, 
    onNavigate: (String) -> Unit, 
    onOpenDrawer: () -> Unit,
    initialType: String? = null,
    initialDateRange: Pair<String, String>? = null
) {
    val expenseLabel = stringResource(R.string.label_expense)
    val incomeLabel = stringResource(R.string.label_income)
    val accountLabel = stringResource(R.string.label_account)
    val tagsLabel = stringResource(R.string.label_tags)
    val merchantsLabel = stringResource(R.string.label_merchants)
    val networthLabel = stringResource(R.string.label_networth)
    val onAccountLoanLabel = stringResource(R.string.label_on_account_loan)

    var type by remember { mutableStateOf(when(initialType) {
        "Income" -> "Income"
        "Account" -> "Account"
        "Tags" -> "Tags"
        "Merchants" -> "Merchants"
        "Networth" -> "Networth"
        "OnAccountLoan" -> "OnAccountLoan"
        else -> "Expense"
    }) }
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(initialDateRange?.first ?: month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(initialDateRange?.second ?: month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    var categoryFilterIds by remember { mutableStateOf<Set<Int>?>(null) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var showAccountSelectionDialog by remember { mutableStateOf(false) }
    var isMainLevelAnalysis by remember { mutableStateOf(false) }
    
    var selectedAnalysisPartyIds = remember { mutableStateListOf<Int>() }
    
    var invoiceStatusFilter by remember { mutableStateOf("Open") }
    
    var filterByParty by remember { mutableStateOf(false) }
    var filterByOverdue by remember { mutableStateOf(false) }
    var filterByAmount by remember { mutableStateOf(false) }
    var minAmtFilter by remember { mutableStateOf("") }
    var maxAmtFilter by remember { mutableStateOf("") }
    var filterByDueInXDays by remember { mutableStateOf(false) }
    var dueInXDaysFilter by remember { mutableStateOf("") }
    var filterByWasDueYDays by remember { mutableStateOf(false) }
    var wasDueYDaysFilter by remember { mutableStateOf("") }
    var showAdvancedFilterDialog by remember { mutableStateOf(false) }
    var tagFilterIds by remember { mutableStateOf<Set<Int>?>(null) }
    var groupByParty by remember { mutableStateOf(false) }

    var accountsSubTab by remember { mutableStateOf("Balance") }
    var isTagBarChartView by remember { mutableStateOf(false) }
    var analysisSelectedAccountId by remember { mutableStateOf<Int?>(null) }
    
    val allTransactionsList by viewModel.allTransactions.collectAsState(initial = emptyList())
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
    val tags by viewModel.getAllTags().collectAsState(initial = emptyList())
    val allAccountsList by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allCategories by viewModel.getEnabledCategories().collectAsState(initial = emptyList())

    val allInvoices by viewModel.getInvoicesForParties(allAccountsList.filter { a -> minorHeads.find { it.id == a.minorHeadId }?.majorHeadId == (majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6) }.map { it.id }, endDate).collectAsState(initial = emptyList())

    val filteredInvoices = remember(allInvoices, selectedAnalysisPartyIds.toList(), filterByParty, filterByOverdue, filterByAmount, minAmtFilter, maxAmtFilter, filterByDueInXDays, dueInXDaysFilter, filterByWasDueYDays, wasDueYDaysFilter, invoiceStatusFilter, categoryFilterIds, tagFilterIds) {
        allInvoices.filter { inv ->
            val outstanding = inv.detail.transaction.amount - inv.totalCleared
            val isOverdue = try { 
                val dueDate = LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0)
                dueDate.isBefore(LocalDate.now()) && outstanding > 0
            } catch(e: Exception) { false }

            val statusMatch = when(invoiceStatusFilter) {
                "Open" -> outstanding > 0
                "Cleared" -> outstanding <= 0
                else -> true
            }
            if (!statusMatch) return@filter false

            if (filterByParty && selectedAnalysisPartyIds.isNotEmpty()) {
                val partyId = inv.detail.transaction.partyId ?: inv.detail.transaction.accountId
                if (partyId !in selectedAnalysisPartyIds) return@filter false
            }
            if (filterByOverdue && !isOverdue) return@filter false
            
            if (filterByAmount) {
                val min = minAmtFilter.toDoubleOrNull() ?: 0.0
                val max = maxAmtFilter.toDoubleOrNull() ?: Double.MAX_VALUE
                if (inv.detail.transaction.amount < min || inv.detail.transaction.amount > max) return@filter false
            }

            if (filterByDueInXDays) {
                val days = dueInXDaysFilter.toLongOrNull() ?: 0L
                try {
                    val dueDate = LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0)
                    val diff = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
                    if (diff < 0 || diff > days) return@filter false
                } catch(e: Exception) { return@filter false }
            }

            if (filterByWasDueYDays) {
                val days = wasDueYDaysFilter.toLongOrNull() ?: 0L
                try {
                    val dueDate = LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0)
                    val diff = ChronoUnit.DAYS.between(dueDate, LocalDate.now())
                    if (diff < 0 || diff > days) return@filter false
                } catch(e: Exception) { return@filter false }
            }

            if (categoryFilterIds != null && inv.detail.transaction.categoryId !in categoryFilterIds!!) return@filter false
            
            if (tagFilterIds != null) {
                val txnTags = inv.detail.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                if (txnTags.none { it in tagFilterIds!! }) return@filter false
            }

            true
        }
    }

    LaunchedEffect(allAccountsList, type) {
        if (type == "Account") {
            if (analysisSelectedAccountId == null && allAccountsList.isNotEmpty()) {
                analysisSelectedAccountId = allAccountsList.first().id
            }
        } else if (type != "Networth" && type != "OnAccountLoan") {
            analysisSelectedAccountId = null
        }
    }

    var showDetailList by remember { mutableStateOf<String?>(null) }
    var showTagDetailList by remember { mutableStateOf<Int?>(null) }
    
    val allAccountsLabel = stringResource(R.string.label_all) + " " + stringResource(R.string.menu_accounts)

    val uncategorizedLabel = stringResource(R.string.label_uncategorized)

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showDetailList != null) {
        BackHandler { showDetailList = null }
        val filteredList = remember(transactions, type, accountsSubTab, analysisSelectedAccountId, showDetailList, isMainLevelAnalysis) {
            if (type == "Account") {
            
                if (accountsSubTab == "Spending") {
                    transactions.filter { it.transaction.accountId == analysisSelectedAccountId && it.categoryName == showDetailList }
                } else if (accountsSubTab == "Source") {
                    transactions.filter { 
                        it.transaction.categoryId == null &&
                        ((it.transaction.accountId == analysisSelectedAccountId && it.toAccountName == showDetailList) || 
                         (it.transaction.toAccountId == analysisSelectedAccountId && it.accountName == showDetailList))
                    }
                } else {
                    transactions.filter { it.accountName == showDetailList || it.toAccountName == showDetailList }
                }
            } else if (type == "Expense" || type == "Income") {
            
                transactions.filter { 
                    val catName = it.categoryName ?: uncategorizedLabel
                    if (isMainLevelAnalysis) {
                        val mainPart = if (catName.contains(":")) catName.split(":").first().trim() else catName
                        mainPart == showDetailList
                    } else {
                        val minorPart = if (catName.contains(":")) catName.split(":").last().trim() else catName
                        minorPart == showDetailList
                    }
                }
            } else if (showDetailList!!.startsWith("MERCHANT:")) {
                val merchant = showDetailList!!.removePrefix("MERCHANT:")
                transactions.filter { it.transaction.merchantName == merchant }
            } else {
                transactions.filter { it.accountName == showDetailList || it.toAccountName == showDetailList }
            }
        }
        TransactionListOverlay(
            title = showDetailList!!,
            transactions = filteredList,
            viewModel = viewModel,
            onBack = { showDetailList = null }
        )
    } else if (showTagDetailList != null) {
        BackHandler { showTagDetailList = null }
        val tagName = tags.find { it.id == showTagDetailList }?.name ?: "Tag"
        TransactionListOverlay(
            title = tagName,
            transactions = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(showTagDetailList.toString()) == true },
            viewModel = viewModel,
            onBack = { showTagDetailList = null }
        )
    } else {
        val chartColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan, Color.Gray, Color.DarkGray, Color.LightGray)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        val currentTypeLabel = when(type) {
                            "Expense" -> expenseLabel
                            "Income" -> incomeLabel
                            "Account" -> accountLabel
                            "Tags" -> tagsLabel
                            "Merchants" -> merchantsLabel
                            "Networth" -> networthLabel
                            "OnAccountLoan" -> onAccountLoanLabel
                            else -> type
                        }
                        TextButton(onClick = { expanded = true }) {
                            Text(currentTypeLabel, style = MaterialTheme.typography.titleLarge)
                            Icon(Icons.Default.ArrowDropDown, "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(expenseLabel) }, onClick = { type = "Expense"; expanded = false })
                            DropdownMenuItem(text = { Text(incomeLabel) }, onClick = { type = "Income"; expanded = false })
                            DropdownMenuItem(text = { Text(accountLabel) }, onClick = { type = "Account"; expanded = false })
                            DropdownMenuItem(text = { Text(tagsLabel) }, onClick = { type = "Tags"; expanded = false })
                            DropdownMenuItem(text = { Text(merchantsLabel) }, onClick = { type = "Merchants"; expanded = false })
                            DropdownMenuItem(text = { Text(networthLabel) }, onClick = { type = "Networth"; expanded = false })
                            DropdownMenuItem(text = { Text(onAccountLoanLabel) }, onClick = { type = "OnAccountLoan"; expanded = false })
                        }
                    }
                    if (type == "Expense" || type == "Income") {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { showCategoryFilterDialog = true }) {
                            Icon(
                                Icons.Default.FilterAlt, 
                                null, 
                                modifier = Modifier.size(20.dp),
                                tint = if (categoryFilterIds != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { isMainLevelAnalysis = !isMainLevelAnalysis }) {
                            Icon(
                                if (isMainLevelAnalysis) Icons.Default.Layers else Icons.Default.LayersClear,
                                contentDescription = if (isMainLevelAnalysis) stringResource(R.string.label_main_level) else stringResource(R.string.label_minor_level),
                                modifier = Modifier.size(20.dp),
                                tint = if (isMainLevelAnalysis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (type == "Account" && accountsSubTab != "Balance") {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { showAccountSelectionDialog = true }) {
                            Icon(
                                Icons.Default.FilterAlt,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = if (analysisSelectedAccountId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (type == "Tags") {
                        IconButton(onClick = { isTagBarChartView = !isTagBarChartView }) {
                            Icon(if (isTagBarChartView) Icons.Default.PieChart else Icons.Default.BarChart, stringResource(R.string.label_toggle_chart))
                        }
                    }
                    
                    if (type == "OnAccountLoan" && viewModel.invoiceAgeTrackingEnabled) {
                        IconButton(onClick = { showAdvancedFilterDialog = true }) {
                            val hasActiveFilter = filterByParty || filterByOverdue || filterByAmount || filterByDueInXDays || filterByWasDueYDays || categoryFilterIds != null || tagFilterIds != null || groupByParty
                            Icon(
                                Icons.Default.FilterList, 
                                contentDescription = stringResource(R.string.label_advanced_filters),
                                tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = { showFilter = true }) { 
                        Icon(if (type == "OnAccountLoan") Icons.Default.DateRange else Icons.Default.FilterList, stringResource(R.string.label_filter)) 
                    }
                }
            }
                
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 0.dp, end = 4.dp, bottom = 0.dp)) {
                IconButton(onClick = {
                    month = month.minusYears(1)
                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                }) {
                    Icon(Icons.Default.KeyboardDoubleArrowLeft, contentDescription = "Prev Year")
                }
                IconButton(onClick = {
                    month = month.minusMonths(1)
                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month")
                }

                var monthMenuExpanded by remember { mutableStateOf(false) }
                var yearMenuExpanded by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { monthMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        val locale = Locale.getDefault()
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM", locale)))
                        Icon(Icons.Default.ArrowDropDown, "")
                    }
                    DropdownMenu(expanded = monthMenuExpanded, onDismissRequest = { monthMenuExpanded = false }) {
                        val locale = Locale.getDefault()
                        (1..12).forEach { m ->
                            val mObj = Month.of(m)
                            DropdownMenuItem(
                                text = { Text(mObj.getDisplayName(java.time.format.TextStyle.FULL, locale)) },
                                onClick = {
                                    month = month.withMonth(m)
                                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                                    monthMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { yearMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(month.year.toString())
                        Icon(Icons.Default.ArrowDropDown, "")
                    }
                    DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                        val currentYear = LocalDate.now().year
                        (currentYear - 5..currentYear + 5).forEach { y ->
                            DropdownMenuItem(
                                text = { Text(y.toString()) },
                                onClick = {
                                    month = month.withYear(y)
                                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                                    yearMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = {
                    month = month.plusMonths(1)
                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
                IconButton(onClick = {
                    month = month.plusYears(1)
                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                }) {
                    Icon(Icons.Default.KeyboardDoubleArrowRight, contentDescription = "Next Year")
                }
            }

            val bTrendData = remember(allTransactionsList, analysisSelectedAccountId, startDate, endDate, accountsSubTab, type) {
                if (type == "Account" && accountsSubTab == "BTrend" && analysisSelectedAccountId != null) {
                    val start = LocalDate.parse(startDate)
                    val end = LocalDate.parse(endDate)
                    val accountId = analysisSelectedAccountId!!
                    
                    val accountObj = allAccountsList.find { it.id == accountId }
                    val initialBalance: Long = accountObj?.openingBalanceMinorUnits ?: (accountObj?.openingBalance?.let { (it * 100).toLong() } ?: 0L)
                    
                    val txnsBefore = allTransactionsList.filter { 
                        it.transaction.date < startDate && 
                        (it.transaction.accountId == accountId || it.transaction.toAccountId == accountId)
                    }
                    
                    var mCalculatedBalance: Long = initialBalance
                    txnsBefore.forEach { t ->
                        val amt: Long = t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong()
                        if (t.transaction.toAccountId == accountId) mCalculatedBalance = mCalculatedBalance + amt
                        else if (t.transaction.accountId == accountId) {
                            if (t.categoryType == "income") mCalculatedBalance = mCalculatedBalance + amt else mCalculatedBalance = mCalculatedBalance - amt
                        }
                    }
                    
                    val daysInMonth = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
                    val trend = mutableListOf<Pair<String, Long>>()
                    
                    val monthTxns = allTransactionsList.filter { 
                        it.transaction.date >= startDate && it.transaction.date <= endDate &&
                        (it.transaction.accountId == accountId || it.transaction.toAccountId == accountId)
                    }
                    
                    for (day in 0 until daysInMonth) {
                        val d = start.plusDays(day.toLong())
                        val dStr = d.format(DateTimeFormatter.ISO_DATE)
                        val dayTxns = monthTxns.filter { it.transaction.date == dStr }
                        
                        var dayChange: Long = 0L
                        dayTxns.forEach { t ->
                            val amt: Long = t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong()
                            if (t.transaction.toAccountId == accountId) dayChange = dayChange + amt
                            else if (t.transaction.accountId == accountId) {
                                if (t.categoryType == "income") dayChange = dayChange + amt else dayChange = dayChange - amt
                            }
                        }
                        mCalculatedBalance = mCalculatedBalance + dayChange
                        trend.add(d.format(DateTimeFormatter.ofPattern("dd MMM")) to mCalculatedBalance)
                    }
                    trend
                } else if (type == "Networth") {
                    val start = LocalDate.parse(startDate)
                    val end = LocalDate.parse(endDate)
                    
                    val txnsBefore = allTransactionsList.filter { it.transaction.date < startDate }
                    val initialNetPosition: Long = allAccountsList.sumOf { it.openingBalanceMinorUnits ?: (it.openingBalance * 100).toLong() }
                    
                    var mCalculatedNetPosition: Long = initialNetPosition
                    txnsBefore.forEach { t ->
                        val amt: Long = t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong()
                        if (t.transaction.toAccountId == null) {
                            if (t.categoryType == "income") mCalculatedNetPosition = mCalculatedNetPosition + amt 
                            else if (t.categoryType == "expense") mCalculatedNetPosition = mCalculatedNetPosition - amt 
                        }
                    }
                    
                    val daysInMonth = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
                    val trend = mutableListOf<Pair<String, Long>>()
                    val monthTxns = allTransactionsList.filter { it.transaction.date >= startDate && it.transaction.date <= endDate }
                    
                    for (day in 0 until daysInMonth) {
                        val d = start.plusDays(day.toLong())
                        val dStr = d.format(DateTimeFormatter.ISO_DATE)
                        val dayTxns = monthTxns.filter { it.transaction.date == dStr }
                        
                        var dayChange: Long = 0L
                        dayTxns.forEach { t ->
                            val amt: Long = t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong()
                            if (t.transaction.toAccountId == null) {
                                if (t.categoryType == "income") dayChange = dayChange + amt 
                                else if (t.categoryType == "expense") dayChange = dayChange - amt 
                            }
                        }
                        mCalculatedNetPosition = mCalculatedNetPosition + dayChange
                        trend.add(d.format(DateTimeFormatter.ofPattern("dd MMM")) to mCalculatedNetPosition)
                    }
                    trend
                } else emptyList<Pair<String, Long>>()
            }

            val uncategorizedLabel = stringResource(R.string.label_uncategorized)
            val data: List<Pair<String, Long>> = when (type) {
                "Expense", "Income" -> {
                    val targetType = if (type == "Expense") "expense" else "income"
                    val filteredTxns = transactions.filter { 
                        it.categoryType?.lowercase() == targetType && 
                        (categoryFilterIds == null || it.transaction.categoryId in categoryFilterIds!!) &&
                        (analysisSelectedAccountId == null || it.transaction.accountId == analysisSelectedAccountId || it.transaction.toAccountId == analysisSelectedAccountId)
                    }
                    
                    if (isMainLevelAnalysis) {
                        filteredTxns.groupBy { 
                            val name = it.categoryName ?: uncategorizedLabel
                            if (name.contains(":")) name.split(":").first().trim() else name
                        }.mapValues { it.value.sumOf { t -> t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong() } }
                        .toList().sortedByDescending { it.second }
                    } else {
                        filteredTxns.groupBy { 
                            val name = it.categoryName ?: uncategorizedLabel
                            if (name.contains(":")) name.split(":").last().trim() else name
                        }.mapValues { it.value.sumOf { t -> t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong() } }
                        .toList().sortedByDescending { it.second }
                    }
                }
                "Account" -> {
                    if (accountsSubTab == "Balance") {
                        val onAccountLoanId = majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6
                        balances.filter { b ->
                            val minor = minorHeads.find { it.id == b.minorHeadId }
                            minor?.majorHeadId != onAccountLoanId && !b.name.equals("On Account", ignoreCase = true)
                        }.map { it.name to it.balance }.sortedByDescending { it.second }
                    } else if (accountsSubTab == "BTrend") {
                        bTrendData
                    } else if (accountsSubTab == "Spending" && analysisSelectedAccountId != null) {
                        transactions.filter { it.transaction.accountId == analysisSelectedAccountId && it.transaction.categoryId != null }
                            .groupBy { it.categoryName ?: uncategorizedLabel }
                            .mapValues { entry -> 
                                entry.value.sumOf { 
                                    val amt = it.transaction.amountMinorUnits ?: (it.transaction.amount * 100).toLong()
                                    if (it.categoryType == "income") amt else -amt 
                                } 
                            }
                            .toList().sortedByDescending { kotlin.math.abs(it.second) }
                    } else if (accountsSubTab == "Source" && analysisSelectedAccountId != null) {
                        transactions.filter { (it.transaction.accountId == analysisSelectedAccountId || it.transaction.toAccountId == analysisSelectedAccountId) && it.transaction.categoryId == null }
                            .groupBy { if (it.transaction.accountId == analysisSelectedAccountId) it.toAccountName ?: stringResource(R.string.label_other) else it.accountName }
                            .mapValues { entry ->
                                entry.value.sumOf {
                                    val amt = it.transaction.amountMinorUnits ?: (it.transaction.amount * 100).toLong()
                                    if (it.transaction.toAccountId == analysisSelectedAccountId) amt
                                    else -amt
                                }
                            }
                            .toList().sortedByDescending { kotlin.math.abs(it.second) }
                    } else {
                        emptyList()
                    }
                }
                "Tags" -> tags.map { tag ->
                    val total = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(tag.id.toString()) == true }.sumOf { t -> t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong() }
                    tag.name to total
                }.filter { it.second > 0 }.sortedByDescending { it.second }
                "Merchants" -> {
                    transactions.filter { it.transaction.merchantName != null && it.transaction.merchantName!!.isNotBlank() }
                        .groupBy { it.transaction.merchantName!! }
                        .mapValues { it.value.sumOf { t -> t.transaction.amountMinorUnits ?: (t.transaction.amount * 100).toLong() } }
                        .toList().sortedByDescending { it.second }
                }
                "Networth" -> bTrendData
                "OnAccountLoan" -> {
                    filteredInvoices.groupBy { it.detail.partyName ?: it.detail.accountName }
                        .mapValues { entry -> 
                            entry.value.sumOf { inv -> 
                                val total = inv.detail.transaction.amountMinorUnits ?: (inv.detail.transaction.amount * 100).toLong()
                                val totalCleared = inv.totalCleared 
                                val outstanding = total - totalCleared
                                if (invoiceStatusFilter == "Cleared") totalCleared else outstanding
                            } 
                        }
                        .toList().sortedByDescending { kotlin.math.abs(it.second) }
                }
                else -> emptyList()
            }

            if (type == "Account") {
                ScrollableTabRow(
                    selectedTabIndex = when(accountsSubTab) { "Spending" -> 0; "Source" -> 1; "BTrend" -> 2; else -> 3 },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    divider = {},
                    edgePadding = 0.dp
                ) {
                    Tab(selected = accountsSubTab == "Spending", onClick = { accountsSubTab = "Spending" }) {
                        Text(stringResource(R.string.label_spending), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "Source", onClick = { accountsSubTab = "Source" }) {
                        Text(stringResource(R.string.label_source), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "BTrend", onClick = { accountsSubTab = "BTrend" }) {
                        Text(stringResource(R.string.label_btrend), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "Balance", onClick = { accountsSubTab = "Balance" }) {
                        Text(stringResource(R.string.label_balance), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }


            if (data.isEmpty() && type == "Account" && accountsSubTab != "Balance" && analysisSelectedAccountId == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_select_account_analysis), color = Color.Gray)
                }
            } else if (data.isNotEmpty()) {
                val chartData = if (type == "OnAccountLoan" || type == "Account" || type == "Networth") {
                    data.map { it.component1() to kotlin.math.abs(it.component2().toDouble() / 100.0) }
                } else data.map { it.first to it.second.toDouble() / 100.0 }

                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if ((type == "Account" && accountsSubTab == "BTrend") || type == "Networth") {
                        LineChart(data = data.map { it.second.toDouble() / 100.0 }, labels = data.map { it.first })
                    } else if (type == "Tags" && isTagBarChartView) {
                        val tagBarData = tags.filter { (it.targetNumber ?: 0.0) > 0.0 }.map { tag ->
                            val total = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(tag.id.toString()) == true }.sumOf { it.transaction.amount }
                            tag to total
                        }
                        TagTargetBarChart(data = tagBarData, viewModel = viewModel)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PieChart(data = chartData, colors = chartColors)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                for (i in 0 until minOf(5, chartData.size)) {
                                    val pair = chartData[i]
                                    val totalVal = chartData.sumOf { it.second }
                                    val pct = if (totalVal != 0.0) (pair.second / totalVal * 100).toInt() else 0
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).background(chartColors[i % chartColors.size]))
                                        Text(" $pct%", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (type != "OnAccountLoan") {
                 Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_no_data), color = Color.Gray)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (type == "OnAccountLoan" && viewModel.invoiceAgeTrackingEnabled) {
                    if (filteredInvoices.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.msg_no_invoices_matching), color = Color.Gray)
                            }
                        }
                    }

                    if (groupByParty) {
                        val grouped = filteredInvoices.groupBy { it.detail.partyName ?: it.detail.accountName }
                            .mapValues { entry -> 
                                entry.value.sumOf { inv -> 
                                    val total = inv.detail.transaction.amountMinorUnits ?: (inv.detail.transaction.amount * 100).toLong()
                                    val totalCleared = inv.totalCleared
                                    val outstanding = total - totalCleared
                                    if (invoiceStatusFilter == "Cleared") totalCleared else outstanding
                                } 
                            }.toList().sortedByDescending { kotlin.math.abs(it.second) }
                        
                        items(grouped) { (name, amount) ->
                            Surface(
                                color = (if (amount >= 0) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    showDetailList = name
                                }
                            ) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                                    trailingContent = {
                                        Text(viewModel.formatAmount(amount), color = if (amount >= 0) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                )
                            }
                        }
                    } else {
                        items(filteredInvoices) { inv ->
                            val total = inv.detail.transaction.amountMinorUnits ?: (inv.detail.transaction.amount * 100).toLong()
                            val outstanding = total - inv.totalCleared
                            val dueDate = try { LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0) } catch(e: Exception) { null }
                            val isCleared = outstanding <= 0
                            
                            Surface(
                                color = (if (isCleared) Color.Gray else MaterialTheme.colorScheme.primaryContainer).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    viewModel.selectedTransactionDetail = inv.detail
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.label_inv_item, inv.detail.transaction.invoiceNumber ?: "N/A", ""), fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isCleared) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.label_cleared_status), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                            } else {
                                                Text(stringResource(R.string.label_open_status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(8.dp))
                                                Text(viewModel.formatAmount(outstanding), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(stringResource(R.string.label_party_colon) + (inv.detail.partyName ?: inv.detail.accountName), style = MaterialTheme.typography.labelSmall)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.label_date_colon) + inv.detail.transaction.date, style = MaterialTheme.typography.labelSmall)
                                        if (dueDate != null) {
                                            Text(stringResource(R.string.label_due_colon) + dueDate, style = MaterialTheme.typography.labelSmall, color = if (!isCleared && dueDate.isBefore(LocalDate.now())) Color.Red else Color.Unspecified)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if ((type == "Account" && accountsSubTab == "BTrend") || type == "Networth") {
                    items(data.reversed()) { pair ->
                        val dateLabel = pair.component1()
                        val amount = pair.component2()
                        Surface(
                            color = (if (amount >= 0) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(dateLabel) },
                                trailingContent = { 
                                    Text(
                                        viewModel.formatAmount(amount), 
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                                    ) 
                                }
                            )
                        }
                    }
                } else {
                    items(data) { pair ->
                        val name = pair.component1()
                        val amount = pair.component2()
                        val index = data.indexOfFirst { it.component1() == name }
                        val itemColor = if (type == "Account") {
                            if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                        } else if (type == "OnAccountLoan" || type == "Account") {
                            if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                        } else MaterialTheme.colorScheme.onSurface

                        val itemIcon = when (type) {
                            "Expense", "Income" -> allCategories.find { it.name == name }?.icon ?: "📁"
                            "Account", "OnAccountLoan" -> balances.find { it.name == name }?.icon ?: "🏦"
                            else -> "📁"
                        }

                        Surface(
                            color = itemColor.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { 
                                    if (type == "Tags") {
                                        showTagDetailList = tags.find { it.name == name }?.id
                                    } else if (type == "Account" && accountsSubTab != "Balance") {
                                        showDetailList = name
                                    } else if (type == "Merchants") {
                                        showDetailList = "MERCHANT:$name"
                                    } else {
                                        showDetailList = name 
                                    }
                                }
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(12.dp).background(chartColors[index % chartColors.size]))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = "$itemIcon $name", style = MaterialTheme.typography.bodyLarge)
                                    }
                                },
                                trailingContent = { 
                                    Text(
                                        viewModel.formatAmount(amount), 
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = itemColor
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
            if (type == "Tags") {
                Button(onClick = { onNavigate("summary_by_tags") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.label_view_all_txns_tag))
                }
            }
        }
    }

    if (showFilter) {
        DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
    }

    if (showAdvancedFilterDialog) {
        val onAccountLoanId = majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6
        val partyAccounts = allAccountsList.filter { a -> minorHeads.find { it.id == a.minorHeadId }?.majorHeadId == onAccountLoanId }
        val allTags by viewModel.getAllTags().collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { showAdvancedFilterDialog = false },
            title = { Text(stringResource(R.string.title_filter_analysis)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = groupByParty, onCheckedChange = { groupByParty = it })
                        Text(stringResource(R.string.label_group_by_party))
                    }

                    Column {
                        Text(stringResource(R.string.label_status), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Open", "Cleared").forEach { s ->
                                val label = when(s) {
                                    "All" -> stringResource(R.string.label_all)
                                    "Open" -> stringResource(R.string.label_open)
                                    else -> stringResource(R.string.label_cleared)
                                }
                                FilterChip(
                                    selected = invoiceStatusFilter == s, 
                                    onClick = { invoiceStatusFilter = s }, 
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByParty, onCheckedChange = { filterByParty = it })
                            Text(stringResource(R.string.label_by_party_name))
                        }
                        if (filterByParty) {
                            Box(modifier = Modifier.height(150.dp).padding(start = 24.dp)) {
                                LazyColumn {
                                    items(partyAccounts) { acc ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (acc.id in selectedAnalysisPartyIds) selectedAnalysisPartyIds.remove(acc.id)
                                                else selectedAnalysisPartyIds.add(acc.id)
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = acc.id in selectedAnalysisPartyIds, onCheckedChange = {
                                                if (it) selectedAnalysisPartyIds.add(acc.id)
                                                else selectedAnalysisPartyIds.remove(acc.id)
                                            })
                                            Text(acc.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByOverdue, onCheckedChange = { filterByOverdue = it })
                            Text(stringResource(R.string.label_overdue))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByAmount, onCheckedChange = { filterByAmount = it })
                            Text(stringResource(R.string.label_amount_range))
                        }
                        if (filterByAmount) {
                            Row(modifier = Modifier.padding(start = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = minAmtFilter, onValueChange = { minAmtFilter = it }, label = { Text(stringResource(R.string.label_min)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                OutlinedTextField(value = maxAmtFilter, onValueChange = { maxAmtFilter = it }, label = { Text(stringResource(R.string.label_max)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByDueInXDays, onCheckedChange = { filterByDueInXDays = it })
                            Text(stringResource(R.string.label_due_in_x_days))
                        }
                        if (filterByDueInXDays) {
                            OutlinedTextField(value = dueInXDaysFilter, onValueChange = { dueInXDaysFilter = it }, label = { Text(stringResource(R.string.label_no_of_days)) }, modifier = Modifier.padding(start = 24.dp).fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByWasDueYDays, onCheckedChange = { filterByWasDueYDays = it })
                            Text(stringResource(R.string.label_was_due_y_days))
                        }
                        if (filterByWasDueYDays) {
                            OutlinedTextField(value = wasDueYDaysFilter, onValueChange = { wasDueYDaysFilter = it }, label = { Text(stringResource(R.string.label_no_of_days)) }, modifier = Modifier.padding(start = 24.dp).fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }

                        var showCatsInFilter by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = categoryFilterIds != null, onCheckedChange = { if (it) showCatsInFilter = true else categoryFilterIds = null })
                            Text(stringResource(R.string.label_category))
                        }
                        if (categoryFilterIds != null || showCatsInFilter) {
                             Box(modifier = Modifier.height(150.dp).padding(start = 24.dp)) {
                                LazyColumn {
                                    items(allCategories) { cat ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val current = categoryFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (cat.id in current) current.remove(cat.id)
                                                else current.add(cat.id)
                                                categoryFilterIds = if (current.isEmpty()) null else current
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = cat.id in (categoryFilterIds ?: emptySet()), onCheckedChange = {
                                                val current = categoryFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (it) current.add(cat.id)
                                                else current.remove(cat.id)
                                                categoryFilterIds = if (current.isEmpty()) null else current
                                            })
                                            Text(cat.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        var showTagsInFilter by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = tagFilterIds != null, onCheckedChange = { if (it) showTagsInFilter = true else tagFilterIds = null })
                            Text(stringResource(R.string.label_tags))
                        }
                        if (tagFilterIds != null || showTagsInFilter) {
                             Box(modifier = Modifier.height(150.dp).padding(start = 24.dp)) {
                                LazyColumn {
                                    items(allTags) { tag ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val current = tagFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (tag.id in current) current.remove(tag.id)
                                                else current.add(tag.id)
                                                tagFilterIds = if (current.isEmpty()) null else current
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = tag.id in (tagFilterIds ?: emptySet()), onCheckedChange = {
                                                val current = tagFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (it) current.add(tag.id)
                                                else current.remove(tag.id)
                                                tagFilterIds = if (current.isEmpty()) null else current
                                            })
                                            Text(tag.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAdvancedFilterDialog = false }) { Text(stringResource(R.string.btn_apply)) } },
            dismissButton = {
                TextButton(onClick = {
                    filterByParty = false; filterByOverdue = false; filterByAmount = false; filterByDueInXDays = false; filterByWasDueYDays = false
                    selectedAnalysisPartyIds.clear(); minAmtFilter = ""; maxAmtFilter = ""; dueInXDaysFilter = ""; wasDueYDaysFilter = ""
                    categoryFilterIds = null; tagFilterIds = null; groupByParty = false; invoiceStatusFilter = "Open"
                    showAdvancedFilterDialog = false
                }) {
                    Text(stringResource(R.string.btn_clear_all), color = Color.Red)
                }
            }
        )
    }

    if (showAccountSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showAccountSelectionDialog = false },
            title = { Text(stringResource(R.string.label_select_account_analysis)) },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    analysisSelectedAccountId = null
                                    showAccountSelectionDialog = false
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = analysisSelectedAccountId == null, onClick = {
                                    analysisSelectedAccountId = null
                                    showAccountSelectionDialog = false
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(allAccountsLabel)
                            }
                        }
                        items(allAccountsList) { acc ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    analysisSelectedAccountId = acc.id
                                    showAccountSelectionDialog = false
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = analysisSelectedAccountId == acc.id, onClick = {
                                    analysisSelectedAccountId = acc.id
                                    showAccountSelectionDialog = false
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(acc.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAccountSelectionDialog = false }) { Text(stringResource(R.string.btn_done)) } }
        )
    }

    if (showCategoryFilterDialog) {
        val targetType = if (type == "Expense") "expense" else "income"
        val filteredCategories = allCategories.filter { it.type.lowercase() == targetType }

        AlertDialog(
            onDismissRequest = { showCategoryFilterDialog = false },
            title = { Text(stringResource(R.string.title_filter_analysis)) },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        items(filteredCategories) { cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val current = categoryFilterIds?.toMutableSet() ?: mutableSetOf()
                                    if (cat.id in current) current.remove(cat.id)
                                    else current.add(cat.id)
                                    categoryFilterIds = if (current.isEmpty()) null else current
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = cat.id in (categoryFilterIds ?: emptySet()), onCheckedChange = {
                                    val current = categoryFilterIds?.toMutableSet() ?: mutableSetOf()
                                    if (it) current.add(cat.id)
                                    else current.remove(cat.id)
                                    categoryFilterIds = if (current.isEmpty()) null else current
                                })
                                Text(cat.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showCategoryFilterDialog = false }) { Text(stringResource(R.string.btn_done)) } },
            dismissButton = {
                TextButton(onClick = { categoryFilterIds = null; showCategoryFilterDialog = false }) {
                    Text(stringResource(R.string.btn_clear_all), color = Color.Red)
                }
            }
        )
    }
}

@Composable
fun TransactionsView(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else {
        Column {
            TransactionHistoryView(viewModel, onOpenDrawer = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsTab(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        BudgetComparisonScreen(viewModel = viewModel, onBack = {}, isTab = true)
    }
}

@Composable
fun UnifiedTopHeaderBar(
    viewModel: ExpenseViewModel,
    onOpenDrawer: () -> Unit,
    onShowLoadRemoteDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu_home))
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (viewModel.webdavUrl.isBlank()) {
                        Toast.makeText(context, "Please configure WebDAV Server URL in Database screen first.", Toast.LENGTH_LONG).show()
                        return@IconButton
                    }
                    if (viewModel.isSyncing) return@IconButton
                    Toast.makeText(context, "Uploading database to WebDAV cloud...", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val result = viewModel.syncNow()
                        if (result.isSuccess) {
                            Toast.makeText(context, "Database uploaded to WebDAV cloud successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            val err = result.exceptionOrNull()?.message ?: "Upload failed"
                            Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !viewModel.isSyncing
            ) {
                if (viewModel.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CloudUpload, "Upload Remote", tint = MaterialTheme.colorScheme.primary)
                }
            }

            IconButton(
                onClick = {
                    if (viewModel.webdavUrl.isBlank()) {
                        Toast.makeText(context, "Please configure WebDAV Server URL in Database screen first.", Toast.LENGTH_LONG).show()
                        return@IconButton
                    }
                    onShowLoadRemoteDialog()
                },
                enabled = !viewModel.isSyncing
            ) {
                Icon(Icons.Default.CloudDownload, "Load Remote", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
