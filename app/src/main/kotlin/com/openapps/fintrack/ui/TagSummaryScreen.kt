package com.openapps.fintrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagSummaryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    
    val transactions by viewModel.getTransactionsByTags(selectedTagIds.toList(), startDate, endDate).collectAsState(initial = emptyList())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summary by Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text("Select Tags:", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            
            FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            if (tag.id in selectedTagIds) selectedTagIds.remove(tag.id)
                            else selectedTagIds.add(tag.id)
                        },
                        label = { Text(tag.name) },
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${transactions.size} Transactions", style = MaterialTheme.typography.labelLarge)
                Row {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = { 
                        exportToLocalPath(context, transactions, "CSV")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            }
            
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(transactions) { detail ->
                    TransactionRow(detail = detail, viewModel = viewModel)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
    
    if (showFilterDialog) {
        DateRangeFilterDialog(
            onDismiss = { showFilterDialog = false },
            onApply = { start, end ->
                startDate = start
                endDate = end
                showFilterDialog = false
            }
        )
    }
}
