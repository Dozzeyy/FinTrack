/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.MajorHead
import com.openapps.fintrack.data.MinorHead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHeadsScreen(
    viewModel: ExpenseViewModel,
    onEditMajor: () -> Unit,
    onEditMinor: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())

    var showDeleteConfirm by remember { mutableStateOf<Any?>(null) }
    val context = LocalContext.current

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Confirm Deletion") },
            text = { 
                val item = showDeleteConfirm
                val msg = if (item is MajorHead && !item.name.equals("On Account (Loan)", ignoreCase = true)) {
                    "Are you sure you want to delete this major head? Warning: All existing minor heads mapped under this major head will fall back to the 'Others' head."
                } else {
                    "Are you sure you want to delete this header? Warning: This might affect linked accounts."
                }
                Text(msg) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = showDeleteConfirm
                        if (item is MajorHead) {
                            if (item.name.equals("On Account (Loan)", ignoreCase = true)) {
                                Toast.makeText(context, "Cannot delete system mandatory head", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.deleteMajorHeadAndRemap(item)
                            }
                        } else if (item is MinorHead) {
                            viewModel.deleteMinorHead(item)
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Headers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (selectedTab == 0) {
                    viewModel.editingMajorHead = null
                    onEditMajor()
                } else {
                    viewModel.editingMinorHead = null
                    onEditMinor()
                }
            }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Major") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Minor") })
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    items(majorHeads) { head ->
                        HeaderItem(
                            name = head.name,
                            isEnabled = head.isEnabled,
                            onEdit = {
                                viewModel.editingMajorHead = head
                                onEditMajor()
                            },
                            onDelete = { showDeleteConfirm = head },
                            onToggle = { viewModel.toggleMajorHeadEnabled(head) }
                        )
                        Divider()
                    }
                } else {
                    items(minorHeads) { head ->
                        val majorName = majorHeads.find { it.id == head.majorHeadId }?.name ?: "Unknown"
                        HeaderItem(
                            name = head.name,
                            nature = "Major: $majorName",
                            isEnabled = head.isEnabled,
                            onEdit = {
                                viewModel.editingMinorHead = head
                                onEditMinor()
                            },
                            onDelete = { showDeleteConfirm = head },
                            onToggle = { viewModel.toggleMinorHeadEnabled(head) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderItem(
    name: String,
    nature: String? = null,
    isEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            if (nature != null) {
                Text(text = nature, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}
