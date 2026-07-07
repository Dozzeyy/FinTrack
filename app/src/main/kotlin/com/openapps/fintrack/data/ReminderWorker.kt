/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val dbFile = applicationContext.getDatabasePath("expenses_database")
        val ef = File(dbFile.path + ".xpt")
        
        if (ef.exists() && !dbFile.exists()) {
            Log.w("ReminderWorker", "Database is encrypted. Skipping background processing.")
            return Result.success()
        }

        val remindersEnabled = prefs.getBoolean("reminders_enabled", false)
        val ccAlertEnabled = prefs.getBoolean("cc_alert_enabled", false)

        if (remindersEnabled) {
            val message = prefs.getString("reminder_message", "Hey, Time to record expenses now") ?: "Hey, Time to record expenses now"
            sendNotification(2001, "FinTrack Reminder", message)
        }

        if (ccAlertEnabled) {
            checkUpcomingLoans()
        }

        return Result.success()
    }

    private suspend fun checkUpcomingLoans() {
        val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)
        val dao = database.expenseDao()
        val loans = dao.getAllActiveLoans().first()
        val today = LocalDate.now()

        loans.forEach { loan ->
            val dueDate = Instant.ofEpochMilli(loan.nextDueDate).atZone(ZoneId.systemDefault()).toLocalDate()
            val daysUntil = ChronoUnit.DAYS.between(today, dueDate)
            
            if (daysUntil == 3L) {
                val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val useMillions = prefs.getBoolean("use_millions_system", false)
                val formattedAmount = if (useMillions) {
                    java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(loan.installmentAmount).replace("$", "")
                } else {
                    java.text.DecimalFormat("##,##,##,###.##").format(loan.installmentAmount)
                }

                sendNotification(
                    3000 + loan.id.toInt(),
                    "Loan Payment Due",
                    "Installment for '${loan.name}' is due in 3 days ($formattedAmount)"
                )
            }
        }
    }

    private fun sendNotification(id: Int, title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Expense Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }
}
