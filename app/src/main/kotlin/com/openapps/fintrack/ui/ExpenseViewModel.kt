/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import com.openapps.fintrack.data.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "last_sync_time" -> lastSyncTime = sharedPreferences.getString(key, "Never") ?: "Never"
            "last_sync_size" -> lastSyncSize = sharedPreferences.getString(key, "") ?: ""
            "last_sync_status" -> syncLastStatus = sharedPreferences.getString(key, "Never") ?: "Never"
            "last_sync_success_time" -> syncLastSuccessTime = sharedPreferences.getString(key, "Never") ?: "Never"
            "last_sync_attempt_time" -> syncLastAttemptTime = sharedPreferences.getString(key, "Never") ?: "Never"
            "last_sync_attempt_error" -> syncLastAttemptError = sharedPreferences.getString(key, "") ?: ""
            "last_rate_refresh_time" -> lastRateRefreshTime = sharedPreferences.getString(key, "Never") ?: "Never"
            "disable_screenshots" -> disableScreenshots = sharedPreferences.getBoolean(key, false)
        }
    }

    private var _database: AppDatabase? = null
    private var _dao: ExpenseDao? = null

    val database: AppDatabase get() { 
        if (_database == null) {
            val df = getApplication<Application>().getDatabasePath("expenses_database")
            val ef = File(df.path + ".xpt")
            // If encrypted file exists, we MUST NOT let Room create a new plain one
            if (ef.exists() && !isDatabaseDecrypted) {
                throw IllegalStateException("Database is encrypted. Unlock required.")
            }
            _database = AppDatabase.getDatabase(getApplication(), viewModelScope)
        }
        return _database!! 
    }
    val dao: ExpenseDao get() { if (_dao == null) _dao = database.expenseDao(); return _dao!! }

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()
    fun triggerRefresh() { _refreshTrigger.value += 1 }

    // UI state
    var selectedTransactionDetail by mutableStateOf<TransactionWithDetails?>(null)
    var summaryInitialTab by mutableStateOf("Transactions")
    var summaryInitialAccountId by mutableStateOf<Int?>(null)
    var editingAccount by mutableStateOf<Account?>(null)
    var editingCategory by mutableStateOf<Category?>(null)
    var editingTag by mutableStateOf<Tag?>(null)
    var editingParty by mutableStateOf<Party?>(null)
    var editingMajorHead by mutableStateOf<MajorHead?>(null)
    var editingMinorHead by mutableStateOf<MinorHead?>(null)
    var editingBudgetRaw by mutableStateOf<Budget?>(null)
    var editingTemplate by mutableStateOf<Template?>(null)
    var editingNote by mutableStateOf<Note?>(null)
    var selectedNotebookId by mutableStateOf<Int?>(null)
    var draftTransaction by mutableStateOf<DraftTransaction?>(null)
    var draftAccount by mutableStateOf<DraftAccount?>(null)

    // Settings
    var inactivityTimeout by mutableStateOf(prefs.getString("inactivity_timeout", "1") ?: "1")
    var autoReadEnabled by mutableStateOf(prefs.getBoolean("auto_read_enabled", false))
    var currentTheme by mutableStateOf(prefs.getString("theme", "System") ?: "System")
    var currentPrimaryColor by mutableStateOf(prefs.getInt("primary_color", 0xFF6200EE.toInt()))
    var smsCurrencies by mutableStateOf(prefs.getString("sms_currencies", "INR,USD") ?: "INR,USD")
    var smsKeywords by mutableStateOf(prefs.getString("sms_keywords", "spent,paid") ?: "spent,paid")
    var smsConditionType by mutableStateOf(prefs.getString("sms_condition_type", "Any") ?: "Any")
    var multiTagEnabled by mutableStateOf(prefs.getBoolean("multi_tag_enabled", false))
    var appLockEnabled by mutableStateOf(prefs.getBoolean("app_lock_enabled", false))
    var useMillionsSystem by mutableStateOf(prefs.getBoolean("use_millions_system", false))
    var showAssetsOnHome by mutableStateOf(prefs.getBoolean("show_assets_on_home", true))
    var enableMultiCurrency by mutableStateOf(prefs.getBoolean("enable_multi_currency", false))
    var baseCurrency by mutableStateOf(prefs.getString("base_currency", "INR") ?: "INR")
    var templateFields by mutableStateOf(prefs.getStringSet("template_fields", setOf("Amount", "Note")) ?: setOf("Amount", "Note"))
    var dashboardAccountIds by mutableStateOf(prefs.getString("dashboard_account_ids", "")?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toIntOrNull() } ?: emptyList())
    var dashboardBudgetIds by mutableStateOf(prefs.getString("dashboard_budget_ids", "")?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toIntOrNull() } ?: emptyList())
    var bottomTabOrder by mutableStateOf(prefs.getString("bottom_tab_order", "home,analysis,transactions,budgets")?.split(",") ?: listOf("home", "analysis", "transactions", "budgets"))
    var remindersEnabled by mutableStateOf(prefs.getBoolean("reminders_enabled", false))
    var reminderFrequency by mutableStateOf(prefs.getInt("reminder_frequency", 1))
    var reminderUnit by mutableStateOf(prefs.getString("reminder_unit", "day/s") ?: "day/s")
    var reminderTime by mutableStateOf(prefs.getString("reminder_time", "20:00") ?: "20:00")
    var reminderMessage by mutableStateOf(prefs.getString("reminder_message", "Record expenses!") ?: "Record expenses!")
    var ccAlertEnabled by mutableStateOf(prefs.getBoolean("cc_alert_enabled", false))
    var dismissedCcAlertIds by mutableStateOf(prefs.getStringSet("dismissed_cc_alerts", emptySet()) ?: emptySet())
    var disableScreenshots by mutableStateOf(prefs.getBoolean("disable_screenshots", false))
    var tapToShowNetPosition by mutableStateOf(prefs.getBoolean("tap_to_show_net_position", false))

    // WebDAV
    var remoteSyncEnabled by mutableStateOf(prefs.getBoolean("remote_sync_enabled", false))
    var webdavUrl by mutableStateOf(EncryptedPrefsHelper.getString("webdav_url", "") ?: "")
    var webdavUsername by mutableStateOf(EncryptedPrefsHelper.getString("webdav_user", "") ?: "")
    var webdavPassword by mutableStateOf(EncryptedPrefsHelper.getString("webdav_pass", "") ?: "")
    var syncFrequency by mutableStateOf(prefs.getString("sync_frequency", "1 day") ?: "1 day")
    var lastSyncTime by mutableStateOf(prefs.getString("last_sync_time", "Never") ?: "Never")
    var lastSyncSize by mutableStateOf(prefs.getString("last_sync_size", "") ?: "")
    var lastRateRefreshTime by mutableStateOf(prefs.getString("last_rate_refresh_time", "Never") ?: "Never")
    var isSyncing by mutableStateOf(false)
    var syncStatus by mutableStateOf("")
    var syncMessage by mutableStateOf("")
    var syncProgress by mutableStateOf(0f)
    var syncTotalSize by mutableStateOf(0L)
    var syncProcessedSize by mutableStateOf(0L)
    var syncLastStatus by mutableStateOf(prefs.getString("last_sync_status", "Never") ?: "Never")
    var syncLastSuccessTime by mutableStateOf(prefs.getString("last_sync_success_time", "Never") ?: "Never")
    var syncLastAttemptTime by mutableStateOf(prefs.getString("last_sync_attempt_time", "Never") ?: "Never")
    var syncLastAttemptError by mutableStateOf(prefs.getString("last_sync_attempt_error", "") ?: "")
    var isTestingConnection by mutableStateOf(false)

    // Sync on new record job
    private var pendingSyncJob: Job? = null

    // Security
    var encryptRemoteEnabled by mutableStateOf(prefs.getBoolean("encrypt_remote_enabled", false))
    var remoteMasterPassword by mutableStateOf(EncryptedPrefsHelper.getString("remote_master_password", "") ?: "")
    var secureModeEnabled by mutableStateOf(prefs.getBoolean("secure_mode_enabled", false))
    var isDatabaseDecrypted by mutableStateOf(false)
    var isPickingFile by mutableStateOf(false)
    
    private val encryptionMutex = Mutex()
    private val serverManager by lazy { 
        ServerManager.getInstance(application, dao).apply {
            onDatabaseChange = { 
                triggerRefresh()
                triggerSyncOnNewRecord()
            }
        }
    }
    val isServerRunning: StateFlow<Boolean> get() = serverManager.isRunning
    val isStopping: StateFlow<Boolean> get() = serverManager.isStopping
    val serverError: StateFlow<String?> get() = serverManager.serverError
    val httpUrl: StateFlow<String?> get() = serverManager.httpUrl
    val httpsUrl: StateFlow<String?> get() = serverManager.httpsUrl
    val activeClients: StateFlow<Map<String, ClientConnection>> get() = serverManager.activeClients
    val terminationLogs: StateFlow<List<TerminationLog>> get() = serverManager.terminationLogs
    var isRefreshingRates by mutableStateOf(false)
    var rateRefreshStatus by mutableStateOf("")
    val lastRateRefreshResult = mutableStateOf<String?>(null)

    // Insights State
    val financialInsights = mutableStateListOf<FinancialInsight>()
    var isGeneratingInsights by mutableStateOf(false)
    var showInsightsOverlay by mutableStateOf(false)
    private val insightEngine = FinancialInsightEngine()

    init {
        if (EncryptedPrefsHelper.getString("base_currency", null) == null) EncryptedPrefsHelper.putString("base_currency", baseCurrency)
        // Priority 1: Secure Mode start state - Must be locked by default if enabled
        isDatabaseDecrypted = !secureModeEnabled
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        scheduleReminders()
        scheduleCcAlerts()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    // Updates
    fun updateTheme(t: String) { currentTheme = t; prefs.edit().putString("theme", t).apply() }
    fun updatePrimaryColor(c: Int) { currentPrimaryColor = c; prefs.edit().putInt("primary_color", c).apply() }
    fun updateInactivityTimeout(v: String) { inactivityTimeout = v; prefs.edit().putString("inactivity_timeout", v).apply() }
    fun updateAutoReadEnabled(e: Boolean) { autoReadEnabled = e; prefs.edit().putBoolean("auto_read_enabled", e).apply() }
    fun updateSmsCurrencies(v: String) { smsCurrencies = v; prefs.edit().putString("sms_currencies", v).apply() }
    fun updateSmsKeywords(v: String) { smsKeywords = v; prefs.edit().putString("sms_keywords", v).apply() }
    fun updateSmsConditionType(v: String) { smsConditionType = v; prefs.edit().putString("sms_condition_type", v).apply() }
    fun updateMultiTagEnabled(e: Boolean) { multiTagEnabled = e; prefs.edit().putBoolean("multi_tag_enabled", e).apply() }
    fun updateAppLockEnabled(e: Boolean) { appLockEnabled = e; prefs.edit().putBoolean("app_lock_enabled", e).apply() }
    fun updateNumberSystem(m: Boolean) { useMillionsSystem = m; prefs.edit().putBoolean("use_millions_system", m).apply() }
    fun updateHomeScreenView(a: Boolean) { showAssetsOnHome = a; prefs.edit().putBoolean("show_assets_on_home", a).apply() }
    fun updateMultiCurrencyEnabled(e: Boolean) { enableMultiCurrency = e; prefs.edit().putBoolean("enable_multi_currency", e).apply(); if(e) refreshExchangeRates() }
    fun updateBaseCurrency(c: String) { baseCurrency = c; EncryptedPrefsHelper.putString("base_currency", c); prefs.edit().putString("base_currency", c).apply(); refreshExchangeRates() }
    fun updateDashboardAccounts(ids: List<Int>) { dashboardAccountIds = ids; prefs.edit().putString("dashboard_account_ids", ids.joinToString(",")).apply() }
    fun updateDashboardBudgets(ids: List<Int>) { dashboardBudgetIds = ids; prefs.edit().putString("dashboard_budget_ids", ids.joinToString(",")).apply() }
    fun updateTabOrder(o: List<String>) { bottomTabOrder = o; prefs.edit().putString("bottom_tab_order", o.joinToString(",")).apply() }
    fun updateReminderEnabled(e: Boolean) { remindersEnabled = e; prefs.edit().putBoolean("reminders_enabled", e).apply(); scheduleReminders() }
    fun updateReminderFrequency(f: Int) { reminderFrequency = f; prefs.edit().putInt("reminder_frequency", f).apply(); scheduleReminders() }
    fun updateReminderUnit(u: String) { reminderUnit = u; prefs.edit().putString("reminder_unit", u).apply(); scheduleReminders() }
    fun updateReminderTime(t: String) { reminderTime = t; prefs.edit().putString("reminder_time", t).apply(); scheduleReminders() }
    fun updateReminderMessage(m: String) { reminderMessage = m; prefs.edit().putString("reminder_message", m).apply() }
    fun updateCcAlertEnabled(e: Boolean) { ccAlertEnabled = e; prefs.edit().putBoolean("cc_alert_enabled", e).apply(); scheduleCcAlerts() }
    fun updateRemoteSyncEnabled(e: Boolean) { remoteSyncEnabled = e; prefs.edit().putBoolean("remote_sync_enabled", e).apply() }
    fun updateSyncFrequency(f: String) { syncFrequency = f; prefs.edit().putString("sync_frequency", f).apply() }
    fun updateWebdavUrl(u: String) { webdavUrl = u; EncryptedPrefsHelper.putString("webdav_url", u) }
    fun updateWebdavUsername(u: String) { webdavUsername = u; EncryptedPrefsHelper.putString("webdav_user", u) }
    fun updateWebdavPassword(p: String) { webdavPassword = p; EncryptedPrefsHelper.putString("webdav_pass", p) }
    fun updateRemoteMasterPassword(p: String) { remoteMasterPassword = p; EncryptedPrefsHelper.putString("remote_master_password", p) }
    fun updateEncryptRemote(e: Boolean) { encryptRemoteEnabled = e; prefs.edit().putBoolean("encrypt_remote_enabled", e).apply() }
    fun updateTemplateField(f: String, e: Boolean) { val n = templateFields.toMutableSet(); if(e) n.add(f) else n.remove(f); templateFields = n; prefs.edit().putStringSet("template_fields", n).apply() }
    fun updateDisableScreenshots(e: Boolean) { disableScreenshots = e; prefs.edit().putBoolean("disable_screenshots", e).apply() }
    fun updateTapToShowNetPosition(e: Boolean) { tapToShowNetPosition = e; prefs.edit().putBoolean("tap_to_show_net_position", e).apply() }

    fun scheduleBackup(context: Context) {
        try {
            val bp = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            val path = bp.getString("path", null)
            val enabled = bp.getBoolean("enabled", false)
            
            if (enabled && path != null) {
                val backupWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.openapps.fintrack.data.BackupWorker>(5, java.util.concurrent.TimeUnit.DAYS)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiresStorageNotLow(true).build())
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "scheduled_backup",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    backupWorkRequest
                )
                Log.d("BackupSchedule", "Scheduled backup work enqueued.")
            }
        } catch (e: Exception) {
            Log.e("BackupSchedule", "Failed to schedule backup", e)
        }
    }

    fun scheduleReminders() {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 2001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!remindersEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val parts = reminderTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val min = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intervalMillis = when (reminderUnit) {
            "day/s" -> reminderFrequency.toLong() * 24 * 60 * 60 * 1000
            "week/s" -> reminderFrequency.toLong() * 7 * 24 * 60 * 60 * 1000
            "month/s" -> reminderFrequency.toLong() * 30 * 24 * 60 * 60 * 1000
            "year/s" -> reminderFrequency.toLong() * 365 * 24 * 60 * 60 * 1000
            else -> 24 * 60 * 60 * 1000
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            intervalMillis,
            pendingIntent
        )
    }

    fun scheduleCcAlerts() {
        val workManager = WorkManager.getInstance(getApplication())
        // We always run CcAlertWorker daily because it also handles Loan Auto-Recording
        // but it will only process alerts if ccAlertEnabled is true.
        
        val ccAlertRequest = androidx.work.PeriodicWorkRequestBuilder<com.openapps.fintrack.data.CcAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) // Run an hour after start to not bog down init
            .build()

        workManager.enqueueUniquePeriodicWork(
            "cc_due_alerts",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            ccAlertRequest
        )
    }

    // Security Logic
    fun updateSecureMode(e: Boolean) { 
        secureModeEnabled = e
        prefs.edit().putBoolean("secure_mode_enabled", e).apply()
        if (!e) {
            prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
            isDatabaseDecrypted = true
        } else {
            val bp = getApplication<Application>().getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            bp.edit().putBoolean("encrypt_scheduled_backup", true).apply()
            isDatabaseDecrypted = false // Force lock immediately
            encryptDatabaseAtRest()
        }
    }

    fun checkDatabaseEncryptionStatus(): Boolean {
        if (!secureModeEnabled) return false
        if (isDatabaseDecrypted) return false
        
        val df = getApplication<Application>().getDatabasePath("expenses_database")
        val ef = File(df.path + ".xpt")
        
        // Return true (show lock) if either file exists and we haven't unlocked yet
        return ef.exists() || df.exists()
    }
    
    fun encryptDatabaseAtRest() {
        val passToUse = if (remoteMasterPassword.isNotBlank()) remoteMasterPassword 
                        else EncryptedPrefsHelper.getString("remote_master_password", "") ?: ""
                        
        if (!secureModeEnabled || passToUse.isBlank() || serverManager.isRunning.value) return
        
        // Critical: Only encrypt if we are sure the current DB is the actual data (not a worker-created empty shell)
        if (!isDatabaseDecrypted) {
            Log.w("SecureMode", "Database is not currently decrypted. Skipping encryption to prevent data loss.")
            return
        }

        // Priority 1: Prevent UI from accessing DB while encryption is starting
        isDatabaseDecrypted = false
        prefs.edit().putBoolean("db_encrypted_at_rest", true).apply()

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { 
            AppDatabase.databaseMutex.withLock {
                try { 
                    val df = getApplication<Application>().getDatabasePath("expenses_database")
                    if (!df.exists()) return@withLock
                    
                    _database?.checkpoint()
                    AppDatabase.closeDatabase()
                    
                    withContext(Dispatchers.Main) {
                        _database = null
                        _dao = null
                    }
                    
                    val result = EncryptionService.encryptFile(df, File(df.path + ".xpt"), passToUse)
                    if (result.isSuccess) { 
                        df.delete()
                        File(df.path + "-shm").delete()
                        File(df.path + "-wal").delete()
                        File(df.path + "-journal").delete() 
                        
                        withContext(Dispatchers.Main) {
                            remoteMasterPassword = "" // Securely wipe session memory
                        }
                        Log.d("SecureMode", "Database is now encrypted at rest.")
                    } else {
                        // If encryption failed, we MUST allow retries or revert state
                        withContext(Dispatchers.Main) {
                            isDatabaseDecrypted = true
                            prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                        }
                    }
                } catch(ex: Exception) { 
                    Log.e("SecureMode", "Background encryption failed", ex) 
                    withContext(Dispatchers.Main) {
                        isDatabaseDecrypted = true
                        prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                    }
                }
            } 
        }
    }

    suspend fun decryptDatabaseAtRest(p: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) { 
        AppDatabase.databaseMutex.withLock {
            try { 
                val df = getApplication<Application>().getDatabasePath("expenses_database")
                val ef = File(df.path + ".xpt")
                if (!ef.exists()) { 
                    withContext(Dispatchers.Main) {
                        isDatabaseDecrypted = true
                        prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                    }
                    return@withLock true 
                }
                
                val result = EncryptionService.decryptFile(ef, df, p) { progress ->
                    onProgress(progress)
                }
                if (result.isSuccess) { 
                    ef.delete()
                    withContext(Dispatchers.Main) {
                        isDatabaseDecrypted = true
                        prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                        remoteMasterPassword = p
                        refreshDatabase() 
                    }
                    return@withLock true 
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Incorrect Password"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Decryption Failed: $error", Toast.LENGTH_LONG).show()
                    }
                    return@withLock false
                }
            } catch(ex: Exception) { 
                Log.e("SecureMode", "Dec error", ex) 
                false
            }
        } 
    }

    // Sync Logic
    suspend fun syncNow(): Result<String> = withContext(Dispatchers.IO) {
        if (webdavUrl.isBlank()) return@withContext Result.failure(Exception("WebDAV settings missing"))
        
        withContext(Dispatchers.Main) {
            isSyncing = true
            syncStatus = "Starting Sync..."
            syncMessage = "Initializing"
            syncProgress = 0f
            syncProcessedSize = 0L
            syncTotalSize = 0L
        }

        val ts = File(getApplication<Application>().cacheDir, "sync_ts.db")
        val tf = File(getApplication<Application>().cacheDir, "sync_tf.db")
        
        try {
            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            withContext(Dispatchers.Main) {
                syncLastAttemptTime = nowStr
                syncLastAttemptError = ""
                syncLastStatus = "Syncing..."
                prefs.edit()
                    .putString("last_sync_attempt_time", nowStr)
                    .putString("last_sync_attempt_error", "")
                    .putString("last_sync_status", "Syncing...")
                    .apply()
            }

            val me = encryptRemoteEnabled || secureModeEnabled
            val df = getApplication<Application>().getDatabasePath("expenses_database")
            val ef = File(df.path + ".xpt")
            
            withContext(Dispatchers.Main) { syncMessage = "Checkpointing" }
            database.checkpoint()
            
            if (ef.exists()) {
                withContext(Dispatchers.Main) { syncMessage = "Preparing Encrypted DB" }
                ef.copyTo(tf, true)
            } else {
                withContext(Dispatchers.Main) { syncMessage = "Snapshotting" }
                FileInputStream(df).use { i -> FileOutputStream(ts).use { o -> i.copyTo(o) } }
                if (me) {
                    withContext(Dispatchers.Main) { syncMessage = "Encrypting" }
                    val passToUse = if (remoteMasterPassword.isNotBlank()) remoteMasterPassword else EncryptedPrefsHelper.getString("remote_master_password", "") ?: ""
                    
                    val result = EncryptionService.encryptFile(ts, tf, passToUse) { progress ->
                        viewModelScope.launch(Dispatchers.Main) { syncProgress = progress * 0.3f }
                    }
                    if (result.isFailure) throw Exception("Encryption failed: ${result.exceptionOrNull()?.message}")
                } else {
                    ts.copyTo(tf, true)
                }
            }
            
            val totalBytes = tf.length()
            withContext(Dispatchers.Main) {
                syncTotalSize = totalBytes
                syncMessage = "Uploading"
                syncProgress = 0.3f
            }

            val fn = if (me) "expenses_database_sync.xpt" else "expenses_database_sync.db"
            val baseUrl = webdavUrl.toHttpUrlOrNull() ?: throw Exception("Invalid URL")
            val fullUrl = baseUrl.newBuilder().addPathSegment(fn).build().toString()
            val tmpUrl = "$fullUrl.tmp"

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    if (attempt > 1) {
                        withContext(Dispatchers.Main) { syncMessage = "Retrying upload (Attempt $attempt)..." }
                        kotlinx.coroutines.delay(3000)
                    }

                    // 1. Upload to .tmp
                    val countingBody = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength() = totalBytes
                        override fun writeTo(sink: okio.BufferedSink) {
                            tf.inputStream().use { input ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                var uploaded = 0L
                                while (input.read(buffer).also { read = it } != -1) {
                                    sink.write(buffer, 0, read)
                                    uploaded += read
                                    val currentUploaded = uploaded
                                    viewModelScope.launch(Dispatchers.Main) {
                                        syncProcessedSize = currentUploaded
                                        syncProgress = 0.3f + (currentUploaded.toFloat() / totalBytes) * 0.6f
                                    }
                                }
                            }
                        }
                    }

                    val putReq = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                        .header("User-Agent", "FinTrack-Android")
                        .put(countingBody)
                        .build()

                    client.newCall(putReq).execute().use { response ->
                        if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                            if (response.code == 429) throw Exception("Server busy (429). Please wait.")
                            throw Exception("Upload stage failed: HTTP ${response.code}")
                        }
                    }

                    // 2. Atomic MOVE
                    withContext(Dispatchers.Main) { 
                        syncMessage = "Finalizing" 
                        syncProgress = 0.95f
                    }

                    val moveReq = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                        .header("User-Agent", "FinTrack-Android")
                        .header("Destination", fullUrl)
                        .header("Overwrite", "T")
                        .method("MOVE", null)
                        .build()

                    client.newCall(moveReq).execute().use { response ->
                        if (response.isSuccessful || response.code == 201 || response.code == 204) {
                val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                withContext(Dispatchers.Main) {
                    lastSyncTime = timeStr
                    syncLastSuccessTime = timeStr
                    syncLastStatus = "Success ($timeStr)"
                    syncProgress = 1.0f
                    prefs.edit()
                        .putString("last_sync_time", timeStr)
                        .putString("last_sync_success_time", timeStr)
                        .putString("last_sync_status", syncLastStatus)
                        .apply()
                }
                return@withContext Result.success("Sync Successful!")
                        } else {
                            throw Exception("Finalize stage failed: HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 3) throw e
                }
            }
            Result.failure(lastError ?: Exception("Unknown sync error"))
        } catch (ex: Exception) {
            withContext(Dispatchers.Main) {
                syncLastAttemptError = ex.localizedMessage ?: "Unknown error"
                syncLastStatus = "Failed"
                prefs.edit()
                    .putString("last_sync_attempt_error", syncLastAttemptError)
                    .putString("last_sync_status", "Failed")
                    .apply()
            }
            Result.failure(ex)
        } finally {
            if (ts.exists()) ts.delete()
            if (tf.exists()) tf.delete()
            withContext(Dispatchers.Main) { isSyncing = false }
        }
    }
    suspend fun testWebdavConnection(): Result<String> = withContext(Dispatchers.IO) {
        try { 
            isTestingConnection = true
            val baseUrl = webdavUrl.toHttpUrlOrNull() ?: throw Exception("Invalid URL")
            
            val req = Request.Builder()
                .url(baseUrl)
                .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                .header("User-Agent", "FinTrack-Android")
                .method("PROPFIND", null)
                .header("Depth", "0") // Depth 0 is just the resource itself
                .build()
            
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful || response.code == 207 || response.code == 405) {
                    Result.success("Connection Successful!")
                } else {
                    Result.failure(Exception("${response.code} ${response.message}"))
                }
            }
        } catch(ex: Exception) { 
            Result.failure(ex) 
        } finally { 
            isTestingConnection = false 
        }
    }
    suspend fun downloadRemoteToLocal(du: Uri): Result<String> = withContext(Dispatchers.IO) {
        val td = File(getApplication<Application>().cacheDir, "remote_dl.db"); try {
            val fu = webdavUrl.toHttpUrlOrNull()?.newBuilder()?.addPathSegment("expenses_database_sync.xpt")?.build()?.toString() ?: return@withContext Result.failure(Exception("URL"))
            val req = Request.Builder().url(fu).header("Authorization", Credentials.basic(webdavUsername, webdavPassword)).get().build()
            OkHttpClient().newCall(req).execute().use { if(it.isSuccessful) { it.body?.let{ b -> FileOutputStream(td).use{ o -> b.source().use{ s -> o.write(s.readByteArray()) } } }; getApplication<Application>().contentResolver.openOutputStream(du)?.use { o -> FileInputStream(td).use { i -> i.copyTo(o) } }; Result.success("OK") } else Result.failure(Exception(it.message)) }
        } catch(ex: Exception) { Result.failure(ex) } finally { td.delete() }
    }

    // Rates
    fun refreshExchangeRates() {
        val workManager = WorkManager.getInstance(getApplication())
        val request = OneTimeWorkRequestBuilder<RateUpdateWorker>().build()
        isRefreshingRates = true
        rateRefreshStatus = "Starting..."
        
        workManager.enqueue(request)
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info != null) {
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            isRefreshingRates = false
                            rateRefreshStatus = "Success"
                            lastRateRefreshResult.value = "Successful Update!"
                            triggerRefresh()
                        }
                        WorkInfo.State.FAILED -> {
                            isRefreshingRates = false
                            val error = info.outputData.getString("error") ?: "Update Failed"
                            rateRefreshStatus = "Failed"
                            lastRateRefreshResult.value = error
                        }
                        WorkInfo.State.RUNNING -> {
                            rateRefreshStatus = info.progress.getString("status") ?: "Refreshing..."
                        }
                        else -> {
                            if (info.state.isFinished) isRefreshingRates = false
                        }
                    }
                }
            }
        }
    }

    // Data Flows
    val allTransactions: Flow<List<TransactionWithDetails>> = _refreshTrigger.flatMapLatest { dao.getAllTransactionsWithDetails() }
    fun getEnabledAccounts() = _refreshTrigger.flatMapLatest { dao.getEnabledAccounts() }
    fun getAllAccounts() = _refreshTrigger.flatMapLatest { dao.getAllAccounts() }
    fun getEnabledCategories() = _refreshTrigger.flatMapLatest { dao.getEnabledCategories() }
    fun getAllCategories() = _refreshTrigger.flatMapLatest { dao.getAllCategories() }
    fun getEnabledCategoriesByType(t: String) = _refreshTrigger.flatMapLatest { dao.getEnabledCategoriesByType(t) }
    fun getAllTags() = _refreshTrigger.flatMapLatest { dao.getAllTags() }
    fun getEnabledTags() = _refreshTrigger.flatMapLatest { dao.getEnabledTags() }
    fun getEnabledParties() = _refreshTrigger.flatMapLatest { dao.getEnabledParties() }
    fun getAllParties() = _refreshTrigger.flatMapLatest { dao.getAllParties() }
    fun getPartyBalances(d: String) = dao.getPartyBalances(d)
    fun getAllMajorHeads() = _refreshTrigger.flatMapLatest { dao.getAllMajorHeads() }
    fun getAllMinorHeads() = _refreshTrigger.flatMapLatest { dao.getAllMinorHeads() }
    fun getMinorHeadsByMajor(id: Int) = _refreshTrigger.flatMapLatest { dao.getMinorHeadsByMajor(id) }
    fun getMajorHeadBalances(d: String) = _refreshTrigger.flatMapLatest { dao.getMajorHeadBalances(d) }
    fun getMinorHeadBalances(d: String) = _refreshTrigger.flatMapLatest { dao.getMinorHeadBalances(d) }
    fun getAllBudgets() = _refreshTrigger.flatMapLatest { dao.getAllBudgets() }
    fun getAllTemplates() = _refreshTrigger.flatMapLatest { dao.getAllTemplates() }
    fun getAllNotes() = _refreshTrigger.flatMapLatest { dao.getAllNotes() }
    fun getNotesByNotebook(id: Int) = _refreshTrigger.flatMapLatest { dao.getNotesByNotebook(id) }
    fun searchNotes(q: String) = dao.searchNotes(q)
    fun getAllNotebooks() = _refreshTrigger.flatMapLatest { dao.getAllNotebooks() }
    val activeLoans = _refreshTrigger.flatMapLatest { dao.getAllActiveLoans() }
    val allLoans = _refreshTrigger.flatMapLatest { dao.getAllLoans() }
    fun getRepaymentsForLoan(id: Long) = dao.getRepaymentsForLoan(id)
    fun getCreditCardAccounts() = combine(dao.getEnabledAccounts(), dao.getMinorHeadsByMajor(8)){ a, m -> val i = m.map{it.id}.toSet(); a.filter{ it.minorHeadId in i } }
    fun getAllSubscriptionStatuses() = _refreshTrigger.flatMapLatest { dao.getAllSubscriptionStatuses() }
    fun getAllSubscriptionsMaster() = _refreshTrigger.flatMapLatest { dao.getAllSubscriptionsMaster() }
    fun getExchangeRates(): Flow<List<ExchangeRate>> = dao.getAllExchangeRates()
    fun getAllRules() = _refreshTrigger.flatMapLatest { dao.getAllRules() }
    suspend fun getLastTransactionForSubscription(name: String) = dao.getAllTransactionsWithDetails().first().find { it.transaction.subName == name }

    // Actions
    fun saveAccount(name: String, openingBalance: Double, description: String?, isEnabled: Boolean, minorHeadId: Int?, creditLimit: Double?, billingCycleStart: String?, billingCycleEnd: String?, paymentDueDate: String?, icon: String?) {
        viewModelScope.launch { dao.upsertAccount(editingAccount?.copy(name=name, openingBalance=openingBalance, description=description, isEnabled=isEnabled, minorHeadId=minorHeadId, creditLimit=creditLimit, billingCycleStart=billingCycleStart, billingCycleEnd=billingCycleEnd, paymentDueDate=paymentDueDate, icon=icon) ?: Account(name=name, type="asset", openingBalance=openingBalance, description=description, isEnabled=isEnabled, minorHeadId=minorHeadId, creditLimit=creditLimit, billingCycleStart=billingCycleStart, billingCycleEnd=billingCycleEnd, paymentDueDate=paymentDueDate, icon=icon)); editingAccount=null; triggerRefresh() }
    }
    fun saveCategory(name: String, type: String, description: String?, isEnabled: Boolean, icon: String?) {
        viewModelScope.launch { dao.upsertCategory(editingCategory?.copy(name=name, type=type, description=description, isEnabled=isEnabled, icon=icon) ?: Category(name=name, type=type, description=description, isEnabled=isEnabled, icon=icon)); editingCategory=null; triggerRefresh() }
    }
    fun saveTag(name: String, isEnabled: Boolean) {
        viewModelScope.launch { dao.upsertTag(editingTag?.copy(name=name, isEnabled=isEnabled) ?: Tag(name=name, isEnabled=isEnabled)); editingTag=null; triggerRefresh() }
    }
    fun saveParty(name: String, openingBalance: Double, isEnabled: Boolean) {
        viewModelScope.launch { dao.upsertParty(editingParty?.copy(name=name, openingBalance=openingBalance, isEnabled=isEnabled) ?: Party(name=name, openingBalance=openingBalance, isEnabled=isEnabled)); editingParty=null; triggerRefresh() }
    }
    fun saveMajorHead(name: String, isEnabled: Boolean) {
        viewModelScope.launch { dao.upsertMajorHead(editingMajorHead?.copy(name=name, isEnabled=isEnabled) ?: MajorHead(name=name, isEnabled=isEnabled)); editingMajorHead=null; triggerRefresh() }
    }
    fun saveMinorHead(name: String, majorHeadId: Int, isEnabled: Boolean) {
        viewModelScope.launch { dao.upsertMinorHead(editingMinorHead?.copy(name=name, majorHeadId=majorHeadId, isEnabled=isEnabled) ?: MinorHead(name=name, majorHeadId=majorHeadId, isEnabled=isEnabled)); editingMinorHead=null; triggerRefresh() }
    }
    fun saveBudgetRaw(name: String?, categoryIds: String, amount: Double, duration: String, note: String?, higherIsBetter: Boolean, accountIds: String?) {
        viewModelScope.launch { dao.upsertBudget(editingBudgetRaw?.copy(name=name, categoryIds=categoryIds, amount=amount, duration=duration, note=note, higherIsBetter=higherIsBetter, accountIds=accountIds) ?: Budget(name=name, categoryIds=categoryIds, amount=amount, duration=duration, note=note, higherIsBetter=higherIsBetter, accountIds=accountIds)); editingBudgetRaw=null; triggerRefresh() }
    }
    suspend fun saveTemplate(t: Template): Boolean { dao.upsertTemplate(t); return true }
    fun saveNote(title: String, content: String, tags: String?, type: String = "text") {
        viewModelScope.launch { 
            val noteToSave = editingNote?.copy(
                title = title, 
                content = content, 
                tags = tags, 
                type = type, 
                notebookId = selectedNotebookId,
                editedAt = System.currentTimeMillis()
            ) ?: Note(
                title = title, 
                content = content, 
                tags = tags, 
                type = type, 
                notebookId = selectedNotebookId
            )
            dao.upsertNote(noteToSave)
            editingNote = null
            triggerRefresh() 
        }
    }
    fun moveNote(note: Note, targetNotebookId: Int?) {
        viewModelScope.launch {
            dao.upsertNote(note.copy(notebookId = targetNotebookId, editedAt = System.currentTimeMillis()))
            triggerRefresh()
        }
    }
    fun copyNote(note: Note, targetNotebookId: Int?) {
        viewModelScope.launch {
            dao.upsertNote(note.copy(id = 0, notebookId = targetNotebookId, createdAt = System.currentTimeMillis(), editedAt = null))
            triggerRefresh()
        }
    }
    fun saveNotebook(n: Notebook) { viewModelScope.launch { dao.upsertNotebook(n); triggerRefresh() } }
    fun saveNotebook(name: String) { viewModelScope.launch { dao.upsertNotebook(Notebook(name = name)); triggerRefresh() } }
    fun deleteNotebook(n: Notebook) { viewModelScope.launch { dao.deleteNotesByNotebook(n.id); dao.deleteNotebook(n); if(selectedNotebookId == n.id) selectedNotebookId = null; triggerRefresh() } }
    fun saveSubscriptionMaster(name: String, frequency: Int, note: String?, isTransfer: Boolean) { viewModelScope.launch { dao.upsertSubscriptionMaster(Subscription(name=name, frequency=frequency, note=note, isTransfer=isTransfer)); triggerRefresh() } }
    fun saveRule(rule: Rule) { viewModelScope.launch { dao.upsertRule(rule); triggerRefresh() } }
    fun deleteAccount(a: Account) { viewModelScope.launch { dao.deleteAccount(a); triggerRefresh() } }
    fun deleteCategory(c: Category) { viewModelScope.launch { dao.deleteCategory(c); triggerRefresh() } }
    fun deleteTag(t: Tag) { viewModelScope.launch { dao.deleteTag(t); triggerRefresh() } }
    fun deleteParty(p: Party) { viewModelScope.launch { dao.deleteParty(p); triggerRefresh() } }
    fun deleteMajorHeadAndRemap(h: MajorHead) { viewModelScope.launch { dao.deleteMajorHead(h); triggerRefresh() } }
    fun deleteMinorHead(h: MinorHead) { viewModelScope.launch { dao.deleteMinorHead(h); triggerRefresh() } }
    fun deleteBudget(b: Budget) { viewModelScope.launch { dao.deleteBudget(b); triggerRefresh() } }
    fun deleteTemplate(t: Template) { viewModelScope.launch { dao.deleteTemplate(t); triggerRefresh() } }
    fun deleteNote(n: Note) { viewModelScope.launch { dao.deleteNote(n); triggerRefresh() } }
    fun deleteLoan(l: Loan) { viewModelScope.launch { dao.deleteLoan(l); triggerRefresh() } }
    fun deleteRule(r: Rule) { viewModelScope.launch { dao.deleteRule(r); triggerRefresh() } }
    fun toggleCategoryEnabled(c: Category) { viewModelScope.launch { dao.updateCategory(c.copy(isEnabled = !c.isEnabled)); triggerRefresh() } }
    fun toggleAccountEnabled(a: Account) { viewModelScope.launch { dao.updateAccount(a.copy(isEnabled = !a.isEnabled)); triggerRefresh() } }
    fun toggleTagEnabled(t: Tag) { viewModelScope.launch { dao.updateTag(t.copy(isEnabled = !t.isEnabled)); triggerRefresh() } }
    fun toggleMajorHeadEnabled(h: MajorHead) { viewModelScope.launch { dao.updateMajorHead(h.copy(isEnabled = !h.isEnabled)); triggerRefresh() } }
    fun toggleMinorHeadEnabled(h: MinorHead) { viewModelScope.launch { dao.updateMinorHead(h.copy(isEnabled = !h.isEnabled)); triggerRefresh() } }
    fun togglePartyEnabled(p: Party) { viewModelScope.launch { dao.updateParty(p.copy(isEnabled = !p.isEnabled)); triggerRefresh() } }
    fun toggleLoanAutoRecord(l: Loan, e: Boolean) { viewModelScope.launch { dao.upsertLoan(l.copy(isAutoRecordEnabled = e)); triggerRefresh() } }

    // Transaction Management
    private val txnNumberMutex = Mutex()
    private suspend fun executeAddTransaction(date: String, time: String, accId: Int, catId: Int?, amount: Double, note: String?, toAccId: Int?, tags: String?, type: String, pId: Int? = null, toPId: Int? = null, subN: String? = null, subF: Int? = null, amtO: Double? = null, cur: String? = null, amtB: Double? = null, updId: Int? = null) {
        txnNumberMutex.withLock {
            val prefix = when(type) { "income"->"INC"; "expense"->"EXP"; "transfer"->"TNF"; else->"TXN" }
            
            var finalTxnNumber: String? = null
            if (updId != null && updId != 0) {
                val existing = dao.getAllTransactionsWithDetails().first().find { it.transaction.id == updId }
                finalTxnNumber = existing?.transaction?.transactionNumber
            }
            
            if (finalTxnNumber == null) {
                val nextSerial = (dao.getLastTransactionNumber(prefix)?.split("/")?.last()?.toIntOrNull() ?: 99999) + 1
                val year = try { LocalDate.parse(date).year } catch(ex:Exception) { LocalDate.now(ZoneId.of("UTC")).year }
                finalTxnNumber = "$prefix/$year/$nextSerial"
            }

            val transaction = Transaction(
                id = updId ?: 0,
                date = date,
                time = time,
                accountId = accId,
                categoryId = if (type == "transfer") null else catId,
                amount = amtB ?: amount,
                note = note,
                toAccountId = toAccId,
                tags = tags,
                transactionNumber = finalTxnNumber,
                partyId = pId,
                toPartyId = toPId,
                subName = subN,
                subFrequency = subF,
                amountOriginal = amtO ?: amount,
                currencyCode = cur ?: baseCurrency,
                amountBase = amtB ?: amount,
                editedAt = if (updId != null && updId != 0) System.currentTimeMillis() else null
            )

            if (updId != null && updId != 0) {
                dao.updateTransaction(transaction)
            } else {
                dao.insertTransaction(transaction)
            }
        }
    }
    fun addTransaction(date: String, time: String, accountId: Int, categoryId: Int?, amount: Double, note: String?, toAccountId: Int?, tags: String?, type: String, partyId: Int? = null, toPartyId: Int? = null, subName: String? = null, subFrequency: Int? = null, amountOriginal: Double? = null, currencyCode: String? = null, amountBase: Double? = null, updateId: Int? = null) {
        viewModelScope.launch { 
            executeAddTransaction(date, time, accountId, categoryId, amount, note, toAccountId, tags, type, partyId, toPartyId, subName, subFrequency, amountOriginal, currencyCode, amountBase, updateId)
            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }
    fun addMultiEntryTransactionExtended(date: String, time: String, accountId: Int, entries: List<MultiEntryRowData>, tags: String?, type: String, partyId: Int?, subName: String?, subFrequency: Int?, updateId: Int? = null) {
        viewModelScope.launch {
            if (updateId != null && updateId != 0) {
                val all = dao.getAllTransactionsWithDetails().first()
                val target = all.find { it.transaction.id == updateId }
                if (target != null) {
                    val siblings = all.filter { it.transaction.transactionNumber == target.transaction.transactionNumber }
                    siblings.forEach { dao.deleteTransaction(it.transaction.id) }
                }
            }
            val prefix = if(type=="income") "MINC" else "MEXP"
            val lastNum = dao.getLastTransactionNumber(prefix)
            var nextSerial = (lastNum?.split("/")?.last()?.toIntOrNull() ?: 99999) + 1
            val year = try { LocalDate.parse(date).year } catch(ex:Exception) { LocalDate.now(ZoneId.of("UTC")).year }
            val txnNumber = "$prefix/$year/$nextSerial"
            
            // Priority 1: Issue 3 - Multi-entry atomicity
            val transactions = entries.map { e -> 
                Transaction(
                    date = date, 
                    time = time, 
                    accountId = accountId, 
                    categoryId = e.categoryId, 
                    amount = e.amount, 
                    note = e.note, 
                    transactionNumber = txnNumber, 
                    tags = tags, 
                    partyId = partyId, 
                    subName = subName, 
                    subFrequency = subFrequency, 
                    amountOriginal = e.amount, 
                    currencyCode = e.currencyCode, 
                    amountBase = e.amount, 
                    editedAt = if (updateId != null) System.currentTimeMillis() else null
                )
            }
            dao.insertTransactions(transactions)
            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }

    // Loan Management
    fun saveLoanWithAccount(loan: Loan, minorHeadId: Int, accountName: String, partyName: String?, isExisting: Boolean, disbursementAccountId: Int?, loanIssueDate: String?, issuedAmount: Double, isCreateAccount: Boolean, isUpdateBank: Boolean) {
        viewModelScope.launch {
            try {
                val allCats = dao.getAllCategories().first()
                val intExpCat = allCats.find { it.name.equals("Interest expense - Loans", ignoreCase = true) }
                val intIncCat = allCats.find { it.name.equals("Interest Income - Loans", ignoreCase = true) }
                val intMiscCat = allCats.find { it.name.equals("Interest Exp Misc", ignoreCase = true) }
                
                var accId = disbursementAccountId ?: 0
                if (isCreateAccount) {
                    val a = Account(name=accountName, type=if(loan.loanType=="BORROWING")"liability" else "asset", openingBalance=0.0, minorHeadId=minorHeadId, isEnabled=true)
                    dao.upsertAccount(a)
                    accId = dao.getAllAccountsInternal().first().find{it.name==accountName}?.id ?: 0
                }
                
                val multiplier = getMultiplier(loan.frequency)
                val firstRepay = Instant.ofEpochMilli(loan.firstRepaymentDate).atZone(ZoneId.of("UTC")).toLocalDate()
                val schedule = LoanCalculator.generateSchedule(loan.principalAmount, loan.interestRateAnnual/multiplier, loan.periodsTotal, loan.installmentAmount, loan.gapInterest, firstRepay, loan.frequency)
                
                // Calculate periods that have actually fallen due up to today
                val actualPeriodsPassedUntilToday = LoanCalculator.countPassedPeriods(firstRepay, LocalDate.now(ZoneId.of("UTC")), loan.frequency).coerceAtMost(loan.periodsTotal)
                
                val balance = if(isExisting) LoanCalculator.calculateOutstandingBalance(loan.principalAmount, loan.interestRateAnnual/multiplier, loan.periodsTotal, loan.installmentAmount, loan.gapInterest, firstRepay, loan.frequency, LocalDate.now(ZoneId.of("UTC"))) else loan.principalAmount
                
                // Record history for ALL periods that have passed up to today to ensure balance is correct
                val passedRows = if(isExisting) schedule.filter{it.period <= actualPeriodsPassedUntilToday} else emptyList()
                
                // Calculate next due date correctly
                var nextDueCalc = firstRepay
                repeat(actualPeriodsPassedUntilToday) {
                    nextDueCalc = when(loan.frequency) {
                        "MONTHLY" -> nextDueCalc.plusMonths(1)
                        "QUARTERLY" -> nextDueCalc.plusMonths(3)
                        "HALF_YEARLY" -> nextDueCalc.plusMonths(6)
                        "YEARLY" -> nextDueCalc.plusYears(1)
                        else -> nextDueCalc.plusMonths(1)
                    }
                }

                dao.upsertLoan(loan.copy(
                    accountId = accId, 
                    outstandingBalance = balance, 
                    totalInterestPaid = passedRows.sumOf { it.interestPortion },
                    periodsPassed = actualPeriodsPassedUntilToday,
                    nextDueDate = nextDueCalc.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                ))
                
                if (isExisting && isCreateAccount) {
                    val sid = getSuspenseAccountId(); val timeStr = "12:00"; val todayStr = LocalDate.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE)
                    val sourceId = if (isUpdateBank && disbursementAccountId != null) disbursementAccountId else sid

                    executeAddTransaction(loanIssueDate ?: todayStr, timeStr, if(loan.loanType=="BORROWING") accId else sourceId, null, issuedAmount, "Loan Disbursement: ${loan.name}", if(loan.loanType=="BORROWING") sourceId else accId, null, "transfer")
                    
                    passedRows.forEach { row ->
                        val date = row.dueDate.format(DateTimeFormatter.ISO_DATE)
                        val actualTotal = if (loan.isActualEmiDifferent) loan.actualRepaymentAmount else loan.installmentAmount
                        val diff = actualTotal - loan.installmentAmount

                        if(loan.loanType=="BORROWING") {
                            executeAddTransaction(date, timeStr, sourceId, null, loan.installmentAmount, "Loan Repayment: ${loan.name}", accId, loan.tags, "transfer")
                            if (intExpCat != null) executeAddTransaction(date, timeStr, accId, intExpCat.id, row.interestPortion, "Loan Interest Accrual: ${loan.name}", null, loan.tags, "expense")
                            if (diff != 0.0 && intMiscCat != null) executeAddTransaction(date, timeStr, sourceId, intMiscCat.id, diff, "Loan Repayment Adjustment: ${loan.name}", null, loan.tags, "expense")
                        } else {
                            executeAddTransaction(date, timeStr, accId, null, loan.installmentAmount, "Loan Recovery: ${loan.name}", sourceId, loan.tags, "transfer")
                            if (intIncCat != null) executeAddTransaction(date, timeStr, accId, intIncCat.id, row.interestPortion, "Loan Interest Earned: ${loan.name}", null, loan.tags, "income")
                            if (diff != 0.0 && intMiscCat != null) executeAddTransaction(date, timeStr, sourceId, intMiscCat.id, diff, "Loan Recovery Adjustment: ${loan.name}", null, loan.tags, "income")
                        }
                    }
                }
                database.checkpoint(); triggerRefresh()
            } catch (e: Exception) { Log.e("ExpenseViewModel", "Failed to save loan history", e) }
        }
    }
    private suspend fun getSuspenseAccountId(): Int { val e = dao.getSuspenseAccountInternal(); if(e!=null) return e.id; dao.upsertAccount(Account(name="Suspense", type="asset", openingBalance=0.0, isEnabled=true)); return dao.getSuspenseAccountInternal()?.id ?: 0 }
    fun ensureLoanHeadsExist() {
        viewModelScope.launch {
            val majorHeads = dao.getAllMajorHeads().first()
            var loansMajorId = majorHeads.find { it.name.equals("Loans", ignoreCase = true) }?.id
            if (loansMajorId == null) loansMajorId = dao.upsertMajorHead(MajorHead(name = "Loans", isEnabled = true)).toInt()
            val loansId = loansMajorId ?: return@launch
            val minorHeads = dao.getMinorHeadsByMajor(loansId).first()
            val expectedMinors = listOf("Banks", "NBFC", "Other FI")
            expectedMinors.forEach { if (minorHeads.none { m -> m.name.equals(it, ignoreCase = true) }) dao.upsertMinorHead(MinorHead(name = it, majorHeadId = loansId, isEnabled = true)) }
            val categories = dao.getAllCategories().first()
            val expectedCats = listOf("Interest expense - Loans" to "expense", "Interest Income - Loans" to "income", "Interest Exp Misc" to "expense")
            expectedCats.forEach { (name, type) -> if (categories.none { it.name.equals(name, ignoreCase = true) && it.type == type }) dao.upsertCategory(Category(name = name, type = type, isEnabled = true)) }
        }
    }
    fun processLoanRepayment(id: Long, amount: Double) { viewModelScope.launch { val l = dao.getLoanById(id) ?: return@launch; val m = getMultiplier(l.frequency); val s = LoanCalculator.calculatePaymentSplit(l.outstandingBalance, l.interestRateAnnual/m, amount, l.gapInterest, l.periodsPassed==0); recordLoanRepayment(LoanRepayment(loanId=id, amountPaid=amount, principalPortion=s.first, interestPortion=s.second, paymentDate=System.currentTimeMillis(), isScheduled=false)) } }
    fun recordLoanRepayment(r: LoanRepayment, tId: Int? = null) { 
        viewModelScope.launch { 
            val l = dao.getLoanById(r.loanId) ?: return@launch
            dao.upsertLoan(l.copy(totalInterestPaid=l.totalInterestPaid+r.interestPortion, totalPrincipalRepaid=l.totalPrincipalRepaid+r.principalPortion, outstandingBalance=(l.outstandingBalance-r.principalPortion).coerceAtLeast(0.0), periodsPassed=l.periodsPassed+1, nextDueDate=LoanCalculator.getNextDate(l.nextDueDate, l.frequency)))
            dao.upsertLoanRepayment(r.copy(transactionId=tId))
            triggerRefresh()
            triggerSyncOnNewRecord()
        } 
    }

    fun getPendingLoanDates(loan: Loan): List<LocalDate> {
        val today = LocalDate.now(ZoneId.of("UTC"))
        var nextDue = Instant.ofEpochMilli(loan.nextDueDate).atZone(ZoneId.of("UTC")).toLocalDate()
        val pending = mutableListOf<LocalDate>()
        var periodsPassed = loan.periodsPassed
        var currentBal = loan.outstandingBalance
        
        while (!nextDue.isAfter(today) && periodsPassed < loan.periodsTotal && currentBal > 1.0) {
            pending.add(nextDue)
            nextDue = when(loan.frequency) {
                "MONTHLY" -> nextDue.plusMonths(1)
                "QUARTERLY" -> nextDue.plusMonths(3)
                "HALF_YEARLY" -> nextDue.plusMonths(6)
                "YEARLY" -> nextDue.plusYears(1)
                else -> nextDue.plusMonths(1)
            }
            periodsPassed++
        }
        return pending
    }

    fun catchupLoanEntries(loanId: Long) {
        viewModelScope.launch {
            val loan = dao.getLoanById(loanId) ?: return@launch
            val dates = getPendingLoanDates(loan)
            if (dates.isEmpty()) return@launch

            val allCategories = dao.getAllCategories().first()
            val intExpCat = allCategories.find { it.name.equals("Interest expense - Loans", ignoreCase = true) }
            val intIncCat = allCategories.find { it.name.equals("Interest Income - Loans", ignoreCase = true) }
            val intMiscCat = allCategories.find { it.name.equals("Interest Exp Misc", ignoreCase = true) }
            
            val suspenseAcc = dao.getSuspenseAccountInternal() ?: run {
                val othersMajorId = dao.getAllMajorHeads().first().find { it.name.equals("Others", true) }?.id
                val defaultMinorId = othersMajorId?.let { dao.getMinorHeadsByMajor(it).first().find { it.name.equals("Default", true) }?.id }
                dao.upsertAccount(Account(name = "Suspense", type = "asset", openingBalance = 0.0, minorHeadId = defaultMinorId, isEnabled = true))
                dao.getSuspenseAccountInternal()
            }
            val effectiveSourceId = loan.sourceAccountId ?: suspenseAcc?.id ?: return@launch

            dates.forEach { date ->
                val currentLoan = dao.getLoanById(loanId) ?: return@forEach
                if (currentLoan.isClosed) return@forEach
                executeSingleLoanCatchup(currentLoan, date, intExpCat, intIncCat, intMiscCat, effectiveSourceId)
            }
            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }

    private suspend fun executeSingleLoanCatchup(loan: Loan, dueDate: LocalDate, intExpCat: Category?, intIncCat: Category?, intMiscCat: Category?, sourceId: Int) {
        val dateStr = dueDate.format(DateTimeFormatter.ISO_DATE)
        val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val multiplier = when(loan.frequency) { "MONTHLY"->12.0; "QUARTERLY"->4.0; "HALF_YEARLY"->2.0; "YEARLY"->1.0; else->12.0 }
        
        val split = LoanCalculator.calculatePaymentSplit(loan.outstandingBalance, loan.interestRateAnnual/multiplier, loan.installmentAmount, gapInterest = loan.gapInterest, isFirstPayment = loan.periodsPassed == 0)
        val actualTotal = if (loan.isActualEmiDifferent) loan.actualRepaymentAmount else loan.installmentAmount
        val diff = actualTotal - loan.installmentAmount

        if (loan.loanType == "BORROWING") {
            dao.insertTransaction(Transaction(date = dateStr, time = timeStr, accountId = sourceId, toAccountId = loan.accountId, categoryId = null, amount = loan.installmentAmount, note = "Loan EMI Repayment: ${loan.name}", subName = "LOAN:${loan.id}", tags = loan.tags, amountOriginal = loan.installmentAmount, currencyCode = baseCurrency, amountBase = loan.installmentAmount))
            if (intExpCat != null) dao.insertTransaction(Transaction(date = dateStr, time = timeStr, accountId = loan.accountId, categoryId = intExpCat.id, amount = split.second, note = "Loan Interest Accrual: ${loan.name}", tags = loan.tags, amountOriginal = split.second, currencyCode = baseCurrency, amountBase = split.second))
            if (diff != 0.0 && intMiscCat != null) dao.insertTransaction(Transaction(date = dateStr, time = timeStr, accountId = sourceId, categoryId = intMiscCat.id, amount = diff, note = "Loan Repayment Adjustment: ${loan.name}", tags = loan.tags, amountOriginal = diff, currencyCode = baseCurrency, amountBase = diff))
        } else {
            dao.insertTransaction(Transaction(date = dateStr, time = timeStr, accountId = loan.accountId, toAccountId = sourceId, categoryId = null, amount = loan.installmentAmount, note = "Loan EMI Recovery: ${loan.name}", subName = "LOAN:${loan.id}", tags = loan.tags, amountOriginal = loan.installmentAmount, currencyCode = baseCurrency, amountBase = loan.installmentAmount))
            if (intIncCat != null) dao.insertTransaction(Transaction(date = dateStr, time = timeStr, accountId = loan.accountId, categoryId = intIncCat.id, amount = split.second, note = "Loan Interest Earned: ${loan.name}", tags = loan.tags, amountOriginal = split.second, currencyCode = baseCurrency, amountBase = split.second))
            if (diff != 0.0 && intMiscCat != null) dao.insertTransaction(Transaction(date = dateStr, time = timeStr, accountId = sourceId, categoryId = intMiscCat.id, amount = diff, note = "Loan Recovery Adjustment: ${loan.name}", tags = loan.tags, amountOriginal = diff, currencyCode = baseCurrency, amountBase = diff))
        }

        val updatedLoan = loan.copy(
            totalInterestPaid = loan.totalInterestPaid + split.second,
            totalPrincipalRepaid = loan.totalPrincipalRepaid + split.first,
            outstandingBalance = (loan.outstandingBalance - split.first).coerceAtLeast(0.0),
            periodsPassed = loan.periodsPassed + 1,
            nextDueDate = LoanCalculator.getNextDate(loan.nextDueDate, loan.frequency),
            isClosed = Math.abs(loan.outstandingBalance - split.first) < 1.0
        )
        dao.upsertLoan(updatedLoan)
        dao.upsertLoanRepayment(LoanRepayment(loanId = loan.id, amountPaid = actualTotal, principalPortion = split.first, interestPortion = split.second, paymentDate = dueDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), isScheduled = true))
    }

    // Alerts
    fun toggleCcPaid(id: Int, p: Boolean) { toggleCcPaidCustom(id.toString(), p) }
    fun toggleCcPaidCustom(id: String, p: Boolean) { val k = LocalDate.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM")); val n = dismissedCcAlertIds.toMutableSet(); if(p) n.add("${id}_$k") else n.remove("${id}_$k"); dismissedCcAlertIds=n; prefs.edit().putStringSet("dismissed_cc_alerts", n).apply() }
    fun dismissCcAlert(id: Int) { toggleCcPaidCustom(id.toString(), true) }
    fun toggleSubscriptionStopped(n: String, s: Boolean) { viewModelScope.launch { dao.upsertSubscriptionStatus(SubscriptionStatus(n, s)); triggerRefresh() } }
    
    fun getCcAlerts(): Flow<List<CcAlert>> = combine(
        dao.getEnabledAccounts(),
        dao.getAllMinorHeads(),
        allTransactions
    ) { accounts, minorHeads, txns ->
        val today = LocalDate.now(ZoneId.of("UTC"))
        val alerts = mutableListOf<CcAlert>()
        
        accounts.filter { it.minorHeadId != null }.forEach { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            if (minor?.majorHeadId == 8) { // Credit Card
                val daysPost = acc.paymentDueDate?.toIntOrNull() ?: return@forEach
                val endDay = acc.billingCycleEnd?.toIntOrNull() ?: return@forEach
                val startDay = acc.billingCycleStart?.toIntOrNull() ?: return@forEach
                
                var cycleEnd = try {
                    val lastDay = today.lengthOfMonth()
                    LocalDate.of(today.year, today.monthValue, endDay.coerceAtMost(lastDay))
                } catch (e: Exception) { return@forEach }

                if (cycleEnd.isAfter(today)) cycleEnd = cycleEnd.minusMonths(1)

                val dueDate = cycleEnd.plusDays(daysPost.toLong())
                val daysUntil = ChronoUnit.DAYS.between(today, dueDate)
                
                if (daysUntil in -5L..3L) {
                    val k = "${acc.id}_${dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))}"
                    if (!dismissedCcAlertIds.contains(k)) {
                        var cycleStart = try {
                            var s = cycleEnd.withDayOfMonth(startDay.coerceAtMost(cycleEnd.lengthOfMonth()))
                            if (s.isAfter(cycleEnd) || s.isEqual(cycleEnd)) s = s.minusMonths(1)
                            s
                        } catch (e: Exception) { cycleEnd.minusMonths(1) }

                        val startStr = cycleStart.format(DateTimeFormatter.ISO_DATE)
                        val endStr = cycleEnd.format(DateTimeFormatter.ISO_DATE)
                        
                        val amount = txns.filter { it.transaction.accountId == acc.id || it.transaction.toAccountId == acc.id }
                            .filter { it.transaction.date in startStr..endStr }
                            .sumOf { 
                                if (it.transaction.toAccountId == acc.id) it.transaction.amount 
                                else -it.transaction.amount 
                            }

                        if (amount != 0.0) {
                            alerts.add(CcAlert(acc.id, acc.name, amount, dueDate))
                        }
                    }
                }
            }
        }
        alerts
    }

    fun getSubscriptionAlerts(): Flow<List<SubscriptionAlert>> = combine(
        allTransactions,
        dao.getAllSubscriptionStatuses()
    ) { txns, statuses ->
        val today = LocalDate.now(ZoneId.of("UTC"))
        val alerts = mutableListOf<SubscriptionAlert>()
        
        txns.filter { it.transaction.subName != null }
            .groupBy { it.transaction.subName!! }
            .entries.forEach { entry ->
                val name = entry.key
                val subTxns = entry.value
                
                val isStopped = statuses.find { it.subName == name }?.isStopped ?: false
                if (isStopped) return@forEach

                val lastTxn = subTxns.sortedByDescending { it.transaction.date }.firstOrNull() ?: return@forEach
                val freq = lastTxn.transaction.subFrequency ?: 1
                val lastDate = try { LocalDate.parse(lastTxn.transaction.date) } catch(e:Exception) { return@forEach }
                val nextDue = lastDate.plusMonths(freq.toLong())
                val daysUntil = ChronoUnit.DAYS.between(today, nextDue)

                if (daysUntil in -5L..3L) {
                    val k = "SUB_${name}_${nextDue.format(DateTimeFormatter.ofPattern("yyyy-MM"))}"
                    if (!dismissedCcAlertIds.contains(k)) {
                        val isTransfer = lastTxn.transaction.categoryId == null && lastTxn.transaction.toAccountId != null
                        alerts.add(SubscriptionAlert(name, lastTxn.transaction.amount, nextDue, isTransfer))
                    }
                }
            }
        alerts
    }

    // Formatting & Budget Reports
    fun formatAmount(a: Double) = if(useMillionsSystem) java.text.NumberFormat.getCurrencyInstance(Locale.US).format(a).replace("$","") else java.text.DecimalFormat("##,##,##,###.##").format(a)
    fun formatAmountWhole(a: Double) = if(useMillionsSystem) java.text.NumberFormat.getIntegerInstance(Locale.US).format(a) else java.text.DecimalFormat("##,##,##,###").format(a)
    fun getFilteredTransactions(s: String, e: String) = _refreshTrigger.flatMapLatest { dao.getTransactionsByDateRange(s, e) }
    fun getAccountBalances(asOfDate: String) = _refreshTrigger.flatMapLatest { dao.getAccountBalances(asOfDate) }
    fun getAccountTransactions(id: Int, s: String, e: String) = dao.getAccountTransactionsByDateRange(id, s, e)
    fun getTransactionsByTags(ids: List<Int>, s: String, e: String) = dao.getTransactionsByDateRange(s, e).map { l -> l.filter { d -> (d.transaction.tags?.split(",")?.mapNotNull{it.toIntOrNull()}?:emptyList()).any{it in ids} } }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getBudgetVsActual(asOfDate: String): Flow<List<BudgetVsActual>> {
        val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now(ZoneId.of("UTC")) }
        return combine(dao.getAllBudgets(), dao.getAllAccountsInternal(), dao.getEnabledCategories()) { budgets, accounts, categories ->
            budgets.map { budget ->
                val range = getRangeForDuration(date, budget.duration)
                dao.getTransactionsByDateRange(range.first, range.second).map { transactions ->
                    val catIds = budget.categoryIds.split(",").mapNotNull { it.toIntOrNull() }
                    val targetAccountIds = budget.accountIds?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    val relevant = transactions.filter { (it.transaction.categoryId in catIds) || (it.transaction.accountId in targetAccountIds && it.transaction.categoryId != null) }
                    val actual = relevant.sumOf { if (it.categoryType == "expense" || (it.transaction.accountId in targetAccountIds && it.transaction.categoryId != null)) it.transaction.amount else -it.transaction.amount }
                    val catNames = catIds.mapNotNull { id -> categories.find { it.id == id }?.name }.toMutableList()
                    catNames.addAll(targetAccountIds.mapNotNull { id -> accounts.find { it.id == id }?.name })
                    BudgetVsActual(budget.name ?: catNames.joinToString(", "), if (relevant.any { it.categoryType == "expense" || it.transaction.accountId in targetAccountIds }) "expense" else "income", budget.amount, actual, budget.duration, budget.higherIsBetter, catIds, targetAccountIds, range.first, range.second)
                }
            }
        }.flatMapLatest { if (it.isEmpty()) flowOf(emptyList()) else combine(it) { it.toList() } }
    }

    private fun getRangeForDuration(date: LocalDate, duration: String): Pair<String, String> {
        val formatter = DateTimeFormatter.ISO_DATE
        return when (duration) {
            "Weekly" -> Pair(date.with(java.time.DayOfWeek.MONDAY).format(formatter), date.with(java.time.DayOfWeek.SUNDAY).format(formatter))
            "Monthly" -> Pair(date.with(TemporalAdjusters.firstDayOfMonth()).format(formatter), date.with(TemporalAdjusters.lastDayOfMonth()).format(formatter))
            "Yearly" -> Pair(date.with(TemporalAdjusters.firstDayOfYear()).format(formatter), date.with(TemporalAdjusters.lastDayOfYear()).format(formatter))
            else -> Pair(date.format(formatter), date.format(formatter))
        }
    }

    fun refreshDatabase(enqueueWorker: Boolean = true) {
        viewModelScope.launch {
            AppDatabase.databaseMutex.withLock {
                AppDatabase.closeDatabase()
                _database = null
                _dao = null
            }
            triggerRefresh()
            if (enqueueWorker) {
                val manager = WorkManager.getInstance(getApplication())
                manager.enqueue(OneTimeWorkRequestBuilder<CcAlertWorker>().build())
            }
        }
    }

    suspend fun prepareForBackup() = AppDatabase.databaseMutex.withLock {
        database.checkpoint()
        AppDatabase.closeDatabase()
        _database = null
        _dao = null
    }

    suspend fun performSafeBackup(context: Context, destUri: Uri): Boolean = AppDatabase.databaseMutex.withLock {
        try {
            val df = context.getDatabasePath("expenses_database")
            if (!df.exists()) return false
            
            _database?.checkpoint()
            AppDatabase.closeDatabase()
            
            withContext(Dispatchers.Main) {
                _database = null
                _dao = null
            }
            
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                FileInputStream(df).use { input -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Safe backup failed", e)
            false
        }
    }

    suspend fun performSafeEncryptedBackup(context: Context, tempEnc: File, destUri: Uri): Boolean = AppDatabase.databaseMutex.withLock {
        try {
            val df = context.getDatabasePath("expenses_database")
            if (!df.exists()) return false
            
            _database?.checkpoint()
            AppDatabase.closeDatabase()
            
            withContext(Dispatchers.Main) {
                _database = null
                _dao = null
            }
            
            val result = EncryptionService.encryptFile(df, tempEnc, remoteMasterPassword)
            if (result.isSuccess) {
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    tempEnc.inputStream().use { input -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Encrypted backup saved!", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown"
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Encryption failed: $err", Toast.LENGTH_LONG).show()
                }
                false
            }
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Safe encrypted backup failed", e)
            false
        }
    }

    fun closeDatabase() { 
        viewModelScope.launch { AppDatabase.closeDatabase() }
    }
    fun authorizePairing(p: String) = serverManager.authorizePairing(p)
    fun disconnectClient(i: String) = serverManager.disconnectClient(i)
    fun toggleServer(o: Boolean) = if(o) serverManager.startServer() else serverManager.stopServer()
    suspend fun exportConfigsJson(): String {
        try {
            val majors = dao.getAllMajorHeads().first()
            val minors = dao.getAllMinorHeads().first()
            val accounts = dao.getAllAccounts().first()
            val categories = dao.getAllCategories().first()
            val budgets = dao.getAllBudgets().first()
            val tags = dao.getAllTags().first()
            val templates = dao.getAllTemplates().first()

            val export = FinTrackConfigExport(
                majorHeads = majors.map { MajorHeadConfig(it.name, it.isEnabled) },
                minorHeads = minors.map { m ->
                    val major = majors.find { it.id == m.majorHeadId }
                    MinorHeadConfig(m.name, major?.name ?: "Others", m.isEnabled)
                },
                accounts = accounts.map { a ->
                    val minor = minors.find { it.id == a.minorHeadId }
                    val major = majors.find { it.id == minor?.majorHeadId }
                    AccountConfig(a.name, a.type, a.openingBalance, a.description, a.isEnabled, minor?.name, major?.name, a.creditLimit, a.billingCycleStart, a.billingCycleEnd, a.paymentDueDate)
                },
                categories = categories.map { CategoryConfig(it.name, it.type, it.description, it.isEnabled) },
                templates = templates.map { t ->
                    val acc = accounts.find { it.id == t.accountId }
                    val toAcc = accounts.find { it.id == t.toAccountId }
                    val cat = categories.find { it.id == t.categoryId }
                    TemplateConfig(t.name, t.type, acc?.name, toAcc?.name, cat?.name, cat?.type, t.amount, t.note, t.tags, t.multiEntries, t.subName, t.subFrequency)
                },
                budgets = budgets.map { b ->
                    val catIds = b.categoryIds.split(",").mapNotNull { it.toIntOrNull() }
                    val accIds = b.accountIds?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    BudgetConfig(
                        b.name,
                        catIds.mapNotNull { id -> categories.find { it.id == id }?.name },
                        accIds.mapNotNull { id -> accounts.find { it.id == id }?.name },
                        b.amount, b.duration, b.note, b.higherIsBetter
                    )
                },
                tags = tags.map { TagConfig(it.name, it.isEnabled) }
            )
            return Json.encodeToString(export)
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Export failed", e)
            return ""
        }
    }
    suspend fun importConfigsJson(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val config = Json.decodeFromString<FinTrackConfigExport>(json)
            val existingTags = dao.getAllTags().first()
            config.tags.forEach { tagCfg ->
                val existing = existingTags.find { it.name == tagCfg.name }
                dao.upsertTag(Tag(id = existing?.id ?: 0, name = tagCfg.name, isEnabled = tagCfg.isEnabled))
            }
            val existingMajors = dao.getAllMajorHeads().first()
            config.majorHeads.forEach { majorCfg ->
                val existing = existingMajors.find { it.name == majorCfg.name }
                dao.upsertMajorHead(MajorHead(id = existing?.id ?: 0, name = majorCfg.name, isEnabled = majorCfg.isEnabled))
            }
            val allMajors = dao.getAllMajorHeads().first()
            val existingMinors = dao.getAllMinorHeads().first()
            config.minorHeads.forEach { m ->
                val major = allMajors.find { it.name == m.majorHeadName }
                if (major != null) {
                    val existing = existingMinors.find { it.name == m.name && it.majorHeadId == major.id }
                    dao.upsertMinorHead(MinorHead(id = existing?.id ?: 0, name = m.name, majorHeadId = major.id, isEnabled = m.isEnabled))
                }
            }
            val allMinors = dao.getAllMinorHeads().first()
            val currentAccounts = dao.getAllAccounts().first()
            config.accounts.forEach { a ->
                val minor = allMinors.find { it.name == a.minorHeadName }
                val existing = currentAccounts.find { it.name == a.name }
                dao.upsertAccount(Account(
                    id = existing?.id ?: 0,
                    name = a.name, type = a.type, openingBalance = a.openingBalance, description = a.description,
                    isEnabled = a.isEnabled, minorHeadId = minor?.id, creditLimit = a.creditLimit,
                    billingCycleStart = a.billingCycleStart, billingCycleEnd = a.billingCycleEnd, paymentDueDate = a.paymentDueDate
                ))
            }
            val allAccounts = dao.getAllAccounts().first()
            val existingCats = dao.getAllCategories().first()
            config.categories.forEach { c ->
                val existing = existingCats.find { it.name == c.name && it.type == c.type }
                dao.upsertCategory(Category(id = existing?.id ?: 0, name = c.name, type = c.type, description = c.description, isEnabled = c.isEnabled))
            }
            val allCategories = dao.getAllCategories().first()
            val currentTemplates = dao.getAllTemplates().first()
            config.templates.forEach { t ->
                val acc = allAccounts.find { it.name == t.accountName }
                val toAcc = allAccounts.find { it.name == t.toAccountName }
                val cat = allCategories.find { it.name == t.categoryName && it.type == (t.categoryType ?: "expense") }
                val existing = currentTemplates.find { it.name == t.name }
                dao.upsertTemplate(Template(
                    id = existing?.id ?: 0,
                    name = t.name, type = t.type, accountId = acc?.id, toAccountId = toAcc?.id, categoryId = cat?.id,
                    amount = t.amount, note = t.note, tags = t.tags, multiEntries = t.multiEntries,
                    subName = t.subName, subFrequency = t.subFrequency
                ))
            }
            val currentBudgets = dao.getAllBudgets().first()
            config.budgets.forEach { b ->
                val catIds = b.categoryNames.mapNotNull { name -> allCategories.find { it.name == name }?.id }.joinToString(",")
                val accIds = b.accountNames.mapNotNull { name -> allAccounts.find { it.name == name }?.id }.joinToString(",")
                val existing = currentBudgets.find { it.name == b.name && it.categoryIds == catIds }
                dao.upsertBudget(Budget(id = existing?.id ?: 0, name = b.name, categoryIds = catIds, amount = b.amount, duration = b.duration, note = b.note, higherIsBetter = b.higherIsBetter, accountIds = accIds))
            }
            withContext(Dispatchers.Main) { triggerRefresh() }
            Result.success(Unit)
        } catch (e: Exception) { Log.e("ExpenseViewModel", "Import failed", e); Result.failure(e) }
    }
    private fun getMultiplier(f: String) = when(f) { "MONTHLY"->12.0; "QUARTERLY"->4.0; "HALF_YEARLY"->2.0; "YEARLY"->1.0; else->12.0 }

    private fun triggerSyncOnNewRecord() {
        if (syncFrequency == "On new record" && remoteSyncEnabled) {
            pendingSyncJob?.cancel()
            pendingSyncJob = viewModelScope.launch(Dispatchers.IO) {
                delay(5000)
                syncNow()
            }
        }
    }

    fun generateFinancialInsights() {
        if (isGeneratingInsights) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    isGeneratingInsights = true
                    financialInsights.clear()
                    showInsightsOverlay = true
                }

                val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                val txns = dao.getAllTransactionsWithDetails().first()
                val balances = dao.getAccountBalances(today).first()
                val loans = dao.getAllActiveLoans().first()
                val budgets = getBudgetVsActual(today).first()
                val majorHeads = dao.getAllMajorHeads().first()
                val minorHeads = dao.getAllMinorHeads().first()

                val insights = insightEngine.generateInsights(txns, balances, budgets, loans, majorHeads, minorHeads)

                withContext(Dispatchers.Main) {
                    financialInsights.addAll(insights)
                    isGeneratingInsights = false
                }
            } catch (e: Exception) {
                Log.e("Insights", "Error generating statistical insights", e)
                withContext(Dispatchers.Main) { isGeneratingInsights = false }
            }
        }
    }

    fun dismissInsight(insight: FinancialInsight) {
        financialInsights.remove(insight)
    }
}

