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
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class WebDavWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("remote_sync_enabled", false)
        if (!enabled) return Result.success()

        val url = EncryptedPrefsHelper.getString("webdav_url", "") ?: ""
        val username = EncryptedPrefsHelper.getString("webdav_username", "") ?: ""
        val password = EncryptedPrefsHelper.getString("webdav_password", "") ?: ""
        
        val encryptRemote = prefs.getBoolean("encrypt_remote_enabled", false)
        val masterPassword = EncryptedPrefsHelper.getString("remote_master_password", "") ?: ""

        if (url.isBlank()) return Result.failure()

        val tempSnapshot = File(applicationContext.cacheDir, "webdav_sync_snap.db")
        val finalFile = File(applicationContext.cacheDir, "webdav_sync_final.db")
        
        return try {
            val dbFile = applicationContext.getDatabasePath("expenses_database")
            
            val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)
            database.checkpoint()

            FileInputStream(dbFile).use { input ->
                FileOutputStream(tempSnapshot).use { output ->
                    input.copyTo(output)
                }
            }

            if (encryptRemote && masterPassword.isNotEmpty()) {
                val encResult = EncryptionService.encryptFile(tempSnapshot, finalFile, masterPassword)
                if (encResult.isFailure) {
                    SafeLogger.e("Auto Sync Failed: Encryption error")
                    sendFailureNotification("Sync Failed", "Encryption failed")
                    return Result.failure()
                }
            } else {
                tempSnapshot.copyTo(finalFile, overwrite = true)
            }

            // Joplin-style reliable configuration
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val fileName = "expenses_database_sync.db"
            val fullUrl = if (url.endsWith("/")) url + fileName else "$url/$fileName"
            val tmpUrl = "$fullUrl.tmp"

            var lastError: String? = null
            for (attempt in 1..3) {
                try {
                    SafeLogger.d("Auto Sync attempt $attempt starting...")
                    
                    // 1. PUT to temporary file
                    val request = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(username, password))
                        .header("User-Agent", "FinTrack-Android")
                        .put(finalFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                            throw Exception("PUT to .tmp failed: ${response.code}")
                        }
                    }

                    // 2. Atomic MOVE to final path (The Joplin method)
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
                                .putString("last_sync_size", size)
                                .apply()
                            
                            SafeLogger.d("Auto Sync Success at $time")
                            return Result.success()
                        } else {
                            lastError = "Move failed: ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message
                    // Attempt to remove corrupted tmp file
                    try {
                        val delReq = Request.Builder()
                            .url(tmpUrl)
                            .header("Authorization", Credentials.basic(username, password))
                            .delete().build()
                        client.newCall(delReq).execute().close()
                    } catch (ignore: Exception) {}

                    if (attempt < 3) {
                        SafeLogger.d("Auto Sync attempt $attempt failed, retrying in 10s...")
                        kotlinx.coroutines.delay(10000)
                    }
                }
            }

            sendFailureNotification("Auto Sync Failed", lastError ?: "Retry exhausted")
            Result.retry()
        } catch (e: Exception) {
            SafeLogger.e("Sync Critical Error", e)
            sendFailureNotification("Sync Failed", e.message ?: "Network error")
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
