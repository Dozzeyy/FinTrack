/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack

import android.app.Application
import com.openapps.fintrack.data.EncryptedPrefsHelper

class FinTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EncryptedPrefsHelper.init(this)
    }
}
