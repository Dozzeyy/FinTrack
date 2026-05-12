/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import android.util.Log
import com.openapps.fintrack.BuildConfig

object SafeLogger {
    private const val TAG = "FinTrack"
    
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, message, throwable)
        }
    }
    
    fun sanitize(value: String?): String {
        if (value == null) return "null"
        if (value.length < 4) return "***"
        return "***${value.takeLast(4)}"
    }
}