data class BudgetVsActual(val categoryName: String, val categoryType: String, val budgetAmount: Double, val actualAmount: Double, val duration: String, val higherIsBetter: Boolean = false, val categoryIds: List<Int> = emptyList(), val accountIds: List<Int> = emptyList(), val startDate: String = "", val endDate: String = "")
data class DraftTransaction(val type: String, val amount: String, val note: String, val date: String, val time: String, val accountId: Int?, val toAccountId: Int?, val categoryId: Int?, val selectedTagIds: List<Int>, val isMultiEntry: Boolean = false, val multiEntryRows: List<DraftMultiEntryRow> = emptyList())
data class DraftMultiEntryRow(val categoryId: Int?, val amount: String, val note: String? = null, val currencyCode: String? = null)
data class MultiEntryRowData(val categoryId: Int, val amount: Double, val note: String?, val currencyCode: String)
data class DraftAccount(val name: String, val type: String, val description: String, val openingBalance: String, val isEnabled: Boolean, val selectedMajorHeadId: Int?, val selectedMinorHeadId: Int?, val creditLimit: String, val billingCycleStart: String, val billingCycleEnd: String, val paymentDueDate: String, val icon: String? = null)
data class CcAlert(val accountId: Int, val accountName: String, val amount: Double, val dueDate: LocalDate)
data class SubscriptionAlert(val subName: String, val amount: Double, val dueDate: LocalDate, val isTransfer: Boolean = false)
