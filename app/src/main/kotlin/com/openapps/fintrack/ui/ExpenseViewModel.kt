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

package com.openapps.fintrack.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Telephony
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import com.openapps.fintrack.data.*
import com.openapps.fintrack.domain.model.Money
import com.openapps.fintrack.domain.model.BudgetVsActual
import com.openapps.fintrack.domain.model.*
import com.openapps.fintrack.domain.repository.FinanceRepository
import com.openapps.fintrack.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

data class InvoiceCrossReference(
    val invoiceId: Int,
    val invoiceTxnNumber: String?,
    val invoiceDate: String,
    val invoiceAmount: Long,
    val invoiceNumber: String?,
    val totalCleared: Long,
    val pendingAmount: Long,
    val clearingTransfers: List<ClearingTransferDetails>
)

data class ClearingTransferDetails(
    val transferTxnId: Int,
    val transferTxnNumber: String?,
    val transferDate: String,
    val amountApplied: Long,
    val otherAccountName: String?
)

data class ExtractedTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Double,
    val isCredit: Boolean,
    val sourceColumn: String? = null
)

enum class ColumnRole { DATE, DESCRIPTION, AMOUNT, DEBIT, CREDIT, DR_CR, BALANCE, IGNORE }

data class ColumnAssignment(
    val role: ColumnRole,
    val start: Float,
    val end: Float
)

