/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
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
                title = { Text(stringResource(R.string.menu_templates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(onClick = { 
                    viewModel.editingTemplate = null
                    onNavigate("add_transaction_template", null)
                }) {
                    Icon(Icons.Default.Add, stringResource(R.string.btn_add_template))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text(stringResource(R.string.label_manage)) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(stringResource(R.string.label_customize)) })
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
    val singleLabel = stringResource(R.string.label_single)
    val multiLabel = stringResource(R.string.label_multi)
    val typeFilters = remember { mutableStateListOf(singleLabel, multiLabel) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(singleLabel, multiLabel).forEach { type ->
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
        Text(stringResource(R.string.title_existing_templates), style = MaterialTheme.typography.titleMedium)
        Divider(Modifier.padding(vertical = 8.dp))

        val filteredTemplates = remember(templates, typeFilters.toList()) {
            templates.filter { t ->
                (typeFilters.contains(singleLabel) && t.multiEntries == null) ||
                (typeFilters.contains(multiLabel) && t.multiEntries != null)
            }
        }

        LazyColumn {
            items(filteredTemplates) { template ->
                ListItem(
                    headlineContent = { Text(template.name) },
                    supportingContent = { Text(template.type.replaceFirstChar { it.uppercase() } + stringResource(R.string.label_template_suffix)) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                viewModel.editingTemplate = template
                                onNavigate("add_transaction_template", null)
                            }) {
                                Icon(Icons.Default.Edit, stringResource(R.string.btn_edit))
                            }
                            IconButton(onClick = { viewModel.deleteTemplate(template) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = Color.Red)
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
        "type" to stringResource(R.string.label_txn_type),
        "accountId" to stringResource(R.string.label_account),
        "categoryId" to stringResource(R.string.label_category),
        "amount" to stringResource(R.string.label_amount),
        "note" to stringResource(R.string.label_notes),
        "tags" to stringResource(R.string.label_tags)
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.msg_select_template_fields), style = MaterialTheme.typography.titleMedium)
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
