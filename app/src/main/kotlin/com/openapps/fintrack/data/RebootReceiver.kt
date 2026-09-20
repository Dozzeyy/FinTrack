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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            rescheduleAlarms(context)
        }
    }

    private fun rescheduleAlarms(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val remindersEnabled = prefs.getBoolean("reminders_enabled", false)
        if (!remindersEnabled) return

        val reminderTime = prefs.getString("reminder_time", "20:00") ?: "20:00"
        val reminderFrequency = prefs.getInt("reminder_frequency", 1)
        val reminderUnit = prefs.getString("reminder_unit", "day/s") ?: "day/s"

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val receiverIntent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2001, receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = reminderTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val min = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intervalMillis = when (reminderUnit) {
            "day/s" -> reminderFrequency.toLong() * 24 * 60 * 60 * 1000
            "week/s" -> reminderFrequency.toLong() * 7 * 24 * 60 * 60 * 1000
            "month/s" -> reminderFrequency.toLong() * 30 * 24 * 60 * 60 * 1000
            "year/s" -> reminderFrequency.toLong() * 365 * 24 * 60 * 60 * 1000
            else -> 24 * 60 * 60 * 1000
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            intervalMillis,
            pendingIntent
        )
    }
}
