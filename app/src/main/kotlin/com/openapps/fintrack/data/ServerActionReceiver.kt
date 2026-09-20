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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openapps.fintrack.domain.repository.FinanceRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ServerActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: FinanceRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_SERVER") {
            ServerManager.getInstance(context, repository).stopServer()
        }
    }
}
