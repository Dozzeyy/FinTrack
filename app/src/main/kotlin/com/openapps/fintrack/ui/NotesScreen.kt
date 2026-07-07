/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
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
fun NotesScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
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
    var actionType by remember { mutableStateOf("") } // "move" or "copy"

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
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search notes...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                        } else {
                            Text("Notebooks & Pages")
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showTagFilter = true }) {
                            Icon(Icons.Default.Label, contentDescription = "Filter by Tags", tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(if (isSearching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            },
            floatingActionButton = {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (showFabMenu) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 72.dp)
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    noteTypeToAdd = "drawing"
                                    isAddingNote = true
                                    showFabMenu = false
                                },
                                icon = { Icon(Icons.Default.Brush, null) },
                                text = { Text("Drawing") },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    noteTypeToAdd = "checklist"
                                    isAddingNote = true
                                    showFabMenu = false
                                },
                                icon = { Icon(Icons.Default.List, null) },
                                text = { Text("Checklist") },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    noteTypeToAdd = "text"
                                    isAddingNote = true
                                    showFabMenu = false
                                },
                                icon = { Icon(Icons.Default.Notes, null) },
                                text = { Text("Text Note") },
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
                        Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = "Add Note")
                    }
                }
            }
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Left Pane: Notebooks
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
                        Text("Notebooks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddNotebookDialog = true }) {
                            Icon(Icons.Default.AddCircle, "Add Notebook", modifier = Modifier.size(20.dp))
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
                                Text("All Notes", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
                                        text = { Text("Rename") },
                                        onClick = {
                                            showNotebookMenu = false
                                            notebookToRename = notebook
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
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
                
                // Vertical Divider
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

                // Right Pane: Pages (Notes)
                Column(modifier = Modifier.weight(0.65f).fillMaxSize()) {
                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (isSearching || selectedTagIds.isNotEmpty()) "No matches" else "Empty", color = Color.Gray)
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
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = when(note.type) {
                                                        "checklist" -> Icons.Default.Checklist
                                                        "drawing" -> Icons.Default.Brush
                                                        else -> Icons.Default.Notes
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(text = note.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = if (note.type == "checklist") {
                                                    try {
                                                        val items = Json.decodeFromString<List<ChecklistItem>>(note.content)
                                                        items.joinToString(", ") { "${it.text} (${it.quantity})" }
                                                    } catch (e: Exception) { note.content }
                                                } else if (note.type == "drawing") {
                                                    "Drawing Note"
                                                } else {
                                                    note.content
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    DropdownMenu(expanded = showNoteMenu, onDismissRequest = { showNoteMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Move") },
                                            onClick = {
                                                showNoteMenu = false
                                                noteForAction = note
                                                actionType = "move"
                                            },
                                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy") },
                                            onClick = {
                                                showNoteMenu = false
                                                noteForAction = note
                                                actionType = "copy"
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
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

    if (showAddNotebookDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddNotebookDialog = false },
            title = { Text("New Notebook") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveNotebook(name)
                        showAddNotebookDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddNotebookDialog = false }) { Text("Cancel") } }
        )
    }

    if (notebookToRename != null) {
        var name by remember { mutableStateOf(notebookToRename!!.name) }
        AlertDialog(
            onDismissRequest = { notebookToRename = null },
            title = { Text("Rename Notebook") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveNotebook(notebookToRename!!.copy(name = name))
                        notebookToRename = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { notebookToRename = null }) { Text("Cancel") } }
        )
    }

    if (notebookToDelete != null) {
        AlertDialog(
            onDismissRequest = { notebookToDelete = null },
            title = { Text("Delete Notebook?") },
            text = { Text("This will permanently delete '${notebookToDelete!!.name}' and all pages inside it.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNotebook(notebookToDelete!!)
                    notebookToDelete = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { notebookToDelete = null }) { Text("Cancel") } }
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
            title = { Text(if (actionType == "move") "Move to Notebook" else "Copy to Notebook") },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("None (General)") },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewNoteScreen(viewModel: ExpenseViewModel, note: Note, onBack: () -> Unit, onEdit: () -> Unit, onUpdateNote: (Note) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val scope = rememberCoroutineScope()
    
    val allTags by viewModel.getAllTags().collectAsState(initial = emptyList())
    val noteTags = remember(note.tags, allTags) {
        val tagIds = note.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        allTags.filter { it.id in tagIds }
    }

    val dateFormatter = remember { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()) }

    // Local state for checklist items to allow toggling/quantity change
    val checklistItems = remember(note.content, note.type) {
        if (note.type == "checklist") {
            try {
                Json.decodeFromString<List<ChecklistItem>>(note.content).toMutableStateList()
            } catch (e: Exception) {
                mutableStateListOf<ChecklistItem>()
            }
        } else mutableStateListOf<ChecklistItem>()
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
                title = { Text(if (note.type == "checklist") "Checklist" else if(note.type == "drawing") "Drawing" else "View Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
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
            FloatingActionButton(
                onClick = { if (!isServerRunning) onEdit() },
                containerColor = if (isServerRunning) Color.Gray else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Note")
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
                Text(text = note.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (noteTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tags: " + noteTags.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(
                        text = "Created: " + dateFormatter.format(java.util.Date(note.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    note.editedAt?.let {
                        Text(
                            text = "Last Edit: " + dateFormatter.format(java.util.Date(it)),
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
                } else {
                    Text(text = note.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(note)
                    onBack()
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
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

    // Checklist specific state
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

    var newItemText by remember { mutableStateOf("") }
    var isCanvasFullWorkspace by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (note == null) (if (initialType == "checklist") "New Checklist" else if(initialType == "drawing") "New Drawing" else "New Note") else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    label = { Text("Header") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(16.dp))
            }

            if (initialType == "checklist") {
                Text("Line Items", style = MaterialTheme.typography.titleSmall)
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
                        label = { Text("Add Item") },
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
                
                // We need to capture the elements to save
                LaunchedEffect(drawingElements.toList()) {
                    content = Json.encodeToString(DrawingData(drawingElements.toList()))
                }
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
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
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.isNotBlank() && !isServerRunning) {
                            viewModel.editingNote = note
                            val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                            
                            val finalContent = if (initialType == "checklist") {
                                Json.encodeToString(checklistItems.toList())
                            } else {
                                content
                            }
                            
                            viewModel.saveNote(title, finalContent, tagsString, initialType)
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank() && !isServerRunning
                ) {
                    Text("Save")
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
    
    // Zoom and Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDarkCanvas by remember { mutableStateOf(false) }

    // Optimization: Path trigger to avoid excessive list allocations
    val activePath = remember { androidx.compose.ui.graphics.Path() }
    val pathTrigger = remember { mutableIntStateOf(0) }
    val currentPathPoints = remember { mutableListOf<Offset>() }
    
    var shapeStart = remember { mutableStateOf<Offset?>(null) }
    var shapeEnd = remember { mutableStateOf<Offset?>(null) }

    Column {
        if (isToolbarVisible) {
            // Toolbar 1: Tools
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { currentTool = "pen" }, colors = if (currentTool == "pen") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Edit, "Pen")
                }
                IconButton(onClick = { currentTool = "highlighter" }, colors = if (currentTool == "highlighter") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Highlight, "Highlighter")
                }
                IconButton(onClick = { currentTool = "eraser" }, colors = if (currentTool == "eraser") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.AutoFixNormal, "Eraser")
                }
                IconButton(onClick = { currentTool = "line" }, colors = if (currentTool == "line") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.HorizontalRule, "Line")
                }
                IconButton(onClick = { currentTool = "arrow" }, colors = if (currentTool == "arrow") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.ArrowForward, "Arrow")
                }
                IconButton(onClick = { currentTool = "rectangle" }, colors = if (currentTool == "rectangle") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Rectangle, "Rectangle")
                }
                IconButton(onClick = { currentTool = "square" }, colors = if (currentTool == "square") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.CropSquare, "Square")
                }
                IconButton(onClick = { currentTool = "circle" }, colors = if (currentTool == "circle") IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
                    Icon(Icons.Default.Circle, "Circle")
                }
                IconButton(onClick = { if (elements.isNotEmpty()) { elements.removeAt(elements.size - 1); pathTrigger.value++ } }) {
                    Icon(Icons.Default.Undo, "Undo")
                }
                IconButton(onClick = { elements.clear(); pathTrigger.value++ }) {
                    Icon(Icons.Default.DeleteSweep, "Clear All")
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Size: ", style = MaterialTheme.typography.labelSmall)
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
                    Icon(if (isDarkCanvas) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle Canvas Theme")
                }
            }
        }
        
        IconButton(onClick = { onToggleToolbar(!isToolbarVisible) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(if (isToolbarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle Workspace")
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
                                // Cancel drawing if second finger touches
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
                            // Fingers moving in same direction = Zoom Out
                            if (dot > 30f && (curDist / preDist) < 1.02f) scale *= 0.98f
                            
                            offset += (move1 + move2) / 2f
                            changes.forEach { it.consume() }
                        } else if (!multiTouchActive) {
                            // Only allow drawing if it's a single-touch gesture from the start
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
