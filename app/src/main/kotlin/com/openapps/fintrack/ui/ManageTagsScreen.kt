/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsScreen(
    viewModel: ExpenseViewModel,
    onEditTag: () -> Unit,
    onBack: () -> Unit
) {
    val tags by viewModel.getAllTags().collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                viewModel.editingTag = null
                onEditTag() 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Tag")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(tags) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                viewModel.editingTag = tag
                                onEditTag() 
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteTag(tag) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            Switch(
                                checked = tag.isEnabled,
                                onCheckedChange = { viewModel.toggleTagEnabled(tag) }
                            )
                        }
                    }
                )
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf(viewModel.editingTag?.name ?: "") }
    var isEnabled by remember { mutableStateOf(viewModel.editingTag?.isEnabled ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editingTag == null) "Add Tag" else "Edit Tag") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tag Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enabled")
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }
            
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveTag(name, isEnabled)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Tag")
            }
        }
    }
}
