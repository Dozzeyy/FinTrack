/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import java.io.File

class ServerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_SERVER") {
            val dbFile = context.getDatabasePath("expenses_database")
            val ef = File(dbFile.path + ".xpt")
            
            // Safety: Don't stop server if it would trigger DB access while encrypted
            if (ef.exists() && !dbFile.exists()) return

            val database = try { 
                AppDatabase.getDatabase(context, kotlinx.coroutines.GlobalScope) 
            } catch (e: Exception) { 
                Log.e("ServerActionReceiver", "Failed to get database: ${e.message}")
                return 
            }
            val dao = database.expenseDao()
            ServerManager.getInstance(context, dao).stopServer()
        }
    }
}
