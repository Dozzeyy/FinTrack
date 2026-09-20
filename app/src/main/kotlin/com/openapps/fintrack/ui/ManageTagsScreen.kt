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

import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
                title = { Text(stringResource(R.string.title_manage_tags)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                viewModel.editingTag = null
                onEditTag() 
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.btn_add))
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
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit))
                            }
                            IconButton(onClick = { viewModel.deleteTag(tag) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete))
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
    val bothLabel = stringResource(R.string.label_both)
    var name by remember { mutableStateOf(viewModel.editingTag?.name ?: "") }
    var isEnabled by remember { mutableStateOf(viewModel.editingTag?.isEnabled ?: true) }
    var trackingType by remember { mutableStateOf(viewModel.editingTag?.trackingType ?: bothLabel) }
    var targetNumber by remember { mutableStateOf(viewModel.editingTag?.targetNumber?.toString() ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editingTag == null) stringResource(R.string.title_add_tag) else stringResource(R.string.title_edit_tag)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_tag_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            var trackingExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = trackingType,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_track_type)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { trackingExpanded = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
                )
                DropdownMenu(expanded = trackingExpanded, onDismissRequest = { trackingExpanded = false }) {
                    listOf(stringResource(R.string.label_income), stringResource(R.string.label_expense), stringResource(R.string.label_both)).forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = {
                            trackingType = type
                            trackingExpanded = false
                        })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = targetNumber,
                onValueChange = { targetNumber = it },
                label = { Text(stringResource(R.string.label_target_number_optional)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.label_enabled))
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }
            
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveTag(name, isEnabled, trackingType, targetNumber.toDoubleOrNull())
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save_tag))
            }
        }
    }
}
