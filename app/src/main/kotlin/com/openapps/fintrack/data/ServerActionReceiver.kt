/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_SERVER") {
            val database = AppDatabase.getDatabase(context, kotlinx.coroutines.GlobalScope)
            val dao = database.expenseDao()
            ServerManager.getInstance(context, dao).stopServer()
        }
    }
}
