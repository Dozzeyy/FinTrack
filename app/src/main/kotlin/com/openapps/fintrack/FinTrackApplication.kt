/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openapps.fintrack.data.EncryptedPrefsHelper
import com.openapps.fintrack.data.RateUpdateWorker
import java.util.concurrent.TimeUnit

class FinTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EncryptedPrefsHelper.init(this)
        scheduleExchangeRateUpdates()
    }

    private fun scheduleExchangeRateUpdates() {
        // PRIVACY-FIRST: We fetch the entire dataset anonymously once a day.
        // No transaction data is sent to external servers.
        val workRequest = PeriodicWorkRequestBuilder<RateUpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) // Delay first run to not impact app startup
            .addTag("exchange_rate_update")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "exchange_rate_update",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
