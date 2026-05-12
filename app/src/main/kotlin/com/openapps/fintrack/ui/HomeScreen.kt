/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.TransactionWithDetails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
                Spacer(Modifier.height(12.dp))
                Text("FinTrack Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Divider()
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; selectedTab = "home" },
                    icon = { Icon(Icons.Default.Home, null) }
                )
                NavigationDrawerItem(
                    label = { Text("View Summary") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("summary") },
                    icon = { Icon(Icons.Default.Assessment, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Manage Categories") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("manage_categories") },
                    icon = { Icon(Icons.Default.Category, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Templates") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("templates") },
                    icon = { Icon(Icons.Default.Dashboard, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Manage Tags") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("manage_tags") },
                    icon = { Icon(Icons.Default.Label, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Manage Budgets") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("manage_budgets") },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Permissions") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("permissions") },
                    icon = { Icon(Icons.Default.Security, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("settings") },
                    icon = { Icon(Icons.Default.Settings, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Database") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("database") },
                    icon = { Icon(Icons.Default.Storage, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Contact Support") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigate("contact") },
                    icon = { Icon(Icons.Default.Email, null) }
                )
                
                Spacer(Modifier.weight(1f))
                Text(
                    "v1.8.2",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    viewModel.bottomTabOrder.forEach { tabKey ->
                        when (tabKey) {
                            "home" -> NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, "Home") },
                                label = { Text("Home") },
                                selected = selectedTab == "home",
                                onClick = { selectedTab = "home" }
                            )
                            "analysis" -> NavigationBarItem(
                                icon = { Icon(Icons.Default.PieChart, "Analysis") },
                                label = { Text("Analysis") },
                                selected = selectedTab == "analysis",
                                onClick = { selectedTab = "analysis" }
                            )
                            "transactions" -> NavigationBarItem(
                                icon = { Icon(Icons.Default.List, "Transactns") },
                                label = { Text("Transactns") },
                                selected = selectedTab == "transactions",
                                onClick = { selectedTab = "transactions" }
                            )
                            "budgets" -> NavigationBarItem(
                                icon = { Icon(Icons.Default.AccountBalanceWallet, "Budgets") },
                                label = { Text("Budgets") },
                                selected = selectedTab == "budgets",
                                onClick = { selectedTab = "budgets" }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                        Column {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search transactions...") },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    singleLine = true,
                                    leadingIcon = {
                                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                            Icon(Icons.Default.ArrowBack, "Back")
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
                                        TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true)
                                    }
                                    Divider(modifier = Modifier.padding(vertical = 4.dp))
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
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn().togetherWith(fadeOut())
                            },
                            label = "TabTransition"
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
                                    onTabChange = { selectedTab = it }
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
                            }
                        }
                    }
                }

                if (!isSearchActive) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { isSearchActive = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        
                        val fabLabel = when (selectedTab) {
                            "budgets" -> "Add Budget"
                            else -> "Add Transaction"
                        }
                        
                        val isServerRunning by viewModel.isServerRunning.collectAsState()
                        val snackbarHostState = remember { SnackbarHostState() }
                        val scope = rememberCoroutineScope()

                        FloatingActionButton(
                            onClick = { 
                                if (isServerRunning) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Editing disabled while local server is active.")
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    viewModel: ExpenseViewModel, 
    onNavigate: (String) -> Unit, 
    onOpenDrawer: () -> Unit,
    onNavigateToAnalysis: (String, String, String) -> Unit,
    onTabChange: (String) -> Unit
) {
    val refreshTrigger by viewModel.refreshTrigger.collectAsState()
    
    var startDate by remember(refreshTrigger) { mutableStateOf(LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember(refreshTrigger) { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    
    val s = LocalDate.parse(startDate)
    val e = LocalDate.parse(endDate)
    val prevStart = s.minusMonths(1).format(DateTimeFormatter.ISO_DATE)
    val prevEnd = e.minusMonths(1).format(DateTimeFormatter.ISO_DATE)
    val prevTransactions by viewModel.getFilteredTransactions(prevStart, prevEnd).collectAsState(initial = emptyList())

    var showTransactionListType by remember { mutableStateOf<String?>(null) }

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showTransactionListType != null) {
        BackHandler { showTransactionListType = null }
        TransactionListOverlay(
            title = showTransactionListType!!.replaceFirstChar { it.uppercase() },
            transactions = transactions.filter { it.categoryType == showTransactionListType },
            viewModel = viewModel,
            onBack = { showTransactionListType = null }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                        Text("There is a draft transaction pending to be recorded", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                    Text("FinTrack", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showFilter = true }) {
                    Icon(Icons.Default.DateRange, "Filter")
                }
            }
            
            Text("$startDate to $endDate", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Spacer(Modifier.height(16.dp))
            
            val curInc = transactions.filter { it.categoryType == "income" }.sumOf { it.transaction.amount }
            val curExp = transactions.filter { it.categoryType == "expense" }.sumOf { it.transaction.amount }
            val prevInc = prevTransactions.filter { it.categoryType == "income" }.sumOf { it.transaction.amount }
            val prevExp = prevTransactions.filter { it.categoryType == "expense" }.sumOf { it.transaction.amount }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "expense" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Expense", style = MaterialTheme.typography.labelMedium, color = Color.Red)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.formatAmount(curExp), style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.Bold)
                                if (curExp > prevExp) {
                                    Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                } else if (curExp < prevExp) {
                                    Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("prev month: ${viewModel.formatAmount(prevExp)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "income" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Income", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.formatAmount(curInc), style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                if (curInc > prevInc) {
                                    Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                } else if (curInc < prevInc) {
                                    Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("prev month: ${viewModel.formatAmount(prevInc)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net", fontWeight = FontWeight.Bold)
                        Text(viewModel.formatAmount(curInc - curExp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Unnecessary gap removed
            val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
            val netPosition = balances.sumOf { it.balance }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { 
                        viewModel.summaryInitialTab = "Assets"
                        onNavigate("summary")
                    }, 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net Position", fontWeight = FontWeight.Bold)
                    Text(viewModel.formatAmount(netPosition), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Text("Top Expenses", style = MaterialTheme.typography.titleMedium)
            val topExpenses = transactions.filter { it.categoryType == "expense" }
                .groupBy { it.categoryName ?: "Uncategorized" }
                .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(4)
            
            if (topExpenses.isNotEmpty()) {
                Box(modifier = Modifier.clickable { onNavigateToAnalysis("Expense", startDate, endDate) }) {
                    ExpensePieChart(topExpenses, viewModel)
                }
            } else {
                Text("No expenses in this period", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            if (viewModel.dashboardAccountIds.isNotEmpty()) {
                val selectedBalances = balances.filter { it.id in viewModel.dashboardAccountIds }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedBalances) { b ->
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable {
                                    viewModel.summaryInitialTab = "Assets"
                                    viewModel.summaryInitialAccountId = b.id
                                    onNavigate("summary")
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(b.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Text(viewModel.formatAmount(b.balance), style = MaterialTheme.typography.titleMedium, color = if (b.balance >= 0) Color(0xFF4CAF50) else Color.Red)
                            }
                        }
                    }
                }
            }

            if (viewModel.dashboardBudgetIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val budgetVsActual by viewModel.getBudgetVsActual(endDate).collectAsState(initial = emptyList())
                val selectedBudgetsRaw by viewModel.getAllBudgets().collectAsState(initial = emptyList())
                val selectedBudgets = selectedBudgetsRaw.filter { it.id in viewModel.dashboardBudgetIds }

                if (selectedBudgets.isNotEmpty()) {
                    Text("Budget Performance", style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedBudgets) { budget ->
                            val performance = budgetVsActual.find { it.categoryName == budget.name || (budget.name == null && it.categoryName.contains(budget.categoryIds.split(",")[0])) }
                            val actual = performance?.actualAmount ?: 0.0
                            val limit = budget.amount
                            val percent = if (limit != 0.0) (actual / limit * 100).toInt() else 0
                            
                            val isExpense = performance?.categoryType != "income"
                            val isRed = if (isExpense) actual > limit else actual < limit
                            val statusColor = if (isRed) Color.Red else Color(0xFF4CAF50)

                            Card(
                                modifier = Modifier.width(180.dp).clickable { onTabChange("budgets") }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(budget.name ?: "Budget", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
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
        Text("Prev: ${viewModel.formatAmount(previous)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
fun TransactionListOverlay(title: String, transactions: List<TransactionWithDetails>, viewModel: ExpenseViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(transactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) {
                    TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true)
                }
                Divider()
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
    var type by remember { mutableStateOf(initialType ?: "Expense") }
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(initialDateRange?.first ?: month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(initialDateRange?.second ?: month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }

    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
    val tags by viewModel.getAllTags().collectAsState(initial = emptyList())

    var showDetailList by remember { mutableStateOf<String?>(null) }
    var showTagDetailList by remember { mutableStateOf<Int?>(null) }

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showDetailList != null) {
        BackHandler { showDetailList = null }
        TransactionListOverlay(
            title = showDetailList!!,
            transactions = transactions.filter { (if (type == "Expense" || type == "Income") it.categoryName else it.accountName) == showDetailList },
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
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(type, style = MaterialTheme.typography.titleLarge)
                            Icon(Icons.Default.ArrowDropDown, "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Expense") }, onClick = { type = "Expense"; expanded = false })
                            DropdownMenuItem(text = { Text("Income") }, onClick = { type = "Income"; expanded = false })
                            DropdownMenuItem(text = { Text("Accounts") }, onClick = { type = "Accounts"; expanded = false })
                            DropdownMenuItem(text = { Text("Tags") }, onClick = { type = "Tags"; expanded = false })
                            DropdownMenuItem(text = { Text("Payer/ee") }, onClick = { type = "Payer/ee"; expanded = false })
                        }
                    }
                }
                IconButton(onClick = { showFilter = true }) { Icon(Icons.Default.FilterList, "") }
            }
                
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { 
                    month = month.minusMonths(1)
                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                }) { Icon(Icons.Default.ChevronLeft, "") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { 
                    month = month.plusMonths(1)
                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                }) { Icon(Icons.Default.ChevronRight, "") }
            }

            val chartColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan, Color.Gray, Color.DarkGray, Color.LightGray)

            val data = when (type) {
                "Expense" -> transactions.filter { it.categoryType == "expense" }
                    .groupBy { it.categoryName ?: "Uncategorized" }
                    .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                    .toList().sortedByDescending { it.second }
                "Income" -> transactions.filter { it.categoryType == "income" }
                    .groupBy { it.categoryName ?: "Uncategorized" }
                    .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                    .toList().sortedByDescending { it.second }
                "Accounts" -> balances.filter { !it.name.equals("On Account", ignoreCase = true) }.map { it.name to it.balance }.sortedByDescending { it.second }
                "Tags" -> tags.map { tag ->
                    val total = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(tag.id.toString()) == true }.sumOf { it.transaction.amount }
                    tag.name to total
                }.filter { it.second > 0 }.sortedByDescending { it.second }
                "Payer/ee" -> {
                    val partyBalances = runBlocking { viewModel.getPartyBalances(endDate).first() }
                    partyBalances.map { it.name to it.balance }.sortedByDescending { kotlin.math.abs(it.second) }
                }
                else -> emptyList()
            }

            if (data.isNotEmpty()) {
                val chartData = if (type == "Payer/ee" || type == "Accounts") {
                    data.map { it.first to kotlin.math.abs(it.second) }
                } else data

                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PieChart(data = chartData, colors = chartColors)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            chartData.take(5).forEachIndexed { i, pair ->
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

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(data) { (name, amount) ->
                    val index = data.indexOfFirst { it.first == name }
                    ListItem(
                        headlineContent = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(chartColors[index % chartColors.size]))
                                Spacer(Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                            }
                        },
                        trailingContent = { Text(viewModel.formatAmount(amount), style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.clickable { 
                            if (type == "Tags") {
                                showTagDetailList = tags.find { it.name == name }?.id
                            } else if (type == "Payer/ee") {
                                // Party logic
                            } else {
                                showDetailList = name 
                            }
                        }
                    )
                    Divider()
                }
            }
            if (type == "Tags") {
                Button(onClick = { onNavigate("summary_by_tags") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("View All Transactions by Tag")
                }
            }
        }
    }

    if (showFilter) {
        DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
    }
}

@Composable
fun TransactionsView(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else {
        Column {
            TransactionHistoryView(viewModel, onOpenDrawer = onOpenDrawer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsTab(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, "Menu")
            }
            Text("Budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        BudgetComparisonScreen(viewModel = viewModel, onBack = {}, isTab = true)
    }
}
