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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.Note
import com.openapps.fintrack.data.Notebook
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class ChecklistItem(
    val text: String,
    val checked: Boolean = false,
    val quantity: Int = 1
)

@Serializable
data class StatementRow(
    val operation: String = "add",
    val description: String = "",
    val amount: Double = 0.0
)

@Serializable
data class StatementData(
    val rows: List<StatementRow> = emptyList()
)

@Serializable
data class DrawingData(
    val elements: List<DrawingElement> = emptyList()
)

@Serializable
sealed class DrawingElement {
    @Serializable
    @SerialName("path")
    data class FreePath(
        val points: List<PointData>,
        val color: Int,
        val thickness: Float,
        val alpha: Float = 1f
    ) : DrawingElement()

    @Serializable
    @SerialName("shape")
    data class Shape(
        val shapeType: ShapeType,
        val start: PointData,
        val end: PointData,
        val color: Int,
        val thickness: Float,
        val isFilled: Boolean = false,
        val alpha: Float = 1f
    ) : DrawingElement()
}

@Serializable
enum class ShapeType { RECTANGLE, SQUARE, CIRCLE, ARROW, LINE }

@Serializable
data class PointData(val x: Float, val y: Float)

fun Offset.toData() = PointData(x, y)
fun PointData.toOffset() = Offset(x, y)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, isEmbedded: Boolean = false) {
    var isAddingNote by remember { mutableStateOf(false) }
    var noteTypeToAdd by remember { mutableStateOf("text") }
    var viewingNote by remember { mutableStateOf<Note?>(null) }
    var editingNoteLocal by remember { mutableStateOf<Note?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    var showFabMenu by remember { mutableStateOf(false) }
    var showAddNotebookDialog by remember { mutableStateOf(false) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    var notebookToRename by remember { mutableStateOf<Notebook?>(null) }
    var showColorPicker by remember { mutableStateOf<Note?>(null) }

    val selectedTagIds = remember { mutableStateListOf<Int>() }
    var showTagFilter by remember { mutableStateOf(false) }
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    
    val notebooks by viewModel.getAllNotebooks().collectAsState(initial = emptyList())

    val notesFlow = remember(searchQuery, selectedTagIds.toList(), viewModel.selectedNotebookId) {
        if (searchQuery.isBlank()) {
            viewModel.selectedNotebookId?.let { viewModel.getNotesByNotebook(it) } ?: viewModel.getAllNotes()
        } else {
            viewModel.searchNotes(searchQuery)
        }
    }
    
    val notesRaw by notesFlow.collectAsState(initial = emptyList())
    
    val notes = remember(notesRaw, selectedTagIds.toList()) {
        if (selectedTagIds.isEmpty()) notesRaw
        else {
            notesRaw.filter { note ->
                val noteTags = note.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                selectedTagIds.any { it in noteTags }
            }
        }
    }

    val isServerRunning by viewModel.isServerRunning.collectAsState()

    var noteForAction by remember { mutableStateOf<Note?>(null) }
    var actionType by remember { mutableStateOf("") } 

    if (isAddingNote || editingNoteLocal != null) {
        AddEditNoteScreen(
            viewModel = viewModel,
            note = editingNoteLocal,
            initialType = if (editingNoteLocal != null) editingNoteLocal!!.type else noteTypeToAdd,
            onBack = { 
                isAddingNote = false
                editingNoteLocal = null
            }
        )
    } else if (viewingNote != null) {
        ViewNoteScreen(
            viewModel = viewModel,
            note = viewingNote!!,
            onBack = { viewingNote = null },
            onEdit = { 
                editingNoteLocal = viewingNote
                viewingNote = null
            },
            onUpdateNote = { updatedNote ->
                viewingNote = updatedNote
            },
            isEmbedded = isEmbedded
        )
    } else {
        Scaffold(
            topBar = {
                if (!isEmbedded || isSearching) {
                    TopAppBar(
                        title = {
                            if (isSearching) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(stringResource(R.string.label_search_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            } else {
                                Text(stringResource(R.string.menu_notes))
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isSearching) {
                                    isSearching = false
                                    searchQuery = ""
                                } else {
                                    onBack()
                                }
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { showTagFilter = true }) {
                                Icon(Icons.Default.Label, contentDescription = stringResource(R.string.title_filter_tags), tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { isSearching = !isSearching }) {
                                Icon(if (isSearching) Icons.Default.Close else Icons.Default.Search, contentDescription = stringResource(R.string.label_search))
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isEmbedded) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (showFabMenu) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 72.dp)
                            ) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        noteTypeToAdd = "statement"
                                        isAddingNote = true
                                        showFabMenu = false
                                    },
                                    icon = { Icon(Icons.Default.TableChart, null) },
                                    text = { Text(stringResource(R.string.title_statement)) },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        noteTypeToAdd = "drawing"
                                        isAddingNote = true
                                        showFabMenu = false
                                    },
                                    icon = { Icon(Icons.Default.Brush, null) },
                                    text = { Text(stringResource(R.string.title_drawing)) },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        noteTypeToAdd = "checklist"
                                        isAddingNote = true
                                        showFabMenu = false
                                    },
                                    icon = { Icon(Icons.Default.List, null) },
                                    text = { Text(stringResource(R.string.title_checklist)) },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        noteTypeToAdd = "text"
                                        isAddingNote = true
                                        showFabMenu = false
                                    },
                                    icon = { Icon(Icons.Default.Notes, null) },
                                    text = { Text(stringResource(R.string.title_new_note)) },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            }
                        }
                        FloatingActionButton(
                            onClick = { 
                                if (!isServerRunning) {
                                    showFabMenu = !showFabMenu
                                }
                            },
                            containerColor = if (isServerRunning) Color.Gray else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = stringResource(R.string.btn_add))
                        }
                    }
                }
            }
        ) { padding ->
            val contentPadding = if (isEmbedded && !isSearching) PaddingValues(0.dp) else padding
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (isEmbedded && !isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showTagFilter = true }) {
                            Icon(Icons.Default.Label, contentDescription = stringResource(R.string.title_filter_tags), tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.label_search))
                        }
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.menu_notes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddNotebookDialog = true }) {
                            Icon(Icons.Default.AddCircle, stringResource(R.string.title_new_notebook), modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    LazyColumn {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectedNotebookId = null }
                                    .padding(vertical = 4.dp),
                                color = if (viewModel.selectedNotebookId == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ) {
                                Text(stringResource(R.string.label_all) + " " + stringResource(R.string.menu_notes), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(notebooks) { notebook ->
                            var showNotebookMenu by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { viewModel.selectedNotebookId = notebook.id },
                                            onLongClick = { showNotebookMenu = true }
                                        )
                                        .padding(vertical = 4.dp),
                                    color = if (viewModel.selectedNotebookId == notebook.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Default.Book, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(notebook.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                DropdownMenu(expanded = showNotebookMenu, onDismissRequest = { showNotebookMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.btn_rename)) },
                                        onClick = {
                                            showNotebookMenu = false
                                            notebookToRename = notebook
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.btn_delete)) },
                                        onClick = {
                                            showNotebookMenu = false
                                            notebookToDelete = notebook
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

                Column(modifier = Modifier.weight(0.65f).fillMaxSize()) {
                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (isSearching || selectedTagIds.isNotEmpty()) stringResource(R.string.label_no_matches) else stringResource(R.string.label_empty), color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(notes) { note ->
                                var showNoteMenu by remember { mutableStateOf(false) }
                                Box {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .combinedClickable(
                                                onClick = { viewingNote = note },
                                                onLongClick = { showNoteMenu = true }
                                            ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = note.color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        val contentColor = if (note.color != null) {
                                            if (Color(note.color).luminance() > 0.5f) Color.Black else Color.White
                                        } else MaterialTheme.colorScheme.onSurfaceVariant

                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = when(note.type) {
                                                        "checklist" -> Icons.Default.Checklist
                                                        "drawing" -> Icons.Default.Brush
                                                        "statement" -> Icons.Default.TableChart
                                                        else -> Icons.Default.Notes
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = if (note.color != null) contentColor else MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = note.title, 
                                                    style = MaterialTheme.typography.bodyMedium, 
                                                    fontWeight = FontWeight.Bold, 
                                                    maxLines = 1,
                                                    color = contentColor
                                                )
                                                if (note.isPinned) {
                                                    Spacer(Modifier.weight(1f))
                                                    Icon(Icons.Default.PushPin, null, modifier = Modifier.size(14.dp), tint = contentColor.copy(alpha = 0.7f))
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            val drawingNoteLabel = stringResource(R.string.label_drawing_note)
                                            val statementTitle = stringResource(R.string.title_statement)
                                            val displayContent = remember(note.content, note.type) {
                                                if (note.type == "checklist") {
                                                    try {
                                                        val items = Json.decodeFromString<List<ChecklistItem>>(note.content)
                                                        items.joinToString(", ") { "${it.text} (${it.quantity})" }
                                                    } catch (e: Exception) { note.content }
                                                } else if (note.type == "drawing") {
                                                    drawingNoteLabel
                                                } else if (note.type == "statement") {
                                                    try {
                                                        val data = Json.decodeFromString<StatementData>(note.content)
                                                        data.rows.joinToString(", ") { it.description }.ifBlank { statementTitle }
                                                    } catch (e: Exception) { note.content }
                                                } else {
                                                    note.content
                                                }
                                            }
                                            Text(
                                                text = displayContent,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                color = contentColor.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    DropdownMenu(expanded = showNoteMenu, onDismissRequest = { showNoteMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                            onClick = {
                                                showNoteMenu = false
                                                viewModel.togglePinNote(note)
                                            },
                                            leadingIcon = { Icon(if (note.isPinned) Icons.Default.PushPin else Icons.Default.PushPin, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Color") },
                                            onClick = {
                                                showNoteMenu = false
                                                showColorPicker = note
                                            },
                                            leadingIcon = { Icon(Icons.Default.Palette, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.btn_move)) },
                                            onClick = {
                                                showNoteMenu = false
                                                noteForAction = note
                                                actionType = "move"
                                            },
                                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.btn_duplicate)) },
                                            onClick = {
                                                showNoteMenu = false
                                                noteForAction = note
                                                actionType = "copy"
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.btn_delete)) },
                                            onClick = {
                                                showNoteMenu = false
                                                viewModel.deleteNote(note)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    if (showAddNotebookDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddNotebookDialog = false },
            title = { Text(stringResource(R.string.title_new_notebook)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveNotebook(name)
                        showAddNotebookDialog = false
                    }
                }) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = { TextButton(onClick = { showAddNotebookDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (notebookToRename != null) {
        var name by remember { mutableStateOf(notebookToRename!!.name) }
        AlertDialog(
            onDismissRequest = { notebookToRename = null },
            title = { Text(stringResource(R.string.title_rename_notebook)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveNotebook(notebookToRename!!.copy(name = name))
                        notebookToRename = null
                    }
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { TextButton(onClick = { notebookToRename = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (notebookToDelete != null) {
        AlertDialog(
            onDismissRequest = { notebookToDelete = null },
            title = { Text(stringResource(R.string.title_delete_notebook)) },
            text = { Text(stringResource(R.string.msg_delete_notebook_desc, notebookToDelete!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNotebook(notebookToDelete!!)
                    notebookToDelete = null
                }) { Text(stringResource(R.string.btn_delete), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { notebookToDelete = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showTagFilter) {
        MultiSelectFilterDialog(
            title = "Filter by Tags",
            items = allTags.map { it.id to it.name },
            selectedIds = selectedTagIds,
            onDismiss = { showTagFilter = false }
        )
    }

    if (noteForAction != null) {
        AlertDialog(
            onDismissRequest = { noteForAction = null },
            title = { Text(if (actionType == "move") stringResource(R.string.title_move_to_notebook) else stringResource(R.string.title_copy_to_notebook)) },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.label_none_general)) },
                                modifier = Modifier.clickable {
                                    if (actionType == "move") viewModel.moveNote(noteForAction!!, null)
                                    else viewModel.copyNote(noteForAction!!, null)
                                    noteForAction = null
                                }
                            )
                            Divider()
                        }
                        items(notebooks) { notebook ->
                            ListItem(
                                headlineContent = { Text(notebook.name) },
                                modifier = Modifier.clickable {
                                    if (actionType == "move") viewModel.moveNote(noteForAction!!, notebook.id)
                                    else viewModel.copyNote(noteForAction!!, notebook.id)
                                    noteForAction = null
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { noteForAction = null }) { Text("Cancel") } }
        )
    }

    if (showColorPicker != null) {
        RgbColorPickerDialog(
            initialColor = showColorPicker!!.color,
            onColorSelected = { color ->
                viewModel.updateNoteColor(showColorPicker!!, color)
                showColorPicker = null
            },
            onDismiss = { showColorPicker = null }
        )
    }
}

@Composable
fun RgbColorPickerDialog(initialColor: Int?, onColorSelected: (Int?) -> Unit, onDismiss: () -> Unit) {
    var red by remember { mutableFloatStateOf(Color(initialColor ?: 0xFFFFFFFF.toInt()).red * 255f) }
    var green by remember { mutableFloatStateOf(Color(initialColor ?: 0xFFFFFFFF.toInt()).green * 255f) }
    var blue by remember { mutableFloatStateOf(Color(initialColor ?: 0xFFFFFFFF.toInt()).blue * 255f) }
    val currentColor = Color(red.toInt(), green.toInt(), blue.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Color") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(100.dp, 40.dp).background(currentColor, MaterialTheme.shapes.small).border(1.dp, Color.Gray, MaterialTheme.shapes.small))
                
                Column {
                    Text("Red: ${red.toInt()}", style = MaterialTheme.typography.labelSmall)
                    Slider(value = red, onValueChange = { red = it }, valueRange = 0f..255f)
                }
                Column {
                    Text("Green: ${green.toInt()}", style = MaterialTheme.typography.labelSmall)
                    Slider(value = green, onValueChange = { green = it }, valueRange = 0f..255f)
                }
                Column {
                    Text("Blue: ${blue.toInt()}", style = MaterialTheme.typography.labelSmall)
                    Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..255f)
                }
                
                TextButton(onClick = { onColorSelected(null) }) {
                    Text("Reset to Default")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor.toArgb()) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewNoteScreen(viewModel: ExpenseViewModel, note: Note, onBack: () -> Unit, onEdit: () -> Unit, onUpdateNote: (Note) -> Unit, isEmbedded: Boolean = false) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    
    val allTags by viewModel.getAllTags().collectAsState(initial = emptyList())
    val noteTags = remember(note.tags, allTags) {
        val tagIds = note.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        allTags.filter { it.id in tagIds }
    }

    val dateFormatter = remember { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()) }

    val checklistItems = remember(note.content, note.type) {
        if (note.type == "checklist") {
            try {
                Json.decodeFromString<List<ChecklistItem>>(note.content).toMutableStateList()
            } catch (e: Exception) {
                mutableStateListOf<ChecklistItem>()
            }
        } else mutableStateListOf<ChecklistItem>()
    }

    val statementData = remember(note.content, note.type) {
        if (note.type == "statement") {
            try { Json.decodeFromString<StatementData>(note.content) }
            catch (e: Exception) { StatementData() }
        } else null
    }

    fun saveChecklistChanges() {
        if (note.type == "checklist") {
            val newContent = Json.encodeToString(checklistItems.toList())
            val updatedNote = note.copy(content = newContent)
            viewModel.editingNote = note
            viewModel.saveNote(note.title, newContent, note.tags, note.type)
            onUpdateNote(updatedNote)
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when(note.type) {
                    "checklist" -> stringResource(R.string.title_checklist)
                    "drawing" -> stringResource(R.string.title_drawing)
                    "statement" -> stringResource(R.string.title_statement)
                    else -> stringResource(R.string.title_view_note)
                }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    if (!isEmbedded) {
                        IconButton(onClick = { if (!isServerRunning) onEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit_note))
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.label_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                onClick = {
                                    showMenu = false
                                    viewModel.togglePinNote(note)
                                    onUpdateNote(note.copy(isPinned = !note.isPinned))
                                },
                                leadingIcon = { Icon(if (note.isPinned) Icons.Default.PushPin else Icons.Default.PushPin, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Color") },
                                onClick = {
                                    showMenu = false
                                    showColorPicker = true
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_delete)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                enabled = !isServerRunning
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isEmbedded) {
                FloatingActionButton(
                    onClick = { if (!isServerRunning) onEdit() },
                    containerColor = if (isServerRunning) Color.Gray else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit_note))
                }
            }
        }
    ) { padding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = note.title, 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold
                )
                if (noteTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.label_tags_colon, noteTags.joinToString(", ") { it.name }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.label_created_colon, dateFormatter.format(java.util.Date(note.createdAt))),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    note.editedAt?.let {
                        Text(
                            text = stringResource(R.string.label_last_edit_colon, dateFormatter.format(java.util.Date(it))),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                if (note.type == "checklist") {
                    checklistItems.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { checked ->
                                    checklistItems[index] = item.copy(checked = checked)
                                    saveChecklistChanges()
                                }
                            )
                            Text(
                                text = item.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) Color.Gray else Color.Unspecified
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { 
                                        if (item.quantity > 1) {
                                            checklistItems[index] = item.copy(quantity = item.quantity - 1)
                                            saveChecklistChanges()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = item.quantity.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { 
                                        checklistItems[index] = item.copy(quantity = item.quantity + 1)
                                        saveChecklistChanges()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else if (note.type == "drawing") {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .border(1.dp, Color.Gray)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale *= zoom
                                offset += pan
                            }
                        }
                    ) {
                        val drawingData = remember(note.content) {
                            try { Json.decodeFromString<DrawingData>(note.content) }
                            catch (e: Exception) { DrawingData() }
                        }
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            withTransform({
                                translate(offset.x, offset.y)
                                scale(scale, scale, Offset.Zero)
                            }) {
                                drawingData.elements.forEach { element ->
                                    drawElement(this, element)
                                }
                            }
                        }
                    }
                } else if (note.type == "statement") {
                    statementData?.let { data ->
                        StatementTableView(data, viewModel)
                    }
                } else {
                    Text(text = note.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.title_delete_note)) },
            text = { Text(stringResource(R.string.msg_delete_note_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(note)
                    onBack()
                }) {
                    Text(stringResource(R.string.btn_delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showColorPicker) {
        RgbColorPickerDialog(
            initialColor = note.color,
            onColorSelected = { color ->
                viewModel.updateNoteColor(note, color)
                onUpdateNote(note.copy(color = color))
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteScreen(viewModel: ExpenseViewModel, note: Note?, initialType: String, onBack: () -> Unit) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val selectedTagIds = remember { 
        mutableStateListOf<Int>().apply {
            note?.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { addAll(it) }
        }
    }

    val checklistItems = remember {
        if (note?.type == "checklist") {
            try {
                Json.decodeFromString<List<ChecklistItem>>(note.content).toMutableStateList()
            } catch (e: Exception) {
                mutableStateListOf<ChecklistItem>()
            }
        } else {
            mutableStateListOf<ChecklistItem>()
        }
    }

    val statementRows = remember {
        if (note?.type == "statement") {
            try {
                val data = Json.decodeFromString<StatementData>(note.content)
                data.rows.toMutableStateList()
            } catch (e: Exception) {
                mutableStateListOf<StatementRow>(StatementRow())
            }
        } else {
            mutableStateListOf<StatementRow>(StatementRow())
        }
    }

    var newItemText by remember { mutableStateOf("") }
    var isCanvasFullWorkspace by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (note == null) (when(initialType) {
                    "checklist" -> stringResource(R.string.title_checklist)
                    "drawing" -> stringResource(R.string.title_drawing)
                    "statement" -> stringResource(R.string.title_statement)
                    else -> stringResource(R.string.title_new_note)
                }) else stringResource(R.string.title_edit_note)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (!isCanvasFullWorkspace) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.label_header)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(16.dp))
            }

            if (initialType == "checklist") {
                Text(stringResource(R.string.label_line_items), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                
                checklistItems.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = item.checked, onCheckedChange = { checklistItems[index] = item.copy(checked = it) })
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { checklistItems[index] = item.copy(text = it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (item.quantity > 1) checklistItems[index] = item.copy(quantity = item.quantity - 1) }) {
                                Icon(Icons.Default.Remove, null)
                            }
                            Text(item.quantity.toString())
                            IconButton(onClick = { 
                                val updated = checklistItems[index].copy(quantity = checklistItems[index].quantity + 1)
                                checklistItems[index] = updated
                            }) {
                                Icon(Icons.Default.Add, null)
                            }
                        }
                        IconButton(onClick = { checklistItems.removeAt(index) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        label = { Text(stringResource(R.string.label_add_item)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = {
                        if (newItemText.isNotBlank()) {
                            checklistItems.add(ChecklistItem(newItemText))
                            newItemText = ""
                        }
                    }) {
                        Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (initialType == "drawing") {
                val currentDrawing = remember {
                    try { Json.decodeFromString<DrawingData>(note?.content ?: "") }
                    catch (e: Exception) { DrawingData() }
                }
                val drawingElements = remember { currentDrawing.elements.toMutableStateList() }
                
                DrawingCanvas(
                    elements = drawingElements,
                    isToolbarVisible = !isCanvasFullWorkspace,
                    onToggleToolbar = { isCanvasFullWorkspace = !it }
                )
                
                LaunchedEffect(drawingElements.toList()) {
                    content = Json.encodeToString(DrawingData(drawingElements.toList()))
                }
            } else if (initialType == "statement") {
                StatementEditor(statementRows, viewModel)
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.label_content)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(Modifier.height(16.dp))
            TagSelectionPopup(
                allTags = allTags,
                selectedIds = selectedTagIds,
                multiSelect = viewModel.multiTagEnabled,
                enabled = !isServerRunning
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Button(
                    onClick = {
                        if (title.isNotBlank() && !isServerRunning) {
                            viewModel.editingNote = note
                            val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                            
                            val finalContent = when(initialType) {
                                "checklist" -> Json.encodeToString(checklistItems.toList())
                                "statement" -> Json.encodeToString(StatementData(statementRows.toList()))
                                else -> content
                            }
                            
                            viewModel.saveNote(title, finalContent, tagsString, initialType)
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank() && !isServerRunning
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }
}

@Composable
fun DrawingCanvas(
    elements: SnapshotStateList<DrawingElement>,
    isToolbarVisible: Boolean,
    onToggleToolbar: (Boolean) -> Unit
) {
    var currentTool by remember { mutableStateOf("pen") }
    var currentColor by remember { mutableIntStateOf(Color.Black.toArgb()) }
    var currentThickness by remember { mutableFloatStateOf(5f) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDarkCanvas by remember { mutableStateOf(false) }

    val activePath = remember { androidx.compose.ui.graphics.Path() }
    val pathTrigger = remember { mutableIntStateOf(0) }
    val currentPathPoints = remember { mutableListOf<Offset>() }
    
    var shapeStart = remember { mutableStateOf<Offset?>(null) }
    var shapeEnd = remember { mutableStateOf<Offset?>(null) }

    Column {
        if (isToolbarVisible) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { currentTool = "pen" }, colors = if (currentTool == "pen") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Edit, stringResource(R.string.label_pen))
                }
                IconButton(onClick = { currentTool = "highlighter" }, colors = if (currentTool == "highlighter") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Highlight, stringResource(R.string.label_highlighter))
                }
                IconButton(onClick = { currentTool = "eraser" }, colors = if (currentTool == "eraser") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.AutoFixNormal, stringResource(R.string.label_eraser))
                }
                IconButton(onClick = { currentTool = "line" }, colors = if (currentTool == "line") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.HorizontalRule, stringResource(R.string.label_line))
                }
                IconButton(onClick = { currentTool = "arrow" }, colors = if (currentTool == "arrow") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.ArrowForward, stringResource(R.string.label_arrow))
                }
                IconButton(onClick = { currentTool = "rectangle" }, colors = if (currentTool == "rectangle") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Rectangle, stringResource(R.string.label_rectangle))
                }
                IconButton(onClick = { currentTool = "square" }, colors = if (currentTool == "square") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.CropSquare, stringResource(R.string.label_square))
                }
                IconButton(onClick = { currentTool = "circle" }, colors = if (currentTool == "circle") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Circle, stringResource(R.string.label_circle))
                }
                IconButton(onClick = { if (elements.isNotEmpty()) { elements.removeAt(elements.size - 1); pathTrigger.value++ } }) {
                    Icon(Icons.Default.Undo, stringResource(R.string.btn_undo))
                }
                IconButton(onClick = { elements.clear(); pathTrigger.value++ }) {
                    Icon(Icons.Default.DeleteSweep, stringResource(R.string.btn_clear_all_notes))
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.label_size_colon), style = MaterialTheme.typography.labelSmall)
                Slider(value = currentThickness, onValueChange = { currentThickness = it }, valueRange = 1f..200f, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(24.dp).background(Color(currentColor), androidx.compose.foundation.shape.CircleShape).border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape))
            }
            
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val colors = listOf(Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.Gray, Color.White)
                colors.forEach { color ->
                    Box(modifier = Modifier.size(30.dp).background(color, androidx.compose.foundation.shape.CircleShape).border(if (currentColor == color.toArgb()) 2.dp else 1.dp, if (currentColor == color.toArgb()) MaterialTheme.colorScheme.primary else Color.LightGray, androidx.compose.foundation.shape.CircleShape).clickable { currentColor = color.toArgb() })
                }
                
                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                
                IconButton(onClick = { isDarkCanvas = !isDarkCanvas }) {
                    Icon(if (isDarkCanvas) Icons.Default.LightMode else Icons.Default.DarkMode, stringResource(R.string.label_toggle_canvas_theme))
                }
            }
        }
        
        IconButton(onClick = { onToggleToolbar(!isToolbarVisible) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(if (isToolbarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, stringResource(R.string.label_toggle_workspace))
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .background(if (isDarkCanvas) Color.Black else Color.White)
            .border(1.dp, Color.Gray)
            .clipToBounds()
            .pointerInput(currentTool, currentColor, currentThickness) {
                awaitEachGesture {
                    var isDrawing = false
                    var multiTouchActive = false
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val pressed = changes.filter { it.pressed }
                        
                        if (pressed.size >= 2) {
                            multiTouchActive = true
                            if (isDrawing) {
                                isDrawing = false
                                activePath.reset()
                                currentPathPoints.clear()
                                pathTrigger.value++
                            }
                            
                            val p1 = pressed[0]; val p2 = pressed[1]
                            val curDist = (p1.position - p2.position).getDistance()
                            val preDist = (p1.previousPosition - p2.previousPosition).getDistance()
                            
                            if (preDist > 0) scale *= (curDist / preDist)
                            
                            val move1 = p1.position - p1.previousPosition
                            val move2 = p2.position - p2.previousPosition
                            val dot = move1.x * move2.x + move1.y * move2.y
                            if (dot > 30f && (curDist / preDist) < 1.02f) scale *= 0.98f
                            
                            offset += (move1 + move2) / 2f
                            changes.forEach { it.consume() }
                        } else if (!multiTouchActive) {
                            val change = changes[0]
                            val adj = (change.position - offset) / scale
                            
                            if (change.changedToDown()) {
                                isDrawing = true
                                when (currentTool) {
                                    "pen", "highlighter", "eraser" -> {
                                        activePath.reset(); activePath.moveTo(adj.x, adj.y)
                                        currentPathPoints.clear(); currentPathPoints.add(adj)
                                    }
                                    else -> { shapeStart.value = adj; shapeEnd.value = adj }
                                }
                                pathTrigger.value++
                            } else if (change.pressed && isDrawing) {
                                when (currentTool) {
                                    "pen", "highlighter", "eraser" -> {
                                        activePath.lineTo(adj.x, adj.y)
                                        currentPathPoints.add(adj)
                                    }
                                    else -> { shapeEnd.value = adj }
                                }
                                pathTrigger.value++
                            } else if (change.changedToUp() && isDrawing) {
                                when (currentTool) {
                                    "pen", "highlighter", "eraser" -> {
                                        if (currentPathPoints.size > 1) {
                                            elements.add(DrawingElement.FreePath(currentPathPoints.map { it.toData() }, if(currentTool=="eraser") Color.White.toArgb() else currentColor, currentThickness, if(currentTool=="highlighter") 0.4f else 1f))
                                        }
                                        activePath.reset(); currentPathPoints.clear()
                                    }
                                    else -> {
                                        if (shapeStart.value != null && shapeEnd.value != null) {
                                            elements.add(DrawingElement.Shape(shapeType = when(currentTool){ "rectangle"->ShapeType.RECTANGLE; "square"->ShapeType.SQUARE; "circle"->ShapeType.CIRCLE; "arrow"->ShapeType.ARROW; else->ShapeType.LINE }, start = shapeStart.value!!.toData(), end = shapeEnd.value!!.toData(), color = currentColor, thickness = currentThickness))
                                        }
                                        shapeStart.value = null; shapeEnd.value = null
                                    }
                                }
                                isDrawing = false
                                pathTrigger.value++
                            }
                            change.consume()
                        }
                        
                        if (changes.all { !it.pressed }) break
                    }
                }
            }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                pathTrigger.value 
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, Offset.Zero)
                }) {
                    elements.forEach { drawElement(this, it) }
                    if (currentPathPoints.size > 1) drawPath(activePath, if(currentTool=="eraser") Color.White else Color(currentColor), alpha = if(currentTool=="highlighter") 0.4f else 1f, style = Stroke(width = currentThickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (shapeStart.value != null && shapeEnd.value != null) drawElement(this, DrawingElement.Shape(shapeType = when(currentTool){ "rectangle"->ShapeType.RECTANGLE; "square"->ShapeType.SQUARE; "circle"->ShapeType.CIRCLE; "arrow"->ShapeType.ARROW; else->ShapeType.LINE }, start = shapeStart.value!!.toData(), end = shapeEnd.value!!.toData(), color = currentColor, thickness = currentThickness))
                }
            }
        }
    }
}

fun drawElement(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, element: DrawingElement) {
    with(drawScope) {
        when (element) {
            is DrawingElement.FreePath -> {
                if (element.points.size > 1) {
                    val p = androidx.compose.ui.graphics.Path()
                    p.moveTo(element.points[0].x, element.points[0].y)
                    for (i in 1 until element.points.size) { p.lineTo(element.points[i].x, element.points[i].y) }
                    drawPath(p, Color(element.color), alpha = element.alpha, style = Stroke(width = element.thickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            is DrawingElement.Shape -> {
                val start = element.start.toOffset(); val end = element.end.toOffset()
                val color = Color(element.color); val thick = element.thickness
                val style = if (element.isFilled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = thick)
                when (element.shapeType) {
                    ShapeType.LINE -> drawLine(color, start, end, strokeWidth = thick, alpha = element.alpha)
                    ShapeType.RECTANGLE -> drawRect(color, start, androidx.compose.ui.geometry.Size(end.x - start.x, end.y - start.y), alpha = element.alpha, style = style)
                    ShapeType.SQUARE -> { val side = Math.min(Math.abs(end.x - start.x), Math.abs(end.y - start.y)); val sx = if (end.x > start.x) 1f else -1f; val sy = if (end.y > start.y) 1f else -1f; drawRect(color, start, androidx.compose.ui.geometry.Size(side * sx, side * sy), alpha = element.alpha, style = style) }
                    ShapeType.CIRCLE -> { val radius = Math.sqrt(Math.pow((end.x - start.x).toDouble(), 2.0) + Math.pow((end.y - start.y).toDouble(), 2.0)).toFloat(); drawCircle(color, radius, start, alpha = element.alpha, style = style) }
                    ShapeType.ARROW -> {
                        drawLine(color, start, end, strokeWidth = thick, alpha = element.alpha)
                        val angle = Math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()); val al = 20f
                        val h1 = Offset(end.x - al * Math.cos(angle - Math.PI / 6).toFloat(), end.y - al * Math.sin(angle - Math.PI / 6).toFloat())
                        val h2 = Offset(end.x - al * Math.cos(angle + Math.PI / 6).toFloat(), end.y - al * Math.sin(angle + Math.PI / 6).toFloat())
                        drawLine(color, end, h1, strokeWidth = thick, alpha = element.alpha); drawLine(color, end, h2, strokeWidth = thick, alpha = element.alpha)
                    }
                }
            }
        }
    }
}

@Composable
fun StatementEditor(rows: SnapshotStateList<StatementRow>, viewModel: ExpenseViewModel) {
    val results = remember(rows.toList()) { calculateStatementRows(rows) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.label_operation), modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.label_note), modifier = Modifier.weight(0.45f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.label_amount), modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        
        rows.forEachIndexed { index, row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                
                Box(modifier = Modifier.weight(0.25f)) {
                    var expanded by remember { mutableStateOf(false) }
                    val ops = listOf(
                        "add" to stringResource(R.string.label_add_op),
                        "less" to stringResource(R.string.label_less_op),
                        "multiply" to stringResource(R.string.label_multiply_op),
                        "divide" to stringResource(R.string.label_divide_op),
                        "subtotal" to stringResource(R.string.label_subtotal),
                        "grand_total" to stringResource(R.string.label_grand_total)
                    )
                    Text(
                        text = ops.find { it.first == row.operation }?.second ?: row.operation,
                        modifier = Modifier.clickable { expanded = true }.padding(4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ops.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                rows[index] = row.copy(operation = key)
                                expanded = false
                            })
                        }
                    }
                }

                OutlinedTextField(
                    value = row.description,
                    onValueChange = { rows[index] = row.copy(description = it) },
                    modifier = Modifier.weight(0.45f),
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text("...", style = MaterialTheme.typography.bodySmall) }
                )

                if (row.operation == "subtotal" || row.operation == "grand_total") {
                    Text(
                        text = viewModel.formatAmount(results[index]),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                } else {
                    var amtText by remember(row.amount) { mutableStateOf(if (row.amount == 0.0) "" else row.amount.toString()) }
                    OutlinedTextField(
                        value = amtText,
                        onValueChange = { 
                            amtText = it
                            it.toDoubleOrNull()?.let { d -> rows[index] = row.copy(amount = d) }
                        },
                        modifier = Modifier.weight(0.3f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                
                IconButton(onClick = { rows.removeAt(index) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        Button(
            onClick = { rows.add(StatementRow()) },
            modifier = Modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.btn_add), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun StatementTableView(data: StatementData, viewModel: ExpenseViewModel) {
    val results = remember(data.rows) { calculateStatementRows(data.rows) }

    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray.copy(alpha = 0.3f))) {
        
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
            Text(stringResource(R.string.label_operation), modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.label_note), modifier = Modifier.weight(0.45f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.label_amount), modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }

        data.rows.forEachIndexed { index, row ->
            val isTotal = row.operation == "subtotal" || row.operation == "grand_total"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isTotal) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val opLabel = when(row.operation) {
                    "add" -> stringResource(R.string.label_add_op)
                    "less" -> stringResource(R.string.label_less_op)
                    "multiply" -> stringResource(R.string.label_multiply_op)
                    "divide" -> stringResource(R.string.label_divide_op)
                    "subtotal" -> stringResource(R.string.label_subtotal)
                    "grand_total" -> stringResource(R.string.label_grand_total)
                    else -> row.operation
                }
                Text(opLabel, modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.bodyMedium)
                Text(row.description, modifier = Modifier.weight(0.45f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = viewModel.formatAmount(if (isTotal) results[index] else row.amount),
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            Divider(color = Color.Gray.copy(alpha = 0.2f))
        }
    }
}

fun calculateStatementRows(rows: List<StatementRow>): List<Double> {
    val results = MutableList(rows.size) { 0.0 }
    var currentGrandTotalSum = 0.0
    
    var runningValue = 0.0
    var hasStarted = false

    for (i in rows.indices) {
        val row = rows[i]
        when (row.operation) {
            "add" -> {
                if (!hasStarted) { runningValue = row.amount; hasStarted = true }
                else runningValue += row.amount
            }
            "less" -> {
                if (!hasStarted) { runningValue = -row.amount; hasStarted = true }
                else runningValue -= row.amount
            }
            "multiply" -> {
                if (!hasStarted) { runningValue = 0.0; hasStarted = true }
                else runningValue *= row.amount
            }
            "divide" -> {
                if (!hasStarted) { runningValue = 0.0; hasStarted = true }
                else if (row.amount != 0.0) runningValue /= row.amount
            }
            "subtotal" -> {
                results[i] = runningValue
                currentGrandTotalSum += runningValue
                runningValue = 0.0
                hasStarted = false
                continue
            }
            "grand_total" -> {
                results[i] = currentGrandTotalSum
                currentGrandTotalSum = 0.0
                runningValue = 0.0
                hasStarted = false
                continue
            }
        }
        results[i] = row.amount
    }
    return results
}
