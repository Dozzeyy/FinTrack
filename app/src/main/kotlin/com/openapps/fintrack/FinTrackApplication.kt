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

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.openapps.fintrack.data.EncryptedPrefsHelper
import com.openapps.fintrack.data.RateUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    override fun onCreate() {
        super.onCreate()
        EncryptedPrefsHelper.init(this)
        
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "en") ?: "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        
        scheduleExchangeRateUpdates()
    }

    private fun scheduleExchangeRateUpdates() {
        val workRequest = PeriodicWorkRequestBuilder<RateUpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .addTag("exchange_rate_update")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "exchange_rate_update",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
