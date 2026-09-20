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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openapps.fintrack.domain.repository.FinanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FinanceRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
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
        val loans = repository.getAllActiveLoans().first()
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
