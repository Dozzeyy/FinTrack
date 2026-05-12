/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    viewModel: ExpenseViewModel,
    onNavigate: (String, Bundle?) -> Unit,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Manage, 1: Customize

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(onClick = { 
                    viewModel.editingTemplate = null
                    val bundle = Bundle().apply { putBoolean("template_mode", true) }
                    onNavigate("add_transaction", bundle)
                }) {
                    Icon(Icons.Default.Add, "Add Template")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Manage") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Customize") })
            }

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn().togetherWith(fadeOut())
                },
                label = "TemplatesTabTransition"
            ) { tab ->
                if (tab == 0) {
                    ManageTemplatesView(viewModel, onNavigate)
                } else {
                    CustomizeTemplatesView(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTemplatesView(viewModel: ExpenseViewModel, onNavigate: (String, Bundle?) -> Unit) {
    val templates by viewModel.getAllTemplates().collectAsState(initial = emptyList())
    val typeFilters = remember { mutableStateListOf("Single", "Multi") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Single", "Multi").forEach { type ->
                val isSelected = typeFilters.contains(type)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) typeFilters.remove(type) else typeFilters.add(type)
                        if (typeFilters.isEmpty()) typeFilters.add(type)
                    },
                    label = { Text(type) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Existing Templates", style = MaterialTheme.typography.titleMedium)
        Divider(Modifier.padding(vertical = 8.dp))

        val filteredTemplates = remember(templates, typeFilters.toList()) {
            templates.filter { t ->
                (typeFilters.contains("Single") && t.multiEntries == null) ||
                (typeFilters.contains("Multi") && t.multiEntries != null)
            }
        }

        LazyColumn {
            items(filteredTemplates) { template ->
                ListItem(
                    headlineContent = { Text(template.name) },
                    supportingContent = { Text("${template.type.replaceFirstChar { it.uppercase() }} template") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                viewModel.editingTemplate = template
                                val bundle = Bundle().apply { putBoolean("template_mode", true) }
                                onNavigate("add_transaction_template", bundle) 
                            }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteTemplate(template) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                            }
                        }
                    }
                )
                Divider()
            }
        }
    }
}

@Composable
fun CustomizeTemplatesView(viewModel: ExpenseViewModel) {
    val fields = listOf(
        "type" to "Transaction Type",
        "accountId" to "Account",
        "categoryId" to "Category",
        "amount" to "Amount",
        "note" to "Note",
        "tags" to "Tags"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select fields to include in templates", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        fields.forEach { (key, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label)
                Switch(
                    checked = viewModel.templateFields.contains(key),
                    onCheckedChange = { viewModel.updateTemplateField(key, it) }
                )
            }
            Divider()
        }
    }
}
