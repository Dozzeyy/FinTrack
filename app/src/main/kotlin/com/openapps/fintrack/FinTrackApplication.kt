/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
