/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("auto_read_enabled", false)) return

            val currencies = (prefs.getString("sms_currencies", "Rs, USD") ?: "Rs, USD").split(",").map { it.trim() }
            val keywords = (prefs.getString("sms_keywords", "debit, credit, spent") ?: "debit, credit, spent").split(",").map { it.trim() }
            val conditionType = prefs.getString("sms_condition_type", "OR") ?: "OR"

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                val body = msg.messageBody
                
                val matchesA = currencies.any { body.contains(it, ignoreCase = true) }
                val matchesB = keywords.any { body.contains(it, ignoreCase = true) }

                val isMatch = when (conditionType) {
                    "OR" -> matchesA || matchesB
                    "AND" -> matchesA && matchesB
                    "ONLY_A" -> matchesA
                    "ONLY_B" -> matchesB
                    else -> matchesA || matchesB
                }

                if (isMatch) {
                    val amount = parseAmount(body, currencies)
                    showTransactionNotification(context, body, amount)
                }
            }
        }
    }

    private fun parseAmount(body: String, currencies: List<String>): Double? {
        for (curr in currencies) {
            val pattern = """(?i)${Regex.escape(curr)}\s*([\d,]+\.?\d*)""".toRegex()
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues.get(1).replace(",", "").toDoubleOrNull()
            }
        }
        val genericPattern = """(?i)(?:spent|amt|amount|rs\.?|inr|usd)\s*([\d,]+\.?\d*)""".toRegex()
        val genericMatch = genericPattern.find(body)
        return genericMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    private fun showTransactionNotification(context: Context, body: String, amount: Double?) {
        val channelId = "transaction_capture"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Transaction Capture", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("sms_body", body) 
            amount?.let { putExtra("amount", it) }
            putExtra("date", LocalDate.now().format(DateTimeFormatter.ISO_DATE))
            putExtra("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            putExtra("navigate_to", "add_transaction")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            System.currentTimeMillis().toInt(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("Transaction Detected")
            .setContentText(if (amount != null) "Amount: $amount. Tap to record." else "Tap to record transaction.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