data class ColumnMap(
    val assignments: List<ColumnAssignment>
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    application: Application,
    private val repositoryProvider: Provider<FinanceRepository>,
    private val sharedPreferences: SharedPreferences,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val getBudgetPerformanceUseCase: GetBudgetPerformanceUseCase,
    private val generateFinancialInsightsUseCase: GenerateFinancialInsightsUseCase,
    private val getCcCycleInfoUseCase: GetCcCycleInfoUseCase,
    private val getSmartCardSuggestionUseCase: GetSmartCardSuggestionUseCase,
    private val detectRecurringTransactionsUseCase: DetectRecurringTransactionsUseCase
) : AndroidViewModel(application) {
    internal val prefs = sharedPreferences
    internal val repository: FinanceRepository get() = repositoryProvider.get()
    
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

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()
    fun triggerRefresh() { _refreshTrigger.value += 1 }

    // Advanced Features Flows
    @OptIn(ExperimentalCoroutinesApi::class)
    val ccCycles: Flow<List<CcCycleInfo>> = _refreshTrigger.flatMapLatest { getCcCycleInfoUseCase() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val smartCardSuggestion: Flow<SmartCardSuggestion?> = _refreshTrigger.flatMapLatest { getSmartCardSuggestionUseCase() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val subscriptionSuggestions: Flow<List<SubscriptionSuggestion>> = _refreshTrigger.flatMapLatest { detectRecurringTransactionsUseCase() }

    // SMS Review Inbox
    @OptIn(ExperimentalCoroutinesApi::class)
    val smsDrafts: Flow<List<SmsTransactionDraft>> = _refreshTrigger.flatMapLatest { repository.getAllSmsDrafts() }

    fun deleteSmsDraft(draft: SmsTransactionDraft) {
        viewModelScope.launch {
            repository.deleteSmsDraft(draft)
            triggerRefresh()
        }
    }

    fun recordFromSmsDraft(draft: SmsTransactionDraft, accountId: Int, categoryId: Int?, tags: String?) {
        viewModelScope.launch {
            val lines = listOf(
                TransactionLineData(
                    accountId = accountId,
                    categoryId = categoryId,
                    amount = draft.amount
                )
            )
            val tagIds = tags?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            
            addTransactionUseCase(
                date = draft.date,
                time = draft.time,
                note = draft.body,
                type = draft.type,
                lines = lines,
                tags = tagIds,
                merchantName = draft.merchantName,
                baseCurrency = baseCurrency
            )
            repository.deleteSmsDraft(draft)
            triggerRefresh()
        }
    }

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
    var editingBudgetRaw by mutableStateOf<BudgetWithRelations?>(null)
    var editingTemplate by mutableStateOf<TemplateLegacy?>(null)
    var editingNote by mutableStateOf<Note?>(null)
    var selectedNotebookId by mutableStateOf<Int?>(null)
    var draftTransaction by mutableStateOf<DraftTransaction?>(null)
    var currentRecordingImportTxnKey by mutableStateOf<String?>(null)
    var draftAccount by mutableStateOf<DraftAccount?>(null)

    // Import Statement State
    var pendingTransactions by mutableStateOf<List<ExtractedTransaction>>(emptyList())
    var importStatus by mutableStateOf("")
    var showAccountSelection by mutableStateOf(true)
    var selectedImportAccount by mutableStateOf<Account?>(null)
    var rawRows by mutableStateOf<List<List<String>>>(emptyList())
    var detectedColumnMap by mutableStateOf<ColumnMap?>(null)
    var isShowingTablePreview by mutableStateOf(false)
    var importType by mutableStateOf("CSV")

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
    var bottomTabOrder by mutableStateOf(
        run {
            val saved = prefs.getString("bottom_tab_order", null)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val customTabs = saved.filter { it != "home" }
            val defaults = listOf("analysis", "transactions", "budgets")
            val finalCustom = (customTabs + defaults).distinct().take(3)
            listOf("home") + finalCustom
        }
    )
    var remindersEnabled by mutableStateOf(prefs.getBoolean("reminders_enabled", false))
    var reminderFrequency by mutableStateOf(prefs.getInt("reminder_frequency", 1))
    var reminderUnit by mutableStateOf(prefs.getString("reminder_unit", "day/s") ?: "day/s")
    var reminderTime by mutableStateOf(prefs.getString("reminder_time", "20:00") ?: "20:00")
    var reminderMessage by mutableStateOf(prefs.getString("reminder_message", "Record expenses!") ?: "Record expenses!")
    var ccAlertEnabled by mutableStateOf(prefs.getBoolean("cc_alert_enabled", false))
    var dismissedCcAlertIds by mutableStateOf(prefs.getStringSet("dismissed_cc_alerts", emptySet()) ?: emptySet())
    var disableScreenshots by mutableStateOf(prefs.getBoolean("disable_screenshots", false))
    var tapToShowNetPosition by mutableStateOf(prefs.getBoolean("tap_to_show_net_position", false))
    var negotiationTrackerEnabled by mutableStateOf(prefs.getBoolean("negotiation_tracker_enabled", false))
    var merchantTrackerEnabled by mutableStateOf(prefs.getBoolean("merchant_tracker_enabled", false))
    var discretionarySpendingTrackerEnabled by mutableStateOf(prefs.getBoolean("discretionary_spending_tracker_enabled", false))
    var incomeAtMonthEnd by mutableStateOf(prefs.getBoolean("income_at_month_end", false))
    var invoiceAgeTrackingEnabled by mutableStateOf(prefs.getBoolean("invoice_age_tracking_enabled", false))
    var disableTransactionDeletion by mutableStateOf(prefs.getBoolean("disable_transaction_deletion", false))
    var appLanguage by mutableStateOf(prefs.getString("app_language", "en") ?: "en")
    var decimalPlaces by mutableStateOf(prefs.getInt("decimal_places", 2))
    var homeBackgroundIndex by mutableStateOf(prefs.getInt("home_bg_index", 0))

    fun updateLanguage(langCode: String) {
        appLanguage = langCode
        prefs.edit().putString("app_language", langCode).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))
    }

    fun updateDisableTransactionDeletion(enabled: Boolean) {
        disableTransactionDeletion = enabled
        prefs.edit().putBoolean("disable_transaction_deletion", enabled).apply()
    }

    // WebDAV
    var remoteSyncEnabled by mutableStateOf(prefs.getBoolean("remote_sync_enabled", false))
    var webdavUrl by mutableStateOf(EncryptedPrefsHelper.getString("webdav_url", "") ?: "")
    var webdavUsername by mutableStateOf(EncryptedPrefsHelper.getString("webdav_user", "") ?: "")
    var webdavPassword by mutableStateOf(EncryptedPrefsHelper.getString("webdav_pass", "") ?: "")
    var serverPort by mutableStateOf(prefs.getInt("server_port", 8080))
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

    var currentSyncStatus by mutableStateOf("")

    var isTestingConnection by mutableStateOf(false)

    // Sync on new record job
    private var pendingSyncJob: Job? = null

    // Security
    var encryptRemoteEnabled by mutableStateOf(prefs.getBoolean("encrypt_remote_enabled", false))
    var remoteMasterPassword by mutableStateOf(EncryptedPrefsHelper.getString("remote_master_password", "")?.toCharArray() ?: charArrayOf())
    var secureModeEnabled by mutableStateOf(prefs.getBoolean("secure_mode_enabled", false))
    var isDatabaseDecrypted by mutableStateOf(false)
    var isPickingFile by mutableStateOf(false)
    var isImportingDatabase by mutableStateOf(false)
    
    private val encryptionMutex = Mutex()
    private val serverManager: ServerManager by lazy { 
        ServerManager.Companion.getInstance(getApplication(), repository).apply {
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
    var showWhatIsNew by mutableStateOf(false)
    var keyChanges by mutableStateOf("")

    fun checkAppUpdate(currentVersion: Int) {
        val lastVersion = prefs.getInt("last_seen_version", 0)
        if (currentVersion > lastVersion) {
            keyChanges = """
                - Introducing Financial Goals section to save money for specific goals.
                - Introducing Fintrack linux desktop application (More info coming on our site).
                - Added upload and download button from cloud url in homescreen for easy tap and upload / download.
                - Added FD Maturity trackers and quick dashboard.
                - Supports custom port during web server creation.
                - Background wallpaper can be changed to preset ones for total expense and income summary card in home screen.
                - Home screen pill customization.
                - Transaction level drill down in Categories screen.
                - Transaction raw string export and import.
            """.trimIndent()
            showWhatIsNew = true
            prefs.edit().putInt("last_seen_version", currentVersion).apply()
        }
    }
    var isGeneratingInsights by mutableStateOf(false)
    var showInsightsOverlay by mutableStateOf(false)

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
    fun updateMultiCurrencyEnabled(e: Boolean) { enableMultiCurrency = e; prefs.edit().putBoolean("enable_multi_currency", e).commit(); if(e) refreshExchangeRates() }
    fun updateBaseCurrency(c: String) { baseCurrency = c; EncryptedPrefsHelper.putString("base_currency", c); prefs.edit().putString("base_currency", c).commit(); refreshExchangeRates() }
    fun updateDashboardAccounts(ids: List<Int>) { dashboardAccountIds = ids; prefs.edit().putString("dashboard_account_ids", ids.joinToString(",")).apply() }
    fun updateDashboardBudgets(ids: List<Int>) { dashboardBudgetIds = ids; prefs.edit().putString("dashboard_budget_ids", ids.joinToString(",")).apply() }
    fun updateTabOrder(o: List<String>) { val custom = o.filter { it != "home" }.take(3); val fixed = listOf("home") + custom; bottomTabOrder = fixed; prefs.edit().putString("bottom_tab_order", fixed.joinToString(",")).apply() }
    fun updateReminderEnabled(e: Boolean) { remindersEnabled = e; prefs.edit().putBoolean("reminders_enabled", e).apply(); scheduleReminders() }
    fun updateReminderFrequency(f: Int) { reminderFrequency = f; prefs.edit().putInt("reminder_frequency", f).apply(); scheduleReminders() }
    fun updateReminderUnit(u: String) { reminderUnit = u; prefs.edit().putString("reminder_unit", u).apply(); scheduleReminders() }
    fun updateReminderTime(t: String) { reminderTime = t; prefs.edit().putString("reminder_time", t).apply(); scheduleReminders() }
    fun updateReminderMessage(m: String) { reminderMessage = m; prefs.edit().putString("reminder_message", m).apply() }
    fun updateCcAlertEnabled(e: Boolean) { ccAlertEnabled = e; prefs.edit().putBoolean("cc_alert_enabled", e).apply(); scheduleCcAlerts() }
    fun updateRemoteSyncEnabled(e: Boolean) { remoteSyncEnabled = e; prefs.edit().putBoolean("remote_sync_enabled", e).apply() }
    fun updateSyncFrequency(f: String) { syncFrequency = f; prefs.edit().putString("sync_frequency", f).apply() }
    fun updateWebdavUrl(u: String) { 
        val cleanUrl = if (u.startsWith("http://", ignoreCase = true)) "https://" + u.substring(7) else u
        webdavUrl = cleanUrl
        EncryptedPrefsHelper.putString("webdav_url", cleanUrl) 
    }
    fun updateServerPort(p: Int) {
        serverPort = p
        prefs.edit().putInt("server_port", p).apply()
        if (isServerRunning.value) {
            serverManager.restartServer(httpPort = p, httpsPort = if (p == 8080) 8443 else p + 1)
        }
    }
    fun updateWebdavUsername(u: String) { webdavUsername = u; EncryptedPrefsHelper.putString("webdav_user", u) }
    fun updateWebdavPassword(p: String) { webdavPassword = p; EncryptedPrefsHelper.putString("webdav_pass", p) }
    fun updateRemoteMasterPassword(p: String) { 
        remoteMasterPassword.fill('\u0000')
        remoteMasterPassword = p.toCharArray()
        EncryptedPrefsHelper.putString("remote_master_password", p) 
    }
    fun updateEncryptRemote(e: Boolean) { encryptRemoteEnabled = e; prefs.edit().putBoolean("encrypt_remote_enabled", e).apply() }
    fun updateTemplateField(f: String, e: Boolean) { val n = templateFields.toMutableSet(); if(e) n.add(f) else n.remove(f); templateFields = n; prefs.edit().putStringSet("template_fields", n).apply() }
    fun updateDisableScreenshots(e: Boolean) { disableScreenshots = e; prefs.edit().putBoolean("disable_screenshots", e).apply() }
    fun updateTapToShowNetPosition(e: Boolean) { tapToShowNetPosition = e; prefs.edit().putBoolean("tap_to_show_net_position", e).apply() }
    fun updateNegotiationTrackerEnabled(e: Boolean) { negotiationTrackerEnabled = e; prefs.edit().putBoolean("negotiation_tracker_enabled", e).apply() }
    fun updateMerchantTrackerEnabled(e: Boolean) { merchantTrackerEnabled = e; prefs.edit().putBoolean("merchant_tracker_enabled", e).apply() }
    fun updateDiscretionarySpendingTrackerEnabled(e: Boolean) { discretionarySpendingTrackerEnabled = e; prefs.edit().putBoolean("discretionary_spending_tracker_enabled", e).apply() }
    fun updateIncomeAtMonthEnd(enabled: Boolean) { incomeAtMonthEnd = enabled; prefs.edit().putBoolean("income_at_month_end", enabled).apply(); triggerRefresh() }
    fun updateInvoiceAgeTrackingEnabled(enabled: Boolean) { invoiceAgeTrackingEnabled = enabled; prefs.edit().putBoolean("invoice_age_tracking_enabled", enabled).apply(); triggerRefresh() }
    fun updateDecimalPlaces(v: Int) { decimalPlaces = v; prefs.edit().putInt("decimal_places", v).apply(); triggerRefresh() }
    fun updateHomeBackgroundIndex(v: Int) { homeBackgroundIndex = v; prefs.edit().putInt("home_bg_index", v).apply(); triggerRefresh() }

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
        
        val ccAlertRequest = androidx.work.PeriodicWorkRequestBuilder<com.openapps.fintrack.data.CcAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) 
            .build()

        workManager.enqueueUniquePeriodicWork(
            "cc_due_alerts",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            ccAlertRequest
        )
    }

    // Security Logic
    fun updateSecureMode(e: Boolean) { 
        if (!e) {
            disableUltraSecureMode {}
        } else {
            secureModeEnabled = true
            prefs.edit().putBoolean("secure_mode_enabled", true).apply()
            val bp = getApplication<Application>().getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            bp.edit().putBoolean("encrypt_scheduled_backup", true).apply()
            
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (repository.getEnabledRulesInternal().isNotEmpty()) {
                        prefs.edit().putLong("last_secure_close_time", System.currentTimeMillis()).apply()
                    }
                } catch(e: Exception) {}
            }

            isDatabaseDecrypted = true 
        }
    }

    fun disableUltraSecureMode(onRequirePassword: () -> Unit) {
        val df = getApplication<Application>().getDatabasePath("expenses_database")
        val ef = File(df.path + ".xpt")
        
        if (ef.exists()) {
            val storedPass = EncryptedPrefsHelper.getString("remote_master_password", "")
            val passToUse = if (remoteMasterPassword.isNotEmpty()) String(remoteMasterPassword) else storedPass ?: ""
            if (passToUse.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val decSuccess = decryptDatabaseAtRest(passToUse)
                    withContext(Dispatchers.Main) {
                        if (decSuccess) {
                            secureModeEnabled = false
                            prefs.edit().putBoolean("secure_mode_enabled", false).apply()
                            prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                            isDatabaseDecrypted = true
                        } else {
                            onRequirePassword()
                        }
                    }
                }
            } else {
                onRequirePassword()
            }
        } else {
            secureModeEnabled = false
            prefs.edit().putBoolean("secure_mode_enabled", false).apply()
            prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
            isDatabaseDecrypted = true
        }
    }

    suspend fun disableSecureModeWithPassword(p: String): Boolean = withContext(Dispatchers.IO) {
        val decSuccess = decryptDatabaseAtRest(p)
        if (decSuccess) {
            withContext(Dispatchers.Main) {
                secureModeEnabled = false
                prefs.edit().putBoolean("secure_mode_enabled", false).apply()
                prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                isDatabaseDecrypted = true
            }
            return@withContext true
        }
        return@withContext false
    }

    fun checkDatabaseEncryptionStatus(): Boolean {
        val df = getApplication<Application>().getDatabasePath("expenses_database")
        val ef = File(df.path + ".xpt")
        
        val needsDecryption = ef.exists() || (secureModeEnabled && (!df.exists() || df.length() < 1024))
        isDatabaseDecrypted = !needsDecryption
        return needsDecryption
    }
    
    fun encryptDatabaseAtRest() {
        val storedPass = EncryptedPrefsHelper.getString("remote_master_password", "") ?: ""
        val passToUse: CharArray = if (remoteMasterPassword.isNotEmpty()) remoteMasterPassword.copyOf()
                        else storedPass.toCharArray()
                        
        if (!secureModeEnabled || passToUse.isEmpty() || serverManager.isRunning.value) {
            if (remoteMasterPassword.isEmpty() && storedPass.isNotEmpty()) passToUse.fill('\u0000')
            return
        }
        
        if (!isDatabaseDecrypted) {
            Log.w("SecureMode", "Database is not currently decrypted. Skipping encryption to prevent data loss.")
            if (remoteMasterPassword.isEmpty() && storedPass.isNotEmpty()) passToUse.fill('\u0000')
            return
        }

        val df = getApplication<Application>().getDatabasePath("expenses_database")
        if (!df.exists()) return

        isDatabaseDecrypted = false
        prefs.edit().putBoolean("db_encrypted_at_rest", true).commit()

        runBlocking(Dispatchers.IO) { 
            AppDatabase.databaseMutex.withLock {
                try { 
                    val targetDb = getApplication<Application>().getDatabasePath("expenses_database")
                    if (!targetDb.exists()) return@withLock
                    
                    try { repository.prepareForBackup() } catch(e: Exception) {}
                    AppDatabase.closeDatabase()
                    
                    val ef = File(targetDb.path + ".xpt")
                    val result = EncryptionService.encryptFile(targetDb, ef, passToUse)
                    if (result.isSuccess) { 
                        targetDb.delete()
                        File(targetDb.path + "-shm").delete()
                        File(targetDb.path + "-wal").delete()
                        File(targetDb.path + "-journal").delete() 
                        
                        remoteMasterPassword.fill('\u0000')
                        remoteMasterPassword = charArrayOf()
                        Log.d("SecureMode", "Database is now encrypted at rest.")
                    } else {
                        isDatabaseDecrypted = true
                        prefs.edit().putBoolean("db_encrypted_at_rest", false).commit()
                    }
                } catch(ex: Exception) { 
                    Log.e("SecureMode", "Background encryption failed", ex) 
                    isDatabaseDecrypted = true
                    prefs.edit().putBoolean("db_encrypted_at_rest", false).commit()
                } finally {
                    passToUse.fill('\u0000')
                }
            } 
        }
    }

    suspend fun decryptDatabaseAtRest(p: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) { 
        AppDatabase.databaseMutex.withLock {
            try { 
                val df = getApplication<Application>().getDatabasePath("expenses_database")
                val ef = File(df.path + ".xpt")
                val pChars = p.toCharArray()

                if (!ef.exists()) { 
                    withContext<Unit>(Dispatchers.Main) {
                        isDatabaseDecrypted = true
                        prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                    }
                    return@withLock true 
                }
                
                AppDatabase.closeDatabase()
                val filesToDelete = listOf(
                    df,
                    File(df.path + "-shm"),
                    File(df.path + "-wal"),
                    File(df.path + "-journal")
                )
                filesToDelete.forEach { if (it.exists()) it.delete() }

                val result = EncryptionService.decryptFile(ef, df, pChars) { progress ->
                    onProgress(progress)
                }
                if (result.isSuccess) { 
                    ef.delete()
                    withContext<Unit>(Dispatchers.Main) {
                        isDatabaseDecrypted = true
                        prefs.edit().putBoolean("db_encrypted_at_rest", false).apply()
                        remoteMasterPassword = pChars
                        triggerRefresh() 
                        processPendingSmsRules()
                    }
                    Log.d("SecureMode", "Database decrypted successfully from .xpt")
                    return@withLock true 
                } else {
                    pChars.fill('\u0000')
                    val error = result.exceptionOrNull()?.message ?: "Incorrect Password"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Decryption Failed: $error", Toast.LENGTH_LONG).show()
                    }
                    return@withLock false
                }
            } catch(ex: Exception) { 
                Log.e("SecureMode", "Dec error", ex) 
                return@withLock false
            }
        } 
    }
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
        val repository = repositoryProvider.get()
        
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
            repository.checkpoint()
            
            if (ef.exists()) {
                withContext(Dispatchers.Main) { syncMessage = "Preparing Encrypted DB" }
                ef.copyTo(tf, true)
            } else {
                if (df.exists()) {
                    withContext(Dispatchers.Main) { syncMessage = "Snapshotting" }
                    FileInputStream(df).use { i -> FileOutputStream(ts).use { o -> i.copyTo(o) } }
                    if (me) {
                        withContext(Dispatchers.Main) { syncMessage = "Encrypting" }
                        val storedPass = EncryptedPrefsHelper.getString("remote_master_password", "")
                        val passToUse: CharArray = if (remoteMasterPassword.isNotEmpty()) remoteMasterPassword 
                                        else (storedPass?.toCharArray() ?: charArrayOf())
                        
                        val result = EncryptionService.encryptFile(ts, tf, passToUse) { progress ->
                            viewModelScope.launch(Dispatchers.Main) { syncProgress = progress * 0.3f }
                        }
                        
                        if (remoteMasterPassword.isEmpty() && storedPass != null) passToUse.fill('\u0000')

                        if (result.isFailure) throw Exception("Encryption failed: ${result.exceptionOrNull()?.message}")
                    } else {
                        ts.copyTo(tf, true)
                    }
                } else {
                    throw Exception("Database file not found")
                }
            }
            
            val totalBytes = tf.length()
            withContext(Dispatchers.Main) {
                syncTotalSize = totalBytes
                syncMessage = "Uploading"
                syncProgress = 0.3f
            }

            val fn = if (me) "expenses_database_sync.xpt" else "expenses_database_sync.db"
            val cleanWebdav = if (webdavUrl.startsWith("http://", ignoreCase = true)) "https://" + webdavUrl.substring(7) else webdavUrl
            val baseUrl = cleanWebdav.toHttpUrlOrNull() ?: throw Exception("Invalid URL")
            val fullUrl = baseUrl.newBuilder().addPathSegment(fn).build().toString()
            val tmpUrl = "$fullUrl.tmp"

            val client = com.openapps.fintrack.data.DohNetworkClient.createClientBuilder()
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

                    val request = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                        .header("User-Agent", "FinTrack-Android")
                        .put(tf.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                            throw Exception("Upload failed: ${response.code}")
                        }
                    }

                    val moveRequest = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                        .header("User-Agent", "FinTrack-Android")
                        .header("Destination", fullUrl)
                        .header("Overwrite", "T")
                        .method("MOVE", null)
                        .build()

                    client.newCall(moveRequest).execute().use { response ->
                        if (response.isSuccessful || response.code == 201 || response.code == 204) {
                            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            val size = String.format("%.2f KB", tf.length() / 1024.0)
                            
                            withContext(Dispatchers.Main) {
                                lastSyncTime = time
                                syncLastStatus = "Success ($time)"
                                syncLastAttemptError = ""
                                prefs.edit()
                                    .putString("last_sync_time", time)
                                    .putString("last_sync_success_time", time)
                                    .putString("last_sync_size", size)
                                    .putString("last_sync_status", "Success ($time)")
                                    .putString("last_sync_attempt_error", "")
                                    .apply()
                                triggerRefresh()
                            }
                            return@withContext Result.success("Sync successful")
                        } else {
                            throw Exception("Finalize failed: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 3) throw e
                }
            }
            Result.failure(lastError ?: Exception("Unknown"))
        } catch (e: Exception) {
            SafeLogger.e("Sync Error", e)
            val em = e.localizedMessage ?: "Sync error"
            withContext(Dispatchers.Main) {
                syncLastStatus = "Failed"
                syncLastAttemptError = em
                prefs.edit()
                    .putString("last_sync_status", "Failed")
                    .putString("last_sync_attempt_error", em)
                    .apply()
            }
            Result.failure(e)
        } finally {
            withContext(Dispatchers.Main) { isSyncing = false }
            ts.delete()
            tf.delete()
        }
    }

    suspend fun downloadRemoteToLocal(du: Uri): Result<String> = withContext(Dispatchers.IO) {
        val td = File(getApplication<Application>().cacheDir, "remote_dl.db"); try {
            val cleanWebdav = if (webdavUrl.startsWith("http://", ignoreCase = true)) "https://" + webdavUrl.substring(7) else webdavUrl
            val fu = cleanWebdav.toHttpUrlOrNull()?.newBuilder()?.addPathSegment(if (encryptRemoteEnabled || secureModeEnabled) "expenses_database_sync.xpt" else "expenses_database_sync.db")?.build()?.toString() ?: return@withContext Result.failure(Exception("URL"))
            val req = Request.Builder().url(fu).header("Authorization", Credentials.basic(webdavUsername, webdavPassword)).get().build()
            com.openapps.fintrack.data.DohNetworkClient.createClientBuilder().build().newCall(req).execute().use { if(it.isSuccessful) { it.body?.let{ b -> FileOutputStream(td).use{ o -> b.source().use{ s -> o.write(s.readByteArray()) } } }; getApplication<Application>().contentResolver.openOutputStream(du)?.use { o -> FileInputStream(td).use { i -> i.copyTo(o) } }; Result.success("OK") } else Result.failure(Exception(it.message)) }
        } catch(ex: Exception) { Result.failure(ex) } finally { td.delete() }
    }

    suspend fun downloadRemoteFileToTempCache(): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(getApplication<Application>().cacheDir, "webdav_download_temp_${System.currentTimeMillis()}.tmp")
        try {
            val cleanWebdav = if (webdavUrl.startsWith("http://", ignoreCase = true)) "https://" + webdavUrl.substring(7) else webdavUrl
            val fileName = if (encryptRemoteEnabled || secureModeEnabled) "expenses_database_sync.xpt" else "expenses_database_sync.db"
            val fullUrl = cleanWebdav.toHttpUrlOrNull()?.newBuilder()?.addPathSegment(fileName)?.build()?.toString()
                ?: return@withContext Result.failure(Exception("Invalid WebDAV URL"))
            
            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                .header("User-Agent", "FinTrack-Android-Sync")
                .get()
                .build()

            val client = com.openapps.fintrack.data.DohNetworkClient.createClientBuilder().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                    FileOutputStream(tempFile).use { output ->
                        body.source().use { source ->
                            output.write(source.readByteArray())
                        }
                    }
                    Result.success(tempFile)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    fun forceSync() {
        viewModelScope.launch { syncNow() }
    }

    suspend fun testWebdavConnection(): Result<String> = withContext(Dispatchers.IO) {
        try { 
            isTestingConnection = true
            val cleanWebdav = if (webdavUrl.startsWith("http://", ignoreCase = true)) "https://" + webdavUrl.substring(7) else webdavUrl
            val testUrl = if (cleanWebdav.endsWith("/")) cleanWebdav else "$cleanWebdav/"
            val baseUrl = testUrl.toHttpUrlOrNull() ?: throw Exception("Invalid URL")
            
            val req = Request.Builder()
                .url(baseUrl)
                .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                .header("User-Agent", "FinTrack-Android-Sync")
                .method("PROPFIND", null)
                .header("Depth", "0") 
                .build()
            
            val client = com.openapps.fintrack.data.DohNetworkClient.createClientBuilder()
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
    @OptIn(ExperimentalCoroutinesApi::class)
    val allTransactions: Flow<List<TransactionWithDetails>> = _refreshTrigger.flatMapLatest { getTransactionsUseCase() }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTransactionHeaderWithLines(headerId: Int): Flow<TransactionWithLinesAndDetails?> = _refreshTrigger.flatMapLatest {
        repository.getTransactionsDetailed().map { list ->
            list.find { it.header.id == headerId }
        }
    }

    fun getEnabledAccounts() = _refreshTrigger.flatMapLatest { repository.getEnabledAccounts() }
    fun getAllAccounts() = _refreshTrigger.flatMapLatest { repository.getAllAccounts() }
    fun getEnabledCategories() = _refreshTrigger.flatMapLatest { repository.getEnabledCategories() }
    fun getAllCategories() = _refreshTrigger.flatMapLatest { repository.getAllCategories() }
    fun getEnabledCategoriesByType(t: String) = _refreshTrigger.flatMapLatest { repository.getEnabledCategoriesByType(t) }
    fun getAllTags() = _refreshTrigger.flatMapLatest { repository.getAllTags() }
    fun getEnabledTags() = _refreshTrigger.flatMapLatest { repository.getEnabledTags() }
    fun getEnabledParties() = _refreshTrigger.flatMapLatest { repository.getEnabledParties() }
    fun getAllParties() = _refreshTrigger.flatMapLatest { repository.getAllParties() }
    fun getPartyBalances(d: String) = repository.getPartyBalances(d)
    fun getAllMajorHeads() = _refreshTrigger.flatMapLatest { repository.getAllMajorHeads() }
    fun getAllMinorHeads() = _refreshTrigger.flatMapLatest { repository.getAllMinorHeads() }
    fun getMinorHeadsByMajor(id: Int) = _refreshTrigger.flatMapLatest { repository.getMinorHeadsByMajor(id) }
    fun getMajorHeadBalances(d: String) = _refreshTrigger.flatMapLatest { repository.getMajorHeadBalances(d) }
    fun getMinorHeadBalances(d: String) = _refreshTrigger.flatMapLatest { repository.getMinorHeadBalances(d) }
    fun getAllBudgetsDetailed() = _refreshTrigger.flatMapLatest { repository.getBudgetsWithRelations() }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val templatesFlow: Flow<List<TemplateLegacy>> = _refreshTrigger.flatMapLatest { 
        repository.getTemplatesWithLines().map { list ->
            list.map { t ->
                val lines = t.lines
                val first = lines.firstOrNull()
                val isMulti = lines.size > 1 || (t.header.type != "transfer" && first?.accountId == null && first?.categoryId != null)
                val multiStr = if (isMulti) {
                    lines.joinToString("|") { "${it.categoryId}:${it.amount}:${it.note ?: ""}" }
                } else null

                TemplateLegacy(
                    id = t.header.id,
                    name = t.header.name,
                    type = t.header.type,
                    accountId = first?.accountId,
                    toAccountId = first?.toAccountId,
                    categoryId = first?.categoryId,
                    amount = first?.amount,
                    amountMinorUnits = first?.amountMinorUnits,
                    note = t.header.note ?: first?.note,
                    tags = t.tags.joinToString(",") { it.id.toString() },
                    multiEntries = multiStr,
                    subName = t.header.subName,
                    subFrequency = t.header.subFrequency
                )
            }
        }
    }
    fun getAllTemplates() = templatesFlow
    fun getAllNotes() = _refreshTrigger.flatMapLatest { repository.getAllNotes() }
    fun getNotesByNotebook(id: Int) = _refreshTrigger.flatMapLatest { repository.getNotesByNotebook(id) }
    fun searchNotes(q: String) = repository.searchNotes(q)
    fun getAllNotebooks() = _refreshTrigger.flatMapLatest { repository.getAllNotebooks() }
    val activeLoans = _refreshTrigger.flatMapLatest { repository.getAllActiveLoans() }
    val allLoans = _refreshTrigger.flatMapLatest { repository.getAllLoans() }
    fun getRepaymentsForLoan(id: Long) = repository.getRepaymentsForLoan(id)
    fun getCreditCardAccounts() = combine(repository.getEnabledAccounts(), repository.getMinorHeadsByMajor(8)){ a, m -> val i = m.map{it.id}.toSet(); a.filter{ it.minorHeadId in i } }
    fun getFixedDeposits(): Flow<List<FdDashboardItem>> = _refreshTrigger.flatMapLatest { repository.getFixedDeposits() }
    fun getAllActiveFdsForAccount(accountId: Int): Flow<List<FdDashboardItem>> = _refreshTrigger.flatMapLatest { repository.getAllActiveFdsForAccount(accountId) }
    fun getAllSubscriptionStatuses() = _refreshTrigger.flatMapLatest { repository.getAllSubscriptionStatuses() }
    fun getAllSubscriptionsMaster() = _refreshTrigger.flatMapLatest { repository.getAllSubscriptionsMaster() }
    fun getExchangeRates(): Flow<List<ExchangeRate>> = repository.getAllExchangeRates()
    fun getAllRules() = _refreshTrigger.flatMapLatest { repository.getAllRules() }
    fun getInvoicesForPartyFlow(partyId: Int, asOfDate: String) = _refreshTrigger.flatMapLatest { repository.getInvoicesForParty(partyId, asOfDate) }
    fun getInvoicesForParties(partyIds: List<Int>, asOfDate: String) = _refreshTrigger.flatMapLatest {
        if (partyIds.isEmpty()) return@flatMapLatest flowOf(emptyList<TransactionWithInvoiceDetails>())
        val flows = partyIds.map { repository.getInvoicesForParty(it, asOfDate) }
        combine(flows) { arrays -> 
            arrays.flatMap { it.toList() }
                .sortedWith(compareBy({ it.detail.transaction.date }, { it.detail.transaction.time }))
        }
    }
    suspend fun getLastTransactionForSubscription(name: String) = repository.getAllTransactionsWithDetails().first().find { it.transaction.subName == name }

    // Actions
    fun saveAccount(name: String, openingBalance: Double, description: String?, isEnabled: Boolean, minorHeadId: Int?, creditLimit: Double?, billingCycleStart: String?, billingCycleEnd: String?, paymentDueDate: String?, icon: String?, isEmergencyFund: Boolean, defaultDueDays: Int? = null, last4Digits: String? = null, ifscCode: String? = null, branchName: String? = null, websiteUrl: String? = null, contactPerson: String? = null, minimumBalance: Double? = null, maturityDate: String? = null, bankName: String? = null) {
        val openingBalanceMinorUnits = Money.fromDecimal(openingBalance).minorUnits
        val creditLimitMinorUnits = creditLimit?.let { Money.fromDecimal(it).minorUnits }
        val minimumBalanceMinorUnits = minimumBalance?.let { Money.fromDecimal(it).minorUnits }
        viewModelScope.launch { 
            repository.upsertAccount(
                editingAccount?.copy(
                    name = name, openingBalance = openingBalance, openingBalanceMinorUnits = openingBalanceMinorUnits, 
                    description = description, isEnabled = isEnabled, minorHeadId = minorHeadId, 
                    creditLimit = creditLimit, creditLimitMinorUnits = creditLimitMinorUnits, 
                    billingCycleStart = billingCycleStart, billingCycleEnd = billingCycleEnd, paymentDueDate = paymentDueDate, 
                    icon = icon, isEmergencyFund = isEmergencyFund, defaultDueDays = defaultDueDays,
                    last4Digits = last4Digits?.ifBlank { null }, ifscCode = ifscCode?.ifBlank { null }, 
                    branchName = branchName?.ifBlank { null }, websiteUrl = websiteUrl?.ifBlank { null }, 
                    contactPerson = contactPerson?.ifBlank { null }, minimumBalance = minimumBalance, minimumBalanceMinorUnits = minimumBalanceMinorUnits,
                    maturityDate = maturityDate?.ifBlank { null }, bankName = bankName?.ifBlank { null }
                ) ?: Account(
                    name = name, type = "asset", openingBalance = openingBalance, openingBalanceMinorUnits = openingBalanceMinorUnits, 
                    description = description, isEnabled = isEnabled, minorHeadId = minorHeadId, 
                    creditLimit = creditLimit, creditLimitMinorUnits = creditLimitMinorUnits, 
                    billingCycleStart = billingCycleStart, billingCycleEnd = billingCycleEnd, paymentDueDate = paymentDueDate, 
                    icon = icon, isEmergencyFund = isEmergencyFund, defaultDueDays = defaultDueDays,
                    last4Digits = last4Digits?.ifBlank { null }, ifscCode = ifscCode?.ifBlank { null }, 
                    branchName = branchName?.ifBlank { null }, websiteUrl = websiteUrl?.ifBlank { null }, 
                    contactPerson = contactPerson?.ifBlank { null }, minimumBalance = minimumBalance, minimumBalanceMinorUnits = minimumBalanceMinorUnits,
                    maturityDate = maturityDate?.ifBlank { null }, bankName = bankName?.ifBlank { null }
                )
            )
            editingAccount = null
            triggerRefresh() 
        }
    }
    fun saveCategory(name: String, type: String, description: String?, isEnabled: Boolean, icon: String?) {
        viewModelScope.launch { repository.upsertCategory(editingCategory?.copy(name=name, type=type, description=description, isEnabled=isEnabled, icon=icon) ?: Category(name=name, type=type, description=description, isEnabled=isEnabled, icon=icon)); editingCategory=null; triggerRefresh() }
    }
    fun saveTag(name: String, isEnabled: Boolean, trackingType: String, targetNumber: Double?) {
        val targetNumberMinorUnits = targetNumber?.let { Money.fromDecimal(it).minorUnits }
        viewModelScope.launch { repository.upsertTag(editingTag?.copy(name=name, isEnabled=isEnabled, trackingType=trackingType, targetNumber=targetNumber, targetNumberMinorUnits=targetNumberMinorUnits) ?: Tag(name=name, isEnabled=isEnabled, trackingType=trackingType, targetNumber=targetNumber, targetNumberMinorUnits=targetNumberMinorUnits)); editingTag=null; triggerRefresh() }
    }
    fun saveParty(name: String, openingBalance: Double, isEnabled: Boolean) {
        val openingBalanceMinorUnits = Money.fromDecimal(openingBalance).minorUnits
        viewModelScope.launch { repository.upsertParty(editingParty?.copy(name=name, openingBalance=openingBalance, openingBalanceMinorUnits=openingBalanceMinorUnits, isEnabled=isEnabled) ?: Party(name=name, openingBalance=openingBalance, openingBalanceMinorUnits=openingBalanceMinorUnits, isEnabled=isEnabled)); editingParty=null; triggerRefresh() }
    }
    fun saveMajorHead(name: String, isEnabled: Boolean) {
        viewModelScope.launch { repository.upsertMajorHead(editingMajorHead?.copy(name=name, isEnabled=isEnabled) ?: MajorHead(name=name, isEnabled=isEnabled)); editingMajorHead=null; triggerRefresh() }
    }
    fun saveMinorHead(name: String, majorHeadId: Int, isEnabled: Boolean) {
        viewModelScope.launch { repository.upsertMinorHead(editingMinorHead?.copy(name=name, majorHeadId=majorHeadId, isEnabled=isEnabled) ?: MinorHead(name=name, majorHeadId=majorHeadId, isEnabled=isEnabled)); editingMinorHead=null; triggerRefresh() }
    }
    fun saveBudgetRaw(name: String?, categoryIds: String, amount: Double, duration: String, note: String?, higherIsBetter: Boolean, accountIds: String?, rolloverEnabled: Boolean = false) {
        val amountMinorUnits = Money.fromDecimal(amount).minorUnits
        viewModelScope.launch { 
            val budgetId = repository.upsertBudget(editingBudgetRaw?.budget?.copy(name=name, amount=amount, amountMinorUnits=amountMinorUnits, duration=duration, note=note, higherIsBetter=higherIsBetter, rolloverEnabled=rolloverEnabled) ?: Budget(name=name, amount=amount, amountMinorUnits=amountMinorUnits, duration=duration, note=note, higherIsBetter=higherIsBetter, rolloverEnabled=rolloverEnabled)).toInt()
            
            repository.deleteBudgetCategories(budgetId)
            categoryIds.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { catId ->
                repository.insertBudgetCategory(BudgetCategory(budgetId, catId))
            }
            
            repository.deleteBudgetAccounts(budgetId)
            if (!accountIds.isNullOrBlank()) {
                accountIds.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { accId ->
                    repository.insertBudgetAccount(BudgetAccount(budgetId, accId))
                }
            }
            
            editingBudgetRaw=null; triggerRefresh() 
        }
    }
    suspend fun saveTemplate(t: TemplateLegacy): Boolean { 
        val header = TemplateHeader(
            id = t.id,
            name = t.name,
            type = t.type,
            note = t.note,
            subName = t.subName,
            subFrequency = t.subFrequency
        )
        val headerId = repository.insertTemplateHeader(header).toInt()
        
        repository.deleteTemplateLines(headerId)
        repository.deleteTemplateTags(headerId)
        
        val lines = mutableListOf<TemplateLine>()
        if (!t.multiEntries.isNullOrBlank()) {
            t.multiEntries.split("|").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size >= 2) {
                    val catId = parts[0].toIntOrNull()
                    val amt = parts[1].toDoubleOrNull() ?: 0.0
                    val note = if (parts.size > 2) parts[2] else null
                    lines.add(TemplateLine(id = 0, headerId = headerId, accountId = null, toAccountId = null, categoryId = catId, amount = amt, amountMinorUnits = (amt * 100).toLong(), note = note))
                }
            }
        } else {
            lines.add(TemplateLine(id = 0, headerId = headerId, accountId = t.accountId, toAccountId = t.toAccountId, categoryId = t.categoryId, amount = t.amount, amountMinorUnits = t.amountMinorUnits, note = t.note))
        }
        repository.insertTemplateLines(lines)
        
        if (!t.tags.isNullOrBlank()) {
            val tagIds = t.tags.split(",").mapNotNull { it.trim().toIntOrNull() }
            repository.insertTemplateTags(tagIds.map { TemplateTag(headerId, it) })
        }
        return true 
    }
    fun saveNote(title: String, content: String, tags: String?, type: String = "text", color: Int? = null) {
        viewModelScope.launch { 
            val noteToSave = editingNote?.copy(
                title = title, 
                content = content, 
                tags = tags, 
                type = type, 
                notebookId = selectedNotebookId,
                editedAt = System.currentTimeMillis(),
                color = color ?: editingNote?.color
            ) ?: Note(
                title = title, 
                content = content, 
                tags = tags, 
                type = type, 
                notebookId = selectedNotebookId,
                color = color
            )
            repository.upsertNote(noteToSave)
            editingNote = null
            triggerRefresh() 
        }
    }
    fun togglePinNote(note: Note) {
        viewModelScope.launch {
            repository.upsertNote(note.copy(isPinned = !note.isPinned, editedAt = System.currentTimeMillis()))
            triggerRefresh()
        }
    }
    fun updateNoteColor(note: Note, color: Int?) {
        viewModelScope.launch {
            repository.upsertNote(note.copy(color = color, editedAt = System.currentTimeMillis()))
            triggerRefresh()
        }
    }
    fun moveNote(note: Note, targetNotebookId: Int?) {
        viewModelScope.launch {
            repository.upsertNote(note.copy(notebookId = targetNotebookId, editedAt = System.currentTimeMillis()))
            triggerRefresh()
        }
    }
    fun copyNote(note: Note, targetNotebookId: Int?) {
        viewModelScope.launch {
            repository.upsertNote(note.copy(id = 0, notebookId = targetNotebookId, createdAt = System.currentTimeMillis(), editedAt = System.currentTimeMillis()))
            triggerRefresh()
        }
    }
    fun saveNotebook(n: Notebook) { viewModelScope.launch { repository.upsertNotebook(n); triggerRefresh() } }
    fun saveNotebook(name: String) { viewModelScope.launch { repository.upsertNotebook(Notebook(name = name)); triggerRefresh() } }
    fun deleteNotebook(n: Notebook) { viewModelScope.launch { repository.deleteNotesByNotebook(n.id); repository.deleteNotebook(n); if(selectedNotebookId == n.id) selectedNotebookId = null; triggerRefresh() } }
    fun saveSubscriptionMaster(name: String, frequency: Int, note: String?, isTransfer: Boolean) { viewModelScope.launch { repository.upsertSubscriptionMaster(Subscription(name=name, frequency=frequency, note=note, isTransfer=isTransfer)); triggerRefresh() } }
    fun saveRule(rule: Rule) { 
        viewModelScope.launch { 
            repository.upsertRule(rule)
            
            if (secureModeEnabled && prefs.getLong("last_secure_close_time", 0L) == 0L) {
                 prefs.edit().putLong("last_secure_close_time", System.currentTimeMillis()).apply()
            }
            triggerRefresh() 
        } 
    }
    fun deleteAccount(a: Account) { viewModelScope.launch { repository.deleteAccount(a); triggerRefresh() } }
    fun deleteCategory(c: Category) { viewModelScope.launch { repository.deleteCategory(c); triggerRefresh() } }
    fun deleteTag(t: Tag) { viewModelScope.launch { repository.deleteTag(t); triggerRefresh() } }
    fun deleteParty(p: Party) { viewModelScope.launch { repository.deleteParty(p); triggerRefresh() } }
    fun deleteMajorHeadAndRemap(h: MajorHead) { viewModelScope.launch { repository.deleteMajorHead(h); triggerRefresh() } }
    fun deleteMinorHead(h: MinorHead) { viewModelScope.launch { repository.deleteMinorHead(h); triggerRefresh() } }
    fun deleteBudget(b: BudgetWithRelations) { viewModelScope.launch { repository.deleteBudget(b.budget); triggerRefresh() } }
    fun deleteTemplate(t: TemplateLegacy) { viewModelScope.launch { repository.deleteTemplateHeader(t.id); triggerRefresh() } }
    fun deleteNote(n: Note) { viewModelScope.launch { repository.deleteNote(n); triggerRefresh() } }
    fun deleteLoan(l: Loan) { viewModelScope.launch { repository.deleteLoan(l); triggerRefresh() } }
    fun deleteRule(r: Rule) { viewModelScope.launch { repository.deleteRule(r); triggerRefresh() } }
    fun toggleCategoryEnabled(c: Category) { viewModelScope.launch { repository.updateCategory(c.copy(isEnabled = !c.isEnabled)); triggerRefresh() } }
    fun toggleAccountEnabled(a: Account) { viewModelScope.launch { repository.updateAccount(a.copy(isEnabled = !a.isEnabled)); triggerRefresh() } }
    fun toggleTagEnabled(t: Tag) { viewModelScope.launch { repository.updateTag(t.copy(isEnabled = !t.isEnabled)); triggerRefresh() } }
    fun toggleMajorHeadEnabled(h: MajorHead) { viewModelScope.launch { repository.updateMajorHead(h.copy(isEnabled = !h.isEnabled)); triggerRefresh() } }
    fun toggleMinorHeadEnabled(h: MinorHead) { viewModelScope.launch { repository.updateMinorHead(h.copy(isEnabled = !h.isEnabled)); triggerRefresh() } }
    fun togglePartyEnabled(p: Party) { viewModelScope.launch { repository.updateParty(p.copy(isEnabled = !p.isEnabled)); triggerRefresh() } }
    fun toggleLoanAutoRecord(l: Loan, e: Boolean) { viewModelScope.launch { repository.upsertLoan(l.copy(isAutoRecordEnabled = e)); triggerRefresh() } }

    // Goals
    val allGoals: Flow<List<Goal>> = repository.getAllGoals()
    val allAllocations: Flow<List<GoalAccountAllocation>> = repository.getAllAllocations()
    val allGoalRules: Flow<List<GoalRule>> = repository.getAllGoalRules()

    fun saveGoal(id: Int, name: String, targetAmount: Double, targetDate: Long?, icon: String, color: Int) {
        viewModelScope.launch {
            repository.upsertGoal(Goal(id = id, name = name, targetAmount = targetAmount, targetDate = targetDate, icon = icon, color = color))
            triggerRefresh()
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            triggerRefresh()
        }
    }

    fun allocateFundsToGoal(goalId: Int, accountId: Int, amount: Double) {
        viewModelScope.launch {
            repository.upsertAllocation(GoalAccountAllocation(goalId = goalId, accountId = accountId, allocatedAmount = amount))
            repository.insertAllocationHistory(GoalAllocationHistory(goalId = goalId, accountId = accountId, amount = amount, source = "Manual"))
            triggerRefresh()
        }
    }

    fun adjustGoalAllocation(goalId: Int, accountId: Int, deltaAmount: Double) {
        viewModelScope.launch {
            val existing = repository.getAllocation(goalId, accountId)
            val currentAmount = existing?.allocatedAmount ?: 0.0
            val newAmount = (currentAmount + deltaAmount).coerceAtLeast(0.0)
            repository.upsertAllocation(GoalAccountAllocation(goalId = goalId, accountId = accountId, allocatedAmount = newAmount))
            if (deltaAmount > 0) {
                repository.insertAllocationHistory(GoalAllocationHistory(goalId = goalId, accountId = accountId, amount = deltaAmount, source = "Manual"))
            }
            triggerRefresh()
        }
    }

    fun getAllocationHistoryForGoal(goalId: Int) = repository.getAllocationHistoryForGoal(goalId)

    fun transferGoalAllocation(goalId: Int, fromAccountId: Int, toAccountId: Int, amount: Double) {
        viewModelScope.launch {
            val existingFrom = repository.getAllocation(goalId, fromAccountId)
            val fromAmount = existingFrom?.allocatedAmount ?: 0.0
            if (fromAmount < amount || amount <= 0.0) return@launch

            val newFromAmount = fromAmount - amount
            if (newFromAmount <= 0.0) {
                repository.deleteAllocation(goalId, fromAccountId)
            } else {
                repository.upsertAllocation(GoalAccountAllocation(goalId = goalId, accountId = fromAccountId, allocatedAmount = newFromAmount))
            }

            val existingTo = repository.getAllocation(goalId, toAccountId)
            val toAmount = existingTo?.allocatedAmount ?: 0.0
            val newToAmount = toAmount + amount
            repository.upsertAllocation(GoalAccountAllocation(goalId = goalId, accountId = toAccountId, allocatedAmount = newToAmount))

            val allAccounts = repository.getAllAccounts().first()
            val fromAccName = allAccounts.find { it.id == fromAccountId }?.name ?: "Source"
            val toAccName = allAccounts.find { it.id == toAccountId }?.name ?: "Destination"

            repository.insertAllocationHistory(GoalAllocationHistory(goalId = goalId, accountId = toAccountId, amount = amount, source = "Transfer: From $fromAccName to $toAccName"))
            triggerRefresh()
        }
    }

    fun removeGoalAllocation(goalId: Int, accountId: Int) {
        viewModelScope.launch {
            repository.deleteAllocation(goalId, accountId)
            triggerRefresh()
        }
    }

    fun saveGoalRule(rule: GoalRule) {
        viewModelScope.launch {
            repository.upsertGoalRule(rule)
            triggerRefresh()
        }
    }

    fun deleteGoalRule(rule: GoalRule) {
        viewModelScope.launch {
            repository.deleteGoalRule(rule)
            triggerRefresh()
        }
    }

    fun getTransactionsForGoal(goalId: Int) = repository.getTransactionsForGoal(goalId)

    fun toggleGoalRuleEnabled(rule: GoalRule) {
        viewModelScope.launch {
            repository.upsertGoalRule(rule.copy(isEnabled = !rule.isEnabled))
            triggerRefresh()
        }
    }

    // TransactionLegacy Management
    private suspend fun executeAddTransaction(date: String, time: String, accId: Int, catId: Int?, amount: Double, note: String?, toAccId: Int?, tags: String?, type: String, pId: Int? = null, toPId: Int? = null, subN: String? = null, subF: Int? = null, amtO: Double? = null, cur: String? = null, amtB: Double? = null, updId: Int? = null, isNeg: Boolean = false, negOrig: Double? = null, merch: String? = null, isDisc: Boolean = false, invNo: String? = null, dueD: Int? = null, clearInvIds: List<Int> = emptyList(), goalId: Int? = null, fdLast4: String? = null, fdMaturityDate: String? = null, selectedFdCreationHeaderId: Int? = null) {
        val lines = listOf(
            TransactionLineData(
                accountId = accId,
                toAccountId = toAccId,
                categoryId = catId,
                amount = amount,
                amountOriginal = amtO,
                currencyCode = cur,
                amountBase = amtB,
                isNegotiated = isNeg,
                negotiationAmountOriginal = negOrig,
                isDiscretionary = isDisc,
                note = null,
                fdLast4 = fdLast4,
                fdMaturityDate = fdMaturityDate
            )
        )
        val tagIds = tags?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

        addTransactionUseCase(
            date = date,
            time = time,
            note = note,
            type = type,
            lines = lines,
            tags = tagIds,
            partyId = pId,
            toPartyId = toPId,
            subName = subN,
            subFrequency = subF,
            merchantName = merch,
            invoiceNumber = invNo,
            dueDays = dueD,
            updateId = updId,
            clearInvoiceIds = clearInvIds,
            baseCurrency = baseCurrency,
            goalId = goalId,
            selectedFdCreationHeaderId = selectedFdCreationHeaderId
        )
    }
    fun addTransaction(date: String, time: String, accountId: Int, categoryId: Int?, amount: Double, note: String?, toAccountId: Int?, tags: String?, type: String, partyId: Int? = null, toPartyId: Int? = null, subName: String? = null, subFrequency: Int? = null, amountOriginal: Double? = null, currencyCode: String? = null, amountBase: Double? = null, updateId: Int? = null, isNegotiated: Boolean = false, negotiationAmountOriginal: Double? = null, merchantName: String? = null, isDiscretionary: Boolean = false, invoiceNumber: String? = null, dueDays: Int? = null, clearInvoiceIds: List<Int> = emptyList(), goalId: Int? = null, fdLast4: String? = null, fdMaturityDate: String? = null, selectedFdCreationHeaderId: Int? = null) {
        viewModelScope.launch { 
            executeAddTransaction(date, time, accountId, categoryId, amount, note, toAccountId, tags, type, partyId, toPartyId, subName, subFrequency, amountOriginal, currencyCode, amountBase, updateId, isNegotiated, negotiationAmountOriginal, merchantName, isDiscretionary, invoiceNumber, dueDays, clearInvoiceIds, goalId, fdLast4, fdMaturityDate, selectedFdCreationHeaderId)
            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }

    fun deleteTransactions(ids: List<Int>) {
        viewModelScope.launch {
            ids.toSet().forEach { 
                repository.deleteClearancesByTransfer(it)
                repository.deleteTransactionHeader(it)
            }
            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }

    fun toggleReconciliation(lineId: Int, reconciled: Boolean) {
        viewModelScope.launch {
            repository.updateLineReconciliation(lineId, reconciled)
            triggerRefresh()
        }
    }

    fun updateTransactionStatus(headerId: Int, status: String) {
        viewModelScope.launch {
            val reconciled = status.equals("VERIFIED", ignoreCase = true)
            repository.updateTransactionStatus(headerId, reconciled, status)
            
            selectedTransactionDetail?.let { detail ->
                if (detail.transaction.id == headerId) {
                    selectedTransactionDetail = detail.copy(
                        transaction = detail.transaction.copy(
                            isReconciled = reconciled,
                            reconciliationStatus = status
                        )
                    )
                }
            }
            triggerRefresh()
        }
    }

    fun updateBulkTransactionStatus(headerIds: List<Int>, status: String) {
        viewModelScope.launch {
            val reconciled = status.equals("VERIFIED", ignoreCase = true)
            headerIds.toSet().forEach { headerId ->
                repository.updateTransactionStatus(headerId, reconciled, status)
            }
            selectedTransactionDetail?.let { detail ->
                if (detail.transaction.id in headerIds) {
                    selectedTransactionDetail = detail.copy(
                        transaction = detail.transaction.copy(
                            isReconciled = reconciled,
                            reconciliationStatus = status
                        )
                    )
                }
            }
            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }

    fun addMultiEntryTransactionExtended(date: String, time: String, accountId: Int, entries: List<MultiEntryRowData>, tags: String?, type: String, partyId: Int?, subName: String?, subFrequency: Int?, updateId: Int? = null) {
        viewModelScope.launch {
            val lines = entries.map { e -> 
                TransactionLineData(
                    accountId = e.accountId ?: accountId,
                    categoryId = e.categoryId, 
                    amount = e.amount, 
                    note = e.note, 
                    amountOriginal = e.amount, 
                    currencyCode = e.currencyCode, 
                    amountBase = e.amount,
                    tags = e.tags
                )
            }
            val allTagIds = mutableSetOf<Int>()
            tags?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.let { allTagIds.addAll(it) }
            entries.forEach { e ->
                e.tags?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.let { allTagIds.addAll(it) }
            }

            addTransactionUseCase(
                date = date,
                time = time,
                note = null,
                type = if(type=="income") "multi_income" else "multi_expense",
                lines = lines,
                tags = allTagIds.toList(),
                partyId = partyId,
                subName = subName,
                subFrequency = subFrequency,
                updateId = updateId,
                baseCurrency = baseCurrency
            )

            triggerRefresh()
            triggerSyncOnNewRecord()
        }
    }

    // Loan Management
    fun saveLoanWithAccount(loan: Loan, minorHeadId: Int, accountName: String, partyName: String?, isExisting: Boolean, disbursementAccountId: Int?, loanIssueDate: String?, issuedAmount: Double, isCreateAccount: Boolean, isUpdateBank: Boolean) {
        viewModelScope.launch {
            try {
                val allCats = repository.getAllCategories().first()
                val intExpCat = allCats.find { it.name.equals("Interest expense - Loans", ignoreCase = true) }
                val intIncCat = allCats.find { it.name.equals("Interest Income - Loans", ignoreCase = true) }
                val intMiscCat = allCats.find { it.name.equals("Interest Exp Misc", ignoreCase = true) }
                
                var accId = disbursementAccountId ?: 0
                if (isCreateAccount) {
                    val opBalance = if (!isUpdateBank) {
                        if (loan.loanType == "BORROWING") -loan.principalAmount else loan.principalAmount
                    } else 0.0
                    
                    val a = Account(
                        name = accountName, 
                        type = if (loan.loanType == "BORROWING") "liability" else "asset", 
                        openingBalance = opBalance,
                        openingBalanceMinorUnits = Money.fromDecimal(opBalance).minorUnits,
                        minorHeadId = minorHeadId, 
                        isEnabled = true
                    )
                    accId = repository.upsertAccount(a).toInt()
                }
                
                val multiplier = getMultiplier(loan.frequency)
                val firstRepay = Instant.ofEpochMilli(loan.firstRepaymentDate).atZone(ZoneId.of("UTC")).toLocalDate()
                val schedule = LoanCalculator.generateSchedule(loan.principalAmount, loan.interestRateAnnual/multiplier, loan.periodsTotal, loan.installmentAmount, loan.gapInterest, firstRepay, loan.frequency)
                
                val actualPeriodsPassedUntilToday = LoanCalculator.countPassedPeriods(firstRepay, LocalDate.now(ZoneId.of("UTC")), loan.frequency).coerceAtMost(loan.periodsTotal)
                
                val balance = if(isExisting) LoanCalculator.calculateOutstandingBalance(loan.principalAmount, loan.interestRateAnnual/multiplier, loan.periodsTotal, loan.installmentAmount, loan.gapInterest, firstRepay, loan.frequency, LocalDate.now(ZoneId.of("UTC"))) else loan.principalAmount
                
                val passedRows = if(isExisting) schedule.filter{it.period <= actualPeriodsPassedUntilToday} else emptyList()
                
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

                val principalAmountMinorUnits = Money.fromDecimal(loan.principalAmount).minorUnits
                val installmentAmountMinorUnits = Money.fromDecimal(loan.installmentAmount).minorUnits
                val gapInterestMinorUnits = Money.fromDecimal(loan.gapInterest).minorUnits
                val totalInterestPaidMinorUnits = Money.fromDecimal(passedRows.sumOf { it.interestPortion }).minorUnits
                val totalPrincipalRepaidMinorUnits = Money.fromDecimal(passedRows.sumOf { row -> schedule.find { it.period == row.period }?.principalPortion ?: 0.0 }).minorUnits
                val outstandingBalanceMinorUnits = Money.fromDecimal(balance).minorUnits

                repository.upsertLoan(loan.copy(
                    accountId = accId, 
                    isUpdateBank = isUpdateBank,
                    outstandingBalance = balance, 
                    outstandingBalanceMinorUnits = outstandingBalanceMinorUnits,
                    totalInterestPaid = passedRows.sumOf { it.interestPortion },
                    totalInterestPaidMinorUnits = totalInterestPaidMinorUnits,
                    totalPrincipalRepaid = passedRows.sumOf { row -> schedule.find { it.period == row.period }?.principalPortion ?: 0.0 },
                    totalPrincipalRepaidMinorUnits = totalPrincipalRepaidMinorUnits,
                    periodsPassed = actualPeriodsPassedUntilToday,
                    nextDueDate = nextDueCalc.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                    principalAmountMinorUnits = principalAmountMinorUnits,
                    installmentAmountMinorUnits = installmentAmountMinorUnits,
                    gapInterestMinorUnits = gapInterestMinorUnits,
                    actualRepaymentAmountMinorUnits = Money.fromDecimal(loan.actualRepaymentAmount).minorUnits
                ))
                
                if (isExisting && isCreateAccount) {
                    val timeStr = "12:00"
                    
                    
                    val effectiveSourceId = if (isUpdateBank && disbursementAccountId != null) {
                        disbursementAccountId
                    } else {
                        getSuspenseAccountId()
                    }

                    if (isUpdateBank && disbursementAccountId != null) {
                         executeAddTransaction(
                            loanIssueDate ?: LocalDate.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE), 
                            timeStr, 
                            if(loan.loanType=="BORROWING") accId else disbursementAccountId, 
                            null, 
                            issuedAmount, 
                            "Loan Disbursement: ${loan.name}", 
                            if(loan.loanType=="BORROWING") disbursementAccountId else accId, 
                            null, 
                            "transfer"
                        )
                    }
                    
                    // 2. Passed Repayments
                    passedRows.forEach { row ->
                        val date = row.dueDate.format(DateTimeFormatter.ISO_DATE)
                        val actualTotal = if (loan.isActualEmiDifferent) loan.actualRepaymentAmount else loan.installmentAmount
                        val diff = actualTotal - loan.installmentAmount
                        val suffix = if (isUpdateBank) "" else " (Historical)"

                        if(loan.loanType=="BORROWING") {
                            executeAddTransaction(date, timeStr, effectiveSourceId, null, loan.installmentAmount, "Loan Repayment$suffix: ${loan.name}", accId, loan.tags, "transfer")
                            
                            if (intExpCat != null) executeAddTransaction(date, timeStr, accId, intExpCat.id, row.interestPortion, "Loan Interest Accrual$suffix: ${loan.name}", null, loan.tags, "expense")
                            
                            if (diff != 0.0 && intMiscCat != null) {
                                executeAddTransaction(date, timeStr, effectiveSourceId, intMiscCat.id, diff, "Loan Repayment Adjustment$suffix: ${loan.name}", null, loan.tags, "expense")
                            }
                        } else {
                            executeAddTransaction(date, timeStr, accId, null, loan.installmentAmount, "Loan Recovery$suffix: ${loan.name}", effectiveSourceId, loan.tags, "transfer")
                            
                            if (intIncCat != null) executeAddTransaction(date, timeStr, accId, intIncCat.id, row.interestPortion, "Loan Interest Earned$suffix: ${loan.name}", null, loan.tags, "income")
                            
                            if (diff != 0.0 && intMiscCat != null) {
                                executeAddTransaction(date, timeStr, effectiveSourceId, intMiscCat.id, diff, "Loan Recovery Adjustment$suffix: ${loan.name}", null, loan.tags, "income")
                            }
                        }
                    }
                }
                repository.checkpoint(); triggerRefresh()
            } catch (e: Exception) { Log.e("ExpenseViewModel", "Failed to save loan history", e) }
        }
    }

    var crossReferenceDetails by mutableStateOf<List<InvoiceCrossReference>>(emptyList())
    var isLoadingCrossReference by mutableStateOf(false)

    fun loadCrossReference(transactionId: Int) {
        viewModelScope.launch {
            isLoadingCrossReference = true
            try {
                val results = mutableListOf<InvoiceCrossReference>()
                
                val clearedInvoices = repository.getInvoicesClearedByTransfer(transactionId)
                if (clearedInvoices.isNotEmpty()) {
                    for (clearing in clearedInvoices) {
                        val invoiceId = clearing.clearance.invoiceTransactionId
                        try {
                            results.add(fetchSingleInvoiceReference(invoiceId))
                        } catch (e: Exception) {
                            Log.e("ExpenseViewModel", "Error fetching invoice ref for id $invoiceId", e)
                        }
                    }
                } else {
            
                    val clearings = repository.getClearingsForInvoice(transactionId)
                    if (clearings.isNotEmpty()) {
                        try {
                            results.add(fetchSingleInvoiceReference(transactionId))
                        } catch (e: Exception) {
                            Log.e("ExpenseViewModel", "Error fetching invoice ref for id $transactionId", e)
                        }
                    } else {
                    
                        val txn = repository.getTransactionWithDetails(transactionId)
                        if (txn?.transaction?.invoiceNumber != null) {
                            try {
                                results.add(fetchSingleInvoiceReference(transactionId))
                            } catch (e: Exception) {
                                Log.e("ExpenseViewModel", "Error fetching invoice ref for id $transactionId", e)
                            }
                        }
                    }
                }
                crossReferenceDetails = results
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error loading cross reference", e)
                crossReferenceDetails = emptyList()
            } finally {
                isLoadingCrossReference = false
            }
        }
    }

    private suspend fun fetchSingleInvoiceReference(invoiceId: Int): InvoiceCrossReference {
        val invDetail = repository.getTransactionWithDetails(invoiceId) ?: throw Exception("Invoice not found")
        
        val clearings = repository.getClearingsForInvoice(invoiceId)
        val transfers = clearings.map { c ->
            ClearingTransferDetails(
                transferTxnId = c.clearance.transferTransactionId,
                transferTxnNumber = c.otherTxnNumber,
                transferDate = c.otherDate,
                amountApplied = c.clearance.amountClearedMinorUnits ?: (c.clearance.amountCleared * 100).toLong(),
                otherAccountName = if (c.accountName == invDetail.accountName) c.toAccountName else c.accountName
            )
        }
        
        val totalCleared = transfers.sumOf { it.amountApplied }
        val invoiceAmount = invDetail.transaction.amountMinorUnits ?: (invDetail.transaction.amount * 100).toLong()
        
        return InvoiceCrossReference(
            invoiceId = invoiceId,
            invoiceTxnNumber = invDetail.transaction.transactionNumber,
            invoiceDate = invDetail.transaction.date,
            invoiceAmount = invoiceAmount,
            invoiceNumber = invDetail.transaction.invoiceNumber,
            totalCleared = totalCleared,
            pendingAmount = invoiceAmount - totalCleared,
            clearingTransfers = transfers
        )
    }
    private suspend fun getSuspenseAccountId(): Int { 
        val existing = repository.getSuspenseAccountInternal()
        if (existing != null) return existing.id
        
        return repository.upsertAccount(
            Account(name = "Suspense", type = "asset", openingBalance = 0.0, isEnabled = true)
        ).toInt()
    }
    fun ensureLoanHeadsExist() {
        viewModelScope.launch {
            val majorHeads = repository.getAllMajorHeads().first()
            var loansMajorId = majorHeads.find { it.name.equals("Loans", ignoreCase = true) }?.id
            if (loansMajorId == null) loansMajorId = repository.upsertMajorHead(MajorHead(name = "Loans", isEnabled = true)).toInt()
            val loansId = loansMajorId ?: return@launch
            val minorHeads = repository.getMinorHeadsByMajor(loansId).first()
            val expectedMinors = listOf("Banks", "NBFC", "Other FI")
            expectedMinors.forEach { if (minorHeads.none { m -> m.name.equals(it, ignoreCase = true) }) repository.upsertMinorHead(MinorHead(name = it, majorHeadId = loansId, isEnabled = true)) }
            val categories = repository.getAllCategories().first()
            val expectedCats = listOf("Interest expense - Loans" to "expense", "Interest Income - Loans" to "income", "Interest Exp Misc" to "expense")
            expectedCats.forEach { (name, type) -> if (categories.none { it.name.equals(name, ignoreCase = true) && it.type == type }) repository.upsertCategory(Category(name = name, type = type, isEnabled = true)) }
        }
    }
    fun processLoanRepayment(id: Long, amount: Double) { viewModelScope.launch { val l = repository.getLoanById(id) ?: return@launch; val m = getMultiplier(l.frequency); val s = LoanCalculator.calculatePaymentSplit(l.outstandingBalance, l.interestRateAnnual/m, amount, l.gapInterest, l.periodsPassed==0); recordLoanRepayment(LoanRepayment(loanId=id, amountPaid=amount, principalPortion=s.first, interestPortion=s.second, paymentDate=System.currentTimeMillis(), isScheduled=false)) } }
    fun recordLoanRepayment(r: LoanRepayment, tId: Int? = null) { 
        viewModelScope.launch { 
            val l = repository.getLoanById(r.loanId) ?: return@launch
            repository.upsertLoan(l.copy(totalInterestPaid=l.totalInterestPaid+r.interestPortion, totalPrincipalRepaid=l.totalPrincipalRepaid+r.principalPortion, outstandingBalance=(l.outstandingBalance-r.principalPortion).coerceAtLeast(0.0), periodsPassed=l.periodsPassed+1, nextDueDate=LoanCalculator.getNextDate(l.nextDueDate, l.frequency)))
            repository.upsertLoanRepayment(r.copy(transactionId=tId))
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
            val loan = repository.getLoanById(loanId) ?: return@launch
            val dates = getPendingLoanDates(loan)
            if (dates.isEmpty()) return@launch

            val allCategories = repository.getAllCategories().first()
            val intExpCat = allCategories.find { it.name.equals("Interest expense - Loans", ignoreCase = true) }
            val intIncCat = allCategories.find { it.name.equals("Interest Income - Loans", ignoreCase = true) }
            val intMiscCat = allCategories.find { it.name.equals("Interest Exp Misc", ignoreCase = true) }
            
            val effectiveSourceId = if (loan.isUpdateBank && loan.sourceAccountId != null && loan.sourceAccountId != 0) {
                loan.sourceAccountId
            } else {
                getSuspenseAccountId()
            }

            dates.forEach { date ->
                val currentLoan = repository.getLoanById(loanId) ?: return@forEach
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
            repository.insertTransactionLegacy(TransactionLegacy(date = dateStr, time = timeStr, accountId = sourceId, toAccountId = loan.accountId, categoryId = null, amount = loan.installmentAmount, note = "Loan EMI Repayment: ${loan.name}", subName = "LOAN:${loan.id}", tags = loan.tags, amountOriginal = loan.installmentAmount, currencyCode = baseCurrency, amountBase = loan.installmentAmount))
            if (intExpCat != null) repository.insertTransactionLegacy(TransactionLegacy(date = dateStr, time = timeStr, accountId = loan.accountId, categoryId = intExpCat.id, amount = split.second, note = "Loan Interest Accrual: ${loan.name}", tags = loan.tags, amountOriginal = split.second, currencyCode = baseCurrency, amountBase = split.second))
            if (diff != 0.0 && intMiscCat != null) repository.insertTransactionLegacy(TransactionLegacy(date = dateStr, time = timeStr, accountId = sourceId, categoryId = intMiscCat.id, amount = diff, note = "Loan Repayment Adjustment: ${loan.name}", tags = loan.tags, amountOriginal = diff, currencyCode = baseCurrency, amountBase = diff))
        } else {
            repository.insertTransactionLegacy(TransactionLegacy(date = dateStr, time = timeStr, accountId = loan.accountId, toAccountId = sourceId, categoryId = null, amount = loan.installmentAmount, note = "Loan EMI Recovery: ${loan.name}", subName = "LOAN:${loan.id}", tags = loan.tags, amountOriginal = loan.installmentAmount, currencyCode = baseCurrency, amountBase = loan.installmentAmount))
            if (intIncCat != null) repository.insertTransactionLegacy(TransactionLegacy(date = dateStr, time = timeStr, accountId = loan.accountId, categoryId = intIncCat.id, amount = split.second, note = "Loan Interest Earned: ${loan.name}", tags = loan.tags, amountOriginal = split.second, currencyCode = baseCurrency, amountBase = split.second))
            if (diff != 0.0 && intMiscCat != null) repository.insertTransactionLegacy(TransactionLegacy(date = dateStr, time = timeStr, accountId = sourceId, categoryId = intMiscCat.id, amount = diff, note = "Loan Recovery Adjustment: ${loan.name}", tags = loan.tags, amountOriginal = diff, currencyCode = baseCurrency, amountBase = diff))
        }

        val updatedLoan = loan.copy(
            totalInterestPaid = loan.totalInterestPaid + split.second,
            totalPrincipalRepaid = loan.totalPrincipalRepaid + split.first,
            outstandingBalance = (loan.outstandingBalance - split.first).coerceAtLeast(0.0),
            periodsPassed = loan.periodsPassed + 1,
            nextDueDate = LoanCalculator.getNextDate(loan.nextDueDate, loan.frequency),
            isClosed = Math.abs(loan.outstandingBalance - split.first) < 1.0
        )
        repository.upsertLoan(updatedLoan)
        repository.upsertLoanRepayment(LoanRepayment(loanId = loan.id, amountPaid = actualTotal, principalPortion = split.first, interestPortion = split.second, paymentDate = dueDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), isScheduled = true))
    }

    // Alerts
    fun toggleCcPaid(id: Int, p: Boolean) { toggleCcPaidCustom(id.toString(), p) }
    fun toggleCcPaidCustom(id: String, p: Boolean) { val k = LocalDate.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM")); val n = dismissedCcAlertIds.toMutableSet(); if(p) n.add("${id}_$k") else n.remove("${id}_$k"); dismissedCcAlertIds=n; prefs.edit().putStringSet("dismissed_cc_alerts", n).apply() }
    fun dismissCcAlert(id: Int) { toggleCcPaidCustom(id.toString(), true) }
    fun toggleSubscriptionStopped(n: String, s: Boolean) { viewModelScope.launch { val current = repository.getAllSubscriptionStatuses().first().find { it.subName == n }; repository.upsertSubscriptionStatus(current?.copy(isStopped = s) ?: SubscriptionStatus(n, isStopped = s)); triggerRefresh() } }
    fun toggleSubscriptionAutoRecord(n: String, e: Boolean) { viewModelScope.launch { val current = repository.getAllSubscriptionStatuses().first().find { it.subName == n }; repository.upsertSubscriptionStatus(current?.copy(isAutoRecordEnabled = e) ?: SubscriptionStatus(n, isAutoRecordEnabled = e)); triggerRefresh() } }
    
    fun getCcAlerts(): Flow<List<CcAlert>> = combine(
        repository.getEnabledAccounts(),
        repository.getAllMinorHeads(),
        allTransactions
    ) { accounts, minorHeads, txns ->
        val today = LocalDate.now(ZoneId.of("UTC"))
        val alerts = mutableListOf<CcAlert>()
        
        accounts.filter { it.minorHeadId != null }.forEach { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            if (minor?.majorHeadId == 8) {
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
                        
                        val cardTxns = txns.filter { it.transaction.accountId == acc.id && it.transaction.date in startStr..endStr }
                        val totalExpense = cardTxns.filter { it.categoryType != "income" }.sumOf { it.transaction.amount }
                        val totalIncome = cardTxns.filter { it.categoryType == "income" }.sumOf { it.transaction.amount }
                        val amount = totalExpense - totalIncome

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
        repository.getAllSubscriptionStatuses()
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

    fun getFdMaturityAlerts(): Flow<List<FdMaturityAlert>> = combine(
        repository.getEnabledAccounts(),
        repository.getAllMinorHeads(),
        repository.getAccountBalances(LocalDate.now(ZoneId.of("UTC")).toString())
    ) { accounts, minorHeads, balances ->
        val today = LocalDate.now(ZoneId.of("UTC"))
        val thirtyDaysLater = today.plusDays(30)
        val alerts = mutableListOf<FdMaturityAlert>()

        accounts.filter { !it.maturityDate.isNullOrBlank() }.forEach { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            val isFd = (minor?.name?.equals("Fixed Deposit", ignoreCase = true) == true) ||
                       (acc.name.contains("Fixed Deposit", ignoreCase = true))
            if (isFd) {
                val maturityDate = try { LocalDate.parse(acc.maturityDate) } catch (e: Exception) { null }
                if (maturityDate != null && !maturityDate.isBefore(today) && !maturityDate.isAfter(thirtyDaysLater)) {
                    val k = "FD_${acc.id}_${maturityDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}"
                    if (!dismissedCcAlertIds.contains(k)) {
                        val bal = balances.find { it.id == acc.id }?.balance?.toDouble()?.div(100.0) ?: acc.openingBalance
                        alerts.add(FdMaturityAlert(acc.id, acc.name, maturityDate, bal))
                    }
                }
            }
        }
        alerts
    }

    fun getMissingSubscriptionDates(subName: String): List<LocalDate> {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val allTxns = runBlocking { repository.getAllTransactionsWithDetails().first() }
        val subTxns = allTxns.filter { it.transaction.subName.equals(subName, ignoreCase = true) }
        if (subTxns.isEmpty()) return emptyList()

        val lastTxn = subTxns.maxByOrNull { it.transaction.date } ?: return emptyList()
        val lastDate = try { LocalDate.parse(lastTxn.transaction.date) } catch (e: Exception) { today }
        val freq = lastTxn.transaction.subFrequency ?: 1

        var nextDue = lastDate.plusMonths(freq.toLong())
        val missing = mutableListOf<LocalDate>()

        while (!nextDue.isAfter(today)) {
            missing.add(nextDue)
            nextDue = nextDue.plusMonths(freq.toLong())
        }
        return missing
    }

    fun catchupSubscriptionEntries(subName: String) {
        viewModelScope.launch {
            try {
                val missingDates = getMissingSubscriptionDates(subName)
                if (missingDates.isEmpty()) {
                    Toast.makeText(getApplication(), "Subscription '$subName' is already up to date. No missing entries to catch up.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val allTxns = repository.getAllTransactionsWithDetails().first()
                val subTxns = allTxns.filter { it.transaction.subName.equals(subName, ignoreCase = true) }
                val lastTxn = subTxns.maxByOrNull { it.transaction.date } ?: return@launch

                var count = 0
                for (nextDue in missingDates) {
                    count++
                    val dateStr = nextDue.format(DateTimeFormatter.ISO_DATE)
                    val timeStr = lastTxn.transaction.time

                    val type = if (lastTxn.transaction.categoryId == null && lastTxn.transaction.toAccountId != null) "transfer" else (lastTxn.categoryType ?: "expense")
                    val originalNote = lastTxn.transaction.note ?: subName
                    val autoNote = if (originalNote.startsWith("[Auto-Recorded]")) originalNote else "[Auto-Recorded] $originalNote"

                    addTransactionUseCase(
                        date = dateStr,
                        time = timeStr,
                        note = autoNote,
                        type = type,
                        lines = listOf(
                            TransactionLineData(
                                accountId = lastTxn.transaction.accountId,
                                toAccountId = lastTxn.transaction.toAccountId,
                                categoryId = lastTxn.transaction.categoryId,
                                amount = lastTxn.transaction.amount,
                                amountOriginal = lastTxn.transaction.amountOriginal,
                                currencyCode = lastTxn.transaction.currencyCode,
                                amountBase = lastTxn.transaction.amountBase
                            )
                        ),
                        subName = subName,
                        subFrequency = lastTxn.transaction.subFrequency ?: 1,
                        baseCurrency = baseCurrency
                    )
                }

                triggerRefresh()
                Toast.makeText(getApplication(), "Successfully recorded $count catch-up entry(ies) for $subName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Catch-up error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Formatting & Budget Reports
    fun formatAmount(a: Double): String {
        return if (useMillionsSystem) {
            val nf = java.text.NumberFormat.getCurrencyInstance(Locale.US)
            nf.maximumFractionDigits = decimalPlaces
            nf.minimumFractionDigits = decimalPlaces
            nf.format(a).replace("$", "").trim()
        } else {
            val pattern = if (decimalPlaces == 0) "##,##,##,###" else "##,##,##,###." + "0".repeat(decimalPlaces)
            val symbols = java.text.DecimalFormatSymbols(Locale.getDefault())
            java.text.DecimalFormat(pattern, symbols).format(a)
        }
    }
    fun formatAmount(minorUnits: Long) = formatAmount(minorUnits.toDouble() / 100.0)
    fun formatAmountWhole(a: Double) = formatAmount(a)
    fun formatAmountWhole(minorUnits: Long) = formatAmountWhole(minorUnits.toDouble() / 100.0)
    fun getFilteredTransactions(s: String, e: String) = _refreshTrigger.flatMapLatest { repository.getTransactionsByDateRange(s, e) }
    fun getAccountBalances(asOfDate: String) = _refreshTrigger.flatMapLatest { repository.getAccountBalances(asOfDate) }
    fun getAccountTransactions(id: Int, s: String, e: String) = repository.getAccountTransactionsByDateRange(id, s, e)
    fun getTransactionsByTags(ids: List<Int>, s: String, e: String) = repository.getTransactionsByDateRange(s, e).map { l -> l.filter { d -> (d.transaction.tags?.split(",")?.mapNotNull{it.toIntOrNull()}?:emptyList()).any{it in ids} } }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getBudgetVsActual(asOfDate: String): Flow<List<BudgetVsActual>> {
        return _refreshTrigger.flatMapLatest { getBudgetPerformanceUseCase(asOfDate) }
    }

    private fun getRangeForDuration(date: LocalDate, duration: String): Pair<String, String> {
        val formatter = DateTimeFormatter.ISO_DATE
        if (duration.startsWith("CUSTOM:")) {
            val parts = duration.split(":")
            val value = parts.getOrNull(1)?.toLongOrNull() ?: 1L
            val unit = parts.getOrNull(2) ?: "month/s"
            
            return when (unit) {
                "day/s" -> Pair(date.minusDays(value - 1).format(formatter), date.format(formatter))
                "week/s" -> {
                    val end = date.with(java.time.DayOfWeek.SUNDAY)
                    val start = end.minusWeeks(value - 1).with(java.time.DayOfWeek.MONDAY)
                    Pair(start.format(formatter), end.format(formatter))
                }
                "month/s" -> {
                    val end = date.with(TemporalAdjusters.lastDayOfMonth())
                    val start = date.minusMonths(value - 1).with(TemporalAdjusters.firstDayOfMonth())
                    Pair(start.format(formatter), end.format(formatter))
                }
                "year/s" -> {
                    val end = date.with(TemporalAdjusters.lastDayOfYear())
                    val start = date.minusYears(value - 1).with(TemporalAdjusters.firstDayOfYear())
                    Pair(start.format(formatter), end.format(formatter))
                }
                else -> Pair(date.format(formatter), date.format(formatter))
            }
        }
        return when (duration.uppercase()) {
            "WEEKLY" -> Pair(date.with(java.time.DayOfWeek.MONDAY).format(formatter), date.with(java.time.DayOfWeek.SUNDAY).format(formatter))
            "MONTHLY" -> Pair(date.with(TemporalAdjusters.firstDayOfMonth()).format(formatter), date.with(TemporalAdjusters.lastDayOfMonth()).format(formatter))
            "YEARLY" -> Pair(date.with(TemporalAdjusters.firstDayOfYear()).format(formatter), date.with(TemporalAdjusters.lastDayOfYear()).format(formatter))
            "HALF YEARLY" -> {
                val start = if (date.monthValue <= 6) date.withMonth(1).withDayOfMonth(1) else date.withMonth(7).withDayOfMonth(1)
                val end = start.plusMonths(5).with(TemporalAdjusters.lastDayOfMonth())
                Pair(start.format(formatter), end.format(formatter))
            }
            "DAILY" -> Pair(date.format(formatter), date.format(formatter))
            else -> {
                when (duration) {
                    "Weekly" -> Pair(date.with(java.time.DayOfWeek.MONDAY).format(formatter), date.with(java.time.DayOfWeek.SUNDAY).format(formatter))
                    "Monthly" -> Pair(date.with(TemporalAdjusters.firstDayOfMonth()).format(formatter), date.with(TemporalAdjusters.lastDayOfMonth()).format(formatter))
                    "Yearly" -> Pair(date.with(TemporalAdjusters.firstDayOfYear()).format(formatter), date.with(TemporalAdjusters.lastDayOfYear()).format(formatter))
                    "Half Yearly" -> {
                        val start = if (date.monthValue <= 6) date.withMonth(1).withDayOfMonth(1) else date.withMonth(7).withDayOfMonth(1)
                        val end = start.plusMonths(5).with(TemporalAdjusters.lastDayOfMonth())
                        Pair(start.format(formatter), end.format(formatter))
                    }
                    else -> Pair(date.format(formatter), date.format(formatter))
                }
            }
        }
    }

    suspend fun refreshDatabase(enqueueWorker: Boolean = true) {
        AppDatabase.databaseMutex.withLock {
            AppDatabase.closeDatabase()
        }
        triggerRefresh()
        if (enqueueWorker) {
            val manager = WorkManager.getInstance(getApplication())
            manager.enqueue(OneTimeWorkRequestBuilder<CcAlertWorker>().build())
        }
    }

    suspend fun prepareForBackup() = AppDatabase.databaseMutex.withLock {
        repository.prepareForBackup()
        AppDatabase.closeDatabase()
    }

    suspend fun performSafeBackup(context: Context, destUri: Uri): Boolean = AppDatabase.databaseMutex.withLock {
        try {
            val df = context.getDatabasePath("expenses_database")
            if (!df.exists()) return false
            
            repository.prepareForBackup()
            AppDatabase.closeDatabase()
            
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
            
            repository.prepareForBackup()
            AppDatabase.closeDatabase()
            
            withContext(Dispatchers.Main) {
            }
            
            val storedPass = EncryptedPrefsHelper.getString("remote_master_password", "")
            val passToUse: CharArray = if (remoteMasterPassword.isNotEmpty()) remoteMasterPassword 
                            else (storedPass?.toCharArray() ?: charArrayOf())
            
            val result = EncryptionService.encryptFile(df, tempEnc, passToUse)
            
            if (remoteMasterPassword.isEmpty() && storedPass != null) passToUse.fill('\u0000')

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
    fun toggleServer(o: Boolean) = if(o) serverManager.startServer(httpPort = serverPort, httpsPort = if (serverPort == 8080) 8443 else serverPort + 1) else serverManager.stopServer()
    suspend fun exportConfigsJson(): String {
        try {
            val majors = repository.getAllMajorHeads().first()
            val minors = repository.getAllMinorHeads().first()
            val accounts = repository.getAllAccounts().first()
            val categories = repository.getAllCategories().first()
            val budgets = repository.getAllBudgets().first()
            val tags = repository.getAllTags().first()
            val templates = repository.getAllTemplatesLegacy().first()

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
                budgets = repository.getBudgetsWithRelations().first().map { detailed ->
                    val b = detailed.budget
                    BudgetConfig(
                        b.name,
                        detailed.categories.map { it.name },
                        detailed.accounts.map { it.name },
                        b.amount, b.duration, b.note, b.higherIsBetter
                    )
                },
                tags = tags.map { TagConfig(it.name, it.isEnabled, it.trackingType, it.targetNumber) }
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
            val existingTags = repository.getAllTags().first()
            config.tags.forEach { tagCfg ->
                val existing = existingTags.find { it.name == tagCfg.name }
                repository.upsertTag(Tag(id = existing?.id ?: 0, name = tagCfg.name, isEnabled = tagCfg.isEnabled, trackingType = tagCfg.trackingType, targetNumber = tagCfg.targetNumber))
            }
            val existingMajors = repository.getAllMajorHeads().first()
            config.majorHeads.forEach { majorCfg ->
                val existing = existingMajors.find { it.name == majorCfg.name }
                repository.upsertMajorHead(MajorHead(id = existing?.id ?: 0, name = majorCfg.name, isEnabled = majorCfg.isEnabled))
            }
            val allMajors = repository.getAllMajorHeads().first()
            val existingMinors = repository.getAllMinorHeads().first()
            config.minorHeads.forEach { m ->
                val major = allMajors.find { it.name == m.majorHeadName }
                if (major != null) {
                    val existing = existingMinors.find { it.name == m.name && it.majorHeadId == major.id }
                    repository.upsertMinorHead(MinorHead(id = existing?.id ?: 0, name = m.name, majorHeadId = major.id, isEnabled = m.isEnabled))
                }
            }
            val allMinors = repository.getAllMinorHeads().first()
            val currentAccounts = repository.getAllAccounts().first()
            config.accounts.forEach { a ->
                val minor = allMinors.find { it.name == a.minorHeadName }
                val existing = currentAccounts.find { it.name == a.name }
                repository.upsertAccount(Account(
                    id = existing?.id ?: 0,
                    name = a.name, type = a.type, openingBalance = a.openingBalance, description = a.description,
                    isEnabled = a.isEnabled, minorHeadId = minor?.id, creditLimit = a.creditLimit,
                    billingCycleStart = a.billingCycleStart, billingCycleEnd = a.billingCycleEnd, paymentDueDate = a.paymentDueDate
                ))
            }
            val allAccounts = repository.getAllAccounts().first()
            val existingCats = repository.getAllCategories().first()
            config.categories.forEach { c ->
                val existing = existingCats.find { it.name == c.name && it.type == c.type }
                repository.upsertCategory(Category(id = existing?.id ?: 0, name = c.name, type = c.type, description = c.description, isEnabled = c.isEnabled))
            }
            val allCategories = repository.getAllCategories().first()
            val currentTemplates = repository.getAllTemplatesLegacy().first()
            config.templates.forEach { t ->
                val acc = allAccounts.find { it.name == t.accountName }
                val toAcc = allAccounts.find { it.name == t.toAccountName }
                val cat = allCategories.find { it.name == t.categoryName && it.type == (t.categoryType ?: "expense") }
                val existing = currentTemplates.find { it.name == t.name }
                repository.upsertTemplateLegacy(TemplateLegacy(
                    id = existing?.id ?: 0,
                    name = t.name, type = t.type, accountId = acc?.id, toAccountId = toAcc?.id, categoryId = cat?.id,
                    amount = t.amount, note = t.note, tags = t.tags, multiEntries = t.multiEntries,
                    subName = t.subName, subFrequency = t.subFrequency
                ))
            }
            config.budgets.forEach { b ->
                val budgetId = repository.upsertBudget(Budget(name = b.name, amount = b.amount, duration = b.duration, note = b.note, higherIsBetter = b.higherIsBetter)).toInt()
                b.categoryNames.mapNotNull { name -> allCategories.find { it.name == name }?.id }.forEach {
                    repository.insertBudgetCategory(BudgetCategory(budgetId, it))
                }
                b.accountNames.mapNotNull { name -> allAccounts.find { it.name == name }?.id }.forEach {
                    repository.insertBudgetAccount(BudgetAccount(budgetId, it))
                }
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

                val rawInsights = generateFinancialInsightsUseCase(merchantTrackerEnabled, incomeAtMonthEnd)
                val insights = rawInsights.filter { !isInsightPaused(it.id) }

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

    private val investmentPrefs = getApplication<Application>().getSharedPreferences("investment_values", Context.MODE_PRIVATE)

    fun getInvestmentValue(accountId: Int): Double {
        return investmentPrefs.getString("val_$accountId", "0.0")?.toDoubleOrNull() ?: 0.0
    }

    fun saveInvestmentValue(accountId: Int, value: Double) {
        investmentPrefs.edit().putString("val_$accountId", value.toString()).apply()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getInvestmentAccounts(asOfDate: String): Flow<List<AccountBalance>> {
        return repository.getAllMajorHeads().flatMapLatest { majors ->
            val investmentMajor = majors.find { it.name.contains("Investment", ignoreCase = true) }
            if (investmentMajor == null) flowOf(emptyList())
            else {
                repository.getMinorHeadsByMajor(investmentMajor.id).flatMapLatest { minors ->
                    val minorIds = minors.map { it.id }
                    repository.getAccountBalances(asOfDate).map { balances ->
                        balances.filter { it.minorHeadId in minorIds }
                    }
                }
            }
        }
    }

    fun dismissInsight(insight: FinancialInsight) {
        financialInsights.remove(insight)
    }

    fun pauseInsight(insightId: String) {
        val resumeDate = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_DATE)
        prefs.edit().putString("paused_insight_$insightId", resumeDate).apply()
        financialInsights.removeIf { it.id == insightId }
    }

    private fun isInsightPaused(insightId: String): Boolean {
        val resumeDateStr = prefs.getString("paused_insight_$insightId", null) ?: return false
        return try {
            val resumeDate = LocalDate.parse(resumeDateStr)
            LocalDate.now().isBefore(resumeDate)
        } catch (e: Exception) {
            false
        }
    }

    fun getPerformanceData(startDate: String, endDate: String): Flow<PerformanceDashboardData> = combine(
        listOf(allTransactions, repository.getAllAccounts(), repository.getAllMinorHeads(), repository.getBudgetsWithRelations(), repository.getEnabledCategories(), repository.getAllParties())
    ) { array ->
        val txns = array[0] as List<TransactionWithDetails>
        val accounts = array[1] as List<Account>
        val minorHeads = array[2] as List<MinorHead>
        val budgetsDetailed = array[3] as List<BudgetWithRelations>
        val categories = array[4] as List<Category>
        val parties = array[5] as List<Party>

        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        val days = mutableListOf<String>()
        var curr = start
        while (!curr.isAfter(end)) {
            days.add(curr.format(DateTimeFormatter.ISO_DATE))
            curr = curr.plusDays(1)
        }

        // 1. Calculate Daily Metrics
        val dailyInc = txns.filter { it.categoryType == "income" }
            .groupBy { it.transaction.date }
            .mapValues { it.value.sumOf { t -> t.transaction.amount } }
        
        val dailyExp = txns.filter { it.categoryType == "expense" }
            .groupBy { it.transaction.date }
            .mapValues { it.value.sumOf { t -> t.transaction.amount } }
            
        val savingsRateMap = days.associateWith { d ->
            val inc = dailyInc[d] ?: 0.0
            val exp = dailyExp[d] ?: 0.0
            if (inc > 0) (inc - exp) / inc * 100 else 0.0
        }

        val investmentMajorId = runBlocking { repository.getAllMajorHeads().first() }.find { it.name.contains("Investment", true) }?.id
        val dailyInvest = txns.filter { t ->
            t.transaction.categoryId == null && t.transaction.toAccountId != null &&
            minorHeads.find { it.id == accounts.find { acc -> acc.id == t.transaction.toAccountId }?.minorHeadId }?.majorHeadId == investmentMajorId
        }.groupBy { it.transaction.date }
        .mapValues { it.value.sumOf { t -> t.transaction.amount } }

        val investRateMap = days.associateWith { d ->
            val inc = dailyInc[d] ?: 0.0
            val inv = dailyInvest[d] ?: 0.0
            if (inc > 0) (inv / inc) * 100 else 0.0
        }

        val dailyDebt = txns.filter { it.transaction.subName?.startsWith("LOAN:") == true }
            .groupBy { it.transaction.date }
            .mapValues { it.value.sumOf { t -> t.transaction.amount } }

        val debtToIncomeMap = days.associateWith { d ->
            val inc = dailyInc[d] ?: 0.0
            val debt = dailyDebt[d] ?: 0.0
            if (inc > 0) (debt / inc) * 100 else 0.0
        }

        // Networth Calculation
        val initialNetworthBase = accounts.sumOf { if (it.name == "On Account") 0.0 else it.openingBalance.toDouble() / 100.0 } +
                parties.filter { it.isEnabled }.sumOf { it.openingBalance.toDouble() / 100.0 }

        val networthMap = mutableMapOf<String, Double>()
        val networthChangePctMap = mutableMapOf<String, Double>()

        var previousDayNetworth = 0.0
        val baselineDate = start.minusDays(1).toString()
        val baselineIncome = txns.filter { it.transaction.date <= baselineDate && it.categoryType == "income" }.sumOf { it.transaction.amount }
        val baselineExpense = txns.filter { it.transaction.date <= baselineDate && it.categoryType == "expense" }.sumOf { it.transaction.amount }
        previousDayNetworth = initialNetworthBase + baselineIncome - baselineExpense

        for (d in days) {
            val totalIncome = txns.filter { it.transaction.date <= d && it.categoryType == "income" }.sumOf { it.transaction.amount }
            val totalExpense = txns.filter { it.transaction.date <= d && it.categoryType == "expense" }.sumOf { it.transaction.amount }

            val currentNetworth = initialNetworthBase + totalIncome - totalExpense
            networthMap[d] = currentNetworth

            if (previousDayNetworth != 0.0) {
                networthChangePctMap[d] = (currentNetworth - previousDayNetworth) / previousDayNetworth * 100
            } else {
                networthChangePctMap[d] = 0.0
            }
            previousDayNetworth = currentNetworth
        }

        val metrics = listOf(
            PerformanceMetricData("Income", days.associateWith { dailyInc[it] ?: 0.0 }),
            PerformanceMetricData("Expense", days.associateWith { dailyExp[it] ?: 0.0 }),
            PerformanceMetricData("Networth", networthMap, aggregation = MetricAggregation.LAST),
            PerformanceMetricData("Savings Rate", savingsRateMap, "%", aggregation = MetricAggregation.AVERAGE),
            PerformanceMetricData("Networth Change", networthChangePctMap, "%", aggregation = MetricAggregation.AVERAGE),
            PerformanceMetricData("Investment Rate", investRateMap, "%", aggregation = MetricAggregation.AVERAGE),
            PerformanceMetricData("Debt-to-Income", debtToIncomeMap, "%", aggregation = MetricAggregation.AVERAGE)
        )

        val months = days.map { it.substring(0, 7) }.distinct()

        // 2. Budget Performance (Summarized)
        val budgetPerf = budgetsDetailed.map { detailed ->
            val b = detailed.budget
            val catIds = detailed.categories.map { it.id }.toSet()
            val actual = txns.filter { it.transaction.date in startDate..endDate && it.transaction.categoryId in catIds }
                .sumOf { it.transaction.amount }
            
            val variance = b.amount - actual
            val varPct = if (b.amount > 0) (variance / b.amount) * 100 else 0.0
            
            BudgetPerformanceData(
                name = b.name ?: detailed.categories.joinToString { it.name },
                duration = b.duration,
                budget = b.amount,
                actual = actual,
                variance = variance,
                variancePercent = varPct,
                budgetId = b.id
            )
        }

        // 3. Monthly Budget Trends
        val budgetTrends = budgetsDetailed.map { detailed ->
            val b = detailed.budget
            val catIds = detailed.categories.map { it.id }.toSet()
            val monthData = months.associateWith { m ->
                val actual = txns.filter { it.transaction.date.startsWith(m) && it.transaction.categoryId in catIds }
                    .sumOf { it.transaction.amount }
                val variance = b.amount - actual
                val varPct = if (b.amount > 0) (variance / b.amount) * 100 else 0.0
                BudgetPerformanceData(
                    name = b.name ?: "Budget",
                    duration = b.duration,
                    budget = b.amount,
                    actual = actual,
                    variance = variance,
                    variancePercent = varPct,
                    budgetId = b.id
                )
            }
            BudgetTrendData(b.id, b.name ?: "Budget ${b.id}", monthData)
        }

        // 4. Category Performance Trends
        val categoryTrends = categories.map { cat ->
            val monthData = months.associateWith { m ->
                val currMonthDate = LocalDate.parse("$m-01")
                val prevMonthStr = currMonthDate.minusMonths(1).toString().substring(0, 7)
                val prevYearStr = currMonthDate.minusYears(1).toString().substring(0, 7)

                val amount = txns.filter { it.transaction.date.startsWith(m) && it.transaction.categoryId == cat.id }.sumOf { it.transaction.amount }
                val prevAmount = txns.filter { it.transaction.date.startsWith(prevMonthStr) && it.transaction.categoryId == cat.id }.sumOf { it.transaction.amount }
                val yoyAmount = txns.filter { it.transaction.date.startsWith(prevYearStr) && it.transaction.categoryId == cat.id }.sumOf { it.transaction.amount }

                val momChange = amount - prevAmount
                val momPct = if (prevAmount > 0) (momChange / prevAmount) * 100 else 0.0
                val yoyChange = amount - yoyAmount

                CategoryMetricData(amount, momChange, momPct, yoyAmount, yoyChange)
            }
            CategoryPerformanceData(cat.id, cat.name, monthData)
        }

        PerformanceDashboardData(metrics, budgetPerf, budgetTrends, categoryTrends)
    }

    private fun processPendingSmsRules() {
        if (!prefs.getBoolean("auto_read_enabled", false)) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastLoggedTime = prefs.getLong("last_secure_close_time", 0L)
                if (lastLoggedTime == 0L) return@launch

                val rules = repository.getEnabledRulesInternal()
                if (rules.isEmpty()) {
                    prefs.edit().remove("last_secure_close_time").apply()
                    return@launch
                }

                val context = getApplication<Application>()
                val uri = Uri.parse("content://sms/inbox")
                val projection = arrayOf("_id", "address", "body", "date")
                val selection = "date > ?"
                val selectionArgs = arrayOf(lastLoggedTime.toString())
                val sortOrder = "date ASC"

                val currencies = (prefs.getString("sms_currencies", "INR,USD") ?: "INR,USD").split(",").map { it.trim() }

                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val addressIdx = cursor.getColumnIndex("address")
                    val bodyIdx = cursor.getColumnIndex("body")
                    val dateIdx = cursor.getColumnIndex("date")

                    while (cursor.moveToNext()) {
                        val sender = cursor.getString(addressIdx)
                        val body = cursor.getString(bodyIdx)
                        val msgDate = cursor.getLong(dateIdx)
                        
                        applyRulesToMessage(body, sender, currencies, rules, msgDate)
                    }
                }
                
                prefs.edit().remove("last_secure_close_time").apply()
                triggerRefresh()
            } catch (e: Exception) {
                Log.e("SecureMode", "Error processing pending SMS rules", e)
            }
        }
    }

    private suspend fun applyRulesToMessage(body: String, sender: String, currencies: List<String>, rules: List<Rule>, msgDate: Long) {
        for (rule in rules) {
            val senderMatches = rule.msgFrom == null || sender.contains(rule.msgFrom, ignoreCase = true)
            val textMatches = body.contains(rule.textContaining, ignoreCase = true)
            
            if (senderMatches && textMatches) {
                val amount = parseAmount(body, currencies) ?: 0.0
                if (amount > 0) {
                    val date = Instant.ofEpochMilli(msgDate).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_DATE)
                    val time = Instant.ofEpochMilli(msgDate).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    
                    val prefix = when(rule.type) { "income"->"INC"; "expense"->"EXP"; "transfer"->"TNF"; else->"TXN" }
                    val lastNum = repository.getLastTransactionNumber(prefix)
                    val nextSerial = (lastNum?.split("/")?.last()?.toIntOrNull() ?: 99999) + 1
                    val year = LocalDate.now().year
                    val txnNumber = "$prefix/$year/$nextSerial"

                    val transaction = TransactionLegacy(
                        date = date,
                        time = time,
                        accountId = rule.accountId ?: 0,
                        toAccountId = rule.toAccountId,
                        categoryId = rule.categoryId,
                        amount = amount,
                        note = rule.note ?: "Auto-recorded via rule: ${rule.name}",
                        tags = rule.tags,
                        transactionNumber = txnNumber,
                        partyId = rule.partyId,
                        toPartyId = rule.toPartyId,
                        amountOriginal = amount,
                        currencyCode = currencies.firstOrNull() ?: "INR",
                        amountBase = amount
                    )
                    
                    val isTransferValid = rule.type == "transfer" && rule.accountId != null && rule.toAccountId != null
                    val isOtherValid = rule.type != "transfer" && rule.accountId != null && rule.categoryId != null

                    if (isTransferValid || isOtherValid) {
                        repository.insertTransactionLegacy(transaction)
                    }
                }
            }
        }
    }

    private fun parseAmount(body: String, currencies: List<String>): Double? {
        for (curr in currencies) {
            val pattern = """(?i)${Regex.escape(curr)}\s*([\d,]+\.?\d*)""".toRegex()
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues.get(1).replace(",", "").toDoubleOrNull()
            }
        }
        val genericPattern = """(?i)(?:spent|amt|amount|rs\.?|inr|usd)\s*([\d,]+\.?\d*)""".toRegex()
        val genericMatch = genericPattern.find(body)
        return genericMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }
}


data class DraftTransaction(
    val type: String,
    val amount: String,
    val note: String,
    val date: String,
    val time: String,
    val accountId: Int?,
    val toAccountId: Int?,
    val categoryId: Int?,
    val selectedTagIds: List<Int>,
    val isMultiEntry: Boolean = false,
    val multiEntryRows: List<DraftMultiEntryRow> = emptyList(),
    val multiEntryType: String = "Category",
    val selectedPartyId: Int? = null,
    val selectedToPartyId: Int? = null,
    val foreignCurrency: String? = null,
    val isNegotiated: Boolean = false,
    val negotiationAmountOriginal: String = "",
    val merchantName: String = "",
    val isDiscretionary: Boolean = false,
    val subName: String = "",
    val subFrequency: String = "",
    val invoiceNumber: String = "",
    val dueDays: String = "",
    val selectedInvoiceIds: List<Int> = emptyList()
)
data class DraftMultiEntryRow(val categoryId: Int?, val accountId: Int?, val amount: String, val note: String? = null, val currencyCode: String? = null, val tags: String? = null)
data class MultiEntryRowData(val categoryId: Int?, val accountId: Int?, val amount: Double, val note: String?, val currencyCode: String, val tags: String? = null)
data class DraftAccount(val name: String, val type: String, val description: String, val openingBalance: String, val isEnabled: Boolean, val selectedMajorHeadId: Int?, val selectedMinorHeadId: Int?, val creditLimit: String, val billingCycleStart: String, val billingCycleEnd: String, val paymentDueDate: String, val icon: String? = null, val isEmergencyFund: Boolean = false, val defaultDueDays: String = "", val last4Digits: String = "", val ifscCode: String = "", val branchName: String = "", val websiteUrl: String = "", val contactPerson: String = "", val minimumBalance: String = "", val maturityDate: String = "", val bankName: String = "")
data class CcAlert(val accountId: Int, val accountName: String, val amount: Double, val dueDate: LocalDate)
data class SubscriptionAlert(val subName: String, val amount: Double, val dueDate: LocalDate, val isTransfer: Boolean = false)
data class FdMaturityAlert(val accountId: Int, val accountName: String, val maturityDate: LocalDate, val balance: Double)
