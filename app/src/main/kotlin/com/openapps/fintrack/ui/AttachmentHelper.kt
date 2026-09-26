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

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile

fun copyAttachmentsToSaf(context: Context, folderUriStr: String?, txnNumber: String, attachments: List<String>) {
    if (folderUriStr.isNullOrEmpty()) return
    val folderUri = Uri.parse(folderUriStr)
    val resolver = context.contentResolver
    val folderDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return
    
    val safeTxnNumber = txnNumber.replace("/", "_")
    
    attachments.forEachIndexed { index, uriString ->
        try {
            val sourceUri = Uri.parse(uriString)
            val mimeType = resolver.getType(sourceUri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val fileName = "${safeTxnNumber}_${index + 1}.$ext"
            
            val newFile = folderDoc.createFile(mimeType, fileName)
            if (newFile != null) {
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
