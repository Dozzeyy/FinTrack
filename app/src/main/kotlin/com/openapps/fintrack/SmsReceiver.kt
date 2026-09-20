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

package com.openapps.fintrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openapps.fintrack.domain.repository.FinanceRepository
import com.openapps.fintrack.domain.usecase.ProcessSmsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var processSmsUseCase: ProcessSmsUseCase
    @Inject lateinit var repository: FinanceRepository

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("auto_read_enabled", false)) return

            val currencies = (prefs.getString("sms_currencies", "Rs, USD, INR") ?: "Rs, USD, INR").split(",").map { it.trim() }
            val baseCurrency = prefs.getString("base_currency", "INR") ?: "INR"

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                val body = msg.messageBody
                val sender = msg.displayOriginatingAddress ?: ""
                
                receiverScope.launch {
                    try {
                        processSmsUseCase(body, sender, currencies, baseCurrency)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error processing SMS", e)
                    }
                }
            }
        }
    }
}
