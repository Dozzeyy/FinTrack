/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.MajorHead
import com.openapps.fintrack.data.MinorHead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHeadScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val editingMajor = viewModel.editingMajorHead
    val editingMinor = viewModel.editingMinorHead

    var selectedTab by remember { mutableStateOf(if (editingMinor != null) 1 else 0) }
    var name by remember { mutableStateOf(editingMajor?.name ?: editingMinor?.name ?: "") }
    var isEnabled by remember { mutableStateOf(editingMajor?.isEnabled ?: editingMinor?.isEnabled ?: true) }

    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    var selectedMajorId by remember { mutableStateOf(editingMinor?.majorHeadId) }
    
    // Ensure selectedMajorId is updated when editingMinor changes
    LaunchedEffect(editingMinor) {
        if (editingMinor != null) {
            selectedMajorId = editingMinor.majorHeadId
        }
    }
    
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (editingMajor != null || editingMinor != null) "Edit Header" else "Add Header") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (editingMajor == null && editingMinor == null) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Major") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Minor") })
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedTab == 1) {
                var expanded by remember { mutableStateOf(false) }
                val majorName = majorHeads.find { it.id == selectedMajorId }?.name ?: "Select Major Head"

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    OutlinedTextField(
                        value = majorName,
                        onValueChange = {},
                        label = { Text("Parent Major Head") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    )
                    DropdownMenu(
                        expanded = expanded, 
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        majorHeads.forEach { head ->
                            DropdownMenuItem(
                                text = { Text(head.name) },
                                onClick = {
                                    selectedMajorId = head.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    if (selectedTab == 0) {
                        viewModel.saveMajorHead(name, isEnabled)
                    } else {
                        if (selectedMajorId == null) {
                            Toast.makeText(context, "Parent Major Head is mandatory", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveMinorHead(name, selectedMajorId!!, isEnabled)
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
