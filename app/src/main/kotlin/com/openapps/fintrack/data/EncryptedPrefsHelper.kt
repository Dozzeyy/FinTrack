/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptedPrefsHelper {
    private var encryptedPrefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "secret_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("EncryptedPrefsHelper", "Failed to initialize encrypted shared preferences", e)
            // Fallback to regular prefs if encryption fails (better than crashing, though less secure)
            encryptedPrefs = context.getSharedPreferences("secret_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getString(key: String, default: String?): String? {
        return encryptedPrefs?.getString(key, default) ?: default
    }

    fun putString(key: String, value: String) {
        encryptedPrefs?.edit()?.putString(key, value)?.apply()
    }
    
    fun getBoolean(key: String, default: Boolean): Boolean {
         return encryptedPrefs?.getBoolean(key, default) ?: default
    }
    
    fun putBoolean(key: String, value: Boolean) {
        encryptedPrefs?.edit()?.putBoolean(key, value)?.apply()
    }
}
