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

package com.openapps.fintrack.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openapps.fintrack.domain.repository.FinanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FinanceRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        val appPrefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val path = prefs.getString("path", null)
        
        if (path == null || path == "Not Set") {
            sendNotification("Backup Failed", "Backup path not set.")
            return Result.failure()
        }

        val encryptBackup = prefs.getBoolean("encrypt_scheduled_backup", false)
        val masterPassword = EncryptedPrefsHelper.getString("remote_master_password", "") ?: ""
        val secureMode = appPrefs.getBoolean("secure_mode_enabled", false)

        val tempSnapshot = File(applicationContext.cacheDir, "backup_snap.db")
        val finalFile = File(applicationContext.cacheDir, "backup_final.db")

        return try {
            val dbFile = applicationContext.getDatabasePath("expenses_database")
            val encryptedAtRestFile = File(dbFile.path + ".xpt")

            AppDatabase.databaseMutex.withLock {
                if (secureMode && encryptedAtRestFile.exists()) {
                
                    encryptedAtRestFile.copyTo(finalFile, overwrite = true)
                } else {
                
                    repository.checkpoint()
                    AppDatabase.closeDatabase()
                    
                    if (dbFile.exists()) {
                        FileInputStream(dbFile).use { input ->
                            FileOutputStream(tempSnapshot).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if ((encryptBackup || secureMode) && masterPassword.isNotEmpty()) {
                            val passChars = masterPassword.toCharArray()
                            val result = EncryptionService.encryptFile(tempSnapshot, finalFile, passChars)
                            passChars.fill('\u0000')
                            if (result.isFailure) throw Exception("Encryption failed")
                        } else if (encryptBackup || secureMode) {
                            
                            throw Exception("Encryption required but master password is not set.")
                        } else {
                            tempSnapshot.copyTo(finalFile, overwrite = true)
                        }
                    } else {
                        throw Exception("Database file not found")
                    }
                }
            }

            val treeUri = Uri.parse(path)
            val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
            
            if (pickedDir == null || !pickedDir.canWrite()) {
                sendNotification("Backup Failed", "Cannot write to selected directory.")
                return Result.failure()
            }

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            val backupFileName = if (encryptBackup) "expenses_backup_$timestamp.ftd" else "expenses_backup_$timestamp.db"
            
            val newFile = pickedDir.createFile("application/octet-stream", backupFileName)
            if (newFile == null) {
                sendNotification("Backup Failed", "Could not create backup file in directory.")
                return Result.failure()
            }

            applicationContext.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                FileInputStream(finalFile).use { input ->
                    input.copyTo(output)
                }
            }
            
            sendNotification("Backup Successful", "Scheduled backup '$backupFileName' saved.")
            Result.success()
        } catch (e: Exception) {
            sendNotification("Backup Failed", "Scheduled backup failed: ${e.message}")
            Result.failure()
        } finally {
            if (tempSnapshot.exists()) tempSnapshot.delete()
            if (finalFile.exists()) finalFile.delete()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "backup_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Backup Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }
}
