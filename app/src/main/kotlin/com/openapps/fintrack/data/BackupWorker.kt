/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

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

        val tempSnapshot = File(applicationContext.cacheDir, "backup_snap.db")
        val finalFile = File(applicationContext.cacheDir, "backup_final.db")

        return try {
            val dbFile = applicationContext.getDatabasePath("expenses_database")
            
            // Checkpoint database to ensure WAL is merged
            val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)
            database.checkpoint()
            
            FileInputStream(dbFile).use { input ->
                FileOutputStream(tempSnapshot).use { output ->
                    input.copyTo(output)
                }
            }

            if (encryptBackup && masterPassword.isNotEmpty()) {
                val result = EncryptionService.encryptFile(tempSnapshot, finalFile, masterPassword)
                if (result.isFailure) throw Exception("Encryption failed")
            } else {
                tempSnapshot.copyTo(finalFile, overwrite = true)
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
