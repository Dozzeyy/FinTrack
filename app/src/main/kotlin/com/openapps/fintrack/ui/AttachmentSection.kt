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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.openapps.fintrack.R
import java.io.File
import java.util.UUID

@Composable
fun AttachmentSection(
    viewModel: ExpenseViewModel,
    state: AddTransactionState?,
    transactionNumber: String?,
    readOnly: Boolean
) {
    if (!viewModel.attachmentsEnabled) return

    val context = LocalContext.current
    var existingFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach {
            state?.attachments?.add(it.toString())
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { state?.attachments?.add(it.toString()) }
        }
    }

    fun launchCamera() {
        val cacheDir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val tempFile = File(cacheDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to capture photos", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(transactionNumber, viewModel.attachmentFolderUri) {
        if (transactionNumber != null && viewModel.attachmentFolderUri != null) {
            val safeNum = transactionNumber.replace("/", "_")
            val folderUri = Uri.parse(viewModel.attachmentFolderUri!!)
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            val files = folder?.listFiles()?.filter { it.name?.startsWith(safeNum) == true } ?: emptyList()
            existingFiles = files
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (existingFiles.isNotEmpty() || state?.attachments?.isNotEmpty() == true) {
            Text(stringResource(R.string.settings_attachments), style = MaterialTheme.typography.labelMedium)
        }
        existingFiles.forEach { file ->
            Text(
                text = file.name ?: stringResource(R.string.label_attachment),
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(file.uri, file.type ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
            )
        }
        
        state?.attachments?.forEachIndexed { index, uriStr ->
            Text(
                text = stringResource(R.string.label_new_attachment, index + 1),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (!readOnly && viewModel.attachmentFolderUri != null) {
            var showOptions by remember { mutableStateOf(false) }
            
            Button(
                onClick = { showOptions = true },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.btn_attach))
            }
            
            if (showOptions) {
                AlertDialog(
                    onDismissRequest = { showOptions = false },
                    title = { Text(stringResource(R.string.title_add_attachment)) },
                    text = {
                        Column {
                            TextButton(onClick = {
                                showOptions = false
                                galleryLauncher.launch("image/*")
                            }) { Text(stringResource(R.string.btn_upload_from_gallery)) }
                            TextButton(onClick = {
                                showOptions = false
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    launchCamera()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }) { Text(stringResource(R.string.btn_capture_from_camera)) }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showOptions = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                )
            }
        }
    }
}
