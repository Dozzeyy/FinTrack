package com.openapps.fintrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    onNavigate: (String) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Multi-select filters
    val selectedTypes = remember { mutableStateListOf("expense", "income", "transfer") }
    
    val filteredTransactions = remember(transactions, searchQuery, selectedTypes.toList()) {
        transactions.filter { t ->
            val typeMatch = if (t.transaction.categoryId == null) "transfer" in selectedTypes 
                            else t.categoryType in selectedTypes
            
            val queryMatch = if (searchQuery.isBlank()) true else {
                t.transaction.note?.contains(searchQuery, ignoreCase = true) == true ||
                t.transaction.amount.toString().contains(searchQuery) ||
                t.accountName.contains(searchQuery, ignoreCase = true) ||
                t.categoryName?.contains(searchQuery, ignoreCase = true) == true ||
                t.toAccountName?.contains(searchQuery, ignoreCase = true) == true ||
                t.transaction.transactionNumber?.contains(searchQuery, ignoreCase = true) == true
            }
            typeMatch && queryMatch
        }
    }

    val navigateFromDrawer = { route: String ->
        scope.launch {
            drawerState.close()
            onNavigate(route)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Drawer Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("FinTrack", style = MaterialTheme.typography.titleLarge)
                        Text("Count every penny", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                Divider()
                Spacer(Modifier.height(12.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, null) },
                    label = { Text("Add Transaction") },
                    selected = false,
                    onClick = { navigateFromDrawer("add_transaction") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Category, null) },
                    label = { Text("Add Category") },
                    selected = false,
                    onClick = { navigateFromDrawer("add_category") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Edit, null) },
                    label = { Text("Manage Category") },
                    selected = false,
                    onClick = { navigateFromDrawer("manage_categories") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Label, null) },
                    label = { Text("Tags") },
                    selected = false,
                    onClick = { navigateFromDrawer("tags_main") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    label = { Text("Budgets") },
                    selected = false,
                    onClick = { navigateFromDrawer("budgets_main") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Assessment, null) },
                    label = { Text("View Summary") },
                    selected = false,
                    onClick = { navigateFromDrawer("summary") }
                )
                Divider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { navigateFromDrawer("settings") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Security, null) },
                    label = { Text("Permissions") },
                    selected = false,
                    onClick = { navigateFromDrawer("permissions") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Storage, null) },
                    label = { Text("Database") },
                    selected = false,
                    onClick = { navigateFromDrawer("database") }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ContactSupport, null) },
                    label = { Text("Contact Us") },
                    selected = false,
                    onClick = { navigateFromDrawer("contact") }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchExpanded) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("FinTrack")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            scope.launch { 
                                drawerState.open()
                            } 
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        if (isSearchExpanded) {
                            IconButton(onClick = { 
                                isSearchExpanded = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        } else {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { onNavigate("add_transaction") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        ) { padding ->
            if (viewModel.showAssetsOnHome) {
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    AssetsLiabilitiesView(viewModel)
                }
            } else {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    // Filter Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("expense", "income", "transfer").forEach { type ->
                            FilterChip(
                                selected = type in selectedTypes,
                                onClick = {
                                    if (type in selectedTypes) {
                                        if (selectedTypes.size > 1) selectedTypes.remove(type)
                                    } else {
                                        selectedTypes.add(type)
                                    }
                                },
                                label = { Text(type.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Text(
                        if (searchQuery.isEmpty()) "Recent Transactions" else "Search Results",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(filteredTransactions.take(if (searchQuery.isEmpty()) 20 else 100)) { detail ->
                            TransactionRow(detail = detail, viewModel = viewModel)
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
