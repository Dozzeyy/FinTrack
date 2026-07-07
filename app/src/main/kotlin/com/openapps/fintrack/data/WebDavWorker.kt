/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class WebDavWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val dbFile = applicationContext.getDatabasePath("expenses_database")
        val ef = File(dbFile.path + ".xpt")
        
        // Safety: If database is encrypted at rest, background workers cannot run
        if (ef.exists() && !dbFile.exists()) {
            Log.w("WebDavWorker", "Database is encrypted. Skipping background processing.")
            return Result.success()
        }

        val enabled = prefs.getBoolean("remote_sync_enabled", false)
        if (!enabled) return Result.success()

        val url = EncryptedPrefsHelper.getString("webdav_url", "") ?: ""
        val username = EncryptedPrefsHelper.getString("webdav_user", "") ?: ""
        val password = EncryptedPrefsHelper.getString("webdav_pass", "") ?: ""
        
        val encryptRemote = prefs.getBoolean("encrypt_remote_enabled", false)
        val masterPassword = EncryptedPrefsHelper.getString("remote_master_password", "") ?: ""
        val secureMode = prefs.getBoolean("secure_mode_enabled", false)

        if (url.isBlank()) return Result.failure()

        val tempSnapshot = File(applicationContext.cacheDir, "webdav_sync_snap.db")
        val finalFile = File(applicationContext.cacheDir, "webdav_sync_final.db")
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        
        prefs.edit()
            .putString("last_sync_attempt_time", nowStr)
            .putString("last_sync_attempt_error", "")
            .putString("last_sync_status", "Syncing...")
            .apply()

        return try {
            val dbFile = applicationContext.getDatabasePath("expenses_database")
            val encryptedAtRestFile = File(dbFile.path + ".xpt")

            AppDatabase.databaseMutex.withLock {
                if (secureMode && encryptedAtRestFile.exists()) {
                    // If in Ultra Secure mode and DB is already encrypted, sync the encrypted file directly
                    encryptedAtRestFile.copyTo(finalFile, overwrite = true)
                } else {
                    if (dbFile.exists()) {
                        val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)
                        database.checkpoint()
                        AppDatabase.closeDatabase() // Release file locks for clean copy

                        FileInputStream(dbFile).use { input ->
                            FileOutputStream(tempSnapshot).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if ((encryptRemote || secureMode) && masterPassword.isNotEmpty()) {
                            val encResult = EncryptionService.encryptFile(tempSnapshot, finalFile, masterPassword)
                            if (encResult.isFailure) {
                                throw Exception("Encryption failed: ${encResult.exceptionOrNull()?.message}")
                            }
                        } else if (encryptRemote || secureMode) {
                            throw Exception("Encryption required but master password is not set.")
                        } else {
                            tempSnapshot.copyTo(finalFile, overwrite = true)
                        }
                    } else {
                        throw Exception("Database file not found")
                    }
                }
            }

            if (!finalFile.exists()) {
                throw Exception("Failed to prepare synchronization file.")
            }

            // Joplin-style reliable configuration
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val baseUrl = url.toHttpUrlOrNull() ?: throw Exception("Invalid URL")
            val fileName = if ((encryptRemote || secureMode) && masterPassword.isNotEmpty()) "expenses_database_sync.xpt" else "expenses_database_sync.db"
            val fullUrl = baseUrl.newBuilder().addPathSegment(fileName).build().toString()
            val tmpUrl = "$fullUrl.tmp"

            var lastError: String? = null
            for (attempt in 1..3) {
                try {
                    if (attempt > 1) kotlinx.coroutines.delay(5000)

                    // 1. PUT to temporary file
                    val request = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(username, password))
                        .header("User-Agent", "FinTrack-Android")
                        .put(finalFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                            if (response.code == 429) throw Exception("429 Too Many Requests")
                            throw Exception("PUT failed: ${response.code}")
                        }
                    }

                    // 2. Atomic MOVE to final path
                    val moveRequest = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(username, password))
                        .header("User-Agent", "FinTrack-Android")
                        .header("Destination", fullUrl)
                        .header("Overwrite", "T")
                        .method("MOVE", null)
                        .build()

                    client.newCall(moveRequest).execute().use { response ->
                        if (response.isSuccessful || response.code == 201 || response.code == 204) {
                            val time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            val size = String.format("%.2f KB", finalFile.length() / 1024.0)
                            
                            prefs.edit()
                                .putString("last_sync_time", time)
                                .putString("last_sync_success_time", time)
                                .putString("last_sync_size", size)
                                .putString("last_sync_status", "Success ($time)")
                                .putString("last_sync_attempt_error", "")
                                .apply()
                            
                            return Result.success()
                        } else {
                            throw Exception("MOVE failed: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message
                    if (attempt == 3) throw e
                }
            }
            Result.retry()
        } catch (e: Exception) {
            SafeLogger.e("Sync Critical Error", e)
            val errorMsg = e.localizedMessage ?: "Unknown error"
            sendFailureNotification("Auto Sync Failed", errorMsg)
            prefs.edit()
                .putString("last_sync_status", "Failed")
                .putString("last_sync_attempt_error", errorMsg)
                .apply()
            Result.retry()
        } finally {
            if (tempSnapshot.exists()) tempSnapshot.delete()
            if (finalFile.exists()) finalFile.delete()
        }
    }

    private fun sendFailureNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sync_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Remote Sync", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1003, notification)
    }
}
