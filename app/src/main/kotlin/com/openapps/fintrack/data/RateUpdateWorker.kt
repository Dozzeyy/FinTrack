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

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.openapps.fintrack.domain.repository.FinanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

@HiltWorker
class RateUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FinanceRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val dbFile = applicationContext.getDatabasePath("expenses_database")
        val ef = File(dbFile.path + ".xpt")
        
        if (ef.exists() && !dbFile.exists()) {
            Log.w("RateUpdateWorker", "Database is encrypted. Skipping background processing.")
            return@withContext Result.success()
        }

        setProgress(Data.Builder().putString("status", "Initializing...").build())

        val baseCurrency = EncryptedPrefsHelper.getString("base_currency", null)
            ?: prefs.getString("base_currency", "USD") ?: "USD"
        
        try {
            setProgress(Data.Builder().putString("status", "Connecting to ECB...").build())
            
            val client = DohNetworkClient.createClientBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "HTTP Error: ${response.code}"
                return@withContext Result.failure(Data.Builder().putString("error", err).build())
            }

            setProgress(Data.Builder().putString("status", "Downloading data...").build())
            val xmlData = response.body?.string() ?: return@withContext Result.failure(Data.Builder().putString("error", "Empty response body").build())
            
            setProgress(Data.Builder().putString("status", "Parsing rates...").build())
            val ratesAgainstEur = parseEcbXml(xmlData)
            
            if (ratesAgainstEur.isEmpty()) {
                return@withContext Result.failure(Data.Builder().putString("error", "No rates found in XML").build())
            }

            ratesAgainstEur["EUR"] = 1.0

            setProgress(Data.Builder().putString("status", "Saving to database...").build())

            val rateEurToBase = ratesAgainstEur[baseCurrency] ?: 1.0
            
            ratesAgainstEur.forEach { (currency, rateAgainstEur) ->
                val rateToBase = rateEurToBase / rateAgainstEur
                
                repository.upsertExchangeRate(ExchangeRate(
                    currencyCode = currency,
                    rateToBase = rateToBase,
                    baseCurrency = baseCurrency,
                    updatedAt = System.currentTimeMillis()
                ))
            }

            setProgress(Data.Builder().putString("status", "Finished").build())
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e("RateUpdateWorker", "Failed to update exchange rates", e)
            val errorMsg = e.localizedMessage ?: "Unknown network error"
            return@withContext Result.failure(Data.Builder().putString("error", errorMsg).build())
        }
    }

    private fun parseEcbXml(xml: String): MutableMap<String, Double> {
        val rates = mutableMapOf<String, Double>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Cube") {
                val currency = parser.getAttributeValue(null, "currency")
                val rate = parser.getAttributeValue(null, "rate")
                if (currency != null && rate != null) {
                    rate.toDoubleOrNull()?.let { rates[currency] = it }
                }
            }
            eventType = parser.next()
        }
        return rates
    }
}
